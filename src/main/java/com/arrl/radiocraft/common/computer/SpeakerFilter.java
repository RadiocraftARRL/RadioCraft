package com.arrl.radiocraft.common.computer;

/**
 * The radio computer's speaker (1.12 music player fixes): what MUSIC's tunes go through on the way out, so they sound
 * like a pair of small, cheap PC speakers rather than a studio. A second-order high-pass at {@link #LOW_CUT} (the
 * little cones have no bass), a gentle bump around {@link #BUMP} (their cone resonance), a second-order low-pass at
 * {@link #HIGH_CUT} (no tweeter), then a mild soft clip (the small amplifier running out of headroom on peaks). Small
 * signals pass at about unity gain, so the game's volume settings still mean what they say.
 * <p>
 * One sample at a time, mono, a handful of multiplies per sample (three RBJ biquads and a {@code tanh}). Pure (no game
 * classes), so it is unit tested. Not thread safe: one per stream.
 */
public final class SpeakerFilter {

	/** High-pass corner, Hz. */
	public static final double LOW_CUT = 280.0D;
	/** Low-pass corner, Hz. */
	public static final double HIGH_CUT = 4500.0D;
	/** Centre of the cone's resonance bump, Hz, and its gain in dB. */
	public static final double BUMP = 1500.0D, BUMP_DB = 3.5D;
	/** How hard the amplifier is driven into its soft clip (1 = hardly). */
	private static final double DRIVE = 1.4D;
	/** Make-up gain for the bass the cones lose. */
	private static final double MAKEUP = 1.1D;

	private final Biquad highPass, bump, lowPass;

	public SpeakerFilter(double sampleRate) {
		highPass = Biquad.highPass(sampleRate, LOW_CUT, 0.707D);
		bump = Biquad.peak(sampleRate, BUMP, 1.0D, BUMP_DB);
		lowPass = Biquad.lowPass(sampleRate, HIGH_CUT, 0.75D);
	}

	/** One sample in (about -1 to 1), one out (always inside -1 to 1, in fact +-1/{@link #DRIVE}). */
	public float process(float sample) {
		double y = lowPass.apply(bump.apply(highPass.apply(sample)));
		return (float)softClip(y * MAKEUP);
	}

	/** The amplifier's soft clip: about {@code x} for small {@code x}, rounding off towards +-1/{@link #DRIVE}. */
	public static double softClip(double x) {
		return Math.tanh(x * DRIVE) / DRIVE;
	}

	/** Forgets the filters' state (a new tune). */
	public void reset() {
		highPass.reset();
		bump.reset();
		lowPass.reset();
	}

	/** A biquad in transposed direct form II, coefficients from the RBJ Audio EQ Cookbook. */
	private static final class Biquad {
		private final double b0, b1, b2, a1, a2;
		private double z1, z2;

		private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
			this.b0 = b0 / a0;
			this.b1 = b1 / a0;
			this.b2 = b2 / a0;
			this.a1 = a1 / a0;
			this.a2 = a2 / a0;
		}

		static Biquad highPass(double rate, double f, double q) {
			double w = 2.0D * Math.PI * f / rate, cos = Math.cos(w), alpha = Math.sin(w) / (2.0D * q);
			return new Biquad((1.0D + cos) / 2.0D, -(1.0D + cos), (1.0D + cos) / 2.0D, 1.0D + alpha, -2.0D * cos, 1.0D - alpha);
		}

		static Biquad lowPass(double rate, double f, double q) {
			double w = 2.0D * Math.PI * f / rate, cos = Math.cos(w), alpha = Math.sin(w) / (2.0D * q);
			return new Biquad((1.0D - cos) / 2.0D, 1.0D - cos, (1.0D - cos) / 2.0D, 1.0D + alpha, -2.0D * cos, 1.0D - alpha);
		}

		static Biquad peak(double rate, double f, double q, double db) {
			double a = Math.pow(10.0D, db / 40.0D), w = 2.0D * Math.PI * f / rate, cos = Math.cos(w), alpha = Math.sin(w) / (2.0D * q);
			return new Biquad(1.0D + alpha * a, -2.0D * cos, 1.0D - alpha * a, 1.0D + alpha / a, -2.0D * cos, 1.0D - alpha / a);
		}

		double apply(double x) {
			double y = b0 * x + z1;
			z1 = b1 * x - a1 * y + z2;
			z2 = b2 * x - a2 * y;
			return y;
		}

		void reset() {
			z1 = z2 = 0.0D;
		}
	}
}
