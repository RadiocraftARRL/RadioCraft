package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.BandUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * The Propagation Predictor's sums (computer apps 2, 2026-11-06): which HF bands carry a signal over a path of a given
 * length, hour by hour, worked out with the mod's own propagation ({@link BandUtils#getBaseStrength}, the same call
 * {@code NonDirectionalAntennaType} makes) for a dipole at each end at full power, against the copy threshold a radio
 * uses for packets ({@code RadioBlockEntity.copyThreshold}: the noise floor times the minimum SNR, never under the
 * minimum signal). Terrain, other antennas and the transmitter's power are left out. Pure (no game state), so it is
 * unit tested; the server side feeds it the live sky ({@code PropagationHandler}).
 */
public final class PropagationForecast {

	private PropagationForecast() {}

	/** Hours predicted: one in-game day. */
	public static final int HOURS = 24;
	/** Ticks in an in-game hour. */
	public static final int TICKS_PER_HOUR = 1000;
	/** CW is copied this much further than voice (the antennas divide a CW path's distance by it). */
	public static final double CW_RANGE_MULTIPLIER = 1.5D;
	/** Quality (signal over the copy threshold) at and above which a band is GOOD, FAIR and POOR. */
	public static final double GOOD = 3.0D, FAIR = 1.5D, POOR = 1.0D;

	/** How well a path copies. POOR: only digital modes and CW get through. */
	public enum Grade {
		GOOD('G'), FAIR('F'), POOR('P'), CLOSED('-');

		public final char letter;

		Grade(char letter) {
			this.letter = letter;
		}

		public static Grade of(char letter) {
			for(Grade grade : values())
				if(grade.letter == letter)
					return grade;
			return CLOSED;
		}
	}

	/** How the signal gets there: line of sight (ground wave), off the sky, or not at all. */
	public enum Path {
		/** Inside the band's line of sight range. */
		LOS('L'),
		/** Inside the band's skip range for the time of day. */
		SKIP('S'),
		/** Too far for line of sight and too near for skip: the skip zone. */
		SKIP_ZONE('Z'),
		/** Past the furthest skip. */
		BEYOND('B');

		public final char letter;

		Path(char letter) {
			this.letter = letter;
		}

		public static Path of(char letter) {
			for(Path path : values())
				if(path.letter == letter)
					return path;
			return BEYOND;
		}
	}

	/**
	 * One hour of the prediction.
	 *
	 * @param hourOfDay  The clock hour (0-23; Minecraft's time 0 is 06:00).
	 * @param day        True if the sun counts as up (the skip ranges by day).
	 * @param known      False when the solar weather for this hour is a guess (past the end of the event known).
	 * @param solarNoise The solar noise floor this hour (0 to 1).
	 * @param extraNoise Noise on top that line of sight gets in full too (the aurora's static).
	 */
	public record Hour(int hourOfDay, boolean day, boolean known, double solarNoise, double extraNoise) {}

	/** The copy threshold: what {@code RadioBlockEntity} uses, from the server config. */
	public record Settings(double minSignal, double minSnr, double losNoiseShare) {

		/** The threshold a signal must reach over {@code noise}. */
		public double threshold(double noise) {
			return Math.max(minSignal, noise * minSnr);
		}
	}

	/** The skip multiplier ({@code BandCondition.getSkipMultiplier}) of {@code band} at hour {@code hour}. */
	@FunctionalInterface
	public interface Conditions {
		double skipMultiplier(Band band, int hour);
	}

	/**
	 * One band in one hour: the voice/data {@code grade} with its {@code strength} and {@code quality} (strength over
	 * the threshold), how the signal goes, and the grade CW gets (its path counts as {@link #CW_RANGE_MULTIPLIER}
	 * shorter).
	 */
	public record Cell(Grade grade, Path path, double strength, double quality, Grade cw) {}

	/** One band's day: its cells in hour order, and the index of its best hour (-1 when closed all day). */
	public record Row(Band band, List<Cell> cells, int bestHour) {}

	/** The whole prediction. */
	public record Result(List<Hour> hours, List<Row> rows) {

		/** The row with the best signal in the first hour, or -1 if every band is closed then. */
		public int bestNow() {
			int best = -1;
			double quality = 0.0D;
			for(int i = 0; i < rows.size(); i++) {
				Cell cell = rows.get(i).cells().isEmpty() ? null : rows.get(i).cells().getFirst();
				if(cell != null && cell.grade() != Grade.CLOSED && cell.quality() > quality) {
					quality = cell.quality();
					best = i;
				}
			}
			return best;
		}
	}

