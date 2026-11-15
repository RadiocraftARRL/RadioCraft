package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.common.blockentities.CrtMonitorBlockEntity;
import com.arrl.radiocraft.common.computer.Waterfall;
import com.arrl.radiocraft.common.network.clientbound.CCrtWaterfallPacket;
import com.arrl.radiocraft.common.radio.BandScope;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The waterfalls External CRT Monitors on premium radios show, on the client (2026-11-12), one per monitor, from
 * {@link CCrtWaterfallPacket}. The server sends a row only when it changed, so each row stands until the next: the
 * picture is {@link #ROWS} rows of {@link CrtMonitorBlockEntity#WATERFALL_TICKS} game ticks each, newest at the top,
 * each the last row received at or before its time. Coloured as rig control's waterfall (blue through cyan, green,
 * yellow and red to white), the dial a faint line down it. A {@link BandScope#BINS} x {@link #ROWS} texture, repainted
 * only when a row comes or the picture moves down a row. Render thread.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = Radiocraft.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class CrtMonitorWaterfalls {

	/** Rows down the tube (32 seconds of it). */
	public static final int ROWS = 64;
	/** Monitors kept (the oldest seen go). */
	private static final int MAX = 32;
	/** Nothing heard: the tube's dark; the dial's line. */
	private static final int DARK = 0x0A0D0A, DIAL = 0x2C3A30;
	/** Faint to strong, as rig control's waterfall. */
	private static final int[] RAMP = { DosColours.BLUE, DosColours.LIGHT_BLUE, DosColours.CYAN, DosColours.LIGHT_CYAN, DosColours.LIGHT_GREEN,
			DosColours.YELLOW, DosColours.LIGHT_RED, DosColours.WHITE };

	private record Row(long slot, byte[] levels) {}

	/** One monitor's waterfall. */
	public static final class Fall {
		boolean on;
		int span;
		double lowHz, highHz, dialHz;
		final ArrayDeque<Row> rows = new ArrayDeque<>();
		@Nullable
		DynamicTexture texture;
		@Nullable
		ResourceLocation id;
		long paintedSlot = Long.MIN_VALUE;
		boolean dirty = true;

		public boolean on() {
			return on;
		}

		/** The texture showing the waterfall at game time {@code gameTime}, repainted if it moved on. */
		public ResourceLocation texture(long gameTime) {
			if(texture == null) {
				texture = new DynamicTexture(BandScope.BINS, ROWS, false);
				id = Radiocraft.id("crt_monitor_waterfall/" + next++);
				Minecraft.getInstance().getTextureManager().register(id, texture);
			}
			long slot = Math.floorDiv(gameTime, CrtMonitorBlockEntity.WATERFALL_TICKS);
			if(dirty || slot != paintedSlot) {
				paint(texture.getPixels(), slot);
				texture.upload();
				paintedSlot = slot;
				dirty = false;
			}
			return id;
		}

		private void paint(@Nullable NativeImage image, long slot) {
			if(image == null)
				return;
			int dial = Waterfall.bin(dialHz, lowHz, highHz, BandScope.BINS);
			Row[] oldestFirst = rows.toArray(new Row[0]);
			int from = oldestFirst.length - 1; // Walks back through the rows as the picture goes down.
			for(int y = 0; y < ROWS; y++) {
				long at = slot - y;
				while(from >= 0 && oldestFirst[from].slot() > at)
					from--;
				byte[] levels = from >= 0 ? oldestFirst[from].levels() : null;
				for(int x = 0; x < BandScope.BINS; x++) {
					int level = levels != null && x < levels.length ? levels[x] : 0;
					int rgb = level > 0 ? colour(level) : x == dial ? DIAL : DARK;
					image.setPixelRGBA(x, y, 0xFF000000 | (rgb & 0xFF) << 16 | rgb & 0xFF00 | rgb >> 16 & 0xFF); // RGB to NativeImage's ABGR.
				}
			}
		}

		void release() {
			if(id != null)
				Minecraft.getInstance().getTextureManager().release(id);
			texture = null;
			id = null;
		}
	}

	private static final Map<BlockPos, Fall> FALLS = new LinkedHashMap<>(16, 0.75F, true);
	private static int next = 0;

	private CrtMonitorWaterfalls() {}

	/** Level 1 to {@link Waterfall#MAX_LEVEL} along the ramp, blended between its colours. */
	static int colour(int level) {
		double t = Math.max(0.0D, Math.min(1.0D, (level - 1) / (double)(Waterfall.MAX_LEVEL - 1))) * (RAMP.length - 1);
		int i = Math.min(RAMP.length - 2, (int)t);
		double f = t - i;
		int a = RAMP[i], b = RAMP[i + 1], out = 0;
		for(int shift = 0; shift <= 16; shift += 8) {
			int ca = a >> shift & 0xFF, cb = b >> shift & 0xFF;
			out |= (int)Math.round(ca + (cb - ca) * f) << shift;
		}
		return out;
	}

	/** A row from the server. */
	public static void receive(CCrtWaterfallPacket packet) {
		BlockPos pos = packet.pos().immutable();
		Fall fall = FALLS.get(pos);
		if(fall == null) {
			fall = new Fall();
			FALLS.put(pos, fall);
			for(Iterator<Fall> it = FALLS.values().iterator(); FALLS.size() > MAX && it.hasNext(); ) {
				Fall oldest = it.next();
				it.remove();
				oldest.release();
			}
		}
		boolean moved = packet.lowHz() != fall.lowHz || packet.highHz() != fall.highHz;
		if(packet.fresh() || moved || !packet.on())
			fall.rows.clear(); // A new span (or off): what came before was another picture.
		fall.on = packet.on();
		fall.span = packet.span();
		fall.lowHz = packet.lowHz();
		fall.highHz = packet.highHz();
		fall.dialHz = packet.dialHz();
		if(packet.on()) {
			long slot = Math.floorDiv(packet.gameTime(), CrtMonitorBlockEntity.WATERFALL_TICKS);
			while(!fall.rows.isEmpty() && fall.rows.peekLast().slot() >= slot)
				fall.rows.pollLast();
			fall.rows.addLast(new Row(slot, packet.row()));
			// Rows that have scrolled off, but one: it still fills the bottom until a newer one is that old.
			while(fall.rows.size() > 1) {
				Row second = fall.rows.toArray(new Row[0])[1];
				if(second.slot() > slot - ROWS)
					break;
				fall.rows.pollFirst();
			}
		}
		fall.dirty = true;
	}

	/** The waterfall of the monitor at {@code pos}, or null. */
	@Nullable
	public static Fall get(BlockPos pos) {
		return FALLS.get(pos);
	}

	@net.neoforged.bus.api.SubscribeEvent
	public static void loggedOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
		clear();
	}

	/** Leaving the world: everything goes. */
	public static void clear() {
		for(Fall fall : FALLS.values())
			fall.release();
		FALLS.clear();
	}
}
