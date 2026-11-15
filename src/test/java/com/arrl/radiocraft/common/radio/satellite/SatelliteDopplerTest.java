package com.arrl.radiocraft.common.radio.satellite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Doppler shift over a pass (2026-11-12): high coming up, zero overhead, low going away, a few kHz on 2m. */
class SatelliteDopplerTest {

	private static final double TWO_M = 145_900_000.0D;

	/** The shift heard at the origin from a pass played straight over it between ticks 0 and 6000, at {@code time}. */
	private static double shiftAt(double time) {
		SatelliteOrbit.Look before = SatelliteOrbit.passOver(0, 0, 0, 0, 0L, 6000L, time, 0.0D, 500);
		SatelliteOrbit.Look after = SatelliteOrbit.passOver(0, 0, 0, 0, 0L, 6000L, time + SatelliteDoppler.SAMPLE_TICKS, 0.0D, 500);
		return SatelliteDoppler.shiftHz(TWO_M, SatelliteDoppler.rangeRate(before, after));
	}

	@Test
	void positiveApproachingZeroOverheadNegativeReceding() {
		assertTrue(shiftAt(300) > 1_000.0D, "coming up: " + shiftAt(300));
		assertTrue(shiftAt(5600) < -1_000.0D, "going away: " + shiftAt(5600));
		assertEquals(0.0D, shiftAt(3000 - SatelliteDoppler.SAMPLE_TICKS / 2.0D), 60.0D, "closest approach");
		// It falls through the pass, never rising.
		double last = Double.MAX_VALUE;
		for(int t = 0; t < 5980; t += 200) {
			double shift = shiftAt(t);
			assertTrue(shift <= last + 1.0D, "rose at " + t);
			last = shift;
		}
	}

	@Test
	void aFewKhzOn2mAndScalesWithFrequency() {
		double max = SatelliteDoppler.maxShiftHz(TWO_M);
		assertEquals(3_650.0D, max, 50.0D);
		double edge = shiftAt(20);
		assertTrue(edge > 3_000.0D && edge <= max, "at the horizon: " + edge);
		assertEquals(SatelliteDoppler.shiftHz(TWO_M * 3.0D, 0.5D), 3.0D * SatelliteDoppler.shiftHz(TWO_M, 0.5D), 1.0E-6D);
	}

	@Test
	void stillOrWrappedIsNoShift() {
		assertEquals(0.0D, SatelliteDoppler.rangeRate(5_000.0D, 5_000.0D, 0.0D));
		assertEquals(0.0D, SatelliteDoppler.rangeRate(5_000.0D, 4_000.0D, 20_000.0D), "a wrap is no movement");
		assertTrue(SatelliteDoppler.slantRange(0.0D) > 0.0D);
		assertTrue(SatelliteDoppler.slantRange(SatelliteOrbit.COVERAGE) > SatelliteDoppler.slantRange(1_000.0D));
	}

	@Test
	void orbitingSatelliteShiftsOnARealPass() {
		SatelliteOrbit orbit = new SatelliteOrbit(0.7D, 1_234.0D, 5_678.0D, 0.02D);
		SatelliteOrbit.Pass pass = orbit.nextPasses(100.0D, -200.0D, 0.0D, 1).getFirst();
		double rise = SatelliteDoppler.rangeRate(orbit.look(100.0D, -200.0D, pass.rise() + 5), orbit.look(100.0D, -200.0D, pass.rise() + 25));
		double set = SatelliteDoppler.rangeRate(orbit.look(100.0D, -200.0D, pass.set() - 25), orbit.look(100.0D, -200.0D, pass.set() - 5));
		assertTrue(SatelliteDoppler.shiftHz(TWO_M, rise) > 0.0D, "rising: " + rise);
		assertTrue(SatelliteDoppler.shiftHz(TWO_M, set) < 0.0D, "setting: " + set);
	}
}
