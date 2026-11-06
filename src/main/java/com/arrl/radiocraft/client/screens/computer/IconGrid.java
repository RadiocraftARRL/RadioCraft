package com.arrl.radiocraft.client.screens.computer;

/**
 * The Program Manager's grid of icons as numbers (1.12, launcher and SETUP): how many icons, how many to a row, how
 * many rows show at once, which is highlighted and which row is at the top. The arrow keys move the highlight (left
 * and right run on through the rows, up and down keep the column; down from the last full row lands on the last icon),
 * Page Up/Down a screen of rows, Home and End the first and last; the top row follows the highlight. A click is turned
 * into an icon with {@link #at}. Pure (no game classes), so it is unit tested.
 */
public final class IconGrid {

	private final int columns, rows;
	private int count = 0;
	private int selected = 0;
	private int top = 0;

	/** A grid {@code columns} icons wide showing {@code rows} rows at a time. */
	public IconGrid(int columns, int rows) {
		if(columns < 1 || rows < 1)
			throw new IllegalArgumentException("An icon grid needs a column and a row");
		this.columns = columns;
		this.rows = rows;
	}

	public int columns() {
		return columns;
	}

	public int rows() {
		return rows;
	}

	public int count() {
		return count;
	}

	/** The highlighted icon, or -1 with none. */
	public int selected() {
		return count == 0 ? -1 : selected;
	}

	/** The first row shown. */
	public int top() {
		return top;
	}

	/** Rows the icons fill. */
	public int totalRows() {
		return (count + columns - 1) / columns;
	}

	/** There are now {@code count} icons: the highlight and the top row stay inside them. */
	public void setCount(int count) {
		this.count = Math.max(0, count);
		select(selected);
	}

	/** Highlights icon {@code index} (kept inside the grid) and scrolls to it. */
	public void select(int index) {
		selected = count == 0 ? 0 : Math.max(0, Math.min(count - 1, index));
		follow();
	}

	/** Moves the highlight by {@code dx} icons along and {@code dy} rows down. Returns true if it moved. */
	public boolean move(int dx, int dy) {
		if(count == 0)
			return false;
		int before = selected;
		int to = selected + dx;
		if(dy != 0) {
			to = selected + dy * columns;
			if(to >= count)
				to = selected / columns < totalRows() - 1 ? count - 1 : selected; // A short last row: land on its end.
			else if(to < 0)
				to = selected;
		}
		select(to);
		return selected != before;
	}

	/** A screen of rows down ({@code pages} positive) or up. */
	public void page(int pages) {
		if(count == 0)
			return;
		int to = selected + pages * rows * columns;
		select(to < 0 ? selected % columns : to >= count ? count - 1 : to);
	}

	public void home() {
		select(0);
	}

	public void end() {
		select(count - 1);
	}

	/** Scrolls {@code by} rows (the wheel), keeping the highlight where it is unless it would leave the view. */
	public void scroll(int by) {
		top = Math.max(0, Math.min(Math.max(0, totalRows() - rows), top + by));
		int row = selected / columns;
		if(row < top)
			selected = Math.min(count - 1, top * columns + selected % columns);
		else if(row >= top + rows)
			selected = Math.min(count - 1, (top + rows - 1) * columns + selected % columns);
	}

	/** The icon in the tile at {@code tileCol, tileRow} of the view (0, 0 the top left), or -1 if there is none. */
	public int at(int tileCol, int tileRow) {
		if(tileCol < 0 || tileCol >= columns || tileRow < 0 || tileRow >= rows)
			return -1;
		int index = (top + tileRow) * columns + tileCol;
		return index < count ? index : -1;
	}

	/** Where icon {@code index} is in the view: {tileCol, tileRow}, or null if it is scrolled out of sight. */
	public int[] tile(int index) {
		int row = index / columns - top;
		if(index < 0 || index >= count || row < 0 || row >= rows)
			return null;
		return new int[] { index % columns, row };
	}

	private void follow() {
		int row = selected / columns;
		if(row < top)
			top = row;
		else if(row >= top + rows)
			top = row - rows + 1;
		top = Math.max(0, Math.min(top, Math.max(0, totalRows() - rows)));
	}
}
