package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.computer.MusicSpectrum;
import com.arrl.radiocraft.common.computer.SpeakerFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The radio computer's sound card for MUSIC (1.12 step 8, reworked in the music player fixes): plays a MIDI file on this
 * computer through Java's built-in software synthesizer (Gervill), not through Minecraft's sound engine. One tune at a
 * time, for the whole client.
 * <p>
 * The tune is rendered to PCM as it plays ({@link AudioSystem#getAudioInputStream} reads a MIDI file as sound through
 * Gervill, much faster than real time), then on its own thread: mixed to mono, measured for the visualizer
 * ({@link MusicSpectrum}, on the clean sound), sent through the computer's small speakers ({@link SpeakerFilter}),
 * scaled to the volume and written to a {@link SourceDataLine} of Java's default audio device. Loudness follows the
 * game's sound settings, master times "Jukebox/Note Blocks" ({@link SoundSource#RECORDS}), times the fade with distance
 * ({@link #updateVolume(float)}), applied to every sample with a short ramp, so held notes follow it too.
 * <p>
 * All methods are safe to call from the render thread.
 */
public final class ComputerMidi {

	/** Visualizer bars. */
	public static final int BARS = 24;
	/** The visualizer's range, Hz (the speakers pass little outside it). */
	private static final double LOW_HZ = 55.0D, HIGH_HZ = 5500.0D;
	/** Gervill leaves notes ringing past the tune's last event: this many seconds of it are played. */
	private static final double TAIL_SECONDS = 1.5D;
	/** Frames per chunk written to the line (23 ms at 44.1 kHz). */
	private static final int CHUNK = 1024;
	/**
	 * The line's buffer, seconds: the cushion against stalls. The synth renders far faster than real time, so the line
	 * is kept this far ahead of the ears, and a hitch in the game (a slow frame or tick, a busy CPU) shorter than this
	 * is not heard. (It was 93 ms, and MUSIC skipped whenever the world lagged.) The visualizer is told what has been
	 * heard ({@link MusicSpectrum#played}), so its bars do not run this far ahead of the sound.
	 */
	private static final double LINE_SECONDS = 0.4D;

	private static final ExecutorService THREAD = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "RadioCraft computer MIDI");
		thread.setDaemon(true);
		thread.setPriority(Thread.MAX_PRIORITY); // Audio: a starved line is heard at once.
		return thread;
	});

	/** Linear gain the audio thread ramps to. */
	private static volatile float gain = 1.0F;
	/** Bumped on every play, stop or close, so the tune being played (or started) gives up. */
	private static volatile int generation;
	private static volatile boolean paused;
	/** True from {@link #play} until the tune has started (or failed), so the last tune's end is not taken for this one's. */
	private static volatile boolean starting;
	/** True once the tune loaded has been played to its end. */
	private static volatile boolean finished;
	/** The {@link #generation} the audio thread is playing (or has paused); another number while nothing is loaded. */
	private static volatile int active = -1;
	/** Frames of this tune heard so far, and in all. */
	private static volatile long heardFrames, totalFrames;
	private static volatile float frameRate = 44100.0F;
	@Nullable
	private static volatile MusicSpectrum spectrum;
	@Nullable
	private static volatile String problem;

	private ComputerMidi() {}

	/** Starts {@code midi} from the top (stopping whatever was playing). */
	public static void play(byte[] midi) {
		int mine = ++generation;
		problem = null;
		starting = true;
		finished = false;
		paused = false;
		heardFrames = 0L;
		totalFrames = 0L;
		updateVolume();
		THREAD.execute(() -> run(mine, midi));
	}

	/** Stops. */
	public static void stop() {
		++generation;
		starting = false;
		paused = false;
	}

	/** Pauses or carries on from where it was. */
	public static void pause(boolean pause) {
		paused = pause;
	}

	/** Stops (the program has exited or the player logged out). */
	public static void close() {
		stop();
	}

	/** True while a tune is playing (not paused, not at its end). */
	public static boolean playing() {
		return active == generation && !paused && !finished;
	}

	/** How far into the tune it is, 0-1 (0 with nothing loaded). */
	public static double position() {
		long total = totalFrames;
		return total <= 0L ? 0.0D : Math.min(1.0D, heardFrames / (double)total);
	}

	/** Seconds into the tune. */
	public static int seconds() {
		return (int)(heardFrames / frameRate);
	}

	/** True when the tune loaded has played to its end. */
	public static boolean finished() {
		return !starting && finished;
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
		if(!playing() && bar == 0)
			s.fall(); // Read once a frame: let the bars sink while paused or stopped.
		return s.level(bar);
	}

	/** Reads the game's sound settings (see the class comment). */
	public static void updateVolume() {
		updateVolume(1.0F);
	}

	/** Reads the game's sound settings, times {@code fade} (0-1: the player's distance from the computer, {@link ComputerMusic}). */
	public static void updateVolume(float fade) {
		Minecraft mc = Minecraft.getInstance();
		float g = fade * mc.options.getSoundSourceVolume(SoundSource.MASTER) * mc.options.getSoundSourceVolume(SoundSource.RECORDS);
		gain = Math.max(0.0F, Math.min(1.0F, g));
	}

	// -------------------- THE AUDIO THREAD --------------------

	private static void run(int mine, byte[] midi) {
		SourceDataLine line = null;
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
				if(mine != generation)
					return;
				AudioFormat out = new AudioFormat(rate, 16, 2, true, false);
				line = AudioSystem.getSourceDataLine(out);
				line.open(out, (int)(LINE_SECONDS * rate) * out.getFrameSize());
				MusicSpectrum s = spectrum;
				if(s == null || rate != frameRate) {
					s = new MusicSpectrum(BARS, rate, LOW_HZ, HIGH_HZ);
					spectrum = s;
				}
				s.reset();
				frameRate = rate;
				totalFrames = frames;
				SpeakerFilter speaker = new SpeakerFilter(rate);
				byte[] buffer = new byte[CHUNK * source.getFrameSize()];
				byte[] output = new byte[CHUNK * 4];
				float[] mono = new float[CHUNK];
				float applied = gain;
				long read = 0L;
				active = mine;
				starting = false;
				line.start();
				while(read < frames && mine == generation) {
					if(paused) {
						if(line.isRunning())
							line.stop();
						Thread.sleep(20L);
						continue;
					}
					if(!line.isRunning())
						line.start();
					int want = (int)Math.min(CHUNK, frames - read) * source.getFrameSize();
					int got = in.readNBytes(buffer, 0, want);
					int n = got / source.getFrameSize();
					if(n <= 0)
						break;
					for(int i = 0; i < n; i++) {
						int sum = 0;
						for(int c = 0; c < channels; c++) {
							int at = (i * channels + c) * 2;
							sum += (short)(buffer[at] & 0xFF | buffer[at + 1] << 8);
						}
						mono[i] = sum / (32768.0F * channels);
					}
					s.feed(mono, n);
					float target = gain, step = (target - applied) / n;
					for(int i = 0; i < n; i++) {
						applied += step;
						int v = Math.round(speaker.process(mono[i]) * applied * 32767.0F);
						v = Math.max(-32768, Math.min(32767, v));
						output[i * 4] = output[i * 4 + 2] = (byte)v;
						output[i * 4 + 1] = output[i * 4 + 3] = (byte)(v >> 8);
					}
					applied = target;
					line.write(output, 0, n * 4);
					read += n;
					heardFrames = Math.max(0L, read - (line.getBufferSize() - line.available()) / 4);
					s.played(heardFrames); // The bars follow the ears, not the synth.
				}
				if(mine == generation) {
					// The last of the tune is still in the line: keep the bars and the clock with it as it plays out.
					long until = System.nanoTime() + (long)((LINE_SECONDS + 0.5D) * 1.0E9D);
					while(mine == generation && line.available() < line.getBufferSize() && System.nanoTime() < until) {
						if(paused) {
							if(line.isRunning())
								line.stop();
							until += 20_000_000L;
							Thread.sleep(20L);
							continue;
						}
						if(!line.isRunning())
							line.start();
						heardFrames = Math.max(0L, read - (line.getBufferSize() - line.available()) / 4);
						s.played(heardFrames);
						Thread.sleep(20L);
					}
					if(mine != generation)
						return; // Stopped or another tune started while the end played out.
					line.drain();
					heardFrames = read;
					finished = true;
				}
			}
		}
		catch(InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		catch(InvalidMidiDataException | UnsupportedAudioFileException | LineUnavailableException | IOException | RuntimeException e) {
			if(mine == generation)
				problem = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
			Radiocraft.LOGGER.warn("The radio computer could not play a MIDI file", e);
		}
		finally {
			if(line != null) {
				line.stop();
				line.flush();
				line.close();
			}
			if(mine == generation)
				starting = false;
			if(active == mine)
				active = -1;
		}
	}
}
