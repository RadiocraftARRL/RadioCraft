package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.CommonConfig;
import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.SmallAlkalineBatteryItem;
import com.arrl.radiocraft.common.items.VHFHandheldItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The small cells (2026-11-08): the rechargeable NiCd one and the throwaway alkaline one. */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class BatteryItemGameTests {

	/** A new alkaline cell is full, gives up its charge, and takes none back. */
	@GameTest(template = GameTestUtil.EMPTY)
	public static void alkalineIsFullAndNotRechargeable(GameTestHelper helper) {
		ItemStack alkaline = new ItemStack(RadiocraftItems.SMALL_ALKALINE_BATTERY.get());
		IEnergyStorage energy = alkaline.getCapability(Capabilities.EnergyStorage.ITEM);
		int capacity = CommonConfig.SMALL_BATTERY_CAPACITY.get();
		helper.assertTrue(energy != null && energy.getEnergyStored() == capacity, "A fresh alkaline cell is full, was " + (energy == null ? "no storage" : energy.getEnergyStored()));
		helper.assertTrue(energy.extractEnergy(1000, false) == 1000 && energy.getEnergyStored() == capacity - 1000, "It gives up its charge");
		helper.assertTrue(energy.receiveEnergy(1000, false) == 0 && !energy.canReceive(), "It took charge back");
		helper.assertTrue(alkaline.getCapability(Capabilities.EnergyStorage.ITEM).getEnergyStored() == capacity - 1000, "Its charge is kept on the item");
		helper.assertTrue(SmallAlkalineBatteryItem.fitsWeatherRadio(alkaline) && SmallAlkalineBatteryItem.fitsWeatherRadio(new ItemStack(RadiocraftItems.SMALL_BATTERY.get())),
				"The weather radio takes both cells");
		helper.assertFalse(VHFHandheldItem.takesSmallBattery(alkaline), "The alkaline cell is not a radio");
		helper.succeed();
	}
}
