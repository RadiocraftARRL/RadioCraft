package com.arrl.radiocraft.client.screens.computer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The radio computer's screen as a DOS program sees it: 80 columns by 25 rows of character cells, each with its own
 * foreground and background colour (0xRRGGBB), a text cursor, and picture areas laid over the cells (map pictures, in
 * pixels of the 480 x 250 screen). Programs draw into it every frame; {@link DosRenderer} puts it on the screen.
 * <p>
 * The box-drawing and shading characters of code page 437 ({@link #H}, {@link #DOUBLE_TL}, {@link #SHADE_LIGHT}...)
 * are drawn as lines and fills, so boxes join up whatever the font. Writing off the grid is clipped. Pure (no game
 * classes), so it is unit tested.
 */
public final class TextGrid {

	public static final int COLS = 80, ROWS = 25;
	/** One cell, in screen pixels (before the screen's scale): 480 x 250 in all. */
	public static final int CELL_W = 6, CELL_H = 10;
	public static final int WIDTH = COLS * CELL_W, HEIGHT = ROWS * CELL_H;

	// Code page 437 box drawing, single and double lines.
	public static final char H = '─', V = '│', TL = '┌', TR = '┐', BL = '└', BR = '┘',
			T_DOWN = '┬', T_UP = '┴', T_RIGHT = '├', T_LEFT = '┤', CROSS = '┼';
	public static final char DOUBLE_H = '═', DOUBLE_V = '║', DOUBLE_TL = '╔', DOUBLE_TR = '╗',
			DOUBLE_BL = '╚', DOUBLE_BR = '╝';
	// Shading and blocks.
	public static final char SHADE_LIGHT = '░', SHADE_MEDIUM = '▒', SHADE_DARK = '▓', FULL = '█',
			LOWER_HALF = '▄', UPPER_HALF = '▀';
	// Arrows and marks drawn as small shapes.
	public static final char ARROW_UP = '▲', ARROW_DOWN = '▼', ARROW_LEFT = '◄', ARROW_RIGHT = '►';

	/** A picture over the screen: map colour ids ({@code width} x {@code height}) drawn at a pixel rectangle. */
	public record Image(int x, int y, int w, int h, String key, byte[] pixels, int width, int height) {}

	private final char[] chars = new char[COLS * ROWS];
	private final int[] fg = new int[COLS * ROWS];
	private final int[] bg = new int[COLS * ROWS];
	private final List<Image> images = new ArrayList<>();
	private int cursorCol = -1, cursorRow = -1;

	public TextGrid() {
		clear(DosColours.LIGHT_GREY, DosColours.BLACK);
	}

	/** Blanks every cell in these colours; drops the cursor and the pictures. */
	public void clear(int foreground, int background) {
		Arrays.fill(chars, ' ');
		Arrays.fill(fg, foreground);
		Arrays.fill(bg, background);
		images.clear();
		cursorCol = cursorRow = -1;
	}

	public static boolean inside(int col, int row) {
		return col >= 0 && col < COLS && row >= 0 && row < ROWS;
	}

	public char charAt(int col, int row) {
		return inside(col, row) ? chars[row * COLS + col] : ' ';
	}

	public int foregroundAt(int col, int row) {
		return inside(col, row) ? fg[row * COLS + col] : 0;
	}

	public int backgroundAt(int col, int row) {
		return inside(col, row) ? bg[row * COLS + col] : 0;
	}

	/** One cell. */
	public void set(int col, int row, char c, int foreground, int background) {
		if(!inside(col, row))
			return;
		int i = row * COLS + col;
		chars[i] = c;
		fg[i] = foreground;
		bg[i] = background;
	}

	/** Recolours one cell, keeping its character. */
	public void colour(int col, int row, int foreground, int background) {
		if(!inside(col, row))
			return;
		fg[row * COLS + col] = foreground;
		bg[row * COLS + col] = background;
	}

	/** Writes {@code text} from {@code col}; returns the column after it. */
	public int put(int col, int row, String text, int foreground, int background) {
		for(int i = 0; i < text.length(); i++)
			set(col + i, row, text.charAt(i), foreground, background);
		return col + text.length();
	}

	/** Writes {@code text} in {@code width} cells: cut to fit, the rest filled with spaces. */
	public void putField(int col, int row, String text, int width, int foreground, int background) {
		for(int i = 0; i < width; i++)
			set(col + i, row, i < text.length() ? text.charAt(i) : ' ', foreground, background);
	}

	/** Writes {@code text} centred in the row. */
	public void center(int row, String text, int foreground, int background) {
		put(Math.max(0, (COLS - text.length()) / 2), row, text, foreground, background);
	}

	/** Fills a rectangle of cells with {@code c}. */
	public void fill(int col, int row, int width, int height, char c, int foreground, int background) {
		for(int r = row; r < row + height; r++)
			for(int x = col; x < col + width; x++)
				set(x, r, c, foreground, background);
	}

	/** A row of {@code c} {@code width} long. */
	public void hline(int col, int row, int width, char c, int foreground, int background) {
		fill(col, row, width, 1, c, foreground, background);
	}

	/**
	 * A box {@code width} by {@code height} cells (at least 2 by 2), its inside filled with spaces in {@code background},
	 * single or double lined, with {@code title} centred in its top edge if given.
	 */
	public void box(int col, int row, int width, int height, boolean doubleLine, String title, int foreground, int background) {
		if(width < 2 || height < 2)
			return;
		fill(col + 1, row + 1, width - 2, height - 2, ' ', foreground, background);
		char h = doubleLine ? DOUBLE_H : H, v = doubleLine ? DOUBLE_V : V;
		hline(col + 1, row, width - 2, h, foreground, background);
		hline(col + 1, row + height - 1, width - 2, h, foreground, background);
		for(int r = row + 1; r < row + height - 1; r++) {
			set(col, r, v, foreground, background);
			set(col + width - 1, r, v, foreground, background);
		}
		set(col, row, doubleLine ? DOUBLE_TL : TL, foreground, background);
		set(col + width - 1, row, doubleLine ? DOUBLE_TR : TR, foreground, background);
		set(col, row + height - 1, doubleLine ? DOUBLE_BL : BL, foreground, background);
		set(col + width - 1, row + height - 1, doubleLine ? DOUBLE_BR : BR, foreground, background);
		if(title != null && !title.isEmpty()) {
			String t = " " + (title.length() > width - 4 ? title.substring(0, Math.max(0, width - 4)) : title) + " ";
			put(col + (width - t.length()) / 2, row, t, foreground, background);
		}
	}

	/** A drop shadow right of and below a box, as DOS dialogs have: those cells darkened. */
	public void shadow(int col, int row, int width, int height) {
		for(int r = row + 1; r <= row + height; r++)
			for(int c = col + width; c < col + width + 2; c++)
				colour(c, r, DosColours.DARK_GREY, DosColours.BLACK);
		for(int c = col + 2; c < col + width; c++)
			colour(c, row + height, DosColours.DARK_GREY, DosColours.BLACK);
	}

	/** A horizontal bar of {@code width} cells, {@code fraction} of it solid (a progress or usage bar). */
	public void bar(int col, int row, int width, double fraction, int foreground, int background) {
		int full = (int)Math.round(Math.max(0.0D, Math.min(1.0D, fraction)) * width);
		for(int i = 0; i < width; i++)
			set(col + i, row, i < full ? FULL : SHADE_LIGHT, foreground, background);
	}

	// -------------------- CURSOR AND PICTURES --------------------

	/** Shows the blinking text cursor at a cell (off the grid hides it). */
	public void cursor(int col, int row) {
		cursorCol = col;
		cursorRow = row;
	}

	public int cursorCol() {
		return cursorCol;
	}

	public int cursorRow() {
		return cursorRow;
	}

	public boolean cursorVisible() {
		return inside(cursorCol, cursorRow);
	}

	/**
	 * A picture of map colour ids ({@code width} x {@code height}, row by row) drawn over the pixel rectangle
	 * {@code x, y, w, h} of the 480 x 250 screen. {@code key} names it so its texture is made once (keep it the same
	 * while the picture is the same; change it when it changes).
	 */
	public void image(int x, int y, int w, int h, String key, byte[] pixels, int width, int height) {
		if(pixels.length >= width * height && width > 0 && height > 0)
			images.add(new Image(x, y, w, h, key, pixels, width, height));
	}

	/** A picture filling a rectangle of cells. */
	public void imageInCells(int col, int row, int cols, int rows, String key, byte[] pixels, int width, int height) {
		image(col * CELL_W, row * CELL_H, cols * CELL_W, rows * CELL_H, key, pixels, width, height);
	}

	public List<Image> images() {
		return images;
	}

	/** True for the characters {@link DosRenderer} draws as lines rather than glyphs. */
	public static boolean isBoxDrawing(char c) {
		return c >= '─' && c <= '╿';
	}

	/** True for the shading and block characters. */
	public static boolean isBlock(char c) {
		return c >= '▀' && c <= '▟';
	}
}
