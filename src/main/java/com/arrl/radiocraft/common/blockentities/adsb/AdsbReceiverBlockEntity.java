package com.arrl.radiocraft.common.blockentities.adsb;

import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.INetworkObjectProvider;
import com.arrl.radiocraft.common.be_networks.network_objects.AdsbNetworkObjects;
import com.arrl.radiocraft.common.blocks.adsb.AdsbAntennaBlock;
import com.arrl.radiocraft.common.blocks.adsb.AdsbReceiverBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.menus.AdsbReceiverMenu;
import com.arrl.radiocraft.common.network.clientbound.CAdsbPacket;
import com.arrl.radiocraft.common.radio.adsb.AdsbAirspace;
import com.arrl.radiocraft.common.radio.adsb.AdsbMath;
import com.arrl.radiocraft.common.radio.adsb.AdsbPicture;
import com.arrl.radiocraft.common.radio.adsb.AdsbTarget;
import com.arrl.radiocraft.common.radio.adsb.AdsbTracks;
import com.arrl.radiocraft.compat.sable.Sublevels;
import net.minecraft.core.BlockPos;
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

import java.util.UUID;

/**
 * An ADS-B receiver (1.12 step 9). While it is switched on, powered and has an ADS-B antenna (on its coax, or set
 * straight on top of it), it listens once a second: it reads the level's {@link AdsbAirspace} (only the beacons on the
 * air, never the world) and keeps a track for every aircraft within range ({@link AdsbMath#range}: the full range for
 * an aircraft at {@code adsb_full_range_y} or higher, falling off steeply below that to {@code adsb_min_range}). A receiver
 * that is off computes nothing. Its own craft (a beacon on the same Sable sub-level) is left off, as a real display
 * hides its own aircraft. Its round green screen and the radio computer's ADS-B program show the tracks
 * ({@link #picture}).
 */
public class AdsbReceiverBlockEntity extends BlockEntity implements INetworkObjectProvider, MenuProvider {

	/** The ranges its screen shows, blocks from the middle to the edge. */
	public static final int[] RANGES = { 250, 500, 1000, 2000, 4000, 8000, 12000, 20000 };
	private static final int DEFAULT_RANGE = 2;
	/** Ticks between listens. */
	public static final int LISTEN_TICKS = 20;
	/**
	 * How near a player must be to get the picture for the scope drawn on the receiver's tube in the world, blocks
	 * (sent once a listen while it is powered; the renderer draws no further).
	 */
	public static final int SCOPE_VIEW_RANGE = 32;

	/** The power switch as saved, put back on the network object when it is made again after a reload. */
	private boolean switchedOn = false;
	private int rangeIndex = DEFAULT_RANGE;

	// Server only.
	private final AdsbTracks tracks = new AdsbTracks();
	private boolean powered = false;
	@Nullable
	private BlockPos antenna;
	/** Where the antenna and the receiver really are (on a ship, where it has carried them). */
	private Vec3 antennaAt = Vec3.ZERO, centre = Vec3.ZERO;

