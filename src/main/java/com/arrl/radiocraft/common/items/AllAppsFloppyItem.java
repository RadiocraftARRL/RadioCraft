package com.arrl.radiocraft.common.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The All Applications Floppy (creative only, 2026-11-08): put in a radio computer's drive, it installs every program
 * that comes on a floppy except the secret ones (they stay to be found), and the drive hands it straight back
 * ({@link com.arrl.radiocraft.common.computer.ComputerServer}). It is never left in the drive, so it never becomes a
 * data floppy. No recipe.
 */
public class AllAppsFloppyItem extends FloppyDiskItem {

	public AllAppsFloppyItem(Properties properties) {
		super(properties, null);
	}

	public static boolean is(ItemStack stack) {
		return stack.getItem() instanceof AllAppsFloppyItem;
	}

	@Override
	public boolean isFoil(@NotNull ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.radiocraft.all_apps_floppy").withStyle(ChatFormatting.LIGHT_PURPLE));
	}
}
