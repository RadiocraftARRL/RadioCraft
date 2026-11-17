package com.arrl.radiocraft.common.radio.satellite.imaging;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stitching passes into the world-anchored tile grid, newest on top, and ageing them out (2026-11-12). Distances follow
 * {@link AptSwath} (the swath was widened 2026-11-14).
 */
class WxMosaicTest {

	private static final int B = AptSwath.BLOCKS_PER_PIXEL, T = WxMosaic.TILE_BLOCKS;
	private static final double HALF = AptSwath.SWATH_BLOCKS / 2.0D;

	private static byte[] line(byte colour) {
		byte[] pixels = new byte[AptSwath.PIXELS];
		Arrays.fill(pixels, colour);
		return pixels;
	}

	/** A pass flying {@code dx, dz} (a unit vector) through {@code x, z}, {@code lines} lines of {@code colour}. */
	private static void pass(WxMosaic mosaic, String sat, long time, double x, double z, double dx, double dz, int lines, byte colour) {
		mosaic.begin(sat, time);
		double[] right = AptSwath.right(dx, dz);
		for(int i = -lines / 2; i < lines / 2; i++)
			mosaic.line(sat, line(colour), x + dx * i * B, z + dz * i * B, right[0], right[1], time + i);
	}

	@Test
	void tilesAreAnchoredToTheWorld() {
		assertEquals(WxMosaic.TILE * B, T);
		assertEquals(0, WxMosaic.tileOf(0));
		assertEquals(0, WxMosaic.tileOf(T - 1));
		assertEquals(1, WxMosaic.tileOf(T));
		assertEquals(-1, WxMosaic.tileOf(-1));
		long key = WxMosaic.key(-3, 7);
		assertEquals(-3, WxMosaic.tileX(key));
		assertEquals(7, WxMosaic.tileZ(key));
	}

	@Test
	void aPassLeavesNoHolesInItsSwathAtAnyAngle() {
		for(double angle : new double[] { 0.0D, 0.3D, Math.PI / 4.0D, 1.1D, 2.0D }) {
			WxMosaic mosaic = new WxMosaic(0, 0);
			double dx = Math.cos(angle), dz = Math.sin(angle);
			pass(mosaic, "CS-1", 100L, 0.0D, 0.0D, dx, dz, 200, (byte)5);
			// Every mosaic pixel well inside the swath (within 0.85 of its half width across, 1,200 blocks along) is filled.
			int reach = (int)(HALF / B) + 2;
			for(int gx = -reach; gx <= reach; gx += 3)
				for(int gz = -reach; gz <= reach; gz += 3) {
					double x = gx * (double)B + B / 2.0D, z = gz * (double)B + B / 2.0D;
					double along = x * dx + z * dz, across = -x * dz + z * dx;
					if(Math.abs(across) < HALF * 0.85D && Math.abs(along) < 1200.0D)
						assertEquals(5, mosaic.pixel(gx, gz, -1), "a hole at " + gx + "," + gz + " flying at " + angle);
				}
			int ox = (int)Math.floor(-dz * HALF * 1.2D), oz = (int)Math.floor(dx * HALF * 1.2D);
			assertEquals(0, mosaic.pixel(Math.floorDiv(ox, B), Math.floorDiv(oz, B), -1), "past the swath's edge across the track");
		}
	}

	@Test
	void theNewestPassIsOnTopAndOlderOnesShowThroughItsGaps() {
		WxMosaic mosaic = new WxMosaic(0, 0);
		pass(mosaic, "CS-1", 100L, 0.0D, 0.0D, 0.0D, -1.0D, 200, (byte)5); // North over the station.
		pass(mosaic, "CS-1", 5000L, HALF * 1.5D, 0.0D, 0.0D, -1.0D, 200, (byte)9); // Another, three quarters of a swath east: they overlap.
		int west = (int)(-HALF * 0.4D / B), east = (int)(HALF * 0.8D / B);
		assertEquals(5, mosaic.pixel(west, 0, -1), "only the first saw the west");
		assertEquals(9, mosaic.pixel(east, 0, -1), "the second is on top where they overlap");
		assertEquals(5, mosaic.pixel(east, 0, 0), "frame 0 (the loop's first) has only the first");
		assertEquals(2, mosaic.passCount());
		byte[] view = mosaic.view(0, 0, 64, 32, 64, -1);
		assertEquals(64 * 32, view.length);
		assertEquals(0, mosaic.view(-100_000, 0, 4, 4, B, -1)[0], "no data far away");
	}

	@Test
	void onlyTilesNearTheStationAreKept() {
		WxMosaic mosaic = new WxMosaic(0, 0);
		double far = (WxMosaic.RADIUS_TILES + 2) * (double)T + HALF;
		pass(mosaic, "CS-1", 100L, far, 0.0D, 0.0D, -1.0D, 50, (byte)5);
		assertEquals(0, mosaic.tileCount(), "a pass beyond the kept tiles is out of the mosaic");
		assertTrue(mosaic.near(WxMosaic.RADIUS_TILES, -WxMosaic.RADIUS_TILES));
		assertFalse(mosaic.near(WxMosaic.RADIUS_TILES + 1, 0));
	}

