package com.arrl.radiocraft.common.radio.satellite;

/**
 * Doppler shift of a satellite's signals (2026-11-12, a server option, off by default). Pure arithmetic over
 * {@link SatelliteOrbit}'s round Earth, so it is unit tested.
 * <p>
 * The satellite flies at the height that puts its horizon {@link SatelliteOrbit#COVERAGE} away. A station's slant range
 * to it is worked out from the ground distance to the point under it; how fast that range changes, as a share of the
 * satellite's own speed, scales a real low orbit's 7.5 km/s. A signal from an approaching satellite is heard high
 * (positive shift), at closest approach on frequency, and low once it recedes: on 2m about +/-3.4 kHz at the horizons
 * of a high pass, enough to need retuning at the ends of a pass with an FM radio (it hears 2 kHz off).
 */
public final class SatelliteDoppler {

	private SatelliteDoppler() {}

	/** Speed of light, m/s. */
	public static final double C = 299_792_458.0D;
	/** A low Earth orbit's speed, m/s. */
	public static final double ORBITAL_SPEED = 7_500.0D;
	/** The satellite's distance from the Earth's centre, blocks. */
	public static final double ORBIT_RADIUS = SatelliteOrbit.EARTH_RADIUS / Math.cos(SatelliteOrbit.COVERAGE / SatelliteOrbit.EARTH_RADIUS);
	/** Ticks between the two looks the range rate is taken from. */
	public static final int SAMPLE_TICKS = 20;
	/** The point under it never jumps this far in {@link #SAMPLE_TICKS}: a jump means it wrapped to its next copy. */
	private static final double WRAP_JUMP = SatelliteOrbit.COVERAGE;

	/** Straight-line distance, blocks, from a station to a satellite over a point {@code groundDistance} blocks away. */
	public static double slantRange(double groundDistance) {
		double angle = Math.max(0.0D, groundDistance) / SatelliteOrbit.EARTH_RADIUS;
		double r = SatelliteOrbit.EARTH_RADIUS, s = ORBIT_RADIUS;
		return Math.sqrt(Math.max(0.0D, r * r + s * s - 2.0D * r * s * Math.cos(angle)));
	}

	/**
	 * How fast the range changes as a share of the satellite's speed (-1 to 1; below 0 approaching), from the ground
	 * distance {@code before} to {@code after} while the point under it moved {@code moved} blocks. 0 if it did not
	 * move (pinned still) or jumped (wrapped).
	 */
	public static double rangeRate(double before, double after, double moved) {
		if(moved <= 1.0E-6D || moved >= WRAP_JUMP)
			return 0.0D;
		double travelled = moved * ORBIT_RADIUS / SatelliteOrbit.EARTH_RADIUS; // Along its orbit, higher up.
		return Math.max(-1.0D, Math.min(1.0D, (slantRange(after) - slantRange(before)) / travelled));
	}

	/** The range rate from two looks {@link #SAMPLE_TICKS} apart from one place. */
	public static double rangeRate(SatelliteOrbit.Look before, SatelliteOrbit.Look after) {
		double moved = Math.hypot(after.subX() - before.subX(), after.subZ() - before.subZ());
		return rangeRate(before.distance(), after.distance(), moved);
	}

	/** The shift, Hz, of a signal on {@code hz} at range rate {@code rangeRate}: positive while it approaches. */
	public static double shiftHz(double hz, double rangeRate) {
		return -hz * ORBITAL_SPEED / C * rangeRate;
	}

	/** The largest shift a signal on {@code hz} can have, Hz. */
	public static double maxShiftHz(double hz) {
		return hz * ORBITAL_SPEED / C;
	}
}
