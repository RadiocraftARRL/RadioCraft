package com.arrl.radiocraft.common.photo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scaling a frame to a map, greyscale and quantising with dithering (camera, 1.12 step 4). */
class PhotoImageTest {

	@Test
	void downscaleTakesTheMiddleSquare() {
		// A 300 x 100 frame: red left third, green middle, blue right. The square is the middle third: all green.
		int[] out = PhotoImage.downscale((x, y) -> x < 100 ? 0xFF0000 : x < 200 ? 0x00FF00 : 0x0000FF, 300, 100, 16);
		assertEquals(16 * 16, out.length);
		for(int pixel : out)
			assertEquals(0x00FF00, pixel);
	}

	@Test
	void downscaleAveragesEachBox() {
		// A fine black and white checkerboard averages to mid grey; top half white, bottom black keeps its halves.
		int[] check = PhotoImage.downscale((x, y) -> ((x + y) & 1) == 0 ? 0xFFFFFF : 0x000000, 64, 64, 8);
		for(int pixel : check)
			assertEquals(0x7F7F7F, pixel); // 32 white samples of 64: 255 * 32 / 64, rounded down.
		int[] halves = PhotoImage.downscale((x, y) -> y < 32 ? 0xFFFFFF : 0x000000, 64, 64, 8);
		assertEquals(0xFFFFFF, halves[0]);
		assertEquals(0x000000, halves[8 * 8 - 1]);
	}

	@Test
	void downscaleCopesWithTinyAndHugeFrames() {
		int[] tiny = PhotoImage.downscale((x, y) -> 0x123456, 3, 2, PhotoImage.SIZE);
		assertEquals(PhotoImage.PIXELS, tiny.length);
		assertEquals(0x123456, tiny[PhotoImage.PIXELS - 1]);
		int[] reads = { 0 };
		PhotoImage.downscale((x, y) -> {
			reads[0]++;
			return 0;
		}, 3840, 2160, PhotoImage.SIZE);
		// At most MAX_SAMPLES (and a remainder) a side per output pixel, whatever the resolution.
		int perSide = PhotoImage.MAX_SAMPLES + 1;
		assertTrue(reads[0] <= PhotoImage.PIXELS * perSide * perSide, "read " + reads[0] + " pixels");
		assertThrows(IllegalArgumentException.class, () -> PhotoImage.downscale((x, y) -> 0, 0, 10, 8));
	}

	@Test
	void greyscaleKeepsBrightness() {
		int[] grey = PhotoImage.greyscale(new int[] { 0xFF0000, 0xFFFFFF, 0x000000 });
		assertEquals(76 * 0x010101, grey[0]);
		assertEquals(0xFFFFFF, grey[1]);
		assertEquals(0, grey[2]);
	}

	@Test
	void quantiseWithoutDitherPicksNearest() {
		PhotoPalette palette = PhotoPaletteTest.small();
		byte[] ids = PhotoImage.quantise(new int[] { 0xFE0101, 0x0101FE, 0xFAFAFA, 0x060606 }, 2, 2, palette, false);
		assertEquals(PhotoPaletteTest.RED, ids[0]);
		assertEquals(PhotoPaletteTest.BLUE, ids[1]);
		assertEquals(PhotoPaletteTest.WHITE, ids[2]);
		assertEquals(PhotoPaletteTest.BLACK, ids[3]);
		assertThrows(IllegalArgumentException.class, () -> PhotoImage.quantise(new int[3], 2, 2, palette, false));
	}

	@Test
	void ditherKeepsTheAverageTone() {
		// A flat 25 % grey with only black and white to hand: plain rounding makes it all black, dithering makes about a
		// quarter of the pixels white.
		PhotoPalette bw = new PhotoPalette(new int[] { 0x000000, 0xFFFFFF }, new byte[] { 4, 8 });
		int[] flat = new int[64 * 64];
		java.util.Arrays.fill(flat, 0x404040);
		byte[] plain = PhotoImage.quantise(flat, 64, 64, bw, false);
		byte[] dithered = PhotoImage.quantise(flat, 64, 64, bw, true);
		assertEquals(0, count(plain, (byte)8));
		double white = count(dithered, (byte)8) / (double)flat.length;
		assertEquals(0x40 / 255.0D, white, 0.03D);
	}

	@Test
	void blackAndWhitePhotosUseOnlyGreys() {
		PhotoPalette palette = PhotoPaletteTest.small();
		int[] rgb = new int[PhotoImage.PIXELS];
		for(int i = 0; i < rgb.length; i++)
			rgb[i] = (i * 2654435761L & 0xFFFFFF) > 0x800000 ? 0xFF2020 : 0x20A0FF;
		for(byte id : PhotoImage.photo(rgb, palette, false))
			assertTrue(palette.greys().contains(id), "B&W photo has colour " + id);
		boolean anyColour = false;
		for(byte id : PhotoImage.photo(rgb, palette, true))
			anyColour |= !palette.greys().contains(id);
		assertTrue(anyColour, "a colour photo of red and blue has some colour");
	}

	@Test
	void toRgbRoundTrips() {
		PhotoPalette palette = PhotoPaletteTest.small();
		int[] back = PhotoImage.toRgb(new byte[] { PhotoPaletteTest.RED, PhotoPaletteTest.WHITE, 99 }, palette);
		assertEquals(0xFF0000, back[0]);
		assertEquals(0xFFFFFF, back[1]);
		assertEquals(0, back[2]);
	}

	private static int count(byte[] ids, byte id) {
		int n = 0;
		for(byte b : ids)
			if(b == id)
				n++;
		return n;
	}
}
