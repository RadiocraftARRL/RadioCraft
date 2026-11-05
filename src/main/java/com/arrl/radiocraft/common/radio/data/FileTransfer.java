package com.arrl.radiocraft.common.radio.data;

import com.arrl.radiocraft.common.radio.voice.Afsk;
import com.arrl.radiocraft.common.radio.voice.Sstv;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Documents a radio fax can send: written books and books and quills, paper (named or not), filled maps and enchanted
 * books. The sender keeps the original, but an enchanted book is used up; the receiver prints a copy on its own blank: a
 * book, a sheet of paper or an empty map. Written books copy as vanilla copies them (a copy of a copy cannot be
 * copied). The old digital interface's tones are still here: books and paper as packet tones, a little longer per page;
 * maps as SSTV, the picture itself. The fax itself paces its packets by {@link FaxRates}.
 */
public final class FileTransfer {

	private FileTransfer() {}

	/** Map pictures are 128 by 128. */
	public static final int MAP_SIZE = 128;
	/** Packet tones for paper and the first page of a book, then each further page. */
	private static final double BASE_SECONDS = 2.0D;
	private static final double SECONDS_PER_PAGE = 0.5D;
	private static final double MAX_SECONDS = 30.0D;

	/** True if {@code stack} can be sent: a file, and (for a written book) one that may still be copied. */
	public static boolean canSend(ItemStack stack) {
		if(stack.is(Items.WRITTEN_BOOK)) {
			WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
			return content == null || content.tryCraftCopy() != null;
		}
		return stack.is(Items.WRITABLE_BOOK) || stack.is(Items.PAPER) || stack.is(Items.FILLED_MAP) || stack.is(Items.ENCHANTED_BOOK);
	}

	/** True if sending {@code stack} uses it up: an enchanted book goes, it is not copied. */
	public static boolean consumed(ItemStack stack) {
		return stack.is(Items.ENCHANTED_BOOK);
	}

	/** The blank a receiver prints {@code stack} on: {@link Items#BOOK}, {@link Items#PAPER} or {@link Items#MAP}. */
	public static ItemStack blankFor(ItemStack stack) {
		if(stack.is(Items.WRITTEN_BOOK) || stack.is(Items.WRITABLE_BOOK) || stack.is(Items.ENCHANTED_BOOK))
			return new ItemStack(Items.BOOK);
		if(stack.is(Items.FILLED_MAP))
			return new ItemStack(Items.MAP);
		return new ItemStack(Items.PAPER);
	}

	/** The copy a receiver prints, or null if it may not be copied. */
	@Nullable
	public static ItemStack copy(ItemStack stack) {
		if(!canSend(stack))
			return null;
		ItemStack copy = stack.copyWithCount(1);
		WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if(content != null)
			copy.set(DataComponents.WRITTEN_BOOK_CONTENT, content.tryCraftCopy());
		return copy;
	}

	/** What the screens call the file: its kind and its title or name. */
	public static String describe(ItemStack stack) {
		String mode = mode(stack);
		String kind = mode.equals(MODE_SSTV) ? "SSTV" : mode.equals(MODE_WEFAX) ? "WEFAX"
				: stack.is(Items.FILLED_MAP) ? (com.arrl.radiocraft.common.photo.PhotoPrints.isPhoto(stack) ? "PHOTO" : "MAP")
				: stack.is(Items.PAPER) ? "PAPER" : stack.is(Items.ENCHANTED_BOOK) ? "ENCH BOOK" : "BOOK";
		WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		String name = content != null ? content.title().raw() : stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : "";
		return name.isEmpty() ? kind : kind + " " + name;
	}

	/** Pages in a book, 1 for anything else. */
	public static int pages(ItemStack stack) {
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if(written != null)
			return written.pages().size();
		WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
		return writable != null ? writable.pages().size() : 1;
	}

	/** Bytes of packet tones a book or paper goes out as. */
	private static int packetBytes(ItemStack stack) {
		double seconds = Math.min(MAX_SECONDS, BASE_SECONDS + SECONDS_PER_PAGE * Math.max(0, pages(stack) - 1));
		return Math.max(1, (int)(seconds * Afsk.BAUD / 8.0D) - Afsk.PREAMBLE_FLAGS - Afsk.TAIL_FLAGS);
	}

	/** How many 20 ms frames of tones the file takes. The same at both ends, so receivers can show progress. */
	public static int frameCount(ItemStack stack) {
		if(mode(stack).equals(MODE_WEFAX))
			return wefaxTicks() * 5 / 2; // 2.5 frames a tick.
		if(stack.is(Items.FILLED_MAP))
			return Sstv.frameCount(MAP_SIZE, MAP_SIZE);
		return Afsk.frameCount(packetBytes(stack));
	}

	/** Ticks a WEFAX chart takes on the air: as long as a weather fax sheet ({@code wefax_bulletin_seconds}). */
	public static int wefaxTicks() {
		try {
			return com.arrl.radiocraft.RadiocraftServerConfig.WEFAX_BULLETIN_SECONDS.get() * 20;
		}
		catch(IllegalStateException e) { // Before the config loads.
			return 12 * 20;
		}
	}

