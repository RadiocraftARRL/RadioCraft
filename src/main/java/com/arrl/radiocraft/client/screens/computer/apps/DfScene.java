package com.arrl.radiocraft.client.screens.computer.apps;

import java.util.Arrays;
import java.util.List;

/**
 * The direction finding program's accuracy view (1.12 step 7): the search turning slowly in 3D, in wireframe, the way
 * the DF software of the 1990s showed it. The ground is a grid round the fix; each bearing is a wedge across it from its
 * station's mast (as wide as its error either side); the fix is a hill of likelihood over the ground, a bell as wide as
 * the fix's error, so a sharp spike is a good fix and a low wide mound a poor one. With no fix the wedges are drawn
 * alone. Drawn into a picture of map colour ids. Pure (no game classes), so it is unit tested.
 */
public final class DfScene {

	/** The colours to draw in (map colour ids). */
	public record Palette(byte background, byte grid, byte mast, byte peak, byte hill, byte text) {}

	private static final double ELEVATION = Math.toRadians(28.0D);
	private static final int RINGS = 6, SPOKES = 16;

	private DfScene() {}

	/**
	 * Draws the scene into {@code buf} ({@code w} by {@code h}), turned {@code angle} radians about the vertical. The
	 * fix is at {@code fixX, fixZ} with {@code radius} (blocks) if {@code fixed}; otherwise the view centres on the
	 * stations.
	 */
	public static void render(byte[] buf, int w, int h, double angle, List<DfDrawing.Bearing> bearings, boolean fixed, double fixX, double fixZ,
			double radius, Palette palette) {
		Arrays.fill(buf, palette.background());
		if(bearings.isEmpty())
			return;
		// The middle of the view and how far it reaches.
		double mx, mz;
		if(fixed) {
			mx = fixX;
			mz = fixZ;
		}
		else {
			mx = 0;
			mz = 0;
			for(DfDrawing.Bearing bearing : bearings) {
				mx += bearing.x() / bearings.size();
				mz += bearing.z() / bearings.size();
			}
		}
		double extent = fixed ? Math.max(radius * 4.0D, 16.0D) : 64.0D;
		for(DfDrawing.Bearing bearing : bearings)
			extent = Math.max(extent, Math.hypot(bearing.x() - mx, bearing.z() - mz) * 1.15D);
		Camera camera = new Camera(w, h, angle, extent);

		// The ground.
		int lines = 8;
		for(int i = -lines; i <= lines; i++) {
			double t = extent * i / lines;
			camera.line(buf, t, 0, -extent, t, 0, extent, palette.grid(), 2);
			camera.line(buf, -extent, 0, t, extent, 0, t, palette.grid(), 2);
		}

		// Each bearing: a mast at the station and a wedge along the ground.
		double reach = extent * 2.2D;
		for(DfDrawing.Bearing bearing : bearings) {
			double sx = bearing.x() - mx, sz = bearing.z() - mz;
			camera.line(buf, sx, 0, sz, sx, extent * 0.18D, sz, palette.mast(), 0);
			for(int side = -1; side <= 1; side++) {
				double a = Math.toRadians(bearing.bearing() + side * bearing.sigma());
				camera.line(buf, sx, 0, sz, sx + Math.sin(a) * reach, 0, sz - Math.cos(a) * reach, bearing.colour(), side == 0 ? 0 : 3);
			}
		}

		// The fix: a bell of likelihood, rings and spokes.
		if(fixed) {
			double height = extent * 0.9D * Math.min(1.0D, 8.0D / Math.max(1.0D, radius)) + extent * 0.1D; // A tight fix stands tall.
			double outer = radius * 2.5D;
			double[] ringX = new double[SPOKES + 1], ringZ = new double[SPOKES + 1];
			for(int s = 0; s <= SPOKES; s++) {
				double a = 2 * Math.PI * s / SPOKES;
				ringX[s] = Math.cos(a);
				ringZ[s] = Math.sin(a);
			}
			for(int r = 1; r <= RINGS; r++) {
				double d = outer * r / RINGS;
				double y = bell(d, radius) * height;
				byte colour = r <= 2 ? palette.peak() : palette.hill();
				for(int s = 0; s < SPOKES; s++)
					camera.line(buf, ringX[s] * d, y, ringZ[s] * d, ringX[s + 1] * d, y, ringZ[s + 1] * d, colour, 0);
			}
			for(int s = 0; s < SPOKES; s += 2) {
				double lastD = 0, lastY = height;
				for(int r = 1; r <= RINGS; r++) {
					double d = outer * r / RINGS, y = bell(d, radius) * height;
					camera.line(buf, ringX[s] * lastD, lastY, ringZ[s] * lastD, ringX[s] * d, y, ringZ[s] * d, palette.hill(), 0);
					lastD = d;
					lastY = y;
				}
			}
			camera.line(buf, 0, 0, 0, 0, height, 0, palette.peak(), 1); // The peak's plumb line.
		}
	}

	/** How likely the transmitter is {@code d} blocks from the fix, 0 to 1 (a normal curve as wide as the fix's error). */
	public static double bell(double d, double radius) {
		double s = Math.max(1.0D, radius);
		return Math.exp(-(d * d) / (2.0D * s * s));
	}

	/** The view: an orbit about the middle, looking down a little, in perspective. */
	private static final class Camera {
		private final int w, h;
		private final double cos, sin, focal, distance;

		Camera(int w, int h, double angle, double extent) {
			this.w = w;
			this.h = h;
			this.cos = Math.cos(angle);
			this.sin = Math.sin(angle);
			this.distance = extent * 3.2D;
			this.focal = Math.min(w, h) * 1.35D;
		}

		/** Screen x, y of a world point (relative to the middle; y up), or NaN behind the camera. */
		private double[] project(double x, double y, double z) {
			double rx = x * cos - z * sin, rz = x * sin + z * cos;
			double vy = y * Math.cos(ELEVATION) - rz * Math.sin(ELEVATION);
			double depth = y * Math.sin(ELEVATION) + rz * Math.cos(ELEVATION) + distance;
			if(depth < 1.0D)
				return new double[] { Double.NaN, Double.NaN };
			return new double[] { w / 2.0D + focal * rx / depth, h * 0.58D - focal * vy / depth };
		}

		void line(byte[] buf, double x0, double y0, double z0, double x1, double y1, double z1, byte colour, int dash) {
			double[] a = project(x0, y0, z0), b = project(x1, y1, z1);
			if(Double.isNaN(a[0]) || Double.isNaN(b[0]))
				return;
			DfDrawing.line(buf, w, h, a[0], a[1], b[0], b[1], colour, dash);
		}
	}
}
