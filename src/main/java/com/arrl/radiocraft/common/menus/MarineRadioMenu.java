package com.arrl.radiocraft.common.menus;

import com.arrl.radiocraft.common.blockentities.radio.MarineRadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/** The front panel of the fixed-mount marine radio: power, channel, 16/9, HI/LO, dual watch, volume and PTT. */
public class MarineRadioMenu extends RadioMenu<MarineRadioBlockEntity> {

	public MarineRadioMenu(int id, MarineRadioBlockEntity blockEntity) {
		super(RadiocraftMenuTypes.MARINE_RADIO.get(), id, blockEntity, blockEntity.getBlockState().getBlock());
	}

	public MarineRadioMenu(final int id, final Inventory playerInventory, final FriendlyByteBuf data) {
		this(id, MenuUtils.getBlockEntity(playerInventory, data, MarineRadioBlockEntity.class));
	}
}
