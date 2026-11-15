package com.arrl.radiocraft.client.screens.computer;

import java.util.Arrays;

/**
 * A full colour picture for a DOS program's picture area (2026-11-13, SATTRACK's Image and Location views), drawn in
 * RGB and then matched to map colours for {@link TextGrid#image}. Shapes are drawn on a design frame of
 * {@link #FRAME_W} x {@link #FRAME_H} units, scaled to fit the picture and centred, so one drawing suits any size.
 * Pure (no game classes), so it is unit tested.
 */
public final class PixelCanvas {

	/** The design frame shapes are laid out on. */
	public static final double FRAME_W = 300.0D, FRAME_H = 200.0D;

	public final int width, height;
	private final int[] rgb;
	private final double scale, offsetX, offsetY;

	public PixelCanvas(int width, int height) {
		this.width = width;
		this.height = height;
		this.rgb = new int[width * height];
		this.scale = Math.min(width / FRAME_W, height / FRAME_H);
		this.offsetX = (width - FRAME_W * scale) / 2.0D;
		this.offsetY = (height - FRAME_H * scale) / 2.0D;
	}

	/** A copy, to draw over without spoiling this. */
	public PixelCanvas copy() {
		PixelCanvas c = new PixelCanvas(width, height);
		System.arraycopy(rgb, 0, c.rgb, 0, rgb.length);
		return c;
	}

	// -------------------- FRAME TO PIXELS --------------------

	public double px(double x) {
		return offsetX + x * scale;
	}

	public double py(double y) {
		return offsetY + y * scale;
	}

	/** A length on the frame, in pixels. */
	public double len(double units) {
		return units * scale;
	}

	/** Pixel to frame. */
	public double fx(double px) {
		return (px - offsetX) / scale;
	}

	public double fy(double py) {
		return (py - offsetY) / scale;
	}

	// -------------------- PIXELS --------------------

	public void fill(int colour) {
		Arrays.fill(rgb, colour & 0xFFFFFF);
	}

	public int get(int x, int y) {
		return x < 0 || y < 0 || x >= width || y >= height ? 0 : rgb[y * width + x];
	}

	public void set(int x, int y, int colour) {
		if(x >= 0 && y >= 0 && x < width && y < height)
			rgb[y * width + x] = colour & 0xFFFFFF;
	}

	/** Mixes {@code colour} over the pixel by {@code alpha} (0 to 1). */
	public void blend(int x, int y, int colour, double alpha) {
		if(x < 0 || y < 0 || x >= width || y >= height || alpha <= 0.0D)
			return;
		rgb[y * width + x] = mix(rgb[y * width + x], colour, Math.min(1.0D, alpha));
	}

	public int[] pixels() {
		return rgb;
	}

