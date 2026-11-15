package com.arrl.radiocraft.common.radio.satellite;

import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import com.arrl.radiocraft.common.radio.voice.Ctcss;

import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.function.Predicate;

/**
 * Pure rules for satellites (2026-11-11): the frequency pairs a new one gets, its name, its tone, how near a frequency
 * must be to count as its uplink, and when it decays. No game classes, so unit tests can use it.
 * <p>
 * Amateur satellites are both ends on 2m (the mod has no 70cm band): the downlink in the 145.800-145.990 MHz satellite
 * sub-band, the uplink 600 kHz below it, so a radio set to the downlink with the standard minus offset and the tone
 * works it. Commercial ones are paired on commercial VHF the way a licensed repeater is (an output and an input 1 to 5
 * MHz away, {@link CommercialLicenseRules#pickInput}).
 */
public final class SatelliteRules {

	/** The amateur downlinks: 145.800 to 145.990 MHz, 10 kHz apart. */
	public static final float AMATEUR_DOWN_LOW_HZ = 145_800_000.0F, AMATEUR_DOWN_HIGH_HZ = 145_990_000.0F, AMATEUR_STEP_HZ = 10_000.0F;
	/** An amateur uplink is this far below its downlink: the radios' repeater offset. */
	public static final float AMATEUR_SPLIT_HZ = Ctcss.REPEATER_OFFSET_HZ;
	/** A signal this near the uplink is on it (half a 12.5 kHz channel, as a license's {@code MATCH_HZ}). */
	public static final float MATCH_HZ = 6_250.0F;
	/** Days before the end of its orbit that SATTRACK warns of the decay. */
	public static final int WARN_DAYS = 7;

	private static final long DAY_MILLIS = 86_400_000L;

	private SatelliteRules() {}

	/** An uplink and downlink pair, Hz. */
	public record Pair(float uplinkHz, float downlinkHz) {}

	/** A new satellite's three frequencies (2026-11-12): its uplink, downlink and telemetry beacon, Hz. */
	public record Frequencies(float uplinkHz, float downlinkHz, float telemetryHz) {}

	/** The 2m band, Hz: an amateur satellite's APRS frequency must be in it. */
	public static final float TWO_M_LOW_HZ = 144_000_000.0F, TWO_M_HIGH_HZ = 148_000_000.0F;
	/** Terrestrial APRS on 2m: allowed for a satellite's APRS, but not recommended (it is busy on the ground). */
	public static final float TERRESTRIAL_APRS_HZ = 144_390_000.0F;

	/**
	 * A new satellite's three frequencies, none of them {@code taken}: the pair as {@link #amateurPair} or
	 * {@link #commercialPair} picks it, and a telemetry channel ({@link #telemetry}). Null if no set is free.
	 */
	@Nullable
	public static Frequencies frequencies(boolean amateur, Random random, Predicate<Float> taken) {
		Pair pair = amateur ? amateurPair(random, taken) : commercialPair(random, taken);
		if(pair == null)
			return null;
		float telemetry = telemetry(amateur, random, taken.or(hz -> near(hz, pair.uplinkHz()) || near(hz, pair.downlinkHz())));
		return telemetry <= 0.0F ? null : new Frequencies(pair.uplinkHz(), pair.downlinkHz(), telemetry);
	}

	/**
	 * A free telemetry channel, or 0 if none is: for an amateur satellite one of the 2m satellite downlink channels
	 * (145.800-145.990 MHz, from the top down, as real beacons sit at the top of the sub-band), for a commercial one a
	 * commercial VHF channel.
	 */
	public static float telemetry(boolean amateur, Random random, Predicate<Float> taken) {
		if(!amateur) {
			float hz = CommercialLicenseRules.pick(Band.VHF_COMMERCIAL, random, taken);
			return Math.max(0.0F, hz);
		}
		int channels = Math.round((AMATEUR_DOWN_HIGH_HZ - AMATEUR_DOWN_LOW_HZ) / AMATEUR_STEP_HZ) + 1;
		for(int i = channels - 1; i >= 0; i--) {
			float hz = AMATEUR_DOWN_LOW_HZ + i * AMATEUR_STEP_HZ;
			if(!taken.test(hz))
				return hz;
		}
		return 0.0F;
	}

	/**
	 * A telemetry channel for a satellite saved before it had one (2026-11-12): free if one is, picked the same way each
	 * time for the same satellite (seeded by its name); if every channel is taken, one that only another satellite's
	 * telemetry uses ({@code pairsOnly} says which frequencies are someone's uplink or downlink), and failing that its
	 * own downlink.
	 */
	public static float legacyTelemetry(String name, boolean amateur, float uplinkHz, float downlinkHz, Predicate<Float> taken, Predicate<Float> pairsOnly) {
		Predicate<Float> own = hz -> near(hz, uplinkHz) || near(hz, downlinkHz);
		float hz = telemetry(amateur, new Random(name.hashCode()), taken.or(own));
		if(hz <= 0.0F)
			hz = telemetry(amateur, new Random(name.hashCode()), pairsOnly.or(own));
		return hz > 0.0F ? hz : downlinkHz;
	}

