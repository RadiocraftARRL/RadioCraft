package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.blocks.HeadlessTncBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.menus.HeadlessTncMenu;
import com.arrl.radiocraft.common.radio.data.AprsIcon;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Headless APRS TNC (2026-11-18, doc/Plan-WeatherNetwork.md): a radio interface and a small APRS station in one
 * box on a radio (touching, or on its data wire), cheaper than an H19 and with no screen of its own. It beacons (with the
 * nearest Local Weather Station's weather when there is one), digipeats if its DIGI switch is on, and keeps the
 * beacons it hears for a Weather Alert Generator on its bus. Next to an H19 it stands in for the radio interface; it
 * does nothing for a radio computer, fax or anything else ({@link StationBus#find}). The ON/OFF rocker switches it off
 * altogether: nothing in or out.
 *
 * <p>Its front panel lamps ({@link #lamps}): DCD (a packet decoded), PTT (its radio sending), STA (a digipeat waiting
 * or a beacon just gone), CON (an H19 joined to it), PWR.
 */
public class HeadlessTncBlockEntity extends RadioInterfaceBlockEntity {

	public static final int DCD = 1, PTT = 2, STA = 4, CON = 8, PWR = 16;
	/** Ticks the DCD and STA lamps stay lit after a packet. */
	public static final int LAMP_TICKS = 20;
	/** Most beacons it keeps, one per callsign. */
	public static final int MAX_BEACONS = 32;
	public static final int SSID_MAX = 15;

	private boolean on = true;
	private boolean beaconEnabled = true;
	private long lastBeacon = -1L;
	private long dcdUntil = -1L, staUntil = -1L;
	private int lamps = 0;
	private final List<AprsTerminalBlockEntity.Heard> beacons = new ArrayList<>();

	public HeadlessTncBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.HEADLESS_TNC.get(), pos, state);
	}

	public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T t) {
		if(t instanceof HeadlessTncBlockEntity tnc && level instanceof ServerLevel serverLevel)
			tnc.serverTick(serverLevel);
	}

	@Override
	protected void serverTick(ServerLevel level) {
		super.serverTick(level);
		long now = level.getGameTime();
		long interval = RadiocraftServerConfig.TNC_BEACON_INTERVAL_SECONDS.get() * 20L;
		if(on && beaconEnabled && (lastBeacon < 0L || now - lastBeacon >= interval || now < lastBeacon) && (now + worldPosition.asLong()) % 20L == 0L)
			beacon();
		if(now % 2L == 0L)
			updateLamps(level, now);
	}

	private void updateLamps(ServerLevel level, long now) {
		int lit = 0;
		if(on) {
			lit |= PWR;
			if(now < dcdUntil)
				lit |= DCD;
			if(getRadios().stream().anyMatch(RadioBlockEntity::isSendingData))
				lit |= PTT;
			if(now < staUntil || isRepeating())
				lit |= STA;
			if(StationBus.find(level, worldPosition).terminals().stream().anyMatch(t -> t instanceof AprsTerminalBlockEntity))
				lit |= CON;
		}
		if(lit != lamps) {
			lamps = lit;
			sync();
			BlockState state = getBlockState();
			if(state.getBlock() instanceof HeadlessTncBlock && (state.getValue(HeadlessTncBlock.ON) != on || state.getValue(HeadlessTncBlock.PTT) != ((lit & PTT) != 0)))
				level.setBlock(worldPosition, state.setValue(HeadlessTncBlock.ON, on).setValue(HeadlessTncBlock.PTT, (lit & PTT) != 0), Block.UPDATE_CLIENTS);
		}
	}

	// -------------------- RADIO --------------------

	@Override
	public boolean transmit(TncPacket packet) {
		return on && super.transmit(packet);
	}

	@Override
	public boolean transmitThrough(TncPacket packet, RadioBlockEntity radio) {
		return on && super.transmitThrough(packet, radio);
	}

	@Override
	public void receive(RadioBlockEntity radio, TncPacket packet) {
		if(!on || level == null)
			return;
		if(packet.kind() == TncPacket.Kind.BEACON || packet.kind() == TncPacket.Kind.MESSAGE || packet.kind() == TncPacket.Kind.ACK)
			dcdUntil = level.getGameTime() + LAMP_TICKS;
		if(packet.kind() == TncPacket.Kind.BEACON && !packet.from().equals(getCallsign()))
			hear(packet, level.getGameTime());
		super.receive(radio, packet);
	}

	/** What it decodes goes to an H19 or a Weather Alert Generator on its bus, nothing else. */
	@Override
	protected boolean deliversTo(StationTerminal terminal) {
		return terminal instanceof AprsTerminalBlockEntity || terminal instanceof WeatherAlertGeneratorBlockEntity;
	}

	private void hear(TncPacket packet, long now) {
		AprsTerminalBlockEntity.Heard before = beacons.stream().filter(h -> h.packet().from().equals(packet.from())).findFirst().orElse(null);
		beacons.remove(before);
		beacons.addFirst(new AprsTerminalBlockEntity.Heard(packet, now, before != null ? before.firstHeard() : now));
		while(beacons.size() > MAX_BEACONS)
			beacons.removeLast();
	}

	/** The beacons it has heard, newest first, one per callsign (for a Weather Alert Generator on its bus). */
	public List<AprsTerminalBlockEntity.Heard> getBeacons() {
		return beacons;
	}

	/**
	 * Beacons its position, with the nearest Local Weather Station's weather when there is one (as the WX symbol), else
	 * as a digipeater or a plain station. False with no callsign, switched off, or no radio on to send it.
	 */
	public boolean beacon() {
		if(level == null || !on || getCallsign().isEmpty())
			return false;
		lastBeacon = level.getGameTime();
		setChanged();
		WeatherStationBlockEntity station = WeatherStationBlockEntity.nearest(level, worldPosition);
		String weather = station == null ? "" : station.aprsWeather();
		long id = level.getRandom().nextLong();
		String comment = "RC-TNC";
		TncPacket packet = weather.isEmpty() ? TncPacket.beacon(id, getCallsign(), worldPosition, comment, (isDigipeatEnabled() ? AprsIcon.DIGI : AprsIcon.HOUSE).name())
				: TncPacket.weatherBeacon(id, getCallsign(), worldPosition, weather, comment);
		boolean sent = transmit(packet);
		if(sent)
			staUntil = level.getGameTime() + LAMP_TICKS;
		return sent;
	}

	// -------------------- SETTINGS --------------------

	public boolean isOn() {
		return on;
	}

	public void setOn(boolean value) {
		on = value;
		setChanged();
		sync();
	}

	public boolean isBeaconEnabled() {
		return beaconEnabled;
	}

	public void setBeaconEnabled(boolean value) {
		beaconEnabled = value;
		if(value)
			lastBeacon = -1L; // Switched on: the first beacon goes soon.
		setChanged();
		sync();
	}

	/** The callsign without its SSID. */
	public String getBaseCall() {
		String call = getCallsign();
		int dash = call.indexOf('-');
		return dash < 0 ? call : call.substring(0, dash);
	}

	/** The SSID (0 for none). */
	public int getSsid() {
		String call = getCallsign();
		int dash = call.indexOf('-');
		if(dash < 0)
			return 0;
		try {
			return Math.max(0, Math.min(SSID_MAX, Integer.parseInt(call.substring(dash + 1))));
		}
		catch(NumberFormatException e) {
			return 0;
		}
	}

	/** Sets the SSID (0 to 15, 0 for none) on the callsign it has. */
	public void setSsid(int ssid, String setBy) {
		String base = getBaseCall();
		if(base.isEmpty())
			return;
		int clamped = Math.max(0, Math.min(SSID_MAX, ssid));
		setCallsign(clamped == 0 ? base : base + "-" + clamped, setBy);
	}

	/** The front panel lamps lit ({@link #DCD}, {@link #PTT}, ...), as last sent to clients. */
	public int getLamps() {
		return lamps;
	}

	// -------------------- MENU & SAVE --------------------

	@Override
	public @NotNull Component getDisplayName() {
		return Component.translatable("container.radiocraft.headless_tnc");
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int id, @NotNull Inventory playerInventory, @NotNull Player player) {
		return new HeadlessTncMenu(id, this);
	}

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putBoolean("on", on);
		tag.putBoolean("beacon", beaconEnabled);
		tag.putInt("lamps", lamps);
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		on = !tag.contains("on") || tag.getBoolean("on");
		beaconEnabled = !tag.contains("beacon") || tag.getBoolean("beacon");
		lamps = tag.getInt("lamps");
	}
}
