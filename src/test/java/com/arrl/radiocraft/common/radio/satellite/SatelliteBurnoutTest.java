package com.arrl.radiocraft.common.radio.satellite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A satellite burning up as its orbit decays (2026-11-12). */
class SatelliteBurnoutTest {

	@Test
	void lastsThreeSeconds() {
		assertEquals(60, SatelliteBurnout.TICKS);
		assertEquals(-1.0D, SatelliteBurnout.progress(1_000L, 999.0D));
		assertEquals(0.0D, SatelliteBurnout.progress(1_000L, 1_000.0D));
		assertEquals(0.5D, SatelliteBurnout.progress(1_000L, 1_030.0D), 1.0E-9);
		assertEquals(1.0D, SatelliteBurnout.progress(1_000L, 1_060.0D), 1.0E-9);
		assertFalse(SatelliteBurnout.burning(1_000L, 1_060.5D), "gone after three seconds");
		assertTrue(SatelliteBurnout.burning(1_000L, 1_059.0D));
	}

	@Test
	void flaresThenFades() {
		assertEquals(0.0D, SatelliteBurnout.brightness(0.0D));
		assertEquals(1.0D, SatelliteBurnout.brightness(0.2D), 1.0E-9);
		assertTrue(SatelliteBurnout.brightness(0.5D) < 1.0D && SatelliteBurnout.brightness(0.5D) > SatelliteBurnout.brightness(0.9D));
		assertEquals(0.0D, SatelliteBurnout.brightness(1.0D), 1.0E-9);
		assertEquals(0.0D, SatelliteBurnout.brightness(-1.0D));
	}

	@Test
	void seenOnlyFromUnderIt() {
		assertTrue(SatelliteBurnout.visible(0.0D, 0.0D, 0.5D * SatelliteOrbit.COVERAGE, 0.0D));
		assertFalse(SatelliteBurnout.visible(0.0D, 0.0D, 1.2D * SatelliteOrbit.COVERAGE, 0.0D), "below the horizon there");
	}

	@Test
	void streaksAlongItsHeadingAndSinks() {
		SatelliteOrbit.Look start = SatelliteBurnout.look(0.0D, 0.0D, -1_000.0D, 0.0D, 0.0D, 0.0D);
		SatelliteOrbit.Look end = SatelliteBurnout.look(0.0D, 0.0D, -1_000.0D, 0.0D, 0.0D, 1.0D);
		assertEquals(-1_000.0D, start.subX(), 1.0E-9);
		assertEquals(-1_000.0D + SatelliteBurnout.TRACK_BLOCKS, end.subX(), 1.0E-9, "ran along +X");
		assertEquals(270.0D, start.azimuth(), 1.0E-6, "began in the west");
		assertEquals(90.0D, end.azimuth(), 1.0E-6, "ended in the east");
		double plain = SatelliteOrbit.lookAt(0.0D, 0.0D, -1_000.0D + SatelliteBurnout.TRACK_BLOCKS, 0.0D).elevation();
		assertEquals(plain * SatelliteBurnout.SINK, end.elevation(), 1.0E-9, "sinks as it burns");
	}
}
