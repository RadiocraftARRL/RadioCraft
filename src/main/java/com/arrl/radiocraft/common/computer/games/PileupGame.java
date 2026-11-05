package com.arrl.radiocraft.common.computer.games;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * PILEUP for the radio computer (1.12 step 8), RadioCraft's own falling-blocks game. In ham radio a pileup is a crowd of
 * stations all calling one rare station at once; here the callers fall into the band as pieces, and every row filled
 * edge to edge is a contact worked ("QSO") and leaves the band. Clear enough and the game moves up a band (160m to
 * 70cm, faster each time). The pile reaching the top ends it.
 * <p>
 * Its own rules, not another game's: a board {@link #WIDTH} wide and {@link #HEIGHT} high; ten pieces of three to five
 * cells (two of three, five of four, three of five: {@link #SHAPES}) dealt from a shuffled bag; rotation with simple
 * kicks; points for contacts by how many at once times the band number, plus a point a row for a soft drop and two for
 * a hard drop. Pure (no game classes), so it is unit tested; the screen ({@code client.screens.computer.apps.PileupApp})
 * calls {@link #tick} every client tick and draws {@link #cell}, {@link #piece} and {@link #next}.
 */
public final class PileupGame {

	public static final int WIDTH = 12, HEIGHT = 20;
	/** Contacts to work before moving up a band. */
	public static final int QSOS_PER_BAND = 8;
	/** The bands, one per level, from the slowest. */
	public static final String[] BANDS = { "160m", "80m", "40m", "30m", "20m", "17m", "15m", "12m", "10m", "6m", "2m", "70cm" };
	/** Points for one, two and three contacts at once (a piece is at most three rows tall), times the band number. */
	private static final int[] CLEAR_POINTS = { 0, 100, 250, 450 };

	/** One kind of piece: its cells in a {@code size} by {@code size} box (x, y pairs), as dealt (not turned). */
	public record Shape(String name, int size, int[] cells, int colour) {
		public int count() {
			return cells.length / 2;
		}
	}

	/** The ten pieces. Colours are CGA (0xRRGGBB) so the board looks like DOS. */
	public static final List<Shape> SHAPES = List.of(
			new Shape("dit", 3, new int[] { 0, 1, 1, 1, 2, 1 }, 0x55FFFF),                  // a row of three
			new Shape("hook", 2, new int[] { 0, 0, 0, 1, 1, 1 }, 0xAAAAAA),                 // three in a corner
			new Shape("box", 2, new int[] { 0, 0, 1, 0, 0, 1, 1, 1 }, 0xFFFF55),            // two by two
			new Shape("tee", 3, new int[] { 0, 1, 1, 1, 2, 1, 1, 0 }, 0xFF55FF),            // T
			new Shape("ell", 3, new int[] { 0, 1, 1, 1, 2, 1, 2, 0 }, 0xAA5500),            // L
			new Shape("zed", 3, new int[] { 0, 0, 1, 0, 1, 1, 2, 1 }, 0xFF5555),            // Z
			new Shape("ess", 3, new int[] { 1, 0, 2, 0, 0, 1, 1, 1 }, 0x55FF55),            // S
			new Shape("flag", 3, new int[] { 0, 0, 1, 0, 0, 1, 1, 1, 0, 2 }, 0x5555FF),     // P: a box with a tail
			new Shape("cup", 3, new int[] { 0, 0, 2, 0, 0, 1, 1, 1, 2, 1 }, 0x00AAAA),      // U
			new Shape("cross", 3, new int[] { 1, 0, 0, 1, 1, 1, 2, 1, 1, 2 }, 0xFFFFFF));   // +

	/** A piece in play: its shape, turn (0-3) and where its box's top left is. */
	public record Piece(Shape shape, int turn, int x, int y) {
		/** Its cells as (x, y) pairs on the board. */
		public int[] cells() {
			int n = shape.size(), count = shape.count();
			int[] out = new int[count * 2];
			for(int i = 0; i < count; i++) {
				int cx = shape.cells()[i * 2], cy = shape.cells()[i * 2 + 1];
				for(int t = 0; t < turn; t++) { // A quarter turn clockwise in the box.
					int nx = n - 1 - cy;
					cy = cx;
					cx = nx;
				}
				out[i * 2] = x + cx;
				out[i * 2 + 1] = y + cy;
			}
			return out;
		}

		Piece moved(int dx, int dy) {
			return new Piece(shape, turn, x + dx, y + dy);
		}

		Piece turned(int by) {
			return new Piece(shape, Math.floorMod(turn + by, 4), x, y);
		}
	}

	private final Random random;
	/** The settled cells: 0 empty, else the colour (0xRRGGBB | 0x1000000 so black would show). */
	private final int[] board = new int[WIDTH * HEIGHT];
	private final List<Shape> bag = new ArrayList<>();
	private Piece piece;
	private Shape next;
	private int score, qsos, band;
	private int fallTicks;
	private boolean over;
	/** Rows just cleared, for the screen's flash; reset by {@link #takeCleared}. */
	private int cleared;

	public PileupGame(long seed) {
		this.random = new Random(seed);
		this.next = deal();
		spawn();
	}

	// -------------------- what the screen reads --------------------

	/** The settled cell's colour (0xRRGGBB), or -1 if empty. */
	public int cell(int x, int y) {
		int v = board[y * WIDTH + x];
		return v == 0 ? -1 : v & 0xFFFFFF;
	}

	public Piece piece() {
		return piece;
	}

	public Shape next() {
		return next;
	}

	/** Where the piece would land if dropped now (same shape and turn). */
	public Piece ghost() {
		Piece p = piece;
		while(fits(p.moved(0, 1)))
			p = p.moved(0, 1);
		return p;
	}

	public int score() {
		return score;
	}

	/** Contacts worked (rows cleared). */
	public int qsos() {
		return qsos;
	}

	/** The band (level), 0 = 160m. */
	public int band() {
		return band;
	}

	public String bandName() {
		return BANDS[Math.min(band, BANDS.length - 1)];
	}

	public boolean over() {
		return over;
	}

	/** Rows cleared since last asked (the screen flashes when it is not 0). */
	public int takeCleared() {
		int c = cleared;
		cleared = 0;
		return c;
	}

	/** Client ticks between two rows of fall on the band now: 24 on 160m, faster each band, never under 2. */
	public int fallInterval() {
		return Math.max(2, 24 - band * 2);
	}

	// -------------------- moves --------------------

	public boolean left() {
		return shift(-1);
	}

	public boolean right() {
		return shift(1);
	}

	private boolean shift(int dx) {
		if(over)
			return false;
		Piece moved = piece.moved(dx, 0);
		if(!fits(moved))
			return false;
		piece = moved;
		return true;
	}

	/** Turns the piece a quarter clockwise ({@code by} 1) or back ({@code -1}), nudging it off a wall or the pile if it must. */
	public boolean rotate(int by) {
		if(over)
			return false;
		Piece turned = piece.turned(by);
		for(int[] kick : new int[][] { { 0, 0 }, { -1, 0 }, { 1, 0 }, { -2, 0 }, { 2, 0 }, { 0, -1 } }) {
			Piece tried = turned.moved(kick[0], kick[1]);
			if(fits(tried)) {
				piece = tried;
				return true;
			}
		}
		return false;
	}

	/** One row down now (a point); lands the piece if it cannot go. */
	public void softDrop() {
		if(over)
			return;
		if(fits(piece.moved(0, 1))) {
			piece = piece.moved(0, 1);
			score++;
			fallTicks = 0;
		}
		else
			land();
	}

	/** Straight down and lands (two points a row). */
	public void hardDrop() {
		if(over)
			return;
		int rows = 0;
		while(fits(piece.moved(0, 1))) {
			piece = piece.moved(0, 1);
			rows++;
		}
		score += rows * 2;
		land();
	}

	/** A client tick: the piece falls a row every {@link #fallInterval} ticks. */
	public void tick() {
		if(over)
			return;
		if(++fallTicks < fallInterval())
			return;
		fallTicks = 0;
		if(fits(piece.moved(0, 1)))
			piece = piece.moved(0, 1);
		else
			land();
	}

	// -------------------- rules --------------------

	/** True if {@code p} is on the board (it may stick out above the top) and on no settled cell. */
	boolean fits(Piece p) {
		int[] cells = p.cells();
		for(int i = 0; i < cells.length; i += 2) {
			int x = cells[i], y = cells[i + 1];
			if(x < 0 || x >= WIDTH || y >= HEIGHT)
				return false;
			if(y >= 0 && board[y * WIDTH + x] != 0)
				return false;
		}
		return true;
	}

	private void land() {
		int[] cells = piece.cells();
		boolean above = false;
		for(int i = 0; i < cells.length; i += 2) {
			int x = cells[i], y = cells[i + 1];
			if(y < 0)
				above = true;
			else
				board[y * WIDTH + x] = piece.shape().colour() | 0x1000000;
		}
		int rows = clearRows();
		if(rows > 0) {
			qsos += rows;
			cleared += rows;
			score += CLEAR_POINTS[Math.min(rows, CLEAR_POINTS.length - 1)] * (band + 1);
			band = Math.min(BANDS.length - 1, qsos / QSOS_PER_BAND);
		}
		if(above && rows == 0) {
			over = true; // Landed sticking out of the top: the band is full.
			return;
		}
		spawn();
	}

	/** Takes out every full row, the ones above coming down. Returns how many. */
	private int clearRows() {
		int rows = 0;
		for(int y = HEIGHT - 1; y >= 0; y--) {
			boolean full = true;
			for(int x = 0; x < WIDTH && full; x++)
				full = board[y * WIDTH + x] != 0;
			if(!full)
				continue;
			rows++;
			System.arraycopy(board, 0, board, WIDTH, y * WIDTH);
			Arrays.fill(board, 0, WIDTH, 0);
			y++; // Look at this row again: the one above has come down into it.
		}
		return rows;
	}

	/** The next piece comes in at the top, centred; the game is over if it does not fit. */
	private void spawn() {
		Shape shape = next;
		next = deal();
		piece = new Piece(shape, 0, (WIDTH - shape.size()) / 2, shape.size() == 2 ? 0 : -1);
		fallTicks = 0;
		if(!fits(piece))
			over = true;
	}

	/** The next shape from the bag (all ten, shuffled; a new bag when it is empty). */
	private Shape deal() {
		if(bag.isEmpty()) {
			bag.addAll(SHAPES);
			Collections.shuffle(bag, random);
		}
		return bag.removeLast();
	}

	// -------------------- for tests --------------------

	/** Fills row {@code y} but for column {@code gap} (-1 for none). */
	void fillRow(int y, int gap) {
		for(int x = 0; x < WIDTH; x++)
			board[y * WIDTH + x] = x == gap ? 0 : 0x1AAAAAA;
	}

	void clearBoard() {
		Arrays.fill(board, 0);
	}

	void setPiece(Piece p) {
		piece = p;
	}
}
