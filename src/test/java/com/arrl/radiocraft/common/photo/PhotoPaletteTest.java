package com.arrl.radiocraft.common.photo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nearest colours, greys, and cleaning up a picture a client sent (camera, 1.12 step 4). */
class PhotoPaletteTest {

	static final byte BLACK = 4, GREY = 8, WHITE = 12, RED = 16, GREEN = 20, BLUE = 24, DARK_RED = 28;

	/** Black, mid grey, white, red, green, blue and a dark red (darker than mid grey). */
	static PhotoPalette small() {
		return new PhotoPalette(new int[] { 0x000000, 0x808080, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0x500000 },
				new byte[] { BLACK, GREY, WHITE, RED, GREEN, BLUE, DARK_RED });
	}

	@Test
	void nearestFindsExactAndCloseColours() {
		PhotoPalette palette = small();
		assertEquals(RED, palette.id(palette.nearest(0xFF0000)));
		assertEquals(RED, palette.id(palette.nearest(0xF01010)));
		assertEquals(BLUE, palette.id(palette.nearest(0x1010E0)));
		assertEquals(WHITE, palette.id(palette.nearest(0xF0F0F0)));
		assertEquals(GREY, palette.id(palette.nearest(0x7A7A7A)));
		assertEquals(BLACK, palette.id(palette.nearest(0x050505)));
	}

	@Test
	void lookupsById() {
		PhotoPalette palette = small();
		assertTrue(palette.contains(RED));
		assertFalse(palette.contains((byte)0));
		assertFalse(palette.contains((byte)-1));
		assertEquals(0xFF0000, palette.rgbOf(RED));
		assertEquals(-1, palette.rgbOf((byte)99));
		assertEquals(BLACK, palette.darkest());
	}

	@Test
	void greysAreOnlyTheGreys() {
		PhotoPalette greys = small().greys();
		assertEquals(3, greys.size());
		for(int i = 0; i < greys.size(); i++)
			assertTrue(PhotoPalette.isGrey(greys.rgb(i)), "not grey: " + Integer.toHexString(greys.rgb(i)));
		assertTrue(greys.contains(BLACK) && greys.contains(GREY) && greys.contains(WHITE));
	}

	@Test
	void luminanceWeightsGreenMost() {
		assertEquals(0, PhotoPalette.luminance(0x000000));
		assertEquals(255, PhotoPalette.luminance(0xFFFFFF));
		assertTrue(PhotoPalette.luminance(0x00FF00) > PhotoPalette.luminance(0xFF0000));
		assertTrue(PhotoPalette.luminance(0xFF0000) > PhotoPalette.luminance(0x0000FF));
	}

	@Test
	void unknownIdsBecomeBlack() {
		byte[] in = { RED, 0, 99, -1, WHITE };
		assertArrayEquals(new byte[] { RED, BLACK, BLACK, BLACK, WHITE }, small().sanitised(in));
		assertEquals(RED, in[0], "the input is left alone");
	}

	@Test
	void greyscaleTurnsEveryColourGrey() {
		PhotoPalette palette = small();
		byte[] grey = palette.greyscaled(new byte[] { RED, GREEN, BLUE, DARK_RED, WHITE, GREY, BLACK, 99 });
		for(byte id : grey)
			assertTrue(palette.greys().contains(id), "not a grey: " + id);
		// By brightness: red (76) and green (150) are nearest mid grey (128), blue (29) and dark red (24) black; greys stay put.
		assertEquals(GREY, grey[0]);
		assertEquals(GREY, grey[1]);
		assertEquals(BLACK, grey[2]);
		assertEquals(BLACK, grey[3]);
		assertEquals(WHITE, grey[4]);
		assertEquals(GREY, grey[5]);
		assertEquals(BLACK, grey[6]);
		assertEquals(BLACK, grey[7], "an unknown id is the darkest grey");
	}
}
