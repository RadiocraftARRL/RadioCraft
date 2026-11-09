package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * The cells a portable radio runs on (2026-11-09): the rechargeable Small NiCd Battery or the throwaway Small Alkaline
 * Battery. A radio keeps only its cell's charge, plus {@link RadiocraftDataComponent#ALKALINE_CELL} when the cell in it
 * is alkaline, which no charger tops up. Swapping a cell in hands back the one that comes out: a NiCd with its charge,
 * an alkaline with what is left in it, or a Dead Alkaline Battery once it is flat.
 */
public final class PortableBatteries {

	private PortableBatteries() {}

	/** A cell a portable radio takes. */
	public static boolean isCell(ItemStack stack) {
		return stack.getItem() == RadiocraftItems.SMALL_BATTERY.get() || stack.getItem() instanceof SmallAlkalineBatteryItem;
	}

	/** True if the cell in this radio is alkaline: chargers leave it alone. */
	public static boolean hasAlkaline(ItemStack radio) {
		return Boolean.TRUE.equals(radio.get(RadiocraftDataComponent.ALKALINE_CELL.value()));
	}

	/**
	 * Puts one {@code cell} into {@code radio} and returns the cell that came out. Server side; the caller decides where
	 * the returned cell goes and takes the one put in from its stack.
	 */
	public static ItemStack swap(ItemStack radio, ItemStack cell, Player player) {
		IEnergyStorage radioEnergy = radio.getCapability(Capabilities.EnergyStorage.ITEM);
		IEnergyStorage cellEnergy = cell.getCapability(Capabilities.EnergyStorage.ITEM);
		if(radioEnergy == null || cellEnergy == null)
			return ItemStack.EMPTY;
		int ours = radioEnergy.getEnergyStored(), theirs = cellEnergy.getEnergyStored();
		boolean alkalineOut = hasAlkaline(radio), alkalineIn = cell.getItem() instanceof SmallAlkalineBatteryItem;
		radioEnergy.extractEnergy(ours, false);
		radioEnergy.receiveEnergy(theirs, false);
		if(radio.getItem() instanceof CbPortableItem) // Its charge is a plain component, set exactly.
			radio.set(RadiocraftDataComponent.CB_ENERGY.value(), Math.min(CbPortableItem.capacity(), theirs));
		if(alkalineIn)
			radio.set(RadiocraftDataComponent.ALKALINE_CELL.value(), true);
		else
			radio.remove(RadiocraftDataComponent.ALKALINE_CELL.value());
		ItemStack out;
		if(alkalineOut && ours <= 0)
			out = new ItemStack(RadiocraftItems.DEAD_ALKALINE_BATTERY.get());
		else {
			out = new ItemStack(alkalineOut ? RadiocraftItems.SMALL_ALKALINE_BATTERY.get() : RadiocraftItems.SMALL_BATTERY.get());
			out.set(RadiocraftDataComponent.BATTERY_ENERGY.value(), ours);
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.displayClientMessage(Component.translatable("message.radiocraft.battery_swapped"), true);
		return out;
	}

	/** A cell on the cursor clicked onto a radio in {@code slot}: the cell goes in, the old one comes back on the cursor. */
	public static void cellOntoRadio(ItemStack carried, Slot slot, Player player) {
		cellOntoRadio(carried, slot, player, player.containerMenu::setCarried);
	}

	/** {@link #cellOntoRadio(ItemStack, Slot, Player)}, putting the old cell on the cursor through {@code setCarried} (creative). */
	public static void cellOntoRadio(ItemStack carried, Slot slot, Player player, java.util.function.Consumer<ItemStack> setCarried) {
		ItemStack out = swap(slot.getItem(), carried.copyWithCount(1), player);
		if(out.isEmpty())
			return;
		slot.setChanged();
		if(carried.getCount() == 1)
			setCarried.accept(out);
		else {
			carried.shrink(1);
			give(player, out);
		}
	}

	/** A radio on the cursor clicked onto a cell in {@code slot}: the cell goes in, the old one takes its place. */
	public static void radioOntoCell(ItemStack radio, Slot slot, Player player) {
		ItemStack cell = slot.getItem();
		ItemStack out = swap(radio, cell.copyWithCount(1), player);
		if(out.isEmpty())
			return;
		if(cell.getCount() == 1)
			slot.set(out);
		else {
			cell.shrink(1);
			slot.setChanged();
			give(player, out);
		}
	}

	private static void give(Player player, ItemStack stack) {
		if(!player.getInventory().add(stack))
			player.drop(stack, false);
	}
}
