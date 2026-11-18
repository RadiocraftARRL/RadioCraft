package com.arrl.radiocraft.common.radio;

import net.minecraft.world.level.Level;

/**
 * How far into day it is for propagation (2026-11-18): 1 with the sun up, 0 at night, and in between through sunrise
 * and sunset, following the sun's height, so skip ranges and band conditions move across dawn and dusk instead of
 * snapping when vanilla's {@code isDay()} flips. The ramp is the stretch where the sky brightens or darkens (the sun
 * within about 15 degrees of the horizon, near 1,900 ticks each way); {@code isDay()} flips about two thirds of the way
 * up it. Rain does not count: it darkens the sky, not the ionosphere.
 */
public final class DayCycle {

	private DayCycle() {}

	/** {@link #dayness(double)} for {@code level} now. A dimension with a fixed time stays where its sun is. */
	public static double dayness(Level level) {
		return dayness(level.getTimeOfDay(1.0F));
	}

	/**
	 * Day for the sun at {@code timeOfDay} ({@code DimensionType.timeOfDay}: 0 at noon, 0.5 at midnight): 0 to 1,
	 * eased at both ends.
	 */
	public static double dayness(double timeOfDay) {
		double sun = Math.cos(timeOfDay * Math.PI * 2.0D);
		double x = Math.max(0.0D, Math.min(1.0D, (sun + 0.25D) / 0.5D));
		return x * x * (3.0D - 2.0D * x);
	}

	/** {@code night} to {@code day} by {@code dayness}. */
	public static double blend(double night, double day, double dayness) {
		return night + (day - night) * dayness;
	}
}
