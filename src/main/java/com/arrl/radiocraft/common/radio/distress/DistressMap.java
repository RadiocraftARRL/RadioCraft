package com.arrl.radiocraft.common.radio.distress;

import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.photo.PhotoPalette;
import com.arrl.radiocraft.common.radio.data.AprsMapPainter;

import java.util.List;
import java.util.Locale;

/**
 * Distress alerts drawn on an APRS-style map picture (2026-11-09): the DISTRESS program's screen and its printouts.
 * A DSC call is a red diamond with a white cross through it; a beacon is an orange ring with a dot, so the two read
 * apart at a glance. Map colour bytes, as {@link AprsMapPainter} paints.
 */
public final class DistressMap {

	private DistressMap() {}

	private static final PhotoPalette PALETTE = MapPalettes.vanilla();
	public static final byte RED = id(0xFF2020), ORANGE = id(0xFF9020), WHITE = AprsMapPainter.WHITE, BLACK = AprsMapPainter.BLACK,
			GREY = id(0x808080), YELLOW = id(0xFFFF40);

	private static byte id(int rgb) {
		return PALETTE.id(PALETTE.nearest(rgb));
	}

	/** {@code alert}'s marker on {@code colors}; {@code lit} false draws only its outline (a flashing marker's off half). */
	public static void marker(byte[] colors, AprsMapPainter.Fit fit, DistressAlert alert, boolean lit, boolean picked) {
		int x = fit.pixelX(alert.x() + 0.5D), z = fit.pixelZ(alert.z() + 0.5D);
		boolean over = alert.state() != DistressAlert.State.ACTIVE;
		byte main = over ? GREY : alert.isDsc() ? RED : ORANGE;
		if(alert.isDsc()) {
			for(int dz = -4; dz <= 4; dz++)
				for(int dx = -4; dx <= 4; dx++) {
					int d = Math.abs(dx) + Math.abs(dz);
					if(d == 4 || d == 5)
						set(colors, fit, x + dx, z + dz, BLACK);
					else if(d < 4)
						set(colors, fit, x + dx, z + dz, lit || over ? (dx == 0 || dz == 0 ? WHITE : main) : BLACK);
				}
			if(!lit && !over)
				for(int dz = -3; dz <= 3; dz++)
					for(int dx = -3; dx <= 3; dx++)
						if(Math.abs(dx) + Math.abs(dz) == 3)
							set(colors, fit, x + dx, z + dz, main);
		}
		else {
			for(int dz = -5; dz <= 5; dz++)
				for(int dx = -5; dx <= 5; dx++) {
					double r = Math.sqrt(dx * dx + dz * dz);
					if(r >= 3.5D && r < 4.6D)
						set(colors, fit, x + dx, z + dz, main);
					else if(r >= 4.6D && r < 5.6D || r >= 2.4D && r < 3.5D)
						set(colors, fit, x + dx, z + dz, BLACK);
					else if(r < 1.6D)
						set(colors, fit, x + dx, z + dz, lit || over ? WHITE : BLACK);
				}
		}
		if(picked) // A yellow box round the one picked in the list.
			for(int d = -7; d <= 7; d++) {
				set(colors, fit, x + d, z - 7, YELLOW);
				set(colors, fit, x + d, z + 7, YELLOW);
				set(colors, fit, x - 7, z + d, YELLOW);
				set(colors, fit, x + 7, z + d, YELLOW);
			}
	}

	/**
	 * The printout's caption, under the map: the alert in four lines of the 3 by 5 font on a black band along the
	 * bottom ({@code lines} of at most 31 characters).
	 */
	public static void caption(byte[] colors, AprsMapPainter.Fit fit, List<String> lines) {
		int top = fit.height() - lines.size() * 7 - 2;
		for(int y = top; y < fit.height(); y++)
			for(int x = 0; x < fit.width(); x++)
				set(colors, fit, x, y, BLACK);
		for(int i = 0; i < lines.size(); i++)
			AprsMapPainter.text(colors, fit, fit(lines.get(i)), 2, top + 2 + i * 7);
	}

	/** The caption's lines for {@code alert}: what, who, where, and its state and the nature. */
	public static List<String> captionLines(DistressAlert alert) {
		String what = alert.isDsc() ? "DSC DISTRESS " + alert.owner() : "BEACON " + alert.owner();
		return List.of(what, alert.nature().label(), String.format(Locale.ROOT, "X%d Y%d Z%d", alert.x(), alert.y(), alert.z()), alert.state().name());
	}

	private static String fit(String text) {
		String upper = text.toUpperCase(Locale.ROOT);
		return upper.length() > 31 ? upper.substring(0, 31) : upper;
	}

	private static void set(byte[] colors, AprsMapPainter.Fit fit, int x, int z, byte colour) {
		if(x >= 0 && x < fit.width() && z >= 0 && z < fit.height())
			colors[x + z * fit.width()] = colour;
	}
}
