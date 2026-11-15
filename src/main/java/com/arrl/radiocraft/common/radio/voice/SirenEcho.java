package com.arrl.radiocraft.common.radio.voice;

/**
 * The echo of a siren used as a loudspeaker (2026-11-13): a voice put out of a siren head carries far and comes back
 * off the land and buildings round it. Each frame gets fading copies of what was said {@link #TAPS} earlier, with a
 * little feedback so the tail rings on. Works in place on 16 bit mono frames of any length.
 */
public final class SirenEcho {

	/** Delays of the echoes, in seconds, and how loud each comes back. */
	static final double[] TAPS = {0.19D, 0.43D, 0.71D};
	static final double[] GAINS = {0.42D, 0.26D, 0.14D};
	/** How much of the echoed signal goes round again. */
	static final double FEEDBACK = 0.18D;

	private final int[] delays;
	private final float[] ring;
	private int at;

	public SirenEcho(int sampleRate) {
		delays = new int[TAPS.length];
		int longest = 1;
		for(int i = 0; i < TAPS.length; i++) {
			delays[i] = Math.max(1, (int)Math.round(TAPS[i] * sampleRate));
			longest = Math.max(longest, delays[i]);
		}
		ring = new float[longest + 1];
	}

	/** {@code pcm} with its echoes added, in a new array. */
	public short[] process(short[] pcm) {
		short[] out = new short[pcm.length];
		for(int n = 0; n < pcm.length; n++) {
			double echo = 0.0D;
			for(int i = 0; i < delays.length; i++)
				echo += ring[Math.floorMod(at - delays[i], ring.length)] * GAINS[i];
			double dry = pcm[n];
			ring[at] = (float)(dry + echo * FEEDBACK);
			at = (at + 1) % ring.length;
			double wet = dry + echo;
			out[n] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(wet)));
		}
		return out;
	}
}
