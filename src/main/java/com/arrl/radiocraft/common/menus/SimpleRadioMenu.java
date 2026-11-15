package com.arrl.radiocraft.common.menus;

import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/**
 * The screen of the radios drawn without a GUI texture (2026-11-13): the airband radio and the FM broadcast transmitter
 * and receiver. The screen picks its layout from the block entity ({@code SimpleRadioScreen}).
 */
public class SimpleRadioMenu extends RadioMenu<RadioBlockEntity> {

	public SimpleRadioMenu(int id, RadioBlockEntity blockEntity) {
		super(RadiocraftMenuTypes.SIMPLE_RADIO.get(), id, blockEntity, blockEntity.getBlockState().getBlock());
	}

	public SimpleRadioMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
		this(id, MenuUtils.getBlockEntity(playerInventory, data, RadioBlockEntity.class));
	}
}
