package com.arrl.radiocraft.common.radio.satellite;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A satellite's telemetry (2026-11-12): what its beacon sends every {@code SatelliteRadio#TELEMETRY_TICKS} on its
 * telemetry frequency as a data frame, and SATTRACK decodes. Plausible values from where it is in the day and orbit:
 * the battery charges in sunlight and runs down in the Earth's shadow (night), the temperature swings between the two,
 * the solar panels' current ripples as it tumbles. After an eclipse on a flat battery ({@link SatelliteEclipse}) the
 * battery shows low and the mode SAFE until it has recovered. During the eclipse itself it still sends a frame now and
 * then ({@link #eclipsed}): battery flat, mode SAFE, {@code powerSaving} set. Pure, unit tested.
 *
 * @param seq       Frame number, 0 to 999, wrapping.
 * @param volts     Battery volts (a 12 V pack: 14.2 full, under 11.8 low).
 * @param amps      Battery current, A: positive charging, negative discharging.
 * @param tempC     Inside temperature, degrees C.
 * @param sunlit    True in sunlight.
 * @param mode      Its mode's name, or {@code SAFE} with a low battery.
 * @param daysLeft  Real days until its orbit decays.
 * @param low       True while the battery is low (after an eclipse).
 * @param powerSaving True while it is eclipsed on a flat battery: everything off but a rare beacon frame.
 */
public record SatelliteTelemetry(int seq, double volts, double amps, int tempC, boolean sunlit, String mode, long daysLeft, boolean low,
                                 boolean powerSaving) {

	/** Below this the battery is low. */
	public static final double LOW_VOLTS = 11.8D;
	/** How fast the battery and temperature settle after dawn or dusk, ticks. */
	private static final double SETTLE_TICKS = 3000.0D;
	private static final double SUN_TEMP = 28.0D, SHADOW_TEMP = -18.0D;
	/** The mode a low battery puts it in. */
	public static final String SAFE = "SAFE";

	/**
	 * Telemetry for the satellite with {@code seed} running {@code mode} at {@code gameTime}, the world's time of day
	 * being {@code dayTime}; {@code recovering} slots after an eclipse ({@link SatelliteEclipse#recovering}, 0 if not).
	 */
	public static SatelliteTelemetry at(long seed, int seq, long gameTime, long dayTime, String mode, long daysLeft, int recovering) {
		boolean sunlit = !SatelliteEclipse.night(dayTime);
		long t = Math.floorMod(dayTime, (long)SatelliteOrbit.DAY_TICKS);
		// Ticks since the last dawn or dusk.
		double since = sunlit ? Math.floorMod(t - SatelliteEclipse.NIGHT_END, (long)SatelliteOrbit.DAY_TICKS) : t - SatelliteEclipse.NIGHT_START;
		double settle = 1.0D - Math.exp(-since / SETTLE_TICKS);
		double spin = Math.sin(gameTime / 37.0D + (seed & 0xFF)); // Tumbling: the panels' angle to the sun.
		double offset = ((seed >>> 8) & 0x7) - 3.5D; // Each satellite runs a few degrees warmer or colder.
		double volts, amps;
		boolean low = recovering > 0;
		if(low) {
			volts = 10.6D + 0.5D * (recovering - 1) + (sunlit ? 0.3D * settle : 0.0D);
			amps = sunlit ? 0.85D + 0.05D * spin : -0.08D;
		}
		else if(sunlit) {
			volts = 13.4D + 0.75D * settle + 0.03D * spin;
			amps = 0.55D + 0.3D * Math.abs(spin) - 0.4D * settle; // Charging hard after dawn, tapering as it fills.
		}
		else {
			volts = 13.9D - 0.85D * settle;
			amps = -0.35D - 0.03D * spin;
		}
		double temp = (sunlit ? SHADOW_TEMP + (SUN_TEMP - SHADOW_TEMP) * settle : SUN_TEMP - (SUN_TEMP - SHADOW_TEMP) * settle) + offset + 1.5D * spin;
		boolean lowNow = low || volts < LOW_VOLTS;
		return new SatelliteTelemetry(Math.floorMod(seq, 1000), round2(volts), round2(amps), (int)Math.round(temp), sunlit, lowNow ? SAFE : mode,
				Math.max(0L, daysLeft), lowNow, false);
	}

	/**
	 * The rare frame it sends while eclipsed on a flat battery: in the shadow, cold, the battery nearly flat and barely
	 * discharging (everything off), mode SAFE, power saving.
	 */
	public static SatelliteTelemetry eclipsed(long seed, int seq, long gameTime, long daysLeft) {
		double spin = Math.sin(gameTime / 37.0D + (seed & 0xFF));
		double offset = ((seed >>> 8) & 0x7) - 3.5D;
		return new SatelliteTelemetry(Math.floorMod(seq, 1000), round2(10.35D + 0.05D * spin), round2(-0.04D - 0.01D * Math.abs(spin)),
				(int)Math.round(SHADOW_TEMP - 4.0D + offset), false, SAFE, Math.max(0L, daysLeft), true, true);
	}

	private static double round2(double v) {
		return Math.round(v * 100.0D) / 100.0D;
	}

	/** The frame's text: {@code SEQ=42 BAT=13.62V CUR=+0.81A TMP=+21C SUN=1 MODE=APRS DK=83 LOW=0}, and {@code PWR=SAVING} while eclipsed. */
	public String encode() {
		return String.format(Locale.ROOT, "SEQ=%03d BAT=%.2fV CUR=%+.2fA TMP=%+dC SUN=%d MODE=%s DK=%d LOW=%d", seq, volts, amps, tempC, sunlit ? 1 : 0,
				mode, daysLeft, low ? 1 : 0) + (powerSaving ? " PWR=SAVING" : "");
	}

	/** A frame's text decoded, or null if it is not telemetry. */
	@Nullable
	public static SatelliteTelemetry decode(@Nullable String text) {
		if(text == null)
			return null;
		Map<String, String> fields = new HashMap<>();
		for(String part : text.strip().split("\\s+")) {
			int eq = part.indexOf('=');
			if(eq > 0)
				fields.put(part.substring(0, eq), part.substring(eq + 1));
		}
		if(!fields.containsKey("SEQ") || !fields.containsKey("BAT") || !fields.containsKey("MODE"))
			return null;
		try {
			return new SatelliteTelemetry(Integer.parseInt(fields.get("SEQ")), Double.parseDouble(strip(fields.get("BAT"), 'V')),
					Double.parseDouble(strip(fields.getOrDefault("CUR", "0"), 'A')), Integer.parseInt(strip(fields.getOrDefault("TMP", "0"), 'C')),
					"1".equals(fields.get("SUN")), fields.get("MODE"), Long.parseLong(fields.getOrDefault("DK", "0")), "1".equals(fields.get("LOW")),
					"SAVING".equals(fields.get("PWR")));
		}
		catch(NumberFormatException e) {
			return null;
		}
	}

	private static String strip(String value, char unit) {
		String v = value.endsWith(String.valueOf(unit)) ? value.substring(0, value.length() - 1) : value;
		return v.startsWith("+") ? v.substring(1) : v;
	}
}
