package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.Radiocraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The radio computer's icons (the Program Manager's, {@link TextGrid#icon}) in the computer's colour scheme: each icon
 * PNG (16 EGA colours, {@code tools/computer_icons.py}) is read once, every pixel put through
 * {@link DosScheme#display}, and kept as a texture per icon and scheme. Transparent pixels stay transparent. Render
 * thread; {@link #releaseAll()} frees them (with the computer's other textures).
 */
public final class DosIcons {

	/** A recoloured icon's texture and size. */
	public record Sprite(ResourceLocation id, int width, int height) {}

	private static final Map<String, Sprite> SPRITES = new HashMap<>();
	/** Icons whose PNG could not be read: not tried again (and not logged again). */
	private static final Set<String> MISSING = new HashSet<>();
	private static int next = 0;

	private DosIcons() {}

	/** The program's icon path (under {@code textures/}) for program id {@code id}. */
	public static String forProgram(String id) {
		return "gui/computer/icons/" + id + ".png";
	}

	/** {@code texture} (a path under {@code assets/radiocraft/textures/}) in {@code scheme}, or null if it cannot be read. */
	@Nullable
	public static Sprite sprite(String texture, DosScheme scheme) {
		String key = scheme.id() + "|" + texture;
		Sprite sprite = SPRITES.get(key);
		if(sprite != null || MISSING.contains(texture))
			return sprite;
		NativeImage image = read(texture);
		if(image == null) {
			MISSING.add(texture);
			return null;
		}
		for(int y = 0; y < image.getHeight(); y++)
			for(int x = 0; x < image.getWidth(); x++) {
				int abgr = image.getPixelRGBA(x, y);
				int alpha = abgr >>> 24;
				if(alpha == 0)
					continue;
				int rgb = (abgr & 0xFF) << 16 | abgr & 0xFF00 | abgr >> 16 & 0xFF;
				int shown = scheme.display(rgb);
				image.setPixelRGBA(x, y, alpha << 24 | (shown & 0xFF) << 16 | shown & 0xFF00 | shown >> 16 & 0xFF);
			}
		ResourceLocation id = Radiocraft.id("computer_icon/" + next++);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
		sprite = new Sprite(id, image.getWidth(), image.getHeight());
		SPRITES.put(key, sprite);
		return sprite;
	}

	@Nullable
	private static NativeImage read(String texture) {
		ResourceLocation location = Radiocraft.id("textures/" + texture);
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
		if(resource.isEmpty()) {
			Radiocraft.LOGGER.warn("No computer icon {}", location);
			return null;
		}
		try(InputStream in = resource.get().open()) {
			return NativeImage.read(in);
		}
		catch(IOException e) {
			Radiocraft.LOGGER.warn("Could not read computer icon {}: {}", location, e.toString());
			return null;
		}
	}

	/** Frees every icon texture (they are made again when next drawn). */
	public static void releaseAll() {
		for(Sprite sprite : SPRITES.values())
			Minecraft.getInstance().getTextureManager().release(sprite.id());
		SPRITES.clear();
		MISSING.clear();
	}
}
