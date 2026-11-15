package com.arrl.radiocraft.common.radio.satellite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** How antennas hear satellites by elevation (2026-11-12): the eggbeater, verticals and the rest. */
class SatellitePatternTest {

	@Test
	void plainCurveIsUnchanged() {
		assertEquals(0.0D, SatellitePattern.OTHER.strength(0.0D), 1e-9, "nothing at the horizon: it fades in as it rises");
		double e = SatellitePattern.RISE_DEGREES;
		assertEquals(SatellitePattern.LOW + (SatellitePattern.HIGH - SatellitePattern.LOW) * Math.sin(Math.toRadians(e)), SatellitePattern.OTHER.strength(e), 1e-9);
		assertEquals(SatellitePattern.HIGH, SatellitePattern.OTHER.strength(90.0D), 1e-9);
		assertEquals(SatelliteRadio.strength(37.0D), SatellitePattern.OTHER.strength(37.0D), 1e-9);
	}

	@Test
	void verticalIsFineLowAndWeakOverhead() {
		for(double e = 0.0D; e <= SatellitePattern.VERTICAL_FADE_FROM; e += 5.0D)
			assertEquals(SatellitePattern.OTHER.strength(e), SatellitePattern.VERTICAL.strength(e), 1e-9, "at " + e);
		assertTrue(SatellitePattern.VERTICAL.strength(90.0D) < 0.25D);
		assertTrue(SatellitePattern.VERTICAL.strength(90.0D) < SatellitePattern.VERTICAL.strength(20.0D) / 2.0D);
		double last = Double.MAX_VALUE;
		for(double e = 60.0D; e <= 90.0D; e += 5.0D) { // Fading all the way into the null.
			double s = SatellitePattern.VERTICAL.strength(e);
			assertTrue(s < last, "at " + e);
			last = s;
		}
		assertTrue(SatellitePattern.VERTICAL.uplinks(30.0D));
		assertTrue(SatellitePattern.VERTICAL.uplinks(75.0D));
		assertFalse(SatellitePattern.VERTICAL.uplinks(88.0D), "heard from straight overhead");
	}

	@Test
	void eggbeaterBeatsEverythingAllPass() {
		for(double e = 0.0D; e <= 90.0D; e += 2.5D) {
			assertTrue(SatellitePattern.EGGBEATER.strength(e) >= SatellitePattern.OTHER.strength(e), "at " + e);
			if(e >= SatellitePattern.RISE_DEGREES)
				assertTrue(SatellitePattern.EGGBEATER.uplinks(e), "at " + e);
		}
		assertTrue(SatellitePattern.EGGBEATER.strength(90.0D) > SatellitePattern.EGGBEATER.strength(10.0D), "best overhead");
		assertTrue(SatellitePattern.OTHER.uplinks(SatellitePattern.RISE_DEGREES) && SatellitePattern.OTHER.uplinks(90.0D));
	}

	@Test
	void outOfRangeElevationsClamp() {
		assertEquals(SatellitePattern.EGGBEATER.strength(0.0D), SatellitePattern.EGGBEATER.strength(-10.0D), 1e-9);
		assertEquals(SatellitePattern.VERTICAL.strength(90.0D), SatellitePattern.VERTICAL.strength(120.0D), 1e-9);
	}

	/** The signal fades in from the horizon and out to it (2026-11-14): nothing at 0, rising smoothly, all of it by RISE_DEGREES. */
	@org.junit.jupiter.api.Test
	void fadesInAtTheHorizon() {
		for(SatellitePattern p : SatellitePattern.values()) {
			assertEquals(0.0D, p.strength(0.0D), 1e-9, p + " at the horizon");
			double last = 0.0D;
			for(double e = 0.5D; e <= SatellitePattern.RISE_DEGREES; e += 0.5D) {
				double s = p.strength(e);
				assertTrue(s > last, p + " rises at " + e);
				last = s;
			}
		}
		assertEquals(1.0D, SatellitePattern.rise(SatellitePattern.RISE_DEGREES), 1e-9);
	}
}
