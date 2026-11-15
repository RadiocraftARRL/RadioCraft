package com.arrl.radiocraft.common.crafting;

import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.init.RadiocraftRecipeSerializers;
import com.arrl.radiocraft.common.items.CommercialLicenseItem;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicense;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * An original commercial license and a license upgrade, anywhere in the grid (2026-11-10): the same license one tier up.
 * The result has no picture yet; when carried it is checked against the server's licenses, gets its paired input
 * frequency on the first upgrade, and is drawn ({@link CommercialLicenseItem#inventoryTick}). Copies and licenses at
 * {@link CommercialLicense#MAX_TIER} do not match.
 */
public class LicenseUpgradeRecipe extends CustomRecipe {

	public LicenseUpgradeRecipe(CraftingBookCategory category) {
		super(category);
	}

	@Override
	public boolean matches(@NotNull CraftingInput input, @NotNull Level level) {
		return !find(input).isEmpty();
	}

	@Override
	public @NotNull ItemStack assemble(@NotNull CraftingInput input, HolderLookup.@NotNull Provider registries) {
		ItemStack found = find(input);
		if(found.isEmpty())
			return ItemStack.EMPTY;
		CommercialLicense license = CommercialLicenseItem.license(found);
		ItemStack out = found.copyWithCount(1);
		out.set(RadiocraftDataComponent.COMMERCIAL_LICENSE.value(), license.withTier(license.tier() + 1, license.inputHz()));
		out.remove(DataComponents.MAP_ID); // Drawn again, with the new tier, when carried.
		return out;
	}

	/** The license, if the grid holds exactly one original license below the top tier and one upgrade; empty otherwise. */
	public static ItemStack find(CraftingInput input) {
		ItemStack license = ItemStack.EMPTY;
		int upgrades = 0;
		for(int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if(stack.isEmpty())
				continue;
			if(stack.is(RadiocraftItems.COMMERCIAL_LICENSE_UPGRADE.get()))
				upgrades++;
			else if(license.isEmpty() && CommercialLicenseItem.isOriginal(stack) && CommercialLicenseItem.license(stack).tier() < CommercialLicense.MAX_TIER)
				license = stack;
			else
				return ItemStack.EMPTY;
		}
		return upgrades == 1 ? license : ItemStack.EMPTY;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public @NotNull RecipeSerializer<?> getSerializer() {
		return RadiocraftRecipeSerializers.LICENSE_UPGRADE.get();
	}
}
