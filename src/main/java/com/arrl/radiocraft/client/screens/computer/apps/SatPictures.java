package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.PixelCanvas;
import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.photo.PhotoPalette;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SATTRACK's pictures as the computer screen shows them (2026-11-13): full colour drawings matched to map colours
 * (with a little ordered dither, so shading stays smooth). The Image view's tumbling frames are ray traced on a
 * background thread, one turn ({@link SatelliteModels#FRAMES} frames) per satellite, and kept for the last few
 * satellites looked at.
 */
final class SatPictures {

	/** One turn takes this long, ms. */
	static final long TURN_MS = 24_000L;
	private static final int KEEP = 4;
	private static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "RadioCraft SATTRACK pictures");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	/** Map colour id by 15-bit colour, -1 until looked up. */
	private static final short[] NEAREST = new short[1 << 15];

	static {
		java.util.Arrays.fill(NEAREST, (short)-1);
	}

	private static final Map<String, byte[][]> TURNS = new LinkedHashMap<>(8, 0.75F, true);
	/** Where the light is in each frame of a turn ({@link SatelliteModels#lightAt}), null where hidden. */
	private static final Map<String, int[][]> LIGHTS = new java.util.HashMap<>();

	private SatPictures() {}

	/** One colour as its nearest map colour id. */
	static byte mapId(int rgb) {
		PhotoPalette palette = MapPalettes.vanilla();
		return (byte)palette.id(palette.nearest((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));
	}

	/** {@code c} as map colour ids. */
	static byte[] toMapIds(PixelCanvas c) {
		PhotoPalette palette = MapPalettes.vanilla();
		byte[] out = new byte[c.width * c.height];
		int[] px = c.pixels();
		for(int y = 0; y < c.height; y++)
			for(int x = 0; x < c.width; x++) {
				int rgb = px[y * c.width + x];
				int d = BAYER[y & 3][x & 3] - 8; // -8 to 7
				int r = clamp(((rgb >> 16) & 0xFF) + d), g = clamp(((rgb >> 8) & 0xFF) + d), b = clamp((rgb & 0xFF) + d);
				int key = (r >> 3) << 10 | (g >> 3) << 5 | b >> 3;
				short id = NEAREST[key];
				if(id < 0) {
					synchronized(NEAREST) {
						id = (short)(palette.id(palette.nearest((r & 0xF8) | 4, (g & 0xF8) | 4, (b & 0xF8) | 4)) & 0xFF);
						NEAREST[key] = id;
					}
				}
				out[y * c.width + x] = (byte)id;
			}
		return out;
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	/**
	 * The frame of {@code kind} to show now for the satellite {@code name}, or null while the first is being made. Frames
	 * not made yet are asked for and the nearest made one shown meanwhile.
	 */
	static byte[] frame(String name, int kind, int width, int height, long now) {
		String key = name + "/" + kind + "/" + width + "x" + height;
		byte[][] turn;
		synchronized(TURNS) {
			turn = TURNS.get(key);
			if(turn == null) {
				byte[][] made = new byte[SatelliteModels.FRAMES][];
				int[][] lights = new int[SatelliteModels.FRAMES][];
				TURNS.put(key, made);
				LIGHTS.put(key, lights);
				while(TURNS.size() > KEEP) {
					String oldest = TURNS.keySet().iterator().next();
					TURNS.remove(oldest);
					LIGHTS.remove(oldest);
				}
				int frames = kind < 0 ? 1 : SatelliteModels.FRAMES; // The Moon keeps still.
				long seed = name.hashCode();
				WORKER.submit(() -> {
					var shapes = SatelliteModels.model(kind);
					for(int f = 0; f < frames; f++) {
						synchronized(TURNS) {
							if(TURNS.get(key) != made)
								return; // Dropped: no longer wanted.
						}
						PixelCanvas c = new PixelCanvas(width, height);
						c.starfield(seed);
						SatelliteModels.render(c, shapes, kind, f);
						lights[f] = SatelliteModels.lightAt(shapes, kind, f, width, height);
						made[f] = toMapIds(c);
					}
				});
				turn = made;
			}
		}
		if(kind < 0)
			return turn[0];
		// Still being made: hold the first frame. Showing the newest one made spun it round as fast as the frames came
		// in, for a moment, every time the view first opened (2026-11-14).
		return turn[shown(turn, now)];
	}

	/** Which frame of {@code turn} shows at {@code now}: the first until all are made, then one a turn's share of time. */
	private static int shown(byte[][] turn, long now) {
		if(turn[SatelliteModels.FRAMES - 1] == null)
			return 0;
		return (int)(now % TURN_MS * SatelliteModels.FRAMES / TURN_MS);
	}

	/** Where the satellite's light is in the frame {@link #frame} gives at {@code now}, {x, y}, or null (hidden, or none yet). */
	static int[] light(String name, int kind, int width, int height, long now) {
		String key = name + "/" + kind + "/" + width + "x" + height;
		synchronized(TURNS) {
			byte[][] turn = TURNS.get(key);
			int[][] lights = LIGHTS.get(key);
			if(turn == null || lights == null || kind < 0)
				return null;
			int f = shown(turn, now);
			return turn[f] == null ? null : lights[f];
		}
	}
}
