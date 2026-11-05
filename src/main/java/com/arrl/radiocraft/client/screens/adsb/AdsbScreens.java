package com.arrl.radiocraft.client.screens.adsb;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Hands what the server sends ({@code CAdsbPacket}) to the ADS-B screen open for that block, if any. Client only. */
public final class AdsbScreens {

	private AdsbScreens() {}

	public static void update(BlockPos pos, CompoundTag data) {
		if(Minecraft.getInstance().screen instanceof AdsbReceiverScreen receiver && receiver.getMenu().getBlockPos().equals(pos))
			receiver.update(data);
		else if(Minecraft.getInstance().screen instanceof AdsbBeaconScreen beacon && beacon.getMenu().getBlockPos().equals(pos))
			beacon.update(data);
	}
}
