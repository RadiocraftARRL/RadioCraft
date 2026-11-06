package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.common.computer.ComputerSettings;

import java.util.List;

/**
 * A colour scheme for the radio computer's screen (1.12, SETUP): what each colour a program draws in looks like on the
 * glass. Programs draw in the sixteen DOS colours and the roles built from them ({@link DosColours}: body, text,
 * highlight, title bar, dim, accent, error...); {@link DosRenderer} turns every cell's colours into the scheme's with
 * {@link #display}, so every program follows the scheme without knowing it. Classic Blue shows them as a VGA card
 * did; colour schemes give the sixteen other colours; monochrome schemes (green, amber, white phosphor, gas plasma)
 * give each a brightness of their one phosphor colour, ordered so text stays readable on its background. Any other
 * 0xRRGGBB a program uses (the band plan's colours) is kept in a colour scheme and turned into a brightness by its
 * luminance in a monochrome one. A colour marked {@link DosColours#RAW} is never changed (SETUP's swatches).
 * Pictures ({@link TextGrid#image}) keep their own colours; icons ({@link TextGrid#icon}) follow the scheme.
 * <p>
 * Pure (no game classes), so it is unit tested. The scheme's id is what {@link ComputerSettings#scheme()} keeps.
 *
 * @param id      saved in the computer's settings
 * @param palette the sixteen DOS colours as this scheme shows them, in CGA order (0xRRGGBB)
 * @param phosphor the one colour of a monochrome scheme, or -1 for a colour scheme
 */
public record DosScheme(String id, int[] palette, int phosphor) {

	/**
	 * How bright each of the sixteen colours shows on a monochrome screen (0 dark to 1 brightest), in CGA order. Blue
	 * (the body) is just above black so input fields (black) still stand out; the dark colours (red, dark grey) stay
	 * well below the bars they sit on (cyan, light grey), the light ones well above the body.
	 */
	static final double[] MONO_LEVELS = { 0.0D, 0.08D, 0.42D, 0.62D, 0.28D, 0.40D, 0.48D, 0.70D, 0.34D, 0.60D, 0.85D,
			0.88D, 0.78D, 0.80D, 0.95D, 1.0D };

	public static final DosScheme CLASSIC = new DosScheme("classic", DosColours.PALETTE.clone(), -1);
	/** Classic, with a dark teal body and sea-green bars. */
	public static final DosScheme TEAL = colour("teal", new int[] { 0x000000, 0x005A5A, 0x00AA55, 0x4FBFAF, 0xAA0000,
			0xAA00AA, 0xAA7A2A, 0xB8C4C2, 0x6A8A88, 0x55D5FF, 0x55FF99, 0x8FFFEF, 0xFF6F5F, 0xFF55FF, 0xFFFF6F, 0xFFFFFF });
	/** Black body, white text, yellow bars and highlights, saturated colours. */
	public static final DosScheme HIGH_CONTRAST = colour("high_contrast", new int[] { 0x1E1E1E, 0x000000, 0x00E000,
			0xFFFF00, 0xD00000, 0xFF00FF, 0xFFA500, 0xFFFFFF, 0xA8A8A8, 0x80C0FF, 0x40FF40, 0x40FFFF, 0xFF6060, 0xFF80FF,
			0xFFFF40, 0xFFFFFF });
	public static final DosScheme GREEN = mono("green", 0x33FF66);
	public static final DosScheme AMBER = mono("amber", 0xFFB000);
	public static final DosScheme WHITE = mono("white", 0xE6EEF2);
	/** The orange of a gas plasma laptop screen. */
	public static final DosScheme PLASMA = mono("plasma", 0xFF5A1E);

	/** Every scheme, in SETUP's order. */
	public static final List<DosScheme> ALL = List.of(CLASSIC, TEAL, HIGH_CONTRAST, GREEN, AMBER, WHITE, PLASMA);

	public DosScheme {
		if(palette.length != 16)
			throw new IllegalArgumentException("A scheme has sixteen colours");
	}

	private static DosScheme colour(String id, int[] palette) {
		return new DosScheme(id, palette, -1);
	}

	private static DosScheme mono(String id, int phosphor) {
		int[] palette = new int[16];
		for(int i = 0; i < 16; i++)
			palette[i] = shade(phosphor, MONO_LEVELS[i]);
		return new DosScheme(id, palette, phosphor);
	}

	/** The scheme with {@code id}, or Classic Blue for an unknown one. */
	public static DosScheme byId(String id) {
		for(DosScheme scheme : ALL)
			if(scheme.id.equals(id))
				return scheme;
		return CLASSIC;
	}

	/** True for a one-colour (phosphor) scheme. */
	public boolean monochrome() {
		return phosphor >= 0;
	}

	/** Its name's lang key suffix, under {@code screen.radiocraft.computer.}. */
	public String nameKey() {
		return "setup.scheme." + id;
	}

	/** What {@code rgb} (a colour a program drew in) looks like in this scheme, 0xRRGGBB. */
	public int display(int rgb) {
		if((rgb & DosColours.RAW) != 0)
			return rgb & 0xFFFFFF;
		rgb &= 0xFFFFFF;
		int index = DosColours.index(rgb);
		if(index >= 0)
			return palette[index];
		if(!monochrome())
			return rgb;
		return shade(phosphor, luminance(rgb));
	}

	/** {@code rgb}'s brightness, 0 to 1 (Rec. 601 luma). */
	static double luminance(int rgb) {
		return (0.299D * (rgb >> 16 & 0xFF) + 0.587D * (rgb >> 8 & 0xFF) + 0.114D * (rgb & 0xFF)) / 255.0D;
	}

	/**
	 * The phosphor at brightness {@code level} (0 to 1): black at 0, rising to the phosphor's colour at 0.85, then
	 * blooming part way to white at 1, as the brightest text on a monochrome tube does.
	 */
	static int shade(int phosphor, double level) {
		level = Math.max(0.0D, Math.min(1.0D, level));
		int out = 0;
		for(int shift = 16; shift >= 0; shift -= 8) {
			double c = phosphor >> shift & 0xFF;
			double v = level <= 0.85D ? c * level / 0.85D : c + (255.0D - c) * (level - 0.85D) / 0.15D * 0.45D;
			out |= (int)Math.round(Math.max(0.0D, Math.min(255.0D, v))) << shift;
		}
		return out;
	}
}
