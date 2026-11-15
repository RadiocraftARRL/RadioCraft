package com.arrl.radiocraft.common.radio.firealarm;

import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.photo.PhotoPalette;
import com.arrl.radiocraft.common.radio.data.AprsMapPainter;

/**
 * Fire alarm boxes drawn on an APRS-style map picture (2026-11-13): the FIRE ALARM program's box map. A box is a small
 * square in its status colour (green normal, red alarm, yellow trouble or missing, grey out of service) with its
 * number beside it; the picked one has a white ring. Map colour bytes, as {@link AprsMapPainter} paints.
 */
public final class FireAlarmMap {

	private FireAlarmMap() {}

	private static final PhotoPalette PALETTE = MapPalettes.vanilla();
	public static final byte RED = id(0xFF2020), YELLOW = id(0xFFFF40), GREEN = id(0x40E040), GREY = id(0x808080),
			WHITE = AprsMapPainter.WHITE, BLACK = AprsMapPainter.BLACK;

	private static byte id(int rgb) {
		return PALETTE.id(PALETTE.nearest(rgb));
	}

	/** The colour for a box's status, as the program's list shows it. */
	public static byte colour(String status, boolean outOfService) {
		if(outOfService)
			return GREY;
		return switch(status) {
			case "ALARM" -> RED;
			case "TROUBLE", "MISSING" -> YELLOW;
			default -> GREEN;
		};
	}

	/**
	 * One box at world {@code x}, {@code z}: a 5 by 5 square, black edged, in {@code colour}; {@code lit} false draws it
	 * dark (a flashing alarm's off half); its number beside it; a white ring when {@code picked}.
	 */
	public static void box(byte[] colors, AprsMapPainter.Fit fit, double x, double z, int number, byte colour, boolean lit, boolean picked) {
		int px = fit.pixelX(x), pz = fit.pixelZ(z);
		for(int dz = -3; dz <= 3; dz++)
			for(int dx = -3; dx <= 3; dx++) {
				boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
				if(edge)
					set(colors, fit, px + dx, pz + dz, picked ? WHITE : BLACK);
				else
					set(colors, fit, px + dx, pz + dz, lit ? colour : BLACK);
			}
		AprsMapPainter.text(colors, fit, Integer.toString(number), px + 5, pz - 2);
	}

	private static void set(byte[] colors, AprsMapPainter.Fit fit, int x, int z, byte colour) {
		if(x >= 0 && x < fit.width() && z >= 0 && z < fit.height())
			colors[x + z * fit.width()] = colour;
	}
}
