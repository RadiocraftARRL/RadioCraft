package com.arrl.radiocraft.common.menus;

import com.arrl.radiocraft.common.blockentities.radio.RealWorldInterfaceBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/** The Real-World Interface's screen: interface, frequency, mode, Everywhere, power, and the link's status. */
public class RealWorldInterfaceMenu extends RadioMenu<RealWorldInterfaceBlockEntity> {

	public RealWorldInterfaceMenu(int id, RealWorldInterfaceBlockEntity blockEntity) {
		super(RadiocraftMenuTypes.REAL_WORLD_INTERFACE.get(), id, blockEntity, blockEntity.getBlockState().getBlock());
	}

	public RealWorldInterfaceMenu(final int id, final Inventory playerInventory, final FriendlyByteBuf data) {
		this(id, MenuUtils.getBlockEntity(playerInventory, data, RealWorldInterfaceBlockEntity.class));
	}
}
