package com.arrl.radiocraft.mixin;

import com.arrl.radiocraft.common.items.CommercialLicenseItem;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A commercial license is a map (so it hangs in item frames), but a cartography table must not clone it: that would make
 * a second original. Copies are crafted instead ({@link com.arrl.radiocraft.common.crafting.LicenseCopyRecipe}), which
 * stamps them COPY. {@code require = 0}: if the target moves in an update, the table clones licenses again; nothing else
 * breaks.
 */
@Mixin(CartographyTableMenu.class)
public abstract class MixinCartographyTableMenu {

	@Shadow @Final private ResultContainer resultContainer;

	@Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true, require = 0)
	private void radiocraft$noLicenseCloning(ItemStack map, ItemStack firstSlotStack, ItemStack resultOutput, CallbackInfo ci) {
		if(map.getItem() instanceof CommercialLicenseItem) {
			resultContainer.removeItemNoUpdate(2);
			((CartographyTableMenu)(Object)this).broadcastChanges();
			ci.cancel();
		}
	}
}
