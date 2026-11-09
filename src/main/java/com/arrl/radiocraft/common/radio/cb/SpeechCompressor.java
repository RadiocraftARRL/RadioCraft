package com.arrl.radiocraft.common.radio.cb;

/**
 * A CB radio's speech compressor (the 5875's SPEECH COMPRESSION switch on MAX): it brings the quiet parts of the voice
 * up toward the loud ones so the transmitter is modulated harder on average, which is what makes a compressed voice
 * cut through noise. A fast envelope follower sets a gain of up to {@link #MAX_GAIN}, which glides so it does not
 * pump, and a soft knee keeps the peaks under full scale. One per radio (it remembers its envelope between frames).
 * Not thread safe: frames come one at a time from the voice thread.
 */
public final class SpeechCompressor {

	static final int SAMPLE_RATE = 48_000;
	/** The level the compressor aims the voice's envelope at, of full scale. */
	static final double TARGET = 0.5D;
	/** The most it lifts a quiet passage. */
	static final double MAX_GAIN = 4.0D;
	/** Below this envelope (of full scale) it is a pause: the gain holds, so the background is not pumped up. */
	static final double GATE = 0.02D;
	private static final double ATTACK = coefficient(0.002D), RELEASE = coefficient(0.15D), GLIDE = coefficient(0.01D);

	private double envelope = 0.0D;
	private double gain = 1.0D;
	/** The peak of the last frame out, 0 to 1: what a modulation meter reads. */
	private double lastPeak = 0.0D;

	/** Compresses {@code pcm} in place and returns it. */
	public short[] process(short[] pcm) {
		double peak = 0.0D;
		for(int i = 0; i < pcm.length; i++) {
			double x = pcm[i] / 32768.0D, magnitude = Math.abs(x);
			envelope = magnitude > envelope ? ATTACK * envelope + (1.0D - ATTACK) * magnitude : RELEASE * envelope + (1.0D - RELEASE) * magnitude;
			double wanted = envelope > GATE ? Math.max(1.0D, Math.min(MAX_GAIN, TARGET / envelope)) : gain;
			gain = GLIDE * gain + (1.0D - GLIDE) * wanted;
			double y = softKnee(x * gain);
			peak = Math.max(peak, Math.abs(y));
			pcm[i] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(y * 32767.0D)));
		}
		lastPeak = peak;
		return pcm;
	}

	/** Peak of the last frame it processed, 0 to 1. */
	public double lastPeak() {
		return lastPeak;
	}

	/** Above 0.8 of full scale the voice is rounded off rather than clipped. */
	static double softKnee(double x) {
		double magnitude = Math.abs(x);
		if(magnitude <= 0.8D)
			return x;
		return Math.signum(x) * (0.8D + 0.2D * Math.tanh((magnitude - 0.8D) / 0.2D));
	}

	/** The peak of {@code pcm}, 0 to 1 (the modulation an uncompressed voice gives). */
	public static double peak(short[] pcm) {
		int peak = 0;
		for(short s : pcm)
			peak = Math.max(peak, Math.abs((int)s));
		return peak / 32768.0D;
	}

	private static double coefficient(double seconds) {
		return Math.exp(-1.0D / (seconds * SAMPLE_RATE));
	}
}
