package com.arrl.radiocraft.common.radio.satellite;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SatelliteTrackingTest {

	@AfterEach
	void forget() {
		SatelliteTracking.clear();
	}

	@Test
	void slewsTheShortWayRoundTheCompass() {
		assertEquals(3.0D, SatelliteTracking.slewAzimuth(0.0D, 90.0D, 3.0D), 1e-9);
		assertEquals(357.0D, SatelliteTracking.slewAzimuth(0.0D, 270.0D, 3.0D), 1e-9, "west of north is the short way");
		assertEquals(1.0D, SatelliteTracking.slewAzimuth(359.0D, 1.0D, 3.0D), 1e-9, "across north, arriving");
		assertEquals(42.0D, SatelliteTracking.slewElevation(40.0D, 42.0D, 3.0D), 1e-9);
		assertEquals(87.0D, SatelliteTracking.slewElevation(90.0D, 10.0D, 3.0D), 1e-9);
	}

	@Test
	void locksOnlyWhenOnTheSatellite() {
		assertTrue(SatelliteTracking.onTarget(100.0D, 30.0D, 101.0D, 31.0D));
		assertFalse(SatelliteTracking.onTarget(100.0D, 30.0D, 110.0D, 30.0D), "ten degrees off in azimuth");
		assertFalse(SatelliteTracking.onTarget(100.0D, 30.0D, 100.0D, 40.0D), "ten degrees off in elevation");
		assertTrue(SatelliteTracking.onTarget(0.0D, 89.0D, 170.0D, 89.5D), "near the zenith the bearing hardly matters");
	}

	@Test
	void aRadioOnEitherFrequencyIsTunedToIt() {
		assertTrue(SatelliteTracking.tunedTo(145_800_000.0F, 145_800_000.0F, 435_300_000.0F));
		assertTrue(SatelliteTracking.tunedTo(435_303_000.0F, 145_800_000.0F, 435_300_000.0F), "a Doppler step off the uplink");
		assertFalse(SatelliteTracking.tunedTo(146_520_000.0F, 145_800_000.0F, 435_300_000.0F));
		assertFalse(SatelliteTracking.tunedTo(0.0F, 0.0F, 0.0F), "no radio");
	}

	@Test
	void remembersWhichTrackerIsOnWhat() {
		SatelliteTracking.setLocked(7L, "RC-1");
		assertTrue(SatelliteTracking.lockedOn(7L, "RC-1"));
		assertFalse(SatelliteTracking.lockedOn(7L, "RC-2"));
		assertFalse(SatelliteTracking.lockedOn(8L, "RC-1"));
		SatelliteTracking.setLocked(7L, null);
		assertNull(SatelliteTracking.lockedOn(7L));
	}

	@Test
	void aLockedTrackerIgnoresTheWeatherOthersFade() {
		assertEquals(SatellitePattern.TRACK, SatellitePattern.TRACKER.strength(20.0D, true, true), 1e-9);
		assertTrue(SatellitePattern.TRACKER.strength(20.0D, false, false) > SatellitePattern.EGGBEATER.strength(90.0D));
		for(SatellitePattern p : new SatellitePattern[] { SatellitePattern.EGGBEATER, SatellitePattern.VERTICAL, SatellitePattern.OTHER })
			assertTrue(p.strength(30.0D, true, false) < p.strength(30.0D, false, false), p + " under an aurora");
		assertTrue(SatellitePattern.OTHER.uplinks(12.0D, false, false)); // Above the horizon fade.
		assertFalse(SatellitePattern.OTHER.uplinks(12.0D, true, false), "low in the pass an aurora costs the uplink");
		assertTrue(SatellitePattern.TRACKER.uplinks(20.0D, true, true));
		assertEquals(1.0D, SatellitePattern.weather(false, false), 1e-9);
	}
}
