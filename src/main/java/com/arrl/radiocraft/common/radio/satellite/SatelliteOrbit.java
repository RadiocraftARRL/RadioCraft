package com.arrl.radiocraft.common.radio.satellite;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Where a satellite is, from the world time alone (2026-11-11, {@code doc/SatellitePlan.md}): no entity, nothing to keep
 * loaded. Pure arithmetic with no game classes, so it is unit tested.
 * <p>
 * The flat world is treated as tiled, {@link #TILE} blocks square, in a frame turned by the orbit's {@link #heading}:
 * the point under the satellite runs along {@code u} {@link #WRAPS} tiles a day and drifts one tile along {@code w}, so
 * its track crosses the tile in lines about 6,000 blocks apart, moving over by one line each time round (as the Earth
 * turns under a real orbit). Every place is within {@link #COVERAGE} of one or two of those lines, so it sees 1 or 2
 * passes a Minecraft day, up to about two minutes each, some high and some low, at different times in different places.
 * It is in view about a seventh of the day and out of view most of each orbit, as a real satellite is (2026-11-14, the
 * maintainer: it was up half the day; 6 orbits a day and 10,000 blocks of coverage before; a real LEO pass is ~15
 * minutes of a ~100 minute orbit, but on a 20 minute day those proportions leave passes too short to copy a picture). Copies
 * of the satellite in the next tiles are {@link #TILE} apart, more than the coverage across, so a place never sees two
 * at once. {@link #drift} makes the day of the orbit a little longer or shorter than the Minecraft day, so the passes
 * come at another time each day.
 * <p>
 * Elevation is worked out as over a round Earth of radius {@link #EARTH_RADIUS} blocks with the satellite high enough
 * that its horizon is {@link #COVERAGE} away: straight up over the point under it, 0 at the edge of the coverage.
 *
 * @param heading     Which way the track runs, radians from +X towards +Z.
 * @param alongPhase  Where along the track it is at time 0, blocks (0 to {@link #TILE}).
 * @param crossPhase  Where across it, blocks (0 to {@link #TILE}).
 * @param drift       How much longer (or shorter, below 0) its day is than the Minecraft day: -0.08 to 0.08.
 */
public record SatelliteOrbit(double heading, double alongPhase, double crossPhase, double drift) {

	/** Heard this far from the point under it, blocks (the plan's 10,000 until 2026-11-14). */
	public static final double COVERAGE = 5_000.0D;
	/** The tile the world is folded into, blocks: more than the coverage across. */
	public static final double TILE = 24_000.0D;
	/** Times round along the track per orbit day. */
	public static final int WRAPS = 4;
	/** A Minecraft day, ticks. */
	public static final double DAY_TICKS = 24_000.0D;
	/**
	 * The Earth the elevation is worked out over, blocks: the coverage is about 19 degrees of it. Halved with the coverage
	 * (2026-11-14), so elevation against the share of the coverage away, and all that hangs on elevation, is as before.
	 */
	public static final double EARTH_RADIUS = 15_000.0D;
	/** The largest drift {@link #random} picks. */
	public static final double MAX_DRIFT = 0.08D;

	private static final double HORIZON_ANGLE = COVERAGE / EARTH_RADIUS;
	private static final double RHO = Math.cos(HORIZON_ANGLE);

	/** What a place sees of the satellite at one moment. */
	public record Look(double elevation, double azimuth, double distance, double subX, double subZ) {

		/** True while it is above this place's horizon (in coverage). */
		public boolean up() {
			return distance < COVERAGE;
		}
	}

	/**
	 * One pass over a place: it rises at {@code rise} from {@code riseAzimuth}, is highest at {@code culmination}
	 * ({@code maxElevation} degrees) and sets at {@code set} (game ticks).
	 */
	public record Pass(double rise, double culmination, double set, double maxElevation, double riseAzimuth) {

		/** True if it is up at {@code time}. */
		public boolean during(double time) {
			return time >= rise && time < set;
		}

		/** How long it is up, ticks. */
		public double duration() {
			return set - rise;
		}
	}

	/** A new orbit with a random heading, phase and drift. */
	public static SatelliteOrbit random(Random random) {
		return new SatelliteOrbit(random.nextDouble() * Math.PI, random.nextDouble() * TILE, random.nextDouble() * TILE,
				(random.nextDouble() * 2.0D - 1.0D) * MAX_DRIFT);
	}

	private double speedFactor() {
		return 1.0D / (1.0D + drift);
	}

	/** Blocks per tick along the track frame's u. */
	private double vu() {
		return WRAPS * TILE / DAY_TICKS * speedFactor();
	}

	/** Blocks per tick along w. */
	private double vw() {
		return TILE / DAY_TICKS * speedFactor();
	}

	/** Blocks per tick along its track (SATTRACK's Location view, 2026-11-13). */
	public double alongSpeed() {
		return vu();
	}

	/** Blocks per tick across its track, as the world turns under it. */
	public double acrossSpeed() {
		return vw();
	}

	/**
	 * Where it is from the place {@code x, z} in its track's frame, {@code {along, across}} blocks, each -{@link #TILE}/2
	 * to {@link #TILE}/2: along runs once round the world (one wrap) and back; it is up while the two are within
	 * {@link #COVERAGE} together (SATTRACK's Location view, 2026-11-13).
	 */
	public double[] track(double x, double z, double time) {
		return relative(x, z, time);
	}

	/** How fast the point under it moves over the ground, blocks per tick. */
	public double groundSpeed() {
		return Math.hypot(vu(), vw());
	}

	private static double wrap(double a) {
		double r = (a + TILE / 2.0D) % TILE;
		if(r < 0.0D)
			r += TILE;
		return r - TILE / 2.0D;
	}

	/** The nearest copy of the point under it, relative to the place, in the track frame {@code {du, dw}}. */
	private double[] relative(double x, double z, double time) {
		double cos = Math.cos(heading), sin = Math.sin(heading);
		double uo = x * cos + z * sin, wo = -x * sin + z * cos;
		return new double[] { wrap(alongPhase + vu() * time - uo), wrap(crossPhase + vw() * time - wo) };
	}

	/** {@code {du, dw}} in the track frame as world {@code {dx, dz}}. */
	private double[] toWorld(double du, double dw) {
		double cos = Math.cos(heading), sin = Math.sin(heading);
		return new double[] { du * cos - dw * sin, du * sin + dw * cos };
	}

	/** What the place at {@code x, z} sees at {@code time} (game ticks). */
	public Look look(double x, double z, double time) {
		double[] r = relative(x, z, time);
		double[] d = toWorld(r[0], r[1]);
		return lookAt(x, z, x + d[0], z + d[1]);
	}

	/**
	 * A pass played out over {@code cx, cz} between game ticks {@code from} and {@code until} (an admin's
	 * {@code overhead}, 2026-11-11: it used to hang still): the point under it runs straight along {@code heading} from
	 * one edge of the coverage to the other, set {@code seed}'s way to one side so two satellites never sit on one spot,
	 * and so it rises, crosses near the point and sets as a real pass does.
	 */
	public static Look passOver(double x, double z, double cx, double cz, long from, long until, double time, double heading, int seed) {
		double progress = Math.max(0.0D, Math.min(1.0D, (time - from) / Math.max(1.0D, until - from)));
		double side = (Math.floorMod(seed, 1000) / 999.0D * 2.0D - 1.0D) * COVERAGE * 0.12D; // Up to 1,200 blocks off: slightly apart, still high.
		double reach = COVERAGE * 0.98D;
		double along = (2.0D * progress - 1.0D) * Math.sqrt(reach * reach - side * side); // From horizon to horizon, offset and all.
		double cos = Math.cos(heading), sin = Math.sin(heading);
		return lookAt(x, z, cx + along * cos - side * sin, cz + along * sin + side * cos);
	}

	/** What the place at {@code x, z} sees of a satellite over {@code subX, subZ}. */
	public static Look lookAt(double x, double z, double subX, double subZ) {
		double dx = subX - x, dz = subZ - z;
		double distance = Math.hypot(dx, dz);
		return new Look(elevation(distance), azimuth(dx, dz), distance, subX, subZ);
	}

	/** Elevation in degrees of a satellite over a point {@code distance} blocks away: 90 overhead, 0 at the coverage edge, below 0 past it. */
	public static double elevation(double distance) {
		double angle = Math.max(0.0D, distance) / EARTH_RADIUS;
		return Math.toDegrees(Math.atan2(Math.cos(angle) - RHO, Math.sin(angle)));
	}

	/** Compass bearing in degrees (0 north, which is -Z; 90 east, +X) of a world offset. */
	public static double azimuth(double dx, double dz) {
		if(dx == 0.0D && dz == 0.0D)
			return 0.0D;
		double a = Math.toDegrees(Math.atan2(dx, -dz));
		return a < 0.0D ? a + 360.0D : a;
	}

	/** "N", "NE" ... for a compass bearing. */
	public static String compass(double azimuth) {
		String[] points = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };
		return points[(int)Math.floor(((azimuth % 360.0D + 360.0D) % 360.0D + 22.5D) / 45.0D) % 8];
	}

	/**
	 * The passes over {@code x, z} that are up at some time between {@code from} and {@code to} (game ticks), in the
	 * order they rise; one already up at {@code from} is first. Closed form: between wraps the point under it moves in a
	 * straight line, so each copy of it passes at most once, nearest at a time worked out directly.
	 */
	public List<Pass> passes(double x, double z, double from, double to) {
		List<Pass> out = new ArrayList<>();
		if(to <= from)
			return out;
		double vu = vu(), vw = vw(), v2 = vu * vu + vw * vw, speed = Math.sqrt(v2);
		double[] r = relative(x, z, from);
		int along = (int)Math.ceil(vu * (to - from) / TILE) + 1, across = (int)Math.ceil(vw * (to - from) / TILE) + 1;
		for(int i = -along; i <= 1; i++)
			for(int j = -across; j <= 1; j++) {
				double ru = r[0] + i * TILE, rw = r[1] + j * TILE;
				double culmination = from - (ru * vu + rw * vw) / v2;
				double cu = ru + vu * (culmination - from), cw = rw + vw * (culmination - from);
				double nearest = Math.hypot(cu, cw);
				if(nearest >= COVERAGE)
					continue;
				double half = Math.sqrt(COVERAGE * COVERAGE - nearest * nearest) / speed;
				double rise = culmination - half, set = culmination + half;
				if(set <= from || rise >= to)
					continue;
				double[] atRise = toWorld(ru + vu * (rise - from), rw + vw * (rise - from));
				out.add(new Pass(rise, culmination, set, elevation(nearest), azimuth(atRise[0], atRise[1])));
			}
		out.sort((a, b) -> Double.compare(a.rise(), b.rise()));
		return out;
	}

	/** The next {@code count} passes over {@code x, z} from {@code from} on (one up now first), looking up to two days ahead. */
	public List<Pass> nextPasses(double x, double z, double from, int count) {
		List<Pass> all = passes(x, z, from, from + 2.0D * DAY_TICKS);
		return all.size() > count ? new ArrayList<>(all.subList(0, count)) : all;
	}
}
