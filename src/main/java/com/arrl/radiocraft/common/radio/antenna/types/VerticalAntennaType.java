package com.arrl.radiocraft.common.radio.antenna.types;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blocks.antennas.VerticalAntennaBaseBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.StaticAntenna;
import com.arrl.radiocraft.common.radio.antenna.data.VerticalAntennaData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A vertical on a {@link VerticalAntennaBaseBlock} (2026-11-18, replacing the 1:1 balun quarter-wave vertical): the
 * antenna poles stacked on the base are the radiator, the radials on the pad its ground, and the loading coil's tap the
 * band. Each tap wants a set number of poles ({@link VerticalAntennaBaseBlock.Tap#poles}): {@link #MATCHED_SWR} with
 * that many, about {@link #ONE_OFF_SWR} one pole off (a tuner sorts that out), worse further off, and 10:1 off the
 * tapped band or with no poles. Like any ground-mounted vertical it sends low: less line of sight, more skip; the coil
 * costs a little on 40 m and more on 80 m, where most of the antenna is coil ({@link #coilLoss}).
 */
public class VerticalAntennaType extends NonDirectionalAntennaType<VerticalAntennaData> {

	public static final double MATCHED_SWR = 1.3D;
	public static final double ONE_OFF_SWR = 2.5D;
	public static final double TWO_OFF_SWR = 5.0D;
	public static final double HF_LOS_SCALE = 0.6D;
	public static final double HF_SKIP_SCALE = 1.2D;

	public VerticalAntennaType() {
		super(Radiocraft.id("vertical"), 0.0D, 0.0D, 1.0D, 1.0D);
	}

	@Override
	public StaticAntenna<VerticalAntennaData> match(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if(!state.is(RadiocraftBlocks.VERTICAL_ANTENNA_BASE.get()))
			return null;
		return new StaticAntenna<>(this, pos, level, new VerticalAntennaData(poles(level, pos), state.getValue(VerticalAntennaBaseBlock.TAP).metres));
	}

	/** The antenna poles stacked straight up on the base at {@code pos}. */
	public static int poles(BlockGetter level, BlockPos pos) {
		int poles = 0;
		BlockPos.MutableBlockPos at = pos.mutable();
		while(level.getBlockState(at.move(0, 1, 0)).is(RadiocraftBlocks.ANTENNA_POLE.get()) && poles < 64)
			poles++;
		return poles;
	}

	/** The SWR of {@code poles} poles on a coil tapped for {@code tapMetres}, at {@code frequencyHertz}. */
	public static double swr(int poles, int tapMetres, float frequencyHertz) {
		Band band = Band.getBand(frequencyHertz);
		VerticalAntennaBaseBlock.Tap tap = tapFor(tapMetres);
		if(poles == 0 || tap == null || band == null || !band.name().equals(tap.bandName()))
			return 10.0D;
		return switch(Math.abs(poles - tap.poles)) {
			case 0 -> MATCHED_SWR;
			case 1 -> ONE_OFF_SWR;
			case 2 -> TWO_OFF_SWR;
			default -> 10.0D;
		};
	}

	private static VerticalAntennaBaseBlock.Tap tapFor(int metres) {
		for(VerticalAntennaBaseBlock.Tap tap : VerticalAntennaBaseBlock.Tap.values())
			if(tap.metres == metres)
				return tap;
		return null;
	}

	/** What the loading coil lets out on {@code band}: all of it on 10 and 20 m, 0.9 on 40 m, 0.8 on 80 m. */
	public static double coilLoss(Band band) {
		if(band == null)
			return 1.0D;
		return switch(band.name()) {
			case "40m" -> 0.9D;
			case "80m" -> 0.8D;
			default -> 1.0D;
		};
	}

	@Override
	protected double getLosRangeScale(Band band) {
		return (band != null && !band.isVhf() ? HF_LOS_SCALE : 1.0D) * coilLoss(band);
	}

	@Override
	protected double getMaxSkipScale(Band band) {
		return (band != null && !band.isVhf() ? HF_SKIP_SCALE : 1.0D) * coilLoss(band);
	}

	@Override
	public double getSWR(VerticalAntennaData data, float frequencyHertz) {
		return swr(data.getPoles(), data.getTapMetres(), frequencyHertz);
	}

	@Override
	public VerticalAntennaData getDefaultData() {
		return new VerticalAntennaData(0, 20);
	}
}
