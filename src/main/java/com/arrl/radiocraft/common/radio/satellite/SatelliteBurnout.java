package com.arrl.radiocraft.common.radio.satellite;

/**
 * A satellite burning up as its orbit decays (2026-11-12, maintainer): a tiny one-pixel streak across the sky for
 * {@link #TICKS}, barely noticeable, seen only by a player it was above the horizon of at that moment. The server sends
 * each such player where it was and which way it was going ({@code CSatelliteBurnoutPacket}); the sky renderer plays
 * the streak from these sums. Pure arithmetic with no game classes, so it is unit tested.
 * <p>
 * During the streak the point under it runs {@link #TRACK_BLOCKS} on along its heading while it sinks to
 * {@link #SINK} of its height, flaring up quickly and fading out.
 */
public final class SatelliteBurnout {

	/** How long the streak lasts, ticks: 3 seconds. */
	public static final int TICKS = 60;
	/** How far the point under it runs during the streak, blocks. */
	public static final double TRACK_BLOCKS = 1_500.0D;
	/** The share of its elevation it keeps by the end of the streak. */
	public static final double SINK = 0.4D;

	private SatelliteBurnout() {}

	/** How far through the streak it is at {@code time} (game ticks), 0 to 1; -1 before it starts or after it ends. */
	public static double progress(long start, double time) {
		double p = (time - start) / TICKS;
		return p < 0.0D || p > 1.0D ? -1.0D : p;
	}

	/** True while the streak that began at {@code start} is still in the sky at {@code time}. */
	public static boolean burning(long start, double time) {
		return progress(start, time) >= 0.0D;
	}

	/** How bright it is {@code progress} through, 0 to 1: up in the first fifth, fading over the rest. */
	public static double brightness(double progress) {
		if(progress < 0.0D || progress > 1.0D)
			return 0.0D;
		return progress < 0.2D ? progress / 0.2D : (1.0D - progress) / 0.8D;
	}

	/** True if a player at {@code x, z} sees a burnout over {@code subX, subZ}: it is above their horizon. */
	public static boolean visible(double x, double z, double subX, double subZ) {
		return SatelliteOrbit.lookAt(x, z, subX, subZ).up();
	}

	/**
	 * Where the place {@code x, z} sees it {@code progress} through a streak that began over {@code subX, subZ} heading
	 * {@code heading} (radians from +X towards +Z).
	 */
	public static SatelliteOrbit.Look look(double x, double z, double subX, double subZ, double heading, double progress) {
		double p = Math.max(0.0D, Math.min(1.0D, progress));
		double along = TRACK_BLOCKS * p;
		SatelliteOrbit.Look look = SatelliteOrbit.lookAt(x, z, subX + along * Math.cos(heading), subZ + along * Math.sin(heading));
		double elevation = look.elevation() * (1.0D - (1.0D - SINK) * p);
		return new SatelliteOrbit.Look(elevation, look.azimuth(), look.distance(), look.subX(), look.subZ());
	}
}
