package com.arrl.radiocraft.common.radio.adsb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ADS-B's range and units (1.12 step 9). */
class AdsbMathTest {

	@Test
	void highAircraftAreHeardToTheFullRange() {
		assertEquals(18000.0D, AdsbMath.range(200.0D, 63.0D, 200.0D, 200.0D, 18000.0D), 1.0E-9D);
		assertEquals(18000.0D, AdsbMath.range(319.0D, 63.0D, 200.0D, 200.0D, 18000.0D), 1.0E-9D);
	}

	@Test
	void theRangeFallsOffWithTheSquareOfHeightToTheMinimum() {
		assertEquals(200.0D, AdsbMath.range(63.0D, 63.0D, 200.0D, 200.0D, 18000.0D), 1.0E-9D, "sea level");
		assertEquals(200.0D, AdsbMath.range(-40.0D, 63.0D, 200.0D, 200.0D, 18000.0D), 1.0E-9D, "below sea level");
		// Half way up (Y 131.5 of 63-200): a quarter of the way from the minimum to the maximum.
		assertEquals(200.0D + 17800.0D / 4.0D, AdsbMath.range(131.5D, 63.0D, 200.0D, 200.0D, 18000.0D), 1.0E-9D);
		assertTrue(AdsbMath.range(150.0D, 63.0D, 200.0D, 200.0D, 18000.0D) > AdsbMath.range(100.0D, 63.0D, 200.0D, 200.0D, 18000.0D), "higher is further");
	}

	@Test
	void theFullRangeIsNeverBelowTheMinimum() {
		assertEquals(500.0D, AdsbMath.range(250.0D, 63.0D, 200.0D, 500.0D, 100.0D), 1.0E-9D);
		assertEquals(500.0D, AdsbMath.range(100.0D, 63.0D, 200.0D, 500.0D, 100.0D), 1.0E-9D);
		// Full range Y under sea level: only Y counts. At or above it, the full range; below it (and so below sea level),
		// the minimum, with no falloff curve in between.
		assertEquals(9000.0D, AdsbMath.range(100.0D, 63.0D, 40.0D, 300.0D, 9000.0D), 1.0E-9D, "full range Y under sea level: only Y counts");
		assertEquals(9000.0D, AdsbMath.range(41.0D, 63.0D, 40.0D, 300.0D, 9000.0D), 1.0E-9D);
		assertEquals(300.0D, AdsbMath.range(39.0D, 63.0D, 40.0D, 300.0D, 9000.0D), 1.0E-9D);
	}

	@Test
	void zeroMaxRangeIsTwiceTheHorn() {
		assertEquals(18000.0D, AdsbMath.maxRange(0.0D, 1500.0D, 6.0D), 1.0E-9D);
		assertEquals(5000.0D, AdsbMath.maxRange(5000.0D, 1500.0D, 6.0D), 1.0E-9D);
	}

	@Test
	void inRangeIsAlongTheGround() {
		assertTrue(AdsbMath.inRange(300.0D, 400.0D, 500.0D));
		assertFalse(AdsbMath.inRange(300.0D, 401.0D, 500.0D));
	}

	@Test
	void bearingsAreCompassDegreesWithNorthUpZ() {
		assertEquals(0.0D, AdsbMath.bearing(0.0D, -10.0D), 1.0E-9D);
		assertEquals(90.0D, AdsbMath.bearing(10.0D, 0.0D), 1.0E-9D);
		assertEquals(180.0D, AdsbMath.bearing(0.0D, 10.0D), 1.0E-9D);
		assertEquals(270.0D, AdsbMath.bearing(-10.0D, 0.0D), 1.0E-9D);
		assertEquals(45.0D, AdsbMath.bearing(5.0D, -5.0D), 1.0E-9D);
	}

	@Test
	void unitsAreAviationUnits() {
		assertEquals(328, AdsbMath.feet(100.0D));
		assertEquals(-33, AdsbMath.feet(-10.0D));
		assertEquals(194, AdsbMath.knots(100.0D));
		assertEquals(0, AdsbMath.feetPerMinute(0.1D), "under half a step rounds to level");
		assertEquals(1024, AdsbMath.feetPerMinute(5.2D), "rounded to 64 ft/min");
		assertEquals("045", AdsbMath.flightLevel(4_520));
		assertEquals("000", AdsbMath.flightLevel(20));
		assertEquals("-01", AdsbMath.flightLevel(-120));
		assertEquals("00ABCD", AdsbMath.hex(0xABCD));
		assertEquals("FFFFFF", AdsbMath.hex(-1));
	}

	@Test
	void callsignsAreCapitalsAndDigits() {
		assertEquals("N123AB", AdsbMath.cleanCallsign("n123ab"));
		assertEquals("SKYSHIP1", AdsbMath.cleanCallsign("Sky Ship 1 the Great"));
		assertEquals("", AdsbMath.cleanCallsign("--  !"));
	}

	@Test
	void squawksAreFourOctalDigits() {
		assertTrue(AdsbMath.validSquawk(1200));
		assertTrue(AdsbMath.validSquawk(7777));
		assertTrue(AdsbMath.validSquawk(0));
		assertFalse(AdsbMath.validSquawk(1280), "8 is not an octal digit");
		assertFalse(AdsbMath.validSquawk(10000));
		assertFalse(AdsbMath.validSquawk(-1));
		assertEquals(7700, AdsbMath.parseSquawk("7700"));
		assertEquals(42, AdsbMath.parseSquawk("0042"));
		assertEquals(-1, AdsbMath.parseSquawk("1290"));
		assertEquals(-1, AdsbMath.parseSquawk("120"));
		assertEquals("0042", AdsbMath.squawk(42));
		assertTrue(AdsbMath.isEmergency(7700));
		assertTrue(AdsbMath.isEmergency(7600));
		assertTrue(AdsbMath.isEmergency(7500));
		assertFalse(AdsbMath.isEmergency(1200));
		assertEquals("EMRG", AdsbMath.emergencyTag(7700));
		assertEquals("", AdsbMath.emergencyTag(1200));
	}
}