	/** {@code a} to {@code b} by {@code t}. */
	public static int mix(int a, int b, double t) {
		t = Math.max(0.0D, Math.min(1.0D, t));
		int r = (int)Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = (int)Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int)Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return r << 16 | g << 8 | bl;
	}

	/** {@code colour} made lighter (above 1) or darker (below 1). */
	public static int shade(int colour, double by) {
		int r = (int)Math.min(255, Math.round(((colour >> 16) & 0xFF) * by));
		int g = (int)Math.min(255, Math.round(((colour >> 8) & 0xFF) * by));
		int b = (int)Math.min(255, Math.round((colour & 0xFF) * by));
		return r << 16 | g << 8 | b;
	}

	// -------------------- SHAPES (frame units) --------------------

	/** A line {@code thick} units wide. */
	public void line(double x0, double y0, double x1, double y1, double thick, int colour) {
		double ax = px(x0), ay = py(y0), bx = px(x1), by = py(y1), r = Math.max(0.5D, len(thick) / 2.0D);
		int minX = (int)Math.floor(Math.min(ax, bx) - r), maxX = (int)Math.ceil(Math.max(ax, bx) + r);
		int minY = (int)Math.floor(Math.min(ay, by) - r), maxY = (int)Math.ceil(Math.max(ay, by) + r);
		double dx = bx - ax, dy = by - ay, l2 = Math.max(1e-9, dx * dx + dy * dy);
		for(int y = Math.max(0, minY); y <= Math.min(height - 1, maxY); y++)
			for(int x = Math.max(0, minX); x <= Math.min(width - 1, maxX); x++) {
				double cx = x + 0.5D, cy = y + 0.5D;
				double t = Math.max(0.0D, Math.min(1.0D, ((cx - ax) * dx + (cy - ay) * dy) / l2));
				double d = Math.hypot(cx - (ax + dx * t), cy - (ay + dy * t));
				if(d <= r + 0.5D)
					blend(x, y, colour, Math.min(1.0D, r + 0.5D - d));
			}
	}

	/** Something drawn pixel by pixel: {@code u, v} from -1 to 1 across a shape, returning a colour or -1 for none. */
	public interface Shader {
		int colour(double u, double v, int x, int y);
	}

	/** An ellipse at {@code cx, cy} with radii {@code rx, ry}, coloured by {@code shader} (u, v from -1 to 1). */
	public void ellipse(double cx, double cy, double rx, double ry, Shader shader) {
		double pcx = px(cx), pcy = py(cy), prx = len(rx), pry = len(ry);
		for(int y = (int)Math.floor(pcy - pry); y <= Math.ceil(pcy + pry); y++)
			for(int x = (int)Math.floor(pcx - prx); x <= Math.ceil(pcx + prx); x++) {
				double u = (x + 0.5D - pcx) / prx, v = (y + 0.5D - pcy) / pry;
				double d = u * u + v * v;
				if(d > 1.0D)
					continue;
				int c = shader.colour(u, v, x, y);
				if(c >= 0) {
					double edge = (1.0D - Math.sqrt(d)) * Math.min(prx, pry); // Soft rim, a pixel wide.
					blend(x, y, c, Math.min(1.0D, edge + 0.3D));
				}
			}
	}

	public void ellipse(double cx, double cy, double rx, double ry, int colour) {
		ellipse(cx, cy, rx, ry, (u, v, x, y) -> colour);
	}

	/**
	 * A ball lit from the upper left, from {@code dark} to {@code light}, with a white glint ({@code shine} 0 to 1).
	 */
	public void ball(double cx, double cy, double r, int dark, int light, double shine) {
		ellipse(cx, cy, r, r, (u, v, x, y) -> {
			double z = Math.sqrt(Math.max(0.0D, 1.0D - u * u - v * v));
			double lambert = Math.max(0.0D, -u * 0.5D - v * 0.6D + z * 0.62D);
			int c = mix(dark, light, lambert);
			double glint = Math.pow(Math.max(0.0D, -u * 0.45D - v * 0.55D + z * 0.7D), 24.0D) * shine;
			return mix(c, 0xFFFFFF, glint);
		});
	}

	/** A polygon (frame points x0, y0, x1, y1...) coloured by {@code shader} (u, v from the polygon's box, -1 to 1). */
	public void polygon(Shader shader, double... xy) {
		int n = xy.length / 2;
		double[] p = new double[xy.length];
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		for(int i = 0; i < n; i++) {
			p[i * 2] = px(xy[i * 2]);
			p[i * 2 + 1] = py(xy[i * 2 + 1]);
			minX = Math.min(minX, p[i * 2]);
			maxX = Math.max(maxX, p[i * 2]);
			minY = Math.min(minY, p[i * 2 + 1]);
			maxY = Math.max(maxY, p[i * 2 + 1]);
		}
		for(int y = Math.max(0, (int)Math.floor(minY)); y <= Math.min(height - 1, (int)Math.ceil(maxY)); y++) {
			double cy = y + 0.5D;
			for(int x = Math.max(0, (int)Math.floor(minX)); x <= Math.min(width - 1, (int)Math.ceil(maxX)); x++) {
				double cx = x + 0.5D;
				boolean in = false;
				for(int i = 0, j = n - 1; i < n; j = i++) {
					double xi = p[i * 2], yi = p[i * 2 + 1], xj = p[j * 2], yj = p[j * 2 + 1];
					if((yi > cy) != (yj > cy) && cx < (xj - xi) * (cy - yi) / (yj - yi) + xi)
						in = !in;
				}
				if(!in)
					continue;
				double u = maxX > minX ? (cx - minX) / (maxX - minX) * 2.0D - 1.0D : 0.0D;
				double v = maxY > minY ? (cy - minY) / (maxY - minY) * 2.0D - 1.0D : 0.0D;
				int c = shader.colour(u, v, x, y);
				if(c >= 0)
					set(x, y, c);
			}
		}
	}

	public void polygon(int colour, double... xy) {
		polygon((u, v, x, y) -> colour, xy);
	}

	// -------------------- NOISE --------------------

	private static double hash(int x, int y, long seed) {
		long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + y * 0x165667B19E3779F9L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (h & 0xFFFFFFL) / (double)0x1000000;
	}

	/** Smooth value noise, 0 to 1, features about a unit apart. */
	public static double noise(double x, double y, long seed) {
		int x0 = (int)Math.floor(x), y0 = (int)Math.floor(y);
		double tx = x - x0, ty = y - y0;
		tx = tx * tx * (3 - 2 * tx);
		ty = ty * ty * (3 - 2 * ty);
		double a = hash(x0, y0, seed), b = hash(x0 + 1, y0, seed), c = hash(x0, y0 + 1, seed), d = hash(x0 + 1, y0 + 1, seed);
		return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
	}

	/** Noise in octaves, 0 to 1. */
	public static double fractal(double x, double y, long seed, int octaves) {
		double sum = 0.0D, amp = 1.0D, total = 0.0D;
		for(int i = 0; i < octaves; i++) {
			sum += noise(x, y, seed + i * 31L) * amp;
			total += amp;
			x *= 2.03D;
			y *= 2.03D;
			amp *= 0.5D;
		}
		return sum / total;
	}

	/** Stars on black: a scatter of points, a few bright with a soft glow. */
	public void starfield(long seed) {
		fill(0x05060A);
		java.util.Random random = new java.util.Random(seed);
		int count = width * height / 180;
		for(int i = 0; i < count; i++) {
			int x = random.nextInt(width), y = random.nextInt(height);
			double bright = random.nextDouble();
			int c = mix(0x404858, 0xFFFFFF, bright * bright);
			set(x, y, c);
			if(bright > 0.93D) {
				blend(x - 1, y, c, 0.4D);
				blend(x + 1, y, c, 0.4D);
				blend(x, y - 1, c, 0.4D);
				blend(x, y + 1, c, 0.4D);
			}
		}
	}
}
