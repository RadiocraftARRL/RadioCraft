package com.arrl.radiocraft.common.radio.commercial;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Commercial frequency licenses (2026-11-10): tiers, frequency picking, callsigns and the certificate. */
class CommercialLicenseTest {

	private static final String VHF = "vhf_commercial", LOW = "low_band";

	private static CommercialLicense license(int tier, float input) {
		return new CommercialLicense("WQAB123", VHF, 154_437_500.0F, input, "Steve", "", tier, "2026-11-10", false);
	}

	@Test
	void tiersDoubleUnitsAndBases() {
		assertEquals(10, license(0, 0).subscribers());
		assertEquals(0, license(0, 0).bases());
		assertEquals(20, license(1, 0).subscribers());
		assertEquals(1, license(1, 0).bases());
		assertEquals(40, license(2, 0).subscribers());
		assertEquals(2, license(2, 0).bases());
		assertEquals(80, license(3, 0).subscribers());
		assertEquals(4, license(3, 0).bases());
		assertEquals(CommercialLicense.MAX_TIER, license(99, 0).tier());
	}

	@Test
	void coversOutputAndInput() {
		CommercialLicense l = license(1, 152_000_000.0F);
		assertTrue(l.covers(154_437_500.0F));
		assertTrue(l.covers(154_440_000.0F));
		assertFalse(l.covers(154_450_000.0F));
		assertTrue(l.covers(152_000_000.0F));
		assertFalse(license(0, 0).covers(0.0F));
	}

	@Test
	void copiesStayTheSameGrant() {
		CommercialLicense l = license(2, 152_000_000.0F);
		assertTrue(l.asCopy().copy());
		assertTrue(l.asCopy().sameGrant(l));
		assertFalse(l.original() == l.asCopy().original());
	}

	@Test
	void picksFreeGridFrequenciesOutsideTheMarineBlock() {
		Random random = new Random(1);
		Set<Float> taken = new HashSet<>();
		for(int i = 0; i < 300; i++) {
			float hz = CommercialLicenseRules.pick(VHF, random, taken::contains);
			assertTrue(hz >= 150_000_000.0F && hz <= 174_000_000.0F, "Out of band: " + hz);
			assertFalse(CommercialLicenseRules.isReserved(hz), "Marine: " + hz);
			assertTrue(CommercialLicenseRules.grantable(VHF, hz));
			assertTrue(taken.add(hz), "Handed out twice: " + hz);
		}
		float low = CommercialLicenseRules.pick(LOW, random, hz -> false);
		assertTrue(low >= 30_000_000.0F && low <= 50_000_000.0F);
		assertEquals(0.0F, CommercialLicenseRules.pick(LOW, random, hz -> true));
	}

	@Test
	void inputsSitOneToFiveMegahertzAway() {
		Random random = new Random(7);
		for(int i = 0; i < 200; i++) {
			float input = CommercialLicenseRules.pickInput(VHF, 151_000_000.0F, random, hz -> false);
			float split = Math.abs(input - 151_000_000.0F);
			assertTrue(split >= 1_000_000.0F - 1 && split <= 5_000_000.0F + 1, "Split " + split);
			assertTrue(CommercialLicenseRules.grantable(VHF, input));
		}
	}

	@Test
	void whichFrequenciesNeedALicense() {
		assertTrue(CommercialLicenseRules.needsLicense(154_437_500.0F));
		assertTrue(CommercialLicenseRules.needsLicense(42_000_000.0F));
		assertFalse(CommercialLicenseRules.needsLicense(156_800_000.0F)); // Marine channel 16.
		assertFalse(CommercialLicenseRules.needsLicense(162_550_000.0F)); // NOAA weather.
		assertFalse(CommercialLicenseRules.needsLicense(146_520_000.0F)); // 2m.
		assertFalse(CommercialLicenseRules.needsLicense(27_185_000.0F)); // CB.
		for(float murs : CommercialLicenseRules.MURS_HZ) { // License free (2026-11-12), and never handed out.
			assertFalse(CommercialLicenseRules.needsLicense(murs));
			assertFalse(CommercialLicenseRules.grantable(com.arrl.radiocraft.common.radio.Band.VHF_COMMERCIAL, murs));
		}
		assertTrue(CommercialLicenseRules.needsLicense(151_812_500.0F)); // The business channel beside MURS 1.
	}

	@Test
	void callsignsArePart90Shaped() {
		Random random = new Random(3);
		Set<String> seen = new HashSet<>();
		for(int i = 0; i < 500; i++) {
			String call = CommercialLicenseRules.callsign(random, seen::contains);
			assertTrue(CommercialLicenseRules.isLicenseCallsign(call), call);
			assertTrue(seen.add(call));
		}
		assertEquals("154.4375", CommercialLicenseRules.mhz(154_437_500.0F));
		assertEquals("42.020", CommercialLicenseRules.mhz(42_020_000.0F));
	}

	@Test
	void certificateDrawsAndStampsCopies() {
		byte[] original = CommercialLicenseCertificate.render(license(1, 152_000_000.0F));
		byte[] copy = CommercialLicenseCertificate.render(license(1, 152_000_000.0F).asCopy());
		assertEquals(128 * 128, original.length);
		assertFalse(java.util.Arrays.equals(original, copy), "The copy has no stamp");
	}

	@Test
	void aSatellitesLicenseCoversItsTelemetryToo() { // 2026-11-12: a commercial satellite's third frequency.
		CommercialLicense plain = license(1, 152_000_000.0F);
		assertEquals(0.0F, plain.telemetryHz());
		assertFalse(plain.covers(160_000_000.0F));
		CommercialLicense satellite = plain.withTelemetry(160_000_000.0F);
		assertTrue(satellite.covers(160_000_000.0F));
		assertTrue(satellite.covers(160_005_000.0F));
		assertTrue(satellite.covers(154_437_500.0F) && satellite.covers(152_000_000.0F), "still its output and input");
		assertFalse(satellite.sameGrant(plain));
		// Kept through upgrades, copies and saving.
		assertEquals(160_000_000.0F, satellite.withTier(3, 152_000_000.0F).telemetryHz());
		assertEquals(160_000_000.0F, satellite.asCopy().telemetryHz());
		assertEquals(satellite, CommercialLicense.fromTag(satellite.toTag()));
		assertEquals(plain, CommercialLicense.fromTag(plain.toTag()), "a license without one loads as before");
		// A picker that treats licensed frequencies as taken never hands it out.
		Random random = new Random(9L);
		for(int i = 0; i < 2000; i++) {
			float hz = CommercialLicenseRules.pick(VHF, random, satellite::covers);
			assertFalse(satellite.covers(hz), "handed out " + hz);
		}
	}
}
