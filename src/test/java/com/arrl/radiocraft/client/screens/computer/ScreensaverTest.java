package com.arrl.radiocraft.client.screens.computer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The screensavers (computer batch 3A): each draws something, moves, and the block letters cover a callsign's characters. */
class ScreensaverTest {

	private static int count(TextGrid g, char c) {
		int n = 0;
		for(int row = 0; row < TextGrid.ROWS; row++)
			for(int col = 0; col < TextGrid.COLS; col++)
				if(g.charAt(col, row) == c)
					n++;
		return n;
	}

	private static String snapshot(TextGrid g) {
		StringBuilder out = new StringBuilder();
		for(int row = 0; row < TextGrid.ROWS; row++)
			for(int col = 0; col < TextGrid.COLS; col++)
				out.append(g.charAt(col, row));
		return out.toString();
	}

	@Test
	void theStarfieldHasStarsThatMove() {
		Screensaver saver = new Screensaver();
		TextGrid g = new TextGrid();
		saver.render(g, 1_000L, "starfield", "W1BTR");
		int stars = count(g, '.') + count(g, '+') + count(g, '*');
		assertTrue(stars > 20, "only " + stars + " stars");
		String before = snapshot(g);
		saver.render(g, 1_500L, "starfield", "W1BTR");
		assertTrue(!before.equals(snapshot(g)), "the stars stood still");
	}

	@Test
	void theCallsignCrossesTheScreenInBlockLetters() {
		Screensaver saver = new Screensaver();
		TextGrid g = new TextGrid();
		int most = 0;
		for(long t = 0; t < 20_000L; t += 500L) {
			saver.render(g, t, "marquee", "w1btr");
			most = Math.max(most, count(g, TextGrid.FULL));
		}
		assertTrue(most > 40, "the letters never showed: " + most + " cells");
	}

	@Test
	void theWaterfallFallsAndHasAScale() {
		Screensaver saver = new Screensaver();
		TextGrid g = new TextGrid();
		saver.render(g, 0L, "waterfall", "W1BTR");
		saver.render(g, 5_000L, "waterfall", "W1BTR");
		assertEquals('|', g.charAt(10, TextGrid.ROWS - 1));
		int signal = count(g, TextGrid.FULL) + count(g, TextGrid.SHADE_MEDIUM);
		assertTrue(signal > 10, "no stations in the waterfall: " + signal);
	}

	@Test
	void unknownStylesAndEmptyTextStillDraw() {
		Screensaver saver = new Screensaver();
		TextGrid g = new TextGrid();
		saver.render(g, 100L, "no_such_style", null);
		saver.render(g, 100L, null, "");
		saver.render(g, 100L, "marquee", "");
	}

	@Test
	void everyCallsignCharacterHasALetter() {
		for(char c : "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-/".toCharArray()) {
			String[] rows = Screensaver.glyph(c);
			assertEquals(5, rows.length, String.valueOf(c));
			boolean lit = false;
			for(String row : rows) {
				assertEquals(5, row.length(), c + ": " + row);
				lit |= row.contains("#");
			}
			assertTrue(lit, c + " is blank");
		}
		for(String style : Screensaver.STYLES)
			assertTrue(style.matches("[a-z0-9_]{1,24}"), style);
	}
}
