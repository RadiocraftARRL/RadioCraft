package com.arrl.radiocraft.common.crafting;

import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.init.RadiocraftRecipeSerializers;
import com.arrl.radiocraft.common.items.MarsModItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * A radio a MARS mod fits ({@link MarsModItem#fits}) and a MARS mod, anywhere in the grid: the same radio, with every
 * component it had (settings, charge, memory channels, crystal), and the mod fitted. A radio that already has one is
 * not a match.
 */
public class MarsModInstallRecipe extends CustomRecipe {

	public MarsModInstallRecipe(CraftingBookCategory category) {
		super(category);
	}

	@Override
	public boolean matches(@NotNull CraftingInput input, @NotNull Level level) {
		return !find(input).isEmpty();
	}

	@Override
	public @NotNull ItemStack assemble(@NotNull CraftingInput input, HolderLookup.@NotNull Provider registries) {
		ItemStack radio = find(input);
		if(radio.isEmpty())
			return ItemStack.EMPTY;
		ItemStack out = radio.copyWithCount(1);
		out.set(RadiocraftDataComponent.MARS_MOD.value(), Unit.INSTANCE);
		return out;
	}

	/** The radio, if the grid holds exactly one unmodded radio and one MARS mod; empty otherwise. */
	public static ItemStack find(CraftingInput input) {
		ItemStack radio = ItemStack.EMPTY;
		int mods = 0;
		for(int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if(stack.isEmpty())
				continue;
			if(stack.is(RadiocraftItems.MARS_MOD.get()))
				mods++;
			else if(radio.isEmpty() && MarsModItem.fits(stack) && !MarsModItem.isFitted(stack))
				radio = stack;
			else
				return ItemStack.EMPTY;
		}
		return mods == 1 ? radio : ItemStack.EMPTY;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public @NotNull RecipeSerializer<?> getSerializer() {
		return RadiocraftRecipeSerializers.MARS_MOD_INSTALL.get();
	}
}
