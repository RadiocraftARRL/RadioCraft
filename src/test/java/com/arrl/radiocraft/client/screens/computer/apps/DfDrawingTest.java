package com.arrl.radiocraft.client.screens.computer.apps;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The direction finding program's map and 3D view (1.12 step 7), drawn into map colour pictures. */
class DfDrawingTest {

	private static final int W = 128, H = 128;
	private static final byte GROUND = 0, OWN = 5, FIX = 9, EDGE = 1;

	private static int count(byte[] pixels, byte colour) {
		int n = 0;
		for(byte b : pixels)
			if(b == colour)
				n++;
		return n;
	}

	@Test
	void worldToPictureMatchesTheTerrainPainter() {
		assertEquals(64.0D, DfDrawing.pixelX(100, 100, 2, W), 1.0E-9D, "the middle");
		assertEquals(74.0D, DfDrawing.pixelX(120, 100, 2, W), 1.0E-9D);
		assertEquals(54.0D, DfDrawing.pixelZ(80, 100, 2, H), 1.0E-9D, "north is up");
	}

	@Test
	void aBearingRunsTheWayItPoints() {
		byte[] out = DfDrawing.plot(new byte[W * H], W, H, 0, 0, 1, List.of(new DfDrawing.Bearing(0.5D, 0.5D, 0.0D, 5.0D, OWN)), false, 0, 0, 0, FIX, EDGE);
		assertEquals(OWN, out[64 + 20 * W], "a bearing of 0 goes north (up)");
		assertEquals(GROUND, out[64 + 100 * W], "and not south");
		assertTrue(count(out, OWN) > 60);
		assertEquals(0, count(out, FIX), "no fix drawn without one");
	}

	@Test
	void theFixIsACrossInItsRing() {
		byte[] out = DfDrawing.plot(new byte[W * H], W, H, 0, 0, 1, List.of(), true, 10.0D, -10.0D, 20.0D, FIX, EDGE);
		assertEquals(FIX, out[(64 + 10) + (64 - 10) * W], "the cross at the fix");
		assertEquals(FIX, out[(64 + 10 + 20) + (64 - 10) * W], "the ring 20 blocks out");
	}

	@Test
	void theSceneTurns() {
		DfScene.Palette palette = new DfScene.Palette((byte)0, (byte)2, (byte)3, (byte)4, (byte)6, (byte)7);
		List<DfDrawing.Bearing> bearings = List.of(new DfDrawing.Bearing(0, 0, 45, 3, OWN), new DfDrawing.Bearing(200, 0, 315, 3, (byte)8));
		byte[] a = new byte[160 * 100], b = new byte[160 * 100];
		DfScene.render(a, 160, 100, 0.0D, bearings, true, 100, -100, 12, palette);
		DfScene.render(b, 160, 100, 1.0D, bearings, true, 100, -100, 12, palette);
		assertTrue(count(a, (byte)4) > 0, "no peak drawn");
		assertTrue(count(a, (byte)2) > 0, "no ground");
		assertTrue(count(a, OWN) > 0, "no bearing");
		assertNotEquals(java.util.Arrays.hashCode(a), java.util.Arrays.hashCode(b), "it did not turn");
		assertEquals(1.0D, DfScene.bell(0, 10), 1.0E-9D);
		assertTrue(DfScene.bell(30, 10) < 0.02D);
	}
}
