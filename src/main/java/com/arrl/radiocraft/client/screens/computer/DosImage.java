package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.photo.PhotoPalette;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Textures for the pictures programs put on the radio computer's screen ({@link TextGrid#image}): map colour ids made
 * into a {@link DynamicTexture} once per key, and uploaded again only when the picture under that key changes. The
 * last {@link #MAX} are kept; {@link #releaseAll()} frees them (the screen closing for good). Render thread.
 */
public final class DosImage {

	/** Most textures kept at once. */
	private static final int MAX = 24;

	private record Entry(ResourceLocation id, DynamicTexture texture, byte[] pixels) {}

	private static final Map<String, Entry> TEXTURES = new LinkedHashMap<>(16, 0.75F, true);
	private static int next = 0;

	private DosImage() {}

	/** The texture of {@code pixels} ({@code width} x {@code height} map colour ids) under {@code key}. */
	public static ResourceLocation texture(String key, byte[] pixels, int width, int height) {
		Entry entry = TEXTURES.get(key);
		if(entry != null && entry.texture().getPixels() != null && entry.texture().getPixels().getWidth() == width
				&& entry.texture().getPixels().getHeight() == height) {
			if(entry.pixels() != pixels) { // A new picture under the same key: paint it in place.
				paint(entry.texture().getPixels(), pixels, width, height);
				entry.texture().upload();
				TEXTURES.put(key, new Entry(entry.id(), entry.texture(), pixels));
			}
			return entry.id();
		}
		if(entry != null)
			release(entry);
		NativeImage image = new NativeImage(width, height, true);
		paint(image, pixels, width, height);
		DynamicTexture texture = new DynamicTexture(image);
		ResourceLocation id = Radiocraft.id("computer_image/" + next++);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		TEXTURES.put(key, new Entry(id, texture, pixels));
		trim();
		return id;
	}

	private static void paint(NativeImage image, byte[] pixels, int width, int height) {
		PhotoPalette palette = MapPalettes.vanilla();
		for(int y = 0; y < height; y++)
			for(int x = 0; x < width; x++) {
				int rgb = palette.rgbOf(pixels[y * width + x]);
				if(rgb < 0)
					rgb = 0;
				// NativeImage wants 0xAABBGGRR.
				image.setPixelRGBA(x, y, 0xFF000000 | (rgb & 0xFF) << 16 | rgb & 0xFF00 | rgb >> 16 & 0xFF);
			}
	}

	private static void trim() {
		for(Iterator<Entry> it = TEXTURES.values().iterator(); TEXTURES.size() > MAX && it.hasNext(); ) {
			Entry oldest = it.next();
			it.remove();
			release(oldest);
		}
	}

	private static void release(Entry entry) {
		Minecraft.getInstance().getTextureManager().release(entry.id());
	}

	/** Frees every texture. */
	public static void releaseAll() {
		for(Entry entry : TEXTURES.values())
			release(entry);
		TEXTURES.clear();
	}
}
