package com.arrl.radiocraft.common.photo;

import java.util.Arrays;

/**
 * The colours a picture may use: each entry a packed map colour id (what a vanilla map stores per pixel) and the RGB it
 * shows as. {@link MapPalettes#vanilla()} is every colour a map can show; this class knows nothing of the game, so it can
 * be unit tested with a palette of its own. Lookups by id take any byte; ids not in the palette have no colour.
 */
public final class PhotoPalette {

	/** Greys may stray this far between their brightest and dimmest channel (out of 255). */
	public static final int GREY_TOLERANCE = 6;

	private final int[] rgb;
	private final byte[] ids;
	/** For each of the 256 byte values, its index here, or -1. */
	private final int[] indexById = new int[256];
	private final byte darkest;
	private PhotoPalette greys;

	/**
	 * @param rgb the colours, {@code 0xRRGGBB}
	 * @param ids the packed map colour id of each colour; a repeated id keeps its first colour
	 */
	public PhotoPalette(int[] rgb, byte[] ids) {
		if(rgb.length != ids.length || rgb.length == 0)
			throw new IllegalArgumentException("A palette needs one id per colour, and at least one colour");
		this.rgb = new int[rgb.length];
		for(int i = 0; i < rgb.length; i++)
			this.rgb[i] = rgb[i] & 0xFFFFFF;
		this.ids = ids.clone();
		Arrays.fill(indexById, -1);
		for(int i = 0; i < ids.length; i++)
			if(indexById[ids[i] & 0xFF] < 0)
				indexById[ids[i] & 0xFF] = i;
		this.darkest = this.ids[nearest(0, 0, 0)];
	}

	public int size() {
		return rgb.length;
	}

	/** Colour {@code index}, {@code 0xRRGGBB}. */
	public int rgb(int index) {
		return rgb[index];
	}

	/** The packed map colour id of colour {@code index}. */
	public byte id(int index) {
		return ids[index];
	}

	/** True if {@code id} is one of this palette's colours. */
	public boolean contains(byte id) {
		return indexById[id & 0xFF] >= 0;
	}

	/** The colour {@code id} shows as, {@code 0xRRGGBB}, or -1 if it is not in this palette. */
	public int rgbOf(byte id) {
		int index = indexById[id & 0xFF];
		return index < 0 ? -1 : rgb[index];
	}

	/** The id of the colour nearest black: what an unknown id becomes. */
	public byte darkest() {
		return darkest;
	}

	/** The index of the colour nearest {@code 0xRRGGBB}. */
	public int nearest(int colour) {
		return nearest((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF);
	}

	/**
	 * The index of the colour nearest {@code r, g, b} (0 to 255 each). Distance is the "redmean" weighting of RGB, which
	 * follows the eye better than plain RGB at no cost.
	 */
	public int nearest(int r, int g, int b) {
		int best = 0;
		long bestDistance = Long.MAX_VALUE;
		for(int i = 0; i < rgb.length; i++) {
			int c = rgb[i];
			int cr = (c >> 16) & 0xFF, cg = (c >> 8) & 0xFF, cb = c & 0xFF;
			int mean = (r + cr) >> 1;
			int dr = r - cr, dg = g - cg, db = b - cb;
			long distance = (((512L + mean) * dr * dr) >> 8) + 4L * dg * dg + (((767L - mean) * db * db) >> 8);
			if(distance < bestDistance) {
				bestDistance = distance;
				best = i;
				if(distance == 0)
					break;
			}
		}
		return best;
	}

	/** The greys of this palette (black to white, every colour whose channels are within {@link #GREY_TOLERANCE}). */
	public PhotoPalette greys() {
		if(greys == null) {
			int count = 0;
			int[] greyRgb = new int[rgb.length];
			byte[] greyIds = new byte[rgb.length];
			for(int i = 0; i < rgb.length; i++) {
				if(isGrey(rgb[i])) {
					greyRgb[count] = rgb[i];
					greyIds[count++] = ids[i];
				}
			}
			if(count == 0) // No greys at all: fall back to the darkest colour, so a B&W photo is at least one tone.
				greys = new PhotoPalette(new int[] { rgbOf(darkest) }, new byte[] { darkest });
			else
				greys = count == rgb.length ? this : new PhotoPalette(Arrays.copyOf(greyRgb, count), Arrays.copyOf(greyIds, count));
		}
		return greys;
	}

	/** True if {@code 0xRRGGBB} is a grey: its channels within {@link #GREY_TOLERANCE} of each other. */
	public static boolean isGrey(int colour) {
		int r = (colour >> 16) & 0xFF, g = (colour >> 8) & 0xFF, b = colour & 0xFF;
		return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= GREY_TOLERANCE;
	}

	/** Brightness of {@code 0xRRGGBB}, 0 to 255 (Rec. 601 weights, as black and white film and TV saw colour). */
	public static int luminance(int colour) {
		return (299 * ((colour >> 16) & 0xFF) + 587 * ((colour >> 8) & 0xFF) + 114 * (colour & 0xFF) + 500) / 1000;
	}

	/** {@code ids} with every id not in this palette replaced by {@link #darkest()}. A new array. */
	public byte[] sanitised(byte[] ids) {
		byte[] out = ids.clone();
		for(int i = 0; i < out.length; i++)
			if(!contains(out[i]))
				out[i] = darkest;
		return out;
	}

	/**
	 * {@code ids} in black and white: each colour becomes the grey of this palette nearest its brightness (ids not in
	 * the palette become the darkest grey). A new array. Greys stay as they are.
	 */
	public byte[] greyscaled(byte[] ids) {
		PhotoPalette greys = greys();
		byte[] table = new byte[256];
		for(int id = 0; id < 256; id++) {
			int colour = rgbOf((byte)id);
			if(colour < 0) {
				table[id] = greys.darkest();
				continue;
			}
			if(greys.contains((byte)id)) {
				table[id] = (byte)id;
				continue;
			}
			int luma = luminance(colour);
			table[id] = greys.id(greys.nearest(luma, luma, luma));
		}
		byte[] out = new byte[ids.length];
		for(int i = 0; i < ids.length; i++)
			out[i] = table[ids[i] & 0xFF];
		return out;
	}
}
