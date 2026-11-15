package com.arrl.radiocraft.common.radio.satellite;

import java.util.Random;

/**
 * Where a geostationary satellite parks (2026-11-12, maintainer): it stays put in the sky. Its coverage is centred on
 * where its owner first activated it ({@link #aimX}, {@link #aimZ}): it is up, and heard, within
 * {@link SatelliteOrbit#COVERAGE} of there, and it images there. Where it hangs in the sky is a point near that
 * ({@link #parkX}, {@link #parkZ}), off by a random {@link #PARK_MIN} to {@link #PARK_MAX} blocks, so two bought in one
 * place do not sit on one spot. Pure arithmetic with no game classes, so it is unit tested.
 * <p>
 * The elevation is worked out as for an orbiting satellite ({@link SatelliteOrbit#elevation}) over the parked point,
 * with the distance scaled by {@code COVERAGE / (COVERAGE + offset)} so that it is above the horizon everywhere in its
 * coverage (even on the far side from where it parks) and never anywhere else nearer the horizon than it must be.
 *
 * @param aimX  Where it points (its owner activated it), x.
 * @param aimZ  And z.
 * @param parkX The point it hangs over, x.
 * @param parkZ And z.
 */
public record SatelliteGeo(double aimX, double aimZ, double parkX, double parkZ) {

	/** The nearest and farthest it parks from where it points, blocks (about 80 to 50 degrees up there). */
	public static final double PARK_MIN = 150.0D, PARK_MAX = 750.0D;
	/** An imaging geostationary satellite scans a strip through where it points, this far each way, blocks. */
	public static final double SCAN_HALF = 2_000.0D;

	/** A new parking place for one activated at {@code aimX, aimZ}. */
	public static SatelliteGeo park(double aimX, double aimZ, Random random) {
		double angle = random.nextDouble() * Math.PI * 2.0D;
		double distance = PARK_MIN + random.nextDouble() * (PARK_MAX - PARK_MIN);
		return new SatelliteGeo(aimX, aimZ, aimX + Math.cos(angle) * distance, aimZ + Math.sin(angle) * distance);
	}

	/** How far it parks from where it points, blocks. */
	public double offset() {
		return Math.hypot(parkX - aimX, parkZ - aimZ);
	}

	/**
	 * What the place {@code x, z} sees of it, at any time: {@link SatelliteOrbit.Look#up} within the coverage of where it
	 * points; the elevation and azimuth of where it parks; the point under it where it parks.
	 */
	public SatelliteOrbit.Look look(double x, double z) {
		double dx = parkX - x, dz = parkZ - z;
		double toPark = Math.hypot(dx, dz);
		double toAim = Math.hypot(aimX - x, aimZ - z);
		double scaled = toPark * SatelliteOrbit.COVERAGE / (SatelliteOrbit.COVERAGE + offset());
		return new SatelliteOrbit.Look(SatelliteOrbit.elevation(scaled), SatelliteOrbit.azimuth(dx, dz), toAim, parkX, parkZ);
	}

	/**
	 * Where an imaging one's scan line is at {@code time} (game ticks), {@code {x, z}}: running along {@code heading} at
	 * {@code speed} blocks a tick from {@link #SCAN_HALF} before where it points to as far past it, then again from the
	 * start (as a weather satellite scans its disc).
	 */
	public double[] scan(double time, double heading, double speed) {
		double span = 2.0D * SCAN_HALF;
		double along = ((time * speed) % span + span) % span - SCAN_HALF;
		return new double[] { aimX + along * Math.cos(heading), aimZ + along * Math.sin(heading) };
	}
}
