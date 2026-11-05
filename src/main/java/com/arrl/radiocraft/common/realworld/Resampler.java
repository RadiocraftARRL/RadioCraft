package com.arrl.radiocraft.common.realworld;

/**
 * Converts a stream of 16-bit mono audio from one sample rate to another (a sound card's 44.1 or 8 kHz to the voice
 * chat's 48 kHz and back), a block at a time, with a windowed-sinc filter so going down does not alias and going up
 * does not image. The stream may be cut into blocks of any size: the output is the same, because the read position is
 * kept as an exact fraction ({@code num / outRate} input samples), not a float that would drift. Equal rates pass the
 * audio through untouched. Adds {@link #HALF} input samples of delay. Not thread safe: one per stream. Pure.
 */
public final class Resampler {

	/** Filter taps either side of the output point. */
	static final int HALF = 16;
	private static final int TAPS = 2 * HALF;
	/** Fractional positions the filter is tabulated at. */
	private static final int PHASES = 256;
	/** The pass band ends this far up to the lower rate's Nyquist frequency (the rest is the filter's roll-off). */
	private static final double CUTOFF = 0.9D;

	private final int inRate;
	private final int outRate;
	private final boolean passthrough;
	private final float[] table;
	/** The last {@link #TAPS} input samples, followed by the block being read. */
	private float[] buffer = new float[TAPS + 1024];
	/** Where the next output sample is, in input samples times {@code outRate}, counted from the buffer's start. */
	private long num;

	public Resampler(int inRate, int outRate) {
		if(inRate <= 0 || outRate <= 0)
			throw new IllegalArgumentException("rates must be positive");
		int gcd = gcd(inRate, outRate);
		this.inRate = inRate / gcd;
		this.outRate = outRate / gcd;
		passthrough = inRate == outRate;
		table = passthrough ? new float[0] : table(Math.min(1.0D, (double)outRate / inRate) * CUTOFF);
		num = (long)HALF * this.outRate;
	}

	private static int gcd(int a, int b) {
		return b == 0 ? a : gcd(b, a % b);
	}

	/** The filter's taps at each fractional position, each row scaled to a gain of exactly 1 at DC. */
	private static float[] table(double cutoff) {
		float[] out = new float[(PHASES + 1) * TAPS];
		for(int p = 0; p <= PHASES; p++) {
			double frac = (double)p / PHASES;
			double sum = 0.0D;
			double[] row = new double[TAPS];
			for(int k = 0; k < TAPS; k++) {
				double x = k - (HALF - 1) - frac; // Tap k reads input sample i0 - (HALF - 1) + k.
				double sinc = x == 0.0D ? 1.0D : Math.sin(Math.PI * cutoff * x) / (Math.PI * cutoff * x);
				double w = Math.abs(x) >= HALF ? 0.0D : 0.42D + 0.5D * Math.cos(Math.PI * x / HALF) + 0.08D * Math.cos(2.0D * Math.PI * x / HALF); // Blackman.
				row[k] = cutoff * sinc * w;
				sum += row[k];
			}
			for(int k = 0; k < TAPS; k++)
				out[p * TAPS + k] = (float)(row[k] / sum);
		}
		return out;
	}

	/** Converts {@code length} samples of {@code in} from {@code offset}; returns the output samples they make (maybe none). */
	public short[] process(short[] in, int offset, int length) {
		if(passthrough) {
			short[] copy = new short[length];
			System.arraycopy(in, offset, copy, 0, length);
			return copy;
		}
		int total = TAPS + length;
		if(buffer.length < total) {
			float[] grown = new float[Math.max(total, buffer.length * 2)];
			System.arraycopy(buffer, 0, grown, 0, TAPS);
			buffer = grown;
		}
		for(int i = 0; i < length; i++)
			buffer[TAPS + i] = in[offset + i];
		// Output samples whose taps all fall inside the buffer: i0 + HALF < total.
		long limit = (long)(total - HALF) * outRate;
		int count = num >= limit ? 0 : (int)((limit - num + inRate - 1) / inRate);
		short[] out = new short[count];
		for(int n = 0; n < count; n++) {
			int i0 = (int)(num / outRate);
			int phase = (int)((num % outRate) * PHASES / outRate);
			int base = phase * TAPS;
			int start = i0 - (HALF - 1);
			double sum = 0.0D;
			for(int k = 0; k < TAPS; k++)
				sum += buffer[start + k] * table[base + k];
			out[n] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sum)));
			num += inRate;
		}
		// Keep the last TAPS samples for the next block's filter, and move the read position back with them.
		System.arraycopy(buffer, total - TAPS, buffer, 0, TAPS);
		num -= (long)(total - TAPS) * outRate;
		return out;
	}

	/** As {@link #process(short[], int, int)} for the whole array. */
	public short[] process(short[] in) {
		return process(in, 0, in.length);
	}
}
