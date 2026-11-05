package com.arrl.radiocraft.common.blockentities.adsb;

import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.INetworkObjectProvider;
import com.arrl.radiocraft.common.be_networks.network_objects.AdsbNetworkObjects;
import com.arrl.radiocraft.common.blocks.adsb.AdsbBeaconBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.menus.AdsbBeaconMenu;
import com.arrl.radiocraft.common.network.clientbound.CAdsbPacket;
import com.arrl.radiocraft.common.radio.adsb.AdsbAirspace;
import com.arrl.radiocraft.common.radio.adsb.AdsbMath;
import com.arrl.radiocraft.common.radio.adsb.AdsbMotion;
import com.arrl.radiocraft.common.radio.adsb.AdsbTarget;
import com.arrl.radiocraft.compat.sable.Sublevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An ADS-B beacon (1.12 step 9), the transponder of a craft: while switched on and powered it sends, about once a
 * second, its 24-bit ICAO address, flight identification, squawk, where it really is (on a Sable sub-level or a Create
 * Aeronautics craft, the place the ship has carried it to, see {@link Sublevels}), its altitude, track, ground speed
 * and climb ({@link AdsbMotion}, from where it was a second before). Each report goes into the level's
 * {@link AdsbAirspace}, where switched-on receivers read it; nothing else is sent anywhere, so a beacon costs a few
 * sums a second. 1090 MHz is line of sight: lightning static and solar weather do not touch it.
 *
 * <p>With no flight identification set, a beacon on a named ship sends the ship's name (capitals and digits, eight
 * at most); otherwise receivers show its ICAO address. The address is drawn at random when the beacon is first
 * placed and kept.</p>
 */
public class AdsbBeaconBlockEntity extends BlockEntity implements INetworkObjectProvider, MenuProvider {

	/** Ticks between reports. */
	public static final int REPORT_TICKS = 20;

	private int icao = -1;
	private String callsign = "";
	private int squawk = AdsbMath.VFR_SQUAWK;
	/** The power switch as saved, put back on the network object when it is made again after a reload. */
	private boolean switchedOn = false;

	// Server only.
	private final AdsbMotion motion = new AdsbMotion();
	@Nullable
	private AdsbTarget last;
	private boolean powered = false;

