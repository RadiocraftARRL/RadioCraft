package com.arrl.radiocraft.client.screens.computer.apps;

import java.util.List;

/**
 * Drawing for the direction finding program (1.12 step 7): lines, dashes, rings and marks into a picture of map colour
 * ids, and the search drawn over its terrain map (each bearing from its station with its spread either side, the fix
 * and its circle). The ids to draw in are the caller's. Pure (no game classes), so it is unit tested.
 */
public final class DfDrawing {

	private DfDrawing() {}

	/** A bearing to draw: where it is taken, which way (compass degrees), how good (degrees), and its colour. */
	public record Bearing(double x, double z, double bearing, double sigma, byte colour) {}

	/**
	 * Where a world column lands in a picture centred on {@code cx, cz} at {@code bpp} blocks a pixel (as the terrain
	 * painter fits it: {@code AprsMapPainter.Fit}).
	 */
	public static double pixelX(double x, int cx, int bpp, int width) {
		return (x - cx) / bpp + width / 2.0D;
	}

	public static double pixelZ(double z, int cz, int bpp, int height) {
		return (z - cz) / bpp + height / 2.0D;
	}

	public static void set(byte[] buf, int w, int h, int x, int y, byte colour) {
		if(x >= 0 && x < w && y >= 0 && y < h)
			buf[x + y * w] = colour;
	}

	/** A straight line from one point to another; {@code dash} pixels on and off (0 for solid). */
	public static void line(byte[] buf, int w, int h, double x0, double y0, double x1, double y1, byte colour, int dash) {
		double dx = x1 - x0, dy = y1 - y0;
		int steps = (int)Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)));
		if(steps > 4 * (w + h))
			steps = 4 * (w + h); // Far off the picture: no need to walk all of it.
		for(int i = 0; i <= steps; i++) {
			if(dash > 0 && (i / dash) % 2 == 1)
				continue;
			double t = steps == 0 ? 0.0D : i / (double)steps;
			set(buf, w, h, (int)Math.floor(x0 + dx * t), (int)Math.floor(y0 + dy * t), colour);
		}
	}

	/** A ring of radius {@code r} pixels. */
	public static void circle(byte[] buf, int w, int h, double cx, double cy, double r, byte colour) {
		int steps = Math.max(12, (int)Math.ceil(2 * Math.PI * r));
		for(int i = 0; i < steps; i++) {
			double a = 2 * Math.PI * i / steps;
			set(buf, w, h, (int)Math.floor(cx + Math.cos(a) * r), (int)Math.floor(cy + Math.sin(a) * r), colour);
		}
	}

	/** A small diamond: a station. */
	public static void station(byte[] buf, int w, int h, double x, double y, byte colour, byte edge) {
		int px = (int)Math.floor(x), py = (int)Math.floor(y);
		for(int dy = -2; dy <= 2; dy++)
			for(int dx = -2; dx <= 2; dx++) {
				int d = Math.abs(dx) + Math.abs(dy);
				if(d <= 2)
					set(buf, w, h, px + dx, py + dy, d == 2 ? edge : colour);
			}
	}

	/** A cross: the fix. */
	public static void cross(byte[] buf, int w, int h, double x, double y, byte colour) {
		int px = (int)Math.floor(x), py = (int)Math.floor(y);
		for(int d = -3; d <= 3; d++) {
			set(buf, w, h, px + d, py + d, colour);
			set(buf, w, h, px + d, py - d, colour);
		}
	}

	/**
	 * A search over its map: each bearing a solid line from its station (as far as the picture goes), its spread (one
	 * sigma either side) dashed, and its station; the fix (if {@code fixed}) a cross in its ring.
	 */
	public static byte[] plot(byte[] terrain, int w, int h, int cx, int cz, int bpp, List<Bearing> bearings, boolean fixed, double fixX, double fixZ,
			double radius, byte fixColour, byte edge) {
		byte[] out = terrain.clone();
		double reach = (w + h) * 2.0D; // Pixels: well off the picture.
		for(Bearing bearing : bearings) {
			double x = pixelX(bearing.x(), cx, bpp, w), y = pixelZ(bearing.z(), cz, bpp, h);
			for(int side = -1; side <= 1; side++) {
				double a = Math.toRadians(bearing.bearing() + side * bearing.sigma());
				line(out, w, h, x, y, x + Math.sin(a) * reach, y - Math.cos(a) * reach, bearing.colour(), side == 0 ? 0 : 2);
			}
		}
		for(Bearing bearing : bearings)
			station(out, w, h, pixelX(bearing.x(), cx, bpp, w), pixelZ(bearing.z(), cz, bpp, h), bearing.colour(), edge);
		if(fixed) {
			double x = pixelX(fixX, cx, bpp, w), y = pixelZ(fixZ, cz, bpp, h);
			circle(out, w, h, x, y, Math.max(2.0D, radius / bpp), fixColour);
			cross(out, w, h, x, y, fixColour);
		}
		return out;
	}
}
