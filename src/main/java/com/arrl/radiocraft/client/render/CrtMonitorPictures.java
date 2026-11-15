package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.CrtMonitorBlockEntity;
import com.arrl.radiocraft.common.network.clientbound.CCrtMonitorPacket;
import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.photo.PhotoPalette;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The pictures External CRT Monitor screens show, on the client (2026-11-12): one per screen, by its master display, from
 * {@link CCrtMonitorPacket}. Each is a texture of the screen's whole front in true colour (the map colours' own RGB,
 * "no data" as the dark tube), sampled with linear filtering by {@link CrtMonitorRenderer} so it scales smoothly; it
 * is uploaded only when the picture (or, in LOOP, the frame shown) changes. Render thread.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = Radiocraft.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class CrtMonitorPictures {

	/** Screens kept (the oldest seen go). */
	private static final int MAX = 32;
	/** "No data": the tube's own dark green-black, ABGR. */
	private static final int DARK = 0xFF0A0D0A;
	/** The words on an empty screen: phosphor green, ABGR. */
	private static final int WORDS = 0xFF60E070;
	/** How long each LOOP frame shows, ms. */
	public static final long LOOP_FRAME_MS = 1000L;

	/** One screen's picture. */
	public static final class Picture {
		public final boolean on;
		public final CrtMonitorBlockEntity.Mode mode;
		public final int width, height;
		final List<byte[]> frames;
		@Nullable
		DynamicTexture texture;
		@Nullable
		ResourceLocation id;
		int uploaded = -1;

		Picture(boolean on, CrtMonitorBlockEntity.Mode mode, int width, int height, List<byte[]> frames) {
			this.on = on;
			this.mode = mode;
			this.width = width;
			this.height = height;
			this.frames = frames;
		}

		public boolean hasPicture() {
			return on && !frames.isEmpty();
		}

		/** The frame showing at {@code millis}: in LOOP each in turn, oldest to newest. */
		public int frameAt(long millis) {
			return frames.size() <= 1 ? 0 : (int)((millis / LOOP_FRAME_MS) % frames.size());
		}

		public int frameCount() {
			return frames.size();
		}

		/** The texture showing frame {@code frame}, uploaded if it is not the one up now; null if there is none. */
		@Nullable
		public ResourceLocation texture(int frame) {
			if(frames.isEmpty())
				return null;
			int f = Math.max(0, Math.min(frames.size() - 1, frame));
			if(texture == null) {
				texture = new DynamicTexture(width, height, false);
				id = Radiocraft.id("external_crt_monitor/" + next++);
				Minecraft.getInstance().getTextureManager().register(id, texture);
			}
			if(uploaded != f) {
				paint(texture.getPixels(), frames.get(f), width, height);
				texture.upload();
				uploaded = f;
			}
			return id;
		}

		void release() {
			if(id != null)
				Minecraft.getInstance().getTextureManager().release(id);
			texture = null;
			id = null;
		}
	}

	private static final Map<BlockPos, Picture> PICTURES = new LinkedHashMap<>(16, 0.75F, true);
	private static int next = 0;

	private CrtMonitorPictures() {}

	/** A picture from the server. */
	public static void receive(CCrtMonitorPacket packet) {
		List<byte[]> frames = new ArrayList<>();
		int size = packet.width() * packet.height();
		if(size > 0 && size <= 4096 * 4096)
			for(byte[] packed : packet.frames()) {
				byte[] frame = CCrtMonitorPacket.inflate(packed, size);
				if(frame != null)
					frames.add(frame);
			}
		CrtMonitorBlockEntity.Mode mode = CrtMonitorBlockEntity.Mode.values()[Math.max(0, Math.min(CrtMonitorBlockEntity.Mode.values().length - 1, packet.mode()))];
		Picture fresh = new Picture(packet.on(), mode, packet.width(), packet.height(), frames);
		Picture old = PICTURES.put(packet.master().immutable(), fresh);
		if(old != null && old.texture != null && old.width == fresh.width && old.height == fresh.height) {
			// The same screen again (a pass adds a line every couple of seconds): repaint its texture in place instead of
			// a new texture and a new name each time, whose render types would be kept for good (2026-11-15).
			fresh.texture = old.texture;
			fresh.id = old.id;
		}
		else if(old != null)
			old.release();
		for(Iterator<Picture> it = PICTURES.values().iterator(); PICTURES.size() > MAX && it.hasNext(); ) {
			Picture oldest = it.next();
			it.remove();
			oldest.release();
		}
	}

	/** The picture of the screen whose master is {@code master}, or null. */
	@Nullable
	public static Picture get(BlockPos master) {
		return PICTURES.get(master);
	}

	@net.neoforged.bus.api.SubscribeEvent
	public static void loggedOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
		clear();
	}

	/** Leaving the world: everything goes. */
	public static void clear() {
		for(Picture picture : PICTURES.values())
			picture.release();
		PICTURES.clear();
	}

	private static void paint(@Nullable NativeImage image, byte[] pixels, int width, int height) {
		if(image == null)
			return;
		PhotoPalette palette = MapPalettes.vanilla();
		boolean empty = true;
		for(int y = 0; y < height; y++)
			for(int x = 0; x < width; x++) {
				byte id = pixels[y * width + x];
				if(id != 0)
					empty = false;
				int rgb = id == 0 ? -1 : palette.rgbOf(id);
				image.setPixelRGBA(x, y, rgb < 0 ? DARK : 0xFF000000 | (rgb & 0xFF) << 16 | rgb & 0xFF00 | rgb >> 16 & 0xFF);
			}
		if(empty) { // Nothing received here yet (2026-11-15): say so on the tube rather than show it blank.
			boolean[] words = CrtText.mask(width, height, "WAITING FOR", "SATELLITE PASS");
			for(int i = 0; i < words.length; i++)
				if(words[i])
					image.setPixelRGBA(i % width, i / width, WORDS);
		}
	}
}