	/**
	 * A pass pushed out by newer ones leaves what only it saw (2026-11-16): the first pass, far east, is folded into the
	 * next; its ground is still in the picture, under the newer passes over the station.
	 */
	@Test
	void aPassPushedOutLeavesItsGround() {
		WxMosaic mosaic = new WxMosaic(0, 0);
		pass(mosaic, "CS-1", 100L, 2.5D * T, 0.0D, 0.0D, -1.0D, 10, (byte)9); // Only this one covers x 2.5 tiles out.
		for(int i = 1; i <= WxMosaic.MAX_PASSES; i++)
			pass(mosaic, "CS-1", 100L + i * 1000L, 0.0D, 0.0D, 0.0D, -1.0D, 10, (byte)(20 + i));
		assertEquals(WxMosaic.MAX_PASSES, mosaic.passCount());
		assertEquals(9, mosaic.pixel((int)(2.5D * T / B), 0, -1), "the first pass's ground went black");
		assertEquals(20 + WxMosaic.MAX_PASSES, mosaic.pixel(0, 0, -1), "the newest pass is on top");
	}

	@Test
	void oldPassesAgeOut() {
		WxMosaic mosaic = new WxMosaic(0, 0);
		for(int i = 0; i < WxMosaic.MAX_PASSES + 3; i++)
			pass(mosaic, "CS-1", 100L + i * 1000L, 0.0D, 0.0D, 0.0D, -1.0D, 10, (byte)(5 + i));
		assertEquals(WxMosaic.MAX_PASSES, mosaic.passCount(), "only the last passes are kept");
		assertEquals(100L + 3 * 1000L, mosaic.passes().getFirst().started(), "the oldest went first");
		mosaic.age(100L + (WxMosaic.MAX_PASSES + 3) * 1000L + WxMosaic.MAX_AGE_TICKS);
		assertEquals(0, mosaic.passCount(), "days later, all gone");
		// The tile cap: many long, wide passes in different places drop the oldest.
		WxMosaic big = new WxMosaic(0, 0);
		for(int i = 0; i < WxMosaic.MAX_PASSES; i++)
			pass(big, "CS-1", i * 1000L, (i % 3 - 1) * T * 0.3D, 0.0D, 0.0D, -1.0D, 900, (byte)5); // Over the station: each lays down many tiles.
		assertTrue(big.tileCount() <= WxMosaic.MAX_TILES, "at most " + WxMosaic.MAX_TILES + " tiles: " + big.tileCount());
		assertTrue(big.passCount() < WxMosaic.MAX_PASSES, "the tile cap dropped the oldest passes");
	}

	@Test
	void theFastViewMatchesPixelByPixel() {
		WxMosaic mosaic = new WxMosaic(0, 0);
		pass(mosaic, "CS-1", 100L, 300.0D, -200.0D, 0.6D, -0.8D, 300, (byte)7);
		int w = 300, h = 170, cx = 437, cz = -1311;
		byte[] view = mosaic.view(cx, cz, w, h, B, -1);
		for(int v = 0; v < h; v += 7)
			for(int u = 0; u < w; u += 5) {
				int x = (int)Math.floor(cx + (u - w / 2.0D + 0.5D) * B), z = (int)Math.floor(cz + (v - h / 2.0D + 0.5D) * B);
				assertEquals(mosaic.pixel(Math.floorDiv(x, B), Math.floorDiv(z, B), -1), view[u + v * w], "at " + u + "," + v);
			}
	}

	@Test
	void itSavesAndLoads() {
		WxMosaic mosaic = new WxMosaic(40, -70);
		pass(mosaic, "CS-2", 100L, 0.0D, 0.0D, 1.0D, 0.0D, 100, (byte)11);
		WxMosaic back = WxMosaic.load(mosaic.save());
		assertEquals(1, back.passCount());
		assertEquals("CS-2", back.passes().getFirst().satellite());
		assertEquals(mosaic.tileCount(), back.tileCount());
		assertEquals(40, back.stationX());
		assertArrayEquals(mosaic.view(0, 0, 50, 50, 16, -1), back.view(0, 0, 50, 50, 16, -1));
	}

	@Test
	void linesOfOnePassJoinOneLayerHoweverTheyArrive() {
		WxMosaic mosaic = new WxMosaic(0, 0);
		WxMosaic.Pass a = mosaic.join("CS-1", -1, 100L, 400L);
		mosaic.line(a, line((byte)5), 0, 0, 1, 0, 100L);
		assertSame(a, mosaic.join("CS-1", -1, 300L, 400L), "the same pass, within the gap");
		assertNotSame(a, mosaic.join("CS-2", -1, 300L, 400L), "another satellite is another layer");
		WxMosaic.Pass later = mosaic.join("CS-1", -1, 2000L, 400L);
		assertNotSame(a, later, "past the gap it is a new pass");
		WxMosaic.Pass f1 = mosaic.join("GEO", 1, 2000L, 400L), f2 = mosaic.join("GEO", 2, 2010L, 400L);
		assertNotSame(f1, f2, "each geostationary frame is its own layer");
		assertSame(f2, mosaic.join("GEO", 2, 2020L, 400L));
		WxMosaic back = WxMosaic.load(mosaic.save());
		assertEquals(2, back.passes().getLast().frame(), "the frame is saved");
	}
}
