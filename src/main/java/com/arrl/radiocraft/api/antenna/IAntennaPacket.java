package com.arrl.radiocraft.api.antenna;

import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.AntennaCWPacket;
import com.arrl.radiocraft.common.radio.antenna.StaticAntenna;
import net.minecraft.server.level.ServerLevel;

/**
 * An object representing data being sent between two {@link StaticAntenna}s.
 *
 * <p>See {@link AntennaCWPacket} for an example implementation of {@link IAntennaPacket}</p>
 */
public interface IAntennaPacket {

	/**
     * The name of the signal being transmitted.
	 * @return The name of the signal being transmitted.
	 */
	Band getBand();

	/**
     * The frequency of the signal being transmitted.
	 * @return The frequency of the signal being transmitted.
	 */
	float getFrequency();

	/**
     * The strength of the signal.
	 * @return A 0-1 double value representing the strength of the signal.
	 */
	double getStrength();

	/**
     * The {@link IAntenna} the signal originated from.
	 * @return The {@link IAntenna} the signal originated from.
	 */
	IAntenna getSource();

	/**
     * The {@link ServerLevel} this packet is being sent within.
	 * @return The {@link ServerLevel} this packet is being sent within.
	 */
	ServerLevel getLevel();

	/**
	 * The {@link IAntenna} this copy of the signal is on its way to, when the sender knows it (terrain needs to know
	 * whether the receiver is a handheld under a roof). Null when only a position is known.
	 *
	 * @return the destination antenna, or null
	 */
	default IAntenna getDestination() {
		return null;
	}

	/**
	 * How far this signal's splatter reaches into neighbouring frequencies, against an in-band signal's (1): a signal
	 * outside its band (a MARS-modded rig) splatters further, see {@link com.arrl.radiocraft.common.radio.BandEdges}.
	 * Receivers pass it to {@link com.arrl.radiocraft.common.radio.voice.Detuning}.
	 *
	 * @return the reach multiplier, 1 in band
	 */
	default double getSplatterReach() {
		return com.arrl.radiocraft.common.radio.BandEdges.splatterReach(getBand(), getFrequency());
	}

}
