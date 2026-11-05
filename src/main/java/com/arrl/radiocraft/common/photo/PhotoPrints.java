package com.arrl.radiocraft.common.photo;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.datacomponents.PhotoInfo;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pictures as things to hold: a locked vanilla filled map with the picture on it (like a WEFAX print), so it goes in an
 * item frame, copies at a cartography table and goes through a radio fax as any map does (the fax copies the stack and
 * its map id, and its SSTV tones read the picture from the map). The map never changes after it is printed.
 * <p>
 * The API the camera uses and the radio computer's scanner and SSTV are meant to: {@link #photo} for a photo,
 * {@link #print} for any picture, {@link #pixels} to read a map's picture back, {@link #isPhoto}/{@link #info}.
 * Pictures are {@link PhotoImage#PIXELS} packed map colour ids (see {@link PhotoImage}).
 */
public final class PhotoPrints {

	/** The inventory tint of a colour photo: glossy paper with a blue cast. */
	public static final int COLOUR_TINT = 0xC8D4E4;
	/** The inventory tint of a black and white photo: matte white. */
	public static final int BW_TINT = 0xE4E4E0;

	private PhotoPrints() {}

	/**
	 * A photo of {@code pixels}, as the camera prints it: named "Photo" or "B&W Photo", "Taken by ..." in its tooltip,
	 * with {@link PhotoInfo}. The pixels are checked here: ids that are no map colour become black, and on black and
	 * white paper every colour becomes its grey (a client cannot sneak colour onto B&W paper).
	 *
	 * @param where where it was taken (the map's centre; nothing shows it)
	 */
	public static ItemStack photo(ServerLevel level, byte[] pixels, PhotoInfo info, BlockPos where) {
		PhotoPalette palette = MapPalettes.vanilla();
		byte[] checked = palette.sanitised(pixels);
		if(!info.colour())
			checked = palette.greyscaled(checked);
		Component name = Component.translatable(Radiocraft.translationKey("item", info.colour() ? "photo" : "photo.bw"));
		List<Component> lore = new ArrayList<>();
		if(!info.photographer().isEmpty())
			lore.add(Component.translatable(Radiocraft.translationKey("item", "photo.by"), info.photographer()).withStyle(ChatFormatting.GRAY));
		ItemStack stack = print(level, checked, name, lore, info.colour() ? COLOUR_TINT : BW_TINT, where);
		stack.set(RadiocraftDataComponent.PHOTO.get(), info);
		return stack;
	}

	/**
	 * A locked filled map of {@code pixels} ({@link PhotoImage#PIXELS} packed map colour ids, taken as they are), named
	 * {@code name}, with {@code lore} and the inventory tint {@code tint}.
	 */
	public static ItemStack print(ServerLevel level, byte[] pixels, Component name, List<Component> lore, int tint, BlockPos where) {
		if(pixels.length != PhotoImage.PIXELS)
			throw new IllegalArgumentException("A map picture is " + PhotoImage.PIXELS + " pixels, not " + pixels.length);
		MapId id = level.getFreeMapId();
		MapItemSavedData data = MapItemSavedData.createFresh(where.getX(), where.getZ(), (byte)0, false, false, level.dimension()).locked();
		System.arraycopy(pixels, 0, data.colors, 0, PhotoImage.PIXELS); // Nobody holds it yet: a new holder gets the whole picture.
		data.setDirty();
		level.setMapData(id, data);
		ItemStack stack = new ItemStack(Items.FILLED_MAP);
		stack.set(DataComponents.MAP_ID, id);
		stack.set(DataComponents.ITEM_NAME, name);
		stack.set(DataComponents.MAP_COLOR, new MapItemColor(tint));
		if(!lore.isEmpty())
			stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	/** The picture on filled map {@code stack} (a copy: {@link PhotoImage#PIXELS} map colour ids), or null if none. */
	@Nullable
	public static byte[] pixels(Level level, ItemStack stack) {
		if(!stack.is(Items.FILLED_MAP))
			return null;
		MapId id = stack.get(DataComponents.MAP_ID);
		MapItemSavedData data = id == null ? null : level.getMapData(id);
		return data == null ? null : data.colors.clone();
	}

	/** True if {@code stack} is a photo (from a camera, or anything else that printed one with {@link #photo}). */
	public static boolean isPhoto(ItemStack stack) {
		return stack.has(RadiocraftDataComponent.PHOTO.get());
	}

	/** The photo's details, or null if {@code stack} is not a photo. */
	@Nullable
	public static PhotoInfo info(ItemStack stack) {
		return stack.get(RadiocraftDataComponent.PHOTO.get());
	}
}
