package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.common.network.serverbound.SBlockRadioControlPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Right click on a radio screen's PTT (2026-11-08): the screen shuts and the radio's hand mic comes off its hook into
 * an empty hand, as sneak and use on the radio does (the server says so if the hand is full or someone has the mic).
 */
public final class HandMicPtt {

	private HandMicPtt() {}

	public static void take(BlockPos radio) {
		SBlockRadioControlPacket.updateServer(radio, SBlockRadioControlPacket.Action.TAKE_HAND_MIC, true);
		Minecraft.getInstance().setScreen(null);
	}
}
