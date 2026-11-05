package com.arrl.radiocraft.common.menus;

import com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/** The front panel of a commercial mobile radio (VHF or low band): power, channel knob, volume and PTT. */
public class CommercialMobileMenu extends RadioMenu<CommercialMobileBlockEntity> {

	public CommercialMobileMenu(int id, CommercialMobileBlockEntity blockEntity) {
		super(RadiocraftMenuTypes.COMMERCIAL_MOBILE.get(), id, blockEntity, blockEntity.getBlockState().getBlock());
	}

	public CommercialMobileMenu(final int id, final Inventory playerInventory, final FriendlyByteBuf data) {
		this(id, MenuUtils.getBlockEntity(playerInventory, data, CommercialMobileBlockEntity.class));
	}
}
