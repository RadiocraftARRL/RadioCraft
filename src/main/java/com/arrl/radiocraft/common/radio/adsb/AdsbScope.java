package com.arrl.radiocraft.common.radio.adsb;

import com.arrl.radiocraft.common.radio.data.PixelFont;

import java.util.List;

/**
 * Paints an ADS-B picture as a square of map colour ids, for the radio computer's ADS-B program (1.12 step 9): a round
 * scope, north up, range rings at each quarter, each target's trail, a block with a track line, and (with
 * {@code tags}) its flight identification in the little pixel font. The receiver block's own screen draws itself in
 * GUI code instead. Pure (the colours are handed in), so it is unit tested.
 */
public final class AdsbScope {

	/** The map colour ids to paint with. */
	public record Palette(byte outside, byte face, byte ring, byte trail, byte target, byte emergency, byte tag, byte selected) {}

	private AdsbScope() {}

	/**
	 * Paints {@code contacts} (positions relative to the receiver, blocks) into {@code pixels} ({@code size} square),
	 * {@code range} blocks from the middle to the edge. {@code selected} is the ICAO address of the target to box, or
	 * -1.
	 */
	public static void paint(byte[] pixels, int size, List<AdsbPicture.Contact> contacts, int range, int selected, boolean tags, Palette p) {
		paint(pixels, size, contacts, range, selected, tags, p, false);
	}

	/**
	 * {@link #paint}, round or {@code square}: a square display (the receiver's in-world screen since 1.12 playtest
	 * fixes 2, like a modern ATC display) fills the whole square, its range rings clipped at the edges; {@code range}
	 * still reaches from the middle to the middle of an edge, so the corners show a little further.
	 */
	public static void paint(byte[] pixels, int size, List<AdsbPicture.Contact> contacts, int range, int selected, boolean tags, Palette p, boolean square) {
		double c = (size - 1) / 2.0D, radius = c - 1.0D;
		int mid = (int)Math.round(c);
		for(int y = 0; y < size; y++)
			for(int x = 0; x < size; x++) {
				double d = Math.hypot(x - c, y - c);
				byte colour = !square && d > radius + 0.5D ? p.outside() : p.face();
				for(int ring = 1; ring <= (square ? 5 : 4) && colour == p.face(); ring++)
					if(Math.abs(d - radius * ring / 4.0D) < 0.5D)
						colour = p.ring();
				if(colour == p.face() && (x == mid || y == mid) && (x + y) % 3 == 0)
					colour = p.ring(); // The cross, dotted.
				pixels[y * size + x] = colour;
			}
		double scale = radius / Math.max(1, range);
		// Off the round scope's face, or (square) off the square's.
		double reach = square ? Double.MAX_VALUE : radius;
		for(AdsbPicture.Contact contact : contacts) {
			int[] trail = contact.trail();
			for(int i = 0; i + 1 < trail.length; i += 2)
				plot(pixels, size, c + trail[i] * scale, c + trail[i + 1] * scale, reach, p.trail());
		}
		for(AdsbPicture.Contact contact : contacts) {
			double x = c + contact.dx() * scale, y = c + contact.dz() * scale;
			if(square ? x < 1.0D || y < 1.0D || x > size - 2.0D || y > size - 2.0D : Math.hypot(x - c, y - c) > radius - 1.0D)
				continue;
			byte colour = contact.emergency() ? p.emergency() : p.target();
			int ix = (int)Math.round(x), iy = (int)Math.round(y);
			for(int dy = -1; dy <= 1; dy++)
				for(int dx = -1; dx <= 1; dx++)
					set(pixels, size, ix + dx, iy + dy, colour);
			if(contact.knots() > 0) { // The track line, five pixels.
				double a = Math.toRadians(contact.track());
				for(int i = 2; i <= 6; i++)
					plot(pixels, size, ix + Math.sin(a) * i, iy - Math.cos(a) * i, reach, colour);
			}
			if(contact.icao() == selected)
				for(int i = -3; i <= 3; i++) {
					set(pixels, size, ix + i, iy - 3, p.selected());
					set(pixels, size, ix + i, iy + 3, p.selected());
					set(pixels, size, ix - 3, iy + i, p.selected());
					set(pixels, size, ix + 3, iy + i, p.selected());
				}
			if(tags)
				text(pixels, size, contact.label(), ix + 4, iy - 7, contact.emergency() ? p.emergency() : p.tag());
		}
	}

	/** Where a contact lands on a {@code size} square scope of {@code range}: {x, y}, or null off the scope. */
	public static int[] place(int size, int range, double dx, double dz) {
		double c = (size - 1) / 2.0D, radius = c - 1.0D, scale = radius / Math.max(1, range);
		double x = c + dx * scale, y = c + dz * scale;
		if(Math.hypot(x - c, y - c) > radius - 1.0D)
			return null;
		return new int[] { (int)Math.round(x), (int)Math.round(y) };
	}

	private static void plot(byte[] pixels, int size, double x, double y, double radius, byte colour) {
		double c = (size - 1) / 2.0D;
		if(Math.hypot(x - c, y - c) > radius - 0.5D)
			return;
		set(pixels, size, (int)Math.round(x), (int)Math.round(y), colour);
	}

	private static void set(byte[] pixels, int size, int x, int y, byte colour) {
		if(x >= 0 && y >= 0 && x < size && y < size)
			pixels[y * size + x] = colour;
	}

	private static void text(byte[] pixels, int size, String text, int x, int y, byte colour) {
		for(int i = 0; i < text.length(); i++) {
			String[] glyph = PixelFont.glyph(text.charAt(i));
			for(int row = 0; row < PixelFont.HEIGHT; row++)
				for(int col = 0; col < PixelFont.WIDTH; col++)
					if(glyph[row].charAt(col) == '#')
						set(pixels, size, x + i * PixelFont.ADVANCE + col, y + row, colour);
		}
	}
}
