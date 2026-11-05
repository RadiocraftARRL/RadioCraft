package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.Radiocraft;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The radio computer's sound card for MUSIC (1.12 step 8): plays a MIDI file on this computer through Java's built-in
 * software synthesizer ({@code javax.sound.midi}, Gervill), not through Minecraft's sound engine. One tune at a time,
 * for the whole client. Opening the synthesizer takes a moment, so it is done on a thread of its own; the methods are
 * safe to call from the render thread.
 * <p>
 * Loudness follows the game's sound settings: master times "Jukebox/Note Blocks" ({@link SoundSource#RECORDS}), read
 * by {@link #updateVolume} every tick the program runs. It is applied to each note as it starts (velocity scaled by the
 * square root of the gain, since a General MIDI synth's loudness goes with velocity squared), so a held note keeps the
 * level it began at; at 0 no note starts.
 */
public final class ComputerMidi {

	private static final ExecutorService THREAD = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "RadioCraft computer MIDI");
		thread.setDaemon(true);
		return thread;
	});

	@Nullable
	private static Sequencer sequencer;
	@Nullable
	private static Synthesizer synth;
	/** Velocity scale, 0-1, read by the MIDI thread. */
	private static volatile float velocityScale = 1.0F;
	/** Bumped on every play or stop, so a slow start that was overtaken gives up. */
	private static volatile int generation;
	/** What each of the 16 channels last did, 0-1 (the program's level meters); decays as the screen reads it. */
	private static final float[] LEVELS = new float[16];
	@Nullable
	private static volatile String problem;
	/** True from {@link #play} until the tune has started (or failed), so the last tune's end is not taken for this one's. */
	private static volatile boolean starting;

	private ComputerMidi() {}

	/** Starts {@code midi} from the top (stopping whatever was playing). */
	public static void play(byte[] midi) {
		int mine = ++generation;
		problem = null;
		starting = true;
		updateVolume();
		THREAD.execute(() -> {
			try {
				Sequencer seq = open();
				if(mine != generation)
					return;
				seq.stop();
				seq.setSequence(new ByteArrayInputStream(midi));
				seq.setTickPosition(0L);
				allNotesOff();
				if(mine != generation)
					return;
				seq.start();
			}
			catch(MidiUnavailableException | InvalidMidiDataException | IOException | RuntimeException e) {
				problem = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
				Radiocraft.LOGGER.warn("The radio computer could not play a MIDI file", e);
			}
			finally {
				if(mine == generation)
					starting = false;
			}
		});
	}

	/** Stops (keeps the synthesizer open for the next tune). */
	public static void stop() {
		++generation;
		starting = false;
		THREAD.execute(() -> {
			Sequencer seq = sequencer;
			if(seq != null && seq.isOpen())
				seq.stop();
			allNotesOff();
		});
	}

	/** Pauses or carries on from where it was. */
	public static void pause(boolean paused) {
		THREAD.execute(() -> {
			Sequencer seq = sequencer;
			if(seq == null || !seq.isOpen() || seq.getSequence() == null)
				return;
			if(paused) {
				seq.stop();
				allNotesOff();
			}
			else
				seq.start();
		});
	}

	/** Closes the synthesizer (the program has exited). */
	public static void close() {
		++generation;
		starting = false;
		THREAD.execute(() -> {
			if(sequencer != null)
				sequencer.close();
			if(synth != null)
				synth.close();
			sequencer = null;
			synth = null;
		});
	}

	/** True while a tune is playing (not paused, not at its end). */
	public static boolean playing() {
		Sequencer seq = sequencer;
		return seq != null && seq.isOpen() && seq.isRunning();
	}

	/** How far into the tune it is, 0-1 (0 with nothing loaded). */
	public static double position() {
		Sequencer seq = sequencer;
		if(seq == null || !seq.isOpen() || seq.getSequence() == null)
			return 0.0D;
		long length = seq.getMicrosecondLength();
		return length <= 0L ? 0.0D : Math.min(1.0D, seq.getMicrosecondPosition() / (double)length);
	}

	/** Seconds into the tune. */
	public static int seconds() {
		Sequencer seq = sequencer;
		return seq == null || !seq.isOpen() ? 0 : (int)(seq.getMicrosecondPosition() / 1_000_000L);
	}

	/** True when the tune loaded has played to its end. */
	public static boolean finished() {
		if(starting)
			return false;
		Sequencer seq = sequencer;
		return seq != null && seq.isOpen() && seq.getSequence() != null && !seq.isRunning() && seq.getTickPosition() >= seq.getTickLength();
	}

	/** Why the last tune did not play, or null. */
	@Nullable
	public static String problem() {
		return problem;
	}

	/** Channel {@code channel}'s level (0-1) for the meters; each read lets it fall a little. */
	public static float level(int channel) {
		synchronized(LEVELS) {
			float level = LEVELS[channel];
			LEVELS[channel] = Math.max(0.0F, level - 0.04F);
			return level;
		}
	}

	/** Reads the game's sound settings (see the class comment). */
	public static void updateVolume() {
		updateVolume(1.0F);
	}

	/** Reads the game's sound settings, times {@code fade} (0-1: the player's distance from the computer, {@link ComputerMusic}). */
	public static void updateVolume(float fade) {
		Minecraft mc = Minecraft.getInstance();
		float gain = fade * mc.options.getSoundSourceVolume(SoundSource.MASTER) * mc.options.getSoundSourceVolume(SoundSource.RECORDS);
		velocityScale = (float)Math.sqrt(Math.max(0.0F, Math.min(1.0F, gain)));
	}

	private static Sequencer open() throws MidiUnavailableException {
		if(sequencer != null && sequencer.isOpen())
			return sequencer;
		Synthesizer s = MidiSystem.getSynthesizer();
		s.open();
		Sequencer seq = MidiSystem.getSequencer(false); // Not to the default device: to our synth, through the volume.
		seq.open();
		seq.getTransmitter().setReceiver(new Volume(s.getReceiver()));
		synth = s;
		sequencer = seq;
		return seq;
	}

	private static void allNotesOff() {
		Synthesizer s = synth;
		if(s == null || !s.isOpen())
			return;
		for(var channel : s.getChannels())
			if(channel != null)
				channel.allNotesOff();
		synchronized(LEVELS) {
			java.util.Arrays.fill(LEVELS, 0.0F);
		}
	}

	/** Passes every message on to the synth, a note's velocity scaled to the volume setting; notes light the meters. */
	private record Volume(Receiver out) implements Receiver {
		@Override
		public void send(MidiMessage message, long timeStamp) {
			if(message instanceof ShortMessage note && note.getCommand() == ShortMessage.NOTE_ON && note.getData2() > 0) {
				float scale = velocityScale;
				if(scale <= 0.001F)
					return;
				int velocity = Math.max(1, Math.round(note.getData2() * scale));
				synchronized(LEVELS) {
					LEVELS[note.getChannel()] = Math.max(LEVELS[note.getChannel()], note.getData2() / 127.0F);
				}
				try {
					out.send(new ShortMessage(ShortMessage.NOTE_ON, note.getChannel(), note.getData1(), velocity), timeStamp);
				}
				catch(InvalidMidiDataException e) {
					out.send(message, timeStamp);
				}
				return;
			}
			out.send(message, timeStamp);
		}

		@Override
		public void close() {
			out.close();
		}
	}
}
