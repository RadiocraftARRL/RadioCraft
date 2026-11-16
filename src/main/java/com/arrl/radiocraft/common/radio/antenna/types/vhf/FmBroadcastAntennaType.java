package com.arrl.radiocraft.common.radio.antenna.types.vhf;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.antenna.StaticAntenna;
import com.arrl.radiocraft.common.radio.antenna.data.EmptyAntennaData;
import com.arrl.radiocraft.common.radio.antenna.types.NonDirectionalAntennaType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The FM broadcast antenna (2026-11-16): a side-mounted, circularly polarised bay cut for the middle of the FM
 * broadcast band ({@link #RESONANT_HZ}). Matched across the band ({@link #MATCHED_SWR} in the middle, about 1.5:1 at
 * the edges); about 2.3:1 on the airband and 3:1 on 2m. Nothing below 80 or above 200 MHz. Up on its mast it reaches a
 * little further than the airband ground plane.
 */
public class FmBroadcastAntennaType extends NonDirectionalAntennaType<EmptyAntennaData> {

	public static final double RESONANT_HZ = 98_000_000.0D;
	public static final double MATCHED_SWR = 1.1D;
	/** How fast the SWR climbs away from resonance, per MHz. */
	public static final double SWR_PER_MHZ = 0.04D;

	public FmBroadcastAntennaType() {
		super(Radiocraft.id("fm_broadcast_antenna"), 0.0D, 0.0D, 1.2D, 0.0D);
	}

	@Override
	public StaticAntenna<EmptyAntennaData> match(Level level, BlockPos pos) {
		return level.getBlockState(pos).is(RadiocraftBlocks.FM_BROADCAST_ANTENNA.get()) ? new StaticAntenna<>(this, pos, level) : null;
	}

	@Override
	public double getSWR(EmptyAntennaData data, float frequencyHertz) {
		return swr(frequencyHertz);
	}

	/** The SWR at {@code hz}: 1.1 at 98 MHz, about 1.5 at the band edges, 10 out of its range. */
	public static double swr(double hz) {
		if(hz < 80_000_000.0D || hz > 200_000_000.0D)
			return 10.0D;
		return Math.min(10.0D, MATCHED_SWR + Math.abs(hz - RESONANT_HZ) / 1_000_000.0D * SWR_PER_MHZ);
	}

	@Override
	public EmptyAntennaData getDefaultData() {
		return new EmptyAntennaData();
	}
}
