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
 * The satellite tracking antenna (2026-11-12): a small dish on a powered az/el mount that follows a satellite across
 * the sky. Locked on, the satellite is heard at full strength all pass long, through auroras and storms, and a stealth
 * satellite is heard at all ({@link com.arrl.radiocraft.common.radio.satellite.SatellitePattern#TRACKER}). Aimed at the
 * sky, it is a poor antenna for stations on the ground.
 */
public class SatelliteTrackerAntennaType extends NonDirectionalAntennaType<EmptyAntennaData> {

	public SatelliteTrackerAntennaType() {
		super(Radiocraft.id("satellite_tracker"), 0.0D, 0.0D, 1.0D, 0.0D);
	}

	@Override
	public StaticAntenna<EmptyAntennaData> match(Level level, BlockPos pos) {
		return level.getBlockState(pos).is(RadiocraftBlocks.SATELLITE_TRACKER.get()) ? new StaticAntenna<>(this, pos, level) : null;
	}

	@Override
	protected double modifyDistanceForTransmit(IAntennaPacket packet, EmptyAntennaData data, BlockPos destination, double distance) {
		return distance * 1.6D; // Its beam is up in the sky: the ground around it only hears its edge.
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
