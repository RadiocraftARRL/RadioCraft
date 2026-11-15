package com.arrl.radiocraft.common.computer;

/**
 * The rig control program's waterfall (computer batch 3B), the pure parts: frequency bins across a span, signal levels
 * from strength over the noise, and finding the next signal along the band. Unit tested.
 */
public final class Waterfall {

	private Waterfall() {}

	/** Levels a bin can show: 0 is nothing heard, 1 the faintest signal, {@link #MAX_LEVEL} the strongest. */
	public static final int MAX_LEVEL = 15;
	/** dB over the noise at level 1, and dB per level. Level 1 is 20 dB under the noise (where FT8 still decodes). */
	public static final double FLOOR_DB = -21.0D, DB_PER_LEVEL = 3.0D;
	/** Weakest noise the levels are measured against (a quiet VHF receiver would otherwise make everything +40 dB). */
	public static final double MIN_NOISE = 0.01D;

	/** The bin {@code hz} falls in, of {@code bins} from {@code lowHz} to {@code highHz}; -1 outside. */
	public static int bin(double hz, double lowHz, double highHz, int bins) {
		if(!(highHz > lowHz) || hz < lowHz || hz > highHz)
			return -1;
		int bin = (int)((hz - lowHz) / (highHz - lowHz) * bins);
		return Math.min(bins - 1, bin);
	}

	/** The centre of {@code bin}. */
	public static double centre(int bin, double lowHz, double highHz, int bins) {
		return lowHz + (bin + 0.5D) * (highHz - lowHz) / bins;
	}

	/** The level (0 to {@link #MAX_LEVEL}) a signal at {@code strength} shows over {@code noise}; 0 for no signal. */
	public static int level(double strength, double noise) {
		if(!(strength > 0.0D))
			return 0;
		double db = 10.0D * Math.log10(strength / Math.max(MIN_NOISE, noise));
		int level = 1 + (int)Math.floor((db - FLOOR_DB) / DB_PER_LEVEL);
		return Math.max(1, Math.min(MAX_LEVEL, level));
	}

	/** The span a zoom step shows around {@code dialHz} inside {@code lowHz}..{@code highHz}: {lo, hi}. 0 is the whole band. */
	public static double[] span(int zoom, double dialHz, double lowHz, double highHz) {
		if(zoom <= 0 || !(highHz > lowHz))
			return new double[] { lowHz, highHz };
		double width = Math.max(1000.0D, (highHz - lowHz) / Math.pow(4.0D, zoom));
		double lo = dialHz - width / 2.0D;
		lo = Math.max(lowHz, Math.min(highHz - width, lo));
		return new double[] { lo, Math.min(highHz, lo + width) };
	}

	/** Zoom steps: the whole band, a quarter, a sixteenth, a sixty-fourth. */
	public static final int ZOOMS = 4;

	/**
	 * The External CRT Monitor's waterfall spans (2026-11-12), Hz: a glance at the signals round the dial, not a band
	 * scope (rig control's widest view is the whole band, hundreds of kHz on HF and MHz on VHF).
	 * <ul>
	 * <li>HF and CB, below {@link #MONITOR_VHF_HZ}: NARROW 6 kHz, one SSB voice (2.4 kHz) either side of the dial with
	 * room to see a neighbour, or a handful of CW signals at about 80 Hz a column; WIDE 24 kHz, a stretch of a crowded
	 * band, still a fifteenth of 20m.</li>
	 * <li>VHF and up: NARROW 50 kHz, an FM signal (up to 15 kHz wide here) and the channels either side; WIDE 200 kHz,
	 * eight 25 kHz channels, a twentieth of 2m.</li>
	 * </ul>
	 */
	public static final double MONITOR_HF_NARROW_HZ = 6_000.0D, MONITOR_HF_WIDE_HZ = 24_000.0D,
			MONITOR_VHF_NARROW_HZ = 50_000.0D, MONITOR_VHF_WIDE_HZ = 200_000.0D;
	/** Dial frequencies from here up take the VHF spans. */
	public static final double MONITOR_VHF_HZ = 30_000_000.0D;

	/** How wide the monitor's waterfall is round {@code dialHz}: {@link #MONITOR_HF_NARROW_HZ} and the rest. */
	public static double monitorWidth(boolean wide, double dialHz) {
		if(dialHz >= MONITOR_VHF_HZ)
			return wide ? MONITOR_VHF_WIDE_HZ : MONITOR_VHF_NARROW_HZ;
		return wide ? MONITOR_HF_WIDE_HZ : MONITOR_HF_NARROW_HZ;
	}

	/**
	 * The span the monitor's waterfall shows, {lo, hi}: {@link #monitorWidth} centred on {@code dialHz}, slid to stay
	 * inside {@code lowHz}..{@code highHz} (the band, or the radio's tuning range) near its edges, and the whole of a
	 * band narrower than that. With no band ({@code highHz <= lowHz}), centred on the dial.
	 */
	public static double[] monitorSpan(boolean wide, double dialHz, double lowHz, double highHz) {
		double width = monitorWidth(wide, dialHz);
		double lo = dialHz - width / 2.0D;
		if(highHz > lowHz) {
			if(highHz - lowHz <= width)
				return new double[] { lowHz, highHz };
			lo = Math.max(lowHz, Math.min(highHz - width, lo));
		}
		return new double[] { lo, lo + width };
	}

	/**
	 * The nearest bin from {@code from} in direction {@code step} (+1 up, -1 down) whose level is at least
	 * {@code minLevel}, skipping the run of active bins {@code from} is in (so it jumps to the next signal, not the
	 * next bin of this one) and landing on that signal's strongest bin; -1 if there is none.
	 */
	public static int nextSignal(int[] levels, int from, int step, int minLevel) {
		int i = from;
		while(i >= 0 && i < levels.length && levels[i] >= minLevel)
			i += step; // Off the signal we are on.
		for(; i >= 0 && i < levels.length; i += step)
			if(levels[i] >= minLevel) {
				int best = i; // The strongest bin of that signal.
				for(int j = i; j >= 0 && j < levels.length && levels[j] >= minLevel; j += step)
					if(levels[j] > levels[best])
						best = j;
				return best;
			}
		return -1;
	}
}