	public AdsbBeaconBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.ADSB_BEACON.get(), pos, state);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if(level != null && !level.isClientSide) {
			if(icao <= 0) { // A new beacon: a random address (never 0, which means none).
				icao = 1 + level.random.nextInt(0xFFFFFE);
				setChanged();
			}
			getNetworkObject(level, worldPosition);
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if(level != null && !level.isClientSide)
			AdsbAirspace.withdraw(level, worldPosition);
	}

	@Override
	public void onChunkUnloaded() {
		super.onChunkUnloaded();
		if(level != null && !level.isClientSide)
			AdsbAirspace.withdraw(level, worldPosition);
	}

	@Override
	public BENetworkObject createNetworkObject() {
		return new AdsbNetworkObjects.Beacon(level, worldPosition);
	}

	/** Networks live in memory only: after a reload the switch comes back from the block entity's save. */
	@Override
	public void onNetworkObjectCreated(Level level, BlockPos pos, BENetworkObject networkObject) {
		if(switchedOn && networkObject instanceof AdsbNetworkObjects.Beacon beacon)
			beacon.wantsOn = true;
	}

	@Nullable
	private AdsbNetworkObjects.Beacon beaconObject() {
		return level != null && getNetworkObject(level, worldPosition) instanceof AdsbNetworkObjects.Beacon beacon ? beacon : null;
	}

	// -------------------- TICK --------------------

	public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T blockEntity) {
		if(level instanceof ServerLevel serverLevel && blockEntity instanceof AdsbBeaconBlockEntity beacon)
			beacon.serverTick(serverLevel, state);
	}

	private void serverTick(ServerLevel serverLevel, BlockState state) {
		AdsbNetworkObjects.Beacon object = beaconObject();
		boolean nowPowered = object != null && object.isPowered;
		long now = serverLevel.getGameTime();
		if(nowPowered != powered) {
			powered = nowPowered;
			if(!powered)
				goSilent(serverLevel);
			if(state.getBlock() instanceof AdsbBeaconBlock && state.getValue(AdsbBeaconBlock.LIT) != powered)
				serverLevel.setBlock(worldPosition, state.setValue(AdsbBeaconBlock.LIT, powered), Block.UPDATE_CLIENTS);
		}
		if((now + worldPosition.asLong()) % REPORT_TICKS != 0L)
			return;
		if(powered)
			report(serverLevel, now);
		for(ServerPlayer player : serverLevel.players())
			if(player.containerMenu instanceof AdsbBeaconMenu menu && menu.blockEntity == this)
				sendStatus(player);
	}

	private void goSilent(Level level) {
		AdsbAirspace.withdraw(level, worldPosition);
		motion.reset();
		last = null;
	}

	/** Sends one report into the airspace. */
	private void report(ServerLevel serverLevel, long now) {
		Vec3 at = Sublevels.worldCenter(serverLevel, worldPosition);
		motion.update(at.x, at.y, at.z, now, noseHeading());
		last = new AdsbTarget(icao, shownCallsign(), squawk, at.x, at.y, at.z, at.y - serverLevel.getSeaLevel(),
				motion.heading(), motion.groundSpeed(), motion.verticalRate(), now, worldPosition.asLong());
		AdsbAirspace.publish(serverLevel, worldPosition, last);
	}

	/** Where the beacon's front points, turned with the ship it is on: compass degrees, or NaN if it points up or down. */
	private double noseHeading() {
		BlockState state = getBlockState();
		Direction facing = state.hasProperty(AdsbBeaconBlock.FACING) ? state.getValue(AdsbBeaconBlock.FACING) : Direction.NORTH;
		Vec3 nose = Sublevels.frame(level, worldPosition).applyDirection(new Vec3(facing.getStepX(), 0.0D, facing.getStepZ()));
		if(nose.x * nose.x + nose.z * nose.z < 1.0E-4D)
			return Double.NaN;
		return AdsbMath.bearing(nose.x, nose.z);
	}

	// -------------------- IDENTITY AND CONTROLS --------------------

	/** The flight identification it sends: the one set, else its ship's name, else "" (receivers show the address). */
	public String shownCallsign() {
		if(!callsign.isEmpty())
			return callsign;
		return level == null ? "" : AdsbMath.cleanCallsign(Sublevels.name(level, worldPosition));
	}

	public int getIcao() {
		return icao;
	}

	public String getCallsign() {
		return callsign;
	}

	public int getSquawk() {
		return squawk;
	}

	public boolean isPowered() {
		return powered;
	}

	public boolean isSwitchedOn() {
		return switchedOn;
	}

	/** The latest report sent, or null while silent. */
	@Nullable
	public AdsbTarget lastReport() {
		return last;
	}

	public void setCallsign(String text) {
		callsign = AdsbMath.cleanCallsign(text);
		setChanged();
	}

	/** False if {@code code} is not a squawk (four digits 0-7). */
	public boolean setSquawk(int code) {
		if(!AdsbMath.validSquawk(code))
			return false;
		squawk = code;
		setChanged();
		return true;
	}

	public void togglePower() {
		AdsbNetworkObjects.Beacon object = beaconObject();
		if(object == null || level == null)
			return;
		object.wantsOn = !object.wantsOn;
		switchedOn = object.wantsOn;
		setChanged();
		object.tick(level, worldPosition); // So the screen's next look finds it on.
	}

	/** What the beacon's screen shows, sent to a player who has it open. */
	public CompoundTag statusTag() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("on", switchedOn);
		tag.putBoolean("powered", powered);
		tag.putInt("icao", icao);
		tag.putString("callsign", callsign);
		tag.putString("shown", shownCallsign());
		tag.putInt("squawk", squawk);
		String ship = level == null ? "" : Sublevels.name(level, worldPosition);
		tag.putBoolean("onShip", level != null && Sublevels.onSublevel(level, worldPosition));
		tag.putString("ship", ship);
		AdsbTarget report = last;
		tag.putBoolean("air", report != null);
		if(report != null) {
			tag.putInt("x", (int)Math.floor(report.x()));
			tag.putInt("y", (int)Math.floor(report.y()));
			tag.putInt("z", (int)Math.floor(report.z()));
			tag.putInt("alt", report.feet());
			tag.putInt("gs", report.knots());
			tag.putInt("trk", (int)Math.round(report.heading()) % 360);
			tag.putInt("vs", report.feetPerMinute());
		}
		return tag;
	}

	public void sendStatus(ServerPlayer player) {
		CAdsbPacket.send(player, worldPosition, statusTag());
	}

	// -------------------- SAVE, MENU --------------------

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("icao", icao);
		tag.putString("callsign", callsign);
		tag.putInt("squawk", squawk);
		tag.putBoolean("switchedOn", switchedOn);
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.loadAdditional(tag, registries);
		icao = tag.contains("icao") ? tag.getInt("icao") & 0xFFFFFF : -1;
		callsign = AdsbMath.cleanCallsign(tag.getString("callsign"));
		squawk = tag.contains("squawk") && AdsbMath.validSquawk(tag.getInt("squawk")) ? tag.getInt("squawk") : AdsbMath.VFR_SQUAWK;
		switchedOn = tag.getBoolean("switchedOn");
	}

	@Override
	public @NotNull Component getDisplayName() {
		return Component.translatable("container.radiocraft.adsb_beacon");
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
		return new AdsbBeaconMenu(id, this);
	}
}
