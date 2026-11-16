package com.arrl.radiocraft.common.radio.satellite;

/**
 * The handheld Yagi's beam (maintainer, 2026-11-15), the pure part: a three-element 2 m beam held in the offhand that
 * works a satellite only while its holder looks roughly towards it. No game classes, so it can be unit tested.
 * <p>
 * On target (within {@link #FULL_DEGREES} of the satellite) it hears and is heard at {@link #ON_TARGET} whatever the
 * elevation (a pointed beam has no null overhead), but for the fade at the horizon ({@link SatellitePattern#rise}).
 * Further off it fades to {@link #OFF_TARGET} at {@link #LOST_DEGREES}: the uplink
 * ({@link SatellitePattern#UPLINK_FLOOR}) goes at about {@value #UPLINK_LOST_ABOUT} degrees off.
 */
public final class HandheldYagiAim {

	/** Strength on target, before the horizon fade and space weather. */
	public static final double ON_TARGET = 0.95D;
	/** What is left well off target (the beam's back and sides). */
	public static final double OFF_TARGET = 0.05D;
	/** Degrees off the satellite still on target. */
	public static final double FULL_DEGREES = 20.0D;
	/** Degrees off the satellite from which only {@link #OFF_TARGET} is left. */
	public static final double LOST_DEGREES = 45.0D;
	/** About where the uplink is lost, degrees off (for the docs). */
	public static final int UPLINK_LOST_ABOUT = 36;

	private HandheldYagiAim() {}

	/**
	 * Degrees between where a satellite is (compass {@code azimuth}, {@code elevation} up) and where the holder looks
	 * (compass {@code bearing}, {@code pitch} up), 0 to 180.
	 */
	public static double offDegrees(double azimuth, double elevation, double bearing, double pitch) {
		double[] a = unit(azimuth, elevation), b = unit(bearing, pitch);
		double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
		return Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, dot))));
	}

	/** How much of the beam is on the satellite {@code off} degrees away: 1 on target, falling to {@link #OFF_TARGET}. */
	public static double aim(double off) {
		if(off <= FULL_DEGREES)
			return 1.0D;
		if(off >= LOST_DEGREES)
			return OFF_TARGET / ON_TARGET;
		double t = (off - FULL_DEGREES) / (LOST_DEGREES - FULL_DEGREES);
		double smooth = t * t * (3.0D - 2.0D * t);
		return 1.0D - smooth * (1.0D - OFF_TARGET / ON_TARGET);
	}

	/** The strength at which the beam hears a satellite {@code elevation} degrees up and {@code off} degrees off its aim. */
	public static double strength(double elevation, double off) {
		return SatellitePattern.rise(Math.max(0.0D, Math.min(90.0D, elevation))) * ON_TARGET * aim(off);
	}

	/** Minecraft's yaw (0 south, 90 west) as a compass bearing (0 north, 90 east). */
	public static double bearingOfYaw(double yaw) {
		return ((yaw + 180.0D) % 360.0D + 360.0D) % 360.0D;
	}

	/** Minecraft's pitch (negative looking up) as degrees above the horizon. */
	public static double elevationOfPitch(double pitch) {
		return -pitch;
	}

	/** A unit vector (east, up, north) for a compass bearing and an elevation. */
	private static double[] unit(double bearing, double elevation) {
		double b = Math.toRadians(bearing), e = Math.toRadians(elevation);
		return new double[] { Math.sin(b) * Math.cos(e), Math.sin(e), Math.cos(b) * Math.cos(e) };
	}
}
