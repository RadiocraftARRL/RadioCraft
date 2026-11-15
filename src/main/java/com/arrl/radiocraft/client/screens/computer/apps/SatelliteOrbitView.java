package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.PixelCanvas;

/**
 * SATTRACK's Location view (2026-11-13): the world side on, a square globe (oceans, continents, ice caps and cloud,
 * lit from the upper left, a thin blue glow of air round it) with the computer's station on top, and the satellite
 * going round it. Space it cannot be heard from is dark; the slice of its way round that is in view from here is
 * lighter. An orbiting satellite goes once round per trip along its track (a few minutes); the slice is widest when its
 * track passes right over the station and narrows as the track moves off, gone when it passes too far away to be heard
 * at all. Pure (no game classes), so it is unit tested.
 */
public final class SatelliteOrbitView {

	private SatelliteOrbitView() {}

	/**
	 * The angle round the world, radians from straight up (over the station) and clockwise, of a satellite
	 * {@code along} blocks along its track from the station, the track {@code tile} blocks round.
	 */
	public static double angle(double along, double tile) {
		return along / tile * Math.PI * 2.0D;
	}

	/**
	 * Half the angle of the slice in view, radians, for a track that passes {@code across} blocks to one side of the
	 * station, heard to {@code coverage} blocks; negative if it never comes into view this time round.
	 */
	public static double halfSlice(double across, double coverage, double tile) {
		if(Math.abs(across) >= coverage)
			return -1.0D;
		return angle(Math.sqrt(coverage * coverage - across * across), tile);
	}

	/** The continents on the globe's face, as a world map squared up: '#' land, '*' desert, '^' ice. */
	private static final String[] MAP = {
			"^^^^^^^^^^^^^^^^^^^^^^^^",
			"...^^###.....^.####.....",
			"..#######..##########...",
			".########..###########..",
			".#######..############..",
			"..######...##########...",
			"...####....#*******##...",
			"....###...*******.###...",
			".....##...*******..##...",
			"......#....*****....#...",
			"......###...####...##...",
			".......####..###....#...",
			".......#####.###........",
			"........####..##........",
			"........###...#.....##..",
			"........###........####.",
			"........##.........###..",
			"........#...............",
			"........#...............",
			"........................",
			"........................",
			"...^^^^^^^^^^^^^^^^^^...",
			".^^^^^^^^^^^^^^^^^^^^^^.",
			"^^^^^^^^^^^^^^^^^^^^^^^^"};

	/** 0 sea, 1 land, 2 desert, 3 ice at a point of the map (0 to 1 each way), coasts roughened. */
	private static int ground(double u, double v) {
		double rough = (PixelCanvas.fractal(u * 14.0D, v * 14.0D, 3L, 3) - 0.5D) * 1.4D;
		double gx = u * MAP[0].length() - 0.5D + rough, gy = v * MAP.length - 0.5D + rough * 0.7D;
		int x0 = (int)Math.floor(gx), y0 = (int)Math.floor(gy);
		double tx = gx - x0, ty = gy - y0;
		double land = 0, ice = 0, desert = 0;
		for(int j = 0; j < 2; j++)
			for(int i = 0; i < 2; i++) {
				char ch = at(x0 + i, y0 + j);
				double w = (i == 0 ? 1 - tx : tx) * (j == 0 ? 1 - ty : ty);
				if(ch != '.')
					land += w;
				if(ch == '^')
					ice += w;
				if(ch == '*')
					desert += w;
			}
		if(land < 0.5D)
			return 0;
		return ice >= 0.5D ? 3 : desert >= 0.4D ? 2 : 1;
	}

	private static char at(int x, int y) {
		y = Math.max(0, Math.min(MAP.length - 1, y));
		x = Math.max(0, Math.min(MAP[0].length() - 1, x));
		return MAP[y].charAt(x);
	}

