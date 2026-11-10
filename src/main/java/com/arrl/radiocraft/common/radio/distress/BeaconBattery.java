package com.arrl.radiocraft.common.radio.distress;

import java.util.Locale;

/**
 * The distress beacon's battery (2026-11-09): one Minecraft day of transmitting, counted down while the beacon is on
 * and ticking (in an inventory or floating as an item), then it is spent for good. An alert burst goes out when it is
 * switched on and every {@link #BURST_TICKS} after, which falls where the remaining time is a whole number of bursts.
 * Pure, so it is unit tested.
 */
public final class BeaconBattery {

	private BeaconBattery() {}

	/** Ticks a beacon transmits for: one Minecraft day. */
	public static final int LIFE_TICKS = 24_000;
	/** Ticks the battery is counted down by at a time (once a second keeps the item's data from changing every tick). */
	public static final int STEP_TICKS = 20;
	/** Ticks between alert bursts: 50 seconds. */
	public static final int BURST_TICKS = 1_000;

	/** {@code remaining} ticks after {@code ticks} more of transmitting, never below 0. */
	public static int drain(int remaining, int ticks) {
		return Math.max(0, remaining - Math.max(0, ticks));
	}

	/** True once nothing is left. */
	public static boolean spent(int remaining) {
		return remaining <= 0;
	}

	/** True if counting down from {@code before} to {@code after} passed a burst (and the battery is not spent). */
	public static boolean burstDue(int before, int after) {
		if(after <= 0 || after >= before)
			return false;
		return Math.floorDiv(before - 1, BURST_TICKS) != Math.floorDiv(after - 1, BURST_TICKS);
	}

	/** "23:59" hours and minutes of real time left, rounded up to the minute. */
	public static String remainingText(int remaining) {
		int seconds = (Math.max(0, remaining) + 19) / 20;
		int minutes = (seconds + 59) / 60;
		return String.format(Locale.ROOT, "%d:%02d", minutes / 60, minutes % 60);
	}

	/** Share of the battery left, 0 to 1. */
	public static float share(int remaining) {
		return Math.max(0.0F, Math.min(1.0F, remaining / (float)LIFE_TICKS));
	}
}
