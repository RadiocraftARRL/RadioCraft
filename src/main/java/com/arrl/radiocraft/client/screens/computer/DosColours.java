package com.arrl.radiocraft.client.screens.computer;

/**
 * The sixteen CGA/EGA text-mode colours (0xRRGGBB), as a VGA card shows them in 80x25 colour text, and the radio
 * computer's semantic roles built from them ({@link #TITLE_FG}, {@link #BODY_BG}, {@link #TEXT_FG},
 * {@link #HIGHLIGHT_BG}, {@link #DIM}, {@link #ACCENT}, {@link #ERROR_BG} and so on). These are <em>logical</em> colours:
 * the computer's colour scheme ({@link DosScheme}, chosen in SETUP) decides what each looks like on the glass, so draw
 * with these and every scheme (green phosphor, amber...) works. Prefer a role where one fits. A program may use any
 * 0xRRGGBB colour (the band plan uses the plan's own): a colour scheme keeps it, a monochrome one shows its brightness.
 * Pictures ({@link TextGrid#image}) are not themed.
 */
public final class DosColours {

	public static final int BLACK = 0x000000, BLUE = 0x0000AA, GREEN = 0x00AA00, CYAN = 0x00AAAA, RED = 0xAA0000,
			MAGENTA = 0xAA00AA, BROWN = 0xAA5500, LIGHT_GREY = 0xAAAAAA, DARK_GREY = 0x555555, LIGHT_BLUE = 0x5555FF,
			LIGHT_GREEN = 0x55FF55, LIGHT_CYAN = 0x55FFFF, LIGHT_RED = 0xFF5555, LIGHT_MAGENTA = 0xFF55FF, YELLOW = 0xFFFF55,
			WHITE = 0xFFFFFF;

	/** All sixteen, in their CGA order (0 black to 15 white). */
	public static final int[] PALETTE = { BLACK, BLUE, GREEN, CYAN, RED, MAGENTA, BROWN, LIGHT_GREY, DARK_GREY, LIGHT_BLUE,
			LIGHT_GREEN, LIGHT_CYAN, LIGHT_RED, LIGHT_MAGENTA, YELLOW, WHITE };

	// The standard look (EDIT / DOS Shell): a grey bar on top, blue body, cyan key bar at the bottom.
	public static final int TITLE_FG = BLACK, TITLE_BG = LIGHT_GREY;
	public static final int BODY_FG = LIGHT_GREY, BODY_BG = BLUE;
	public static final int TEXT_FG = WHITE;
	public static final int HIGHLIGHT_FG = BLACK, HIGHLIGHT_BG = CYAN;
	public static final int KEYS_FG = BLACK, KEYS_BG = CYAN;
	public static final int DIALOG_FG = BLACK, DIALOG_BG = LIGHT_GREY;
	public static final int FIELD_FG = WHITE, FIELD_BG = BLACK;
	public static final int ERROR_FG = WHITE, ERROR_BG = RED;
	public static final int HOTKEY_FG = RED;
	/** Less important text on the body (hints, empty slots, "uncertain"). */
	public static final int DIM = DARK_GREY;
	/** Headings and what is selected or new, on the body. */
	public static final int ACCENT = YELLOW;
	/** Good news and bad news on the body ("online", "sent"; "no radio", "failed"). */
	public static final int GOOD = LIGHT_GREEN, BAD = LIGHT_RED;

	/**
	 * Marks a colour as already final (0x01RRGGBB): no scheme changes it. Only for showing a scheme's own colours
	 * (SETUP's swatches); programs should not need it.
	 */
	public static final int RAW = 0x01000000;

	/** {@code rgb}'s place among the sixteen (0 black to 15 white), or -1 if it is not one of them. */
	public static int index(int rgb) {
		rgb &= 0xFFFFFF;
		for(int i = 0; i < PALETTE.length; i++)
			if(PALETTE[i] == rgb)
				return i;
		return -1;
	}

	private DosColours() {}
}
