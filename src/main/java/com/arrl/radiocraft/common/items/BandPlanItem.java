package com.arrl.radiocraft.common.items;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * The Band Plan sheet: right click to read it. Every band (the ham bands and the commercial ones) is a bar coloured by
 * what each part is for, with markers for frequencies worth knowing and a legend. What it shows comes from the server
 * config ({@link com.arrl.radiocraft.common.radio.bandplan.BandPlan}), so the sheet is the same for everyone on a server.
 * Its tooltip line comes from {@code tooltip.radiocraft.band_plan} (ItemTooltipEvents).
 */
public class BandPlanItem extends Item {

	public BandPlanItem(Properties properties) {
		super(properties);
	}

	@Override
	public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if(level.isClientSide)
			com.arrl.radiocraft.client.screens.BandPlanScreen.open(); // A separate class, so the server never loads it.
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}
}
