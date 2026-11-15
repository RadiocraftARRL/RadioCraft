package com.arrl.radiocraft.common.radio.satellite.imaging;

import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Random;

/**
 * One scan line of a weather satellite's picture on the air (2026-11-12). The packet ({@code TncPacket.Kind.APT}) carries
 * where the line lies in its text ({@link Geometry#encode}); its pixels stay on the server ({@code AptLines}) under the
 * packet's id, as the satellite's own copy. The radio that decodes it adds how far above its copy threshold it was
 * ({@link #withQuality}), and the receiving program takes the pixels through {@link #degrade}: like real APT, a strong
 * signal gives a clean line, a weak one a snowy line, and near the noise lines go missing. Pure, unit tested.
 */
public final class AptLine {

	/** Below this quality (strength over the copy threshold) the line is lost altogether. */
	public static final double FLOOR = 0.4D;
	/** At or above this quality the line is clean. */
	public static final double CLEAN = 1.5D;
	/** The most of a line that turns to noise just above the floor. */
	public static final double MAX_NOISE = 0.85D;

	private static final byte[] NOISE = {
			MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.LOWEST), MapColor.COLOR_GRAY.getPackedId(MapColor.Brightness.LOW),
			MapColor.COLOR_LIGHT_GRAY.getPackedId(MapColor.Brightness.NORMAL), MapColor.SNOW.getPackedId(MapColor.Brightness.NORMAL),
			MapColor.SNOW.getPackedId(MapColor.Brightness.HIGH) };

	private AptLine() {}

	/**
	 * Where line {@code seq} lies: its centre {@code cx, cz} (the point under the satellite) and its right vector
	 * {@code rx, rz} ({@link AptSwath}).
	 */
	public record Geometry(int seq, int cx, int cz, double rx, double rz, int frame, int row, int rows) {

		/** An orbiting imager's line: no frame. */
		public Geometry(int seq, int cx, int cz, double rx, double rz) {
			this(seq, cx, cz, rx, rz, -1, -1, 0);
		}

		/**
		 * True for a geostationary imager's frame line (2026-11-14): row {@code row} of {@code rows} of frame
		 * {@code frame}, a square grid sent north row first; {@link #frameEnd} for the end-of-frame signal after its
		 * last row.
		 */
		public boolean inFrame() {
			return frame >= 0;
		}

		/** True for the end-of-frame signal: a frame's row {@code rows}, past its last, with no pixels. */
		public boolean frameEnd() {
			return frame >= 0 && row >= rows;
		}

		/** As packet text: {@code seq|cx|cz|rx|rz}, and {@code |g<frame>,<row>,<rows>} for a frame line. */
		public String encode() {
			String base = String.format(Locale.ROOT, "%d|%d|%d|%.4f|%.4f", seq, cx, cz, rx, rz);
			return frame < 0 ? base : base + String.format(Locale.ROOT, "|g%d,%d,%d", frame, row, rows);
		}

		/** The geometry in packet {@code text} (with or without a quality after it), or null if it is not one. */
		@Nullable
		public static Geometry decode(String text) {
			if(text == null)
				return null;
			String[] parts = text.split("\\|");
			if(parts.length < 5)
				return null;
			try {
				double rx = Double.parseDouble(parts[3]), rz = Double.parseDouble(parts[4]);
				if(!Double.isFinite(rx) || !Double.isFinite(rz) || Math.abs(Math.hypot(rx, rz) - 1.0D) > 0.01D)
					return null;
				int frame = -1, row = -1, rows = 0;
				for(int i = 5; i < parts.length; i++)
					if(parts[i].startsWith("g")) {
						String[] f = parts[i].substring(1).split(",");
						if(f.length == 3) {
							frame = Integer.parseInt(f[0]);
							row = Integer.parseInt(f[1]);
							rows = Integer.parseInt(f[2]);
						}
					}
				return new Geometry(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), rx, rz, frame, row, rows);
			}
			catch(NumberFormatException e) {
				return null;
			}
		}
	}

	/** {@code text} with the quality the decoding radio measured added, {@code ...|q1.23}. */
	public static String withQuality(String text, double quality) {
		return text + String.format(Locale.ROOT, "|q%.2f", Math.max(0.0D, Math.min(99.0D, quality)));
	}

	/** The quality in {@code text} ({@link #withQuality}), or -1 if none was added. */
	public static double quality(String text) {
		if(text == null)
			return -1.0D;
		int at = text.lastIndexOf("|q");
		if(at < 0)
			return -1.0D;
		try {
			return Double.parseDouble(text.substring(at + 2));
		}
		catch(NumberFormatException e) {
			return -1.0D;
		}
	}

	/** The share of a line's pixels that come out as noise at {@code quality}: none from {@link #CLEAN}, most at the floor. */
	public static double noiseFraction(double quality) {
		if(quality >= CLEAN)
			return 0.0D;
		double t = (CLEAN - quality) / (CLEAN - FLOOR);
		return Math.max(0.0D, Math.min(MAX_NOISE, t * t * MAX_NOISE));
	}

	/** The chance a whole line goes missing at {@code quality}: certain under the floor, none from 1 (the copy threshold). */
	public static double lossChance(double quality) {
		if(quality < FLOOR)
			return 1.0D;
		if(quality >= 1.0D)
			return 0.0D;
		return 0.6D * (1.0D - quality) / (1.0D - FLOOR);
	}

	/**
	 * {@code clean} as a station copying it at {@code quality} gets it: the same line, some of it snow, or null if it was
	 * lost. {@code seed} fixes the noise (the packet and the station), so a test sees the same line each time.
	 */
	@Nullable
	public static byte[] degrade(byte[] clean, double quality, long seed) {
		Random random = new Random(seed);
		if(random.nextDouble() < lossChance(quality))
			return null;
		byte[] out = clean.clone();
		double noise = noiseFraction(quality);
		if(noise <= 0.0D)
			return out;
		for(int i = 0; i < out.length; i++)
			if(random.nextDouble() < noise)
				out[i] = NOISE[random.nextInt(NOISE.length)];
		return out;
	}

	/** True if {@code id} is one of the noise greys {@link #degrade} uses. */
	public static boolean isNoise(byte id) {
		for(byte n : NOISE)
			if(n == id)
				return true;
		return false;
	}
}
