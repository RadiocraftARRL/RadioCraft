package com.arrl.radiocraft.common.sounds;

import com.arrl.radiocraft.common.blockentities.radio.HFRadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftSoundEvents;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

public class RadioMorseSoundInstance extends AbstractTickableSoundInstance {

	private final HFRadioBlockEntity radio;

	public RadioMorseSoundInstance(HFRadioBlockEntity radio) {
		super(RadiocraftSoundEvents.MORSE.get(), SoundSource.VOICE, SoundInstance.createUnseededRandom());
		this.radio = radio;
		this.looping = true;
		this.delay = 0;
		this.x = radio.getBlockPos().getX() + 0.5D;
		this.y = radio.getBlockPos().getY() + 0.5D;
		this.z = radio.getBlockPos().getZ() + 0.5D;
		this.volume = 0.0F;
	}

	@Override
	public void tick() {
		if(radio.isRemoved())
			stop();
		else {
			volume = radio.getCWReceiveBuffer().getNextStrength();
			// Played at a computer (RIGCTL REMOTE audio): the Morse is heard there instead (2026-11-09).
			java.util.List<com.arrl.radiocraft.common.radio.BEVoiceReceiver.WiredSpeaker> at = radio.isAudioRouted() ? radio.getStaticSpeakers() : java.util.List.of();
			net.minecraft.core.BlockPos pos = at.isEmpty() ? radio.getBlockPos() : at.get(0).pos();
			x = pos.getX() + 0.5D;
			y = pos.getY() + 0.5D;
			z = pos.getZ() + 0.5D;
		}
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

}
