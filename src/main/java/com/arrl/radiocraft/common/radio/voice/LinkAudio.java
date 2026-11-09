package com.arrl.radiocraft.common.radio.voice;

import com.arrl.radiocraft.common.radio.microwave.MicrowaveMath;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What a link between two radio computers does to the audio of a remote rig (RIGCTL remote audio, 2026-11-09). Data
 * wire is clean and immediate. A microwave link adds a small delay, and with its grade
 * ({@link MicrowaveMath.Grade}) roughens the voice (fewer bits, a little hiss) and drops frames: GOOD only delays it,
 * FAIR crackles now and then, POOR (or TOO WEAK) breaks up.
 * <p>
 * Pure apart from the grade enum, so it is unit tested. A {@link Line} is one direction of one session's audio.
 */
public final class LinkAudio {

	private LinkAudio() {}

	/** Frames held at most (two seconds): a line nobody drains cannot grow. */
	public static final int MAX_HELD = 100;

	/**
	 * How a link spoils the audio.
	 *
	 * @param delayNanos how long each frame is held
	 * @param drop       the share of frames lost, 0 to 1
	 * @param bits       how many bits of each sample survive (16 = all)
	 * @param hiss       noise added, as a share of full scale
	 */
	public record Impairment(long delayNanos, double drop, int bits, double hiss) {

		/** Data wire, or a computer at the rig itself. */
		public static final Impairment CLEAN = new Impairment(0L, 0.0D, 16, 0.0D);

		public Impairment {
			delayNanos = Math.max(0L, delayNanos);
			drop = Math.max(0.0D, Math.min(1.0D, drop));
			bits = Math.max(1, Math.min(16, bits));
			hiss = Math.max(0.0D, hiss);
		}

		public boolean clean() {
			return delayNanos == 0L && drop <= 0.0D && bits >= 16 && hiss <= 0.0D;
		}
	}

	/** The impairment of a link: {@code grade} null for data wire. */
	public static Impairment of(@Nullable MicrowaveMath.Grade grade) {
		if(grade == null)
			return Impairment.CLEAN;
		return switch(grade) {
			case GOOD -> new Impairment(60_000_000L, 0.0D, 16, 0.0D);
			case FAIR -> new Impairment(100_000_000L, 0.03D, 10, 0.004D);
			default -> new Impairment(160_000_000L, 0.12D, 7, 0.012D); // POOR, TOO WEAK (and NO SIGNAL, which ends the session).
		};
	}

	/** {@code pcm} with its low bits gone and {@code hiss} added (a new array; {@code pcm} as it is when clean). */
	public static short[] roughen(short[] pcm, Impairment impairment, Random random) {
		if(impairment.bits() >= 16 && impairment.hiss() <= 0.0D)
			return pcm;
		short[] out = new short[pcm.length];
		int shift = 16 - impairment.bits();
		double hiss = impairment.hiss() * 32767.0D;
		for(int i = 0; i < pcm.length; i++) {
			int sample = (pcm[i] >> shift) << shift;
			if(hiss > 0.0D)
				sample += (int)Math.round((random.nextDouble() * 2.0D - 1.0D) * hiss);
			out[i] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
		}
		return out;
	}

	/**
	 * One direction of a session's audio over a link: frames go in as they come ({@link #offer}) and come out once
	 * their delay is up ({@link #due}), roughened, some of them lost. Thread safe: the voice thread offers and drains,
	 * the game thread drains what is left between voice frames.
	 */
	public static final class Line {

		private record Held(long at, short[] pcm) {}

		private final Impairment impairment;
		private final Random random;
		private final ArrayDeque<Held> held = new ArrayDeque<>();

		public Line(Impairment impairment) {
			this(impairment, new Random());
		}

		public Line(Impairment impairment, Random random) {
			this.impairment = impairment;
			this.random = random;
		}

		public Impairment impairment() {
			return impairment;
		}

		/** A frame arrives at {@code nanos}; it may be lost on the way. */
		public synchronized void offer(short[] pcm, long nanos) {
			if(impairment.drop() > 0.0D && random.nextDouble() < impairment.drop())
				return;
			if(held.size() >= MAX_HELD)
				held.poll();
			held.add(new Held(nanos + impairment.delayNanos(), roughen(pcm, impairment, random)));
		}

		/** The frames whose delay is up at {@code nanos}, oldest first (taken off the line). */
		public synchronized List<short[]> due(long nanos) {
			if(held.isEmpty())
				return List.of();
			List<short[]> out = new ArrayList<>();
			while(!held.isEmpty() && held.peek().at() <= nanos)
				out.add(held.poll().pcm());
			return out;
		}

		/** How many frames wait. */
		public synchronized int waiting() {
			return held.size();
		}

		public synchronized void clear() {
			held.clear();
		}
	}
}
