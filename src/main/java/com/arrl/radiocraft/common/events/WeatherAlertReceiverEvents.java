package com.arrl.radiocraft.common.events;

import com.arrl.radiocraft.common.blockentities.WeatherAlertReceiverBlockEntity;
import com.arrl.radiocraft.common.blocks.WeatherAlertReceiverBlock;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Shift + right-click on a Simple Weather Alert Receiver with a Small Alkaline Battery in the main hand swaps its
 * battery. Vanilla never asks the block when a sneaking player holds an item, so it is done here.
 */
@EventBusSubscriber
public final class WeatherAlertReceiverEvents {

	private WeatherAlertReceiverEvents() {}

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if(event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isSecondaryUseActive()
				|| event.getItemStack().getItem() != RadiocraftItems.SMALL_BATTERY.get()
				|| !(event.getLevel().getBlockEntity(event.getPos()) instanceof WeatherAlertReceiverBlockEntity))
			return;
		WeatherAlertReceiverBlock.swapBattery(event.getLevel(), event.getPos(), event.getEntity(), event.getItemStack());
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
	}
}
