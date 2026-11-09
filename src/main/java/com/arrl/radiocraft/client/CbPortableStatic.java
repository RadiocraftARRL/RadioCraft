package com.arrl.radiocraft.client;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftSoundEvents;
import com.arrl.radiocraft.common.items.CbPortableItem;
import com.arrl.radiocraft.common.radio.cb.CbRadioState;
import com.arrl.radiocraft.common.radio.voice.handheld.CbPortableRadio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The CB portable's band hiss (2026-11-09): with its squelch turned down to the noise, an AM set hisses all the time, as
 * the block radios do through their static sound. The portable's received audio only plays while a station comes in, so
 * its hiss is a looping sound here on the client, at the player, for the player carrying it (in hand or on their back).
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID, value = Dist.CLIENT)
public final class CbPortableStatic {

	private CbPortableStatic() {}

	/** Loudness against the block radios' static at the same volume: a small speaker held close. */
	private static final float LEVEL = 0.5F;

	@Nullable
	private static Hiss playing;

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if(player == null || minecraft.level == null) {
			playing = null;
			return;
		}
		if(target(player) > 0.0F && (playing == null || playing.isStopped())) {
			playing = new Hiss(player);
			minecraft.getSoundManager().play(playing);
		}
	}

	/** How loud the hiss should be now, 0 for none. */
	static float target(LocalPlayer player) {
		ItemStack stack = CbPortableRadio.find(player);
		if(!CbPortableItem.isOn(stack))
			return 0.0F;
		CbRadioState state = CbPortableItem.state(stack);
		double noise = Math.min(1.0D, RadiocraftClientValues.NOISE_VOLUME);
		if(!RadioBlockEntity.squelchOpensOnNoise(state.squelch() / (double)CbRadioState.MAX_KNOB, noise))
			return 0.0F;
		return (float)(noise * state.volume() / 5.0D * LEVEL);
	}

	private static final class Hiss extends AbstractTickableSoundInstance {

		private final LocalPlayer player;

		Hiss(LocalPlayer player) {
			super(RadiocraftSoundEvents.STATIC.get(), SoundSource.VOICE, SoundInstance.createUnseededRandom());
			this.player = player;
			this.looping = true;
			this.delay = 0;
			this.relative = false;
			this.volume = 0.0F;
			follow();
		}

		private void follow() {
			x = player.getX();
			y = player.getEyeY();
			z = player.getZ();
		}

		@Override
		public void tick() {
			if(player.isRemoved() || Minecraft.getInstance().player != player) {
				stop();
				return;
			}
			follow();
			volume = target(player);
			if(volume <= 0.0F)
				stop(); // Started again next tick if it is wanted again.
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}
}
