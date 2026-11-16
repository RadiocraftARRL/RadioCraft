package com.arrl.radiocraft.common.radio.voice;

import java.util.List;

/**
 * What a fast digital burst sounds like through an FM receiver's speaker (2026-11-15, a satellite's telemetry): not
 * APRS's two whistling tones ({@link Afsk}) but the raw two-level signal itself, as a pager (POCSAG) or a wideband
 * data downlink sounds on a scanner, a harsh buzz. A preamble of alternating bits (a steady tone at half the bit
 * rate), then random bits as a softened square wave. No game classes, so it can be tested.
 */
public final class PagerAudio {

	/** Bits a second: POCSAG's fastest. */
	public static final double BAUD = 2400.0D;
	/** The alternating preamble, bits. */
	public static final int PREAMBLE_BITS = 576;
	private static final double LEVEL = 0.3D;
	/** How quickly the level follows a bit change (a one-pole filter): softens the square wave's edges a little. */
	private static final double SMOOTHING = 0.45D;

	private PagerAudio() {}

	/** Frames of the burst for a packet of {@code bytes} bytes, its bits seeded from {@code seed}. */
	public static List<short[]> burst(int bytes, long seed) {
		int bits = PREAMBLE_BITS + Math.max(0, bytes) * 8;
		double samplesPerBit = RadioVoiceProcessor.SAMPLE_RATE / BAUD;
		double[] samples = new double[(int)Math.ceil(bits * samplesPerBit)];
		long x = seed | 1L;
		double level = 0.0D;
		int sample = 0;
		for(int bit = 0; bit < bits; bit++) {
			boolean one;
			if(bit < PREAMBLE_BITS)
				one = (bit & 1) == 0;
			else {
				x ^= x << 13;
				x ^= x >>> 7;
				x ^= x << 17;
				one = (x & 1L) != 0L;
			}
			double target = one ? LEVEL : -LEVEL;
			int end = (int)Math.round((bit + 1) * samplesPerBit);
			for(; sample < end && sample < samples.length; sample++) {
				level += (target - level) * SMOOTHING;
				samples[sample] = level;
			}
		}
		return RepeaterAudio.frames(samples);
	}

	/** How long the burst for a packet of {@code bytes} bytes lasts, in 20 ms frames. */
	public static int frameCount(int bytes) {
		int samples = (int)Math.ceil((PREAMBLE_BITS + Math.max(0, bytes) * 8) * RadioVoiceProcessor.SAMPLE_RATE / BAUD);
		return (samples + RepeaterAudio.FRAME - 1) / RepeaterAudio.FRAME;
	}
}
