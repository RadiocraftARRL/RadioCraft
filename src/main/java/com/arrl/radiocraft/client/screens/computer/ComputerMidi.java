package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.computer.MusicSpectrum;
import com.arrl.radiocraft.common.computer.SpeakerFilter;
import com.arrl.radiocraft.common.radio.voice.SpeakerKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The radio computer's sound card for MUSIC (1.12 step 8; reworked in the music player fixes, and on 2026-11-06 to play
 * through Minecraft's own sound engine): plays a MIDI file through Java's built-in software synthesizer (Gervill). One
 * tune at a time, for the whole client, heard only by this player.
 * <p>
 * <b>Rendering.</b> The tune is rendered to PCM once, ahead of the ears, on its own thread
 * ({@link AudioSystem#getAudioInputStream} reads a MIDI file as sound through Gervill, far faster than real time), mixed
 * to mono, measured for the visualizer ({@link MusicSpectrum}, on the clean sound) and kept in a ring of
 * {@link #RING_SECONDS} seconds, filled {@link #AHEAD_SECONDS} ahead of what has been heard. That ring is the cushion
 * against stalls.
 * <p>
 * <b>Playing.</b> Each place the music comes out of (an {@link Outlet}: the computer's own little speaker, or the
 * speakers connected to it) is a positional, mono, streaming {@link SoundInstance} at that place, on the
 * "Jukebox/Note Blocks" slider ({@link SoundSource#RECORDS}), fading linearly to silence at the outlet's range; OpenAL
 * gives it direction and distance, and the game's Master and Records sliders apply as to any sound. Every outlet reads
 * the same rendered ring through its own {@link Reader}, which puts the sound through that speaker's voicing
 * ({@link SpeakerFilter}), so the MIDI is rendered once however many speakers play it. The engine queues four buffers
 * of {@link #BUFFER_SECONDS} each ahead and refills them every client tick, so a stall of the game shorter than about
 * {@code 4 x BUFFER_SECONDS} is not heard; a longer one empties the source, the engine drops the sound, and it is
 * started again where it was ({@link #tick}). MUSIC's own volume ({@link #setVolume}) is the sounds' volume, which the
 * engine applies every tick, so it changes at once.
 * <p>
 * <b>What has been heard</b> (the clock, the bar and the visualizer) is counted on the render thread: from the moment
 * the engine first reads the stream, at real time, stopped while paused, while the game is paused (the engine pauses
 * its sounds then too) and while no sound is playing. The OpenAL source's own offset is not reachable through the
 * game's classes; this clock drifts from it by no more than the device's clock error, and starts within the first
 * buffer's read.
 * <p>
 * Pause stops the sounds and starts new ones at the same place on resume (the rendered sound is still in the ring).
 * All public methods are for the render thread.
 */
public final class ComputerMidi {

	/** Visualizer bars. */
	public static final int BARS = 24;
	/** The visualizer's range, Hz (the speakers pass little outside it). */
	private static final double LOW_HZ = 55.0D, HIGH_HZ = 5500.0D;
	/** Gervill leaves notes ringing past the tune's last event: this many seconds of it are played. */
	private static final double TAIL_SECONDS = 1.5D;
	/** Frames rendered at a time. */
	private static final int CHUNK = 1024;
	/** The rendered ring, and how far ahead of the ears it is kept full, seconds. */
	private static final double RING_SECONDS = 4.0D, AHEAD_SECONDS = 3.0D;
	/** One OpenAL buffer handed to the engine, seconds (it keeps four queued). */
	private static final double BUFFER_SECONDS = 0.4D;
	/** The most outlets played at once (the engine has only a few streaming channels). */
	public static final int MAX_OUTLETS = 4;

	private static final ResourceLocation LOCATION = Radiocraft.id("computer_music");
	private static final SoundEvent EVENT = SoundEvent.createVariableRangeEvent(LOCATION);
	private static final WeighedSoundEvents EVENTS = new WeighedSoundEvents(LOCATION, null);

	private static final ExecutorService THREAD = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "RadioCraft computer MIDI");
		thread.setDaemon(true);
		thread.setPriority(Thread.MAX_PRIORITY); // Audio: it must stay ahead of the ears.
		return thread;
	});

	/**
	 * Where the music comes out: a place, the kind of speaker there (its voicing), and how far it is heard, blocks.
	 */
	public record Outlet(Vec3 pos, SpeakerKind kind, float range) {}

	/** MUSIC's own volume (the sounds' volume, 0-1), on top of the game's sliders. */
	private static float volume = 1.0F;
	/** Bumped on every play, stop or close, so the tune being rendered gives up. */
	private static volatile int generation;
	private static boolean paused;
	@Nullable
	private static Tune tune;
	private static List<Outlet> outlets = List.of();
	@Nullable
	private static volatile MusicSpectrum spectrum;
	@Nullable
	private static volatile String problem;

	private ComputerMidi() {}

	// -------------------- CONTROLS --------------------

	/** Starts {@code midi} from the top out of {@code where} (stopping whatever was playing). */
	public static void play(byte[] midi, List<Outlet> where) {
		stopSounds();
		int mine = ++generation;
		problem = null;
		paused = false;
		outlets = cap(where);
		Tune t = new Tune(mine);
		tune = t;
		THREAD.execute(() -> render(t, midi));
		startSounds(t);
	}

	/** Stops. */
	public static void stop() {
		++generation;
		stopSounds();
		tune = null;
		paused = false;
	}

	/** Pauses (the sounds stop) or carries on from where it was. */
	public static void pause(boolean pause) {
		if(pause == paused)
			return;
		paused = pause;
		Tune t = tune;
		if(t == null)
			return;
		if(pause)
			stopSounds();
		else
			startSounds(t);
	}

	/** Stops (the program has exited or the player logged out). */
	public static void close() {
		stop();
	}

	/**
	 * Where the music comes out from now on: the computer, or the speakers connected to it. A change while it plays
	 * moves it at once (new sounds at the new places, carrying on where it was).
	 */
	public static void setOutlets(List<Outlet> where) {
		List<Outlet> capped = cap(where);
		if(capped.equals(outlets))
			return;
		outlets = capped;
		Tune t = tune;
		if(t != null && !paused && !t.finished) {
			stopSounds();
			startSounds(t);
		}
	}

	/** Where the music comes out now. */
	public static List<Outlet> outlets() {
		return outlets;
	}

	/** MUSIC's own volume, 0-1 (applied to the sounds at once). */
	public static void setVolume(float v) {
		volume = Math.max(0.0F, Math.min(1.0F, v));
	}

	// -------------------- WHAT IT KNOWS --------------------

	/** True while a tune is playing (not paused, not at its end). */
	public static boolean playing() {
		Tune t = tune;
		return t != null && !paused && !t.finished && !t.failed;
	}

	/** How far into the tune it is, 0-1 (0 with nothing loaded). */
	public static double position() {
		Tune t = tune;
		long total = t == null ? 0L : t.total;
		return total <= 0L || total == Long.MAX_VALUE ? 0.0D : Math.min(1.0D, t.heard / total);
	}

	/** Seconds into the tune. */
	public static int seconds() {
		Tune t = tune;
		return t == null ? 0 : (int)(t.heard / t.rate);
	}

	/** True when the tune loaded has played to its end. */
	public static boolean finished() {
		Tune t = tune;
		return t != null && t.finished;
	}

	/** Why the last tune did not play, or null. */
	@Nullable
	public static String problem() {
		return problem;
	}

	/** Visualizer bar {@code bar}'s level (0-1, {@code bar} below {@link #BARS}); falls away while nothing plays. */
	public static float level(int bar) {
		MusicSpectrum s = spectrum;
		if(s == null)
			return 0.0F;
		synchronized(s) {
			if(bar == 0) { // Read once a frame.
				Tune t = tune;
				if(playing() && t != null) {
					advanceClock(t);
					s.played((long)t.heard);
				}
				else
					s.fall(); // Let the bars sink while paused or stopped.
			}
			return s.level(bar);
		}
	}

	// -------------------- EVERY CLIENT TICK --------------------

	/**
	 * Keeps the clock, the sounds' volume and the ring's floor up to date, notices the end of the tune, and starts the
	 * sounds again if the engine dropped them before the end (a stall long enough to empty them, or the sound engine
	 * reloaded). Render thread, once a client tick ({@link ComputerMusic}).
	 */
	public static void tick() {
		Tune t = tune;
		if(t == null || t.generation != generation)
			return;
		advanceClock(t);
		if(paused || t.finished || t.failed)
			return;
		if(t.ended && problem != null) { // It could not be rendered: nothing to play (MUSIC shows why).
			t.failed = true;
			stopSounds();
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if(mc.options.getSoundSourceVolume(SoundSource.MASTER) <= 0.0F || mc.options.getSoundSourceVolume(SoundSource.RECORDS) <= 0.0F) {
			// The game's sliders are down: the engine will not keep the sounds, so the tune goes on in silence, by the
			// clock, and is heard again from where it has got to when they come up.
			if(!t.started) {
				t.startedAt = System.nanoTime();
				t.started = true;
			}
			if(t.ended && t.heard >= t.total) {
				t.finished = true;
				stopSounds();
			}
			return;
		}
		SoundManager sounds = mc.getSoundManager();
		boolean anyActive = false;
		for(MusicSound sound : t.sounds)
			anyActive |= sounds.isActive(sound);
		if(anyActive || t.sounds.isEmpty() && outlets.isEmpty())
			return;
		if(t.ended && t.heard >= t.total - t.rate * BUFFER_SECONDS) { // Played out.
			t.heard = t.total;
			t.finished = true;
			stopSounds();
		}
		else { // Dropped before the end, never let in (no free streaming channel), or the sliders came up: carry on where it was.
			long now = System.currentTimeMillis();
			if(now - t.lastRestart > 10_000L)
				t.restarts = 0;
			t.lastRestart = now;
			if(++t.restarts > 20) { // Twenty times in ten seconds: something is wrong.
				problem = "the sound engine keeps stopping the music";
				t.failed = true;
				stopSounds();
				return;
			}
			stopSounds();
			startSounds(t);
		}
	}

	/** Counts what has been heard since the last look (see the class comment). */
	private static void advanceClock(Tune t) {
		long now = System.nanoTime();
		boolean running = t.started && !paused && !t.finished && !Minecraft.getInstance().isPaused();
		if(running) {
			long from = Math.max(t.clockAt, t.startedAt);
			if(now > from)
				t.heard = Math.min(t.heard + (now - from) * (double)t.rate / 1.0E9D, Math.min(t.written, t.total));
		}
		t.clockAt = now;
		t.floor = (long)t.heard;
	}

	// -------------------- THE SOUNDS --------------------

	private static List<Outlet> cap(List<Outlet> where) {
		return where.size() <= MAX_OUTLETS ? List.copyOf(where) : List.copyOf(where.subList(0, MAX_OUTLETS));
	}

	/** One sound per outlet, each reading the ring from what has been heard. */
	private static void startSounds(Tune t) {
		SoundManager sounds = Minecraft.getInstance().getSoundManager();
		long from = (long)t.heard;
		t.started = false; // The clock waits for the engine to read the new sounds.
		int epoch = ++t.epoch;
		for(Outlet outlet : outlets) {
			MusicSound sound = new MusicSound(t, outlet, from, epoch);
			t.sounds.add(sound);
			sounds.play(sound);
		}
	}

	private static void stopSounds() {
		Tune t = tune;
		if(t == null)
			return;
		if(t.started)
			advanceClock(t);
		SoundManager sounds = Minecraft.getInstance().getSoundManager();
		for(MusicSound sound : t.sounds)
			sounds.stop(sound);
		t.sounds.clear();
	}

	/** A tune: the rendered ring and how far it is rendered (render thread), heard (render thread) and played. */
	private static final class Tune {
		final int generation;
		/** Set by the render thread before anything is read. */
		volatile float rate = 44100.0F;
		volatile long total = Long.MAX_VALUE;
		volatile float[] ring = new float[0];
		/** Frames rendered into the ring so far (written after the samples). */
		volatile long written;
		/** Frames before this may be overwritten: what has been heard (the render thread waits for it). */
		volatile long floor;
		/** Rendered to the end (or failed). */
		volatile boolean ended;
		/** Completed when the first buffers' worth is rendered (or the tune ended), so a sound never starts empty. */
		final CompletableFuture<Void> buffered = new CompletableFuture<>();
		/** The engine has read the current sounds (the clock runs from {@link #startedAt}). */
		volatile boolean started;
		/** Bumped each time the sounds are started, so a stopped sound's last read does not start the clock. */
		volatile int epoch;
		/** It could not be rendered (see {@link #problem}). */
		boolean failed;
		volatile long startedAt;
		// Render thread only.
		double heard;
		long clockAt;
		boolean finished;
		int restarts;
		long lastRestart;
		final List<MusicSound> sounds = new ArrayList<>();

		Tune(int generation) {
			this.generation = generation;
		}
	}

	/** One outlet's sound: where it is, as far as its speaker carries, its stream reading the ring. */
	private static final class MusicSound extends AbstractTickableSoundInstance {
		private final Tune tune;
		private final Outlet outlet;
		private final long from;
		private final int epoch;

		MusicSound(Tune tune, Outlet outlet, long from, int epoch) {
			super(EVENT, SoundSource.RECORDS, RandomSource.create());
			this.tune = tune;
			this.outlet = outlet;
			this.from = from;
			this.epoch = epoch;
			x = outlet.pos().x;
			y = outlet.pos().y;
			z = outlet.pos().z;
			volume = ComputerMidi.volume;
			attenuation = SoundInstance.Attenuation.LINEAR;
			relative = false;
			looping = false;
		}

		@Override
		public WeighedSoundEvents resolve(@NotNull SoundManager manager) {
			// No sounds.json entry: a streamed sound made here, heard as far as the outlet carries.
			sound = new Sound(LOCATION, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, true, false, Math.max(1, Math.round(outlet.range())));
			return EVENTS;
		}

		@Override
		public boolean canStartSilent() {
			return true; // MUSIC turned right down still keeps its place.
		}

		@Override
		public void tick() {
			if(tune.generation != generation)
				stop();
			else
				volume = ComputerMidi.volume;
		}

		@Override
		public @NotNull CompletableFuture<AudioStream> getStream(@NotNull SoundBufferLibrary buffers, @NotNull Sound sound, boolean looping) {
			return tune.buffered.thenApply(done -> new Reader(tune, from, outlet.kind(), epoch));
		}
	}

	/**
	 * One outlet's stream: reads the rendered ring from {@code from} on, through its speaker's voicing, as mono 16-bit
	 * PCM, a {@link #BUFFER_SECONDS} buffer at a time. Sound thread.
	 */
	private static final class Reader implements AudioStream {
		private final Tune tune;
		private final SpeakerFilter filter;
		private final AudioFormat format;
		private final ByteBuffer out;
		private final int epoch;
		private long at;

		Reader(Tune tune, long from, SpeakerKind kind, int epoch) {
			this.tune = tune;
			this.at = from;
			this.epoch = epoch;
			filter = new SpeakerFilter(tune.rate, SpeakerFilter.Voicing.of(kind));
			format = new AudioFormat(tune.rate, 16, 1, true, false);
			out = BufferUtils.createByteBuffer((int)(BUFFER_SECONDS * tune.rate) * 2); // Copied by OpenAL at once: kept and reused.
		}

		@Override
		public @NotNull AudioFormat getFormat() {
			return format;
		}

		@Override
		public ByteBuffer read(int size) {
			if(!tune.started && epoch == tune.epoch) {
				tune.startedAt = System.nanoTime();
				tune.started = true;
			}
			if(tune.generation != generation)
				return null;
			float[] ring = tune.ring;
			int frames = Math.min(size / 2, out.capacity() / 2);
			long ready = Math.min(tune.written, tune.total) - at;
			out.clear();
			if(ready <= 0L) {
				if(tune.ended)
					return null; // The end: the source plays out what it has.
				frames = Math.min(frames, (int)(tune.rate / 20.0F)); // Not rendered yet (hardly ever): a moment of silence keeps it going.
				for(int i = 0; i < frames; i++)
					out.putShort((short)0);
			}
			else {
				frames = (int)Math.min(frames, ready);
				for(int i = 0; i < frames; i++) {
					float sample = filter.process(ring[(int)((at + i) % ring.length)]);
					out.putShort((short)Math.max(-32768, Math.min(32767, Math.round(sample * 32767.0F))));
				}
				at += frames;
			}
			out.flip();
			return out;
		}

		@Override
		public void close() {}
	}

	// -------------------- THE RENDER THREAD --------------------

	private static void render(Tune t, byte[] midi) {
		try {
			long micros = MidiSystem.getSequence(new ByteArrayInputStream(midi)).getMicrosecondLength();
			try(AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(midi))) {
				AudioFormat source = in.getFormat(); // Gervill: 44.1 kHz, 16 bit, stereo, signed, little-endian.
				if(source.getEncoding() != AudioFormat.Encoding.PCM_SIGNED || source.getSampleSizeInBits() != 16 || source.isBigEndian())
					throw new UnsupportedAudioFileException("unexpected synth output " + source);
				int channels = source.getChannels();
				float rate = source.getSampleRate();
				long frames = (long)((micros / 1_000_000.0D + TAIL_SECONDS) * rate);
				if(in.getFrameLength() > 0L)
					frames = Math.min(frames, in.getFrameLength());
				MusicSpectrum s = spectrum;
				if(s == null || rate != t.rate) {
					s = new MusicSpectrum(BARS, rate, LOW_HZ, HIGH_HZ);
					spectrum = s;
				}
				synchronized(s) {
					s.reset();
				}
				float[] ring = new float[(int)(RING_SECONDS * rate)];
				t.rate = rate;
				t.ring = ring;
				t.total = frames;
				long ahead = (long)(AHEAD_SECONDS * rate), firstBuffers = (long)(4.0D * BUFFER_SECONDS * rate);
				byte[] buffer = new byte[CHUNK * source.getFrameSize()];
				float[] mono = new float[CHUNK];
				long written = 0L;
				while(written < frames && t.generation == generation) {
					if(written - t.floor >= ahead) { // Far enough ahead: wait for the ears.
						Thread.sleep(10L);
						continue;
					}
					int want = (int)Math.min(CHUNK, frames - written) * source.getFrameSize();
					int got = in.readNBytes(buffer, 0, want);
					int n = got / source.getFrameSize();
					if(n <= 0)
						break;
					for(int i = 0; i < n; i++) {
						int sum = 0;
						for(int c = 0; c < channels; c++) {
							int pos = (i * channels + c) * 2;
							sum += (short)(buffer[pos] & 0xFF | buffer[pos + 1] << 8);
						}
						float v = sum / (32768.0F * channels);
						mono[i] = v;
						ring[(int)((written + i) % ring.length)] = v;
					}
					synchronized(s) {
						s.feed(mono, n);
					}
					written += n;
					t.written = written;
					if(written >= firstBuffers)
						t.buffered.complete(null);
				}
				t.total = Math.min(frames, written);
			}
		}
		catch(InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		catch(InvalidMidiDataException | UnsupportedAudioFileException | IOException | RuntimeException e) {
			if(t.generation == generation)
				problem = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
			Radiocraft.LOGGER.warn("The radio computer could not play a MIDI file", e);
			t.total = Math.min(t.total, t.written);
		}
		finally {
			t.ended = true;
			t.buffered.complete(null);
		}
	}
}
