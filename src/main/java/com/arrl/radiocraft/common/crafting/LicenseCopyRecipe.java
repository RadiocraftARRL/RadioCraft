package com.arrl.radiocraft.common.crafting;

import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftRecipeSerializers;
import com.arrl.radiocraft.common.items.CommercialLicenseItem;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicense;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * A commercial license (original or copy) and an empty map (2026-11-10): a copy, stamped COPY, while the license
 * itself stays in the grid. A copy shows everything the license does but is no license to program radios with. The
 * copy's picture is drawn when it is first carried.
 */
public class LicenseCopyRecipe extends CustomRecipe {

	public LicenseCopyRecipe(CraftingBookCategory category) {
		super(category);
	}

	@Override
	public boolean matches(@NotNull CraftingInput input, @NotNull Level level) {
		return find(input) >= 0;
	}

	@Override
	public @NotNull ItemStack assemble(@NotNull CraftingInput input, HolderLookup.@NotNull Provider registries) {
		int at = find(input);
		if(at < 0)
			return ItemStack.EMPTY;
		ItemStack source = input.getItem(at);
		CommercialLicense license = CommercialLicenseItem.license(source);
		ItemStack out = source.copyWithCount(1);
		out.set(RadiocraftDataComponent.COMMERCIAL_LICENSE.value(), license.asCopy());
		out.remove(DataComponents.MAP_ID); // Its own picture, with the COPY stamp, drawn when carried.
		return out;
	}

	@Override
	public @NotNull NonNullList<ItemStack> getRemainingItems(@NotNull CraftingInput input) {
		NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
		int at = find(input);
		if(at >= 0)
			remaining.set(at, input.getItem(at).copyWithCount(1));
		return remaining;
	}

	/** The slot of the license, if the grid holds exactly one license and one empty map; -1 otherwise. */
	public static int find(CraftingInput input) {
		int license = -1, maps = 0;
		for(int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if(stack.isEmpty())
				continue;
			if(stack.is(Items.MAP))
				maps++;
			else if(license < 0 && CommercialLicenseItem.license(stack) != null)
				license = i;
			else
				return -1;
		}
		return maps == 1 ? license : -1;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public @NotNull RecipeSerializer<?> getSerializer() {
		return RadiocraftRecipeSerializers.LICENSE_COPY.get();
	}
}
