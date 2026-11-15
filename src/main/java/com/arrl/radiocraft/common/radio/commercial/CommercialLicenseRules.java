package com.arrl.radiocraft.common.radio.commercial;

import com.arrl.radiocraft.common.radio.Band;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.function.Predicate;

/**
 * Pure rules for commercial frequency licenses (2026-11-10): where a license's frequency may fall, picking a free one
 * at random, pairing a repeater input with it, making up a Part 90 callsign, and which frequencies need a license at
 * all. No game classes, so unit tests can use it.
 * <p>
 * VHF licenses are on the 12.5 kHz grid from 150 to 174 MHz; low band on a 20 kHz grid from 30 to 50 MHz. The marine
 * channels, DSC, AIS and the NOAA weather channels (156.000-162.600 MHz) are never handed out and never need one of
 * these licenses (marine radios are licensed by rule).
 */
public final class CommercialLicenseRules {

	public static final float VHF_LOW_HZ = 150_000_000.0F, VHF_HIGH_HZ = 174_000_000.0F, VHF_STEP_HZ = 12_500.0F;
	public static final float LOW_BAND_LOW_HZ = 30_000_000.0F, LOW_BAND_HIGH_HZ = 50_000_000.0F, LOW_BAND_STEP_HZ = 20_000.0F;
	/** The marine and weather block inside commercial VHF: never licensed, never needs a license. */
	public static final float MARINE_LOW_HZ = 156_000_000.0F, MARINE_HIGH_HZ = 162_600_000.0F;
	/** How far a repeater's input sits from its output: 1 to 5 MHz, so a duplexer can part them. */
	public static final float INPUT_MIN_SPLIT_HZ = 1_000_000.0F, INPUT_MAX_SPLIT_HZ = 5_000_000.0F;
	/** Tries before giving up on a free frequency (a full band). */
	public static final int TRIES = 4000;

	private CommercialLicenseRules() {}

	/** True for the two bands licenses are issued for. */
	public static boolean isLicensedBand(String band) {
		return Band.VHF_COMMERCIAL.equals(band) || Band.LOW_BAND.equals(band);
	}

	/** The band a frequency is licensed in ({@code vhf_commercial} or {@code low_band}), or null if none. */
	@Nullable
	public static String bandOf(float hz) {
		if(hz >= VHF_LOW_HZ && hz <= VHF_HIGH_HZ)
			return Band.VHF_COMMERCIAL;
		if(hz >= LOW_BAND_LOW_HZ && hz <= LOW_BAND_HIGH_HZ)
			return Band.LOW_BAND;
		return null;
	}

	/** True inside the marine and weather block, which licenses never cover. */
	public static boolean isReserved(float hz) {
		return hz >= MARINE_LOW_HZ && hz <= MARINE_HIGH_HZ;
	}

	/** The five MURS channels (license free, 2026-11-12), in Hz. */
	public static final float[] MURS_HZ = {151_820_000.0F, 151_880_000.0F, 151_940_000.0F, 154_570_000.0F, 154_600_000.0F};

	/** True on a MURS channel (within 3.75 kHz of one): license free, never handed out. */
	public static boolean isMurs(float hz) {
		for(float murs : MURS_HZ)
			if(Math.abs(hz - murs) <= 3_750.0F)
				return true;
		return false;
	}

	/**
	 * True if transmitting on {@code hz} needs a commercial license: commercial VHF or low band, outside the marine block
	 * and off the license-free MURS channels.
	 */
	public static boolean needsLicense(float hz) {
		return hz > 0 && bandOf(hz) != null && !isReserved(hz) && !isMurs(hz);
	}

	/** The grid step of {@code band}. */
	public static float step(String band) {
		return Band.LOW_BAND.equals(band) ? LOW_BAND_STEP_HZ : VHF_STEP_HZ;
	}

	private static float low(String band) {
		return Band.LOW_BAND.equals(band) ? LOW_BAND_LOW_HZ : VHF_LOW_HZ;
	}

	private static float high(String band) {
		return Band.LOW_BAND.equals(band) ? LOW_BAND_HIGH_HZ : VHF_HIGH_HZ;
	}

	/** True if {@code hz} may be handed out in {@code band}: inside it, on its grid, outside the marine block. */
	public static boolean grantable(String band, float hz) {
		if(hz < low(band) || hz > high(band) || isReserved(hz) || isMurs(hz))
			return false;
		double steps = (hz - low(band)) / step(band);
		return Math.abs(steps - Math.rint(steps)) < 1.0E-3;
	}

	/** A random free frequency in {@code band} ({@code taken} says which are in use), or 0 if the band is full. */
	public static float pick(String band, Random random, Predicate<Float> taken) {
		int channels = (int)((high(band) - low(band)) / step(band));
		for(int i = 0; i < TRIES; i++) {
			float hz = low(band) + random.nextInt(channels + 1) * step(band);
			if(grantable(band, hz) && !taken.test(hz))
				return hz;
		}
		return 0.0F;
	}

	/**
	 * A free input frequency to pair with {@code outputHz} for a repeater: 1 to 5 MHz above or below it, in the same band
	 * and on its grid, or 0 if none is free.
	 */
	public static float pickInput(String band, float outputHz, Random random, Predicate<Float> taken) {
		float step = step(band);
		int minSteps = (int)Math.ceil(INPUT_MIN_SPLIT_HZ / step), maxSteps = (int)(INPUT_MAX_SPLIT_HZ / step);
		for(int i = 0; i < TRIES; i++) {
			int steps = minSteps + random.nextInt(maxSteps - minSteps + 1);
			float hz = outputHz + (random.nextBoolean() ? steps : -steps) * step;
			if(grantable(band, hz) && !taken.test(hz))
				return hz;
		}
		return 0.0F;
	}

	/** A made-up Part 90 callsign, {@code WQ} + two letters + three digits ({@code WQAB123}), not {@code taken}. */
	public static String callsign(Random random, Predicate<String> taken) {
		for(int i = 0; i < TRIES; i++) {
			String call = "WQ" + (char)('A' + random.nextInt(26)) + (char)('A' + random.nextInt(26)) + String.format("%03d", random.nextInt(1000));
			if(!taken.test(call))
				return call;
		}
		return "";
	}

	/** True for a callsign in the form {@link #callsign} makes. */
	public static boolean isLicenseCallsign(String callsign) {
		return callsign != null && callsign.matches("WQ[A-Z]{2}[0-9]{3}");
	}

	/** {@code hz} in MHz as a license shows it: {@code 154.4375}. */
	public static String mhz(float hz) {
		String s = String.format(java.util.Locale.ROOT, "%.4f", hz / 1_000_000.0D);
		return s.endsWith("0") ? s.substring(0, s.length() - 1) : s;
	}
}
