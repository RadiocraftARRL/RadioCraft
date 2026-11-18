package com.arrl.radiocraft.common.radio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Skip moves across sunrise and sunset instead of snapping (2026-11-18). */
class DayCycleTest {

	private final Band forty = new Band("40m", 650, 100, 2000, 2000, 7000, 7_000_000, 7_300_000);
	private final Band lowBand = new Band("low_band", 600, 3000, 7000, 0, 0, 30_000_000, 50_000_000);

	@Test
	void noonIsDayMidnightIsNightAndDuskIsBetween() {
		assertEquals(1.0D, DayCycle.dayness(0.0D), 1e-9);
		assertEquals(0.0D, DayCycle.dayness(0.5D), 1e-9);
		double last = 1.0D;
		boolean between = false;
		for(double t = 0.0D; t <= 0.5D; t += 0.005D) { // Noon to midnight: never brightens, passes through dusk.
			double d = DayCycle.dayness(t);
			assertTrue(d <= last + 1e-12, "brightened at " + t);
			between |= d > 0.1D && d < 0.9D;
			last = d;
		}
		assertTrue(between, "it snapped");
	}

	@Test
	void theWindowMovesAcrossDusk() {
		double[] half = BandUtils.skipWindow(forty, 0.5D, 1.0D, 1.0D);
		assertEquals(1050, half[0], 1e-9);
		assertEquals(4500, half[1], 1e-9);
		assertEquals(1.0D, half[2], 1e-9);
	}

	@Test
	void aDayOnlyWindowFadesOut() {
		assertEquals(0.25D, BandUtils.skipWindow(lowBand, 0.25D, 1.0D, 1.0D)[2], 1e-9);
		assertNull(BandUtils.skipWindow(lowBand, 0.0D, 1.0D, 1.0D));
		assertEquals(BandUtils.getBaseStrength(lowBand, 5000, 1, 1, true, 1, 1, 1, 1, 0.5D),
				BandUtils.getBaseStrength(lowBand, 5000, 1, 1, 1.0D, 1, 1, 1, 1, 0.5D), 1e-12, "full day changed");
	}
}
