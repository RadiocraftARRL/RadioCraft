package com.arrl.radiocraft.common.radio.satellite;

/**
 * How well a station's antenna hears a satellite (and is heard by it) at each elevation (2026-11-12). The pure part,
 * so it can be tested without the game.
 * <ul>
 * <li>{@link #EGGBEATER}: the EB-VHF, made for satellites. Circularly polarised and aimed at the sky, it is strong all
 * pass long, best overhead.</li>
 * <li>{@link #VERTICAL}: a J-pole, slim jim, ringo, whip or handheld. Fine low in the pass, but a vertical has a null
 * straight up: from about 40 degrees the satellite fades, and within a few degrees of overhead the uplink is lost.</li>
 * <li>{@link #OTHER}: anything else (yagis, the dish, wire antennas): the plain curve, stronger as it climbs.</li>
 * <li>{@link #HANDHELD_YAGI}: the handheld Yagi in a player's offhand (2026-11-15). This is its strength on target; how far
 * its holder looks off the satellite takes off more ({@link HandheldYagiAim}).</li>
 * <li>{@link #TRACKER}: the satellite tracking antenna while it is locked on this satellite ({@link SatelliteTracking}):
 * a dish following it, full strength all pass long (but for the fade at the horizon) and untouched by space weather.</li>
 * </ul>
 * Space weather (2026-11-12): an aurora over the station makes the signal flutter and fade
 * ({@link #AURORA_FADE}), a radio blackout or a thunderstorm takes off more ({@link #STORM_FADE}); low in a pass that can
 * cost the uplink. Only a locked tracker shrugs it off.
 */
public enum SatellitePattern {
	EGGBEATER, VERTICAL, OTHER, TRACKER, HANDHELD_YAGI;

	/** The plain curve's strength at the coverage edge and straight overhead. */
	public static final double LOW = 0.35D, HIGH = 0.9D;
	/** The eggbeater's, at the edge and overhead. */
	public static final double EGG_LOW = 0.55D, EGG_HIGH = 0.95D;
	/** A vertical is as good as the plain curve up to this elevation, then fades into its null. */
	public static final double VERTICAL_FADE_FROM = 40.0D;
	/** What is left of a vertical's signal straight overhead, as a fraction of the plain curve. */
	public static final double VERTICAL_NULL = 0.2D;
	/** A locked tracker's strength, all pass long. */
	public static final double TRACK = 1.0D;
	/** What is left of the signal under an aurora, and in a radio blackout or a thunderstorm. */
	public static final double AURORA_FADE = 0.55D, STORM_FADE = 0.75D;
	/**
	 * Below this elevation the signal fades in as the satellite rises (and out as it sets), from nothing at the horizon,
	 * instead of popping in at full strength (2026-11-14, the maintainer): the long path through the air and the ground
	 * round the station. A tracker fades the same way.
	 */
	public static final double RISE_DEGREES = 8.0D;
	/** Below this strength the satellite cannot hear the station's uplink. */
	public static final double UPLINK_FLOOR = 0.3D;

	/** The strength at which this antenna hears a satellite {@code elevation} degrees up. */
	public double strength(double elevation) {
		double e = Math.max(0.0D, Math.min(90.0D, elevation));
		return rise(e) * pattern(e);
	}

	/** How much of the signal is through near the horizon: 0 at it, all from {@link #RISE_DEGREES} up, smoothly. */
	public static double rise(double elevation) {
		double t = Math.max(0.0D, Math.min(1.0D, elevation / RISE_DEGREES));
		return t * t * (3.0D - 2.0D * t);
	}

	private double pattern(double e) {
		double sin = Math.sin(Math.toRadians(e));
		return switch(this) {
			case TRACKER -> TRACK;
			case HANDHELD_YAGI -> HandheldYagiAim.ON_TARGET;
			case EGGBEATER -> EGG_LOW + (EGG_HIGH - EGG_LOW) * sin;
			case OTHER -> plain(sin);
			case VERTICAL -> {
				if(e <= VERTICAL_FADE_FROM)
					yield plain(sin);
				double into = (e - VERTICAL_FADE_FROM) / (90.0D - VERTICAL_FADE_FROM); // 0 where it starts to fade, 1 overhead.
				double left = VERTICAL_NULL + (1.0D - VERTICAL_NULL) * Math.cos(into * Math.PI / 2.0D);
				yield plain(sin) * left;
			}
		};
	}

	/** The strength under the space weather: {@link #strength(double)} faded by {@link #weather}, but for a tracker. */
	public double strength(double elevation, boolean aurora, boolean storm) {
		return this == TRACKER ? strength(elevation) : strength(elevation) * weather(aurora, storm);
	}

	/** True if a satellite {@code elevation} degrees up hears this antenna's uplink. */
	public boolean uplinks(double elevation) {
		return strength(elevation) >= UPLINK_FLOOR;
	}

	/** True if a satellite {@code elevation} degrees up hears this antenna's uplink under the space weather. */
	public boolean uplinks(double elevation, boolean aurora, boolean storm) {
		return strength(elevation, aurora, storm) >= UPLINK_FLOOR;
	}

	/** What a handheld's own little whip gets of a vertical's signal low in the world (2026-11-15). */
	public static final double HANDHELD_WHIP = 0.7D;
	/** Heights between which a handheld's whip comes up to a full vertical: up a mountain or a tower, clear of everything. */
	public static final double HANDHELD_FROM_Y = 128.0D, HANDHELD_FULL_Y = 224.0D;

	/**
	 * What is left of a vertical's signal on a handheld's own antenna held at height {@code y} (maintainer, 2026-11-15):
	 * {@link #HANDHELD_WHIP} up to {@link #HANDHELD_FROM_Y}, rising smoothly to all of it at {@link #HANDHELD_FULL_Y}.
	 * Low passes are lost, and the null overhead comes sooner; the handheld Yagi or climbing high makes up for it.
	 */
	public static double handheld(double y) {
		double t = Math.max(0.0D, Math.min(1.0D, (y - HANDHELD_FROM_Y) / (HANDHELD_FULL_Y - HANDHELD_FROM_Y)));
		return HANDHELD_WHIP + (1.0D - HANDHELD_WHIP) * t * t * (3.0D - 2.0D * t);
	}

	/** What the space weather leaves of a satellite's signal: 1 on a quiet day. */
	public static double weather(boolean aurora, boolean storm) {
		return (aurora ? AURORA_FADE : 1.0D) * (storm ? STORM_FADE : 1.0D);
	}

	private static double plain(double sin) {
		return LOW + (HIGH - LOW) * sin;
	}
}
