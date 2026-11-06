package com.arrl.radiocraft.client.screens.computer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Program Manager's icon grid: moving the highlight, scrolling to it, and clicks to icons. */
class IconGridTest {

	/** 6 to a row, 3 rows shown, 26 icons (5 rows, the last with 2). */
	private static IconGrid grid() {
		IconGrid grid = new IconGrid(6, 3);
		grid.setCount(26);
		return grid;
	}

	@Test
	void arrowsMoveAndRunOnThroughTheRows() {
		IconGrid grid = grid();
		assertEquals(0, grid.selected());
		assertFalse(grid.move(-1, 0), "left of the first icon");
		grid.move(1, 0);
		assertEquals(1, grid.selected());
		grid.select(5);
		grid.move(1, 0);
		assertEquals(6, grid.selected(), "right from the end of a row goes on to the next");
		grid.move(0, 1);
		assertEquals(12, grid.selected(), "down keeps the column");
		grid.move(0, -1);
		grid.move(0, -1);
		assertEquals(0, grid.selected());
		assertFalse(grid.move(0, -1), "up from the top row");
	}

	@Test
	void downIntoAShortLastRowLandsOnItsEnd() {
		IconGrid grid = grid();
		grid.select(21); // Row 3, column 3; row 4 has only 24 and 25.
		grid.move(0, 1);
		assertEquals(25, grid.selected());
		assertFalse(grid.move(0, 1), "down from the last row");
	}

	@Test
	void theViewFollowsTheHighlight() {
		IconGrid grid = grid();
		assertEquals(5, grid.totalRows());
		grid.end();
		assertEquals(25, grid.selected());
		assertEquals(2, grid.top(), "the last rows show");
		assertArrayEquals(new int[] { 1, 2 }, grid.tile(25));
		assertNull(grid.tile(0), "the first row is scrolled away");
		grid.home();
		assertEquals(0, grid.top());
		grid.page(1);
		assertEquals(18, grid.selected());
		assertEquals(1, grid.top());
	}

	@Test
	void clicksFindIconsInTheView() {
		IconGrid grid = grid();
		assertEquals(7, grid.at(1, 1));
		assertEquals(-1, grid.at(6, 0), "past the columns");
		grid.end();
		assertEquals(-1, grid.at(2, 2), "after the last icon");
		assertEquals(24, grid.at(0, 2));
	}

	@Test
	void scrollingMovesTheViewAndKeepsTheHighlightInIt() {
		IconGrid grid = grid();
		grid.scroll(5);
		assertEquals(2, grid.top(), "no further than the last rows");
		assertTrue(grid.selected() >= 12, "the highlight came into view: " + grid.selected());
		grid.scroll(-9);
		assertEquals(0, grid.top());
	}

	@Test
	void fewerIconsKeepTheHighlightInside() {
		IconGrid grid = grid();
		grid.end();
		grid.setCount(4);
		assertEquals(3, grid.selected());
		assertEquals(0, grid.top());
		grid.setCount(0);
		assertEquals(-1, grid.selected());
		assertFalse(grid.move(1, 0));
	}
}
