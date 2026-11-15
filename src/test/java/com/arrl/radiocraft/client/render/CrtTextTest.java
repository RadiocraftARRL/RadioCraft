package com.arrl.radiocraft.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The CRT monitor's pixel font (2026-11-15): words fit, centred, on one block's tube and on a wall of them. */
class CrtTextTest {

	private static int lit(boolean[] mask) {
		int n = 0;
		for(boolean b : mask)
			if(b)
				n++;
		return n;
	}

	@Test
	void glyphsAreDrawn() {
		assertTrue(CrtText.lit('T', 2, 0) && CrtText.lit('T', 2, 6) && !CrtText.lit('T', 0, 6));
		assertTrue(CrtText.lit('w', 0, 0), "lower case is drawn as capitals");
		assertFalse(CrtText.lit('?', 2, 3), "unknown marks are spaces");
		assertEquals(5 * 6 - 1, CrtText.width("HELLO", 1));
	}

	@Test
	void theMessageFitsOneBlockAndScalesUpOnAWall() {
		boolean[] one = CrtText.mask(128, 128, "WAITING FOR", "SATELLITE PASS");
		assertTrue(lit(one) > 100, "nothing drawn on one block");
		for(int y = 0; y < 128; y++) { // A clear border all round.
			assertFalse(one[y * 128] || one[y * 128 + 127]);
			assertFalse(one[y] || one[127 * 128 + y]);
		}
		boolean[] wall = CrtText.mask(384, 256, "WAITING FOR", "SATELLITE PASS");
		assertTrue(lit(wall) > lit(one) * 3, "a wall of monitors draws it larger");
		assertEquals(0, lit(CrtText.mask(20, 10, "WAITING FOR")), "too small for even scale 1: nothing");
	}
}
