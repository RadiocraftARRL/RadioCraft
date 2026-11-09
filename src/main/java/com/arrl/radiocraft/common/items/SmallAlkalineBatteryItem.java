package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.common.init.RadiocraftItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
  * A Small Alkaline Battery (2026-11-08): a throwaway cell, full when made and never recharged (no charger or charge
 * controller takes it). It runs the portable radios too (2026-11-09, {@link PortableBatteries}), and comes out of one as
 * a Dead Alkaline Battery once flat. The battery weather radio runs on one, as
 * real ones run on alkaline cells. Its charge is {@link com.arrl.radiocraft.common.capabilities.AlkalineEnergyStorage}.
 */
public class SmallAlkalineBatteryItem extends Item {

	public SmallAlkalineBatteryItem(Properties properties) {
		super(properties);
	}

	/** A battery the weather alert receiver takes: the alkaline cell, or the NiCd one. */
	public static boolean fitsWeatherRadio(ItemStack stack) {
		return stack.getItem() instanceof SmallAlkalineBatteryItem || stack.getItem() == RadiocraftItems.SMALL_BATTERY.get();
	}

		/** Clicked onto a portable radio in an inventory: it goes in, and the radio's old cell comes back on the cursor (2026-11-09). */
	@Override
	public boolean overrideStackedOnOther(@NotNull ItemStack cell, net.minecraft.world.inventory.@NotNull Slot slot, net.minecraft.world.inventory.@NotNull ClickAction action, net.minecraft.world.entity.player.@NotNull Player player) {
		if(action != net.minecraft.world.inventory.ClickAction.PRIMARY || !VHFHandheldItem.takesSmallBattery(slot.getItem()))
			return false;
		if(!player.level().isClientSide)
			PortableBatteries.cellOntoRadio(cell, slot, player);
		return true;
	}

	@Override
	public boolean isBarVisible(@NotNull ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(@NotNull ItemStack stack) {
		IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
		return energy == null ? 0 : Math.round(13.0F * energy.getEnergyStored() / Math.max(1, energy.getMaxEnergyStored()));
	}

	@Override
	public int getBarColor(@NotNull ItemStack stack) {
		IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
		float charge = energy == null ? 0.0F : (float)energy.getEnergyStored() / Math.max(1, energy.getMaxEnergyStored());
		return Mth.hsvToRgb(Math.max(0.0F, charge) / 3.0F, 1.0F, 1.0F);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
		if(energy != null)
			tooltip.add(Component.translatable("tooltip.radiocraft.battery_percentage",
					String.format(Locale.ROOT, "%.1f", 100.0D * energy.getEnergyStored() / Math.max(1, energy.getMaxEnergyStored()))));
		tooltip.add(Component.translatable("tooltip.radiocraft.small_alkaline_battery").withStyle(ChatFormatting.GRAY));
	}
}
