package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.PixelCanvas;

import java.util.ArrayList;
import java.util.List;

/**
 * SATTRACK's Image view (2026-11-13): each kind of satellite as a small 3D model, ray traced in full colour as it
 * tumbles. Built of boxes only and shaded the way Minecraft shades blocks (2026-11-14, the maintainer: "a bit more
 * minecraft-y, cubes instead of perfect spheres"): spheres are cubes, drums square prisms, rods thin square beams,
 * cones stacked boxes; each face one flat light (top brightest, the sides less, the bottom darkest) with a 16 by 16
 * pixel texture. The six kinds a launch picks from ({@code Satellite.ART_KINDS}), after the maintainer's photos, none
 * a copy of any one craft: a polished cube trailing four long whips; a square drum covered in solar cells; a spinner
 * of blue cells with a copper stepped cone and gold whips; a small cube with six rods; a gold box with two solar wings
 * and an antenna array; a cubesat. The Moon, still, for moonbounce: a cube too. Pure (no game classes), so it is unit
 * tested.
 */
public final class SatelliteModels {

	/** Names for the kinds, for the caption ({@code screen.radiocraft.computer.sat.art.<name>}). */
	public static final String[] KINDS = {"cube", "drum", "spinner", "small_cube", "winged", "cubesat"};
	/** Frames in one turn. */
	public static final int FRAMES = 72;
	/** Texture pixels across a face. */
	private static final int TEXELS = 16;

	private SatelliteModels() {}

	// -------------------- VECTORS --------------------

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static double[] sub(double[] a, double[] b) {
		return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}

	private static double[] add(double[] a, double[] b, double s) {
		return new double[] {a[0] + b[0] * s, a[1] + b[1] * s, a[2] + b[2] * s};
	}

