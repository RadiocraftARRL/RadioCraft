package com.arrl.radiocraft.common.radio;

/**
 * Util class for helper methods regarding bands and ranges.
 */
public class BandUtils {

	public static double getBaseStrength(Band band, double distance, double losEfficiency, double skipEfficiency, boolean isDay) {
		return getBaseStrength(band, distance, losEfficiency, skipEfficiency, isDay, 1.0D, 1.0D);
	}

	/**
	 * {@link #getBaseStrength(Band, double, double, double, boolean)} for an antenna whose pattern changes the band's
	 * reach: line of sight reaches {@code losScale} times as far, and skip reaches {@code maxSkipScale} times as far
	 * (the far edge of the skip zone moves out; the near edge stays).
	 */
	public static double getBaseStrength(Band band, double distance, double losEfficiency, double skipEfficiency, boolean isDay,
	                                     double losScale, double maxSkipScale) {
		return getBaseStrength(band, distance, losEfficiency, skipEfficiency, isDay, losScale, maxSkipScale, 1.0D);
	}

	/**
	 * {@link #getBaseStrength(Band, double, double, double, boolean, double, double)} with the solar weather's
	 * {@code skipReach} (2026-11-18): skip reaches only that share of its window, from the near edge (a light opening).
	 */
	public static double getBaseStrength(Band band, double distance, double losEfficiency, double skipEfficiency, boolean isDay,
	                                     double losScale, double maxSkipScale, double skipReach) {
		return getBaseStrength(band, distance, losEfficiency, skipEfficiency, isDay, losScale, maxSkipScale, skipReach, 1, 0.0D);
	}

	/**
	 * The full form (2026-11-18): line of sight to {@code band}'s range times {@code losScale}, fading linearly to 0
	 * there; past it skip in its window ({@link #skipWindow}: both edges times {@code skipScale}, so a longer-reaching
	 * antenna moves the whole window out, skip zone and all), in up to {@code maxHops} hops each losing
	 * {@code hopLoss} more ({@link SkipPath#best}).
	 */
	public static double getBaseStrength(Band band, double distance, double losEfficiency, double skipEfficiency, boolean isDay,
	                                     double losScale, double skipScale, double skipReach, int maxHops, double hopLoss) {
		return getBaseStrength(band, distance, losEfficiency, skipEfficiency, isDay ? 1.0D : 0.0D, losScale, skipScale, skipReach, maxHops, hopLoss);
	}

	/**
	 * As above at {@code dayness} ({@link DayCycle}, 2026-11-18): through sunrise and sunset the skip window moves from
	 * its night place to its day one ({@link #skipWindow(Band, double, double, double)}).
	 */
	public static double getBaseStrength(Band band, double distance, double losEfficiency, double skipEfficiency, double dayness,
	                                     double losScale, double skipScale, double skipReach, int maxHops, double hopLoss) {
		if(band == null)
			return 0.0D;

		double los = band.losRange() * losScale;
		if(distance < los)
			return (1 - (distance / los)) * losEfficiency; // If within LoS scale the strength linearly to 0 as it reaches LoS

		double[] window = skipWindow(band, dayness, skipScale, skipReach);
		return window == null ? 0.0D : SkipPath.best(distance, window[0], window[1], maxHops, hopLoss).share() * skipEfficiency * window[2];
	}

	/**
	 * {@code band}'s skip window at {@code dayness} (0 night, 1 day; 2026-11-18), {min, max, fade}: between the night
	 * and day windows, each edge moving across; a band with skip only by day (or night) keeps that window and fades it
	 * ({@code fade}, 0 to 1) instead. Edges times {@code skipScale}, the far edge pulled in to {@code skipReach}. Null
	 * when there is none.
	 */
	public static double[] skipWindow(Band band, double dayness, double skipScale, double skipReach) {
		double[] day = dayness > 0.0D ? skipWindow(band, true, skipScale, 1.0D) : null;
		double[] night = dayness < 1.0D ? skipWindow(band, false, skipScale, 1.0D) : null;
		double min, max, fade;
		if(day != null && night != null) {
			min = DayCycle.blend(night[0], day[0], dayness);
			max = DayCycle.blend(night[1], day[1], dayness);
			fade = 1.0D;
		}
		else if(day != null) {
			min = day[0];
			max = day[1];
			fade = dayness;
		}
		else if(night != null) {
			min = night[0];
			max = night[1];
			fade = 1.0D - dayness;
		}
		else
			return null;
		if(fade <= 0.0D || max <= min)
			return null;
		if(skipReach < 1.0D)
			max = min + (max - min) * skipReach;
		return new double[] { min, max, fade };
	}

