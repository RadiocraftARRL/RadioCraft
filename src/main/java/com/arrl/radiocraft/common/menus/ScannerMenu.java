package com.arrl.radiocraft.common.menus;

import com.arrl.radiocraft.common.blockentities.ScannerBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * The scanner's lid opened (1.12 step 5): the glass, the paper tray and the output tray, over the player's inventory.
 * Scanning and printing are done from the radio computer's programs, not here.
 */
public final class ScannerMenu extends AbstractContainerMenu {

	/** Where the slots are on the panel (see {@code ScannerScreen}). */
	public static final int[] BED_AT = {44, 30}, TRAY_AT = {80, 52}, OUTPUT_AT = {134, 30};
	public static final int INVENTORY_X = 8, INVENTORY_Y = 90;
	private static final int PLAYER_START = 3, PLAYER_END = PLAYER_START + 36;

	public final ScannerBlockEntity scanner;

	public ScannerMenu(int id, Inventory playerInventory, ScannerBlockEntity scanner) {
		super(RadiocraftMenuTypes.SCANNER.get(), id);
		this.scanner = scanner;
		addSlot(new SlotItemHandler(scanner.getInventory(), ScannerBlockEntity.BED, BED_AT[0], BED_AT[1]));
		addSlot(new SlotItemHandler(scanner.getInventory(), ScannerBlockEntity.TRAY, TRAY_AT[0], TRAY_AT[1]));
		addSlot(new SlotItemHandler(scanner.getInventory(), ScannerBlockEntity.OUTPUT, OUTPUT_AT[0], OUTPUT_AT[1]));
		for(int row = 0; row < 3; row++)
			for(int col = 0; col < 9; col++)
				addSlot(new Slot(playerInventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
		for(int col = 0; col < 9; col++)
			addSlot(new Slot(playerInventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
	}

	public ScannerMenu(final int id, final Inventory playerInventory, final FriendlyByteBuf data) {
		this(id, playerInventory, MenuUtils.getBlockEntity(playerInventory, data, ScannerBlockEntity.class));
	}

	@Override
	public boolean stillValid(@NotNull Player player) {
		return !scanner.isRemoved() && player.distanceToSqr(scanner.getBlockPos().getCenter()) <= 64.0D;
	}

	/** Shift click: out of the scanner to the player; from the player onto the glass or into the paper tray. */
	@Override
	public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
		Slot slot = slots.get(index);
		if(!slot.hasItem())
			return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if(index < PLAYER_START) {
			if(!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true))
				return ItemStack.EMPTY;
		}
		else if(ScannerBlockEntity.isPaper(stack)) {
			if(!moveItemStackTo(stack, ScannerBlockEntity.TRAY, ScannerBlockEntity.TRAY + 1, false))
				return ItemStack.EMPTY;
		}
		else if(ScannerBlockEntity.isScannable(stack)) {
			if(!moveItemStackTo(stack, ScannerBlockEntity.BED, ScannerBlockEntity.BED + 1, false))
				return ItemStack.EMPTY;
		}
		else
			return ItemStack.EMPTY;
		if(stack.isEmpty())
			slot.setByPlayer(ItemStack.EMPTY);
		else
			slot.setChanged();
		return original;
	}
}
