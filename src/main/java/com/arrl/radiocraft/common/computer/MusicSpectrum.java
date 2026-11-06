package com.arrl.radiocraft.common.computer;

/**
 * The MUSIC program's visualizer (1.12 music player fixes): a spectrum analyser on the sound the computer plays, in
 * {@code bars} bands spaced evenly in pitch (log frequency) from {@code low} to {@code high} Hz, so a bass line, the
 * melody and the cymbals each move their own bars. (The first visualizer showed what each MIDI channel did, and most
 * tunes use one or two channels, so only the left bars moved.)
 * <p>
 * Fed mono samples as they are played ({@link #feed}); every {@link #HOP} samples it takes a Hann-windowed
 * {@link #FFT}-point FFT, adds up the power in each band (wider bands get more FFT bins, which offsets music having
 * less energy per Hz the higher it goes), and shows each band in dB below a slowly falling reference, the loudest band
 * lately (an automatic gain, so a quiet tune moves the bars as much as a loud one). Bars jump up and fall back at
 * {@link #FALL} per hop. Pure (no game classes), so it is unit tested. Written by the audio thread, read by the render
 * thread (a torn read is a bar one frame late, nothing worse).
 */
public final class MusicSpectrum {

	/** FFT size, samples (46 ms at 44.1 kHz). */
	public static final int FFT = 2048;
	/** Samples between FFTs (half overlap: 43 a second at 44.1 kHz). */
	public static final int HOP = FFT / 2;
	/** How far below the reference a band's bar is empty, dB. */
	public static final double RANGE_DB = 36.0D;
	/**
	 * Bands are lifted this much per octave above the lowest, dB: a tune's power still falls with pitch even in bands
	 * that widen with it, and without this the treble bars hardly move.
	 */
	public static final double TILT_DB = 2.0D;
	/** How far a bar falls per hop, 0-1. */
	public static final float FALL = 0.035F;
	/** How fast the reference forgets a loud passage, per hop (about 6 dB in 3 seconds). */
	private static final double REFERENCE_DECAY = 0.9894D;
	/**
	 * The reference never goes below this band power (about 60 dB under a full-scale sine's), so a near-silent fade-out
	 * stays dark instead of being turned up.
	 */
	private static final double REFERENCE_FLOOR = 0.3D;

	private final int bars;
	private final int[] edges;
	private final float[] levels;
	private final double[] window = new double[FFT], re = new double[FFT], im = new double[FFT];
	private final double[] power, tilt;
	private final float[] ring = new float[FFT];
	private int write, sinceLast;
	private double reference = REFERENCE_FLOOR;

	public MusicSpectrum(int bars, double sampleRate, double low, double high) {
		this.bars = bars;
		edges = bandEdges(bars, sampleRate, FFT, low, high);
		levels = new float[bars];
		power = new double[bars];
		tilt = new double[bars];
		for(int b = 0; b < bars; b++)
			tilt[b] = Math.pow(10.0D, TILT_DB * Math.log(high / low) / Math.log(2.0D) * b / bars / 10.0D);
		for(int i = 0; i < FFT; i++)
			window[i] = 0.5D - 0.5D * Math.cos(2.0D * Math.PI * i / (FFT - 1));
	}

	/**
	 * The FFT bins of each band: band {@code b} is bins {@code [edges[b], edges[b + 1])}, the edges spaced evenly in log
	 * frequency from {@code low} to {@code high} Hz, every band at least one bin wide (so the lowest bands, narrower than
	 * a bin, are a bin each and the bands above them start a little higher), never past the Nyquist bin.
	 */
	public static int[] bandEdges(int bars, double sampleRate, int fftSize, double low, double high) {
		int[] edges = new int[bars + 1];
		double binHz = sampleRate / fftSize;
		int nyquist = fftSize / 2;
		for(int i = 0; i <= bars; i++) {
			double f = low * Math.pow(high / low, i / (double)bars);
			edges[i] = (int)Math.round(f / binHz);
			if(i > 0)
				edges[i] = Math.max(edges[i], edges[i - 1] + 1);
		}
		// Keep the top inside the spectrum, pushing bands down if they were forced up past it.
		edges[bars] = Math.min(edges[bars], nyquist);
		for(int i = bars - 1; i >= 0; i--)
			edges[i] = Math.min(edges[i], edges[i + 1] - 1);
		return edges;
	}

