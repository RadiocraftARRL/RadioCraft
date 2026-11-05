package com.arrl.radiocraft.common.blockentities.radio;

/**
 * A channelized radio's display (commercial and marine radios): it shows the channel, not a frequency. The block's
 * display renderer and the radio's screen draw these two lines.
 */
public interface ChannelDisplay {

	/** The big line: the channel name or number. */
	String displayLine();

	/** The small line: the channel number, mode, power and so on. */
	String displayStatus();
}
