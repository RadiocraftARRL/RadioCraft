package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.common.radio.voice.RepeaterAudio;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The radio computer's screensavers (computer batch 3A), drawn into the 80 x 25 text screen, so the in-world monitor
 * shows them too. Each is RadioCraft's own:
 * <ul>
 * <li><b>Starfield</b> ({@code starfield}): stars streaming out from the middle of the screen, dim far away and bright
 * close by.</li>
 * <li><b>Callsign</b> ({@code marquee}): the station's callsign (or the computer's name) in big block letters, sliding
 * across the screen and changing colour on each pass.</li>
 * <li><b>Waterfall</b> ({@code waterfall}): a band scope's waterfall, the band noise falling down the screen with a few
 * stations drifting in it, one of them sending the callsign in Morse.</li>
 * </ul>
 * One per session ({@link ComputerSession}); it keeps its own animation state. Pure apart from {@link TextGrid}, so it
 * is unit tested. Add a style: a name in {@link #STYLES}, a case in {@link #render}, and a lang key
 * {@code setup.saver.style.<id>}.
 */
public final class Screensaver {

	/** The styles SETUP offers, by id (the first is the default). */
	public static final List<String> STYLES = List.of("starfield", "marquee", "waterfall");

	private static final int STARS = 90;
	/** How far the stars start, and how fast they come (depth units a second). */
	private static final double FAR = 8.0D, SPEED = 2.4D;
	private static final int[] RAINBOW = { DosColours.LIGHT_RED, DosColours.YELLOW, DosColours.LIGHT_GREEN, DosColours.LIGHT_CYAN,
			DosColours.LIGHT_BLUE, DosColours.LIGHT_MAGENTA, DosColours.WHITE };
	/** The waterfall moves a row every this many ms. */
	private static final long WATERFALL_ROW_MS = 160L;

	private final Random random = new Random(0x5EED_CAFEL);
	private final double[] starX = new double[STARS], starY = new double[STARS], starZ = new double[STARS];
	private long last = -1L;
	private boolean seeded = false;
	// Waterfall: the rows drawn so far (as chars and colours), newest at the top.
	private final char[][] fallChars = new char[TextGrid.ROWS][TextGrid.COLS];
	private final int[][] fallColours = new int[TextGrid.ROWS][TextGrid.COLS];
	private long fallRows = 0L;
	private long fallStart = -1L;
	private final double[] signalAt = { 14.0D, 37.0D, 58.0D, 70.0D };
	private final double[] signalDrift = { 0.02D, -0.015D, 0.0D, 0.03D };

	/** Starts over (when it comes on again). */
	public void reset() {
		last = -1L;
		seeded = false;
		fallRows = 0L;
		fallStart = -1L;
	}

	/**
	 * Draws style {@code style} (an id of {@link #STYLES}; anything else is the starfield) at time {@code now} (ms) with
	 * {@code text} (the callsign, or the computer's name) where it shows one.
	 */
	public void render(TextGrid g, long now, String style, String text) {
		String shown = text == null || text.isBlank() ? "RC-DOS" : text.trim().toUpperCase(Locale.ROOT);
		switch(style == null ? "" : style) {
			case "marquee" -> marquee(g, now, shown);
			case "waterfall" -> waterfall(g, now, shown);
			default -> starfield(g, now);
		}
	}

	// -------------------- STARFIELD --------------------

	private void star(int i, boolean anywhere) {
		starX[i] = random.nextDouble() * 2.0D - 1.0D;
		starY[i] = random.nextDouble() * 2.0D - 1.0D;
		starZ[i] = anywhere ? 0.2D + random.nextDouble() * (FAR - 0.2D) : FAR;
	}

	private void starfield(TextGrid g, long now) {
		g.clear(DosColours.BLACK, DosColours.BLACK);
		if(!seeded) {
			for(int i = 0; i < STARS; i++)
				star(i, true);
			seeded = true;
		}
		double dt = last < 0L ? 0.0D : Math.min(0.25D, (now - last) / 1000.0D);
		last = now;
		double cx = TextGrid.COLS / 2.0D, cy = TextGrid.ROWS / 2.0D;
		for(int i = 0; i < STARS; i++) {
			starZ[i] -= SPEED * dt;
			if(starZ[i] <= 0.15D) {
				star(i, false);
				continue;
			}
			// Cells are tall (6 x 10 pixels): spread the stars twice as far across as down.
			int col = (int)Math.round(cx + starX[i] / starZ[i] * cx * 1.6D);
			int row = (int)Math.round(cy + starY[i] / starZ[i] * cy * 0.9D);
			if(!TextGrid.inside(col, row)) {
				star(i, false);
				continue;
			}
			double near = 1.0D - starZ[i] / FAR;
			char c = near > 0.8D ? '*' : near > 0.55D ? '+' : '.';
			int colour = near > 0.7D ? DosColours.WHITE : near > 0.4D ? DosColours.LIGHT_GREY : DosColours.DARK_GREY;
			g.set(col, row, c, colour, DosColours.BLACK);
		}
	}

	// -------------------- CALLSIGN --------------------

	/** Block letters, 5 wide and 5 high ('#' is lit). */
	private static final String[] FONT_KEYS = { "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P",
			"Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "-", "/", " ", "." };
	private static final String[][] FONT = {
			{ " ### ", "#   #", "#####", "#   #", "#   #" }, { "#### ", "#   #", "#### ", "#   #", "#### " },
			{ " ####", "#    ", "#    ", "#    ", " ####" }, { "#### ", "#   #", "#   #", "#   #", "#### " },
			{ "#####", "#    ", "#### ", "#    ", "#####" }, { "#####", "#    ", "#### ", "#    ", "#    " },
			{ " ####", "#    ", "#  ##", "#   #", " ####" }, { "#   #", "#   #", "#####", "#   #", "#   #" },
			{ "#####", "  #  ", "  #  ", "  #  ", "#####" }, { "    #", "    #", "    #", "#   #", " ### " },
			{ "#   #", "#  # ", "###  ", "#  # ", "#   #" }, { "#    ", "#    ", "#    ", "#    ", "#####" },
			{ "#   #", "## ##", "# # #", "#   #", "#   #" }, { "#   #", "##  #", "# # #", "#  ##", "#   #" },
			{ " ### ", "#   #", "#   #", "#   #", " ### " }, { "#### ", "#   #", "#### ", "#    ", "#    " },
			{ " ### ", "#   #", "# # #", "#  # ", " ## #" }, { "#### ", "#   #", "#### ", "#  # ", "#   #" },
			{ " ####", "#    ", " ### ", "    #", "#### " }, { "#####", "  #  ", "  #  ", "  #  ", "  #  " },
			{ "#   #", "#   #", "#   #", "#   #", " ### " }, { "#   #", "#   #", "#   #", " # # ", "  #  " },
			{ "#   #", "#   #", "# # #", "## ##", "#   #" }, { "#   #", " # # ", "  #  ", " # # ", "#   #" },
			{ "#   #", " # # ", "  #  ", "  #  ", "  #  " }, { "#####", "   # ", "  #  ", " #   ", "#####" },
			{ " ### ", "#  ##", "# # #", "##  #", " ### " }, { "  #  ", " ##  ", "  #  ", "  #  ", " ### " },
			{ "#### ", "    #", " ### ", "#    ", "#####" }, { "#### ", "    #", " ### ", "    #", "#### " },
			{ "#   #", "#   #", "#####", "    #", "    #" }, { "#####", "#    ", "#### ", "    #", "#### " },
			{ " ### ", "#    ", "#### ", "#   #", " ### " }, { "#####", "    #", "   # ", "  #  ", "  #  " },
			{ " ### ", "#   #", " ### ", "#   #", " ### " }, { " ### ", "#   #", " ####", "    #", " ### " },
			{ "     ", "     ", "#####", "     ", "     " }, { "    #", "   # ", "  #  ", " #   ", "#    " },
			{ "     ", "     ", "     ", "     ", "     " }, { "     ", "     ", "     ", "     ", "  #  " } };
	/** Each letter's width with the gap after it, in cells. */
	private static final int GLYPH_STEP = 6;
	private static final int GLYPH_ROWS = 5;

	/** The block letters of {@code c} ('?' for none known: drawn as a space). */
	static String[] glyph(char c) {
		String key = String.valueOf(Character.toUpperCase(c));
		for(int i = 0; i < FONT_KEYS.length; i++)
			if(FONT_KEYS[i].equals(key))
				return FONT[i];
		return FONT[FONT_KEYS.length - 2];
	}

	private void marquee(TextGrid g, long now, String text) {
		g.clear(DosColours.BLACK, DosColours.BLACK);
		String word = text.length() > 12 ? text.substring(0, 12) : text;
		int width = word.length() * GLYPH_STEP;
		long span = TextGrid.COLS + width;
		long step = now / 90L; // A cell every 90 ms.
		int pass = (int)(step / span);
		int left = TextGrid.COLS - (int)(step % span);
		// Each pass on its own row, so it wanders over the screen.
		int top = 2 + Math.floorMod(pass * 7, TextGrid.ROWS - GLYPH_ROWS - 4);
		int colour = RAINBOW[Math.floorMod(pass, RAINBOW.length)];
		for(int n = 0; n < word.length(); n++) {
			String[] rows = glyph(word.charAt(n));
			for(int r = 0; r < GLYPH_ROWS; r++)
				for(int k = 0; k < 5; k++)
					if(rows[r].charAt(k) == '#') {
						int col = left + n * GLYPH_STEP + k;
						if(TextGrid.inside(col, top + r))
							g.set(col, top + r, TextGrid.FULL, colour, DosColours.BLACK);
					}
		}
		g.center(TextGrid.ROWS - 1, "RC-DOS", DosColours.DARK_GREY, DosColours.BLACK);
	}

	// -------------------- WATERFALL --------------------

	private void waterfall(TextGrid g, long now, String text) {
		if(fallStart < 0L) {
			fallStart = now;
			for(char[] row : fallChars)
				java.util.Arrays.fill(row, ' ');
			for(int[] row : fallColours)
				java.util.Arrays.fill(row, DosColours.BLUE);
		}
		long due = (now - fallStart) / WATERFALL_ROW_MS;
		List<Boolean> morse = RepeaterAudio.morseUnits("CQ DE " + text + " ");
		for(int n = 0; fallRows < due && n < TextGrid.ROWS - 1; n++) { // Catch up, at most a screen's worth.
			fallRows++;
			for(int r = TextGrid.ROWS - 2; r > 0; r--) {
				System.arraycopy(fallChars[r - 1], 0, fallChars[r], 0, TextGrid.COLS);
				System.arraycopy(fallColours[r - 1], 0, fallColours[r], 0, TextGrid.COLS);
			}
			newRow(morse);
		}
		g.clear(DosColours.BLACK, DosColours.BLACK);
		for(int r = 0; r < TextGrid.ROWS - 1; r++)
			for(int c = 0; c < TextGrid.COLS; c++)
				g.set(c, r, fallChars[r][c], fallColours[r][c], DosColours.BLACK);
		// A frequency scale along the bottom.
		for(int c = 0; c < TextGrid.COLS; c++)
			g.set(c, TextGrid.ROWS - 1, c % 10 == 0 ? '|' : '-', DosColours.DARK_GREY, DosColours.BLACK);
		g.put(1, TextGrid.ROWS - 1, " 14.000 ", DosColours.CYAN, DosColours.BLACK);
		g.put(TextGrid.COLS - 9, TextGrid.ROWS - 1, " 14.350 ", DosColours.CYAN, DosColours.BLACK);
	}

	/** The newest row at the top: band noise, the drifting stations, and the Morse one. */
	private void newRow(List<Boolean> morse) {
		char[] chars = fallChars[0];
		int[] colours = fallColours[0];
		for(int c = 0; c < TextGrid.COLS; c++) {
			double noise = random.nextDouble();
			chars[c] = noise > 0.93D ? TextGrid.SHADE_LIGHT : noise > 0.7D ? '.' : ' ';
			colours[c] = noise > 0.93D ? DosColours.BLUE : DosColours.DARK_GREY;
		}
		for(int s = 0; s < signalAt.length; s++) {
			signalAt[s] += signalDrift[s] + (random.nextDouble() - 0.5D) * 0.1D;
			if(signalAt[s] < 2.0D || signalAt[s] > TextGrid.COLS - 3.0D)
				signalDrift[s] = -signalDrift[s];
			signalAt[s] = Math.max(2.0D, Math.min(TextGrid.COLS - 3.0D, signalAt[s]));
			int centre = (int)Math.round(signalAt[s]);
			boolean keyed = s != 2 || morse.isEmpty() || morse.get((int)(fallRows % morse.size()));
			if(!keyed)
				continue;
			boolean wide = s == 1; // A voice signal, three cells wide; the others narrow.
			for(int d = wide ? -2 : -1; d <= (wide ? 2 : 1); d++) {
				int c = centre + d;
				boolean core = d == 0 || wide && Math.abs(d) == 1;
				chars[c] = core ? TextGrid.FULL : TextGrid.SHADE_MEDIUM;
				colours[c] = core ? s == 2 ? DosColours.WHITE : DosColours.YELLOW : DosColours.LIGHT_RED;
			}
		}
	}
}