	/** The grade a signal {@code quality} times the copy threshold gets. */
	public static Grade grade(double quality) {
		if(quality >= GOOD)
			return Grade.GOOD;
		if(quality >= FAIR)
			return Grade.FAIR;
		if(quality >= POOR)
			return Grade.POOR;
		return Grade.CLOSED;
	}

	/** How a signal on {@code band} covers {@code distance} blocks, by day or night (as {@link BandUtils#getBaseStrength} decides). */
	public static Path path(Band band, double distance, boolean day) {
		if(distance < band.losRange())
			return Path.LOS;
		double minSkip = day ? band.minSkipDay() : band.minSkipNight();
		double maxSkip = day ? band.maxSkipDay() : band.maxSkipNight();
		if(distance > minSkip && distance < maxSkip)
			return Path.SKIP;
		return distance <= minSkip ? Path.SKIP_ZONE : Path.BEYOND;
	}

	/**
	 * One cell: the strength {@link BandUtils#getBaseStrength} gives a dipole at each end (line of sight and skip
	 * efficiency 1, skip times {@code skipMultiplier}), against the noise a radio copies that path against: a skip
	 * signal the whole noise floor, a line of sight one only {@link Settings#losNoiseShare} of the solar part.
	 */
	public static Cell cell(Band band, double distance, boolean day, double skipMultiplier, double solarNoise, double extraNoise, Settings settings) {
		Path path = path(band, distance, day);
		double strength = BandUtils.getBaseStrength(band, distance, 1.0D, skipMultiplier, day);
		double quality = strength / settings.threshold(noise(path, solarNoise, extraNoise, settings));
		double cwDistance = distance / CW_RANGE_MULTIPLIER;
		double cwStrength = BandUtils.getBaseStrength(band, cwDistance, 1.0D, skipMultiplier, day);
		double cwQuality = cwStrength / settings.threshold(noise(path(band, cwDistance, day), solarNoise, extraNoise, settings));
		Grade grade = grade(quality);
		Grade cw = grade(cwQuality);
		return new Cell(grade, path, strength, quality, cw.ordinal() < grade.ordinal() ? cw : grade);
	}

	private static double noise(Path path, double solarNoise, double extraNoise, Settings settings) {
		double share = path == Path.LOS ? settings.losNoiseShare() : 1.0D;
		return Math.min(1.0D, solarNoise * share + extraNoise);
	}

	/** The prediction for {@code bands} over a path of {@code distance} blocks, hour by hour. */
	public static Result predict(List<Band> bands, double distance, List<Hour> hours, Conditions conditions, Settings settings) {
		List<Row> rows = new ArrayList<>();
		for(Band band : bands) {
			List<Cell> cells = new ArrayList<>();
			int best = -1;
			double bestQuality = 0.0D;
			for(int h = 0; h < hours.size(); h++) {
				Hour hour = hours.get(h);
				Cell cell = cell(band, distance, hour.day(), conditions.skipMultiplier(band, h), hour.solarNoise(), hour.extraNoise(), settings);
				cells.add(cell);
				if(cell.grade() != Grade.CLOSED && cell.quality() > bestQuality) {
					bestQuality = cell.quality();
					best = h;
				}
			}
			rows.add(new Row(band, List.copyOf(cells), best));
		}
		return new Result(List.copyOf(hours), List.copyOf(rows));
	}

	/**
	 * True if the sun counts as up: vanilla's {@code Level.isDay()} (sky darkness under 4) for the sun at
	 * {@code timeOfDay} ({@code DimensionType.timeOfDay}, 0 to 1) and the rain and thunder levels (0 to 1).
	 */
	public static boolean isDay(double timeOfDay, double rain, double thunder) {
		double rainFactor = 1.0D - rain * 5.0D / 16.0D;
		double thunderFactor = 1.0D - thunder * 5.0D / 16.0D;
		double sun = 0.5D + 2.0D * Math.max(-0.25D, Math.min(0.25D, Math.cos(timeOfDay * Math.PI * 2.0D)));
		int darken = (int)((1.0D - sun * rainFactor * thunderFactor) * 11.0D);
		return darken < 4;
	}

	/** The clock hour (0-23) of Minecraft day time {@code dayTime} (time 0 is 06:00). */
	public static int hourOfDay(long dayTime) {
		return (int)((Math.floorMod(dayTime, 24000L) / TICKS_PER_HOUR + 6) % 24);
	}

	/** Where {@code distance} blocks on {@code bearing} degrees (0 north, clockwise) from {@code x, z} ends: {x, z}. */
	public static double[] offset(double x, double z, double distance, double bearing) {
		double radians = Math.toRadians(bearing);
		return new double[] { x + Math.sin(radians) * distance, z - Math.cos(radians) * distance };
	}
}
