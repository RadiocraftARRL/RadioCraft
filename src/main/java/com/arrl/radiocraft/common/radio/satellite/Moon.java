package com.arrl.radiocraft.common.radio.satellite;

/**
 * The Moon as an EME (moonbounce) reflector (2026-11-12): where it is and how well it reflects. The pure part, so it
 * can be tested without the game.
 * <p>
 * Minecraft's moon is always opposite the sun and crosses the sky from east (+X) to west (-X) straight overhead, the
 * same for every player wherever they are: it is up from dusk to dawn everywhere at once. So, unlike the real Moon,
 * every station in the world shares the whole night. What comes back is very weak and fades slowly in and out
 * (libration fading): CW is just readable, close to the noise.
 */
public final class Moon {

	/** The name SATTRACK lists it under, and a tracker follows it by. */
	public static final String NAME = "MOON";
	/** The echo's delay, ticks: 2.5 seconds, as for the real Moon. */
	public static final int ECHO_TICKS = 50;
	/** Below this elevation, degrees, the path is lost in the ground noise and the trees. */
	public static final double MIN_ELEVATION = 5.0D;
	/** The echo's strength at the top of a fade and at the bottom. */
	public static final double PEAK = 0.16D, FLOOR = 0.015D;
	/** The two slow fades that make up the fading, ticks per cycle. */
	private static final double FADE_SLOW = 900.0D, FADE_FAST = 230.0D;

	private Moon() {}

	/**
	 * Where the Moon is at the celestial angle {@code timeOfDay} (Minecraft's {@code getTimeOfDay}: 0 with the sun
	 * overhead at noon, 0.5 at midnight with the Moon overhead): elevation and compass azimuth, degrees.
	 */
	public static SatelliteOrbit.Look look(double timeOfDay) {
		double a = timeOfDay * 2.0D * Math.PI;
		double east = Math.sin(a), up = -Math.cos(a); // Opposite the sun, which is at (-sin a, cos a) east/up.
		double elevation = Math.toDegrees(Math.asin(Math.max(-1.0D, Math.min(1.0D, up))));
		double azimuth = east >= 0.0D ? 90.0D : 270.0D;
		return new SatelliteOrbit.Look(elevation, azimuth, 0.0D, 0.0D, 0.0D);
	}

	/** True if the Moon at {@code timeOfDay} is high enough for a moonbounce path. */
	public static boolean usable(double timeOfDay) {
		return look(timeOfDay).elevation() >= MIN_ELEVATION;
	}

	/**
	 * How strong the echo is at {@code gameTime}: between {@link #FLOOR} and {@link #PEAK}, fading slowly in and out
	 * as two slow swings beat together. {@code seed} varies it from night to night.
	 */
	public static double echo(double gameTime, long seed) {
		double p1 = (seed % 997L) / 997.0D * 2.0D * Math.PI, p2 = (seed % 613L) / 613.0D * 2.0D * Math.PI;
		double slow = 0.5D + 0.5D * Math.sin(gameTime / FADE_SLOW * 2.0D * Math.PI + p1);
		double fast = 0.5D + 0.5D * Math.sin(gameTime / FADE_FAST * 2.0D * Math.PI + p2);
		double depth = slow * (0.35D + 0.65D * fast);
		return FLOOR + (PEAK - FLOOR) * depth;
	}
}
