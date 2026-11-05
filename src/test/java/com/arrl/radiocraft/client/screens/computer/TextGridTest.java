package com.arrl.radiocraft.client.screens.computer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The radio computer's 80 x 25 text screen (1.12 step 5): writing, clipping, boxes and the cursor. */
class TextGridTest {

	@Test
	void eightyByTwentyFiveInSixByTenCells() {
		assertEquals(480, TextGrid.WIDTH);
		assertEquals(250, TextGrid.HEIGHT);
		assertEquals(16, DosColours.PALETTE.length);
	}

	@Test
	void putWritesAndClipsAtTheEdge() {
		TextGrid g = new TextGrid();
		int next = g.put(76, 3, "HELLO", DosColours.WHITE, DosColours.BLUE);
		assertEquals(81, next);
		assertEquals('H', g.charAt(76, 3));
		assertEquals('L', g.charAt(79, 3));
		assertEquals(DosColours.BLUE, g.backgroundAt(79, 3));
		assertEquals(' ', g.charAt(80, 3), "off the grid reads as a space");
		g.put(-2, 0, "ABC", DosColours.WHITE, DosColours.BLACK);
		assertEquals('C', g.charAt(0, 0));
	}

	@Test
	void fieldsPadAndCut() {
		TextGrid g = new TextGrid();
		g.put(0, 0, "XXXXXX", DosColours.WHITE, DosColours.BLACK);
		g.putField(0, 0, "AB", 4, DosColours.WHITE, DosColours.BLACK);
		assertEquals("AB  XX", row(g, 0, 6));
		g.putField(0, 1, "ABCDEFG", 3, DosColours.WHITE, DosColours.BLACK);
		assertEquals("ABC ", row(g, 1, 4));
	}

	@Test
	void boxesHaveCornersEdgesAndTitles() {
		TextGrid g = new TextGrid();
		g.box(2, 2, 10, 4, true, "T", DosColours.WHITE, DosColours.BLUE);
		assertEquals(TextGrid.DOUBLE_TL, g.charAt(2, 2));
		assertEquals(TextGrid.DOUBLE_BR, g.charAt(11, 5));
		assertEquals(TextGrid.DOUBLE_V, g.charAt(2, 3));
		assertEquals(TextGrid.DOUBLE_H, g.charAt(3, 5));
		assertTrue(row(g, 2, 12).contains(" T "));
		assertEquals(' ', g.charAt(5, 3));
		assertEquals(DosColours.BLUE, g.backgroundAt(5, 3));
		g.box(0, 10, 4, 3, false, null, DosColours.WHITE, DosColours.BLACK);
		assertEquals(TextGrid.TL, g.charAt(0, 10));
		assertTrue(TextGrid.isBoxDrawing(TextGrid.TL));
		assertTrue(TextGrid.isBlock(TextGrid.SHADE_LIGHT));
		assertFalse(TextGrid.isBoxDrawing('A'));
	}

	@Test
	void barsFillByFraction() {
		TextGrid g = new TextGrid();
		g.bar(0, 0, 10, 0.3D, DosColours.WHITE, DosColours.BLACK);
		assertEquals(TextGrid.FULL, g.charAt(2, 0));
		assertEquals(TextGrid.SHADE_LIGHT, g.charAt(3, 0));
		g.bar(0, 1, 4, 5.0D, DosColours.WHITE, DosColours.BLACK);
		assertEquals(TextGrid.FULL, g.charAt(3, 1));
	}

	@Test
	void clearDropsTheCursorAndPictures() {
		TextGrid g = new TextGrid();
		g.cursor(5, 5);
		g.image(0, 0, 10, 10, "k", new byte[4], 2, 2);
		g.image(0, 0, 10, 10, "too small", new byte[3], 2, 2);
		assertTrue(g.cursorVisible());
		assertEquals(1, g.images().size());
		g.clear(DosColours.WHITE, DosColours.BLACK);
		assertFalse(g.cursorVisible());
		assertTrue(g.images().isEmpty());
	}

	private static String row(TextGrid g, int row, int width) {
		StringBuilder out = new StringBuilder();
		for(int col = 0; col < width; col++)
			out.append(g.charAt(col, row));
		return out.toString();
	}
}
