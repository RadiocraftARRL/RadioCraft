package com.arrl.radiocraft.common.radio.adsb;

import java.util.Locale;

/**
 * The arithmetic of ADS-B (1.12 step 9): how far a 1090 MHz squitter reaches, and the units an aircraft reports in.
 * Pure (no game classes), so it is unit tested.
 *
 * <p>1090 MHz goes in straight lines, so how far an aircraft is heard depends on how high it flies. A Minecraft world
 * is flat and small, so the range is a simple game curve of the aircraft's absolute Y (maintainer, 2026-11-05): at or
 * above {@code adsb_full_range_y} (200) it is heard out to the full range ({@code adsb_max_range}, 0 = twice the
 * microwave horn's reach, 18,000 blocks with the defaults); below that the range falls off with the square of the
 * height above sea level, down to {@code adsb_min_range} (200 blocks) at sea level and below. The receiving antenna's
 * height does not count.</p>
 */
public final class AdsbMath {

	/** A block is a metre: feet in a block, knots in a block a second, feet a minute in a block a second. */
	public static final double FEET_PER_BLOCK = 3.28084D;
	public static final double KNOTS_PER_BLOCK_PER_SECOND = 1.943844D;
	public static final double FPM_PER_BLOCK_PER_SECOND = 196.8504D;

	private AdsbMath() {}

	/**
	 * How far along the ground an aircraft at absolute {@code y} is heard: {@code maxRange} at or above {@code fullY},
	 * falling off with the square of its height above {@code seaLevel} to {@code minRange} at sea level and below.
	 * {@code maxRange} is never taken as less than {@code minRange}.
	 */
	public static double range(double y, double seaLevel, double fullY, double minRange, double maxRange) {
		double max = Math.max(minRange, maxRange);
		if(y >= fullY)
			return max;
		if(fullY <= seaLevel || y <= seaLevel)
			return minRange;
		double t = (y - seaLevel) / (fullY - seaLevel);
		return minRange + (max - minRange) * t * t;
	}

	/** The full range when the setting is 0: twice the microwave horn's reach ({@code dishRange x hornFactor}). */
	public static double maxRange(double setting, double dishRange, double hornFactor) {
		return setting > 0.0D ? setting : 2.0D * dishRange * hornFactor;
	}

	/** True if a target {@code dx}, {@code dz} blocks away (along the ground) is within {@code range}. */
	public static boolean inRange(double dx, double dz, double range) {
		return dx * dx + dz * dz <= range * range;
	}

	/** The compass bearing (0 north, 90 east) of a point {@code dx}, {@code dz} blocks away; north is -Z. */
	public static double bearing(double dx, double dz) {
		double degrees = Math.toDegrees(Math.atan2(dx, -dz));
		return degrees < 0.0D ? degrees + 360.0D : degrees >= 360.0D ? 0.0D : degrees;
	}

	/** Altitude in feet of a point {@code height} blocks above sea level (below sea level is negative). */
	public static int feet(double height) {
		return (int)Math.round(height * FEET_PER_BLOCK);
	}

	/** Knots from blocks a second. */
	public static int knots(double blocksPerSecond) {
		return (int)Math.round(blocksPerSecond * KNOTS_PER_BLOCK_PER_SECOND);
	}

	/** Feet a minute from blocks a second, rounded to 64 ft/min as ADS-B reports it. */
	public static int feetPerMinute(double blocksPerSecond) {
		return (int)Math.round(blocksPerSecond * FPM_PER_BLOCK_PER_SECOND / 64.0D) * 64;
	}

	/** A flight level tag: hundreds of feet, three digits ("045" for 4,500 ft; "-01" below sea level). */
	public static String flightLevel(int feet) {
		int hundreds = (int)Math.round(feet / 100.0D);
		return hundreds < 0 ? String.format(Locale.ROOT, "-%02d", Math.min(99, -hundreds)) : String.format(Locale.ROOT, "%03d", Math.min(999, hundreds));
	}

	/** A 24-bit ICAO address as six hex digits. */
	public static String hex(int icao) {
		return String.format(Locale.ROOT, "%06X", icao & 0xFFFFFF);
	}

	// -------------------- CALLSIGN AND SQUAWK --------------------

	/** Most characters in a flight identification. */
	public static final int CALLSIGN_MAX = 8;
	/** The squawk a VFR flight sets in the US, and the default. */
	public static final int VFR_SQUAWK = 1200;
	public static final int SQUAWK_HIJACK = 7500, SQUAWK_RADIO_FAILURE = 7600, SQUAWK_EMERGENCY = 7700;

	/** {@code text} as a flight identification: capitals and digits only, at most eight. */
	public static String cleanCallsign(String text) {
		StringBuilder out = new StringBuilder();
		for(char c : text.toUpperCase(Locale.ROOT).toCharArray()) {
			if(out.length() >= CALLSIGN_MAX)
				break;
			if(c >= 'A' && c <= 'Z' || c >= '0' && c <= '9')
				out.append(c);
		}
		return out.toString();
	}

	/** True if {@code squawk} is a transponder code: four digits, each 0 to 7. */
	public static boolean validSquawk(int squawk) {
		if(squawk < 0 || squawk > 7777)
			return false;
		for(int rest = squawk; rest > 0; rest /= 10)
			if(rest % 10 > 7)
				return false;
		return true;
	}

	/** {@code text} as a squawk, or -1 if it is not one. */
	public static int parseSquawk(String text) {
		String digits = text.trim();
		if(digits.length() != 4)
			return -1;
		for(char c : digits.toCharArray())
			if(c < '0' || c > '7')
				return -1;
		return Integer.parseInt(digits);
	}

	/** A squawk as four digits ("0042"). */
	public static String squawk(int squawk) {
		return String.format(Locale.ROOT, "%04d", squawk);
	}

	/** True for the three emergency codes, which a display shows in red. */
	public static boolean isEmergency(int squawk) {
		return squawk == SQUAWK_HIJACK || squawk == SQUAWK_RADIO_FAILURE || squawk == SQUAWK_EMERGENCY;
	}

	/** What an emergency squawk means on a display ("EMRG", "NORDO", "HIJK"), or "" for any other code. */
	public static String emergencyTag(int squawk) {
		return switch(squawk) {
			case SQUAWK_EMERGENCY -> "EMRG";
			case SQUAWK_RADIO_FAILURE -> "NORDO";
			case SQUAWK_HIJACK -> "HIJK";
			default -> "";
		};
	}
}
