package com.arrl.radiocraft.common.radio.satellite;

import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import com.arrl.radiocraft.common.radio.voice.Ctcss;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Satellite frequencies, names, tones and decay (2026-11-11); three frequencies, modes and APRS (2026-11-12). */
class SatelliteRulesTest {

	@Test
	void amateurPairsAreOn2mWithTheRepeaterOffset() {
		Random random = new Random(1L);
		Set<Float> used = new HashSet<>();
		for(int i = 0; i < 20; i++) {
			SatelliteRules.Pair pair = SatelliteRules.amateurPair(random, used::contains);
			assertNotNull(pair, "ran out after " + i);
			assertTrue(pair.downlinkHz() >= SatelliteRules.AMATEUR_DOWN_LOW_HZ && pair.downlinkHz() <= SatelliteRules.AMATEUR_DOWN_HIGH_HZ);
			assertEquals(Ctcss.REPEATER_OFFSET_HZ, pair.downlinkHz() - pair.uplinkHz(), 1.0F);
			assertTrue(used.add(pair.downlinkHz()) && used.add(pair.uplinkHz()), "a pair used twice");
		}
		assertNull(SatelliteRules.amateurPair(random, used::contains), "20 channels, all taken");
	}

	@Test
	void commercialPairsAreGrantableAndApart() {
		Random random = new Random(2L);
		Set<Float> used = new HashSet<>();
		for(int i = 0; i < 30; i++) {
			SatelliteRules.Pair pair = SatelliteRules.commercialPair(random, used::contains);
			assertNotNull(pair);
			assertTrue(CommercialLicenseRules.grantable("vhf_commercial", pair.downlinkHz()));
			assertTrue(CommercialLicenseRules.grantable("vhf_commercial", pair.uplinkHz()));
			float split = Math.abs(pair.downlinkHz() - pair.uplinkHz());
			assertTrue(split >= CommercialLicenseRules.INPUT_MIN_SPLIT_HZ && split <= CommercialLicenseRules.INPUT_MAX_SPLIT_HZ);
			assertTrue(used.add(pair.downlinkHz()) && used.add(pair.uplinkHz()));
		}
	}

	@Test
	void namesTonesAndMatching() {
		assertEquals("RO-3", SatelliteRules.name(true, 3));
		assertEquals("CS-12", SatelliteRules.name(false, 12));
		float tone = SatelliteRules.tone(new Random(4L));
		assertEquals(tone, Ctcss.sanitize(tone));
		assertTrue(tone > 0.0F);
		assertTrue(SatelliteRules.on(145_200_000.0F, 145_203_000.0F));
		assertFalse(SatelliteRules.on(145_200_000.0F, 145_210_000.0F));
		assertFalse(SatelliteRules.on(0.0F, 0.0F));
	}

	@Test
	void decayCountsRealDays() {
		long day = 86_400_000L, launch = 1_000_000L;
		assertEquals(90, SatelliteRules.daysLeft(launch, 90, launch));
		assertEquals(1, SatelliteRules.daysLeft(launch, 90, launch + 89 * day + 1));
		assertEquals(0, SatelliteRules.daysLeft(launch, 90, launch + 90 * day));
		assertFalse(SatelliteRules.decayNear(launch, 90, launch + 10 * day));
		assertTrue(SatelliteRules.decayNear(launch, 90, launch + 84 * day));
	}

	@Test
	void idTextIsWhatMorseCanSend() {
		assertEquals("RO-3 DE W1AW", Satellite.cleanId("ro-3 de w1aw!"));
		assertEquals(Satellite.ID_MAX, Satellite.cleanId("ABCDEFGHIJKLMNOPQRST").length());
	}

	@Test
	void threeFrequenciesAmateurAllOn2m() {
		Random random = new Random(5L);
		Set<Float> used = new HashSet<>();
		int made = 0;
		SatelliteRules.Frequencies f;
		while((f = SatelliteRules.frequencies(true, random, hz -> used.stream().anyMatch(u -> SatelliteRules.near(u, hz)))) != null) {
			assertEquals(Ctcss.REPEATER_OFFSET_HZ, f.downlinkHz() - f.uplinkHz(), 1.0F);
			assertTrue(f.telemetryHz() >= SatelliteRules.AMATEUR_DOWN_LOW_HZ && f.telemetryHz() <= SatelliteRules.AMATEUR_DOWN_HIGH_HZ, "telemetry in the satellite sub-band");
			assertFalse(SatelliteRules.near(f.telemetryHz(), f.downlinkHz()) || SatelliteRules.near(f.telemetryHz(), f.uplinkHz()), "three different channels");
			assertTrue(used.add(f.uplinkHz()) && used.add(f.downlinkHz()) && used.add(f.telemetryHz()), "a frequency used twice: " + f);
			made++;
		}
		assertEquals(10, made, "20 sub-band channels make 10 satellites (downlink and telemetry each)");
	}

