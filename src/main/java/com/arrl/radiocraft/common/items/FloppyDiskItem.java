package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.datacomponents.FloppyData;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
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
 * <p>
 * A blank disk in the drive is drive A: (computer batch 3A): files copied onto it make it a Data Floppy, the same item
 * with a {@link FloppyData} component (its files are on the server, named by the component's id). Its tooltip shows its
 * label and how full it is. Program disks are write-protected.
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

	/** A blank disk with files on it (or a label) is a Data Floppy. */
	@Override
	public @NotNull Component getName(@NotNull ItemStack stack) {
		if(app == null && stack.has(RadiocraftDataComponent.FLOPPY_DATA.value()))
			return Component.translatable(getDescriptionId() + ".data");
		return super.getName(stack);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		FloppyData data = stack.get(RadiocraftDataComponent.FLOPPY_DATA.value());
		if(app == null && data != null) { // A data floppy (ItemTooltipEvents leaves out the blank disk's line for it).
			if(!data.label().isEmpty())
				tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "floppy_data.label"), data.label()).withStyle(ChatFormatting.AQUA));
			tooltip.add(data.files() == 0 ? Component.translatable(Radiocraft.translationKey("tooltip", "floppy_data.empty")).withStyle(ChatFormatting.GRAY)
					: Component.translatable(Radiocraft.translationKey("tooltip", "floppy_data.files"), data.files(),
					com.arrl.radiocraft.common.computer.FloppyDisk.size(data.used())).withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "floppy_data.use")).withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		if(app == null)
			return; // A blank disk's line comes from tooltip.radiocraft.floppy_disk (ItemTooltipEvents).
		tooltip.add(Component.literal("A:\\" + app.fileName()).withStyle(ChatFormatting.DARK_AQUA));
		tooltip.add(Component.translatable(app.descriptionKey()).withStyle(ChatFormatting.GRAY));
		if(app.kind() == com.arrl.radiocraft.common.computer.ComputerApp.Kind.SECRET)
			tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "floppy_disk.secret")).withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "floppy_disk.install")).withStyle(ChatFormatting.DARK_GRAY));
	}
}
