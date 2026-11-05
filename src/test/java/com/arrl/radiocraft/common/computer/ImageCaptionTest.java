package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SSTV captions (1.12 step 7): cleaned, wrapped, big when short, drawn with an outline at the top and the bottom. */
class ImageCaptionTest {

	private static final byte PICTURE = 7, FILL = 1, OUTLINE = 2;

	private static byte[] picture() {
		byte[] pixels = new byte[ImageCaption.SIZE * ImageCaption.SIZE];
		Arrays.fill(pixels, PICTURE);
		return pixels;
	}

	private static int count(byte[] pixels, byte colour, int fromRow, int toRow) {
		int n = 0;
		for(int y = fromRow; y < toRow; y++)
			for(int x = 0; x < ImageCaption.SIZE; x++)
				if(pixels[x + y * ImageCaption.SIZE] == colour)
					n++;
		return n;
	}

	@Test
	void cleanKeepsWhatTheFontDraws() {
		assertEquals("73 DE W1BTR!", ImageCaption.clean("  73   de w1btr! "));
		assertEquals("CAF", ImageCaption.clean("café"), "letters the font lacks go");
		assertEquals(ImageCaption.MAX_CHARS, ImageCaption.clean("X".repeat(200)).length());
	}

	@Test
	void wrapBreaksAtSpacesAndCutsLongWords() {
		assertEquals(List.of("HELLO", "WORLD"), ImageCaption.wrap("HELLO WORLD", 8));
		assertEquals(List.of("ABCDE", "FGH", "IJ"), ImageCaption.wrap("ABCDEFGH IJ", 5));
		assertTrue(ImageCaption.wrap("", 10).isEmpty());
	}

	@Test
	void shortCaptionsAreBig() {
		assertEquals(2, ImageCaption.scaleFor("CQ CQ"));
		assertEquals(1, ImageCaption.scaleFor("THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG TODAY"));
		assertTrue(ImageCaption.perLine(1) >= 30 && ImageCaption.perLine(2) >= 14);
	}

	@Test
	void captionsLandTopAndBottomWithAnOutline() {
		byte[] out = ImageCaption.apply(picture(), "TOP", "BOTTOM", FILL, OUTLINE);
		int half = ImageCaption.SIZE / 2;
		assertTrue(count(out, FILL, 0, 20) > 0, "no top caption");
		assertTrue(count(out, FILL, ImageCaption.SIZE - 20, ImageCaption.SIZE) > 0, "no bottom caption");
		assertEquals(0, count(out, FILL, 30, half + 30), "caption in the middle");
		assertTrue(count(out, OUTLINE, 0, 20) > count(out, FILL, 0, 20) / 2, "no outline");
		assertEquals(PICTURE, out[half + half * ImageCaption.SIZE], "the picture under it changed");
	}

	@Test
	void emptyCaptionsLeaveThePicture() {
		byte[] in = picture();
		assertArrayEquals(in, ImageCaption.apply(in, "", "  ", FILL, OUTLINE));
		assertFalse(ImageCaption.apply(in, "A", "", FILL, OUTLINE) == in, "must be a copy");
		assertThrows(IllegalArgumentException.class, () -> ImageCaption.apply(new byte[10], "A", "", FILL, OUTLINE));
	}
}
