package com.arrl.radiocraft.common.radio.antenna.types;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blocks.antennas.CbGroundPlaneAntennaBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.antenna.StaticAntenna;
import com.arrl.radiocraft.common.radio.antenna.data.EmptyAntennaData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A CB ground plane (maintainer, 2026-11-08): a vertical five blocks tall with radials at its foot, cut for the middle of
 * the CB band. Matched across CB ({@link #MATCHED_SWR} at {@link #RESONANT_HZ}, a little worse at the band edges); the
 * match falls off away from it, so on 10m it manages about 1.8:1 at best (at the bottom of the band) and worse higher
 * up. Nothing below 20 MHz or above 50 MHz. A little gain over a plain quarter wave, low to the horizon.
 */
public class CbGroundPlaneAntennaType extends NonDirectionalAntennaType<EmptyAntennaData> {

	/** Where it is cut: channel 19. */
	public static final double RESONANT_HZ = 27_185_000.0D;
	public static final double MATCHED_SWR = 1.1D;
	/** How fast the SWR climbs away from resonance, per MHz. */
	public static final double SWR_PER_MHZ = 0.85D;

	public CbGroundPlaneAntennaType() {
		super(Radiocraft.id("cb_ground_plane"), 1.0D, 1.0D, 1.2D, 0.8D);
	}

	@Override
	public StaticAntenna<EmptyAntennaData> match(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(RadiocraftBlocks.CB_GROUND_PLANE_ANTENNA.get()) && state.getValue(CbGroundPlaneAntennaBlock.SEGMENT) == 0
				? new StaticAntenna<>(this, pos, level) : null;
	}

	@Override
	public double getSWR(EmptyAntennaData data, float frequencyHertz) {
		return swr(frequencyHertz);
	}

	/** The SWR at {@code hz}: 1.1 on channel 19, about 1.3 at the CB band edges, 1.8 at 28.0 MHz, 10 out of its range. */
	public static double swr(double hz) {
		if(hz < 20_000_000.0D || hz > 50_000_000.0D)
			return 10.0D;
		return Math.min(10.0D, MATCHED_SWR + Math.abs(hz - RESONANT_HZ) / 1_000_000.0D * SWR_PER_MHZ);
	}

	@Override
	public EmptyAntennaData getDefaultData() {
		return new EmptyAntennaData();
	}
}
