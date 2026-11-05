package com.arrl.radiocraft.common.photo;

import net.minecraft.world.level.material.MapColor;

/**
 * The game's map colours as a {@link PhotoPalette}: every base colour but {@link MapColor#NONE} (transparent) at each of
 * its four brightnesses, as {@link MapColor#getColorFromPackedId} draws them. The same on client and server.
 */
public final class MapPalettes {

	private static PhotoPalette vanilla;

	private MapPalettes() {}

	/** Every colour a map can show. */
	public static PhotoPalette vanilla() {
		PhotoPalette palette = vanilla;
		if(palette == null) {
			int[] rgb = new int[64 * 4];
			byte[] ids = new byte[64 * 4];
			int count = 0;
			for(int base = 1; base < 64; base++) {
				MapColor colour = MapColor.byId(base);
				if(colour == MapColor.NONE)
					continue;
				for(MapColor.Brightness brightness : MapColor.Brightness.values()) {
					int abgr = colour.calculateRGBColor(brightness); // 0xAABBGGRR
					rgb[count] = (abgr & 0xFF) << 16 | (abgr & 0xFF00) | (abgr >> 16) & 0xFF;
					ids[count++] = colour.getPackedId(brightness);
				}
			}
			palette = new PhotoPalette(java.util.Arrays.copyOf(rgb, count), java.util.Arrays.copyOf(ids, count));
			vanilla = palette;
		}
		return palette;
	}

	/** The map greys, black to white: what black and white photo paper prints with. */
	public static PhotoPalette greys() {
		return vanilla().greys();
	}
}
