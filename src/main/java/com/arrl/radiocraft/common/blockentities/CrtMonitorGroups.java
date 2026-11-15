package com.arrl.radiocraft.common.blockentities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * External CRT Monitors joined into one screen (2026-11-12): same-facing displays edge to edge in the plane of their faces.
 * Cells are numbered in that plane as the viewer sees it: {@code a} to the right, {@code b} up. A wall of them is cut
 * into rectangles, each one picture, by a rule every member works out the same way from the same wall: take the lowest
 * row's leftmost cell not yet in a screen and give it the largest rectangle (at most {@link #MAX_W} by {@link #MAX_H})
 * of free cells with it at the bottom left; repeat. A full rectangle up to the cap is one screen; an L or a wall too big
 * is cut into several.
 * <p>
 * The picture covers a screen's whole front, bezels and all, so each block shows exactly the part behind its own glass
 * and the bezels hide the strips between ({@link #slice}). Pure, unit tested.
 */
public final class CrtMonitorGroups {

	/** The largest screen, blocks across and up. */
	public static final int MAX_W = 8, MAX_H = 6;
	/** Most displays looked at in one wall. */
	public static final int MAX_CELLS = 256;
	/** The glass on the model's north face, model units (tools/crt_monitor_model.py's SCREEN_*). */
	public static final float GLASS_X0 = 1.25F, GLASS_X1 = 14.75F, GLASS_Y0 = 1.75F, GLASS_Y1 = 14.5F;

	private CrtMonitorGroups() {}

	/** A screen: its bottom-left cell and its size, cells. */
	public record Rect(int a, int b, int w, int h) {
		public boolean contains(int ca, int cb) {
			return ca >= a && ca < a + w && cb >= b && cb < b + h;
		}
	}

	/** A cell's key. */
	public static long cell(int a, int b) {
		return (long)a << 32 | (b & 0xFFFFFFFFL);
	}

	/** The screens {@code cells} (keys from {@link #cell}) are cut into. */
	public static List<Rect> partition(Set<Long> cells) {
		List<long[]> order = new ArrayList<>();
		for(long c : cells)
			order.add(new long[] { (int)(c >> 32), (int)c });
		order.sort((x, y) -> x[1] != y[1] ? Long.compare(x[1], y[1]) : Long.compare(x[0], y[0])); // Bottom row first, then left to right.
		Set<Long> free = new HashSet<>(cells);
		List<Rect> out = new ArrayList<>();
		for(long[] c : order) {
			int a = (int)c[0], b = (int)c[1];
			if(!free.contains(cell(a, b)))
				continue;
			Rect best = largest(a, b, free);
			for(int i = 0; i < best.w(); i++)
				for(int j = 0; j < best.h(); j++)
					free.remove(cell(a + i, b + j));
			out.add(best);
		}
		return out;
	}

	/** The largest rectangle of {@code free} cells with {@code a, b} at its bottom left; the wider of two the same size. */
	private static Rect largest(int a, int b, Set<Long> free) {
		Rect best = new Rect(a, b, 1, 1);
		int width = MAX_W;
		for(int h = 1; h <= MAX_H; h++) {
			int row = 0;
			while(row < width && free.contains(cell(a + row, b + h - 1)))
				row++;
			width = row;
			if(width == 0)
				break;
			if(width * h > best.w() * best.h())
				best = new Rect(a, b, width, h);
		}
		return best;
	}

	/** The screen cell {@code a, b} is in, or a 1 by 1 one if it is not among {@code cells}. */
	public static Rect groupOf(int a, int b, Set<Long> cells) {
		if(cells.contains(cell(a, b)))
			for(Rect r : partition(cells))
				if(r.contains(a, b))
					return r;
		return new Rect(a, b, 1, 1);
	}

	/**
	 * The part of a screen's picture block {@code da, db} (from its bottom left) shows behind its glass, for a screen
	 * {@code w} by {@code h}: u from the viewer's left, v from the top, 0 to 1 over the whole front with its bezels.
	 */
	public record Slice(float u0, float v0, float u1, float v1) {}

	/** See {@link Slice}. The glass is symmetric left to right, so the model's mirror (its north face seen from -z) does not show here. */
	public static Slice slice(int da, int db, int w, int h) {
		float width = w * 16.0F, height = h * 16.0F;
		float left = 16.0F - GLASS_X1, right = 16.0F - GLASS_X0; // The model's x runs right to left for the viewer.
		return new Slice((da * 16.0F + left) / width, (height - (db * 16.0F + GLASS_Y1)) / height,
				(da * 16.0F + right) / width, (height - (db * 16.0F + GLASS_Y0)) / height);
	}
}
