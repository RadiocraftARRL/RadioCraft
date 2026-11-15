package com.arrl.radiocraft.common.radio;

import net.minecraft.core.BlockPos;

/**
 * Light coax loss (2026-11-13): a little of the signal is lost in the cable between a radio and its antenna, more on
 * higher bands and longer runs, both when transmitting and receiving. The run is taken as the distance along the
 * blocks from the radio to the antenna (how far apart they are, not the cable actually laid), so it costs nothing
 * per packet. At the standard rate 50 blocks on 2m loses 1 dB (a fifth of the power); on HF the same run barely
 * matters. Scaled by the {@code coax_loss} config (0 = lossless).
 */
public final class CoaxLoss {

	private CoaxLoss() {}

	/** Loss in dB per 100 blocks of coax at a frequency, at the standard rate. */
	public static double dbPer100(double hz) {
		if(hz < 5_000_000.0D) return 0.25D;  // 80m
		if(hz < 10_000_000.0D) return 0.35D; // 40m
		if(hz < 20_000_000.0D) return 0.5D;  // 20m
		if(hz < 35_000_000.0D) return 0.75D; // 10m, CB
		if(hz < 100_000_000.0D) return 1.0D; // Low band
		return 2.0D;                         // VHF
	}

	/** Loss in dB for a run of {@code blocks} at {@code hz}, times {@code scale} (the config). */
	public static double db(int blocks, double hz, double scale) {
		return Math.max(0, blocks) * dbPer100(hz) / 100.0D * Math.max(0.0D, scale);
	}

	/** Share of the signal left after the run: 1 for none, 10^(-dB/10). */
	public static double factor(int blocks, double hz, double scale) {
		return Math.pow(10.0D, -db(blocks, hz, scale) / 10.0D);
	}

	/** The run's length between two blocks: the steps along the block grid. */
	public static int length(BlockPos radio, BlockPos antenna) {
		return radio.distManhattan(antenna);
	}
}
