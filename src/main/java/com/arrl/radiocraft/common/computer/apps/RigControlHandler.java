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
import com.arrl.radiocraft.common.computer.ComputerNetwork;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.FrequencyList;
import com.arrl.radiocraft.common.computer.RigScan;
import com.arrl.radiocraft.common.computer.Waterfall;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.BandEdges;
import com.arrl.radiocraft.common.radio.BandScope;
import com.arrl.radiocraft.common.radio.BEVoiceReceiver;
import com.arrl.radiocraft.common.radio.voice.LinkAudio;
import com.arrl.radiocraft.common.radio.voice.RemoteMics;
import com.arrl.radiocraft.common.radio.voice.SpeakerKind;
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
import java.util.Set;
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
 * <p>
 * <b>Remote audio and remote control (2026-11-09).</b> Audio is LOCAL (as before: the rig is heard at its speaker and
 * talked into at its mic) or REMOTE ({@code audio} {remote}): the computer is one more speaker of the rig it drives
 * ({@link RadioBlockEntity#remoteListen}, everyone near it hears it), and while a player holds the PTT here their voice
 * goes into the rig as the hand mic's would ({@link RemoteMics}). A computer may let another on its network
 * ({@link ComputerNetwork}) drive its rig: {@code host} {access, anyone} and {@code code} {code} (owner only), OWNER or
 * ANYONE WITH CODE. The other lists those hosts ({@code hosts}), {@code connect}s {id, code} and from then on every
 * rig request it makes is carried out on the host's rig and the host's state, as if it ran there, until
 * {@code disconnect}. One remote user at a time; a local user at the host can {@code takeover}. The session ends (the
 * PTT let go, the scan stopped) when the remote player closes the screen, the link drops (wire broken, microwave NO
 * SIGNAL), either computer goes, or the host turns remote access off. Over microwave the remote audio is delayed and
 * spoilt with the link's grade ({@link LinkAudio}). The server checks every remote request: the session must be this
 * computer's and this player's, and the host must still be on its network; nothing the client says about positions
 * is used. {@code remote} answers the Remote page; pushes {@code remote} (something changed) and
 * {@code remote_ended} {reason, host}.
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
	/** Longest access code. */
	public static final int MAX_CODE = 12;
	/** How long a computer plays a rig remotely after it last said so, ticks (it says so every second). */
	public static final int LISTEN_TICKS = 50;
	/** Requests a local user at a host may make while a remote user has its rig (they look, they do not touch). */
	private static final Set<String> LOOK_ONLY = Set.of("status", "scope", "leave");
	/** The Remote page's requests: always about this computer, never carried to a host. */
	private static final Set<String> REMOTE_REQUESTS = Set.of("remote", "audio", "host", "code", "hosts", "connect", "disconnect", "takeover");

	/** Host side: who drives this computer's rig from where. */
	public record Session(UUID clientId, BlockPos clientPos, UUID player, String playerName, String clientName) {}

	/** Client side: the host whose rig this computer drives, for {@code player}. */
	public record Remote(UUID hostId, BlockPos hostPos, UUID player, String hostName) {}

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
		// Remote audio and control (2026-11-09). Saved: the audio setting and the host settings.
		boolean remoteAudio;
		boolean hostAccess;
		String accessCode = "";
		boolean anyone;
		// Not saved: sessions end with the server.
		@Nullable UUID pttBy; // Who keyed the PTT, and at which computer (this one, or a remote client).
		@Nullable BlockPos pttFrom;
		/** The program is running: open, or left running with C. Its audio plays only then (not saved: off after a restart). */
		boolean running;
		@Nullable Session session;
		ComputerNetwork.Link sessionLink = ComputerNetwork.Link.WIRE;
		@Nullable Remote remote;
		@Nullable RadioBlockEntity cachedRig;
		long cachedRigAt = -100L;
		@Nullable RadioBlockEntity listening; // The rig this computer plays (REMOTE audio).

		public RigScan scan() {
			return scan;
		}

		public List<FrequencyList.Entry> memories() {
			return memories;
		}

		/** Host side: the remote user, or null. */
		@Nullable
		public Session session() {
			return session;
		}

		/** Client side: the host this computer drives, or null. */
		@Nullable
		public Remote remote() {
			return remote;
		}

		public boolean remoteAudio() {
			return remoteAudio;
		}

		public boolean hostAccess() {
			return hostAccess;
		}

		@Override
		public void save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putLong("selected", selected);
			tag.putInt("squelch", squelch);
			tag.putInt("zoom", zoom);
			tag.putString("memoryFile", memoryFile);
			tag.putBoolean("remoteAudio", remoteAudio);
			tag.putBoolean("hostAccess", hostAccess);
			tag.putString("accessCode", accessCode);
			tag.putBoolean("anyone", anyone);
		}

		@Override
		public void load(CompoundTag tag, HolderLookup.Provider registries) {
			selected = tag.getLong("selected");
			squelch = Math.max(0, Math.min(9, tag.getInt("squelch")));
			zoom = Math.max(0, Math.min(Waterfall.ZOOMS - 1, tag.getInt("zoom")));
			String file = ComputerStorage.normalise(tag.getString("memoryFile"));
			memoryFile = file == null ? DEFAULT_MEMORIES : file;
			remoteAudio = tag.getBoolean("remoteAudio");
			hostAccess = tag.getBoolean("hostAccess");
			accessCode = cleanCode(tag.getString("accessCode"));
			anyone = tag.getBoolean("anyone");
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

	/** {@link #rig}, looked up again at most every two seconds (for the ticks: no bus walk each tick). */
	@Nullable
	static RadioBlockEntity cachedRig(RadioComputerBlockEntity computer, State state, long now) {
		RadioBlockEntity rig = state.cachedRig;
		if(rig == null || rig.isRemoved() || now - state.cachedRigAt > 40L || now < state.cachedRigAt) {
			rig = rig(computer, state);
			state.cachedRig = rig;
			state.cachedRigAt = now;
		}
		return rig;
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
		if(action.equals("leave")) { // The program exits (2026-11-09): its audio stops, and a rig it drove elsewhere is let go.
			state.running = false;
			if(state.remote != null && context.player().getUUID().equals(state.remote.player()))
				endRemote(computer, state, "disconnected");
			stopListening(computer, state);
		}
		else
			state.running = true; // In use: open, or left running with C.
		if(REMOTE_REQUESTS.contains(action)) {
			remoteRequest(context, state, action, data, reply);
			return;
		}
		if(state.remote != null) { // Driving a host's rig: carried out there, if the session still holds.
			RadioComputerBlockEntity host = remoteHost(computer, state);
			if(host != null) {
				if(!context.player().getUUID().equals(state.remote.player()) && !LOOK_ONLY.contains(action)) {
					reply.error("remote_other_user", state.remote.hostName());
					return;
				}
				run(context, host, state(host), computer, action, data, reply);
				return;
			}
			endRemote(computer, state, "link_lost");
			if(!action.equals("status")) {
				reply.error("remote_lost");
				return;
			}
		}
		if(state.session != null && !LOOK_ONLY.contains(action)) { // A remote user has this rig.
			reply.error("rig_remote_in_use", state.session.playerName());
			return;
		}
		run(context, computer, state, computer, action, data, reply);
	}

	/**
	 * Carries out a rig request on {@code computer}'s rig and state: this computer's own, or a host's for a remote
	 * user at {@code from}.
	 */
	private void run(ComputerContext context, RadioComputerBlockEntity computer, State state, RadioComputerBlockEntity from, String action, CompoundTag data,
					 ComputerReply reply) {
		StationBus bus = RadioStation.bus(computer);
		RadioBlockEntity rig = rig(computer, state);
		long now = context.level().getGameTime();
		if(action.equals("status")) {
			reply.ok(withRemote(status(computer, state, bus, rig), context, from));
			return;
		}
		if(action.equals("leave")) { // The program exits: stop what it started (a looker at a driven rig started nothing).
			if(state.session == null || from != computer && context.player().getUUID().equals(state.session.player()))
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
						reply.ok(withRemote(status(computer, state, bus, radio), context, from));
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
				if(action.equals("ptt")) { // Whose PTT, and where: their voice goes in from there with REMOTE audio.
					state.pttBy = state.pttOurs ? context.player().getUUID() : null;
					state.pttFrom = state.pttOurs ? from.getBlockPos().immutable() : null;
					feedMic(computer, state, now);
				}
			}
		}
		reply.ok(withRemote(status(computer, state, bus, rig), context, from));
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
		if(state.remote != null && state.remote.player().equals(context.player().getUUID())) {
			releaseRemotePtt(context.computer(), state); // Up from the screen: the session (and its audio) stays, the PTT goes.
			state.mirror.put(context.player().getUUID(), Long.MAX_VALUE); // And the host's waterfall goes on on this monitor.
		}
		long now = context.level().getGameTime();
		// The screen closed with RIGCTL running: keep the waterfall going on the monitor while this player is near it
		// (2026-11-09: no time limit any more, like the music player left playing).
		if(context.player().getUUID().equals(state.lastViewer) && now - state.lastViewAt <= 20L)
			state.mirror.put(context.player().getUUID(), Long.MAX_VALUE);
		RadioBlockEntity rig = rig(context.computer(), state);
		if(rig != null && state.pttOurs && rig.isPTTDown() && state.session == null) { // Nobody at the keyboard to let go (a remote user's is theirs).
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
		tickRemote(computer, state, gameTime);
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
		// Driving a rig elsewhere (2026-11-09): the waterfall on this monitor is the host's rig's.
		State source = state;
		RadioBlockEntity rig;
		if(state.remote != null) {
			RadioComputerBlockEntity host = remoteHost(computer, state);
			if(host != null && host.existingState(ComputerApps.RIG_CONTROL) instanceof State hostState) {
				source = hostState;
				rig = rig(host, hostState);
			}
			else
				rig = null;
		}
		else
			rig = rig(computer, state);
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
				sample = scope(source, rig, gameTime);
			RadioComputerBlockEntity.push(player, ComputerApps.RIG_CONTROL, "scope", sample);
		}
		if(state.mirror.isEmpty() && rig != null && gameTime - source.lastViewAt > SCOPE_LEASE_TICKS)
			rig.dropScope();
	}

	// -------------------- REMOTE CONTROL AND REMOTE AUDIO (2026-11-09) --------------------

	/** {@code code} as an access code: printable ASCII, trimmed, at most {@link #MAX_CODE} long. */
	public static String cleanCode(String code) {
		if(code == null)
			return "";
		StringBuilder out = new StringBuilder();
		for(int i = 0; i < code.length() && out.length() < MAX_CODE; i++) {
			char c = code.charAt(i);
			if(c > ' ' && c <= '~')
				out.append(c);
		}
		return out.toString();
	}

	/** A computer's name for the lists, "?" for none. */
	private static String nameOf(RadioComputerBlockEntity computer) {
		String name = ComputerNetwork.name(computer);
		return name.isEmpty() ? "?" : name;
	}

	/** The computer at {@code pos} if it is loaded there and still the one with {@code id}, else null (never loads a chunk). */
	@Nullable
	private static RadioComputerBlockEntity computerAt(@Nullable net.minecraft.world.level.Level level, BlockPos pos, UUID id) {
		if(level == null || !level.isLoaded(pos))
			return null;
		return level.getBlockEntity(pos) instanceof RadioComputerBlockEntity computer && !computer.isRemoved() && id.equals(computer.getComputerId()) ? computer : null;
	}

	/**
	 * The host {@code client} drives, if the session still holds: the host is where it was, still runs RIGCTL with
	 * remote access, its session is this computer's and this player's, and it is still on this computer's network with
	 * a link up. Null otherwise.
	 */
	@Nullable
	static RadioComputerBlockEntity remoteHost(RadioComputerBlockEntity client, State state) {
		Remote remote = state.remote;
		if(remote == null)
			return null;
		RadioComputerBlockEntity host = computerAt(client.getLevel(), remote.hostPos(), remote.hostId());
		if(host == null || !host.isInstalled(ComputerApps.RIG_CONTROL) || !(host.existingState(ComputerApps.RIG_CONTROL) instanceof State hostState))
			return null;
		Session session = hostState.session;
		if(!hostState.hostAccess || session == null || !session.clientId().equals(client.getComputerId()) || !session.player().equals(remote.player()))
			return null;
		ComputerNetwork.Link link = ComputerNetwork.link(client, host);
		return link != null && link.up() ? host : null;
	}

	/** The screen at {@code client} closed: the host's PTT, if the remote user had it, lets go. The session goes on. */
	private static void releaseRemotePtt(RadioComputerBlockEntity client, State state) {
		Remote remote = state.remote;
		if(remote == null)
			return;
		RadioComputerBlockEntity host = computerAt(client.getLevel(), remote.hostPos(), remote.hostId());
		if(host == null || !(host.existingState(ComputerApps.RIG_CONTROL) instanceof State hostState) || hostState.session == null
				|| !hostState.session.clientId().equals(client.getComputerId()))
			return;
		RadioBlockEntity rig = rig(host, hostState);
		if(rig != null && hostState.pttOurs && rig.isPTTDown())
			rig.setPTTDown(false);
		hostState.pttOurs = false;
	}

	/** {@code client} stops driving its host: the host's session ends too (if it is still ours). */
	public static void endRemote(RadioComputerBlockEntity client, State state, String reason) {
		Remote remote = state.remote;
		if(remote == null)
			return;
		RadioComputerBlockEntity host = computerAt(client.getLevel(), remote.hostPos(), remote.hostId());
		if(host != null && host.existingState(ComputerApps.RIG_CONTROL) instanceof State hostState && hostState.session != null
				&& hostState.session.clientId().equals(client.getComputerId())) {
			endSession(host, hostState, reason); // Tells this computer too.
			return;
		}
		state.remote = null;
		stopListening(client, state);
		CompoundTag out = new CompoundTag();
		out.putString("reason", reason);
		out.putString("host", remote.hostName());
		client.push(ComputerApps.RIG_CONTROL, "remote_ended", out);
	}

	/**
	 * {@code host}'s remote session ends ({@code reason}: disconnected, taken, closed, link_lost, gone, no_access): as
	 * when a program is left, the PTT goes and the scan stops; the client is told and plays the rig no more.
	 */
	public static void endSession(RadioComputerBlockEntity host, State hostState, String reason) {
		Session session = hostState.session;
		if(session == null)
			return;
		hostState.session = null;
		RadioBlockEntity rig = rig(host, hostState);
		leave(hostState, rig);
		hostState.pttBy = null;
		hostState.pttFrom = null;
		RemoteMics.stop(session.player());
		if(rig != null)
			rig.stopRemoteListen(session.clientPos());
		RadioComputerBlockEntity client = computerAt(host.getLevel(), session.clientPos(), session.clientId());
		if(client != null && client.existingState(ComputerApps.RIG_CONTROL) instanceof State clientState && clientState.remote != null
				&& clientState.remote.hostId().equals(host.getComputerId())) {
			clientState.remote = null;
			stopListening(client, clientState);
			CompoundTag out = new CompoundTag();
			out.putString("reason", reason);
			out.putString("host", nameOf(host));
			client.push(ComputerApps.RIG_CONTROL, "remote_ended", out);
		}
		CompoundTag changed = new CompoundTag();
		changed.putString("reason", reason);
		changed.putString("player", session.playerName());
		host.push(ComputerApps.RIG_CONTROL, "remote", changed);
	}

	/** The Remote page's requests (about this computer, never a host's). */
	private void remoteRequest(ComputerContext context, State state, String action, CompoundTag data, ComputerReply reply) {
		RadioComputerBlockEntity computer = context.computer();
		ServerPlayer player = context.player();
		long now = context.level().getGameTime();
		switch(action) {
			case "remote" -> {}
			case "audio" -> {
				state.remoteAudio = data.getBoolean("remote");
				computer.setChanged();
				listen(computer, state, now);
				if(state.pttBy != null)
					feedMic(computer, state, now);
			}
			case "host", "code" -> {
				if(!computer.mayChangePassword(player)) {
					reply.error("remote_owner_settings");
					return;
				}
				if(action.equals("code"))
					state.accessCode = cleanCode(data.getString("code"));
				else {
					state.hostAccess = data.getBoolean("access");
					state.anyone = data.getBoolean("anyone");
				}
				computer.setChanged();
				if(!state.hostAccess)
					endSession(computer, state, "no_access");
			}
			case "hosts" -> {
				reply.ok(hosts(computer));
				return;
			}
			case "connect" -> {
				String refused = connect(computer, state, player, data.getString("id"), data.getString("code"), now);
				if(refused != null) {
					reply.error(refused.contains("|") ? refused.substring(0, refused.indexOf('|')) : refused,
							refused.contains("|") ? refused.substring(refused.indexOf('|') + 1) : "");
					return;
				}
			}
			case "disconnect" -> {
				if(state.remote == null) {
					reply.error("remote_not_connected");
					return;
				}
				endRemote(computer, state, "disconnected");
			}
			case "takeover" -> {
				if(state.session == null) {
					reply.error("remote_no_session");
					return;
				}
				endSession(computer, state, "taken");
			}
			default -> {
				reply.error("unknown_request");
				return;
			}
		}
		reply.ok(remoteTag(computer, state, player));
	}

	/** The hosts {@code computer} reaches that let others drive their rig: id, name, link, code, owner only, busy, on. */
	static CompoundTag hosts(RadioComputerBlockEntity computer) {
		ListTag list = new ListTag();
		for(ComputerNetwork.Peer peer : ComputerNetwork.peersNow(computer)) { // Asked for: fresh.
			RadioComputerBlockEntity host = peer.computer();
			if(!host.isInstalled(ComputerApps.RIG_CONTROL))
				continue;
			State hostState = state(host);
			if(!hostState.hostAccess)
				continue;
			CompoundTag entry = new CompoundTag();
			entry.putString("id", host.getComputerId().toString());
			entry.putString("name", nameOf(host));
			entry.putString("link", peer.link().label());
			entry.putBoolean("up", peer.link().up());
			entry.putBoolean("code", !hostState.accessCode.isEmpty());
			entry.putBoolean("ownerOnly", !hostState.anyone);
			entry.putBoolean("busy", hostState.session != null && !hostState.session.clientId().equals(computer.getComputerId()));
			entry.putBoolean("on", host.isPowered());
			list.add(entry);
		}
		CompoundTag out = new CompoundTag();
		out.put("hosts", list);
		return out;
	}

	/**
	 * {@code player} at {@code client} connects to the host with id {@code id}, found among the computers the server
	 * finds on the client's network (never a position from the screen). Null if done, else the error key (with
	 * "|arg").
	 */
	@Nullable
	static String connect(RadioComputerBlockEntity client, State state, ServerPlayer player, String id, String code, long now) {
		ComputerNetwork.Peer found = null;
		for(ComputerNetwork.Peer peer : ComputerNetwork.peers(client))
			if(peer.computer().getComputerId().toString().equals(id))
				found = peer;
		if(found == null || !found.link().up())
			return "remote_not_reached";
		RadioComputerBlockEntity host = found.computer();
		if(!host.isInstalled(ComputerApps.RIG_CONTROL))
			return "remote_no_access";
		State hostState = state(host);
		if(!hostState.hostAccess)
			return "remote_no_access";
		if(!host.isPowered())
			return "remote_host_off";
		if(!hostState.anyone && !host.mayChangePassword(player))
			return "remote_owner_only";
		if(!hostState.accessCode.isEmpty()) {
			if(code.isEmpty())
				return "remote_need_code";
			if(!hostState.accessCode.equals(cleanCode(code)))
				return "remote_bad_code";
		}
		Session busy = hostState.session;
		if(busy != null && !(busy.clientId().equals(client.getComputerId()) && busy.player().equals(player.getUUID())))
			return "remote_busy|" + busy.playerName();
		if(state.remote != null && !state.remote.hostId().equals(host.getComputerId()))
			endRemote(client, state, "disconnected");
		leave(state, rig(client, state)); // Its own rig: the PTT and scan let go, as when the program is left.
		state.pttBy = null;
		state.pttFrom = null;
		hostState.session = new Session(client.getComputerId(), client.getBlockPos().immutable(), player.getUUID(), player.getScoreboardName(), nameOf(client));
		hostState.sessionLink = found.link();
		state.remote = new Remote(host.getComputerId(), host.getBlockPos().immutable(), player.getUUID(), nameOf(host));
		listen(client, state, now);
		CompoundTag changed = new CompoundTag();
		changed.putString("player", player.getScoreboardName());
		host.push(ComputerApps.RIG_CONTROL, "remote", changed);
		return null;
	}

	/** The Remote page: the audio, the host settings (the code only for who may change it), the session, the host driven. */
	static CompoundTag remoteTag(RadioComputerBlockEntity computer, State state, ServerPlayer player) {
		CompoundTag out = new CompoundTag();
		boolean owner = computer.mayChangePassword(player);
		out.putBoolean("audio", state.remoteAudio);
		out.putBoolean("access", state.hostAccess);
		out.putBoolean("anyone", state.anyone);
		out.putBoolean("hasCode", !state.accessCode.isEmpty());
		out.putString("code", owner ? state.accessCode : "");
		out.putBoolean("mayConfigure", owner);
		out.putString("myName", ComputerNetwork.name(computer));
		if(state.session != null) {
			out.putString("by", state.session.playerName());
			out.putString("via", state.session.clientName());
			out.putString("viaLink", state.sessionLink.label());
		}
		if(state.remote != null) {
			out.putString("to", state.remote.hostName());
			RadioComputerBlockEntity host = remoteHost(computer, state);
			ComputerNetwork.Link link = host == null ? null : ComputerNetwork.link(computer, host);
			out.putString("toLink", link == null ? "" : link.label());
			out.putBoolean("mine", state.remote.player().equals(player.getUUID()));
		}
		return out;
	}

	/** A status answer with the Remote page under {@code remote}, as {@code from} (the computer asking) sees it. */
	private static CompoundTag withRemote(CompoundTag status, ComputerContext context, RadioComputerBlockEntity from) {
		status.put("remote", remoteTag(from, state(from), context.player()));
		return status;
	}

	/**
	 * Where the PTT's voice goes: while {@code computer}'s PTT is held by a player from a computer set to REMOTE audio
	 * (this one, or the remote client), into the rig over that computer's link ({@link RemoteMics}); otherwise the
	 * rig hears whoever stands at its mics, as before.
	 */
	static void feedMic(RadioComputerBlockEntity computer, State state, long now) {
		UUID by = state.pttBy;
		if(by == null)
			return;
		RadioBlockEntity rig = cachedRig(computer, state, now);
		boolean remoteAudio = false;
		LinkAudio.Impairment impairment = LinkAudio.Impairment.CLEAN;
		if(computer.getBlockPos().equals(state.pttFrom))
			remoteAudio = state.remoteAudio;
		else if(state.session != null && state.session.clientPos().equals(state.pttFrom)
				&& computerAt(computer.getLevel(), state.session.clientPos(), state.session.clientId()) instanceof RadioComputerBlockEntity client
				&& client.existingState(ComputerApps.RIG_CONTROL) instanceof State clientState) {
			remoteAudio = clientState.remoteAudio;
			impairment = state.sessionLink.impairment();
		}
		if(rig != null && state.pttOurs && rig.isPTTDown() && remoteAudio)
			RemoteMics.feed(by, rig, impairment);
		else
			RemoteMics.stop(by);
	}

	/**
	 * With REMOTE audio, {@code computer} plays the rig it drives (its own, or its host's) through its speakers, or at
	 * itself with none, as one more speaker of that rig; renewed every second, it lapses if the computer goes.
	 */
	static void listen(RadioComputerBlockEntity computer, State state, long now) {
		RadioBlockEntity rig = null;
		LinkAudio.Impairment impairment = LinkAudio.Impairment.CLEAN;
		if(state.running && (state.remoteAudio || state.remote != null)) { // Driving a rig elsewhere, its audio is always here.
			if(state.remote != null) {
				RadioComputerBlockEntity host = remoteHost(computer, state);
				if(host != null && host.existingState(ComputerApps.RIG_CONTROL) instanceof State hostState) {
					rig = cachedRig(host, hostState, now);
					ComputerNetwork.Link link = ComputerNetwork.link(computer, host);
					impairment = link == null ? impairment : link.impairment();
				}
			}
			else
				rig = cachedRig(computer, state, now);
		}
		if(state.listening != null && state.listening != rig)
			state.listening.stopRemoteListen(computer.getBlockPos());
		state.listening = rig;
		if(rig == null)
			return;
		List<BEVoiceReceiver.WiredSpeaker> speakers = computer.getSpeakers();
		if(speakers.isEmpty())
			speakers = List.of(new BEVoiceReceiver.WiredSpeaker(computer.getBlockPos(), SpeakerKind.STANDARD));
		List<BEVoiceReceiver.RemoteSpeaker> out = new ArrayList<>();
		for(BEVoiceReceiver.WiredSpeaker speaker : speakers)
			out.add(new BEVoiceReceiver.RemoteSpeaker(speaker, impairment));
		rig.remoteListen(computer.getBlockPos(), out, now + LISTEN_TICKS);
	}

	private static void stopListening(RadioComputerBlockEntity computer, State state) {
		if(state.listening != null)
			state.listening.stopRemoteListen(computer.getBlockPos());
		state.listening = null;
	}

	/**
	 * The remote ticks: a host checks its session once a second (the client still there and driving it, the link up),
	 * a client checks the host still has it; a PTT held with REMOTE audio renews the voice feed and drains what waited
	 * on a microwave link; REMOTE audio renews the speaker. All from caches: no wire walk on these ticks.
	 */
	private static void tickRemote(RadioComputerBlockEntity computer, State state, long gameTime) {
		long phase = gameTime + computer.getBlockPos().asLong();
		if(state.session != null && phase % 20L == 0L)
			checkSession(computer, state);
		if(state.remote != null && phase % 20L == 10L && remoteHost(computer, state) == null)
			endRemote(computer, state, "link_lost");
		if(state.pttBy != null) {
			if(!state.pttOurs) { // Let go (time-out, leave, power): the voice no longer goes in.
				RemoteMics.stop(state.pttBy);
				state.pttBy = null;
				state.pttFrom = null;
			}
			else {
				if(phase % 5L == 0L)
					feedMic(computer, state, gameTime);
				if(computer.getLevel() instanceof ServerLevel level)
					RemoteMics.drain(state.pttBy, level);
			}
		}
		if((state.remoteAudio || state.remote != null || state.listening != null) && phase % 20L == 5L)
			listen(computer, state, gameTime);
	}

	/** The host's session still holds: the client is there, still drives this host, and the link is up. */
	private static void checkSession(RadioComputerBlockEntity host, State state) {
		Session session = state.session;
		if(session == null)
			return;
		RadioComputerBlockEntity client = computerAt(host.getLevel(), session.clientPos(), session.clientId());
		if(client == null || !client.isPowered() || !client.isInstalled(ComputerApps.RIG_CONTROL) || !(client.existingState(ComputerApps.RIG_CONTROL) instanceof State clientState)
				|| clientState.remote == null || !clientState.remote.hostId().equals(host.getComputerId())) {
			endSession(host, state, "gone");
			return;
		}
		ComputerNetwork.Link link = ComputerNetwork.link(host, client);
		if(link == null || !link.up()) {
			endSession(host, state, "link_lost");
			return;
		}
		state.sessionLink = link;
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
		// On the air the meter shows the voice driving it (2026-11-09), the S-meter otherwise.
		out.putInt("meter", rig.isPTTDown() ? (int)Math.round(rig.getTxModulation() * 15.0D) : rig.getMeterReading());
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
		boolean picked = false;
		for(RadioBlockEntity radio : radios(computer)) {
			CompoundTag entry = new CompoundTag();
			entry.putLong("pos", radio.getBlockPos().asLong());
			entry.putString("name", radio.getBlockState().getBlock().getDescriptionId());
			entry.putDouble("hz", radio.getFrequency());
			entry.putBoolean("on", radio.isPowered());
			radios.add(entry);
			picked |= radio.getBlockPos().asLong() == state.selected;
		}
		out.put("radios", radios);
		// A radio was picked (R) and is still on the bus (2026-11-09): with more than one and none picked, the screen asks.
		out.putBoolean("picked", picked);
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
