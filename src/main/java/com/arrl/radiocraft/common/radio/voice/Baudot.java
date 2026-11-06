package com.arrl.radiocraft.common.radio.voice;

import java.util.random.RandomGenerator;

/**
 * The five-bit ITA2 code as American teleprinters use it (US TTY figures), which is what amateur RTTY sends: 32 codes,
 * each meaning a letter or a figure depending on the shift the machine is in. LTRS and FIGS change the shift; the
 * space, carriage return and line feed mean the same in both. Receivers here "unshift on space" (USOS) as most
 * amateur software does: a space puts them back in letters. Pure, so it is unit tested.
 * <p>
 * Codes are the five data bits as an int, bit 0 sent first (after the start bit).
 */
public final class Baudot {

	private Baudot() {}

	public static final int NUL = 0, LF = 2, SPACE = 4, CR = 8, FIGS = 27, LTRS = 31;

	/** What each code prints in letters shift; 0 for the codes that print nothing (NUL, LF, CR, FIGS, LTRS). */
	private static final char[] LETTERS = {
			0, 'E', 0, 'A', ' ', 'S', 'I', 'U', 0, 'D', 'R', 'J', 'N', 'F', 'C', 'K',
			'T', 'Z', 'L', 'W', 'H', 'Y', 'P', 'Q', 'O', 'B', 'G', 0, 'M', 'X', 'V', 0 };
	/** What each code prints in figures shift (US TTY: code 5 rings the bell, which prints nothing). */
	private static final char[] FIGURES = {
			0, '3', 0, '-', ' ', 0, '8', '7', 0, '$', '4', '\'', ',', '!', ':', '(',
			'5', '"', ')', '2', '#', '6', '0', '1', '9', '?', '&', 0, '.', '/', ';', 0 };

	/** The code that prints {@code c} in letters shift, or -1. */
	public static int letterCode(char c) {
		return indexOf(LETTERS, c);
	}

	/** The code that prints {@code c} in figures shift, or -1 (a space counts as a letter). */
	public static int figureCode(char c) {
		return c == ' ' ? -1 : indexOf(FIGURES, c);
	}

	/** True if a US teleprinter can send {@code c} (upper case letters, figures, its punctuation, space, newline). */
	public static boolean canSend(char c) {
		return c == '\n' || letterCode(c) >= 0 || figureCode(c) >= 0;
	}

	private static int indexOf(char[] table, char c) {
		if(c == 0)
			return -1;
		for(int i = 0; i < table.length; i++)
			if(table[i] == c)
				return i;
		return -1;
	}

	/**
	 * {@code text} as the codes a teleprinter sends, starting in letters shift: a FIGS before the first figure, a
	 * LTRS before the first letter after figures (and after a space, since the far end unshifts on space), CR LF for
	 * a newline. Characters it cannot send are left out.
	 */
	public static int[] encode(String text) {
		int[] out = new int[text.length() * 2 + 2];
		int n = 0;
		boolean figures = false;
		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if(c == '\n') {
				out = room(out, n, 2);
				out[n++] = CR;
				out[n++] = LF;
				continue;
			}
			int letter = letterCode(c), figure = figureCode(c);
			if(letter < 0 && figure < 0)
				continue;
			out = room(out, n, 2);
			if(c == ' ') {
				out[n++] = SPACE;
				figures = false; // The far end unshifts on space; so do we.
			}
			else if(letter >= 0) {
				if(figures)
					out[n++] = LTRS;
				figures = false;
				out[n++] = letter;
			}
			else {
				if(!figures)
					out[n++] = FIGS;
				figures = true;
				out[n++] = figure;
			}
		}
		return java.util.Arrays.copyOf(out, n);
	}

	private static int[] room(int[] out, int n, int more) {
		return n + more <= out.length ? out : java.util.Arrays.copyOf(out, (n + more) * 2);
	}

	/**
	 * What a receiver prints for {@code codes}, starting in letters shift: FIGS and LTRS shift, a space unshifts
	 * (USOS), CR prints nothing, LF ends the line, NUL and the bell print nothing.
	 */
	public static String decode(int[] codes) {
		StringBuilder out = new StringBuilder();
		boolean figures = false;
		for(int raw : codes) {
			int code = raw & 31;
			switch(code) {
				case FIGS -> figures = true;
				case LTRS -> figures = false;
				case LF -> out.append('\n');
				case CR, NUL -> {}
				case SPACE -> {
					out.append(' ');
					figures = false;
				}
				default -> {
					char c = (figures ? FIGURES : LETTERS)[code];
					if(c != 0)
						out.append(c);
				}
			}
		}
		return out.toString();
	}

	/**
	 * What a receiver prints instead of {@code c} when one of its five bits is lost in the noise: another character of
	 * the same shift one bit away (E for A, T for O), or, when that bit turned it into a shift or a control code, the
	 * same key in the other shift (the classic "letters printed as figures" of a missed shift). Always a printable
	 * character, never a newline, so a garbled line keeps its length and breaks where it should.
	 */
	public static char misprint(char c, RandomGenerator random) {
		int letter = letterCode(c), figure = figureCode(c);
		boolean figures = letter < 0 && figure >= 0;
		int code = figures ? figure : letter;
		if(code < 0) // Not a teleprinter character: any letter.
			return LETTERS[pickPrintable(LETTERS, random)];
		int flipped = code ^ (1 << random.nextInt(5));
		char same = (figures ? FIGURES : LETTERS)[flipped];
		if(same != 0)
			return same;
		char other = (figures ? LETTERS : FIGURES)[code];
		if(other != 0 && other != c)
			return other;
		return LETTERS[pickPrintable(LETTERS, random)];
	}

	private static int pickPrintable(char[] table, RandomGenerator random) {
		while(true) {
			int i = random.nextInt(table.length);
			if(table[i] != 0 && table[i] != ' ')
				return i;
		}
	}
}