	/** Draws the globe, square, centred at {@code cx, cy} (pixels), {@code half} pixels from its middle to an edge. */
	private static void globe(PixelCanvas c, double cx, double cy, double half) {
		// The air: a soft blue glow round it.
		for(int y = (int)(cy - half - 6); y <= cy + half + 6; y++)
			for(int x = (int)(cx - half - 6); x <= cx + half + 6; x++) {
				double out = Math.max(Math.abs(x + 0.5 - cx), Math.abs(y + 0.5 - cy)) - half;
				if(out > 0 && out < 6)
					c.blend(x, y, 0x4A90E8, 0.45 * (1 - out / 6) * (1 - out / 6));
			}
		for(int y = (int)Math.floor(cy - half); y < cy + half; y++)
			for(int x = (int)Math.floor(cx - half); x < cx + half; x++) {
				double u = (x + 0.5 - (cx - half)) / (half * 2), v = (y + 0.5 - (cy - half)) / (half * 2);
				int colour = switch(ground(u, v)) {
					case 0 -> PixelCanvas.mix(0x0E2E6A, 0x1E58A8, PixelCanvas.fractal(u * 6, v * 6, 9L, 2));
					case 2 -> PixelCanvas.mix(0x9C7A40, 0xC8A464, PixelCanvas.fractal(u * 20, v * 20, 6L, 2));
					case 3 -> PixelCanvas.mix(0xD8E0E8, 0xFFFFFF, PixelCanvas.fractal(u * 20, v * 20, 8L, 2));
					default -> PixelCanvas.mix(0x2A5A22, 0x5A8A3A, PixelCanvas.fractal(u * 18, v * 18, 7L, 3));
				};
				double cloud = PixelCanvas.fractal(u * 7 + 3, v * 9, 12L, 4);
				colour = PixelCanvas.mix(colour, 0xF4F6FA, Math.max(0, (cloud - 0.55) * 2.6));
				// Lit from the upper left; the edges round off a little, so it reads as a globe made square.
				double light = 1.15 - 0.55 * (u + v) / 2;
				double edge = Math.max(Math.abs(u - 0.5), Math.abs(v - 0.5)) * 2;
				light *= 1 - 0.35 * Math.pow(edge, 6);
				c.set(x, y, PixelCanvas.shade(colour, light));
			}
	}

	/**
	 * Draws the view filling {@code c} (black space and stars first): the slice ({@code halfSlice} radians each side of
	 * straight up, none below 0), the orbit, the globe and its station, and the satellite at {@code angle}
	 * ({@code Double.NaN} for none), bright while {@code up}.
	 */
	public static void draw(PixelCanvas c, double halfSlice, double angle, boolean up) {
		double cx = c.width / 2.0D, cy = c.height / 2.0D;
		double r = Math.min(cx, cy) - 8.0D;
		// The slice in view: lighter, fading out past the orbit.
		if(halfSlice > 0.0D)
			for(int y = 0; y < c.height; y++)
				for(int x = 0; x < c.width; x++) {
					double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
					double d = Math.hypot(dx, dy) / r;
					double a = Math.atan2(dx, -dy);
					double inside = (halfSlice - Math.abs(a)) * r * 0.5; // Pixels in from the slice's side, roughly.
					if(inside > -1 && d < 1.25)
						c.blend(x, y, 0x2A4E86, 0.55 * Math.min(1, inside + 1) * Math.min(1, (1.25 - d) * 4));
				}
		// The orbit, a dotted ring, bright where in view.
		int dots = (int)(r * 2.2);
		for(int i = 0; i < dots; i++) {
			double a = Math.PI * 2 * i / dots;
			double wrapped = Math.atan2(Math.sin(a), Math.cos(a));
			boolean lit = halfSlice > 0.0D && Math.abs(wrapped) <= halfSlice;
			int x = (int)Math.round(cx + Math.sin(a) * r), y = (int)Math.round(cy - Math.cos(a) * r);
			c.set(x, y, lit ? 0x8CE8FF : 0x50586A);
		}
		double half = r * 0.42;
		globe(c, cx, cy, half);
		// The station: a mast on top of the world, a red light on it.
		int sx = (int)Math.round(cx), top = (int)Math.round(cy - half);
		for(int y = top - 7; y < top; y++)
			c.set(sx, y, 0xD8D8D8);
		for(int x = sx - 2; x <= sx + 2; x++)
			c.set(x, top - 5, 0xD8D8D8);
		c.set(sx, top - 8, 0xFF4030);
		// The satellite: a body and two panels, glowing while it is in view.
		if(!Double.isNaN(angle)) {
			double px = cx + Math.sin(angle) * r, py = cy - Math.cos(angle) * r;
			if(up)
				for(int y = (int)py - 6; y <= py + 6; y++)
					for(int x = (int)px - 6; x <= px + 6; x++) {
						double d = Math.hypot(x + 0.5 - px, y + 0.5 - py);
						if(d < 6)
							c.blend(x, y, 0xFFF0A0, 0.35 * (1 - d / 6));
					}
			int body = up ? 0xFFE060 : 0xA0A0A0, panel = up ? 0x4A7AE0 : 0x404860;
			for(int y = -1; y <= 1; y++)
				for(int x = -1; x <= 1; x++)
					c.set((int)Math.round(px) + x, (int)Math.round(py) + y, body);
			for(int x = 2; x <= 5; x++)
				for(int y = -1; y <= 1; y++) {
					c.set((int)Math.round(px) + x, (int)Math.round(py) + y, panel);
					c.set((int)Math.round(px) - x, (int)Math.round(py) + y, panel);
				}
		}
	}
}
