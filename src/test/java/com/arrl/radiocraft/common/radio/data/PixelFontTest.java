package com.arrl.radiocraft.common.radio.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The little 3 by 5 font of the APRS maps and the SSTV captions (moved out of AprsMapPainter in 1.12 step 7). */
class PixelFontTest {

	@Test
	void everyGlyphIsThreeByFive() {
		for(char c : "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-/.,!?:'+=()#%&*\"_ ".toCharArray()) {
			assertTrue(PixelFont.has(c), "missing " + c);
			String[] glyph = PixelFont.glyph(c);
			assertEquals(PixelFont.HEIGHT, glyph.length, "rows of " + c);
			for(String row : glyph)
				assertTrue(row.matches("[#.]{3}"), "row of " + c + ": " + row);
		}
	}

	@Test
	void lowerCaseDrawsAsCapitalsAndUnknownAsADash() {
		assertArrayEquals(PixelFont.glyph('A'), PixelFont.glyph('a'));
		assertArrayEquals(PixelFont.glyph('-'), PixelFont.glyph('é'));
		assertFalse(PixelFont.has('é'));
		assertArrayEquals(new String[] { "...", "...", "...", "...", "..." }, PixelFont.glyph(' '));
	}

	@Test
	void widthCountsTheGapsBetween() {
		assertEquals(0, PixelFont.width("", 1));
		assertEquals(3, PixelFont.width("A", 1));
		assertEquals(7, PixelFont.width("AB", 1));
		assertEquals(14, PixelFont.width("AB", 2));
	}
}
