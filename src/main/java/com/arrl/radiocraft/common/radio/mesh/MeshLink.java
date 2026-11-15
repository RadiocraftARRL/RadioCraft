package com.arrl.radiocraft.common.radio.mesh;

/**
 * Whether two mesh devices hear each other (2026-11-10). Pure. A device with only its built-in antenna reaches
 * {@code internalRange} of the band's line of sight; one with an antenna on its coax reaches all of it. A link takes
 * the geometric mean of its two ends' reaches, so a box with a built-in antenna still talks further to a controller on
 * a tower than to another box with a built-in antenna. The signal falls off in a straight line to nothing at that
 * reach, as line of sight does everywhere else ({@code BandUtils#getBaseStrength}); it decodes at {@link #MIN_STRENGTH}.
 */
public final class MeshLink {

	/** Weakest signal a mesh device copies (a data burst needs a little above the noise). */
	public static final double MIN_STRENGTH = 0.1D;

	private MeshLink() {}

	/** How far one end reaches, as a share of the band's line-of-sight range. */
	public static double reach(boolean externalAntenna, double internalRange) {
		return externalAntenna ? 1.0D : Math.max(0.01D, Math.min(1.0D, internalRange));
	}

	/** The strength one end hears the other at, {@code distance} blocks apart, on a band with {@code losRange}. */
	public static double strength(double distance, double losRange, double reachA, double reachB) {
		double range = losRange * Math.sqrt(reachA * reachB);
		if(range <= 0.0D || distance >= range)
			return 0.0D;
		return 1.0D - distance / range;
	}

	/** True if the two ends copy each other. */
	public static boolean hears(double distance, double losRange, double reachA, double reachB) {
		return strength(distance, losRange, reachA, reachB) >= MIN_STRENGTH;
	}

	/** The furthest the two ends hear each other, blocks. */
	public static double range(double losRange, double reachA, double reachB) {
		return losRange * Math.sqrt(reachA * reachB) * (1.0D - MIN_STRENGTH);
	}
}
