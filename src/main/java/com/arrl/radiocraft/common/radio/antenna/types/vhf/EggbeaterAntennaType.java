package com.arrl.radiocraft.common.radio.antenna.types.vhf;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.antenna.IAntennaPacket;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.StaticAntenna;
import com.arrl.radiocraft.common.radio.antenna.data.EmptyAntennaData;
import com.arrl.radiocraft.common.radio.antenna.types.NonDirectionalAntennaType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The EB-VHF eggbeater (2026-11-12): two crossed loops over a ring of reflector radials, after the M2 eggbeater. Made for
 * satellites ({@link com.arrl.radiocraft.common.radio.satellite.SatellitePattern#EGGBEATER}): strong all pass long,
 * best overhead, where a vertical fades. Along the ground it is a poor match for the vertical stations around it, so
 * it carries a little less far than a J-pole.
 */
public class EggbeaterAntennaType extends NonDirectionalAntennaType<EmptyAntennaData> {

	public EggbeaterAntennaType() {
		super(Radiocraft.id("eggbeater"), 0.0D, 0.0D, 1.0D, 0.0D);
	}

	@Override
	public StaticAntenna<EmptyAntennaData> match(Level level, BlockPos pos) {
		return level.getBlockState(pos).is(RadiocraftBlocks.EGGBEATER_ANTENNA.get()) ? new StaticAntenna<>(this, pos, level) : null;
	}

	@Override
	protected double modifyDistanceForTransmit(IAntennaPacket packet, EmptyAntennaData data, BlockPos destination, double distance) {
		return distance / 1.1D; // A J-pole's is 1.3: horizontal at the horizon, it gives up some of the ground wave.
	}

	@Override
	public double getSWR(EmptyAntennaData data, float frequencyHertz) {
		Band band = Band.getBand(frequencyHertz); // A fresh Band from the config each call: compare by band, not identity.
		return com.arrl.radiocraft.common.radio.Band.twoMetreAntennaSwr(band);
	}

	@Override
	public EmptyAntennaData getDefaultData() {
		return new EmptyAntennaData();
	}
}