	public AdsbReceiverBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.ADSB_RECEIVER.get(), pos, state);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if(level != null && !level.isClientSide)
			getNetworkObject(level, worldPosition);
	}

	@Override
	public BENetworkObject createNetworkObject() {
		return new AdsbNetworkObjects.Receiver(level, worldPosition);
	}

	@Override
	public void onNetworkObjectCreated(Level level, BlockPos pos, BENetworkObject networkObject) {
		if(switchedOn && networkObject instanceof AdsbNetworkObjects.Receiver receiver)
			receiver.wantsOn = true;
	}

	@Nullable
	private AdsbNetworkObjects.Receiver receiverObject() {
		return level != null && getNetworkObject(level, worldPosition) instanceof AdsbNetworkObjects.Receiver receiver ? receiver : null;
	}

	// -------------------- TICK --------------------

	public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T blockEntity) {
		if(level instanceof ServerLevel serverLevel && blockEntity instanceof AdsbReceiverBlockEntity receiver)
			receiver.serverTick(serverLevel, state);
	}

	private void serverTick(ServerLevel serverLevel, BlockState state) {
		AdsbNetworkObjects.Receiver object = receiverObject();
		boolean nowPowered = object != null && object.isPowered;
		if(nowPowered != powered) {
			powered = nowPowered;
			if(!powered)
				tracks.clear();
			if(state.getBlock() instanceof AdsbReceiverBlock && state.getValue(AdsbReceiverBlock.LIT) != powered)
				serverLevel.setBlock(worldPosition, state.setValue(AdsbReceiverBlock.LIT, powered), Block.UPDATE_CLIENTS);
		}
		long now = serverLevel.getGameTime();
		if((now + worldPosition.asLong()) % LISTEN_TICKS != 0L)
			return;
		if(powered)
			listen(serverLevel, object, now);
		CompoundTag nearby = null;
		for(ServerPlayer player : serverLevel.players())
			if(player.containerMenu instanceof AdsbReceiverMenu menu && menu.blockEntity == this)
				sendPicture(player);
			else if(powered && player.distanceToSqr(worldPosition.getCenter()) <= SCOPE_VIEW_RANGE * SCOPE_VIEW_RANGE) {
				// The tube seen in the world (AdsbScopeRenderer): a compact picture, made once for everyone near.
				if(nearby == null)
					nearby = AdsbPicture.compact(picture(), AdsbPicture.COMPACT_TARGETS);
				CAdsbPacket.send(player, worldPosition, nearby);
			}
	}

	/** One listen: every fresh report in the airspace, kept if it is in range of the antenna. */
	private void listen(ServerLevel serverLevel, @Nullable AdsbNetworkObjects.Receiver object, long now) {
		antenna = findAntenna(serverLevel, object);
		centre = Sublevels.worldCenter(serverLevel, worldPosition);
		if(antenna == null) {
			tracks.clear();
			return;
		}
		antennaAt = Sublevels.worldCenter(serverLevel, antenna);
		double seaLevel = serverLevel.getSeaLevel(), fullY = RadiocraftServerConfig.ADSB_FULL_RANGE_Y.get();
		double minRange = RadiocraftServerConfig.ADSB_MIN_RANGE.get(), maxRange = fullRange();
		UUID ownShip = Sublevels.id(serverLevel, worldPosition);
		for(AdsbTarget report : AdsbAirspace.reports(serverLevel)) {
			if(!AdsbAirspace.isFresh(report, now))
				continue;
			if(ownShip != null && ownShip.equals(Sublevels.id(serverLevel, BlockPos.of(report.beacon()))))
				continue; // Our own craft.
			double range = AdsbMath.range(report.y(), seaLevel, fullY, minRange, maxRange);
			if(AdsbMath.inRange(report.x() - antennaAt.x, report.z() - antennaAt.z, range))
				tracks.heard(report, now);
		}
		tracks.prune(now);
	}

	/** How far an aircraft at {@code adsb_full_range_y} or higher is heard, blocks (the config, or twice the horn's reach). */
	public static double fullRange() {
		return Math.max(RadiocraftServerConfig.ADSB_MIN_RANGE.get(), AdsbMath.maxRange(RadiocraftServerConfig.ADSB_MAX_RANGE.get(),
				RadiocraftServerConfig.MICROWAVE_DISH_RANGE.get(), RadiocraftServerConfig.MICROWAVE_HORN_RANGE_FACTOR.get()));
	}

	/** The highest ADS-B antenna on its coax or straight on top of it, or null. */
	@Nullable
	private BlockPos findAntenna(ServerLevel serverLevel, @Nullable AdsbNetworkObjects.Receiver object) {
		BlockPos best = null;
		double bestY = Double.NEGATIVE_INFINITY;
		BlockPos above = worldPosition.above();
		if(serverLevel.getBlockState(above).getBlock() instanceof AdsbAntennaBlock) {
			best = above;
			bestY = Sublevels.worldCenter(serverLevel, above).y;
		}
		if(object != null)
			for(BlockPos at : object.antennas()) {
				if(!serverLevel.isLoaded(at) || !(serverLevel.getBlockState(at).getBlock() instanceof AdsbAntennaBlock))
					continue;
				double y = Sublevels.worldCenter(serverLevel, at).y;
				if(y > bestY) {
					best = at;
					bestY = y;
				}
			}
		return best;
	}

	// -------------------- CONTROLS --------------------

	public void togglePower() {
		AdsbNetworkObjects.Receiver object = receiverObject();
		if(object == null || level == null)
			return;
		object.wantsOn = !object.wantsOn;
		switchedOn = object.wantsOn;
		setChanged();
		object.tick(level, worldPosition);
	}

	/** Shows the next range out ({@code step} 1) or in (-1). */
	public void stepRange(int step) {
		rangeIndex = Math.max(0, Math.min(RANGES.length - 1, rangeIndex + Integer.signum(step)));
		setChanged();
	}

	public int getRange() {
		return RANGES[rangeIndex];
	}

	public boolean isPowered() {
		return powered;
	}

	public boolean isSwitchedOn() {
		return switchedOn;
	}

	/** True if it found an antenna at its last listen. */
	public boolean hasAntenna() {
		return antenna != null;
	}

	/** What it hears now (server side). */
	public AdsbTracks getTracks() {
		return tracks;
	}

	/**
	 * Everything its screen (and the computer's ADS-B program) shows: see {@link AdsbPicture#write}. Positions are
	 * relative to where the receiver really is.
	 */
	public CompoundTag picture() {
		double antennaHeight = antenna == null || level == null ? 0.0D : antennaAt.y - level.getSeaLevel();
		int reach = antenna == null ? 0 : (int)Math.round(fullRange());
		int reachLow = antenna == null ? 0 : RadiocraftServerConfig.ADSB_MIN_RANGE.get();
		return AdsbPicture.write(switchedOn, powered, antenna != null, getRange(), reach, reachLow, AdsbMath.feet(antennaHeight),
				centre.x, centre.z, tracks, level == null ? 0L : level.getGameTime());
	}

	public void sendPicture(ServerPlayer player) {
		CAdsbPacket.send(player, worldPosition, picture());
	}

	// -------------------- SAVE, MENU --------------------

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putBoolean("switchedOn", switchedOn);
		tag.putInt("range", rangeIndex);
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.loadAdditional(tag, registries);
		switchedOn = tag.getBoolean("switchedOn");
		rangeIndex = tag.contains("range") ? Math.max(0, Math.min(RANGES.length - 1, tag.getInt("range"))) : DEFAULT_RANGE;
	}

	@Override
	public @NotNull Component getDisplayName() {
		return Component.translatable("container.radiocraft.adsb_receiver");
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
		return new AdsbReceiverMenu(id, this);
	}
}
