package com.arrl.radiocraft.common.radio.ft8;

import net.minecraft.core.BlockPos;

import java.util.Locale;

/**
 * FT8 (computer batch 3B): the weak-signal digital mode, simplified. Every station transmits in 15-second slots, even
 * and odd slots taking turns, a short structured message (a CQ, a reply with a grid square, a signal report, R plus a
 * report, RR73, 73; see {@link Ft8Message}) on the band's FT8 dial frequency, each station at its own audio offset in
 * the 3 kHz passband. A message decodes far below where voice and RTTY copy ({@link #DECODE_FLOOR_DB}). Pure: the slot
 * clock, the thresholds, the dial frequencies and the grid squares, unit tested.
 * <p>
 * <b>Slots are game time</b>, {@link #SLOT_TICKS} (300 ticks, 15 s at 20 TPS) from game tick 0, not the real clock: every
 * station on a server shares the game clock, so they stay in step even when the server lags (a lagging slot is just
 * longer in real seconds for everyone), and GameTests are repeatable. Even slots start at multiples of 600 ticks.
 */
public final class Ft8 {

	private Ft8() {}

	/** One slot, in ticks: 15 seconds. */
	public static final int SLOT_TICKS = 300;
	/** Ticks the transmission takes: 79 symbols of 0.16 s = 12.64 s. */
	public static final int TX_TICKS = 253;
	/** Tick in the slot at which the slot's decodes are made (13.5 s in, after the last transmission ended). */
	public static final int DECODE_AT = 270;
	/** Symbols in a transmission, of which {@link #COSTAS} (7 tones) at the start, middle and end. */
	public static final int SYMBOLS = 79;
	/** Seconds per symbol; tones are {@link #TONE_SPACING_HZ} apart. */
	public static final double SYMBOL_SECONDS = 0.16D;
	public static final double TONE_SPACING_HZ = 6.25D;
	/** The Costas sync array, sent at symbols 0, 36 and 72. */
	public static final int[] COSTAS = { 3, 1, 4, 0, 6, 5, 2 };
	/** Audio offsets a station may transmit at, Hz above the dial frequency (USB). */
	public static final int MIN_OFFSET_HZ = 200, MAX_OFFSET_HZ = 2800;
	/** Two signals in one slot closer than this in audio offset collide. */
	public static final int COLLISION_HZ = 50;

	/**
	 * How far below the copy threshold voice and RTTY use ({@code RadioBlockEntity#copyThreshold}, the same SNR model)
	 * FT8 still decodes: 12 dB, so at a sixteenth of the signal. RTTY prints garbled down to -3 dB and is lost below.
	 */
	public static final double DECODE_FLOOR_DB = -12.0D;
	/** {@link #DECODE_FLOOR_DB} as a share of the copy threshold. */
	public static final double DECODE_SHARE = Math.pow(10.0D, DECODE_FLOOR_DB / 10.0D);
	/**
	 * The SNR shown (dB in 2500 Hz, as WSJT-X reports it) for a signal right at the copy threshold; so the weakest FT8
	 * decode shows -20 dB, as weak as real FT8 decodes go (about -21).
	 */
	public static final int COPY_THRESHOLD_SNR = -8;
	public static final int MIN_SNR = -24, MAX_SNR = 30;

	/** True if a signal at {@code quality} (its strength over the copy threshold) decodes as FT8. */
	public static boolean decodes(double quality) {
		return quality > 0.0D && quality >= DECODE_SHARE;
	}

	/** The SNR report for a signal at {@code quality} (strength over the copy threshold), -24 to +30 dB. */
	public static int snr(double quality) {
		if(!(quality > 0.0D))
			return MIN_SNR;
		long db = Math.round(10.0D * Math.log10(quality)) + COPY_THRESHOLD_SNR;
		return (int)Math.max(MIN_SNR, Math.min(MAX_SNR, db));
	}

	// -------------------- SLOTS --------------------

	/** The slot {@code gameTime} is in. */
	public static long slot(long gameTime) {
		return Math.floorDiv(gameTime, SLOT_TICKS);
	}

	/** Ticks into its slot. */
	public static int tickInSlot(long gameTime) {
		return Math.floorMod(gameTime, SLOT_TICKS);
	}

	/** Game tick at which {@code slot} starts. */
	public static long slotStart(long slot) {
		return slot * SLOT_TICKS;
	}

	/** True for an even slot (the first of each pair, "00" and "30" seconds). */
	public static boolean isEven(long slot) {
		return Math.floorMod(slot, 2L) == 0L;
	}

