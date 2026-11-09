package com.arrl.radiocraft.common.radio.antenna.types;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.radio.antenna.StaticAntenna;
import com.arrl.radiocraft.common.radio.antenna.data.EmptyAntennaData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The CB portable's telescopic whip, pulled out to its full length when the radio is on (2026-11-08): close to a
 * quarter wave at 27 MHz (2.75 m, about 3 blocks), but held at chest height with the operator for a ground plane, so
 * a few dB down on a proper antenna both ways. Never built in the world: the radio carries it.
 * <p>
 * The portable is a cheap set (maintainer, 2026-11-08): it reaches half as far as a CB mobile, both ways. Sending, its
 * line of sight and skip reach are halved ({@link #getLosRangeScale}, {@link #getMaxSkipScale}); receiving, the
 * signal is cut by what the same halving would cost it over that path ({@link #receiveRangeFactor}).
 */
public class CbWhipAntennaType extends NonDirectionalAntennaType<EmptyAntennaData> {

	public static final CbWhipAntennaType INSTANCE = new CbWhipAntennaType();

	public CbWhipAntennaType() {
		super(Radiocraft.id("cb_whip"), -3.0D, -3.0D, 1.0D, 0.5D);
	}

	@Override
	public StaticAntenna<EmptyAntennaData> match(Level level, BlockPos pos) {
		return null;
	}

	/** Half a mobile's reach, both ways. */
	public static final double RANGE_SCALE = 0.5D;

	@Override
	protected double getLosRangeScale(com.arrl.radiocraft.common.radio.Band band) {
		return RANGE_SCALE;
	}

	@Override
	protected double getMaxSkipScale(com.arrl.radiocraft.common.radio.Band band) {
		return RANGE_SCALE;
	}

	/**
	 * What the portable's poor receiver leaves of a signal that came {@code distance} blocks on {@code band}: the
	 * strength the path gives at half the reach over the strength it gives at full reach (0 to 1).
	 */
	public double receiveRangeFactor(com.arrl.radiocraft.common.radio.Band band, double distance, boolean day) {
		if(band == null)
			return 1.0D;
		double full = com.arrl.radiocraft.common.radio.BandUtils.getBaseStrength(band, distance, getLosEfficiency(), getSkipEfficiency(), day, 1.0D, 1.0D);
		if(full <= 0.0D)
			return 0.0D;
		double half = com.arrl.radiocraft.common.radio.BandUtils.getBaseStrength(band, distance, getLosEfficiency(), getSkipEfficiency(), day, RANGE_SCALE, RANGE_SCALE);
		return Math.max(0.0D, Math.min(1.0D, half / full));
	}

	/** Cut for the middle of the band: near enough 1.3:1 on every channel. */
	@Override
	public double getSWR(EmptyAntennaData data, float frequencyHertz) {
		return 1.3D;
	}

	@Override
	public EmptyAntennaData getDefaultData() {
		return new EmptyAntennaData();
	}
}
