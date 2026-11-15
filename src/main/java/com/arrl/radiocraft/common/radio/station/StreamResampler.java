package com.arrl.radiocraft.common.radio.station;

/**
 * Turns decoded audio, chunk after chunk, into the radio's: mono, 16 bit, 48 kHz (2026-11-14). Channels are averaged and
 * the rate changed by linear interpolation that carries on smoothly across chunk boundaries (the last sample and the
 * fraction of a step are kept). Pure: unit tested.
 */
public final class StreamResampler {

	/** The radio's sample rate. */
	public static final int OUT_RATE = 48_000;

	private final int inRate;
	private final double step;
	private float prev;
	private double phase;
	private boolean started;

	public StreamResampler(int inRate) {
		this.inRate = Math.max(1, inRate);
		this.step = (double)this.inRate / OUT_RATE;
	}

	public int inRate() {
		return inRate;
	}

	/** Interleaved 16-bit {@code samples} ({@code frames} of {@code channels}) averaged to mono floats -1..1. */
	public static float[] downmix(short[] samples, int frames, int channels) {
		int ch = Math.max(1, channels);
		float[] out = new float[frames];
		for(int i = 0; i < frames; i++) {
			float sum = 0.0F;
			for(int c = 0; c < ch; c++)
				sum += samples[i * ch + c];
			out[i] = sum / ch / 32768.0F;
		}
		return out;
	}

	/** The next {@code n} mono samples (-1..1) at the input rate, as 16-bit samples at {@link #OUT_RATE}. */
	public short[] push(float[] x, int n) {
		if(n <= 0)
			return new short[0];
		if(!started) {
			prev = x[0];
			phase = 0.0D;
			started = true;
		}
		int estimate = (int)Math.ceil((n + 1) / step) + 2;
		short[] out = new short[estimate];
		int count = 0;
		double t = phase;
		while(t <= n - 1) {
			int a = (int)Math.floor(t);
			double f = t - a;
			float va = a < 0 ? prev : x[a];
			float vb = a + 1 < n ? x[a + 1] : x[Math.max(0, a)];
			double v = va + (vb - va) * f;
			if(count == out.length)
				out = java.util.Arrays.copyOf(out, out.length * 2);
			out[count++] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(v * 32767.0D)));
			t += step;
		}
		phase = t - n;
		prev = x[n - 1];
		return count == out.length ? out : java.util.Arrays.copyOf(out, count);
	}
}
