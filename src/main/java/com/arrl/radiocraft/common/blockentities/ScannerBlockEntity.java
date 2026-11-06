package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.computer.BookPages;
import com.arrl.radiocraft.common.datacomponents.PhotoInfo;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.menus.ScannerMenu;
import com.arrl.radiocraft.common.photo.PhotoImage;
import com.arrl.radiocraft.common.photo.PhotoPrints;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The radio computer's scanner and printer (1.12 step 5), set beside the computer (or on its data wire). Three slots:
 * the glass ({@link #BED}: a map, a photo or a book to scan), the paper tray ({@link #TRAY}: paper, photo paper, color
 * photo paper or empty maps) and the output tray ({@link #OUTPUT}, take only). The computer's programs reach it with
 * {@code ComputerContext#scanner()} and call the methods below; it does nothing on its own (no ticking).
 * <ul>
 * <li>{@link #scanImage()}: the picture on a map or photo on the glass. {@link #scanPages()}: a book's pages.</li>
 * <li>{@link #printImage}: a picture on the tray's paper: color photo paper prints a color photo, an empty map a plain
 * color print, photo paper a black and white photo.</li>
 * <li>{@link #printBook}: a written book, one sheet of paper per page.</li>
 * </ul>
 * Prints go to the output tray; one waiting there jams the next ({@link Result#OUTPUT_FULL}), as a real one would.
 */
public class ScannerBlockEntity extends BlockEntity implements MenuProvider {

	public static final int BED = 0, TRAY = 1, OUTPUT = 2;
	/** The inventory tint of a plain print on a map. */
	public static final int PRINT_TINT = 0x46402E;

	/** How a print or scan went. {@link #key()} is the lang key suffix under {@code screen.radiocraft.computer.err.}. */
	public enum Result {
		OK, NO_PAPER, OUTPUT_FULL, BED_EMPTY, BAD_IMAGE, NOTHING_TO_PRINT;

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private final ItemStackHandler inventory = new ItemStackHandler(3) {
		@Override
		public boolean isItemValid(int slot, @NotNull ItemStack stack) {
			return slot == BED ? isScannable(stack) : slot == TRAY && isPaper(stack);
		}

		@Override
		public int getSlotLimit(int slot) {
			return slot == BED ? 1 : 64;
		}

		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}
	};

	public ScannerBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.SCANNER.get(), pos, state);
	}

	public ItemStackHandler getInventory() {
		return inventory;
	}

	/**
	 * Things that go on the glass: maps and photos, written books and books and quills. (Radios went on it in 1.12
	 * step 6 to be programmed; since playtest fixes 2 the CPS programs a radio held in the hand instead.)
	 */
	public static boolean isScannable(ItemStack stack) {
		return stack.is(Items.FILLED_MAP) || stack.is(Items.WRITTEN_BOOK) || stack.is(Items.WRITABLE_BOOK)
				|| com.arrl.radiocraft.common.qsl.QslCards.is(stack); // A QSL card, read into a .QSL file (computer batch 3C).
	}

	/** Things that go in the paper tray. */
	public static boolean isPaper(ItemStack stack) {
		return stack.is(Items.PAPER) || stack.is(Items.MAP) || stack.is(RadiocraftItems.PHOTO_PAPER.get()) || stack.is(RadiocraftItems.COLOR_PHOTO_PAPER.get());
	}

	public ItemStack bed() {
		return inventory.getStackInSlot(BED);
	}

	public ItemStack tray() {
		return inventory.getStackInSlot(TRAY);
	}

	public ItemStack output() {
		return inventory.getStackInSlot(OUTPUT);
	}

	// -------------------- SCANNING --------------------

	/** The picture on the map or photo on the glass ({@link PhotoImage#PIXELS} map colour ids), or null. */
	@Nullable
	public byte[] scanImage() {
		if(level == null || !bed().is(Items.FILLED_MAP))
			return null;
		byte[] pixels = PhotoPrints.pixels(level, bed());
		if(pixels != null)
			scanSound();
		return pixels;
	}

	/** The pages of the book on the glass, as plain text, or null if there is no book. */
	@Nullable
	public List<String> scanPages() {
		ItemStack stack = bed();
		List<String> pages = new ArrayList<>();
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
		if(stack.is(Items.WRITTEN_BOOK) && written != null)
			for(Filterable<Component> page : written.pages())
				pages.add(page.raw().getString());
		else if(stack.is(Items.WRITABLE_BOOK) && writable != null)
			for(Filterable<String> page : writable.pages())
				pages.add(page.raw());
		else if(!stack.is(Items.WRITABLE_BOOK))
			return null;
		scanSound();
		return pages;
	}

	/** The title of the book on the glass, or its name for anything else; empty if the glass is bare. */
	public String bedTitle() {
		ItemStack stack = bed();
		if(stack.isEmpty())
			return "";
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		return written != null ? written.title().raw() : stack.getHoverName().getString();
	}

	/** The QSL card on the glass (computer batch 3C), or null. */
	@Nullable
	public com.arrl.radiocraft.common.qsl.QslCard scanQsl() {
		com.arrl.radiocraft.common.qsl.QslCard card = com.arrl.radiocraft.common.qsl.QslCards.card(bed());
		if(card != null)
			scanSound();
		return card;
	}

	// -------------------- PRINTING --------------------

	/**
	 * Prints {@code pixels} on the tray's paper (see the class comment), named {@code title} (a photo is named "Photo"
	 * whatever the title) and marked as printed by {@code by}.
	 */
	public Result printImage(byte[] pixels, String title, String by) {
		return printImage(pixels, title, by, false);
	}

	/**
	 * As {@link #printImage(byte[], String, String)}; with {@code plainPaper} a sheet of paper takes a plain print too
	 * (as an empty map does): the Awards program's certificates (computer batch 3C).
	 */
	public Result printImage(byte[] pixels, String title, String by, boolean plainPaper) {
		if(!(level instanceof ServerLevel server))
			return Result.NO_PAPER;
		if(pixels == null || pixels.length != PhotoImage.PIXELS)
			return Result.BAD_IMAGE;
		if(!output().isEmpty())
			return Result.OUTPUT_FULL;
		ItemStack paper = tray();
		ItemStack print;
		if(paper.is(RadiocraftItems.COLOR_PHOTO_PAPER.get()) || paper.is(RadiocraftItems.PHOTO_PAPER.get()))
			print = PhotoPrints.photo(server, pixels, new PhotoInfo(by, paper.is(RadiocraftItems.COLOR_PHOTO_PAPER.get()), "scanner"), worldPosition);
		else if(paper.is(Items.MAP) || plainPaper && paper.is(Items.PAPER)) {
			Component name = title == null || title.isBlank() ? Component.translatable(Radiocraft.translationKey("item", "printout"))
					: Component.literal(title.length() > 32 ? title.substring(0, 32) : title);
			print = PhotoPrints.print(server, com.arrl.radiocraft.common.photo.MapPalettes.vanilla().sanitised(pixels), name, List.of(), PRINT_TINT, worldPosition);
		}
		else
			return Result.NO_PAPER;
		paper.shrink(1);
		inventory.setStackInSlot(TRAY, paper.isEmpty() ? ItemStack.EMPTY : paper);
		inventory.setStackInSlot(OUTPUT, print);
		printSound();
		return Result.OK;
	}

	/**
	 * Prints a written book of {@code pages} (cut to the game's 100 pages, each to {@link BookPages#PAGE_CHARS}),
	 * titled {@code title} by {@code author}: one sheet of plain paper per page.
	 */
	public Result printBook(String title, String author, List<String> pages) {
		if(level == null || level.isClientSide)
			return Result.NO_PAPER;
		if(pages.isEmpty())
			return Result.NOTHING_TO_PRINT;
		if(!output().isEmpty())
			return Result.OUTPUT_FULL;
		List<String> kept = pages.size() > BookPages.MAX_PAGES ? pages.subList(0, BookPages.MAX_PAGES) : pages;
		ItemStack paper = tray();
		if(!paper.is(Items.PAPER) || paper.getCount() < kept.size())
			return Result.NO_PAPER;
		List<Filterable<Component>> bookPages = new ArrayList<>();
		for(String page : kept)
			bookPages.add(Filterable.passThrough(Component.literal(page.length() > BookPages.PAGE_CHARS ? page.substring(0, BookPages.PAGE_CHARS) : page)));
		String cleanTitle = title == null || title.isBlank() ? "Printout" : title.strip();
		if(cleanTitle.length() > WrittenBookContent.TITLE_MAX_LENGTH)
			cleanTitle = cleanTitle.substring(0, WrittenBookContent.TITLE_MAX_LENGTH);
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(cleanTitle), author == null ? "" : author, 0, bookPages, true));
		paper.shrink(kept.size());
		inventory.setStackInSlot(TRAY, paper.isEmpty() ? ItemStack.EMPTY : paper);
		inventory.setStackInSlot(OUTPUT, book);
		printSound();
		return Result.OK;
	}

	/**
	 * Prints {@code count} blank QSL cards of {@code card}'s design (computer batch 3C), one sheet of paper or photo
	 * paper each (as many as the tray holds, at most 64); they stack in the output tray. Answers how many were printed
	 * in {@code printed[0]}.
	 */
	public Result printCards(com.arrl.radiocraft.common.qsl.QslCard card, int count, int[] printed) {
		printed[0] = 0;
		if(level == null || level.isClientSide)
			return Result.NO_PAPER;
		if(!output().isEmpty())
			return Result.OUTPUT_FULL;
		ItemStack paper = tray();
		if(!isCardStock(paper))
			return Result.NO_PAPER;
		int n = Math.min(Math.min(64, Math.max(1, count)), paper.getCount());
		paper.shrink(n);
		inventory.setStackInSlot(TRAY, paper.isEmpty() ? ItemStack.EMPTY : paper);
		inventory.setStackInSlot(OUTPUT, com.arrl.radiocraft.common.qsl.QslCards.stack(card, n));
		printed[0] = n;
		printSound();
		return Result.OK;
	}

	/** What a QSL card prints on: paper, photo paper or color photo paper. */
	public static boolean isCardStock(ItemStack stack) {
		return stack.is(Items.PAPER) || stack.is(RadiocraftItems.PHOTO_PAPER.get()) || stack.is(RadiocraftItems.COLOR_PHOTO_PAPER.get());
	}

	/** How many of the tray's sheets {@code item} there are (0 if the tray holds something else). */
	public int paperCount(net.minecraft.world.item.Item item) {
		return tray().is(item) ? tray().getCount() : 0;
	}

	private void scanSound() {
		if(level != null)
			level.playSound(null, worldPosition, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.6F, 0.7F);
	}

	private void printSound() {
		if(level != null)
			level.playSound(null, worldPosition, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 0.8F, 1.2F);
	}

	/** Drops everything it holds. */
	public void dropContents() {
		if(level == null)
			return;
		for(int slot = 0; slot < inventory.getSlots(); slot++)
			Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), inventory.getStackInSlot(slot));
	}

	// -------------------- MENU & SAVE --------------------

	@Override
	public @NotNull Component getDisplayName() {
		return Component.translatable(Radiocraft.translationKey("container", "scanner"));
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory playerInventory, @NotNull Player player) {
		return new ScannerMenu(id, playerInventory, this);
	}

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("inventory", inventory.serializeNBT(registries));
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.loadAdditional(tag, registries);
		if(tag.contains("inventory"))
			inventory.deserializeNBT(registries, tag.getCompound("inventory"));
	}
}
