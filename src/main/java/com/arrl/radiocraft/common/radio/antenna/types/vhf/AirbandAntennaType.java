package com.arrl.radiocraft.common.radio.antenna.types.vhf;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.antenna.types.NonDirectionalAntennaType;
import com.arrl.radiocraft.common.radio.antenna.StaticAntenna;
import com.arrl.radiocraft.common.radio.antenna.data.EmptyAntennaData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The aircraft VHF antenna (2026-11-13): a short swept whip on a fin, cut for the middle of the airband
 * ({@link #RESONANT_HZ}). Matched across the airband ({@link #MATCHED_SWR} in the middle, about 1.9:1 at the edges);
 * the match falls off away from it, about 2.6:1 on 2m and worse on commercial VHF. Nothing below 80 or above 200 MHz.
 * A little less reach than a J-pole: it is short. It stands on top of a block or hangs under one.
 */
public class AirbandAntennaType extends NonDirectionalAntennaType<EmptyAntennaData> {

	public static final double RESONANT_HZ = 127_500_000.0D;
	public static final double MATCHED_SWR = 1.1D;
	/** How fast the SWR climbs away from resonance, per MHz. */
	public static final double SWR_PER_MHZ = 0.085D;

	private final boolean groundPlane;

	/** The aircraft antenna: short, a little less reach than a J-pole. */
	public AirbandAntennaType() {
		this(Radiocraft.id("airband_antenna"), 0.9D, false);
	}

	private AirbandAntennaType(net.minecraft.resources.ResourceLocation id, double los, boolean groundPlane) {
		super(id, 0.0D, 0.0D, los, 0.0D);
		this.groundPlane = groundPlane;
	}

	/** The airband ground plane (2026-11-13): the same match, up on a mast, reaching further than a J-pole. */
	public static AirbandAntennaType groundPlane() {
		return new AirbandAntennaType(Radiocraft.id("airband_ground_plane"), 1.15D, true);
	}

	@Override
	public StaticAntenna<EmptyAntennaData> match(Level level, BlockPos pos) {
		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
		boolean ok = groundPlane
				? state.is(RadiocraftBlocks.AIRBAND_GROUND_PLANE.get())
						&& state.getValue(com.arrl.radiocraft.common.blocks.antennas.DoubleVHFAntennaBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
				: state.is(RadiocraftBlocks.AIRBAND_ANTENNA.get());
		return ok ? new StaticAntenna<>(this, pos, level) : null;
	}

	@Override
	public double getSWR(EmptyAntennaData data, float frequencyHertz) {
		return swr(frequencyHertz);
	}

	/** The SWR at {@code hz}: 1.1 at 127.5 MHz, about 1.9 at the airband edges, 10 out of its range. */
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
