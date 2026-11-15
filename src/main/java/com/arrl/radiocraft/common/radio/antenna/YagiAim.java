package com.arrl.radiocraft.common.radio.antenna;

/**
 * The yagi's aim (2026-11-12), the pure part: 16 directions in 22.5 degree steps, their split into the block's FACING
 * and TURN, and the beam pattern. No game classes, so it can be unit tested.
 */
public final class YagiAim {

	/** The TURN with no step off FACING (0 is 22.5 degrees anticlockwise, 2 and 3 22.5 and 45 clockwise). */
	public static final int STRAIGHT = 1;

	private YagiAim() {}

	/** The nearest of 16 steps (0 north, 4 east, 8 south, 12 west) to a compass bearing in degrees. */
	public static int step(double bearing) {
		return Math.floorMod((int)Math.round(bearing / 22.5D), 16);
	}

	/** A step 0..15 as {quadrant, turn}: quadrant 0 north, 1 east, 2 south, 3 west, and the turn as TURN counts it. */
	public static int[] split(int step) {
		int quadrant = Math.floorMod((step + 1) / 4, 4);
		int off = Math.floorMod(step - quadrant * 4 + 8, 16) - 8; // -1..2
		return new int[] { quadrant, off + STRAIGHT };
	}

	/** The compass bearing of a quadrant (0 north .. 3 west) and a turn. */
	public static double bearing(int quadrant, int turn) {
		double b = quadrant * 90.0D + (turn - STRAIGHT) * 22.5D;
		return (b % 360.0D + 360.0D) % 360.0D;
	}

	/** How well a yagi pointing at compass {@code bearing} works towards a world offset {@code dx, dz}. */
	public static double efficiency(double bearing, double dx, double dz) {
		double length = Math.hypot(dx, dz);
		if(length < 1.0E-6D)
			return 1.0D;
		double b = Math.toRadians(bearing);
		double dot = (Math.sin(b) * dx - Math.cos(b) * dz) / length; // Compass 0 is -Z (north), 90 is +X (east).
		double f = -(dot - 1.0D) / 2.0D; // 0 when same direction, 1 when opposite.
		if(f < 0.25D)
			return 1.0D + (0.1D - 1.0D) * (f / 0.25D); // Full on the facing side, down to a tenth at 45 degrees off.
		return f * 0.1D; // A tenth or less on the sides and rear.
	}
}
