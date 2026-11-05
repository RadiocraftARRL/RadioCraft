package com.arrl.radiocraft.api.blockentities.radio;

import com.arrl.radiocraft.common.radio.antenna.AntennaDataPacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A {@link BlockEntity} that an antenna on its coax hands voice and data to. Every RadioCraft radio is one. A block
 * entity of another mod that is not a radio but should hear what an antenna picks up (a scanner, a recorder) implements
 * this and puts a radio network object on the coax ({@link com.arrl.radiocraft.api.RadiocraftApi#radioNetworkObject}),
 * since antennas only hand signals to the radio objects on their coax.
 *
 * <p>Both methods are called on the <b>voice chat thread</b> for voice and on the server thread for data: keep them
 * quick, allocate little and do not touch the world from the voice thread.</p>
 *
 * <p>CW goes to {@link ICWReceiver}.</p>
 *
 * @since 1.12.0
 */
public interface IAntennaReceiver {

	/**
	 * A voice frame arrived from the antenna. The receiver decides whether it is near enough in frequency to hear it.
	 *
	 * @param packet the frame, its strength already reduced by the path, the antennas and the coax
	 * @return true if it was heard
	 */
	boolean receiveVoice(AntennaVoicePacket packet);

	/**
	 * A data burst (packet radio, RTTY, APRS) arrived from the antenna. Ignored unless overridden.
	 *
	 * @param packet the burst
	 * @return true if it was decoded
	 */
	default boolean receiveData(AntennaDataPacket packet) {
		return false;
	}
}
