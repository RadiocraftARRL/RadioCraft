package com.arrl.radiocraft.common.radio.satellite.imaging;

/**
 * The geometry of a weather satellite's scan (2026-11-12, {@code doc/SatellitePlan.md}, "Weather imaging"): like a NOAA
 * APT imager it sweeps a line across its ground track, {@link #PIXELS} pixels of {@link #BLOCKS_PER_PIXEL} blocks
 * ({@link #SWATH_BLOCKS} across), and moves on one line as the point under it covers another {@link #BLOCKS_PER_PIXEL}
 * blocks, so a pass (up to 10,000 blocks of track) makes a long strip of square pixels. Widened 2026-11-14 (256 pixels of
 * 8 blocks before) with the fewer, shorter passes of {@code SatelliteOrbit}, so a pass sees most of the ground it is up
 * over, as a real APT swath does, and storms between passes are not missed so often.
 * <p>
 * A line is placed by its centre (the point under the satellite) and its {@code right} unit vector: across the track,
 * to the right of someone looking along it. Pixel 0 is on the left, as a picture of the ground seen from above with the
 * satellite flying up the page. Pure, unit tested.
 */
public final class AptSwath {

	/** Pixels in one scan line. */
	public static final int PIXELS = 512;
	/** Blocks one pixel covers, across and along the track. */
	public static final int BLOCKS_PER_PIXEL = 16;
	/** How wide the swath is, blocks. */
	public static final int SWATH_BLOCKS = PIXELS * BLOCKS_PER_PIXEL;
	/** The ground speed assumed when there is none to measure (a satellite an admin holds still), blocks a tick. */
	public static final double DEFAULT_SPEED = 4.0D;

	private AptSwath() {}

	/** How far pixel {@code i}'s centre is from the line's centre, blocks (below 0 to the left). */
	public static double offset(int i) {
		return (i - (PIXELS - 1) / 2.0D) * BLOCKS_PER_PIXEL;
	}

	/** World x of pixel {@code i}'s centre on the line through {@code cx} with right vector x part {@code rx}. */
	public static double x(double cx, double rx, int i) {
		return cx + offset(i) * rx;
	}

	/** World z of pixel {@code i}'s centre on the line through {@code cz} with right vector z part {@code rz}. */
	public static double z(double cz, double rz, int i) {
		return cz + offset(i) * rz;
	}

	/**
	 * The unit vector across a track moving {@code dx, dz} (blocks a tick), to the right of the way it goes: north
	 * (-z) gives east (+x). East when it is not moving.
	 */
	public static double[] right(double dx, double dz) {
		double length = Math.hypot(dx, dz);
		if(length < 1.0E-9D)
			return new double[] { 1.0D, 0.0D };
		return new double[] { -dz / length, dx / length };
	}

	/** The unit vector along the track for a line whose right vector is {@code rx, rz}. */
	public static double[] along(double rx, double rz) {
		return new double[] { rz, -rx };
	}

	/** Ticks between lines for a satellite whose point below moves {@code speed} blocks a tick. */
	public static double lineTicks(double speed) {
		return BLOCKS_PER_PIXEL / (speed > 0.1D ? speed : DEFAULT_SPEED);
	}
}
