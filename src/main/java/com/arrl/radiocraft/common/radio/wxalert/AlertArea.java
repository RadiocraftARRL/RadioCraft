package com.arrl.radiocraft.common.radio.wxalert;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The area a weather alert generator covers: the convex hull of the weather stations it hears, widened by
 * {@code margin} blocks all round. A point is in it if it is inside the hull or within the margin of it. Pure.
 */
public final class AlertArea {

	/** Blocks the area reaches past the outermost weather stations. */
	public static final int DEFAULT_MARGIN = 200;

	private final List<double[]> hull;
	private final double margin;
	private final double centreX, centreZ, radius;

	private AlertArea(List<double[]> hull, double margin) {
		this.hull = hull;
		this.margin = margin;
		double sx = 0, sz = 0;
		for(double[] p : hull) {
			sx += p[0];
			sz += p[1];
		}
		centreX = hull.isEmpty() ? 0 : sx / hull.size();
		centreZ = hull.isEmpty() ? 0 : sz / hull.size();
		double far = 0;
		for(double[] p : hull)
			far = Math.max(far, Math.hypot(p[0] - centreX, p[1] - centreZ));
		radius = far + margin;
	}

	/** The area round {@code points} (each {x, z}), {@code margin} blocks past them. */
	public static AlertArea around(List<double[]> points, double margin) {
		return new AlertArea(hull(points), margin);
	}

	/** The middle of the stations' hull (the average of its corners). */
	public double centreX() {
		return centreX;
	}

	public double centreZ() {
		return centreZ;
	}

	/** A circle round {@link #centreX()}, {@link #centreZ()} holding the whole area, blocks. */
	public double radius() {
		return radius;
	}

	public double margin() {
		return margin;
	}

	/** The hull's corners, anticlockwise (in x, z). */
	public List<double[]> corners() {
		return hull;
	}

	/** Whether ({@code x}, {@code z}) is in the area. */
	public boolean contains(double x, double z) {
		return distance(x, z) <= margin;
	}

	/** Whether a circle of {@code r} blocks round ({@code x}, {@code z}) reaches the area. */
	public boolean touches(double x, double z, double r) {
		return distance(x, z) <= margin + r;
	}

	/** Blocks from ({@code x}, {@code z}) to the stations' hull, 0 inside it. */
	public double distance(double x, double z) {
		int n = hull.size();
		if(n == 0)
			return Double.MAX_VALUE;
		if(n == 1)
			return Math.hypot(x - hull.getFirst()[0], z - hull.getFirst()[1]);
		if(n >= 3 && inside(x, z))
			return 0.0D;
		double best = Double.MAX_VALUE;
		for(int i = 0; i < n; i++) {
			double[] a = hull.get(i), b = hull.get((i + 1) % n);
			best = Math.min(best, segment(x, z, a, b));
		}
		return best;
	}

	private boolean inside(double x, double z) {
		int n = hull.size();
		for(int i = 0; i < n; i++) {
			double[] a = hull.get(i), b = hull.get((i + 1) % n);
			if(cross(a, b, x, z) < 0)
				return false;
		}
		return true;
	}

	private static double segment(double x, double z, double[] a, double[] b) {
		double dx = b[0] - a[0], dz = b[1] - a[1];
		double length = dx * dx + dz * dz;
		double t = length == 0 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * dx + (z - a[1]) * dz) / length));
		return Math.hypot(x - (a[0] + t * dx), z - (a[1] + t * dz));
	}

	private static double cross(double[] o, double[] a, double x, double z) {
		return (a[0] - o[0]) * (z - o[1]) - (a[1] - o[1]) * (x - o[0]);
	}

	/** The convex hull of {@code points}, anticlockwise, without repeated or collinear points (Andrew's monotone chain). */
	static List<double[]> hull(List<double[]> points) {
		List<double[]> sorted = new ArrayList<>(points);
		sorted.sort(Comparator.<double[]>comparingDouble(p -> p[0]).thenComparingDouble(p -> p[1]));
		List<double[]> unique = new ArrayList<>();
		for(double[] p : sorted)
			if(unique.isEmpty() || unique.getLast()[0] != p[0] || unique.getLast()[1] != p[1])
				unique.add(p);
		if(unique.size() <= 2)
			return unique;
		List<double[]> out = new ArrayList<>();
		for(int pass = 0; pass < 2; pass++) {
			int start = out.size();
			for(int i = 0; i < unique.size(); i++) {
				double[] p = unique.get(pass == 0 ? i : unique.size() - 1 - i);
				while(out.size() >= start + 2 && cross(out.get(out.size() - 2), out.getLast(), p[0], p[1]) <= 0)
					out.removeLast();
				out.add(p);
			}
			out.removeLast(); // The first point of the other half.
		}
		return out;
	}
}