	/** The band FFT bin {@code bin} falls in, or -1 if below the first or past the last. */
	public int bandOf(int bin) {
		for(int b = 0; b < bars; b++)
			if(bin >= edges[b] && bin < edges[b + 1])
				return b;
		return -1;
	}

	public int bars() {
		return bars;
	}

	/** Band {@code bar}'s level, 0-1. */
	public float level(int bar) {
		return levels[bar];
	}

	/** Takes {@code count} mono samples (about -1 to 1) as they are played. */
	public void feed(float[] samples, int count) {
		for(int i = 0; i < count; i++) {
			ring[write] = samples[i];
			write = (write + 1) % FFT;
			if(++sinceLast >= HOP) {
				sinceLast = 0;
				analyse();
			}
		}
	}

	/** Lets every bar fall one step (no sound is being fed: paused or stopped). */
	public void fall() {
		for(int b = 0; b < bars; b++)
			levels[b] = Math.max(0.0F, levels[b] - FALL);
	}

	/** Empties the bars and the history (a new tune). */
	public void reset() {
		java.util.Arrays.fill(levels, 0.0F);
		java.util.Arrays.fill(ring, 0.0F);
		write = sinceLast = 0;
		reference = REFERENCE_FLOOR;
	}

	private void analyse() {
		for(int i = 0; i < FFT; i++) {
			re[i] = ring[(write + i) % FFT] * window[i];
			im[i] = 0.0D;
		}
		fft(re, im);
		double loudest = 0.0D;
		for(int b = 0; b < bars; b++) {
			double sum = 0.0D;
			for(int k = edges[b]; k < edges[b + 1]; k++)
				sum += re[k] * re[k] + im[k] * im[k];
			power[b] = sum * tilt[b];
			loudest = Math.max(loudest, sum);
		}
		reference = Math.max(REFERENCE_FLOOR, Math.max(loudest, reference * REFERENCE_DECAY));
		double referenceDb = 10.0D * Math.log10(reference);
		for(int b = 0; b < bars; b++) {
			float level = power[b] <= 0.0D ? 0.0F
					: (float)Math.max(0.0D, Math.min(1.0D, 1.0D + (10.0D * Math.log10(power[b]) - referenceDb) / RANGE_DB));
			levels[b] = Math.max(level, levels[b] - FALL);
		}
	}

	/** In-place iterative radix-2 FFT ({@code re.length} a power of two). */
	static void fft(double[] re, double[] im) {
		int n = re.length;
		for(int i = 1, j = 0; i < n; i++) {
			int bit = n >> 1;
			for(; (j & bit) != 0; bit >>= 1)
				j ^= bit;
			j ^= bit;
			if(i < j) {
				double t = re[i];
				re[i] = re[j];
				re[j] = t;
				t = im[i];
				im[i] = im[j];
				im[j] = t;
			}
		}
		for(int len = 2; len <= n; len <<= 1) {
			double angle = -2.0D * Math.PI / len, wr = Math.cos(angle), wi = Math.sin(angle);
			for(int i = 0; i < n; i += len) {
				double cr = 1.0D, ci = 0.0D;
				for(int k = 0; k < len / 2; k++) {
					int a = i + k, b = a + len / 2;
					double tr = re[b] * cr - im[b] * ci, ti = re[b] * ci + im[b] * cr;
					re[b] = re[a] - tr;
					im[b] = im[a] - ti;
					re[a] += tr;
					im[a] += ti;
					double next = cr * wr - ci * wi;
					ci = cr * wi + ci * wr;
					cr = next;
				}
			}
		}
	}
}
