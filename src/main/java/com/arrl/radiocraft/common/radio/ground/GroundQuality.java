package com.arrl.radiocraft.common.radio.ground;

import java.util.List;

/**
 * How good an HF station's ground is, 0 (none) to 1 (as good as it gets), and what it does (maintainer, 2026-11-08).
 * Pure logic, so it unit tests without Minecraft; {@link Grounding} reads the world into it.
 * <p>
 * A ground rod's worth is the soil it is driven into, how deep it goes and how wet the ground is: a rod in damp clay
 * or mud is a fine ground; the same rod in desert sand, gravel, rock or frozen ground is poor, and wants going deeper
 * or more rods. Rods bonded together add up in parallel, each doing less than the last.
 * <p>
 * Grounding is never required. A good ground takes most of the local noise off an HF receiver (the redstone and
 * electrical hash that rides in on the coax shield) and some of the band noise ({@link #noiseFactor},
 * {@link #localNoiseFactor}); a lightning arrestor needs it to do its job properly.
 */
public final class GroundQuality {

	private GroundQuality() {}

	/** What a rod segment sits in, best first; {@link #conductivity} is its share of an ideal ground. */
	public enum Soil {
		/** Mud, or water right against the rod. */
		WET(1.0D),
		CLAY(0.9D),
		/** Dirt, grass, farmland, podzol, mycelium, rooted dirt, moss. */
		LOAM(0.7D),
		/** Soul soil and the like: dry and loose. */
		DRY(0.4D),
		SAND(0.3D),
		GRAVEL(0.25D),
		ROCK(0.1D),
		/** Ice, packed snow, permafrost. */
		FROZEN(0.05D),
		/** Air, or nothing the rod touches: no contact at all. */
		NONE(0.0D);

		public final double conductivity;

		Soil(double conductivity) {
			this.conductivity = conductivity;
		}
	}

	/** How deep a rod goes, in segments (blocks), before it is two thirds as good as an infinitely deep one. */
	static final double DEPTH_SCALE = 3.0D;
	/** At most this much is taken off the whole HF noise floor by a perfect ground. */
	public static final double MAX_NOISE_CUT = 0.3D;
	/** At most this much is taken off the local (redstone, lightning) noise by a perfect ground. */
	public static final double MAX_LOCAL_NOISE_CUT = 0.6D;
	/** Below this a ground counts as none for a lightning arrestor. */
	public static final double ARRESTOR_MIN = 0.25D;

	/**
	 * How wet the ground is, 0.6 (arid) to 1: the biome's downfall, plus rain falling on it now, plus water nearby.
	 *
	 * @param downfall the biome's downfall, 0 (desert) to 1 (jungle, swamp)
	 */
	public static double moisture(double downfall, boolean raining, boolean waterNearby) {
		double m = 0.6D + 0.4D * Math.max(0.0D, Math.min(1.0D, downfall));
		if(raining)
			m += 0.2D;
		if(waterNearby)
			m += 0.2D;
		return Math.min(1.0D, m);
	}

	/**
	 * One rod: the soil round each of its buried segments (top first), how wet, and whether the ground is frozen
	 * (a cold biome: it all counts as {@link Soil#FROZEN} below the frost line, the top two segments).
	 */
	public static double rod(List<Soil> segments, double moisture, boolean frozenBiome) {
		if(segments.isEmpty())
			return 0.0D;
		double sum = 0.0D;
		int buried = 0;
		for(int i = 0; i < segments.size(); i++) {
			Soil soil = segments.get(i);
			if(soil == Soil.NONE)
				continue;
			buried++;
			double c = frozenBiome && i < 2 && soil != Soil.WET ? Soil.FROZEN.conductivity : soil.conductivity;
			sum += c;
		}
		if(buried == 0)
			return 0.0D;
		double soil = sum / buried;
		double depth = 1.0D - Math.exp(-buried / DEPTH_SCALE);
		return Math.max(0.0D, Math.min(1.0D, soil * depth * moisture));
	}

	/** Rods bonded together, in parallel: 1 minus the product of what each leaves undone. */
	public static double combine(double... rods) {
		double left = 1.0D;
		for(double rod : rods)
			left *= 1.0D - Math.max(0.0D, Math.min(1.0D, rod));
		return 1.0D - left;
	}

	/** What the HF noise floor is multiplied by on a station grounded this well. */
	public static double noiseFactor(double quality) {
		return 1.0D - MAX_NOISE_CUT * clamp(quality);
	}

	/** What the local noise (redstone, lightning crashes nearby) is multiplied by on top of {@link #noiseFactor}. */
	public static double localNoiseFactor(double quality) {
		return 1.0D - MAX_LOCAL_NOISE_CUT * clamp(quality);
	}

	/** A word for the quality, for the analyzer and Jade: none, poor, fair, good, excellent. */
	public static String grade(double quality) {
		if(quality <= 0.0D)
			return "none";
		if(quality < 0.25D)
			return "poor";
		if(quality < 0.5D)
			return "fair";
		if(quality < 0.75D)
			return "good";
		return "excellent";
	}

	private static double clamp(double quality) {
		return Math.max(0.0D, Math.min(1.0D, quality));
	}
}