	@Test
	void threeFrequenciesCommercial() {
		Random random = new Random(6L);
		Set<Float> used = new HashSet<>();
		for(int i = 0; i < 20; i++) {
			SatelliteRules.Frequencies f = SatelliteRules.frequencies(false, random, used::contains);
			assertNotNull(f);
			assertTrue(CommercialLicenseRules.grantable("vhf_commercial", f.telemetryHz()));
			assertTrue(used.add(f.uplinkHz()) && used.add(f.downlinkHz()) && used.add(f.telemetryHz()), "a frequency used twice: " + f);
		}
	}

	@Test
	void legacyTelemetryIsFreeAndTheSameEachLoad() {
		Set<Float> used = new HashSet<>(Set.of(145_990_000.0F, 145_390_000.0F));
		float first = SatelliteRules.legacyTelemetry("RO-1", true, 145_200_000.0F, 145_800_000.0F, hz -> used.stream().anyMatch(u -> SatelliteRules.near(u, hz)), hz -> false);
		float again = SatelliteRules.legacyTelemetry("RO-1", true, 145_200_000.0F, 145_800_000.0F, hz -> used.stream().anyMatch(u -> SatelliteRules.near(u, hz)), hz -> false);
		assertEquals(first, again);
		assertEquals(145_980_000.0F, first, 1.0F, "the highest free channel");
		// Every channel taken by someone's telemetry: share one that is nobody's uplink or downlink.
		float shared = SatelliteRules.legacyTelemetry("RO-2", true, 145_200_000.0F, 145_800_000.0F, hz -> true, hz -> SatelliteRules.near(hz, 145_990_000.0F));
		assertEquals(145_980_000.0F, shared, 1.0F);
		// Nothing at all: its own downlink.
		assertEquals(145_800_000.0F, SatelliteRules.legacyTelemetry("RO-3", true, 145_200_000.0F, 145_800_000.0F, hz -> true, hz -> true));
	}

	@Test
	void aprsFrequencies() {
		assertTrue(SatelliteRules.aprsAllowed(145_825_000.0F));
		assertTrue(SatelliteRules.aprsAllowed(144_390_000.0F));
		assertFalse(SatelliteRules.aprsAllowed(146_000_000.0F * 3));
		assertFalse(SatelliteRules.aprsAllowed(143_990_000.0F));
		assertTrue(SatelliteRules.aprsNotRecommended(144_390_000.0F));
		assertFalse(SatelliteRules.aprsNotRecommended(145_825_000.0F));
	}

	@Test
	void modes() {
		assertEquals(SatelliteMode.APRS, SatelliteMode.parse(" aprs "));
		assertNull(SatelliteMode.parse("BEACON"));
		assertNull(SatelliteMode.parse(null));
		for(SatelliteMode mode : SatelliteMode.values())
			assertEquals(mode != SatelliteMode.IMAGING, mode.allowedOn(true), mode + " on an amateur satellite");
		assertTrue(SatelliteMode.REPEATER.allowedOn(false));
		assertTrue(SatelliteMode.IMAGING.allowedOn(false), "a commercial satellite images");
		assertFalse(SatelliteMode.SSTV.allowedOn(false));
		assertFalse(SatelliteMode.MAILBOX.allowedOn(false));
		assertEquals(SatelliteMode.APRS, SatelliteMode.REPEATER.next(true));
		assertEquals(SatelliteMode.REPEATER, SatelliteMode.SSTV.next(true), "wraps round, past IMAGING");
		assertEquals(SatelliteMode.IMAGING, SatelliteMode.REPEATER.next(false));
		assertEquals(SatelliteMode.REPEATER, SatelliteMode.IMAGING.next(false), "a commercial one steps between its two");
	}
}
