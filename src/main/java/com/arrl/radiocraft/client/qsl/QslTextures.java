package com.arrl.radiocraft.client.qsl;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.photo.PhotoPalette;
import com.arrl.radiocraft.common.qsl.QslArt;
import com.arrl.radiocraft.common.qsl.QslCard;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * QSL card textures (computer batch 3C): each side of each card drawn once from its data ({@link QslArt}) into a
 * {@link DynamicTexture}, keyed by the card itself (its record equality: identical cards share one), the most recently
 * used {@link #MAX} kept. Render thread only.
 */
public final class QslTextures {

	private static final int MAX = 48;

	private record Key(QslCard card, boolean back) {}

	private static final Map<Key, ResourceLocation> TEXTURES = new LinkedHashMap<>(16, 0.75F, true);
	private static int next = 0;

	private QslTextures() {}

	/** The texture of {@code card}'s front or back ({@link QslArt#WIDTH} x {@link QslArt#HEIGHT}). */
	public static ResourceLocation get(QslCard card, boolean back) {
		Key key = new Key(card, back);
		ResourceLocation id = TEXTURES.get(key);
		if(id != null)
			return id;
		NativeImage image = new NativeImage(QslArt.WIDTH, QslArt.HEIGHT, true);
		paint(image, back ? QslArt.back(card) : QslArt.front(card));
		id = Radiocraft.id("qsl_card/" + next++);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
		TEXTURES.put(key, id);
		for(Iterator<ResourceLocation> it = TEXTURES.values().iterator(); TEXTURES.size() > MAX && it.hasNext(); ) {
			ResourceLocation oldest = it.next();
			it.remove();
			Minecraft.getInstance().getTextureManager().release(oldest);
		}
		return id;
	}

	/** Paints a card side ({@link QslArt#WIDTH} x {@link QslArt#HEIGHT} map colour ids) into {@code image}. */
	public static void paint(NativeImage image, byte[] pixels) {
		PhotoPalette palette = MapPalettes.vanilla();
		for(int y = 0; y < QslArt.HEIGHT; y++)
			for(int x = 0; x < QslArt.WIDTH; x++) {
				int rgb = Math.max(0, palette.rgbOf(pixels[y * QslArt.WIDTH + x]));
				image.setPixelRGBA(x, y, 0xFF000000 | (rgb & 0xFF) << 16 | rgb & 0xFF00 | rgb >> 16 & 0xFF); // ABGR.
			}
	}
}
