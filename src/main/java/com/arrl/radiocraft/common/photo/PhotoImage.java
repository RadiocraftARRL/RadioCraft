package com.arrl.radiocraft.common.photo;

/**
 * Pictures for maps, with no game classes so it can be unit tested: scaling a frame down to a map's 128 by 128,
 * black and white, and quantising to a {@link PhotoPalette} (Floyd-Steinberg dithering, so the few map colours still
 * make a photo). Pixels are {@code 0xRRGGBB} ints, row by row from the top left; a map picture is packed map colour ids
 * in the same order, which is how {@code MapItemSavedData.colors} keeps them ({@code x + y * 128}).
 * <p>
 * The camera uses all of it; the radio computer's scanner and SSTV are meant to as well (see {@link PhotoPrints} for
 * the map item side).
 */
public final class PhotoImage {

	/** A map picture is this many pixels across and down. */
	public static final int SIZE = 128;
	/** Pixels in a map picture. */
	public static final int PIXELS = SIZE * SIZE;
	/** Most samples taken along each side of the box one output pixel covers: a 4K frame costs no more than 1080p. */
	public static final int MAX_SAMPLES = 8;

	private PhotoImage() {}

	/** Somewhere to read pixels from: a frame grabbed from the screen, an image file, a test pattern. */
	@FunctionalInterface
	public interface PixelSource {
		/** The pixel at {@code x, y} (from the top left), {@code 0xRRGGBB}. */
		int rgb(int x, int y);
	}

	/**
	 * The middle square of a {@code width} by {@code height} picture, scaled to {@code size} by {@code size}: each pixel
	 * the average of the box it covers (at most {@link #MAX_SAMPLES} samples a side). A camera's square frame from a
	 * wide screen.
	 */
	public static int[] downscale(PixelSource source, int width, int height, int size) {
		if(width <= 0 || height <= 0 || size <= 0)
			throw new IllegalArgumentException("Empty picture");
		int crop = Math.min(width, height);
		int left = (width - crop) / 2, top = (height - crop) / 2;
		int[] out = new int[size * size];
		for(int oy = 0; oy < size; oy++) {
			int y0 = top + (int)((long)oy * crop / size);
			int y1 = Math.max(y0 + 1, top + (int)((long)(oy + 1) * crop / size));
			int yStep = Math.max(1, (y1 - y0) / MAX_SAMPLES);
			for(int ox = 0; ox < size; ox++) {
				int x0 = left + (int)((long)ox * crop / size);
				int x1 = Math.max(x0 + 1, left + (int)((long)(ox + 1) * crop / size));
				int xStep = Math.max(1, (x1 - x0) / MAX_SAMPLES);
				long r = 0, g = 0, b = 0;
				int n = 0;
				for(int y = y0; y < y1; y += yStep) {
					for(int x = x0; x < x1; x += xStep) {
						int c = source.rgb(x, y);
						r += (c >> 16) & 0xFF;
						g += (c >> 8) & 0xFF;
						b += c & 0xFF;
						n++;
					}
				}
				out[ox + oy * size] = (int)(r / n) << 16 | (int)(g / n) << 8 | (int)(b / n);
			}
		}
		return out;
	}

	/** {@code rgb} in black and white (each pixel its {@link PhotoPalette#luminance}). A new array. */
	public static int[] greyscale(int[] rgb) {
		int[] out = new int[rgb.length];
		for(int i = 0; i < rgb.length; i++) {
			int l = PhotoPalette.luminance(rgb[i]);
			out[i] = l << 16 | l << 8 | l;
		}
		return out;
	}

	/**
	 * {@code rgb} ({@code width} by {@code height}) as ids of {@code palette}'s colours, each the nearest; with
	 * {@code dither}, Floyd-Steinberg error diffusion spreads what each pixel missed onto the pixels after it.
	 */
	public static byte[] quantise(int[] rgb, int width, int height, PhotoPalette palette, boolean dither) {
		if(rgb.length != width * height)
			throw new IllegalArgumentException("Expected " + width * height + " pixels, got " + rgb.length);
		byte[] out = new byte[rgb.length];
		if(!dither) {
			for(int i = 0; i < rgb.length; i++)
				out[i] = palette.id(palette.nearest(rgb[i]));
			return out;
		}
		// Errors in sixteenths, three channels per pixel, one pixel of margin each side.
		int[] here = new int[(width + 2) * 3];
		int[] below = new int[(width + 2) * 3];
		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) {
				int c = rgb[x + y * width];
				int at = (x + 1) * 3;
				int r = clamp(((c >> 16) & 0xFF) + here[at] / 16);
				int g = clamp(((c >> 8) & 0xFF) + here[at + 1] / 16);
				int b = clamp((c & 0xFF) + here[at + 2] / 16);
				int index = palette.nearest(r, g, b);
				out[x + y * width] = palette.id(index);
				int q = palette.rgb(index);
				int[] error = { r - ((q >> 16) & 0xFF), g - ((q >> 8) & 0xFF), b - (q & 0xFF) };
				for(int ch = 0; ch < 3; ch++) {
					int e = error[ch];
					here[at + 3 + ch] += e * 7;
					below[at - 3 + ch] += e * 3;
					below[at + ch] += e * 5;
					below[at + 3 + ch] += e;
				}
			}
			int[] swap = here;
			here = below;
			below = swap;
			java.util.Arrays.fill(below, 0);
		}
		return out;
	}

	/** A photo of {@code rgb} ({@link #SIZE} square): dithered map colour ids, in colour or black and white. */
	public static byte[] photo(int[] rgb, PhotoPalette palette, boolean colour) {
		return colour ? quantise(rgb, SIZE, SIZE, palette, true) : quantise(greyscale(rgb), SIZE, SIZE, palette.greys(), true);
	}

	/** Map colour {@code ids} back as {@code 0xRRGGBB} pixels (black for ids {@code palette} lacks). A new array. */
	public static int[] toRgb(byte[] ids, PhotoPalette palette) {
		int[] out = new int[ids.length];
		for(int i = 0; i < ids.length; i++)
			out[i] = Math.max(0, palette.rgbOf(ids[i]));
		return out;
	}

	private static int clamp(int value) {
		return value < 0 ? 0 : Math.min(255, value);
	}
}
