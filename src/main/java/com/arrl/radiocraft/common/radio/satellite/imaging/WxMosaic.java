package com.arrl.radiocraft.common.radio.satellite.imaging;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A ground station's weather satellite pictures stitched onto the map (2026-11-12): the WXSAT program's mosaic. The
 * world is cut into tiles {@link #TILE} pixels square, {@link #TILE_BLOCKS} blocks, anchored at 0, 0; each pass the
 * station receives is a layer of tiles ({@link Pass}), and a picture of any place is the layers laid oldest first, so
 * the newest data is on top and where a pass left a gap (lost lines, the edge of its swath) an older one shows through.
 * Pixel colour 0 is "no data".
 * <p>
 * Only tiles within {@link #RADIUS_TILES} of the station are kept (a regional map, {@code 7 x 7} tiles), and old
 * layers age out: at most {@link #MAX_PASSES} passes, {@link #MAX_TILES} tiles in all, none older than
 * {@link #MAX_AGE_TICKS}. Pure apart from the NBT it saves to; unit tested.
 */
public final class WxMosaic {

	/** Pixels across a tile. */
	public static final int TILE = 128;
	/** Blocks across a tile. */
	public static final int TILE_BLOCKS = TILE * AptSwath.BLOCKS_PER_PIXEL;
	/** Tiles kept each way from the station's tile. */
	public static final int RADIUS_TILES = 3;
	/** Passes kept (the loop's frames). */
	public static final int MAX_PASSES = 8;
	/** Tiles kept over every pass. */
	public static final int MAX_TILES = 160; // A pass over the wider swath (2026-11-14) lays down up to about 30 near the station.
	/** How long a pass is kept, game ticks (five Minecraft days). */
	public static final long MAX_AGE_TICKS = 5L * 24_000L;

	/** One pass received: a layer of tiles, by {@link #key}. */
	public static final class Pass {
		private final String satellite;
		private final long started;
		private long last;
		private int lines;
		/** The geostationary frame it holds, or -1 for an orbiting pass (2026-11-15, to join a frame shared over the LAN). */
		private int frame = -1;
		private final Map<Long, byte[]> tiles = new HashMap<>();

		Pass(String satellite, long started) {
			this.satellite = satellite;
			this.started = started;
			this.last = started;
		}

		public String satellite() {
			return satellite;
		}

		/** The game time its first line came in. */
		public long started() {
			return started;
		}

		/** The game time its last line came in. */
		public long last() {
			return last;
		}

		/** Lines it got (counting ones only partly in the kept area). */
		public int lines() {
			return lines;
		}

		public int tileCount() {
			return tiles.size();
		}

		public int frame() {
			return frame;
		}
	}

	private final List<Pass> passes = new ArrayList<>();
	private int stationX, stationZ;
	private long version = 0L;
	// Whole tiles composited (per frame), kept until anything changes.
	private final Map<Long, byte[]> cache = new HashMap<>();
	private long cacheVersion = -1L;

	public WxMosaic(int stationX, int stationZ) {
		this.stationX = stationX;
		this.stationZ = stationZ;
	}

	/** A tile's key: its column and row (block / {@link #TILE_BLOCKS}, rounded down). */
	public static long key(int tx, int tz) {
		return (long)tx << 32 | (tz & 0xFFFFFFFFL);
	}

	public static int tileX(long key) {
		return (int)(key >> 32);
	}

	public static int tileZ(long key) {
		return (int)key;
	}

	/** The tile column (or row) a block coordinate is in. */
	public static int tileOf(int block) {
		return Math.floorDiv(block, TILE_BLOCKS);
	}

	/** Where the station is (the computer): what "near" means for the tiles kept. A computer moved keeps its pictures. */
	public void station(int x, int z) {
		if(x != stationX || z != stationZ) {
			stationX = x;
			stationZ = z;
			version++;
		}
	}

	public int stationX() {
		return stationX;
	}

	public int stationZ() {
		return stationZ;
	}

	/** Goes up by one at every change: the program and the displays redraw when it moves. */
	public long version() {
		return version;
	}

	/** True if tile {@code tx, tz} is near enough the station to be kept. */
	public boolean near(int tx, int tz) {
		return Math.abs(tx - tileOf(stationX)) <= RADIUS_TILES && Math.abs(tz - tileOf(stationZ)) <= RADIUS_TILES;
	}

	/** The passes kept, oldest first. */
	public List<Pass> passes() {
		return Collections.unmodifiableList(passes);
	}

	public int passCount() {
		return passes.size();
	}

	/** The pass lines go into now, or null before the first. */
	@Nullable
	public Pass current() {
		return passes.isEmpty() ? null : passes.getLast();
	}

	/** Tiles kept over every pass. */
	public int tileCount() {
		int n = 0;
		for(Pass pass : passes)
			n += pass.tiles.size();
		return n;
	}

	/** Starts a new layer for a pass of {@code satellite} at game time {@code time}, ageing out old ones first. */
	public Pass begin(String satellite, long time) {
		age(time);
		while(passes.size() >= MAX_PASSES)
			foldOldest();
		Pass pass = new Pass(satellite, time);
		passes.add(pass);
		version++;
		return pass;
	}

	/**
	 * The layer for a pass of {@code satellite} (geostationary {@code frame}, -1 for an orbiting pass) at {@code time}:
	 * the newest layer of the same satellite and frame if its last line came within {@code gap} ticks, else a new one
	 * (2026-11-15). Two computers on a LAN hearing the same pass both put their lines into one layer this way, however
	 * the lines arrive, instead of each starting its own.
	 */
	public Pass join(String satellite, int frame, long time, long gap) {
		for(int i = passes.size() - 1; i >= 0; i--) {
			Pass pass = passes.get(i);
			if(pass.satellite.equals(satellite) && pass.frame == frame && time - pass.last <= gap)
				return pass;
		}
		Pass pass = begin(satellite, time);
		pass.frame = frame;
		return pass;
	}

	/**
	 * Lets the oldest layer go without losing what only it saw (2026-11-16, the maintainer: as a new pass came in and
	 * pushed an old one out, ground went black and layers vanished): its pixels fill the gaps in the next layer up, which
	 * becomes the oldest. With one layer left it simply goes.
	 */
	private void foldOldest() {
		Pass oldest = passes.removeFirst();
		version++;
		if(passes.isEmpty())
			return;
		Pass next = passes.getFirst();
		for(Map.Entry<Long, byte[]> e : oldest.tiles.entrySet()) {
			byte[] above = next.tiles.get(e.getKey());
			if(above == null) {
				next.tiles.put(e.getKey(), e.getValue());
				continue;
			}
			byte[] below = e.getValue();
			for(int i = 0; i < above.length && i < below.length; i++)
				if(above[i] == 0)
					above[i] = below[i];
		}
	}

	/** Drops passes whose last line is older than {@link #MAX_AGE_TICKS} at {@code now}. */
	public void age(long now) {
		if(passes.removeIf(pass -> now - pass.last > MAX_AGE_TICKS))
			version++;
	}

	/**
	 * Stitches a scan line into the current pass (one is begun for {@code satellite} if there is none): each pixel is
	 * stamped at four points a quarter pixel either side of its centre, across and along the track, so a line laid at
	 * any angle leaves no holes on the grid. Pixels that are 0, or fall outside the kept area, are skipped. Then the
	 * oldest passes go while there are more than {@link #MAX_TILES} tiles.
	 */
	public void line(String satellite, byte[] pixels, double cx, double cz, double rx, double rz, long time) {
		Pass pass = current();
		if(pass == null)
			pass = begin(satellite, time);
		line(pass, pixels, cx, cz, rx, rz, time);
	}

	/** Stitches a scan line into {@code pass} (one of {@link #passes()}, as {@link #join} gives), as {@link #line}. */
	public void line(Pass pass, byte[] pixels, double cx, double cz, double rx, double rz, long time) {
		if(!passes.contains(pass))
			return; // Aged or pushed out meanwhile.
		double[] along = AptSwath.along(rx, rz);
		double quarter = AptSwath.BLOCKS_PER_PIXEL / 4.0D;
		int count = Math.min(pixels.length, AptSwath.PIXELS);
		for(int i = 0; i < count; i++) {
			byte colour = pixels[i];
			if(colour == 0)
				continue;
			double across = AptSwath.offset(i);
			for(int sa = -1; sa <= 1; sa += 2)
				for(int sb = -1; sb <= 1; sb += 2) {
					double a = across + sa * quarter, b = sb * quarter;
					stamp(pass, cx + a * rx + b * along[0], cz + a * rz + b * along[1], colour);
				}
		}
		pass.lines++;
		pass.last = Math.max(pass.last, time);
		while(tileCount() > MAX_TILES && passes.size() > 1)
			foldOldest();
		version++;
	}

	private void stamp(Pass pass, double x, double z, byte colour) {
		int gx = Math.floorDiv((int)Math.floor(x), AptSwath.BLOCKS_PER_PIXEL), gz = Math.floorDiv((int)Math.floor(z), AptSwath.BLOCKS_PER_PIXEL);
		int tx = Math.floorDiv(gx, TILE), tz = Math.floorDiv(gz, TILE);
		if(!near(tx, tz))
			return;
		byte[] tile = pass.tiles.computeIfAbsent(key(tx, tz), k -> new byte[TILE * TILE]);
		tile[Math.floorMod(gx, TILE) + Math.floorMod(gz, TILE) * TILE] = colour;
	}

	/** The frame index for "the latest": every pass. */
	public int latest() {
		return passes.size() - 1;
	}

	/**
	 * Tile {@code tx, tz} with passes 0 to {@code upto} laid oldest first (newest on top); {@code upto} below 0 or past
	 * the last means every pass. A new array the caller may keep; 0 where no pass saw.
	 */
	public byte[] tile(int tx, int tz, int upto) {
		return cachedTile(tx, tz, upto).clone();
	}

	private byte[] cachedTile(int tx, int tz, int upto) {
		int last = upto < 0 || upto >= passes.size() ? passes.size() - 1 : upto;
		if(cacheVersion != version) {
			cache.clear();
			cacheVersion = version;
		}
		long cacheKey = key(tx, tz) * 31L + last;
		byte[] out = cache.get(cacheKey);
		if(out != null)
			return out;
		out = new byte[TILE * TILE];
		long k = key(tx, tz);
		for(int p = 0; p <= last; p++) {
			byte[] layer = passes.get(p).tiles.get(k);
			if(layer == null)
				continue;
			for(int i = 0; i < layer.length; i++)
				if(layer[i] != 0)
					out[i] = layer[i];
		}
		if(cache.size() > 512)
			cache.clear();
		cache.put(cacheKey, out);
		return out;
	}

	/** The colour at mosaic pixel {@code gx, gz} (block / {@link AptSwath#BLOCKS_PER_PIXEL}) with passes to {@code upto}. */
	public byte pixel(int gx, int gz, int upto) {
		return cachedTile(Math.floorDiv(gx, TILE), Math.floorDiv(gz, TILE), upto)[Math.floorMod(gx, TILE) + Math.floorMod(gz, TILE) * TILE];
	}

	/**
	 * A picture {@code width} by {@code height} (north up) centred on block {@code centreX, centreZ}, each pixel
	 * {@code blocksPerPixel} blocks (the mosaic's 8, or more zoomed out: the pixel's centre is sampled), with passes 0
	 * to {@code upto} (below 0: all). Row by row, x fastest; 0 where there is no data.
	 */
	public byte[] view(int centreX, int centreZ, int width, int height, int blocksPerPixel, int upto) {
		byte[] out = new byte[width * height];
		if(passes.isEmpty())
			return out;
		int bpp = Math.max(1, blocksPerPixel);
		if(bpp == AptSwath.BLOCKS_PER_PIXEL) { // The mosaic's own scale: whole runs of tile rows.
			int gx0 = Math.floorDiv((int)Math.floor(centreX + (0.5D - width / 2.0D) * bpp), AptSwath.BLOCKS_PER_PIXEL);
			for(int v = 0; v < height; v++) {
				int gz = Math.floorDiv((int)Math.floor(centreZ + (v - height / 2.0D + 0.5D) * bpp), AptSwath.BLOCKS_PER_PIXEL);
				int tz = Math.floorDiv(gz, TILE), row = Math.floorMod(gz, TILE) * TILE;
				for(int u = 0; u < width; ) {
					int gx = gx0 + u, tx = Math.floorDiv(gx, TILE), col = Math.floorMod(gx, TILE);
					int run = Math.min(TILE - col, width - u);
					System.arraycopy(cachedTile(tx, tz, upto), row + col, out, u + v * width, run);
					u += run;
				}
			}
			return out;
		}
		for(int v = 0; v < height; v++) {
			int z = (int)Math.floor(centreZ + (v - height / 2.0D + 0.5D) * bpp);
			int gz = Math.floorDiv(z, AptSwath.BLOCKS_PER_PIXEL);
			for(int u = 0; u < width; u++) {
				int x = (int)Math.floor(centreX + (u - width / 2.0D + 0.5D) * bpp);
				out[u + v * width] = pixel(Math.floorDiv(x, AptSwath.BLOCKS_PER_PIXEL), gz, upto);
			}
		}
		return out;
	}

	// -------------------- SAVING --------------------

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("sx", stationX);
		tag.putInt("sz", stationZ);
		ListTag list = new ListTag();
		for(Pass pass : passes) {
			CompoundTag p = new CompoundTag();
			p.putString("sat", pass.satellite);
			p.putLong("started", pass.started);
			p.putLong("last", pass.last);
			p.putInt("lines", pass.lines);
			p.putInt("frame", pass.frame);
			ListTag tiles = new ListTag();
			for(Map.Entry<Long, byte[]> e : pass.tiles.entrySet()) {
				CompoundTag t = new CompoundTag();
				t.putLong("k", e.getKey());
				t.putByteArray("p", e.getValue());
				tiles.add(t);
			}
			p.put("tiles", tiles);
			list.add(p);
		}
		tag.put("passes", list);
		return tag;
	}

	public static WxMosaic load(CompoundTag tag) {
		WxMosaic mosaic = new WxMosaic(tag.getInt("sx"), tag.getInt("sz"));
		ListTag list = tag.getList("passes", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size() && mosaic.passes.size() < MAX_PASSES; i++) {
			CompoundTag p = list.getCompound(i);
			Pass pass = new Pass(p.getString("sat"), p.getLong("started"));
			pass.last = p.getLong("last");
			pass.lines = p.getInt("lines");
			pass.frame = p.contains("frame") ? p.getInt("frame") : -1;
			ListTag tiles = p.getList("tiles", Tag.TAG_COMPOUND);
			for(int j = 0; j < tiles.size(); j++) {
				CompoundTag t = tiles.getCompound(j);
				byte[] pixels = t.getByteArray("p");
				if(pixels.length == TILE * TILE)
					pass.tiles.put(t.getLong("k"), pixels);
			}
			mosaic.passes.add(pass);
		}
		while(mosaic.tileCount() > MAX_TILES && mosaic.passes.size() > 1)
			mosaic.foldOldest();
		return mosaic;
	}

	/** Every tile key any pass has (for tests and the program's summary). */
	public List<Long> keys() {
		java.util.Set<Long> out = new java.util.LinkedHashSet<>();
		for(Pass pass : passes)
			out.addAll(pass.tiles.keySet());
		return new ArrayList<>(out);
	}
}
