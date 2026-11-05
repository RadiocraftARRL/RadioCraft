package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.computer.ComputerApp;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A 3.5" floppy disk for the radio computer (1.12 step 5): blank, or holding one program ({@link ComputerApp}). Put it
 * in a computer's drive (use it on the computer or its monitor, or pick it from the Install program) and run INSTALL to
 * copy the program onto the computer; the disk stays as it was. One item per program, registered from
 * {@link com.arrl.radiocraft.common.computer.ComputerApps} ({@code RadiocraftItems.FLOPPIES}).
 */
public class FloppyDiskItem extends Item {

	@Nullable
	private final ComputerApp app;

	public FloppyDiskItem(Properties properties, @Nullable ComputerApp app) {
		super(properties);
		this.app = app;
	}

	/** The program on this disk, or null for a blank one. */
	@Nullable
	public ComputerApp app() {
		return app;
	}

	/** The program on the floppy {@code stack}, or null if it is blank or no floppy. */
	@Nullable
	public static ComputerApp app(ItemStack stack) {
		return stack.getItem() instanceof FloppyDiskItem floppy ? floppy.app : null;
	}

	public static boolean isFloppy(ItemStack stack) {
		return stack.getItem() instanceof FloppyDiskItem;
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		if(app == null)
			return; // A blank disk's line comes from tooltip.radiocraft.floppy_disk (ItemTooltipEvents).
		tooltip.add(Component.literal("A:\\" + app.fileName()).withStyle(ChatFormatting.DARK_AQUA));
		tooltip.add(Component.translatable(app.descriptionKey()).withStyle(ChatFormatting.GRAY));
		if(app.kind() == com.arrl.radiocraft.common.computer.ComputerApp.Kind.SECRET)
			tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "floppy_disk.secret")).withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "floppy_disk.install")).withStyle(ChatFormatting.DARK_GRAY));
	}
}
