package com.arrl.radiocraft.common.radio.data;

import java.util.Map;

/**
 * The little 3 by 5 pixel font the APRS maps write callsigns in, and the radio computer's SSTV captions and charts
 * (1.12 step 7, moved here from {@link AprsMapPainter} so pure code can use it): capitals, digits, a few signs. Pure
 * (no game classes), so it is unit tested.
 */
public final class PixelFont {

	/** A glyph's size in pixels, and the step from one character to the next (a pixel between). */
	public static final int WIDTH = 3, HEIGHT = 5, ADVANCE = 4;

	private PixelFont() {}

	/** The glyph for {@code c} (any case), rows of '#' (lit) and '.'; a dash for a character the font lacks, blank for a space. */
	public static String[] glyph(char c) {
		if(c == ' ')
			return SPACE;
		return FONT.getOrDefault(Character.toUpperCase(c), FONT.get('-'));
	}

	/** True if the font has {@code c} (or it is a space). */
	public static boolean has(char c) {
		return c == ' ' || FONT.containsKey(Character.toUpperCase(c));
	}

	/** Pixels {@code text} takes across at {@code scale}, no gap after the last character. */
	public static int width(String text, int scale) {
		return text.isEmpty() ? 0 : (text.length() * ADVANCE - 1) * scale;
	}

	private static final String[] SPACE = { "...", "...", "...", "...", "..." };

	/** Capitals, digits and the signs callsigns and captions need. */
	private static final Map<Character, String[]> FONT = Map.ofEntries(
			Map.entry('A', new String[] { ".#.", "#.#", "###", "#.#", "#.#" }), Map.entry('B', new String[] { "##.", "#.#", "##.", "#.#", "##." }),
			Map.entry('C', new String[] { ".##", "#..", "#..", "#..", ".##" }), Map.entry('D', new String[] { "##.", "#.#", "#.#", "#.#", "##." }),
			Map.entry('E', new String[] { "###", "#..", "##.", "#..", "###" }), Map.entry('F', new String[] { "###", "#..", "##.", "#..", "#.." }),
			Map.entry('G', new String[] { ".##", "#..", "#.#", "#.#", ".##" }), Map.entry('H', new String[] { "#.#", "#.#", "###", "#.#", "#.#" }),
			Map.entry('I', new String[] { "###", ".#.", ".#.", ".#.", "###" }), Map.entry('J', new String[] { "..#", "..#", "..#", "#.#", ".#." }),
			Map.entry('K', new String[] { "#.#", "#.#", "##.", "#.#", "#.#" }), Map.entry('L', new String[] { "#..", "#..", "#..", "#..", "###" }),
			Map.entry('M', new String[] { "#.#", "###", "#.#", "#.#", "#.#" }), Map.entry('N', new String[] { "##.", "#.#", "#.#", "#.#", "#.#" }),
			Map.entry('O', new String[] { ".#.", "#.#", "#.#", "#.#", ".#." }), Map.entry('P', new String[] { "##.", "#.#", "##.", "#..", "#.." }),
			Map.entry('Q', new String[] { ".#.", "#.#", "#.#", "##.", ".##" }), Map.entry('R', new String[] { "##.", "#.#", "##.", "#.#", "#.#" }),
			Map.entry('S', new String[] { ".##", "#..", ".#.", "..#", "##." }), Map.entry('T', new String[] { "###", ".#.", ".#.", ".#.", ".#." }),
			Map.entry('U', new String[] { "#.#", "#.#", "#.#", "#.#", "###" }), Map.entry('V', new String[] { "#.#", "#.#", "#.#", "#.#", ".#." }),
			Map.entry('W', new String[] { "#.#", "#.#", "###", "###", "#.#" }), Map.entry('X', new String[] { "#.#", "#.#", ".#.", "#.#", "#.#" }),
			Map.entry('Y', new String[] { "#.#", "#.#", ".#.", ".#.", ".#." }), Map.entry('Z', new String[] { "###", "..#", ".#.", "#..", "###" }),
			Map.entry('0', new String[] { "###", "#.#", "#.#", "#.#", "###" }), Map.entry('1', new String[] { ".#.", "##.", ".#.", ".#.", "###" }),
			Map.entry('2', new String[] { "##.", "..#", ".#.", "#..", "###" }), Map.entry('3', new String[] { "##.", "..#", ".#.", "..#", "##." }),
			Map.entry('4', new String[] { "#.#", "#.#", "###", "..#", "..#" }), Map.entry('5', new String[] { "###", "#..", "##.", "..#", "##." }),
			Map.entry('6', new String[] { ".##", "#..", "###", "#.#", "###" }), Map.entry('7', new String[] { "###", "..#", ".#.", ".#.", ".#." }),
			Map.entry('8', new String[] { "###", "#.#", "###", "#.#", "###" }), Map.entry('9', new String[] { "###", "#.#", "###", "..#", "##." }),
			Map.entry('-', new String[] { "...", "...", "###", "...", "..." }), Map.entry('/', new String[] { "..#", "..#", ".#.", "#..", "#.." }),
			Map.entry('.', new String[] { "...", "...", "...", "...", ".#." }), Map.entry(',', new String[] { "...", "...", "...", ".#.", "#.." }),
			Map.entry('!', new String[] { ".#.", ".#.", ".#.", "...", ".#." }), Map.entry('?', new String[] { "##.", "..#", ".#.", "...", ".#." }),
			Map.entry(':', new String[] { "...", ".#.", "...", ".#.", "..." }), Map.entry('\'', new String[] { ".#.", ".#.", "...", "...", "..." }),
			Map.entry('+', new String[] { "...", ".#.", "###", ".#.", "..." }), Map.entry('=', new String[] { "...", "###", "...", "###", "..." }),
			Map.entry('(', new String[] { "..#", ".#.", ".#.", ".#.", "..#" }), Map.entry(')', new String[] { "#..", ".#.", ".#.", ".#.", "#.." }),
			Map.entry('#', new String[] { "#.#", "###", "#.#", "###", "#.#" }), Map.entry('%', new String[] { "#.#", "..#", ".#.", "#..", "#.#" }),
			Map.entry('&', new String[] { ".#.", "#.#", ".#.", "#.#", ".##" }), Map.entry('*', new String[] { "#.#", ".#.", "###", ".#.", "#.#" }),
			Map.entry('"', new String[] { "#.#", "#.#", "...", "...", "..." }), Map.entry('_', new String[] { "...", "...", "...", "...", "###" }));
}
