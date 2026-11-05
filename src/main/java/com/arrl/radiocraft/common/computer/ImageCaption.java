package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.radio.data.PixelFont;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Text written onto a picture (1.12 step 7, the SSTV program's captions): a line or two along the top and the bottom of
 * a 128 by 128 map picture, in capitals of the {@link PixelFont} with an outline so it reads on any picture, as on the
 * pictures hams send over SSTV (callsign, report, "73") or a meme. Short captions are drawn twice the size. Pure (no
 * game classes): the pixels are map colour ids, and the caller says which ids to draw in.
 */
public final class ImageCaption {

	/** A picture's width and height. */
	public static final int SIZE = 128;
	/** Most characters a caption may have (top or bottom). */
	public static final int MAX_CHARS = 64;
	/** Most lines a caption takes. */
	public static final int MAX_LINES = 3;
	/** Pixels kept clear between the text and the picture's edge. */
	private static final int MARGIN = 2;

	private ImageCaption() {}

	/** {@code text} as the font can draw it: upper case, characters it lacks dropped, runs of spaces made one, cut to {@link #MAX_CHARS}. */
	public static String clean(String text) {
		StringBuilder out = new StringBuilder();
		for(char c : text.toUpperCase(Locale.ROOT).toCharArray()) {
			if(!PixelFont.has(c) || c == ' ' && (out.isEmpty() || out.charAt(out.length() - 1) == ' '))
				continue;
			out.append(c);
			if(out.length() >= MAX_CHARS)
				break;
		}
		return out.toString().strip();
	}

	/** Characters that fit across the picture at {@code scale}. */
	public static int perLine(int scale) {
		return (SIZE - 2 * MARGIN + scale) / (PixelFont.ADVANCE * scale);
	}

	/** {@code text} broken into lines at spaces, at most {@code width} characters each (a longer word is cut). */
	public static List<String> wrap(String text, int width) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for(String word : text.split(" ")) {
			if(word.isEmpty())
				continue;
			while(word.length() > width) { // A word longer than a line.
				if(!line.isEmpty()) {
					lines.add(line.toString());
					line.setLength(0);
				}
				lines.add(word.substring(0, width));
				word = word.substring(width);
			}
			if(!line.isEmpty() && line.length() + 1 + word.length() > width) {
				lines.add(line.toString());
				line.setLength(0);
			}
			if(!line.isEmpty())
				line.append(' ');
			line.append(word);
		}
		if(!line.isEmpty())
			lines.add(line.toString());
		return lines;
	}

	/** The scale a caption is drawn at: 2 if it fits in two lines that big, else 1. */
	public static int scaleFor(String text) {
		return wrap(clean(text), perLine(2)).size() <= 2 ? 2 : 1;
	}

	/**
	 * A copy of {@code pixels} ({@link #SIZE} square) with {@code top} written along the top and {@code bottom} along
	 * the bottom, centred, in {@code fill} with an {@code outline} round each letter. Either may be empty.
	 */
	public static byte[] apply(byte[] pixels, String top, String bottom, byte fill, byte outline) {
		if(pixels.length != SIZE * SIZE)
			throw new IllegalArgumentException("A picture is " + SIZE * SIZE + " pixels");
		byte[] out = pixels.clone();
		draw(out, clean(top), true, fill, outline);
		draw(out, clean(bottom), false, fill, outline);
		return out;
	}

	private static void draw(byte[] out, String text, boolean top, byte fill, byte outline) {
		if(text.isEmpty())
			return;
		int scale = scaleFor(text);
		List<String> lines = wrap(text, perLine(scale));
		if(lines.size() > MAX_LINES)
			lines = lines.subList(0, MAX_LINES);
		int lineHeight = (PixelFont.HEIGHT + 2) * scale;
		int y = top ? MARGIN : SIZE - MARGIN - lines.size() * lineHeight + 2 * scale;
		for(String line : lines) {
			int x = (SIZE - PixelFont.width(line, scale)) / 2;
			text(out, line, x, y, scale, outline, true);
			text(out, line, x, y, scale, fill, false);
			y += lineHeight;
		}
	}

	/** Draws {@code line} at {@code x, y}: the letters, or (with {@code halo}) the pixels round them. */
	private static void text(byte[] out, String line, int x, int y, int scale, byte colour, boolean halo) {
		for(int c = 0; c < line.length(); c++) {
			String[] glyph = PixelFont.glyph(line.charAt(c));
			int left = x + c * PixelFont.ADVANCE * scale;
			for(int row = 0; row < PixelFont.HEIGHT; row++)
				for(int col = 0; col < PixelFont.WIDTH; col++) {
					if(glyph[row].charAt(col) != '#')
						continue;
					int px = left + col * scale, py = y + row * scale;
					if(halo)
						fill(out, px - 1, py - 1, scale + 2, colour);
					else
						fill(out, px, py, scale, colour);
				}
		}
	}

	private static void fill(byte[] out, int x, int y, int size, byte colour) {
		for(int dy = 0; dy < size; dy++)
			for(int dx = 0; dx < size; dx++) {
				int px = x + dx, py = y + dy;
				if(px >= 0 && px < SIZE && py >= 0 && py < SIZE)
					out[px + py * SIZE] = colour;
			}
	}
}