	private static double[] norm(double[] a) {
		double l = Math.sqrt(dot(a, a));
		return l < 1e-12 ? new double[] {0, 1, 0} : new double[] {a[0] / l, a[1] / l, a[2] / l};
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private static double[] v(double x, double y, double z) {
		return new double[] {x, y, z};
	}

	// -------------------- SHAPES --------------------

	/**
	 * What a surface looks like: its colour at face coordinates {@code u, v} (each -1 to 1 across the face, already
	 * snapped to its texture pixels) and the point {@code p} (snapped too).
	 */
	interface Paint {
		int colour(double u, double v, double[] p);
	}

	record Material(Paint paint) {
		static Material flat(int colour) {
			return new Material((u, v, p) -> colour);
		}
	}

	/** A hit: distance along the ray, the normal, face coordinates, material and which face (for its texture). */
	record Hit(double t, double[] normal, double u, double v, Material material, int face) {}

	interface Shape {
		Hit hit(double[] o, double[] d);
	}

	/** A box: centre, half sizes along its own axes {@code ax, ay} (and their cross). */
	record Box(double[] c, double[] half, double[] ax, double[] ay, Material m) implements Shape {
		Box(double[] c, double[] half, Material m) {
			this(c, half, v(1, 0, 0), v(0, 1, 0), m);
		}

		public Hit hit(double[] o, double[] d) {
			double[][] axes = {norm(ax), norm(ay), norm(cross(ax, ay))};
			double[] oc = sub(o, c);
			double tmin = -1e18, tmax = 1e18;
			int face = -1;
			double sign = 1;
			for(int i = 0; i < 3; i++) {
				double e = dot(axes[i], oc), f = dot(axes[i], d);
				if(Math.abs(f) < 1e-12) {
					if(Math.abs(e) > half[i])
						return null;
					continue;
				}
				double t1 = (-e - half[i]) / f, t2 = (-e + half[i]) / f;
				double s = -1;
				if(t1 > t2) {
					double tmp = t1;
					t1 = t2;
					t2 = tmp;
					s = 1;
				}
				if(t1 > tmin) {
					tmin = t1;
					face = i;
					sign = s;
				}
				tmax = Math.min(tmax, t2);
				if(tmin > tmax)
					return null;
			}
			if(tmin < 1e-6 || face < 0)
				return null;
			double[] n = new double[] {axes[face][0] * sign, axes[face][1] * sign, axes[face][2] * sign};
			double[] p = sub(add(o, d, tmin), c);
			int ua = (face + 1) % 3, va = (face + 2) % 3;
			// Face coordinates from -1 to 1 across the face.
			return new Hit(tmin, n, dot(p, axes[ua]) / half[ua], dot(p, axes[va]) / half[va], m, face * 2 + (sign > 0 ? 1 : 0));
		}
	}

	private static double[] perpendicular(double[] w) {
		return norm(Math.abs(w[1]) < 0.9 ? cross(w, v(0, 1, 0)) : cross(w, v(1, 0, 0)));
	}

	/** A square beam from {@code a} to {@code b}, {@code half} thick each way: a rod, the Minecraft way. */
	static Shape beam(double[] a, double[] b, double half, Material m) {
		double[] axis = sub(b, a), w = norm(axis);
		double len = Math.sqrt(dot(axis, axis));
		return new Box(add(a, axis, 0.5), v(len / 2, half, half), w, perpendicular(w), m);
	}

	static Shape beam(double[] a, double[] b, double half, int colour) {
		return beam(a, b, half, Material.flat(colour));
	}

	/** A cone as {@code steps} stacked square slabs, from {@code a} ({@code ra} half wide) to {@code b} ({@code rb}). */
	static void stepped(List<Shape> s, double[] a, double[] b, double ra, double rb, int steps, Material m) {
		double[] axis = sub(b, a), w = norm(axis);
		double[] side = perpendicular(w);
		double len = Math.sqrt(dot(axis, axis)), slab = len / steps;
		for(int i = 0; i < steps; i++) {
			double r = ra + (rb - ra) * (i + 0.5) / steps;
			s.add(new Box(add(a, w, slab * (i + 0.5)), v(r, slab / 2, r), side, w, m));
		}
	}

	// -------------------- PAINTS --------------------

	private static double fract(double x) {
		return x - Math.floor(x);
	}

	private static double cellHash(int a, int b, int seed) {
		long h = a * 73856093L ^ b * 19349663L ^ seed * 83492791L;
		h ^= h >>> 13;
		h *= 0x5bd1e995L;
		h ^= h >>> 15;
		return (h & 0xFFFF) / 65536.0D;
	}

	/** Solar cells, {@code cells} to a face: dark squares, lighter lines between, a few left bare (white). */
	static Paint cells(int cell, int line, int cells, double bare) {
		return (u, v, p) -> {
			double a = (u + 1) / 2 * cells, b = (v + 1) / 2 * cells;
			if(fract(a) < 0.2 || fract(b) < 0.2)
				return line;
			double h = cellHash((int)Math.floor(a), (int)Math.floor(b), 7);
			if(h < bare)
				return 0xE8E8E0;
			return PixelCanvas.mix(cell, PixelCanvas.shade(cell, 1.5), cellHash((int)Math.floor(a), (int)Math.floor(b), 3) * 0.5);
		};
	}

	/** Crinkled gold foil, in blotches of texture pixels. */
	static Paint foil() {
		return (u, v, p) -> PixelCanvas.mix(0x8A6A18, 0xF0C850, PixelCanvas.fractal(p[0] * 6 + u * 2 + 3, p[1] * 6 + p[2] * 5 + v * 2, 11L, 2));
	}

	// -------------------- THE KINDS --------------------

	private static final int ROD = 0xC8C0D0, GOLD = 0xE0B040;
	private static final double WHIP = 0.02;

	/** The shapes of {@code kind} (-1 the Moon), centred on the origin, about 2.4 units across. */
	public static List<Shape> model(int kind) {
		List<Shape> s = new ArrayList<>();
		Material polished = new Material((u, vv, p) -> PixelCanvas.mix(0xB8B8C0, 0xE0E0E8, Math.max(0, 0.6 - Math.abs(u + vv) * 0.4)));
		switch(kind) {
			case -1 -> {
				Paint moon = (u, vv, p) -> {
					double maria = PixelCanvas.fractal(p[0] * 1.6 + 5, p[1] * 1.6 + p[2] * 1.3, 21L, 3);
					int base = PixelCanvas.mix(0xB4B2AC, 0x5E5C58, Math.max(0, (maria - 0.48) * 4));
					return cellHash((int)Math.round(u * 8), (int)Math.round(vv * 8), 5) < 0.06 ? PixelCanvas.shade(base, 0.7) : base; // A crater.
				};
				s.add(new Box(v(0, 0, 0), v(0.85, 0.85, 0.85), new Material(moon)));
			}
			case 0 -> { // A polished cube, four whips swept back.
				double[] c = v(0.25, 0.05, 0);
				s.add(new Box(c, v(0.36, 0.36, 0.36), new Material((u, vv, p) -> Math.abs(u) < 0.07 ? 0x505058 : polished.paint().colour(u, vv, p))));
				for(double[] tip : new double[][] {{-1.55, 0.55, 0.45}, {-1.55, 0.55, -0.45}, {-1.4, -0.6, 0.5}, {-1.4, -0.6, -0.5}}) {
					double[] corner = v(c[0] - 0.3, c[1] + Math.signum(tip[1]) * 0.3, Math.signum(tip[2]) * 0.3);
					s.add(beam(corner, tip, WHIP, ROD));
					s.add(beam(corner, add(corner, norm(sub(tip, corner)), 0.12), 0.045, 0x606068));
				}
			}
			case 1 -> { // A square drum of solar cells, whips above and below.
				Material cells = new Material(cells(0x14204A, 0x6070A0, 6, 0.06));
				s.add(new Box(v(0, 0, 0), v(0.6, 0.3, 0.6), cells));
				s.add(new Box(v(0, -0.33, 0), v(0.56, 0.03, 0.56), Material.flat(0xB8B8B8)));
				s.add(beam(v(0.1, 0.3, 0.1), v(0.5, 1.15, 0.2), WHIP, ROD));
				s.add(beam(v(-0.45, -0.34, 0.2), v(-0.85, -1.1, 0.4), WHIP, ROD));
				s.add(beam(v(0.35, -0.34, 0.4), v(0.55, -1.15, 0.7), WHIP, ROD));
				s.add(beam(v(0.3, -0.34, -0.4), v(0.7, -1.05, -0.8), WHIP, ROD));
			}
			case 2 -> { // A spinner: blue cells, a white band, a copper stepped cone, a mast and gold whips.
				s.add(new Box(v(0, 0.1, 0), v(0.44, 0.35, 0.44), new Material(cells(0x183C9A, 0xC8D0E8, 5, 0.0))));
				s.add(new Box(v(0, -0.315, 0), v(0.44, 0.065, 0.44), Material.flat(0xE8E4D8)));
				stepped(s, v(0, -0.38, 0), v(0, -0.85, 0), 0.12, 0.3, 4, Material.flat(0xB0602A));
				s.add(beam(v(0, 0.45, 0), v(0, 1.05, 0), 0.03, 0xD0D0C8));
				s.add(new Box(v(0, 0.965, 0), v(0.06, 0.02, 0.06), Material.flat(GOLD)));
				for(int i = 0; i < 4; i++) {
					double a = i * Math.PI / 2 + Math.PI / 4;
					s.add(beam(v(Math.cos(a) * 0.5, -0.38, Math.sin(a) * 0.5), v(Math.cos(a) * 1.05, -1.15, Math.sin(a) * 1.05), WHIP, GOLD));
				}
			}
			case 3 -> { // A small polished cube with six rods.
				s.add(new Box(v(0, 0, 0), v(0.22, 0.22, 0.22), polished));
				double[][] dirs = {{1, 0.5, 0.2}, {-1, 0.6, -0.1}, {0.3, -1, 0.4}, {-0.4, -0.9, -0.5}, {0.6, 0.2, -1}, {-0.2, 0.4, 1}};
				for(double[] dir : dirs)
					s.add(beam(add(v(0, 0, 0), norm(dir), 0.2), add(v(0, 0, 0), norm(dir), 1.2), WHIP, ROD));
				s.add(new Box(v(0.1, 0.08, 0.225), v(0.06, 0.05, 0.01), Material.flat(0x101828)));
				s.add(new Box(v(-0.1, -0.07, 0.225), v(0.06, 0.05, 0.01), Material.flat(0x101828)));
			}
			case 4 -> { // A gold box, two solar wings on a boom, an antenna array on its face, a nozzle under it.
				s.add(new Box(v(0, 0, 0), v(0.28, 0.26, 0.24), new Material(foil())));
				s.add(beam(v(-0.95, 0, 0), v(0.95, 0, 0), 0.03, 0xA0A0A0));
				Paint wing = (u, vv, p) -> {
					if(Math.abs(u) > 0.88 || Math.abs(vv) > 0.88 || Math.abs(fract((u + 1) * 1.5) - 0.5) > 0.44)
						return 0xE0E0E0;
					return PixelCanvas.mix(0x2A2622, 0x4A423A, PixelCanvas.noise(u * 3, vv * 3, 4L));
				};
				Material panels = new Material(wing);
				s.add(new Box(v(-0.95, 0, 0), v(0.6, 0.38, 0.015), v(1, 0, 0), v(0, 1, 0.35), panels));
				s.add(new Box(v(0.95, 0, 0), v(0.6, 0.38, 0.015), v(1, 0, 0), v(0, 1, 0.35), panels));
				s.add(new Box(v(0, 0, 0.28), v(0.17, 0.17, 0.04), Material.flat(0xB0B0B0)));
				for(int i = 0; i < 8; i++) {
					double a = i * Math.PI / 4;
					s.add(beam(v(Math.cos(a) * 0.11, Math.sin(a) * 0.11, 0.32), v(Math.cos(a) * 0.11, Math.sin(a) * 0.11, 0.48), 0.022, 0x9A6A30));
				}
				stepped(s, v(0.12, -0.26, -0.1), v(0.12, -0.42, -0.1), 0.04, 0.08, 2, Material.flat(0x9A9A9A));
			}
			default -> { // A cubesat: red frame round dark cells, rails on its edges, a whip.
				Paint face = (u, vv, p) -> {
					if(Math.abs(u) > 0.84 || Math.abs(vv) > 0.84 || Math.abs(u) < 0.07)
						return 0xC03028;
					double a = (u + 1) * 2, b = (vv + 1) * 1.5;
					if(fract(a) < 0.1 || fract(b) < 0.1)
						return 0x804040;
					return PixelCanvas.mix(0x0C1020, 0x283A70, 0.5 + 0.5 * Math.sin(u * 3 + vv * 2));
				};
				s.add(new Box(v(0, 0, 0), v(0.45, 0.45, 0.45), new Material(face)));
				for(int i = 0; i < 4; i++) {
					double x = (i & 1) == 0 ? -0.46 : 0.46, z = (i & 2) == 0 ? -0.46 : 0.46;
					s.add(new Box(v(x, 0, z), v(0.03, 0.5, 0.03), Material.flat(0xD0D0D8)));
				}
				s.add(beam(v(0.3, -0.45, 0.46), v(0.55, -1.2, 0.8), WHIP, ROD));
			}
		}
		return s;
	}

	// -------------------- RENDERING --------------------

	/** The turn at {@code frame} of {@link #FRAMES}: a spin on a tilted, nodding axis, as a rows-of-columns matrix. */
	public static double[][] turn(int kind, int frame) {
		double t = frame * Math.PI * 2 / FRAMES;
		if(kind < 0)
			t = 0.6; // The Moon keeps one face to us.
		double tilt = 0.45 + 0.2 * Math.sin(t * 1.0 + kind), lean = 0.25 * Math.cos(t + kind * 0.7);
		double[][] spin = {{Math.cos(t), 0, Math.sin(t)}, {0, 1, 0}, {-Math.sin(t), 0, Math.cos(t)}};
		double[][] rx = {{1, 0, 0}, {0, Math.cos(tilt), -Math.sin(tilt)}, {0, Math.sin(tilt), Math.cos(tilt)}};
		double[][] rz = {{Math.cos(lean), -Math.sin(lean), 0}, {Math.sin(lean), Math.cos(lean), 0}, {0, 0, 1}};
		return mul(rz, mul(rx, spin));
	}

	private static double[][] mul(double[][] a, double[][] b) {
		double[][] out = new double[3][3];
		for(int i = 0; i < 3; i++)
			for(int j = 0; j < 3; j++)
				out[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
		return out;
	}

	/** {@code m} times {@code p}. */
	private static double[] apply(double[][] m, double[] p) {
		return new double[] {dot(m[0], p), dot(m[1], p), dot(m[2], p)};
	}

	/** {@code m}'s transpose (its inverse, a rotation) times {@code p}. */
	private static double[] applyBack(double[][] m, double[] p) {
		return new double[] {m[0][0] * p[0] + m[1][0] * p[1] + m[2][0] * p[2], m[0][1] * p[0] + m[1][1] * p[1] + m[2][1] * p[2],
				m[0][2] * p[0] + m[1][2] * p[1] + m[2][2] * p[2]};
	}

	/** A face coordinate (-1 to 1) snapped to the middle of its texture pixel. */
	private static double texel(double u) {
		double cell = Math.floor((Math.max(-1, Math.min(0.9999, u)) + 1) / 2 * TEXELS);
		return (cell + 0.5) / TEXELS * 2 - 1;
	}

	/**
	 * How bright a face is, the way Minecraft lights blocks, from its normal as seen ({@code y} up the picture, {@code z}
	 * towards us): the top full, faces towards us 0.8, the sides 0.6, the bottom half; a slanted face in between.
	 */
	static double faceLight(double[] seen) {
		double x = seen[0] * seen[0], y = seen[1] * seen[1], z = seen[2] * seen[2];
		return x * 0.6 + z * 0.8 + y * (seen[1] > 0 ? 1.0 : 0.5);
	}

	/**
	 * Draws {@code kind} at {@code frame} over {@code c} (stars already on it): each pixel a ray from the viewer, into the
	 * model's own frame, to the nearest box, its texture pixel lit by its face's light. One ray a pixel: crisp edges.
	 */
	public static void render(PixelCanvas c, List<Shape> shapes, int kind, int frame) {
		double[][] m = turn(kind, frame);
		double half = 1.15; // Half the picture's height, in model units.
		double unit = c.height / (half * 2);
		double[] d = applyBack(m, v(0, 0, -1));
		for(int y = 0; y < c.height; y++)
			for(int x = 0; x < c.width; x++) {
				double wx = (x + 0.5 - c.width / 2.0) / unit, wy = -(y + 0.5 - c.height / 2.0) / unit;
				double[] o = applyBack(m, v(wx, wy, 10));
				Hit best = null;
				for(Shape shape : shapes) {
					Hit h = shape.hit(o, d);
					if(h != null && (best == null || h.t() < best.t()))
						best = h;
				}
				if(best == null)
					continue; // The stars.
				double u = texel(best.u()), vv = texel(best.v());
				double[] p = add(o, d, best.t());
				double[] snapped = {Math.floor(p[0] * 12) / 12, Math.floor(p[1] * 12) / 12, Math.floor(p[2] * 12) / 12};
				int base = best.material().paint().colour(u, vv, snapped);
				// The texture's grain: each pixel a shade lighter or darker, as a block's texture.
				int iu = (int)Math.floor((u + 1) / 2 * TEXELS), iv = (int)Math.floor((vv + 1) / 2 * TEXELS);
				double grain = 0.92 + 0.16 * cellHash(iu, iv, best.face() + kind * 7 + 31);
				double light = faceLight(apply(m, best.normal()));
				c.set(x, y, PixelCanvas.shade(base, light * grain));
			}
	}

	/**
	 * Where the satellite's light is in the picture at {@code frame}, {x, y} pixels, or null where the body hides it
	 * (2026-11-14, the maintainer: the model blinks the light the satellite flashes in the sky): a small lamp on top of
	 * the body (the first box), a little to one side. The Moon has none.
	 */
	public static int[] lightAt(List<Shape> shapes, int kind, int frame, int width, int height) {
		if(kind < 0 || shapes.isEmpty() || !(shapes.getFirst() instanceof Box body))
			return null;
		double[] up = norm(body.ay()), side = norm(body.ax());
		double[] lamp = add(add(body.c(), up, body.half()[1] + 0.03), side, body.half()[0] * 0.45);
		double[][] m = turn(kind, frame);
		double[] seen = apply(m, lamp);
		double half = 1.15, unit = height / (half * 2);
		double px = seen[0] * unit + width / 2.0, py = -seen[1] * unit + height / 2.0;
		if(px < 0 || py < 0 || px >= width || py >= height)
			return null;
		// Hidden if the ray to it from the viewer meets the model first.
		double[] o = applyBack(m, v(seen[0], seen[1], 10)), d = applyBack(m, v(0, 0, -1));
		double lampT = 10 - seen[2];
		for(Shape shape : shapes) {
			Hit h = shape.hit(o, d);
			if(h != null && h.t() < lampT - 0.04)
				return null;
		}
		return new int[] {(int)Math.floor(px), (int)Math.floor(py)};
	}

	/** A whole picture: stars, then {@code kind} at {@code frame}. */
	public static PixelCanvas picture(int width, int height, int kind, int frame, long seed) {
		PixelCanvas c = new PixelCanvas(width, height);
		c.starfield(seed);
		render(c, model(kind), kind, frame);
		return c;
	}
}