	/**
	 * {@code band}'s skip window by day or night, {min, max} blocks: both edges times {@code skipScale} (the gap after
	 * line of sight scales with it), the far edge pulled in to {@code skipReach} of the window. Null if it has none.
	 */
	public static double[] skipWindow(Band band, boolean isDay, double skipScale, double skipReach) {
		double min = (isDay ? band.minSkipDay() : band.minSkipNight()) * skipScale;
		double max = (isDay ? band.maxSkipDay() : band.maxSkipNight()) * skipScale;
		if(max <= min)
			return null;
		if(skipReach < 1.0D)
			max = min + (max - min) * skipReach;
		return new double[] { min, max };
	}

	/**
	 * Strength of a VHF signal scattered off an aurora: {@code peak} across most of the zone from {@code minRange} to
	 * {@code maxRange} blocks, fading to 0 over the outer 15% at each edge. 0 outside the zone.
	 */
	public static double getAuroraStrength(double distance, double minRange, double maxRange, double peak) {
		if(distance <= minRange || distance >= maxRange)
			return 0.0D;
		double fade = (maxRange - minRange) * 0.15D;
		double edge = Math.min(distance - minRange, maxRange - distance);
		return peak * Math.min(1.0D, edge / fade);
	}

    /**
     * How much further than a band's own ranges any antenna, mode or path can carry it: the longest antenna reach
     * (a Yagi divides distance by 2), CW (by 1.5) and the biggest skip scale, with room to spare.
     */
    private static final double REACH_MARGIN = 4.0D;

    /**
     * Furthest a signal on {@code band} can possibly be heard, in blocks: beyond it nothing works out a strength, so a
     * transmission skips far stations with one distance check. Generous on purpose: it only saves work, never decides
     * whether a signal arrives.
     */
    public static double maxReach(Band band) {
        if(band == null)
            return 0.0D;
        // The radio horizon can stretch line of sight, and skip can hop several times (2026-11-18).
        double reach = Math.max(band.losRange() * com.arrl.radiocraft.RadiocraftServerConfig.TERRAIN_HORIZON_MAX_SCALE.get(),
                Math.max(band.maxSkipDay(), band.maxSkipNight()) * (double)com.arrl.radiocraft.RadiocraftServerConfig.SKIP_MAX_HOPS.get());
        if(band.isVhf())
            reach = Math.max(reach, Math.max(com.arrl.radiocraft.RadiocraftServerConfig.AURORA_VHF_MAX_RANGE.get(),
                    com.arrl.radiocraft.RadiocraftServerConfig.SPORADIC_E_MAX_RANGE.get()));
        return reach * REACH_MARGIN;
    }

    /** True if two antennas are too far apart for anything on {@code band} to reach (see {@link #maxReach}). */
    public static boolean outOfReach(Band band, net.minecraft.core.BlockPos a, net.minecraft.core.BlockPos b) {
        double reach = maxReach(band);
        return a.distSqr(b) > reach * reach;
    }

    public static double getWavelengthMetersFromFrequencyHertz(double frequencyHertz) {
        // The constant c represents the speed of light in a vacuum, which is exactly 299,792,458 meters per second.
        final double SPEED_OF_LIGHT_METERS_PER_SECOND = 299792458D;
        return SPEED_OF_LIGHT_METERS_PER_SECOND / frequencyHertz;
    }

    /**
     * Check if two frequencies are within a tolerance of each other.
     * @param a Frequency in Hz
     * @param b Frequency in Hz
     * @param tolerance Tolerance in Hz
     * @return Whether the frequencies are within tolerance of each other given the tolerance value (Hz)
     */
    public static boolean areFrequenciesEqualWithTolerance(float a, float b, float tolerance) {
        return Math.abs(a - b) < tolerance;
    }
}