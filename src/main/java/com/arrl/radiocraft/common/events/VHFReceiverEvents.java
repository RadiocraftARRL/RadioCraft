package com.arrl.radiocraft.common.events;

import com.arrl.radiocraft.common.blockentities.radio.VHFReceiverBlockEntity;
import com.arrl.radiocraft.common.blocks.radios.VHFReceiverBlock;
import com.arrl.radiocraft.common.items.PortableBatteries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Shift + right-click on a VHF receiver with a Small NiCd or Small Alkaline Battery in the main hand puts the cell in
 * (2026-11-09), as on the weather alert receiver ({@link WeatherAlertReceiverEvents}). Vanilla never asks the block when
 * a sneaking player holds an item, so it is done here.
 */
@EventBusSubscriber
public final class VHFReceiverEvents {

	private VHFReceiverEvents() {}

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if(event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isSecondaryUseActive()
				|| !PortableBatteries.isCell(event.getItemStack()))
			return;
		var blockEntity = event.getLevel().getBlockEntity(event.getPos());
		if(blockEntity instanceof VHFReceiverBlockEntity)
			VHFReceiverBlock.swapCell(event.getLevel(), event.getPos(), event.getEntity(), event.getItemStack());
		else if(blockEntity instanceof com.arrl.radiocraft.common.blockentities.radio.FmBroadcastReceiverBlockEntity) // The table radio too (2026-11-13).
			com.arrl.radiocraft.common.blocks.radios.FmBroadcastReceiverBlock.swapCell(event.getLevel(), event.getPos(), event.getEntity(), event.getItemStack());
		else
			return;
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
	}
}
