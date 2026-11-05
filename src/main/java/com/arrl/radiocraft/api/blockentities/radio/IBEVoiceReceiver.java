package com.arrl.radiocraft.api.blockentities.radio;

import com.arrl.radiocraft.common.radio.BEVoiceReceiver;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Any {@link BlockEntity} which plays received voice through a speaker should implement {@link IBEVoiceReceiver}. The
 * {@link BEVoiceReceiver} it returns holds the Simple Voice Chat channel the audio is played on, the speaker kind and
 * the squelch. Hearing a signal from an antenna is {@link IAntennaReceiver}'s job; this is only the playback side.
 */
public interface IBEVoiceReceiver {

	/**
     * Get the {@link BEVoiceReceiver} instance for this radio.
	 * @return Returns {@link BEVoiceReceiver} instance for this radio, which acts as a container for Simple Voice Chat API
	 * interactions.
	 */
	BEVoiceReceiver getVoiceReceiver();

}
