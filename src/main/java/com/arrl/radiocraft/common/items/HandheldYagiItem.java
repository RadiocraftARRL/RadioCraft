package com.arrl.radiocraft.common.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The handheld Yagi (maintainer, 2026-11-15): a three-element 2 m beam. Held in the offhand, it is the antenna of the
 * holder's VHF handhelds for satellites, strong while they look towards the satellite and deaf to it when they look
 * away ({@link com.arrl.radiocraft.common.radio.satellite.HandheldYagiAim}, read in
 * {@link com.arrl.radiocraft.common.radio.voice.handheld.PlayerRadio}).
 */
public class HandheldYagiItem extends Item {

	public HandheldYagiItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.handheld_yagi").withStyle(ChatFormatting.GRAY));
	}
}
