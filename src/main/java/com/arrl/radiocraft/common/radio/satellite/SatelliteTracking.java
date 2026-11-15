package com.arrl.radiocraft.common.radio.satellite;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The satellite tracking antenna's sums (2026-11-12): how its az/el mount slews, when it counts as on the satellite, and
 * which antennas are on which satellite now. The pure part, so it can be tested without the game.
 * <p>
 * A powered tracker picks a satellite (the one its radio is tuned to, else the highest one up), turns and tilts toward
 * it at {@link #SLEW_DEGREES} a tick, and once within {@link #LOCK_DEGREES} it is locked: the satellite heard through it
 * is at full strength all pass long ({@link SatellitePattern#TRACKER}), through auroras and storms, and a stealth
 * satellite is heard at all. Unpowered or with nothing up, it stows, pointing straight up.
 */
public final class SatelliteTracking {

	/** How far the mount turns or tilts a tick, degrees. */
	public static final double SLEW_DEGREES = 3.0D;
	/** Within this of the satellite both ways, degrees, the tracker is locked on. */
	public static final double LOCK_DEGREES = 2.5D;
	/** Where an idle tracker points: straight up (stowed). */
	public static final double STOW_ELEVATION = 90.0D;
	/** A radio within this of a satellite's downlink or uplink counts as tuned to it, Hz (Doppler included). */
	public static final double TUNED_HZ = 15_000.0D;

	/** The satellite each locked tracker is on: antenna block position (as a long) to satellite name. Any thread. */
	private static final Map<Long, String> LOCKED = new ConcurrentHashMap<>();

	private SatelliteTracking() {}

	/** {@code from} moved toward {@code to} by at most {@code step}, the short way round the compass. */
	public static double slewAzimuth(double from, double to, double step) {
		double diff = azimuthDiff(from, to);
		double moved = Math.abs(diff) <= step ? to : from + Math.signum(diff) * step;
		return ((moved % 360.0D) + 360.0D) % 360.0D;
	}

	/** {@code from} moved toward {@code to} by at most {@code step}. */
	public static double slewElevation(double from, double to, double step) {
		double diff = to - from;
		return Math.abs(diff) <= step ? to : from + Math.signum(diff) * step;
	}

	/** The signed short way from {@code from} to {@code to} round the compass, -180..180 degrees. */
	public static double azimuthDiff(double from, double to) {
		double d = ((to - from) % 360.0D + 540.0D) % 360.0D - 180.0D;
		return d == -180.0D ? 180.0D : d;
	}

	/**
	 * True if a mount at {@code az, el} is on a satellite at {@code targetAz, targetEl}. Near the zenith the azimuth
	 * hardly matters (a dish pointing straight up sees the same sky whichever way it is turned).
	 */
	public static boolean onTarget(double az, double el, double targetAz, double targetEl) {
		return separation(az, el, targetAz, targetEl) <= LOCK_DEGREES;
	}

	/** The angle between two directions in the sky, degrees. */
	public static double separation(double az1, double el1, double az2, double el2) {
		double a1 = Math.toRadians(az1), e1 = Math.toRadians(el1), a2 = Math.toRadians(az2), e2 = Math.toRadians(el2);
		double dot = Math.cos(e1) * Math.cos(e2) * Math.cos(a1 - a2) + Math.sin(e1) * Math.sin(e2);
		return Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, dot))));
	}

	/** True if a radio on {@code radioHz} is tuned to a satellite on {@code downlinkHz} / {@code uplinkHz}. */
	public static boolean tunedTo(float radioHz, float downlinkHz, float uplinkHz) {
		return radioHz > 0.0F && (Math.abs(radioHz - downlinkHz) <= TUNED_HZ || Math.abs(radioHz - uplinkHz) <= TUNED_HZ);
	}

	// -------------------- WHO IS ON WHAT --------------------

	/** Notes the tracker at {@code antennaPos} is locked on {@code satellite} (null: on nothing). Server thread. */
	public static void setLocked(long antennaPos, String satellite) {
		if(satellite == null || satellite.isEmpty())
			LOCKED.remove(antennaPos);
		else
			LOCKED.put(antennaPos, satellite);
	}

	/** True if the tracker at {@code antennaPos} is locked on the satellite named {@code satellite} now. Any thread. */
	public static boolean lockedOn(long antennaPos, String satellite) {
		return satellite.equals(LOCKED.get(antennaPos));
	}

	/** The satellite the tracker at {@code antennaPos} is locked on, or null. */
	public static String lockedOn(long antennaPos) {
		return LOCKED.get(antennaPos);
	}

	/** Server stopped: no tracker is on anything. */
	public static void clear() {
		LOCKED.clear();
	}
}