	/** Ticks from {@code gameTime} to the start of the next slot of parity {@code even}. */
	public static long ticksToNext(long gameTime, boolean even) {
		long next = slot(gameTime) + 1;
		if(isEven(next) != even)
			next++;
		return slotStart(next) - gameTime;
	}

	/**
	 * The station clock for a slot: the game's running time (ticks / 20) as HHMMSS, wrapped at 24 hours, so slots read
	 * 000000, 000015, 000030... as in WSJT-X's UTC column.
	 */
	public static String clock(long slot) {
		long seconds = Math.floorMod(slotStart(slot) / 20L, 86_400L);
		return String.format(Locale.ROOT, "%02d%02d%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
	}

	// -------------------- FREQUENCIES --------------------

	/** The FT8 dial frequencies, Hz, lowest first: 80m, 40m, 20m, 10m and 2m (USB dial). */
	public static final float[] DIAL_HZ = { 3_573_000.0F, 7_074_000.0F, 14_074_000.0F, 28_074_000.0F, 144_174_000.0F };

	/** The FT8 dial frequency in the band from {@code lowHz} to {@code highHz}, or 0 if it has none. */
	public static float dialIn(double lowHz, double highHz) {
		for(float hz : DIAL_HZ)
			if(hz >= lowHz && hz <= highHz)
				return hz;
		return 0.0F;
	}

	/** True if {@code hz} is on an FT8 dial frequency (within 500 Hz). */
	public static boolean isDial(double hz) {
		for(float dial : DIAL_HZ)
			if(Math.abs(dial - hz) <= 500.0D)
				return true;
		return false;
	}

	/** {@code hz} clamped into the offsets a station transmits at. */
	public static int clampOffset(int hz) {
		return Math.max(MIN_OFFSET_HZ, Math.min(MAX_OFFSET_HZ, hz));
	}

	// -------------------- GRID SQUARES --------------------

	/** Blocks per grid square (a field is ten squares). */
	public static final int SQUARE_BLOCKS = 256;

	/**
	 * The four-character Maidenhead-style locator of a block, as FT8 sends it: the world is cut into squares of
	 * {@link #SQUARE_BLOCKS}, east along x and north along -z, with the world's centre at the corner of JJ00 (the real
	 * grid's 0 degrees, 0 degrees). Fields A-R, squares 0-9; beyond the 180 x 180 squares (23,040 blocks) it stays on
	 * the edge.
	 */
	public static String grid(BlockPos pos) {
		return grid(pos.getX(), pos.getZ());
	}

	public static String grid(int x, int z) {
		int col = clamp(Math.floorDiv(x, SQUARE_BLOCKS) + 90, 0, 179);
		int row = clamp(Math.floorDiv(-z - 1, SQUARE_BLOCKS) + 90, 0, 179); // North is -z.
		return "" + (char)('A' + col / 10) + (char)('A' + row / 10) + (char)('0' + col % 10) + (char)('0' + row % 10);
	}

	/** True if {@code text} is a four-character locator (AA00 to RR99); RR73 never is, as in WSJT-X. */
	public static boolean isGrid(String text) {
		return text.length() == 4 && !text.equals("RR73") && text.charAt(0) >= 'A' && text.charAt(0) <= 'R' && text.charAt(1) >= 'A' && text.charAt(1) <= 'R'
				&& Character.isDigit(text.charAt(2)) && Character.isDigit(text.charAt(3));
	}

	/** The block {x, z} at the centre of a locator ({@link #grid} of it is the locator again), or null if it is not one. */
	@org.jetbrains.annotations.Nullable
	public static int[] gridCentre(String grid) {
		if(grid == null || !isGrid(grid))
			return null;
		int col = squareCol(grid) - 90, row = squareRow(grid) - 90;
		return new int[] { col * SQUARE_BLOCKS + SQUARE_BLOCKS / 2, -(row * SQUARE_BLOCKS + SQUARE_BLOCKS / 2) - 1 };
	}

	/** Blocks between the centres of two locators, or -1 if either is not one. */
	public static int gridDistance(String a, String b) {
		if(!isGrid(a) || !isGrid(b))
			return -1;
		double dx = (squareCol(a) - squareCol(b)) * (double)SQUARE_BLOCKS;
		double dz = (squareRow(a) - squareRow(b)) * (double)SQUARE_BLOCKS;
		return (int)Math.round(Math.sqrt(dx * dx + dz * dz));
	}

	private static int squareCol(String grid) {
		return (grid.charAt(0) - 'A') * 10 + (grid.charAt(2) - '0');
	}

	private static int squareRow(String grid) {
		return (grid.charAt(1) - 'A') * 10 + (grid.charAt(3) - '0');
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
