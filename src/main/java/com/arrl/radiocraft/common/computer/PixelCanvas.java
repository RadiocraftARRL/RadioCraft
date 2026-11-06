package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.radio.data.PixelFont;

/**
 * A small picture of packed map colour ids (what a filled map, a {@code .IMG} file and the radio computer's screen
 * pictures hold) to draw on: fills, lines, frames, discs and {@link PixelFont} text at any whole scale (computer batch
 * 3C: the Awards program's certificates and the QSL cards). Pure (no game classes), so it is unit tested. Anything
 * drawn outside is clipped.
 */
public final class PixelCanvas {

	/**
	 * Packed map colour ids ({@code MapColor} id times four plus the brightness, HIGH = 2 being the colour itself) the
	 * certificate and card art use. Checked against {@code MapColor} by a unit test.
	 */
	public static final byte WHITE = 34, CREAM = 58, BLACK = 118, RED = 114, BRIGHT_RED = 18, ORANGE = 62, YELLOW = 74, GOLD = 122,
			GREEN = 110, LIME = 78, CYAN = 94, LIGHT_BLUE = 70, BLUE = 102, NAVY = 103, PURPLE = 98, MAGENTA = 66, PINK = 82, BROWN = 106,
			GREY = 86, LIGHT_GREY = 90, TAN = 10, DARK_GREY = 87;

	private final byte[] pixels;
	private final int width, height;

	public PixelCanvas(int width, int height, byte background) {
		this.width = width;
		this.height = height;
		this.pixels = new byte[width * height];
		java.util.Arrays.fill(pixels, background);
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	/** The picture drawn (the canvas's own array: copy it to keep it while drawing on). */
	public byte[] pixels() {
		return pixels;
	}

	public byte get(int x, int y) {
		return x < 0 || y < 0 || x >= width || y >= height ? 0 : pixels[y * width + x];
	}

	public void set(int x, int y, byte colour) {
		if(x >= 0 && y >= 0 && x < width && y < height)
			pixels[y * width + x] = colour;
	}

	public void fill(int x, int y, int w, int h, byte colour) {
		for(int j = Math.max(0, y); j < Math.min(height, y + h); j++)
			for(int i = Math.max(0, x); i < Math.min(width, x + w); i++)
				pixels[j * width + i] = colour;
	}

	/** A one-pixel outline {@code inset} pixels in from the edges, {@code thickness} pixels thick. */
	public void frame(int inset, int thickness, byte colour) {
		rect(inset, inset, width - 2 * inset, height - 2 * inset, thickness, colour);
	}

	/** An outline of the rectangle {@code x, y, w, h}, {@code thickness} pixels thick, drawn inside it. */
	public void rect(int x, int y, int w, int h, int thickness, byte colour) {
		fill(x, y, w, thickness, colour);
		fill(x, y + h - thickness, w, thickness, colour);
		fill(x, y, thickness, h, colour);
		fill(x + w - thickness, y, thickness, h, colour);
	}

	public void hline(int x, int y, int w, byte colour) {
		fill(x, y, w, 1, colour);
	}

	/** A dotted line, every other pixel (a blank to write on). */
	public void dots(int x, int y, int w, byte colour) {
		for(int i = 0; i < w; i += 2)
			set(x + i, y, colour);
	}

	/** A filled disc round {@code cx, cy}. */
	public void disc(int cx, int cy, int radius, byte colour) {
		for(int y = -radius; y <= radius; y++)
			for(int x = -radius; x <= radius; x++)
				if(x * x + y * y <= radius * radius + radius / 2)
					set(cx + x, cy + y, colour);
	}

	/** {@code text} in the 3 x 5 font at {@code scale}, its top left at {@code x, y}; returns the x after it. */
	public int text(int x, int y, String text, int scale, byte colour) {
		int at = x;
		for(int k = 0; k < text.length(); k++) {
			String[] glyph = PixelFont.glyph(text.charAt(k));
			for(int row = 0; row < PixelFont.HEIGHT; row++)
				for(int col = 0; col < PixelFont.WIDTH; col++)
					if(glyph[row].charAt(col) == '#')
						fill(at + col * scale, y + row * scale, scale, scale, colour);
			at += PixelFont.ADVANCE * scale;
		}
		return at;
	}

	/** {@code text} centred between {@code left} and {@code right} (exclusive). */
	public void centred(int left, int right, int y, String text, int scale, byte colour) {
		text(left + (right - left - PixelFont.width(text, scale)) / 2, y, text, scale, colour);
	}

	/** The largest scale (at most {@code max}, at least 1) at which {@code text} fits in {@code width} pixels. */
	public static int scaleToFit(String text, int width, int max) {
		for(int scale = max; scale > 1; scale--)
			if(PixelFont.width(text, scale) <= width)
				return scale;
		return 1;
	}

	/** Characters of the 3 x 5 font that fit in {@code width} pixels at {@code scale}. */
	public static int charsIn(int width, int scale) {
		return Math.max(0, (width / scale + 1) / PixelFont.ADVANCE);
	}

	/** {@code text} cut to what fits in {@code width} pixels at {@code scale}. */
	public static String fit(String text, int width, int scale) {
		int max = charsIn(width, scale);
		return text.length() > max ? text.substring(0, max) : text;
	}

	/** {@code source} ({@code w} x {@code h}) drawn with its top left at {@code x, y}, pixel for pixel. */
	public void blit(byte[] source, int w, int h, int x, int y) {
		if(source == null || source.length < w * h)
			return;
		for(int j = 0; j < h; j++)
			for(int i = 0; i < w; i++)
				set(x + i, y + j, source[j * w + i]);
	}
}
