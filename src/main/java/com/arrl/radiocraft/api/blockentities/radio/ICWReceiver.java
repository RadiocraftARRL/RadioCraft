package com.arrl.radiocraft.api.blockentities.radio;

import com.arrl.radiocraft.api.antenna.IAntennaPacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaCWPacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaNetwork;
import com.arrl.radiocraft.common.radio.morse.CWBuffer;
import com.arrl.radiocraft.common.radio.morse.CWReceiveBuffer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Any {@link BlockEntity} which receives and plays back CW/morse should implement {@link ICWReceiver}.
 *
 * <p>The HF radios ({@code HFRadioBlockEntity}) are CW receivers. Since 1.12 any other block entity that implements
 * this and has a radio network object on an antenna's coax (or is a radio using its bare connector) is handed the CW
 * its antenna hears too, when {@link #acceptsCW} says yes.</p>
 */
public interface ICWReceiver {

	/**
     * Get the {@link CWReceiveBuffer} for this receiver.
	 * @return The {@link CWReceiveBuffer} for this receiver, which is what receives and sorts {@link CWBuffer}s from
	 * {@link com.arrl.radiocraft.common.network.clientbound.CCWBufferPacket}s on the client.
	 */
	CWReceiveBuffer getCWReceiveBuffer();

	/**
     * Checks if this receiver is currently open to receive CW/morse.
	 * @return True if this receiver is currently open to receive CW/morse. Checks for if CW mode is enabled, power is
	 * on etc. should be done here.
	 */
	boolean canReceiveCW();

	/**
	 * Whether this receiver wants {@code packet}: is it open and tuned near enough to the packet's frequency? Asked
	 * before {@link #receiveCW} for receivers that are not RadioCraft's own HF radios (those check a 1 kHz window
	 * themselves). The default is {@link #canReceiveCW()} alone: override it to check the frequency.
	 *
	 * @param packet the CW arriving
	 * @return true to have {@link #receiveCW} called
	 * @since 1.12.0
	 */
	default boolean acceptsCW(AntennaCWPacket packet) {
		return canReceiveCW();
	}

	/**
	 * Handles the (server-side) receiving of CW packets from an {@link AntennaNetwork}. This should also be where any
	 * {@link com.arrl.radiocraft.common.network.clientbound.CCWBufferPacket}s are then sent to clients tracking this receiver.
	 *
	 * @param packet The {@link AntennaCWPacket} being received. This is an {@link IAntennaPacket}, not a network packet.
	 */
	void receiveCW(AntennaCWPacket packet);

}