	/** True if {@code a} and {@code b} are on one channel (closer than a channel step). */
	public static boolean near(float a, float b) {
		return Math.abs(a - b) < AMATEUR_STEP_HZ;
	}

	/** True if {@code hz} may be an amateur satellite's APRS frequency: anywhere on 2m. */
	public static boolean aprsAllowed(float hz) {
		return hz >= TWO_M_LOW_HZ && hz <= TWO_M_HIGH_HZ;
	}

	/** True if {@code hz} is terrestrial APRS: allowed, but SATTRACK shows it as not recommended. */
	public static boolean aprsNotRecommended(float hz) {
		return on(hz, TERRESTRIAL_APRS_HZ);
	}

	/** A free amateur pair ({@code taken} says which frequencies are in use), or null if every channel is. */
	public static Pair amateurPair(Random random, Predicate<Float> taken) {
		int channels = Math.round((AMATEUR_DOWN_HIGH_HZ - AMATEUR_DOWN_LOW_HZ) / AMATEUR_STEP_HZ) + 1;
		int start = random.nextInt(channels);
		for(int i = 0; i < channels; i++) {
			float down = AMATEUR_DOWN_LOW_HZ + ((start + i) % channels) * AMATEUR_STEP_HZ;
			float up = down - AMATEUR_SPLIT_HZ;
			if(!taken.test(down) && !taken.test(up))
				return new Pair(up, down);
		}
		return null;
	}

	/** A free commercial VHF pair, or null if none is left. */
	public static Pair commercialPair(Random random, Predicate<Float> taken) {
		for(int i = 0; i < 50; i++) {
			float down = CommercialLicenseRules.pick(Band.VHF_COMMERCIAL, random, taken);
			if(down <= 0.0F)
				return null;
			float up = CommercialLicenseRules.pickInput(Band.VHF_COMMERCIAL, down, random, taken);
			if(up > 0.0F)
				return new Pair(up, down);
		}
		return null;
	}

	/** A satellite's name: amateur ones {@code RO-3} (RadioCraft OSCAR), commercial ones {@code CS-3}. */
	public static String name(boolean amateur, int number) {
		return (amateur ? "RO-" : "CS-") + number;
	}

	/** A random standard CTCSS tone for a new satellite. */
	public static float tone(Random random) {
		return Ctcss.TONES[random.nextInt(Ctcss.TONES.length)];
	}

	/** True if {@code hz} is on {@code channelHz}. */
	public static boolean on(float hz, float channelHz) {
		return hz > 0.0F && channelHz > 0.0F && Math.abs(hz - channelHz) <= MATCH_HZ;
	}

	/** When a satellite launched at {@code launchMillis} decays, real time in milliseconds. */
	public static long decayMillis(long launchMillis, int decayDays) {
		return launchMillis + Math.max(1, decayDays) * DAY_MILLIS;
	}

	/** Whole days (rounded up) until it decays; 0 or less once it has. */
	public static long daysLeft(long launchMillis, int decayDays, long nowMillis) {
		long left = decayMillis(launchMillis, decayDays) - nowMillis;
		return left <= 0 ? 0 : (left + DAY_MILLIS - 1) / DAY_MILLIS;
	}

	/** True once the decay is close enough for SATTRACK to warn. */
	public static boolean decayNear(long launchMillis, int decayDays, long nowMillis) {
		return daysLeft(launchMillis, decayDays, nowMillis) <= WARN_DAYS;
	}

	/** How many times faster a geostationary satellite's orbit decays (2026-11-12, maintainer): twice. */
	public static final int GEO_DECAY_RATE = 2;

	/**
	 * When a satellite launched at {@code launchMillis} decays, ms: {@link #decayMillis(long, int)}, or for a
	 * geostationary one after half as long ({@link #GEO_DECAY_RATE}).
	 */
	public static long decayMillis(long launchMillis, int decayDays, boolean geostationary) {
		long life = Math.max(1, decayDays) * DAY_MILLIS;
		return launchMillis + (geostationary ? life / GEO_DECAY_RATE : life);
	}

	/** Whole days (rounded up) until it decays; 0 or less once it has. */
	public static long daysLeft(long launchMillis, int decayDays, boolean geostationary, long nowMillis) {
		long left = decayMillis(launchMillis, decayDays, geostationary) - nowMillis;
		return left <= 0 ? 0 : (left + DAY_MILLIS - 1) / DAY_MILLIS;
	}

	/** True once the decay is close enough for SATTRACK to warn. */
	public static boolean decayNear(long launchMillis, int decayDays, boolean geostationary, long nowMillis) {
		return daysLeft(launchMillis, decayDays, geostationary, nowMillis) <= WARN_DAYS;
	}
}
