package com.arrl.radiocraft.common.radio;

import java.util.Locale;

/**
 * The tuning STEP a VHF radio's dial moves by (2026-11-14), as on a real rig: 5, 6.25, 10, 12.5, 15, 20 or 25 kHz, or 0
 * for the server's channel step ({@code vhf_frequency_step}). Changing the step does not move the frequency; the next
 * click lands on the new step's grid (counted from the band's bottom edge), the way it is turned: up from 164.480 on
 * 12.5 kHz gives 164.4875. Pure (no game classes), so it is unit tested.
 */
public final class TuningStep {

	/** The steps, smallest first. */
	public static final int[] STEPS = {5_000, 6_250, 10_000, 12_500, 15_000, 20_000, 25_000};
	/** How close to a grid line, in steps, counts as on it (a float frequency is a few Hz off at VHF). */
	private static final double ON_GRID = 0.01D;

	private TuningStep() {}

	/** {@code hz} if it is one of {@link #STEPS}, else 0 (the server's step). */
	public static int sanitize(int hz) {
		for(int step : STEPS)
			if(step == hz)
				return hz;
		return 0;
	}

	/**
	 * The largest step whose grid {@code hz} lies on, or 0 if none does: for a memory that kept a frequency but not the
	 * step it was tuned with (a channel cloned from a handheld), so it comes back exactly (2026-11-14: 164.4875 came
	 * back as 164.480 on a 10 kHz step).
	 */
	public static int fitting(double hz) {
		for(int k = STEPS.length - 1; k >= 0; k--) {
			double steps = hz / STEPS[k];
			if(Math.abs(steps - Math.rint(steps)) < ON_GRID)
				return STEPS[k];
		}
		return 0;
	}

	/** The step in use: {@code chosen}, or {@code serverStep} for 0. */
	public static int effective(int chosen, int serverStep) {
		return chosen > 0 ? chosen : Math.max(1, serverStep);
	}

	/** The next step up from the one in use ({@code effective}), round to the smallest after the largest. */
	public static int next(int effective) {
		for(int step : STEPS)
			if(step > effective)
				return step;
		return STEPS[0];
	}

	/** The next step down from the one in use, round to the largest after the smallest. */
	public static int previous(int effective) {
		for(int i = STEPS.length - 1; i >= 0; i--)
			if(STEPS[i] < effective)
				return STEPS[i];
		return STEPS[STEPS.length - 1];
	}

	/** "12.5", "6.25", "20": kHz, as the display shows it. */
	public static String label(int hz) {
		if(hz % 1_000 == 0)
			return String.valueOf(hz / 1_000);
		String text = String.format(Locale.ROOT, "%.2f", hz / 1_000.0D);
		return text.endsWith("0") ? text.substring(0, text.length() - 1) : text;
	}

	/**
	 * {@code frequency} turned {@code clicks} steps of {@code step} on the grid counted from {@code anchor}, kept inside
	 * {@code low} to {@code high}. On the grid it moves whole steps; off it (the step was just changed), the first click
	 * lands on the nearest grid line that way.
	 */
	public static float tune(double anchor, double low, double high, double frequency, int step, int clicks) {
		double at = (frequency - anchor) / step;
		long nearest = Math.round(at);
		long steps;
		if(clicks == 0 || Math.abs(at - nearest) <= ON_GRID)
			steps = nearest + clicks;
		else if(clicks > 0)
			steps = (long)Math.floor(at) + clicks;
		else
			steps = (long)Math.ceil(at) + clicks;
		long minSteps = (long)Math.ceil((low - anchor) / step - 1e-9);
		long maxSteps = (long)Math.floor((high - anchor) / step + 1e-9);
		steps = Math.max(minSteps, Math.min(maxSteps, steps));
		return (float)(anchor + (double)steps * step);
	}

	/** "146.520", or "164.4875" for a frequency off the whole kHz (as a 12.5 or 6.25 kHz step leaves it). */
	public static String mhz(double hz) {
		double khz = hz / 1_000.0D;
		boolean whole = Math.abs(khz - Math.round(khz)) < 0.02D;
		return String.format(Locale.ROOT, whole ? "%.3f" : "%.4f", hz / 1_000_000.0D);
	}
}