	/** Ticks an SSTV picture takes on the air (its frames at 2.5 a tick). */
	public static int sstvTicks() {
		return (Sstv.frameCount(MAP_SIZE, MAP_SIZE) * 2 + 4) / 5;
	}

	/**
	 * The tones of the file: a WEFAX chart's fax tones, SSTV of a picture (a map's as {@code level} knows it), packet
	 * tones for the rest.
	 */
	public static List<short[]> audio(ItemStack stack, @Nullable Level level, long seed) {
		if(!stack.is(Items.FILLED_MAP))
			return Afsk.burst(packetBytes(stack), seed);
		byte[] pixels = imagePixels(stack, level);
		if(mode(stack).equals(MODE_WEFAX))
			return com.arrl.radiocraft.common.radio.voice.WefaxAudio.frames(pixels == null ? new byte[MAP_SIZE * MAP_SIZE] : pixels, MAP_SIZE, wefaxTicks());
		double[][] rows = new double[MAP_SIZE][MAP_SIZE];
		for(int y = 0; y < MAP_SIZE; y++) {
			for(int x = 0; x < MAP_SIZE; x++) {
				int colour = pixels == null ? 0 : MapColor.getColorFromPackedId(pixels[x + y * MAP_SIZE] & 0xFF);
				rows[y][x] = (((colour >> 16) & 0xFF) + ((colour >> 8) & 0xFF) + (colour & 0xFF)) / (3.0D * 255.0D);
			}
		}
		return Sstv.frames(rows);
	}

	// -------------------- PICTURES FROM THE RADIO COMPUTER (1.12 step 7) --------------------

	/** How the radio computer sends a picture: by SSTV, as a WEFAX chart, or as a plain picture (by radio fax). */
	public static final String MODE_SSTV = "sstv", MODE_WEFAX = "wefax", MODE_PICTURE = "picture";
	private static final String PIXELS = "radiocraft_pixels", MODE = "radiocraft_mode";

	/**
	 * A picture as the radio computer sends it, with no map behind it: a filled map item carrying its
	 * {@code MAP_SIZE * MAP_SIZE} map colour ids itself ({@link #imagePixels}), called {@code name}. It only ever goes
	 * over the air (packets are never saved, so no map file is made for it); what receives it keeps the picture (a
	 * computer) or prints it on a blank map of its own ({@link #materialise}: a fax machine). {@code mode} is
	 * {@link #MODE_SSTV}, {@link #MODE_WEFAX} or {@link #MODE_PICTURE}; a filled map sent with {@link #MODE_SSTV} or
	 * {@link #MODE_PICTURE} goes out as SSTV tones, one with {@link #MODE_WEFAX} as weather fax tones.
	 */
	public static ItemStack image(byte[] pixels, String name, String mode) {
		if(pixels.length != MAP_SIZE * MAP_SIZE)
			throw new IllegalArgumentException("A picture is " + MAP_SIZE * MAP_SIZE + " pixels, not " + pixels.length);
		CompoundTag tag = new CompoundTag();
		tag.putByteArray(PIXELS, pixels.clone());
		tag.putString(MODE, mode);
		ItemStack stack = new ItemStack(Items.FILLED_MAP);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		if(!name.isEmpty())
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		return stack;
	}

	/** True for a picture made by {@link #image}: it has no map of its own. */
	public static boolean isVirtualImage(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return stack.is(Items.FILLED_MAP) && data != null && data.contains(PIXELS);
	}

	/** How a computer's picture was sent ({@link #MODE_SSTV}, {@link #MODE_WEFAX}, {@link #MODE_PICTURE}); "" for anything else. */
	public static String mode(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if(!stack.is(Items.FILLED_MAP) || data == null || !data.contains(MODE))
			return "";
		return data.copyTag().getString(MODE);
	}

	/**
	 * The picture on {@code stack}: a computer's picture's own pixels, or a filled map's (as {@code level} knows it).
	 * A copy, {@code MAP_SIZE * MAP_SIZE} map colour ids; null if it has none.
	 */
	@Nullable
	public static byte[] imagePixels(ItemStack stack, @Nullable Level level) {
		if(!stack.is(Items.FILLED_MAP))
			return null;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if(data != null && data.contains(PIXELS)) {
			byte[] pixels = data.copyTag().getByteArray(PIXELS);
			return pixels.length == MAP_SIZE * MAP_SIZE ? pixels : null;
		}
		MapItemSavedData map = level == null ? null : MapItem.getSavedData(stack, level);
		return map == null ? null : map.colors.clone();
	}

	/**
	 * A computer's picture put on a real map, as a fax machine prints it on its blank: a locked filled map of its pixels
	 * with its name. Anything else comes back as it is.
	 */
	public static ItemStack materialise(ServerLevel level, ItemStack stack, BlockPos where) {
		if(!isVirtualImage(stack))
			return stack;
		byte[] pixels = imagePixels(stack, level);
		if(pixels == null)
			return stack;
		Component name = stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName() : Component.translatable("item.radiocraft.printout");
		return com.arrl.radiocraft.common.photo.PhotoPrints.print(level, com.arrl.radiocraft.common.photo.MapPalettes.vanilla().sanitised(pixels), name, List.of(),
				com.arrl.radiocraft.common.photo.PhotoPrints.BW_TINT, where);
	}
}
