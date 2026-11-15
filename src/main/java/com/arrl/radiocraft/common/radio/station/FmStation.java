package com.arrl.radiocraft.common.radio.station;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * An FM broadcast station's mixer (2026-11-14, the maintainer's FM station): what the transmitter puts on the air, a
 * frame at a time, while it is on. Pure: the transmitter's block entity feeds it the mic, the program and the station's
 * spoken items, and sends what it returns ({@code FmBroadcastTransmitterBlockEntity}).
 * <p>
 * <b>Priorities</b> (the maintainer's choices): the live mic over everything: the program, and anything the station is
 * saying, duck under it to {@link #duckLevel} and come back {@link #RELEASE_FRAMES} after the mic lets go. A weather
 * warning waits while the mic is live, then interrupts the program (a tape pauses, a stream is muted) for its tone and
 * announcement. Forecasts and the hourly station ID wait for a break in the program (between tapes) and never cut a tape
 * off; a stream has no breaks, so it is ducked under them instead. Between and under it all a steady carrier: a quiet
 * frame when there is nothing to send, so receivers hear the station, not static.
 * <p>
 * The program goes through a gentle limiter ({@link #limit}) so tapes and streams of any loudness come out even.
 */
public final class FmStation {

	/** Frames of 20 ms. */
	public static final int FRAME = ProgramSource.FRAME;
	/** Frames the program takes to come back up after the mic lets go: 1.5 s. */
	public static final int RELEASE_FRAMES = 75;
	/** Frames to duck down when the mic opens: 0.1 s. */
	public static final int ATTACK_FRAMES = 5;
	/** Mic frames kept waiting at most (the voice thread runs ahead of the tick now and then). */
	private static final int MIC_QUEUE = 12;
	/** Frames of program loss before the tape player takes over a dead stream: 5 s. */
	public static final int DEAD_AIR_FRAMES = 250;
	/** Frames a stream must be back before the station returns to it: 2 s. */
	public static final int BACK_FRAMES = 100;
	/** Where the limiter holds the program's peaks, of full scale. */
	private static final double LIMIT = 0.7D;

	/** What the station is saying, if anything. */
	public enum Speaking { NONE, ALERT, FORECAST, STATION_ID }

	/** Which program the station plays. */
	public enum Program {
		TAPE, STREAM;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Program byName(String name) {
			for(Program p : values())
				if(p.key().equals(name))
					return p;
			return TAPE;
		}
	}

	// Settings (saved).
	private Program program = Program.TAPE;
	/** Minutes between forecasts, 0 for none. */
	private int forecastMinutes = 10;
	private boolean stationIdOn = true;
	/** The program's level under the mic, 0 to 1. */
	private float duckLevel = 0.2F;

	// State.
	private final ConcurrentLinkedQueue<short[]> mic = new ConcurrentLinkedQueue<>();
	private volatile boolean micKeyed;
	/** Frames since the mic last had a frame or was keyed: the duck's release. */
	private int sinceMic = Integer.MAX_VALUE / 2;
	private float gain = 1.0F;
	private double envelope = 0.0D;
	private final ArrayDeque<String> alerts = new ArrayDeque<>();
	private boolean forecastDue, idDue;
	@Nullable
	private short[] speech;
	private int speechAt;
	private Speaking speaking = Speaking.NONE;
	private int deadFrames, backFrames;
	private boolean fallback;
	private String nowPlaying = "";

	/** A frame the station's voice for {@link Speaking} needs: null while it cannot be had yet (try the next frame). */
	public interface Voice {
		@Nullable
		short[] alert(String text);

		@Nullable
		short[] forecast();

		@Nullable
		short[] stationId();
	}

	// -------------------- INPUTS --------------------

	/** A mic frame (any thread): it goes out mixed over the ducked program on the next frame. */
	public void offerMic(short[] pcm) {
		mic.add(pcm);
		while(mic.size() > MIC_QUEUE)
			mic.poll();
	}

	/** Whether the mic is keyed (PTT, the desk mic): the program ducks while it is, even in a pause. */
	public void setMicKeyed(boolean keyed) {
		micKeyed = keyed;
	}

	/** True while the mic is live: keyed, or a frame of it within the release. */
	public boolean micLive() {
		return micKeyed || sinceMic < ATTACK_FRAMES * 2;
	}

	/** A weather warning to relay (from the station's alert decoder): it waits for the mic. */
	public void queueAlert(String text) {
		if(text != null && !text.isBlank() && alerts.size() < 4)
			alerts.add(text);
	}

	/** A forecast is due (every {@link #forecastMinutes}): at the next break. */
	public void forecastDue() {
		if(forecastMinutes > 0)
			forecastDue = true;
	}

	/** The station ID is due (on the hour): at the next break. */
	public void stationIdDue() {
		if(stationIdOn)
			idDue = true;
	}

	// -------------------- THE FRAME --------------------

	/**
	 * The next frame to put on the air. {@code selected} is the program picked ({@link #program}), {@code tape} the tape
	 * player (for the fallback when the selected one is a dead stream), either null if there is none; {@code voice}
	 * makes the station's spoken items.
	 */
	public short[] frame(@Nullable ProgramSource selected, @Nullable ProgramSource tape, Voice voice) {
		short[] fromMic = mic.poll();
		if(fromMic != null || micKeyed)
			sinceMic = 0;
		else if(sinceMic < Integer.MAX_VALUE / 2)
			sinceMic++;
		boolean live = micLive();

		// Which program: the selected one, or the tape player while a stream is dead.
		ProgramSource source = choose(selected, tape);
		boolean stream = source != null && source == selected && program == Program.STREAM && !fallback;

		// Start a spoken item when its time has come and nothing is in the way.
		if(speaking == Speaking.NONE)
			startSpeaking(source, stream, live, voice);

		// The program: paused under a warning (a tape keeps its place; a stream goes on, muted), and under a forecast or
		// ID on a tape (it started at a break: the next tape waits); a stream ducks under a forecast.
		short[] fromProgram = null;
		double programGain = 1.0D;
		if(source != null) {
			boolean pull = switch(speaking) {
				case NONE -> true;
				case ALERT -> stream;
				case FORECAST, STATION_ID -> stream;
			};
			if(pull) {
				fromProgram = source.nextFrame();
				if(speaking == Speaking.ALERT)
					programGain = 0.0D;
				else if(speaking != Speaking.NONE)
					programGain = duckLevel;
			}
			nowPlaying = source.nowPlaying();
		}
		else
			nowPlaying = "";

		// The duck: down fast when the mic opens, back up over the release when it closes.
		float target = live ? duckLevel : sinceMic < RELEASE_FRAMES ? duckLevel + (1.0F - duckLevel) * sinceMic / RELEASE_FRAMES : 1.0F;
		gain = target < gain ? gain + (target - gain) / ATTACK_FRAMES : target; // Down over a few frames; up along the release ramp.

		short[] fromSpeech = null;
		if(speech != null) {
			fromSpeech = new short[FRAME];
			int n = Math.min(FRAME, speech.length - speechAt);
			System.arraycopy(speech, speechAt, fromSpeech, 0, n);
			speechAt += n;
			if(speechAt >= speech.length) {
				speech = null;
				speaking = Speaking.NONE;
			}
		}

		double[] out = new double[FRAME];
		if(fromProgram != null) {
			double[] limited = limit(fromProgram);
			for(int i = 0; i < FRAME; i++)
				out[i] += limited[i] * programGain * gain;
		}
		if(fromSpeech != null)
			for(int i = 0; i < FRAME; i++)
				out[i] += fromSpeech[i] * gain;
		if(fromMic != null)
			for(int i = 0; i < Math.min(FRAME, fromMic.length); i++)
				out[i] += fromMic[i];
		short[] frame = new short[FRAME];
		for(int i = 0; i < FRAME; i++)
			frame[i] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(out[i])));
		return frame; // All zeros is the bare carrier.
	}

	/** The selected program, or the tape player while a selected stream has been dead {@link #DEAD_AIR_FRAMES}. */
	@Nullable
	private ProgramSource choose(@Nullable ProgramSource selected, @Nullable ProgramSource tape) {
		if(program != Program.STREAM || selected == null) {
			fallback = false;
			return selected != null ? selected : program == Program.TAPE ? tape : null;
		}
		if(selected.live()) {
			deadFrames = 0;
			if(fallback && ++backFrames >= BACK_FRAMES)
				fallback = false;
		}
		else {
			backFrames = 0;
			if(++deadFrames >= DEAD_AIR_FRAMES && tape != null)
				fallback = true;
		}
		return fallback ? tape : selected;
	}

	private void startSpeaking(@Nullable ProgramSource source, boolean stream, boolean live, Voice voice) {
		if(live)
			return; // The mic has the air: everything waits (a warning too: the maintainer's choice).
		if(!alerts.isEmpty()) {
			short[] said = voice.alert(alerts.peek());
			if(said != null) {
				alerts.poll();
				say(said, Speaking.ALERT);
			}
			return;
		}
		boolean atBreak = source == null || !source.live() || stream || source.atBreak();
		if(!atBreak)
			return;
		if(idDue) {
			short[] said = voice.stationId();
			if(said != null) {
				idDue = false;
				say(said, Speaking.STATION_ID);
			}
			return;
		}
		if(forecastDue) {
			short[] said = voice.forecast();
			if(said != null) {
				forecastDue = false;
				say(said, Speaking.FORECAST);
			}
		}
	}

	private void say(short[] pcm, Speaking kind) {
		speech = pcm;
		speechAt = 0;
		speaking = pcm.length == 0 ? Speaking.NONE : kind;
		if(pcm.length == 0)
			speech = null;
	}

	/**
	 * A gentle limiter: an envelope that follows the frame's peaks fast and lets go slowly brings loud program down to
	 * {@link #LIMIT} of full scale, and leaves quiet program alone.
	 */
	double[] limit(short[] pcm) {
		double[] out = new double[FRAME];
		for(int i = 0; i < Math.min(FRAME, pcm.length); i++) {
			double x = pcm[i] / 32768.0D, level = Math.abs(x);
			envelope = level > envelope ? envelope + (level - envelope) * 0.2D : envelope * 0.9995D;
			double g = envelope > LIMIT ? LIMIT / envelope : 1.0D;
			out[i] = x * g * 32767.0D;
		}
		return out;
	}

	// -------------------- STATUS --------------------

	public Speaking speaking() {
		return speaking;
	}

	/** Weather warnings waiting for the mic. */
	public int alertsWaiting() {
		return alerts.size();
	}

	public boolean forecastWaiting() {
		return forecastDue;
	}

	public boolean stationIdWaiting() {
		return idDue;
	}

	/** True while the tape player stands in for a dead stream. */
	public boolean onFallback() {
		return fallback;
	}

	public String nowPlaying() {
		return nowPlaying;
	}

	/** The program's gain now (1 open, {@link #duckLevel} under the mic), for tests and the meter. */
	public float duckGain() {
		return gain;
	}

	// -------------------- SETTINGS --------------------

	public Program program() {
		return program;
	}

	public void setProgram(Program program) {
		this.program = program == null ? Program.TAPE : program;
		fallback = false;
		deadFrames = backFrames = 0;
	}

	public int forecastMinutes() {
		return forecastMinutes;
	}

	public void setForecastMinutes(int minutes) {
		forecastMinutes = Math.max(0, Math.min(120, minutes));
		if(forecastMinutes == 0)
			forecastDue = false;
	}

	public boolean stationIdOn() {
		return stationIdOn;
	}

	public void setStationIdOn(boolean on) {
		stationIdOn = on;
		if(!on)
			idDue = false;
	}

	public float duckLevel() {
		return duckLevel;
	}

	public void setDuckLevel(float level) {
		duckLevel = Math.max(0.0F, Math.min(1.0F, level));
	}

	public void save(CompoundTag tag) {
		tag.putString("program", program.key());
		tag.putInt("forecastMinutes", forecastMinutes);
		tag.putBoolean("stationId", stationIdOn);
		tag.putFloat("duck", duckLevel);
	}

	public void load(CompoundTag tag) {
		if(tag.contains("program"))
			program = Program.byName(tag.getString("program"));
		if(tag.contains("forecastMinutes"))
			forecastMinutes = Math.max(0, Math.min(120, tag.getInt("forecastMinutes")));
		if(tag.contains("stationId"))
			stationIdOn = tag.getBoolean("stationId");
		if(tag.contains("duck"))
			duckLevel = Math.max(0.0F, Math.min(1.0F, tag.getFloat("duck")));
	}
}
