package com.arrl.radiocraft.common.radio.satellite;

import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.api.antenna.IAntenna;
import com.arrl.radiocraft.api.antenna.IAntennaType;
import com.arrl.radiocraft.common.computer.PacketBbs;
import com.arrl.radiocraft.common.moderation.ModerationHooks;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.AntennaCWPacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaDataPacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket;
import com.arrl.radiocraft.common.radio.antenna.BareConnectorAntenna;
import com.arrl.radiocraft.common.radio.antenna.data.AntennaData;
import com.arrl.radiocraft.common.radio.antenna.data.RubberDuckyAntennaData;
import com.arrl.radiocraft.common.radio.antenna.networks.AntennaNetworkManager;
import com.arrl.radiocraft.common.radio.antenna.types.DummyLoadAntennaType;
import com.arrl.radiocraft.common.radio.antenna.types.RubberDuckyAntennaType;
import com.arrl.radiocraft.common.radio.data.FileTransfer;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.common.radio.distress.DistressPackets;
import com.arrl.radiocraft.common.radio.satellite.imaging.AptAudio;
import com.arrl.radiocraft.common.radio.satellite.imaging.AptLine;
import com.arrl.radiocraft.common.radio.satellite.imaging.AptLines;
import com.arrl.radiocraft.common.radio.satellite.imaging.AptPainter;
import com.arrl.radiocraft.common.radio.satellite.imaging.AptSwath;
import com.arrl.radiocraft.common.radio.morse.CWBuffer;
import com.arrl.radiocraft.common.radio.terrain.TerrainPropagation;
import com.arrl.radiocraft.common.radio.voice.Afsk;
import com.arrl.radiocraft.common.radio.voice.Ctcss;
import com.arrl.radiocraft.common.radio.voice.RepeaterAudio;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * How satellites hear and send (2026-11-11, {@code doc/SatellitePlan.md}; modes, telemetry, Doppler and eclipses
 * 2026-11-12), at the server level: a satellite is no block, so it hears every transmission at its source, as the
 * Everywhere mode of a Real-World Interface does. The transmitters' source hooks ({@link ModerationHooks#voice},
 * {@link ModerationHooks#data}) call {@link #onVoice} and {@link #onData}.
 * <ul>
 * <li><b>Repeater</b> ({@link SatelliteMode#REPEATER}, a commercial satellite's only mode): voice on a satellite's uplink
 * with its tone, from an antenna (not a bare connector or a dummy load) under open sky while it is up over the
 * transmitter, goes out on its downlink to every antenna under open sky it is up over, at a strength set by its
 * elevation there and the antenna ({@link SatellitePattern}: an eggbeater is best, a vertical fades overhead and
 * loses its uplink right under it). Without the tone it is ignored. After an over it sends its ID text in Morse, at most every
 * {@link #ID_INTERVAL_TICKS}.</li>
 * <li><b>APRS</b> ({@link SatelliteMode#APRS}): a digipeater, simplex on its APRS frequency ({@link Satellite#aprsHz}):
 * an APRS beacon, message or ack heard there from under it goes out again, one hop further, across its whole footprint,
 * {@link SatelliteDelay#DIGIPEAT_TICKS} later (the sender's radio is deaf while it finishes), once per packet.</li>
 * <li><b>Mailbox</b> ({@link SatelliteMode#MAILBOX}): APRS messages to the satellite's name on its uplink or downlink are
 * lines to a Packet BBS on board ({@link Satellite#mailbox}, the BBS command set); the answers come down on its downlink.
 * A message left on one pass is read anywhere on a later one.</li>
 * <li><b>SSTV</b> ({@link SatelliteMode#SSTV}): its owner's picture goes out as SSTV on its downlink every
 * {@link #SSTV_GAP_TICKS} or so while anyone is under it, the way the radio computer's SSTV program sends one (a file
 * header, the tones, the file), so the SSTV program receives and keeps it.</li>
 * <li><b>Telemetry beacon</b> (every satellite, on or off, every mode): a telemetry frame ({@link SatelliteTelemetry})
 * every {@link #TELEMETRY_TICKS} on its telemetry frequency, and its name in Morse every {@link #CW_NAME_TICKS}; eclipsed,
 * only a power-saving frame every {@link #ECLIPSE_TELEMETRY_TICKS}.</li>
 * <li><b>Imaging</b> ({@link SatelliteMode#IMAGING}, commercial satellites only, 2026-11-12): a weather imager like
 * NOAA's APT satellites. While anyone is under it, it scans a line across its ground track every
 * {@link AptSwath#BLOCKS_PER_PIXEL} blocks it moves ({@link AptPainter}: radar over the ground) and sends it on its
 * downlink as an {@link TncPacket.Kind#APT} packet, its pixels kept in {@link AptLines}; and the downlink sounds like
 * APT ({@link AptAudio}). The WXSAT program receives it.</li>
 * <li><b>Distress relay</b> (every satellite, on or off, every mode, even eclipsed): a distress beacon alert (161.975
 * MHz) or a DSC distress burst (channel 70) it hears goes out again on the same frequency to every antenna under open
 * sky in its coverage, unshifted. Each burst is relayed once (by packet id): a copy heard again, from a digipeater or
 * another satellite, is not relayed.</li>
 * </ul>
 * <b>Transit delay</b> (2026-11-12, {@link SatelliteDelay}): repeated voice comes back down {@link SatelliteDelay#VOICE_MS}
 * after it went up (held in order, the stream unbroken), a digipeat and a mailbox answer later still; a geostationary
 * satellite takes half as long; a distress relay goes out at once. A decommissioned satellite ({@link Satellites#working})
 * hears and sends nothing at all.
 * <p>
 * With {@link Satellites#dopplerOn} its signals are heard shifted by {@link SatelliteDoppler} at each station, and it
 * hears theirs shifted the same way. Eclipsed on a flat battery ({@link Satellite#eclipsed}) everything but the distress
 * relay and a rare power-saving telemetry frame is silent.
 * <p>
 * "Open sky" is the terrain's indoors test ({@link TerrainPropagation#isIndoors}: a roof of wood, glass or leaves still
 * counts as open). It is read on the server thread and kept per position for a few seconds; the voice thread uses what
 * is kept, and asks the server thread about a position it has never seen (open until then).
 */
public final class SatelliteRadio {

	/** Strength at the coverage edge and straight overhead. */
	public static final double STRENGTH_LOW = SatellitePattern.LOW, STRENGTH_HIGH = SatellitePattern.HIGH;
	/** Ticks between Morse IDs. */
	public static final int ID_INTERVAL_TICKS = 6000;
	/** Ticks of quiet on the uplink before an over counts as ended. */
	public static final int OVER_GAP_TICKS = 30;
	/** Ticks between telemetry frames. */
	public static final int TELEMETRY_TICKS = 200;
	/** Ticks between telemetry frames while it is eclipsed on a flat battery (power saving). */
	public static final int ECLIPSE_TELEMETRY_TICKS = 1200;
	/** Ticks between its name in Morse on the telemetry frequency. */
	public static final int CW_NAME_TICKS = 1200;
	/** Ticks of quiet after an SSTV picture before the next. */
	public static final int SSTV_GAP_TICKS = 600;
	/** Ticks between the lines of a mailbox answer. */
	public static final int REPLY_SPACING_TICKS = 8;
	/** Most scan lines a satellite sends in one tick (catching up after a lag). */
	private static final int APT_MAX_LINES = 3;
	/** The ground under every imaging satellite, shared (they scan the same world). */
	private static final AptPainter PAINTER = new AptPainter();
	/** How long a sky check is kept, ticks. */
	private static final int SKY_TTL = 100;
	/** How long a relayed burst's id is kept, ticks. */
	private static final int RELAYED_TTL = 2400;
	/** Message numbers remembered per mailbox caller, so a retried message is answered once. */
	private static final int NUMBERS_KEPT = 16;

	private SatelliteRadio() {}

	/** A frame of audio and the voice id it goes out under. */
	private record Frame(short[] pcm, UUID source) {}

	/** A frame of relayed voice held for the transit delay ({@link SatelliteDelay}) until {@code dueMillis}. */
	private record Held(long dueMillis, short[] pcm, @Nullable UUID source) {}

	/** A packet to send at game time {@code due} on {@code hz}. */
	private record Pending(long due, TncPacket packet, float hz) {}

	/** What a satellite is doing on the air. Its queues are the server thread's, apart from {@link #lastRelay}. */
	private static final class State {
		volatile long lastRelay = -1L;
		long lastId = -1L;
		/**
		 * Voice heard on its uplink, held for the transit delay (2026-11-12), oldest first. Both the voice thread (as
		 * frames arrive) and the server thread (the tail of an over) send from it, under its lock, so the frames go out
		 * in order and none twice.
		 */
		final ArrayDeque<Held> held = new ArrayDeque<>();
		final Queue<short[]> idFrames = new ConcurrentLinkedQueue<>();
		final SatelliteAntenna antenna;
		/** The voice ids its telemetry bursts and SSTV tones go out under (data: receivers that mute packet noise may). */
		final UUID beaconVoice, sstvVoice;
		long lastTelemetry = -1L, lastCwName = -1L;
		int seq = 0;
		final Queue<Frame> beaconFrames = new ArrayDeque<>();
		@Nullable
		TncPacket beaconPacket;
		final Queue<short[]> sstvFrames = new ArrayDeque<>();
		@Nullable
		TncPacket sstvEnd;
		long lastSstv = -1L;
		final List<Pending> pending = new ArrayList<>();
		final Map<String, ArrayDeque<String>> numbers = new HashMap<>();
		/** Imaging: the game time (with a fraction) its next scan line is due, or -1 while it is not scanning. */
		double aptNext = -1.0D;
		int aptSeq = 0;
		/** A geostationary imager's frame (2026-11-14): which one, and the row it sends next. */
		int geoFrame = 0, geoRow = 0;
		final AptAudio aptAudio = new AptAudio();
		final UUID aptVoice;

		State(Satellite satellite) {
			antenna = new SatelliteAntenna(satellite);
			beaconVoice = Afsk.markDataSource(UUID.nameUUIDFromBytes(("radiocraft-satellite-beacon:" + satellite.name).getBytes(StandardCharsets.UTF_8)));
			sstvVoice = Afsk.markDataSource(UUID.nameUUIDFromBytes(("radiocraft-satellite-sstv:" + satellite.name).getBytes(StandardCharsets.UTF_8)));
			aptVoice = Afsk.markDataSource(UUID.nameUUIDFromBytes(("radiocraft-satellite-apt:" + satellite.name).getBytes(StandardCharsets.UTF_8)));
		}

		/** Eclipsed: everything but the beacon (which slows down) stops. */
		void hush() {
			synchronized(held) {
				held.clear();
			}
			idFrames.clear();
			sstvFrames.clear();
			sstvEnd = null;
			pending.clear();
			aptNext = -1.0D;
		}
	}

	private record Sky(boolean open, long checkedAt) {}

	private static final Map<String, State> STATES = new ConcurrentHashMap<>();
	private static final Map<Long, Sky> SKY = new ConcurrentHashMap<>();
	private static final Queue<Long> SKY_ASKED = new ConcurrentLinkedQueue<>();
	private static final Map<Long, Long> RELAYED = Collections.synchronizedMap(new LinkedHashMap<>());
	private static final ThreadLocal<Boolean> INSIDE = new ThreadLocal<>();

	/** Server stopped: nothing kept. */
	public static void clear() {
		STATES.clear();
		SKY.clear();
		SKY_ASKED.clear();
		RELAYED.clear();
		AptLines.clear();
		PAINTER.forget();
		SatelliteTracking.clear();
		MoonBounce.clear();
	}

	private static State state(Satellite satellite) {
		State state = STATES.get(satellite.name);
		if(state == null || state.antenna.satellite != satellite) { // A new record under that name (removed and added again).
			state = new State(satellite);
			STATES.put(satellite.name, state);
		}
		return state;
	}

	/** True if {@code antenna} is a satellite's (what it sends never goes up again). */
	public static boolean isSatellite(@Nullable Object antenna) {
		return antenna instanceof SatelliteAntenna;
	}

	/** A strength for a satellite {@code elevation} degrees up, on an antenna with no special pattern. */
	public static double strength(double elevation) {
		return SatellitePattern.OTHER.strength(elevation);
	}

	/** The strength at which {@code antenna} hears a satellite {@code elevation} degrees up ({@link SatellitePattern}). */
	public static double strength(double elevation, @Nullable Object antenna) {
		return pattern(antenna).strength(elevation);
	}

	/** Antenna types with a vertical's null straight up (2026-11-12). */
	private static final Set<net.minecraft.resources.ResourceLocation> VERTICALS = Set.of(
			com.arrl.radiocraft.Radiocraft.id("j_pole"), com.arrl.radiocraft.Radiocraft.id("slim_jim"), com.arrl.radiocraft.Radiocraft.id("ringo"),
			com.arrl.radiocraft.Radiocraft.id("folded_dipole"), com.arrl.radiocraft.Radiocraft.id("quarter_wave_vertical"),
			com.arrl.radiocraft.Radiocraft.id("rubber_ducky"), com.arrl.radiocraft.Radiocraft.id("manpack_whip"),
			com.arrl.radiocraft.Radiocraft.id("lightning_rod"), com.arrl.radiocraft.Radiocraft.id("cb_whip"),
			com.arrl.radiocraft.Radiocraft.id("cb_ground_plane"), com.arrl.radiocraft.Radiocraft.id("five_btv"));

	/** How {@code antenna} hears satellites: the eggbeater, a vertical, or anything else. */
	public static SatellitePattern pattern(@Nullable Object antenna) {
		if(antenna instanceof com.arrl.radiocraft.common.radio.voice.handheld.PlayerRadio radio && radio.yagiAim() != null)
			return SatellitePattern.HANDHELD_YAGI;
		if(!(antenna instanceof IAntenna a))
			return SatellitePattern.OTHER;
		IAntennaType<?> type = a.getType();
		if(type == null)
			return SatellitePattern.OTHER;
		if(type instanceof com.arrl.radiocraft.common.radio.antenna.types.vhf.EggbeaterAntennaType)
			return SatellitePattern.EGGBEATER;
		return VERTICALS.contains(type.getId()) ? SatellitePattern.VERTICAL : SatellitePattern.OTHER;
	}

	/**
	 * How {@code antenna} hears {@code satellite} (2026-11-12): a satellite tracking antenna locked on it is a
	 * {@link SatellitePattern#TRACKER}; one pointed elsewhere (or stowed) hears it as any other antenna does.
	 */
	public static SatellitePattern pattern(@Nullable Object antenna, Satellite satellite) {
		if(antenna instanceof IAntenna a && a.getType() instanceof com.arrl.radiocraft.common.radio.antenna.types.vhf.SatelliteTrackerAntennaType) {
			IAntenna.AntennaPos at = a.getAntennaPos();
			return at != null && SatelliteTracking.lockedOn(at.position().asLong(), satellite.name) ? SatellitePattern.TRACKER : SatellitePattern.OTHER;
		}
		return pattern(antenna);
	}

	/** True while an aurora is over {@code pos}: satellite signals flutter and fade. Any thread. */
	public static boolean aurora(Level level, BlockPos pos) {
		return com.arrl.radiocraft.common.radio.solar.Aurora.affects(level, pos);
	}

	/** True in a radio blackout or a thunderstorm: satellite signals fade. Any thread. */
	public static boolean storm(Level level) {
		if(level.isThundering())
			return true;
		com.arrl.radiocraft.common.radio.solar.SolarEvent.SolarEventInstance event = com.arrl.radiocraft.common.radio.solar.SolarEventManager.getEvent(level);
		return event != null && com.arrl.radiocraft.common.radio.solar.SolarEventManager.isBlackout(event.getEvent());
	}

	/** The strength at which {@code antenna} at {@code pos} hears {@code satellite} {@code elevation} degrees up, space weather included. */
	public static double strength(Level level, Satellite satellite, double elevation, @Nullable Object antenna, BlockPos pos) {
		SatellitePattern pattern = pattern(antenna, satellite);
		double strength = pattern.strength(elevation, aurora(level, pos), storm(level));
		if(pattern == SatellitePattern.HANDHELD_YAGI)
			strength *= yagiAim(level, satellite, (com.arrl.radiocraft.common.radio.voice.handheld.PlayerRadio)antenna, pos);
		else if(antenna instanceof com.arrl.radiocraft.common.radio.voice.handheld.PlayerRadio)
			strength *= SatellitePattern.handheld(pos.getY()); // The handheld's own whip, weaker but high up (2026-11-15).
		return strength;
	}

	/**
	 * How much of the handheld Yagi's beam is on {@code satellite} (2026-11-15, {@link HandheldYagiAim}): 1 while its
	 * holder looks within {@link HandheldYagiAim#FULL_DEGREES} of it, less further off.
	 */
	private static double yagiAim(Level level, Satellite satellite, com.arrl.radiocraft.common.radio.voice.handheld.PlayerRadio radio, BlockPos pos) {
		double[] aim = radio.yagiAim();
		if(aim == null)
			return 1.0D;
		SatelliteOrbit.Look look = satellite.look(pos.getX(), pos.getZ(), level.getGameTime());
		return HandheldYagiAim.aim(HandheldYagiAim.offDegrees(look.azimuth(), look.elevation(), aim[0], aim[1]));
	}

	/** True if {@code satellite} {@code elevation} degrees up hears {@code antenna} at {@code pos}, space weather included. */
	public static boolean uplinks(Level level, Satellite satellite, double elevation, @Nullable Object antenna, BlockPos pos) {
		return heardThrough(satellite, antenna) && strength(level, satellite, elevation, antenna, pos) >= SatellitePattern.UPLINK_FLOOR;
	}

	/**
	 * True if {@code antenna} can work {@code satellite} at all: anything can, but a satellite in stealth mode
	 * ({@link Satellite#stealth}) is so quiet only a tracker locked on it hears it, and only it is heard.
	 */
	public static boolean heardThrough(Satellite satellite, @Nullable Object antenna) {
		return !satellite.stealth() || pattern(antenna, satellite) == SatellitePattern.TRACKER;
	}

	/** True if {@code satellite} is eclipsed on a flat battery now: silent but for the distress relay. Any thread. */
	public static boolean silent(Satellite satellite, ServerLevel overworld, long now) {
		return satellite.eclipsed(now, overworld.getDayTime(), Satellites.eclipsesOn());
	}

	/**
	 * {@code hz} sent by {@code satellite} as heard at {@code x, z} (or sent from there as the satellite hears it) at
	 * {@code now}: shifted by Doppler when that is on.
	 */
	public static float shifted(Satellite satellite, float hz, double x, double z, long now) {
		return Satellites.dopplerOn() ? (float)(hz + satellite.dopplerHz(hz, x, z, now)) : hz;
	}

	/** The packet number a satellite's frame goes out under. */
	private static long newId(ServerLevel level) {
		return level.getRandom().nextLong();
	}

	// -------------------- THE SKY CHECK --------------------

	/** True if there is open sky over {@code pos} in {@code level}: read here on the server thread, kept otherwise. */
	public static boolean openSky(ServerLevel level, BlockPos pos) {
		long key = pos.asLong();
		Sky known = SKY.get(key);
		long now = level.getGameTime();
		if(level.getServer().isSameThread()) {
			if(known == null || now - known.checkedAt() > SKY_TTL) {
				known = new Sky(check(level, pos), now);
				SKY.put(key, known);
			}
			return known.open();
		}
		if(known == null || now - known.checkedAt() > SKY_TTL)
			SKY_ASKED.add(key);
		return known == null || known.open();
	}

	private static boolean check(ServerLevel level, BlockPos pos) {
		if(!level.isLoaded(pos))
			return true; // Nothing loaded to be under (a far probe in a test); a real station's chunk is loaded.
		return !TerrainPropagation.isIndoors(level, pos);
	}

	/** Forgets every sky check (a GameTest built a roof). */
	public static void forgetSky() {
		SKY.clear();
	}

	// -------------------- HEARING --------------------

	/** True if {@code antenna} can reach a satellite at all: a real antenna, not a bare connector or dummy load. */
	private static boolean radiates(Object antenna) {
		if(!(antenna instanceof IAntenna a) || antenna instanceof BareConnectorAntenna || antenna instanceof SatelliteAntenna)
			return false;
		IAntennaType<?> type = a.getType();
		return !(type instanceof DummyLoadAntennaType);
	}

	/** Voice going out from {@code antenna} on {@code hz} with {@code ctcss}: repeated by every repeating satellite whose uplink it is. Any thread. */
	public static void onVoice(@Nullable Object antenna, @Nullable Level level, @Nullable UUID source, short[] pcm, float hz, float ctcss) {
		List<Satellite> all = Satellites.working();
		if(all.isEmpty() || !(level instanceof ServerLevel serverLevel) || level.dimension() != Level.OVERWORLD || INSIDE.get() != null || !radiates(antenna))
			return;
		IAntenna.AntennaPos at = ((IAntenna)antenna).getAntennaPos();
		if(at == null)
			return;
		BlockPos where = at.world();
		long now = serverLevel.getGameTime();
		Boolean sky = null;
		for(Satellite satellite : all) {
			if(!satellite.isOn() || satellite.mode() != SatelliteMode.REPEATER || !Ctcss.opens(satellite.tone(), ctcss))
				continue;
			SatelliteOrbit.Look up = satellite.look(where.getX(), where.getZ(), now);
			if(!up.up() || !uplinks(serverLevel, satellite, up.elevation(), antenna, where))
				continue;
			if(!SatelliteRules.on(shifted(satellite, hz, where.getX(), where.getZ(), now), satellite.uplinkHz) || silent(satellite, serverLevel, now))
				continue;
			if(sky == null)
				sky = openSky(serverLevel, where);
			if(!sky)
				return;
			State state = state(satellite);
			state.lastRelay = now;
			long millis = System.currentTimeMillis();
			synchronized(state.held) {
				state.held.addLast(new Held(SatelliteDelay.voiceDue(millis, satellite.geostationary()), pcm.clone(), source));
			}
			release(serverLevel, satellite, state, millis);
		}
	}

	/**
	 * Sends the voice {@code satellite} has held for its transit delay whose time has come by {@code millis}, in order,
	 * on its downlink with its tone. From the voice thread as frames arrive and from the server thread every tick (the
	 * tail of an over, after the talker stops).
	 */
	private static void release(ServerLevel level, Satellite satellite, State state, long millis) {
		synchronized(state.held) {
			if(state.held.isEmpty() || state.held.peekFirst().dueMillis() > millis)
				return;
			Boolean was = INSIDE.get();
			INSIDE.set(Boolean.TRUE); // What it sends never goes up again.
			try {
				while(!state.held.isEmpty() && state.held.peekFirst().dueMillis() <= millis) {
					Held frame = state.held.pollFirst();
					sendVoice(level, satellite, frame.pcm(), frame.source(), satellite.downlinkHz, satellite.tone());
				}
			}
			finally {
				if(was == null)
					INSIDE.remove();
			}
		}
	}

	/** Frames of voice {@code satellite} is holding for its transit delay (for tests). */
	public static int holding(Satellite satellite) {
		State state = STATES.get(satellite.name);
		if(state == null)
			return 0;
		synchronized(state.held) {
			return state.held.size();
		}
	}

	/** Sends {@code pcm} on {@code hz} with {@code tone} from {@code satellite} to every antenna it is up over, under open sky. */
	private static void sendVoice(ServerLevel level, Satellite satellite, short[] pcm, @Nullable UUID source, float hz, float tone) {
		Band band = Band.getBand(hz);
		de.maxhenkel.voicechat.api.ServerLevel voiceLevel = () -> level;
		SatelliteAntenna self = state(satellite).antenna;
		long now = level.getGameTime();
		for(IAntenna other : listeners()) {
			SatelliteOrbit.Look look = reaches(level, satellite, other, now, false);
			if(look == null)
				continue;
			BlockPos pos = other.getAntennaPos().world();
			AntennaVoicePacket packet = new AntennaVoicePacket(voiceLevel, pcm.clone(), band, shifted(satellite, hz, pos.getX(), pos.getZ(), now),
					strength(level, satellite, look.elevation(), other, pos), self, source, tone);
			packet.setDestination(other);
			other.receiveAudioPacket(packet);
		}
	}

	/** Sends {@code packet} on {@code hz} (Doppler shifted at each station when {@code doppler}) to every antenna {@code satellite} is up over. */
	private static void sendData(ServerLevel level, Satellite satellite, TncPacket packet, float hz, boolean doppler) {
		Band band = Band.getBand(hz);
		SatelliteAntenna self = state(satellite).antenna;
		long now = level.getGameTime();
		for(IAntenna other : listeners()) {
			SatelliteOrbit.Look look = reaches(level, satellite, other, now, packet.kind() == TncPacket.Kind.DISTRESS);
			if(look == null)
				continue;
			BlockPos pos = other.getAntennaPos().world();
			AntennaDataPacket out = new AntennaDataPacket(level, packet, band, doppler ? shifted(satellite, hz, pos.getX(), pos.getZ(), now) : hz,
					strength(level, satellite, look.elevation(), other, pos), self);
			out.setDestination(other);
			other.receiveDataPacket(out);
		}
	}

	/**
	 * What {@code other} sees of {@code satellite}, if it is a station that can hear it now; null if not. A stealth
	 * satellite reaches only trackers locked on it, but for {@code everyone} (a distress relay, made to be heard).
	 */
	@Nullable
	private static SatelliteOrbit.Look reaches(ServerLevel level, Satellite satellite, IAntenna other, long now, boolean everyone) {
		if(!radiates(other) || (!everyone && !heardThrough(satellite, other)))
			return null;
		IAntenna.AntennaPos there = other.getAntennaPos();
		if(there == null || there.level() != level)
			return null;
		BlockPos pos = there.world();
		SatelliteOrbit.Look look = satellite.look(pos.getX(), pos.getZ(), now);
		return look.up() && openSky(level, pos) ? look : null;
	}

	/** True if {@code satellite} is up over {@code where} and hears {@code antenna} there. */
	private static boolean hears(ServerLevel level, Satellite satellite, BlockPos where, long now, Object antenna) {
		SatelliteOrbit.Look look = satellite.look(where.getX(), where.getZ(), now);
		return look.up() && uplinks(level, satellite, look.elevation(), antenna, where);
	}

	/** True if any station could hear {@code satellite} now. */
	private static boolean anyoneUnder(ServerLevel level, Satellite satellite, long now) {
		for(IAntenna other : listeners())
			if(reaches(level, satellite, other, now, false) != null)
				return true;
		return false;
	}

	/** Every antenna on the VHF network, copied under its lock. */
	private static List<IAntenna> listeners() {
		Set<IAntenna> all = AntennaNetworkManager.getNetwork(AntennaNetworkManager.VHF_ID).allAntennas();
		synchronized(all) {
			return new ArrayList<>(all);
		}
	}

	/** True the first time packet {@code id} is relayed (and notes it); false for a copy. */
	private static boolean firstTime(long id, long now) {
		synchronized(RELAYED) {
			RELAYED.values().removeIf(at -> now - at > RELAYED_TTL);
			if(RELAYED.containsKey(id))
				return false;
			RELAYED.put(id, now);
			return true;
		}
	}

	/**
	 * A data packet going out from {@code antenna} on {@code hz}: a distress burst is relayed by the first satellite up
	 * over the sender; an APRS packet is digipeated by an APRS satellite on its frequency; a message to a mailbox
	 * satellite is a line to its BBS. One copy each. Server thread.
	 */
	public static void onData(@Nullable Object antenna, @Nullable Level level, TncPacket packet, float hz) {
		List<Satellite> all = Satellites.working();
		if(all.isEmpty() || !(level instanceof ServerLevel serverLevel) || level.dimension() != Level.OVERWORLD || !radiates(antenna))
			return;
		IAntenna.AntennaPos at = ((IAntenna)antenna).getAntennaPos();
		if(at == null)
			return;
		BlockPos where = at.world();
		switch(packet.kind()) {
			case DISTRESS -> distress(serverLevel, all, where, packet, hz); // Beacons are made to be heard from any angle.
			case MESSAGE -> {
				if(!mailbox(serverLevel, all, where, packet, hz, antenna))
					aprs(serverLevel, all, where, packet, hz, antenna);
			}
			case BEACON, ACK -> aprs(serverLevel, all, where, packet, hz, antenna);
			default -> {}
		}
	}

	private static void distress(ServerLevel level, List<Satellite> all, BlockPos where, TncPacket packet, float hz) {
		if(!(DistressPackets.isDsc(hz) || DistressPackets.isBeacon(hz)) || DistressPackets.alert(packet) == null)
			return;
		long now = level.getGameTime();
		synchronized(RELAYED) {
			RELAYED.values().removeIf(at -> now - at > RELAYED_TTL);
			if(RELAYED.containsKey(packet.id()))
				return; // Relayed already: a copy is never relayed again.
		}
		for(Satellite satellite : all) {
			if(!satellite.look(where.getX(), where.getZ(), now).up())
				continue;
			if(!openSky(level, where) || !firstTime(packet.id(), now))
				return;
			TncPacket copy = new TncPacket(packet.id(), packet.kind(), packet.from(), packet.to(), packet.text(), packet.position(), packet.icon(),
					packet.hops() + 1, packet.item());
			sendData(level, satellite, copy, hz, false); // On the distress channel itself: no Doppler, so every receiver keeps it.
			return;
		}
	}

	/** An APRS packet heard by a satellite in APRS mode on its frequency: digipeated across its footprint, once. */
	private static void aprs(ServerLevel level, List<Satellite> all, BlockPos where, TncPacket packet, float hz, Object antenna) {
		if(packet.hops() >= RadiocraftServerConfig.TNC_DIGIPEAT_MAX_HOPS.get())
			return;
		long now = level.getGameTime();
		for(Satellite satellite : all) {
			if(!satellite.isOn() || satellite.mode() != SatelliteMode.APRS || !hears(level, satellite, where, now, antenna))
				continue;
			if(!SatelliteRules.on(shifted(satellite, hz, where.getX(), where.getZ(), now), satellite.aprsHz()) || silent(satellite, level, now))
				continue;
			if(!openSky(level, where) || !firstTime(packet.id(), now))
				return;
			state(satellite).pending.add(new Pending(now + SatelliteDelay.ticks(SatelliteDelay.Kind.DIGIPEAT, satellite.geostationary()), packet.digipeated(),
					satellite.aprsHz()));
			return;
		}
	}

	/** True if {@code packet} is a message to a satellite's mailbox (answered if it heard it). */
	private static boolean mailbox(ServerLevel level, List<Satellite> all, BlockPos where, TncPacket packet, float hz, Object antenna) {
		long now = level.getGameTime();
		for(Satellite satellite : all) {
			if(satellite.mode() != SatelliteMode.MAILBOX || !packet.to().equalsIgnoreCase(satellite.name))
				continue;
			if(!satellite.isOn() || !hears(level, satellite, where, now, antenna) || silent(satellite, level, now))
				return true; // For it, but it is not listening (or cannot hear this antenna from overhead).
			float heard = shifted(satellite, hz, where.getX(), where.getZ(), now);
			if(!SatelliteRules.on(heard, satellite.uplinkHz) && !SatelliteRules.on(heard, satellite.downlinkHz))
				return true;
			if(openSky(level, where) && firstTime(packet.id(), now))
				serve(level, satellite, packet, now);
			return true;
		}
		return false;
	}

	/** A caller's line for a satellite's mailbox: acked if numbered, answered once, on its downlink. */
	private static void serve(ServerLevel level, Satellite satellite, TncPacket packet, long now) {
		State state = state(satellite);
		String from = packet.from();
		String number = packet.messageNumber();
		long due = now + SatelliteDelay.ticks(SatelliteDelay.Kind.MAILBOX, satellite.geostationary());
		if(!number.isEmpty()) { // Ack every copy: the last ack may have been lost.
			state.pending.add(new Pending(due, TncPacket.ack(newId(level), satellite.name, from, number), satellite.downlinkHz));
			due += REPLY_SPACING_TICKS;
			ArrayDeque<String> seen = state.numbers.computeIfAbsent(PacketBbs.base(from), call -> new ArrayDeque<>());
			if(seen.contains(number))
				return;
			seen.addLast(number);
			while(seen.size() > NUMBERS_KEPT)
				seen.removeFirst();
		}
		PacketBbs box = satellite.mailbox();
		for(String line : box.handle(satellite.name, from, packet.messageBody(), now)) {
			state.pending.add(new Pending(due, TncPacket.message(newId(level), satellite.name, from, line), satellite.downlinkHz));
			due += REPLY_SPACING_TICKS;
		}
		box.takeDownloads(); // No QSL cards on orbit: nothing to send.
		if(box.takeModified())
			Satellites.of(level.getServer()).mailboxChanged();
	}

	// -------------------- ON THE AIR BY ITSELF --------------------

	/** Every tick, in the overworld: sky checks the voice thread asked for, then each satellite's own transmissions. */
	static void tick(ServerLevel level, long now) {
		for(int i = 0; i < 64; i++) {
			Long key = SKY_ASKED.poll();
			if(key == null)
				break;
			Sky known = SKY.get(key);
			if(known == null || now - known.checkedAt() > SKY_TTL)
				SKY.put(key, new Sky(check(level, BlockPos.of(key)), now));
		}
		if(SKY.size() > 4096)
			SKY.values().removeIf(sky -> now - sky.checkedAt() > SKY_TTL * 10L);
		ModerationHooks.Tag was = ModerationHooks.push(ModerationHooks.Tag.SKIP); // Its IDs, beacons and pictures: not logged.
		try {
			for(Satellite satellite : Satellites.working()) {
				State state = state(satellite);
				if(silent(satellite, level, now)) {
					state.hush(); // In the Earth's shadow on a flat battery: only a rare power-saving telemetry frame.
					beacon(level, satellite, state, now, true);
					continue;
				}
				if(satellite.isOn() && satellite.mode() == SatelliteMode.REPEATER)
					release(level, satellite, state, System.currentTimeMillis()); // The tail of an over.
				else
					synchronized(state.held) {
						state.held.clear(); // Switched off or to another mode: what it held is not sent.
					}
				pending(level, satellite, state, now);
				id(level, satellite, state, now);
				beacon(level, satellite, state, now, false);
				sstv(level, satellite, state, now);
				imaging(level, satellite, state, now);
				PacketBbs box = satellite.mailbox;
				if(box != null && now % 200 == 0)
					for(String call : box.expire(now))
						state.numbers.remove(call);
			}
		}
		finally {
			ModerationHooks.pop(was);
		}
		STATES.keySet().removeIf(name -> Satellites.working().stream().noneMatch(s -> s.name.equals(name)));
	}

	private static int holding(State state) {
		synchronized(state.held) {
			return state.held.size();
		}
	}

	/** Frames a tick: 2.5 frames of 20 ms. */
	private static int framesThisTick(long now) {
		return now % 2 == 0 ? 3 : 2;
	}

	/** Digipeats and mailbox answers whose time has come. */
	private static void pending(ServerLevel level, Satellite satellite, State state, long now) {
		if(state.pending.isEmpty())
			return;
		Iterator<Pending> it = state.pending.iterator();
		while(it.hasNext()) {
			Pending p = it.next();
			if(now < p.due())
				continue;
			it.remove();
			sendData(level, satellite, p.packet(), p.hz(), true);
		}
	}

	/** Its ID text in Morse on the downlink after an over (repeater mode). */
	private static void id(ServerLevel level, Satellite satellite, State state, long now) {
		if(state.idFrames.isEmpty() && state.lastRelay >= 0 && now - state.lastRelay > OVER_GAP_TICKS && satellite.isOn() && satellite.mode() == SatelliteMode.REPEATER
				&& holding(state) == 0
				&& !satellite.idText().isEmpty() && (state.lastId < 0 || now - state.lastId >= ID_INTERVAL_TICKS)) {
			state.idFrames.addAll(RepeaterAudio.morse(satellite.idText()));
			state.lastId = now;
		}
		for(int f = 0, n = framesThisTick(now); f < n; f++) {
			short[] frame = state.idFrames.poll();
			if(frame == null)
				break;
			sendVoice(level, satellite, frame, satellite.voiceId, satellite.downlinkHz, satellite.tone());
		}
	}

	/** Its telemetry now ({@code eclipsed}: the power-saving frame). */
	public static SatelliteTelemetry telemetry(Satellite satellite, int seq, ServerLevel overworld, long now, boolean eclipsed) {
		long day = overworld.getDayTime();
		if(eclipsed)
			return SatelliteTelemetry.eclipsed(satellite.seed, seq, now, satellite.daysLeft(System.currentTimeMillis()));
		String mode = satellite.isOn() ? satellite.mode().name() : "OFF";
		return SatelliteTelemetry.at(satellite.seed, seq, now, day, mode, satellite.daysLeft(System.currentTimeMillis()),
				satellite.recovering(now, day, Satellites.eclipsesOn()));
	}

	/** Its name as Morse can send it ({@code RO-3} is {@code RO3}). */
	static String morseName(Satellite satellite) {
		return RepeaterAudio.sanitizeCallsign(satellite.name);
	}

	/**
	 * The telemetry beacon: a data frame every {@link #TELEMETRY_TICKS}, its name in Morse every {@link #CW_NAME_TICKS};
	 * while {@code eclipsed}, only a power-saving frame every {@link #ECLIPSE_TELEMETRY_TICKS}.
	 */
	private static void beacon(ServerLevel level, Satellite satellite, State state, long now, boolean eclipsed) {
		float hz = satellite.telemetryHz();
		if(hz <= 0.0F)
			return;
		if(state.beaconFrames.isEmpty() && state.beaconPacket == null) {
			if(state.lastTelemetry < 0 || now - state.lastTelemetry >= (eclipsed ? ECLIPSE_TELEMETRY_TICKS : TELEMETRY_TICKS)) {
				TncPacket frame = TncPacket.telemetry(newId(level), satellite.name, telemetry(satellite, state.seq++, level, now, eclipsed).encode());
				for(short[] pcm : Afsk.burst(frame.airBytes(), frame.id()))
					state.beaconFrames.add(new Frame(pcm, state.beaconVoice));
				state.beaconPacket = frame;
				state.lastTelemetry = now;
			}
			else if(!eclipsed && (state.lastCwName < 0 || now - state.lastCwName >= CW_NAME_TICKS)) {
				for(short[] pcm : RepeaterAudio.morse(morseName(satellite)))
					state.beaconFrames.add(new Frame(pcm, satellite.voiceId));
				state.lastCwName = now;
			}
		}
		for(int f = 0, n = framesThisTick(now); f < n; f++) {
			Frame frame = state.beaconFrames.poll();
			if(frame == null)
				break;
			sendVoice(level, satellite, frame.pcm(), frame.source(), hz, Ctcss.NONE);
		}
		if(state.beaconFrames.isEmpty() && state.beaconPacket != null) {
			sendData(level, satellite, state.beaconPacket, hz, true);
			state.beaconPacket = null;
		}
	}

	/** SSTV mode: its picture on the downlink, a header, the tones and the file, every so often while anyone is under it. */
	private static void sstv(ServerLevel level, Satellite satellite, State state, long now) {
		byte[] image = satellite.image();
		if(!satellite.isOn() || satellite.mode() != SatelliteMode.SSTV || image == null) {
			state.sstvFrames.clear();
			state.sstvEnd = null;
			return;
		}
		if(state.sstvFrames.isEmpty() && state.sstvEnd == null && (state.lastSstv < 0 || now - state.lastSstv >= SSTV_GAP_TICKS)) {
			if(now % 20 != 0 || !anyoneUnder(level, satellite, now))
				return;
			ItemStack item;
			try {
				String name = satellite.imageName();
				int dot = name.indexOf('.');
				item = FileTransfer.image(image, satellite.name + " " + (dot < 0 ? name : name.substring(0, dot)), FileTransfer.MODE_SSTV);
			}
			catch(IllegalArgumentException e) {
				return; // Not a picture after all.
			}
			TncPacket file = TncPacket.file(newId(level), satellite.name, "", item);
			sendData(level, satellite, file.header(), satellite.downlinkHz, true); // Receivers learn a picture is coming.
			state.sstvFrames.addAll(FileTransfer.audio(item, level, file.id()));
			state.sstvEnd = file;
		}
		for(int f = 0, n = framesThisTick(now); f < n; f++) {
			short[] frame = state.sstvFrames.poll();
			if(frame == null)
				break;
			sendVoice(level, satellite, frame, state.sstvVoice, satellite.downlinkHz, satellite.tone());
		}
		if(state.sstvFrames.isEmpty() && state.sstvEnd != null) {
			sendData(level, satellite, state.sstvEnd, satellite.downlinkHz, true);
			state.sstvEnd = null;
			state.lastSstv = now;
		}
	}

	/** A station under an imaging satellite: its antenna and what it sees of the satellite. */
	private record Under(IAntenna antenna, SatelliteOrbit.Look look) {}

	/**
	 * Imaging mode: scan lines due by now go down to every station under it, each copy of the satellite (they are
	 * {@link SatelliteOrbit#TILE} apart) scanning its own ground; and the APT sound on the downlink. Nothing while
	 * nobody is under it.
	 */
	private static void imaging(ServerLevel level, Satellite satellite, State state, long now) {
		if(!satellite.isOn() || satellite.mode() != SatelliteMode.IMAGING) {
			state.aptNext = -1.0D;
			return;
		}
		Map<Long, List<Under>> copies = new LinkedHashMap<>(); // Stations by the copy of the satellite they see.
		for(IAntenna other : listeners()) {
			SatelliteOrbit.Look look = reaches(level, satellite, other, now, false);
			if(look != null)
				copies.computeIfAbsent(Math.round(look.subX() / 4096.0D) * 1_000_003L + Math.round(look.subZ() / 4096.0D), k -> new ArrayList<>())
						.add(new Under(other, look));
		}
		if(copies.isEmpty()) {
			state.aptNext = -1.0D;
			return;
		}
		if(state.aptNext < 0.0D || state.aptNext < now - 40.0D)
			state.aptNext = now;
		Band band = Band.getBand(satellite.downlinkHz);
		SatelliteAntenna self = state.antenna;
		boolean geoFrames = satellite.geo != null && !satellite.pinned(now);
		for(int n = 0; n < APT_MAX_LINES && state.aptNext <= now; n++) {
			double t = state.aptNext;
			double period = AptSwath.lineTicks(AptSwath.DEFAULT_SPEED);
			boolean first = true;
			if(geoFrames) { // A geostationary imager sends frames: a square grid, north row first, then an end-of-frame.
				geoLine(level, satellite, state, copies.values(), band, self, now);
				state.aptSeq++;
				state.aptNext = t + period + (state.geoRow == 0 ? GEO_FRAME_PAUSE : 0.0D);
				continue;
			}
			for(List<Under> group : copies.values()) {
				BlockPos ref = group.getFirst().antenna().getAntennaPos().world();
				SatelliteOrbit.Look here = satellite.lookAt(ref.getX(), ref.getZ(), t), next = satellite.lookAt(ref.getX(), ref.getZ(), t + 1.0D);
				double dx = next.subX() - here.subX(), dz = next.subZ() - here.subZ();
				double speed = Math.hypot(dx, dz);
				if(speed < 0.01D || speed > 1000.0D) { // Held still by an admin, or wrapping round the world: along its heading.
					dx = Math.cos(satellite.orbit.heading());
					dz = Math.sin(satellite.orbit.heading());
					speed = AptSwath.DEFAULT_SPEED;
				}
				double cx = here.subX(), cz = here.subZ();
				double[] right = AptSwath.right(dx, dz);
				if(first)
					period = AptSwath.lineTicks(speed);
				byte[] pixels = PAINTER.line(level, cx, cz, right[0], right[1], now);
				AptLine.Geometry geometry = new AptLine.Geometry(state.aptSeq, (int)Math.round(cx), (int)Math.round(cz), right[0], right[1]);
				TncPacket packet = TncPacket.apt(newId(level), satellite.name, geometry.encode());
				AptLines.put(packet.id(), pixels, now);
				if(first)
					state.aptAudio.line(pixels);
				first = false;
				for(Under under : group) {
					BlockPos pos = under.antenna().getAntennaPos().world();
					AntennaDataPacket out = new AntennaDataPacket(level, packet, band, shifted(satellite, satellite.downlinkHz, pos.getX(), pos.getZ(), now),
							strength(level, satellite, under.look().elevation(), under.antenna(), pos), self);
					out.setDestination(under.antenna());
					under.antenna().receiveDataPacket(out);
				}
			}
			state.aptSeq++;
			state.aptNext = t + period;
		}
		if(state.aptNext <= now) // Still behind after the most a tick sends: skip ahead rather than flood.
			state.aptNext = now + 1.0D;
		for(int f = 0, n = framesThisTick(now); f < n; f++)
			sendVoice(level, satellite, state.aptAudio.frame(), state.aptVoice, satellite.downlinkHz, satellite.tone());
	}

	/** Ticks a geostationary imager rests between frames, after its end-of-frame signal. */
	static final double GEO_FRAME_PAUSE = 100.0D;

	/**
	 * A geostationary imager's next line (2026-11-14, the maintainer: "a wider scan that repeatedly transmits a large
	 * grid, then an end signal, and then re-transmits it", as GOES full-disc frames): row {@code geoRow} of a square grid
	 * {@link AptSwath#SWATH_BLOCKS} across centred where it points, the north row first, each row west to east, the
	 * weather read fresh for it. After the last row it sends the end of the frame (a line with no pixels, row = rows) and
	 * starts the next frame from the top.
	 */
	private static void geoLine(ServerLevel level, Satellite satellite, State state, java.util.Collection<List<Under>> copies, Band band, SatelliteAntenna self, long now) {
		int rows = AptSwath.PIXELS;
		double half = AptSwath.SWATH_BLOCKS / 2.0D;
		boolean end = state.geoRow >= rows;
		double cx = satellite.geo.aimX(), cz = satellite.geo.aimZ() - half + (Math.min(state.geoRow, rows - 1) + 0.5D) * AptSwath.BLOCKS_PER_PIXEL;
		AptLine.Geometry geometry = new AptLine.Geometry(state.aptSeq, (int)Math.round(cx), (int)Math.round(cz), 1.0D, 0.0D, state.geoFrame, state.geoRow, rows);
		TncPacket packet = TncPacket.apt(newId(level), satellite.name, geometry.encode());
		if(!end) {
			byte[] pixels = PAINTER.line(level, cx, cz, 1.0D, 0.0D, now); // Right is east: pixel 0 on the west, north up.
			AptLines.put(packet.id(), pixels, now);
			state.aptAudio.line(pixels);
		}
		for(List<Under> group : copies)
			for(Under under : group) {
				BlockPos pos = under.antenna().getAntennaPos().world();
				AntennaDataPacket out = new AntennaDataPacket(level, packet, band, shifted(satellite, satellite.downlinkHz, pos.getX(), pos.getZ(), now),
						strength(level, satellite, under.look().elevation(), under.antenna(), pos), self);
				out.setDestination(under.antenna());
				under.antenna().receiveDataPacket(out);
			}
		if(end) {
			state.geoRow = 0;
			state.geoFrame++;
		}
		else
			state.geoRow++;
	}

	/** Moves a geostationary imager's scan on to {@code row} of its frame (tests: a whole frame takes minutes). */
	public static void geoSkipTo(Satellite satellite, int row) {
		State state = STATES.get(satellite.name);
		if(state != null)
			state.geoRow = row;
	}

	/** The frame and row a geostationary imager sends next, {frame, row} (for tests). */
	public static int[] geoScan(Satellite satellite) {
		State state = STATES.get(satellite.name);
		return state == null ? new int[] { -1, -1 } : new int[] { state.geoFrame, state.geoRow };
	}

	/** True while {@code satellite} is scanning for someone under it (for tests). */
	public static boolean imaging(Satellite satellite) {
		State state = STATES.get(satellite.name);
		return state != null && state.aptNext >= 0.0D;
	}

	/** True while {@code satellite} is sending an SSTV picture (for tests). */
	public static boolean sendingSstv(Satellite satellite) {
		State state = STATES.get(satellite.name);
		return state != null && state.sstvEnd != null;
	}

	// -------------------- ITS ANTENNA --------------------

	/**
	 * What receivers see as the source of a satellite's signal. It is on no network: it only sends, from
	 * {@link SatelliteRadio}. Its position is the point under the satellite (high above it), for code that asks.
	 */
	public static final class SatelliteAntenna implements IAntenna {

		final Satellite satellite;

		SatelliteAntenna(Satellite satellite) {
			this.satellite = satellite;
		}

		public Satellite satellite() {
			return satellite;
		}

		@Override
		public void transmitAudioPacket(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, Band band, float frequencyHertz, UUID sourcePlayer) {}

		@Override
		public void receiveAudioPacket(AntennaVoicePacket packet) {}

		@Override
		public void transmitCWPacket(ServerLevel level, Collection<CWBuffer> buffers, Band band, float frequencyHertz) {}

		@Override
		public void receiveCWPacket(AntennaCWPacket packet) {}

		@Override
		public AntennaPos getAntennaPos() {
			net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
			if(server == null)
				return null;
			ServerLevel overworld = server.overworld();
			SatelliteOrbit.Look look = satellite.look(0.0D, 0.0D, overworld.getGameTime());
			return new AntennaPos(BlockPos.containing(look.subX(), overworld.getMaxBuildHeight(), look.subZ()), overworld);
		}

		@Override
		public IAntennaType<? extends AntennaData> getType() {
			return new RubberDuckyAntennaType(); // Something harmless for code that asks; nothing about it is used.
		}

		@Override
		public AntennaData getData() {
			return new RubberDuckyAntennaData(0.0D);
		}

		@Override
		public Player getPlayer() {
			return null;
		}
	}
}
