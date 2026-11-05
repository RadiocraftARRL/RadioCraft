package com.arrl.radiocraft.common.radio.adsb;

/**
 * Works out an ADS-B beacon's velocity from where it was at each report (about once a second): its ground speed, the
 * track it is making good and its climb or descent. A real transponder takes these from its GPS; here they come from
 * the craft's real place (a Sable sub-level moves its blocks without telling them). Smoothed a little, so one jerky
 * second does not swing the track. Slower than {@link #MOVING} along the ground the track is unknown, and the
 * heading given with {@link #update} (where the craft's nose points) is used instead, as an aircraft on the ground
 * reports its heading. A jump of more than {@link #TELEPORT} a second, or a report after a long gap, starts afresh.
 * Pure, so it is unit tested.
 */
public final class AdsbMotion {

	/** Ground speed below which a craft is standing (blocks a second, about 1 kt). */
	public static final double MOVING = 0.5D;
	/** Faster than this (blocks a second) is a teleport or a world reload, not flight. */
	public static final double TELEPORT = 400.0D;
	/** The longest gap (seconds) between two reports that are still compared. */
	public static final double LONGEST_GAP = 10.0D;
	/** Weight of the newest second in the smoothed velocity. */
	public static final double SMOOTHING = 0.5D;

	private boolean primed = false;
	private double lastX, lastY, lastZ;
	private long lastTick;
	private double vx, vy, vz;
	private double heading = 0.0D;

	/**
	 * A new report: the beacon is at {@code x, y, z} at game time {@code tick}; its nose points at
	 * {@code noseHeading} degrees (used while it stands still; NaN for no idea).
	 */
	public void update(double x, double y, double z, long tick, double noseHeading) {
		double seconds = (tick - lastTick) / 20.0D;
		boolean fresh = !primed || seconds <= 0.0D || seconds > LONGEST_GAP;
		if(!fresh) {
			double ix = (x - lastX) / seconds, iy = (y - lastY) / seconds, iz = (z - lastZ) / seconds;
			if(Math.sqrt(ix * ix + iy * iy + iz * iz) > TELEPORT)
				fresh = true;
			else {
				vx += (ix - vx) * SMOOTHING;
				vy += (iy - vy) * SMOOTHING;
				vz += (iz - vz) * SMOOTHING;
			}
		}
		if(fresh) {
			vx = vy = vz = 0.0D;
			primed = true;
		}
		lastX = x;
		lastY = y;
		lastZ = z;
		lastTick = tick;
		if(groundSpeed() >= MOVING)
			heading = AdsbMath.bearing(vx, vz);
		else if(!Double.isNaN(noseHeading))
			heading = normalise(noseHeading);
	}

	/** Forgets the past: the next report starts afresh (the beacon was switched off, or moved by hand). */
	public void reset() {
		primed = false;
		vx = vy = vz = 0.0D;
	}

	/** Speed along the ground, blocks a second. */
	public double groundSpeed() {
		return Math.sqrt(vx * vx + vz * vz);
	}

	/** Climb (positive) or descent, blocks a second. */
	public double verticalRate() {
		return vy;
	}

	/** The track made good while moving, else the nose heading: compass degrees. */
	public double heading() {
		return heading;
	}

	/** True once moving along the ground fast enough for a track. */
	public boolean moving() {
		return groundSpeed() >= MOVING;
	}

	private static double normalise(double degrees) {
		double out = degrees % 360.0D;
		if(out < 0.0D)
			out += 360.0D;
		return out >= 360.0D ? 0.0D : out;
	}
}
