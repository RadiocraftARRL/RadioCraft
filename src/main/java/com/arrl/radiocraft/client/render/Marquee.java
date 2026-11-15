package com.arrl.radiocraft.client.render;

import net.minecraft.client.gui.Font;

/**
 * A line of text running across a small radio display from right to left and round again, a character at a time as a
 * segment or dot LCD steps it (2026-11-11: the commercial VHF radios' "DISTRESS SOS RX x, y, z", on the mobile's
 * in-world display and screen and on the handheld's). Stepping whole characters needs no clipping, so the same window
 * works on a block's display in the world and on a GUI. Client side, from the wall clock, so it runs at the same pace
 * whatever the tick rate.
 */
public final class Marquee {

	private Marquee() {}

	/** How long each character stays before the text steps on one, ms. */
	public static final long STEP_MS = 220L;
	/** What runs between the end of the text and its start coming round again. */
	private static final String GAP = "   ";

	/**
	 * The part of {@code text} to draw now, at most {@code widthPx} wide in {@code font} (at the scale it will be drawn
	 * at): the text started {@code millis / STEP_MS} characters along, wrapping round through a gap. It always runs,
	 * even when it would fit, so a distress alert catches the eye.
	 */
	public static String window(Font font, String text, int widthPx, long millis) {
		if(text.isEmpty() || widthPx <= 0)
			return "";
		String loop = text + GAP;
		int offset = (int)((Math.max(0L, millis) / STEP_MS) % loop.length());
		String rotated = loop.substring(offset) + loop.substring(0, offset);
		return font.plainSubstrByWidth(rotated + rotated, widthPx);
	}
}
