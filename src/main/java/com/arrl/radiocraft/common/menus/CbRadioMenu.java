package com.arrl.radiocraft.common.menus;

import com.arrl.radiocraft.common.blockentities.radio.CbRadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/** The front panel of a CB mobile (5875 or PC68): channel, volume, squelch, meters and switches, and a PTT. */
public class CbRadioMenu extends RadioMenu<CbRadioBlockEntity> {

	public CbRadioMenu(int id, CbRadioBlockEntity blockEntity) {
		super(RadiocraftMenuTypes.CB_RADIO.get(), id, blockEntity, blockEntity.getBlockState().getBlock());
	}

	public CbRadioMenu(final int id, final Inventory playerInventory, final FriendlyByteBuf data) {
		this(id, MenuUtils.getBlockEntity(playerInventory, data, CbRadioBlockEntity.class));
	}
}
