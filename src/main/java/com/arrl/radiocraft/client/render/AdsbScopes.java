package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.radio.adsb.AdsbPicture;
import com.arrl.radiocraft.common.radio.adsb.AdsbScope;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The scopes drawn on ADS-B receivers' tubes in the world (1.12 playtest), client side: the last picture each receiver
 * sent ({@code CAdsbPacket}, a compact one to players near it) and a small texture per receiver, painted by
 * {@link AdsbScope} (rings, trails, targets, tags) into colour indices and lit in green phosphor when a picture
 * arrives. No sweep: ADS-B is not a radar, the reports come in all the time. Textures are made when a receiver is first drawn and
 * freed when it has not been drawn for a while or the level changes.
 */
public final class AdsbScopes {

	/** The texture's size, pixels (square). */
	public static final int SIZE = 96;
	/** A picture older than this is stale (the receiver stopped sending: off, or the player walked away), ms. */
	private static final long STALE_MS = 5000L;
	/** A texture not drawn for this long is freed, ms. */
	private static final long FORGET_MS = 30_000L;

	// Colour indices handed to AdsbScope as its "palette".
	private static final byte OUTSIDE = 0, FACE = 1, RING = 2, TRAIL = 3, TARGET = 4, EMERGENCY = 5, TAG = 6, SELECTED = 7;
	private static final AdsbScope.Palette INDICES = new AdsbScope.Palette(OUTSIDE, FACE, RING, TRAIL, TARGET, EMERGENCY, TAG, SELECTED);

	private static final Map<BlockPos, Scope> SCOPES = new HashMap<>();
	@Nullable
	private static ClientLevel level;
	private static int names = 0;

	private AdsbScopes() {}

	/** A receiver's picture arrived (client thread). */
	public static void update(BlockPos pos, CompoundTag picture) {
		checkLevel();
		Scope scope = SCOPES.computeIfAbsent(pos.immutable(), p -> new Scope());
		scope.picture = picture;
		scope.receivedAt = Util.getMillis();
		scope.dirty = true;
	}

	/**
	 * The texture to draw on the receiver at {@code pos} now, repainted if a new picture came; null if it has sent
	 * nothing lately (draw nothing: the model's own tube shows).
	 */
	@Nullable
	public static ResourceLocation texture(BlockPos pos) {
		checkLevel();
		Scope scope = SCOPES.get(pos);
		long now = Util.getMillis();
		forgetOld(now);
		if(scope == null || scope.picture == null || now - scope.receivedAt > STALE_MS)
			return null;
		scope.drawnAt = now;
		if(scope.texture == null) {
			scope.texture = new DynamicTexture(SIZE, SIZE, false);
			scope.id = Radiocraft.id("adsb_scope/" + names++);
			Minecraft.getInstance().getTextureManager().register(scope.id, scope.texture);
		}
		if(scope.dirty) { // New data: the rings, trails, targets and tags again.
			List<AdsbPicture.Contact> contacts = AdsbPicture.contacts(scope.picture);
			AdsbScope.paint(scope.indices, SIZE, contacts, Math.max(1, scope.picture.getInt("range")), -1, true, INDICES, true); // Square, edge to edge.
			scope.dirty = false;
			light(scope);
		}
		return scope.id;
	}

	/** Lights the colour indices in green phosphor. */
	private static void light(Scope scope) {
		NativeImage image = scope.texture.getPixels();
		if(image == null)
			return;
		for(int y = 0; y < SIZE; y++)
			for(int x = 0; x < SIZE; x++) {
				int i = y * SIZE + x;
				byte index = scope.indices[i];
				int rgb;
				if(index == OUTSIDE)
					rgb = 0x050805;
				else
					rgb = switch(index) {
						case RING -> mix(0x0B2A12, 0x2E9A44, 0.35F);
						case TRAIL -> mix(0x0B2A12, 0x2E9A44, 0.6F);
						case TARGET -> 0x9CFFAE;
						case EMERGENCY -> 0xFF6A5A;
						case TAG -> 0x7CE88C;
						default -> 0x041206; // The face.
					};
				image.setPixelRGBA(x, y, 0xFF000000 | abgr(rgb));
			}
		scope.texture.upload();
	}

	/** {@code a} to {@code b} by {@code t} (0-1), RGB. */
	private static int mix(int a, int b, float t) {
		float k = Math.max(0.0F, Math.min(1.0F, t));
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
		return r << 16 | g << 8 | bl;
	}

	/** NativeImage keeps ABGR. */
	private static int abgr(int rgb) {
		return (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16) & 0xFF;
	}

	/** A new level (dimension, server): every scope forgotten. */
	private static void checkLevel() {
		ClientLevel now = Minecraft.getInstance().level;
		if(now != level) {
			for(Scope scope : SCOPES.values())
				scope.release();
			SCOPES.clear();
			level = now;
		}
	}

	private static void forgetOld(long now) {
		for(Iterator<Scope> it = SCOPES.values().iterator(); it.hasNext(); ) {
			Scope scope = it.next();
			long last = Math.max(scope.drawnAt, scope.receivedAt);
			if(now - last > FORGET_MS) {
				scope.release();
				it.remove();
			}
		}
	}

	/** One receiver's last picture and its texture. */
	private static final class Scope {
		@Nullable
		CompoundTag picture;
		long receivedAt, drawnAt;
		boolean dirty;
		final byte[] indices = new byte[SIZE * SIZE];
		@Nullable
		DynamicTexture texture;
		@Nullable
		ResourceLocation id;

		void release() {
			if(id != null)
				Minecraft.getInstance().getTextureManager().release(id);
			id = null;
			texture = null;
		}
	}
}
