package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.StationBus;
import com.arrl.radiocraft.common.blockentities.radio.ChannelDisplay;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioAllBandBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerAppState;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.FrequencyList;
import com.arrl.radiocraft.common.computer.RigScan;
import com.arrl.radiocraft.common.computer.Waterfall;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.BandEdges;
import com.arrl.radiocraft.common.radio.BandScope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The rig control program's server side (computer batch 3B): CAT control of a radio on the computer's station bus,
 * through the radio interface as every radio program goes (a radio wired straight to the computer is not reached). It
 * tunes (steps, typed frequencies, band switch on the all-band radio), sets the mode (SSB/CW, FM on the all-band),
 * switches the power and the PTT, scans a range or the memory channels with squelch-stop ({@link RigScan}), and feeds
 * the waterfall.
 * <p>
 * <b>The waterfall's cost.</b> Nothing is computed for it: the antenna network already works out every signal's
 * strength at each antenna it reaches, on any frequency, and hands it to the radio, which picks its own frequency out.
 * While the waterfall is watched the radio has a {@link BandScope} that taps those packets before the frequency check
 * and keeps the strongest per bin; each {@code scope} request (the screen asks four times a second) drains it into
 * levels and renews its lease ({@link #SCOPE_LEASE_TICKS}). Nobody watching: no scope, one null check per packet. A
 * player who closes the screen with the waterfall in front keeps getting it pushed twice a second while within
 * {@link #MIRROR_RANGE} blocks (the monitor shows it), for up to {@link #MIRROR_TICKS}.
 * <p>
 * Requests: {@code status} (the radios on the bus, the rig, scan, memories), {@code select} {pos}, {@code step}
 * {steps}, {@code tune} {hz}, {@code band} {dir}, {@code mode}, {@code power}, {@code ptt} {on}, {@code scan}
 * {mode range|memory|off, low, high (Hz, range)}, {@code hold}, {@code squelch} {level 0-9}, {@code memories} {file},
 * {@code store} {name}, {@code zoom} {zoom}, {@code scope}, {@code leave}. Pushes {@code scope} to mirror watchers.
 */
public final class RigControlHandler implements ComputerAppHandler {

	/** Ticks a band scope stays on a radio after the last look. */
	public static final int SCOPE_LEASE_TICKS = 40;
	/** Ticks within which a second look at the scope gets the same row (two players at one waterfall). */
	public static final int SHARED_ROW_TICKS = 4;
	/** A player who leaves the screen with the waterfall showing is sent it while this near the computer. */
	public static final double MIRROR_RANGE = 16.0D;
	public static final int MIRROR_TICKS = 12_000;
	/** The PTT lets go after this long held from the keyboard (a transmitter time-out timer). */
	public static final int PTT_TIMEOUT_TICKS = 3 * 60 * 20;
	/** The memory channels: the Frequency Manager's list. */
	public static final String DEFAULT_MEMORIES = "CHANNELS.FRQ";

	/** One computer's rig control. */
	public static final class State implements ComputerAppState {
		long selected = 0L; // The radio picked, by position; 0 = the first on the bus.
		final RigScan scan = new RigScan();
		int squelch = 0;
		int zoom = 0;
		String memoryFile = DEFAULT_MEMORIES;
		final List<FrequencyList.Entry> memories = new ArrayList<>();
		boolean pttOurs;
		long pttAt;
		@Nullable RadioBlockEntity scanning; // The radio being scanned, looked up again now and then.
		long scanningFound;
		final float[] bins = new float[BandScope.BINS];
		byte[] row = new byte[BandScope.BINS];
		long rowAt = -100L;
		@Nullable UUID lastViewer;
		long lastViewAt = -100L;
		final Map<UUID, Long> mirror = new HashMap<>();

		public RigScan scan() {
			return scan;
		}

		public List<FrequencyList.Entry> memories() {
			return memories;
		}

		@Override
		public void save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putLong("selected", selected);
			tag.putInt("squelch", squelch);
			tag.putInt("zoom", zoom);
			tag.putString("memoryFile", memoryFile);
		}

		@Override
		public void load(CompoundTag tag, HolderLookup.Provider registries) {
			selected = tag.getLong("selected");
			squelch = Math.max(0, Math.min(9, tag.getInt("squelch")));
			zoom = Math.max(0, Math.min(Waterfall.ZOOMS - 1, tag.getInt("zoom")));
			String file = ComputerStorage.normalise(tag.getString("memoryFile"));
			memoryFile = file == null ? DEFAULT_MEMORIES : file;
		}
	}

	public static State state(RadioComputerBlockEntity computer) {
		return computer.state(ComputerApps.RIG_CONTROL, State::new);
	}

	// -------------------- FINDING THE RIG --------------------

	/** The radios rig control reaches: those on the radio interfaces of the computer's bus. */
	public static List<RadioBlockEntity> radios(RadioComputerBlockEntity computer) {
		return RadioStation.radios(RadioStation.bus(computer));
	}

	/** The rig: the picked radio if it is still on the bus, else the first one; null if there is none. */
	@Nullable
	public static RadioBlockEntity rig(RadioComputerBlockEntity computer, State state) {
		List<RadioBlockEntity> radios = radios(computer);
		for(RadioBlockEntity radio : radios)
			if(radio.getBlockPos().asLong() == state.selected)
				return radio;
		return radios.isEmpty() ? null : radios.getFirst();
	}

	/** Why the rig cannot be controlled (an error key), or null. */
	@Nullable
	static String cannotControl(StationBus bus, @Nullable RadioBlockEntity rig) {
		if(!bus.hasInterface())
			return "station_no_interface";
		if(rig == null)
			return "rig_no_radio";
		return null;
	}

	// -------------------- REQUESTS --------------------

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		RadioComputerBlockEntity computer = context.computer();
		State state = state(computer);
		StationBus bus = RadioStation.bus(computer);
		RadioBlockEntity rig = rig(computer, state);
		long now = context.level().getGameTime();
		if(action.equals("status")) {
			reply.ok(status(computer, state, bus, rig));
			return;
		}
		if(action.equals("leave")) { // The program exits: stop what it started.
			leave(state, rig);
			state.mirror.remove(context.player().getUUID());
			reply.ok();
			return;
		}
		String problem = cannotControl(bus, rig);
		if(problem != null) {
			reply.error(problem);
			return;
		}
		switch(action) {
			case "select" -> {
				long pos = data.getLong("pos");
				for(RadioBlockEntity radio : radios(computer))
					if(radio.getBlockPos().asLong() == pos) {
						leave(state, rig);
						state.selected = pos;
						computer.setChanged();
						reply.ok(status(computer, state, bus, radio));
						return;
					}
				reply.error("rig_no_radio");
				return;
			}
			case "scope" -> {
				state.lastViewer = context.player().getUUID();
				state.lastViewAt = now;
				state.mirror.remove(context.player().getUUID()); // At the screen again.
				reply.ok(scope(state, rig, now));
				return;
			}
			case "zoom" -> {
				state.zoom = Math.floorMod(data.getInt("zoom"), Waterfall.ZOOMS);
				computer.setChanged();
			}
			case "squelch" -> {
				state.squelch = Math.max(0, Math.min(9, data.getInt("level")));
				computer.setChanged();
			}
			case "memories" -> {
				String file = ComputerStorage.normalise(data.getString("file"));
				if(file == null) {
					reply.error("bad_name");
					return;
				}
				String loaded = loadMemories(computer, state, file);
				if(loaded != null) {
					reply.error(loaded);
					return;
				}
			}
			case "store" -> {
				String stored = store(computer, state, rig, data.getString("name"));
				if(stored != null) {
					reply.error(stored);
					return;
				}
			}
			case "power" -> {
				if(!rig.mayTogglePower(context.player())) {
					reply.error("rig_power_op_only");
					return;
				}
				if(state.pttOurs && rig.isPTTDown())
					rig.setPTTDown(false);
				state.pttOurs = false;
				rig.toggle();
			}
			default -> {
				String refused = control(state, rig, action, data, now);
				if(refused != null) {
					reply.error(refused);
					return;
				}
			}
		}
		reply.ok(status(computer, state, bus, rig));
	}

	/** Tuning, mode, PTT and scanning; null if done, else the error key. */
	@Nullable
	static String control(State state, RadioBlockEntity rig, String action, CompoundTag data, long now) {
		boolean channelized = rig instanceof ChannelDisplay;
		switch(action) {
			case "step" -> {
				if(channelized)
					return "rig_channelized";
				state.scan.stop();
				rig.updateFrequency(Math.max(-1000, Math.min(1000, data.getInt("steps"))));
			}
			case "tune" -> {
				if(channelized)
					return "rig_channelized";
				state.scan.stop();
				if(!tune(rig, (float)data.getDouble("hz")))
					return "rig_out_of_range";
			}
			case "band" -> {
				if(!(rig instanceof HFRadioAllBandBlockEntity allBand))
					return "rig_one_band";
				state.scan.stop();
				int index = allBand.getBandIndex() + (data.getInt("dir") < 0 ? -1 : 1);
				if(index < 0 || index >= HFRadioAllBandBlockEntity.BANDS.size())
					return "rig_no_more_bands";
				allBand.setBandIndex(index);
			}
			case "mode" -> {
				List<String> modes = modes(rig);
				if(modes.size() < 2)
					return "rig_one_mode";
				int at = modes.indexOf(mode(rig));
				setMode(rig, modes.get((at + 1) % modes.size()));
			}
			case "ptt" -> {
				boolean on = data.getBoolean("on");
				if(on && (!rig.hasTransmitter() || !rig.isPowered()))
					return rig.hasTransmitter() ? "station_no_radio" : "rig_receiver";
				if(on && rig.isTransmitInhibited())
					return "rig_out_of_band";
				rig.setPTTDown(on);
				state.pttOurs = on;
				state.pttAt = now;
			}
			case "scan" -> {
				String mode = data.getString("mode");
				if(mode.equals("off")) {
					state.scan.stop();
					return null;
				}
				if(channelized)
					return "rig_channelized";
				float[] channels = mode.equals("memory") ? memoryChannels(state, rig) : rangeChannels(rig, data.getDouble("low"), data.getDouble("high"));
				if(channels.length == 0)
					return mode.equals("memory") ? "rig_no_memories" : "rig_bad_range";
				if(state.pttOurs && rig.isPTTDown()) { // No scanning with the transmitter keyed.
					rig.setPTTDown(false);
					state.pttOurs = false;
				}
				state.scan.start(mode.equals("memory") ? RigScan.Mode.MEMORY : RigScan.Mode.RANGE, channels, rig.getFrequency(), now);
				state.scanning = rig;
				state.scanningFound = now;
			}
			case "hold" -> {
				if(!state.scan.isScanning())
					return "rig_not_scanning";
				state.scan.toggleHold(now);
			}
			default -> {
				return "unknown_request";
			}
		}
		return null;
	}

	/** Tunes {@code rig} to {@code hz}; the all-band radio turns its band knob first if it must. */
	static boolean tune(RadioBlockEntity rig, float hz) {
		if(rig.tuneTo(hz))
			return true;
		if(rig instanceof HFRadioAllBandBlockEntity allBand)
			for(int i = 0; i < HFRadioAllBandBlockEntity.BANDS.size(); i++) {
				Band band = Band.getBand(HFRadioAllBandBlockEntity.BANDS.get(i));
				if(band != null && BandEdges.canTune(band, hz)) {
					allBand.setBandIndex(i);
					return allBand.tuneTo(hz);
				}
			}
		return false;
	}

	/** The modes {@code rig} switches between: SSB and CW on HF (and FM on the all-band); FM only on VHF. */
	public static List<String> modes(RadioBlockEntity rig) {
		if(rig instanceof HFRadioAllBandBlockEntity)
			return List.of("SSB", "CW", "FM");
		if(rig instanceof HFRadioBlockEntity)
			return List.of("SSB", "CW");
		return List.of("FM");
	}

	/** The rig's mode: SSB, CW, FM, or OFF (an HF radio with neither voice nor CW on). */
	public static String mode(RadioBlockEntity rig) {
		if(rig.isFM())
			return "FM";
		if(rig instanceof HFRadioBlockEntity hf) {
			if(hf.getCWEnabled())
				return "CW";
			return rig.getSSBEnabled() ? "SSB" : "OFF";
		}
		return "FM";
	}

	/** What the display shows: USB or LSB for SSB. */
	public static String modeShown(RadioBlockEntity rig) {
		String mode = mode(rig);
		return mode.equals("SSB") ? (rig.isLowerSideband() ? "LSB" : "USB") : mode;
	}

	static void setMode(RadioBlockEntity rig, String mode) {
		switch(mode) {
			case "CW" -> {
				if(rig instanceof HFRadioBlockEntity hf)
					hf.setCWEnabled(true);
			}
			case "FM" -> {
				if(rig instanceof HFRadioAllBandBlockEntity allBand)
					allBand.setFMEnabled(true);
			}
			default -> {
				if(rig instanceof HFRadioAllBandBlockEntity allBand)
					allBand.setFMEnabled(false);
				rig.setSSBEnabled(true);
			}
		}
	}

	/** Every step of the radio from {@code low} to {@code high} (either order), inside what it tunes. */
	static float[] rangeChannels(RadioBlockEntity rig, double low, double high) {
		Band band = rig.getBand();
		if(band == null)
			return new float[0];
		double lo = Math.max(Math.min(low, high), BandEdges.tuneMin(band)), hi = Math.min(Math.max(low, high), BandEdges.tuneMax(band));
		if(!rig.tunesOutOfBand()) {
			lo = Math.max(lo, band.minFrequency());
			hi = Math.min(hi, band.maxFrequency());
		}
		int step = Math.max(1, rig.getFrequencyStep());
		lo = band.minFrequency() + Math.ceil((lo - band.minFrequency()) / step - 1.0E-6D) * step; // On the radio's step grid.
		return RigScan.range(lo, hi, step);
	}

	/** The memory channels {@code rig} can tune to (others are skipped). */
	static float[] memoryChannels(State state, RadioBlockEntity rig) {
		Band band = rig.getBand();
		List<Float> out = new ArrayList<>();
		for(FrequencyList.Entry entry : state.memories) {
			float hz = (float)(entry.rxMhz() * 1_000_000.0D);
			boolean tunes = band != null && (rig.tunesOutOfBand() ? BandEdges.canTune(band, hz) : band.contains(hz));
			if(tunes && !out.contains(hz))
				out.add(hz);
		}
		float[] channels = new float[out.size()];
		for(int i = 0; i < channels.length; i++)
			channels[i] = out.get(i);
		return channels;
	}

	/** Reads the memory channels from {@code file} (a .FRQ); null if done, else the error key. */
	@Nullable
	static String loadMemories(RadioComputerBlockEntity computer, State state, String file) {
		CompoundTag tag = computer.files().readNbt(file);
		if(tag == null)
			return "not_found";
		state.memories.clear();
		state.memories.addAll(FrequencyList.fromTag(tag).entries());
		state.memoryFile = file;
		computer.setChanged();
		return null;
	}

	/** Adds the rig's frequency to the memory file (the Frequency Manager's list); null if done, else the error key. */
	@Nullable
	static String store(RadioComputerBlockEntity computer, State state, RadioBlockEntity rig, String name) {
		CompoundTag tag = computer.files().readNbt(state.memoryFile);
		FrequencyList list = tag == null ? new FrequencyList() : FrequencyList.fromTag(tag);
		double mhz = rig.getFrequency() / 1_000_000.0D;
		String mode = modeShown(rig);
		FrequencyList.Mode listMode = FrequencyList.Mode.parse(mode.equals("OFF") ? "USB" : mode);
		String label = name.isBlank() ? FrequencyList.mhz(mhz) : name;
		if(!list.add(FrequencyList.Entry.simplex(label, mhz, listMode == null ? FrequencyList.Mode.FM : listMode)))
			return "rig_memories_full";
		ComputerStorage.Problem problem = computer.files().writeNbt(state.memoryFile, list.toTag());
		if(problem != null)
			return problem.key();
		state.memories.clear();
		state.memories.addAll(list.entries());
		return null;
	}

	/** The program exits: the PTT it holds is let go, the scan stops, the scope goes. */
	static void leave(State state, @Nullable RadioBlockEntity rig) {
		state.scan.stop();
		if(rig != null && state.pttOurs && rig.isPTTDown())
			rig.setPTTDown(false);
		state.pttOurs = false;
		if(rig != null && state.mirror.isEmpty())
			rig.dropScope();
	}

	@Override
	public void closed(ComputerContext context) {
		State state = state(context.computer());
		long now = context.level().getGameTime();
		// The screen closed with the waterfall in front (it asked just now): keep it going on the monitor for this player.
		if(context.player().getUUID().equals(state.lastViewer) && now - state.lastViewAt <= 20L)
			state.mirror.put(context.player().getUUID(), now + MIRROR_TICKS);
		RadioBlockEntity rig = rig(context.computer(), state);
		if(rig != null && state.pttOurs && rig.isPTTDown()) { // Nobody at the keyboard to let go.
			rig.setPTTDown(false);
			state.pttOurs = false;
		}
	}

	// -------------------- THE WATERFALL --------------------

	/** The span the scope watches: the band (its tuning range while the dial is outside it), zoomed round the dial. */
	static double[] span(State state, RadioBlockEntity rig) {
		Band band = rig.getBand();
		if(band == null)
			return new double[] { rig.getFrequency() - 1000.0D, rig.getFrequency() + 1000.0D };
		double lo = band.minFrequency(), hi = band.maxFrequency();
		if(rig.tunesOutOfBand() && !band.contains(rig.getFrequency())) {
			lo = BandEdges.tuneMin(band);
			hi = BandEdges.tuneMax(band);
		}
		return Waterfall.span(state.zoom, rig.getFrequency(), lo, hi);
	}

	/** One waterfall row and the rig's state. A second look within {@link #SHARED_ROW_TICKS} gets the same row. */
	static CompoundTag scope(State state, RadioBlockEntity rig, long now) {
		double[] span = span(state, rig);
		BandScope scope = rig.scope(span[0], span[1], SCOPE_LEASE_TICKS);
		if(now - state.rowAt >= SHARED_ROW_TICKS) {
			scope.drain(state.bins);
			double noise = rig.getNoiseFloor(false);
			byte[] row = new byte[BandScope.BINS];
			for(int i = 0; i < row.length; i++)
				row[i] = (byte)Waterfall.level(state.bins[i], noise);
			state.row = row;
			state.rowAt = now;
		}
		CompoundTag out = rigTag(state, rig);
		out.putByteArray("row", state.row);
		out.putDouble("lo", span[0]);
		out.putDouble("hi", span[1]);
		out.putInt("zoom", state.zoom);
		out.putDouble("noise", rig.getNoiseFloor(false));
		out.putLong("gameTime", now);
		return out;
	}

	// -------------------- TICKING: SCAN, PTT TIME-OUT, MIRROR --------------------

	@Override
	public boolean ticks() {
		return true;
	}

	@Override
	public void tick(RadioComputerBlockEntity computer, long gameTime) {
		if(!(computer.existingState(ComputerApps.RIG_CONTROL) instanceof State state))
			return;
		if(state.scan.isScanning())
			tickScan(computer, state, gameTime);
		if(state.pttOurs && gameTime - state.pttAt > PTT_TIMEOUT_TICKS) {
			RadioBlockEntity rig = rig(computer, state);
			if(rig != null && rig.isPTTDown())
				rig.setPTTDown(false);
			state.pttOurs = false;
		}
		if(!state.mirror.isEmpty() && (gameTime + computer.getBlockPos().asLong()) % 10L == 0L)
			tickMirror(computer, state, gameTime);
	}

	private static void tickScan(RadioComputerBlockEntity computer, State state, long gameTime) {
		RadioBlockEntity radio = state.scanning;
		if(radio == null || radio.isRemoved() || gameTime - state.scanningFound > 40L) { // The bus can change; look again now and then.
			radio = rig(computer, state);
			state.scanning = radio;
			state.scanningFound = gameTime;
		}
		if(radio == null || !radio.isPowered()) {
			if(radio == null)
				state.scan.stop();
			return; // Switched off: the scan waits.
		}
		double squelch = state.squelch / 9.0D;
		boolean heard = radio.getCarrierGameTime() > state.scan.stepAt() && radio.getCarrierStrength() > 0.0D && radio.getCarrierStrength() >= squelch;
		float hz = state.scan.tick(gameTime, heard);
		if(!Float.isNaN(hz))
			radio.tuneTo(hz);
	}

	private static void tickMirror(RadioComputerBlockEntity computer, State state, long gameTime) {
		if(!(computer.getLevel() instanceof ServerLevel level))
			return;
		RadioBlockEntity rig = rig(computer, state);
		BlockPos at = computer.getBlockPos();
		CompoundTag sample = null;
		var watchers = state.mirror.entrySet().iterator();
		while(watchers.hasNext()) {
			Map.Entry<UUID, Long> watcher = watchers.next();
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(watcher.getKey());
			if(rig == null || player == null || player.level() != level || gameTime > watcher.getValue()
					|| player.distanceToSqr(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D) > MIRROR_RANGE * MIRROR_RANGE) {
				watchers.remove();
				continue;
			}
			if(computer.viewers().contains(player))
				continue; // At the screen: it asks for itself.
			if(sample == null)
				sample = scope(state, rig, gameTime);
			RadioComputerBlockEntity.push(player, ComputerApps.RIG_CONTROL, "scope", sample);
		}
		if(state.mirror.isEmpty() && rig != null && gameTime - state.lastViewAt > SCOPE_LEASE_TICKS)
			rig.dropScope();
	}

	// -------------------- FOR THE SCREEN --------------------

	/** The rig: {@code pos}, {@code name} (translation key), frequency, band, mode, power, PTT, meter, and the scan. */
	static CompoundTag rigTag(State state, RadioBlockEntity rig) {
		CompoundTag out = new CompoundTag();
		out.putLong("pos", rig.getBlockPos().asLong());
		out.putString("name", rig.getBlockState().getBlock().getDescriptionId());
		out.putDouble("hz", rig.getFrequency());
		Band band = rig.getBand();
		out.putString("band", band == null ? "" : band.name());
		out.putDouble("bandLo", band == null ? 0.0D : band.minFrequency());
		out.putDouble("bandHi", band == null ? 0.0D : band.maxFrequency());
		out.putString("mode", modeShown(rig));
		out.putBoolean("modes", modes(rig).size() > 1);
		out.putBoolean("allBand", rig instanceof HFRadioAllBandBlockEntity);
		out.putBoolean("channelized", rig instanceof ChannelDisplay);
		out.putBoolean("power", rig.isPowered());
		out.putBoolean("transmitter", rig.hasTransmitter());
		out.putBoolean("ptt", rig.isPTTDown());
		out.putBoolean("inhibited", rig.isTransmitInhibited());
		out.putInt("meter", rig.getMeterReading());
		out.putInt("step", rig.getFrequencyStep());
		out.putString("scan", state.scan.mode().name());
		out.putBoolean("scanStopped", state.scan.isStopped());
		out.putBoolean("scanHeld", state.scan.isHeld());
		out.putInt("squelch", state.squelch);
		return out;
	}

	static CompoundTag status(RadioComputerBlockEntity computer, State state, StationBus bus, @Nullable RadioBlockEntity rig) {
		CompoundTag out = new CompoundTag();
		String problem = cannotControl(bus, rig);
		out.putString("problem", problem == null ? "" : problem);
		out.putString("myCall", bus.callsign());
		ListTag radios = new ListTag();
		for(RadioBlockEntity radio : radios(computer)) {
			CompoundTag entry = new CompoundTag();
			entry.putLong("pos", radio.getBlockPos().asLong());
			entry.putString("name", radio.getBlockState().getBlock().getDescriptionId());
			entry.putDouble("hz", radio.getFrequency());
			radios.add(entry);
		}
		out.put("radios", radios);
		if(rig != null)
			out.put("rig", rigTag(state, rig));
		out.putString("memoryFile", state.memoryFile);
		ListTag memories = new ListTag();
		for(FrequencyList.Entry entry : state.memories)
			memories.add(entry.toTag());
		out.put("memories", memories);
		out.putInt("zoom", state.zoom);
		return out;
	}
}
