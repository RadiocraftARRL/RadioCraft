package com.arrl.radiocraft.common.radio;

import com.arrl.radiocraft.RadiocraftServerConfig;

import java.util.List;

/**
 * How a skip (sky wave) signal gets from one station to another (2026-11-18): in one hop off the ionosphere, or, on the
 * bands that do it when they are open, in two or more hops with a bounce off the ground between, each costing
 * {@code skip_hop_loss}; and the angle it leaves at, from the hop's length and the layer's height, which the terrain
 * around each station must not block ({@code TerrainPropagation}).
 * <p>
 * Distances are game blocks, at {@link #BLOCKS_PER_KM} to a real kilometre, the scale the band skip ranges use.
 */
public final class SkipPath {

	/** The scale of the band skip ranges: 2 blocks to a real kilometre. */
	public static final double BLOCKS_PER_KM = 2.0D;
	/** The lowest angle a real antenna puts much signal out at: no hop leaves lower. */
	public static final double LOWEST_TAKEOFF_DEGREES = 3.0D;
	/** The weakest skip ({@code BandCondition} multiplier) that still hops more than once: fair. */
	public static final double MULTI_HOP_CONDITION = 0.6D;
	private static final double EARTH_KM = 6371.0D;

	private SkipPath() {}

	/** The best way over a path: {@code count} hops (0 for none), giving {@code share} of a single hop's peak strength. */
	public record Hops(int count, double share) {
		public static final Hops NONE = new Hops(0, 0.0D);
	}

	/**
	 * The strongest way to cover {@code distance} blocks with a skip window from {@code min} to {@code max} blocks, in up
	 * to {@code maxHops} equal hops each losing {@code hopLoss} more: a hop is strongest in the middle of the window and
	 * fades to nothing at its edges, as one hop always has.
	 */
	public static Hops best(double distance, double min, double max, int maxHops, double hopLoss) {
		Hops best = Hops.NONE;
		double loss = 1.0D;
		for(int n = 1; n <= Math.max(1, maxHops); n++) {
			double share = hopShare(distance / n, min, max) * loss;
			if(share > best.share())
				best = new Hops(n, share);
			loss *= hopLoss;
		}
		return best;
	}

	/** One hop of {@code hop} blocks in a window from {@code min} to {@code max}: 1 in the middle, 0 at and past the edges. */
	static double hopShare(double hop, double min, double max) {
		if(!(hop > min && hop < max))
			return 0.0D;
		double radius = (max - min) / 2.0D;
		return 1.0D - Math.abs(hop - (min + radius)) / radius;
	}

	/**
	 * How many hops {@code band} may take now: more than one only on the bands in {@code skip_multi_hop_bands} (or that
	 * follow one, as CB follows 10m), and only while it is open (fair or better, {@code conditionMultiplier}) and
	 * reaching its whole window ({@code reach} 1, not a light opening). Server config.
	 */
	public static int maxHops(Band band, double conditionMultiplier, double reach) {
		if(band == null || band.isVhf() || reach < 1.0D || conditionMultiplier < MULTI_HOP_CONDITION)
			return 1;
		List<? extends String> bands = RadiocraftServerConfig.SKIP_MULTI_HOP_BANDS.get();
		return bands.contains(band.name()) || bands.contains(band.conditionName()) ? RadiocraftServerConfig.SKIP_MAX_HOPS.get() : 1;
	}

	/** What each hop past the first leaves of the signal (server config). */
	public static double hopLoss() {
		return RadiocraftServerConfig.SKIP_HOP_LOSS.get();
	}

	/**
	 * The angle above the horizon a hop of {@code hopBlocks} leaves at, off a layer {@code layerKm} high, in radians:
	 * on the curved real earth, so a short hop goes nearly straight up (80m by day) and the longest single hop leaves
	 * at the horizon. Never below {@link #LOWEST_TAKEOFF_DEGREES}.
	 */
	public static double takeoffRadians(double hopBlocks, double layerKm) {
		double km = Math.max(1.0D, hopBlocks / BLOCKS_PER_KM);
		double theta = km / (2.0D * EARTH_KM); // Half the hop's angle at the earth's centre.
		double elevation = Math.atan2(Math.cos(theta) - EARTH_KM / (EARTH_KM + layerKm), Math.sin(theta));
		return Math.max(Math.toRadians(LOWEST_TAKEOFF_DEGREES), elevation);
	}
}
