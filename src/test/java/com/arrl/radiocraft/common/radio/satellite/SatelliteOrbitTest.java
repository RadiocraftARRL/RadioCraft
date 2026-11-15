package com.arrl.radiocraft.common.radio.satellite;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Satellite orbits (2026-11-11; 2026-11-14 fewer, shorter): 1 or 2 passes a day of up to two minutes, elevations, and passes that agree with looks. */
class SatelliteOrbitTest {

	private static final double DAY = SatelliteOrbit.DAY_TICKS;

	@Test
	void elevationIsStraightUpOverheadAndZeroAtTheCoverageEdge() {
		assertEquals(90.0D, SatelliteOrbit.elevation(0.0D), 1.0E-9);
		assertEquals(0.0D, SatelliteOrbit.elevation(SatelliteOrbit.COVERAGE), 1.0E-9);
		assertTrue(SatelliteOrbit.elevation(SatelliteOrbit.COVERAGE + 100.0D) < 0.0D);
		assertTrue(SatelliteOrbit.elevation(2000.0D) > SatelliteOrbit.elevation(5000.0D));
	}

	@Test
	void azimuthIsACompassBearing() {
		assertEquals(0.0D, SatelliteOrbit.azimuth(0.0D, -1.0D), 1.0E-9); // North is -Z.
		assertEquals(90.0D, SatelliteOrbit.azimuth(1.0D, 0.0D), 1.0E-9);
		assertEquals(180.0D, SatelliteOrbit.azimuth(0.0D, 1.0D), 1.0E-9);
		assertEquals(270.0D, SatelliteOrbit.azimuth(-1.0D, 0.0D), 1.0E-9);
		assertEquals("NE", SatelliteOrbit.compass(44.0D));
		assertEquals("N", SatelliteOrbit.compass(359.0D));
	}

	/**
	 * Over many orbits and places, a day almost always brings 1 to 3 passes, under 2 on average, and the satellite is in
	 * view well under a quarter of the day (2026-11-14: out of view most of the time, as a real one is).
	 */
	@Test
	void aFewPassesADayMostlyOutOfView() {
		Random random = new Random(11L);
		int days = 0, inRange = 0, total = 0;
		double up = 0.0D;
		for(int n = 0; n < 2000; n++) {
			SatelliteOrbit orbit = SatelliteOrbit.random(random);
			double x = random.nextDouble() * 100_000.0D - 50_000.0D, z = random.nextDouble() * 100_000.0D - 50_000.0D;
			double from = random.nextInt(200) * DAY;
			java.util.List<SatelliteOrbit.Pass> passes = orbit.passes(x, z, from, from + DAY).stream().filter(p -> p.rise() >= from).toList();
			int count = passes.size();
			days++;
			total += count;
			if(count >= 1 && count <= 3)
				inRange++;
			for(SatelliteOrbit.Pass pass : passes) {
				up += pass.duration();
				assertTrue(pass.duration() <= 2_700.0D, "a pass of " + pass.duration() + " ticks");
			}
		}
		assertTrue(inRange >= days * 0.95D, "only " + inRange + " of " + days + " days had 1 to 3 passes");
		double mean = (double)total / days;
		assertTrue(mean > 1.3D && mean < 2.0D, "mean passes a day " + mean);
		assertTrue(up / (days * DAY) < 0.2D, "in view " + up / (days * DAY) + " of the day");
	}

	/** A pass is up for a few minutes at most, and its times agree with what the place sees. */
	@Test
	void passesLastAFewMinutesAndAgreeWithTheLook() {
		Random random = new Random(5L);
		for(int n = 0; n < 200; n++) {
			SatelliteOrbit orbit = SatelliteOrbit.random(random);
			double x = random.nextDouble() * 40_000.0D, z = random.nextDouble() * 40_000.0D;
			for(SatelliteOrbit.Pass pass : orbit.passes(x, z, 0.0D, DAY)) {
				assertTrue(pass.duration() > 0.0D && pass.duration() <= 3.5D * 1200.0D, "a pass of " + pass.duration() / 1200.0D + " minutes");
				assertTrue(orbit.look(x, z, pass.culmination()).up());
				assertEquals(pass.maxElevation(), orbit.look(x, z, pass.culmination()).elevation(), 0.05D);
				assertFalse(orbit.look(x, z, pass.rise() - 20.0D).up(), "up before it rose");
				assertFalse(orbit.look(x, z, pass.set() + 20.0D).up(), "up after it set");
				assertTrue(orbit.look(x, z, pass.rise() + 20.0D).up() || pass.duration() < 40.0D);
				assertEquals(pass.riseAzimuth(), orbit.look(x, z, pass.rise() + 0.01D).azimuth(), 1.0D);
			}
		}
	}

	/** Never two copies in coverage at once: the place is up exactly during one of its passes, or none. */
	@Test
	void upOnlyDuringAPass() {
		Random random = new Random(9L);
		SatelliteOrbit orbit = SatelliteOrbit.random(random);
		double x = 1234.0D, z = -5678.0D;
		List<SatelliteOrbit.Pass> passes = orbit.passes(x, z, 0.0D, DAY);
		for(double t = 0.0D; t < DAY; t += 37.0D) {
			final double time = t;
			long during = passes.stream().filter(p -> p.during(time)).count();
			assertTrue(during <= 1, "two passes at once at " + t);
			assertEquals(during == 1, orbit.look(x, z, t).up(), "look and passes disagree at " + t);
		}
	}

	@Test
	void nextPassesStartWithTheOneUpNow() {
		SatelliteOrbit orbit = new SatelliteOrbit(0.3D, 100.0D, 200.0D, 0.02D);
		List<SatelliteOrbit.Pass> passes = orbit.nextPasses(0.0D, 0.0D, 0.0D, 3);
		assertEquals(3, passes.size());
		double t = passes.getFirst().culmination();
		List<SatelliteOrbit.Pass> during = orbit.nextPasses(0.0D, 0.0D, t, 3);
		assertEquals(passes.getFirst().rise(), during.getFirst().rise(), 1.0E-6);
		assertTrue(during.getFirst().during(t));
	}

	@Test
	void theSameOrbitIsInTheSamePlaceEverywhere() {
		SatelliteOrbit orbit = new SatelliteOrbit(1.1D, 5000.0D, 9000.0D, -0.03D);
		SatelliteOrbit.Look a = orbit.look(0.0D, 0.0D, 5000.0D);
		SatelliteOrbit.Look b = orbit.look(a.subX() + 300.0D, a.subZ(), 5000.0D);
		assertEquals(a.subX(), b.subX(), 1.0E-6);
		assertEquals(a.subZ(), b.subZ(), 1.0E-6);
		assertEquals(300.0D, b.distance(), 1.0E-6);
	}

	/** A played pass (the admin's overhead, 2026-11-11) rises, crosses high near the point and sets; two satellites differ. */
	@org.junit.jupiter.api.Test
	void playedPassMovesAcrossTheSky() {
		SatelliteOrbit.Look start = SatelliteOrbit.passOver(0, 0, 0, 0, 0L, 6000L, 0.0D, 0.3D, 42);
		SatelliteOrbit.Look middle = SatelliteOrbit.passOver(0, 0, 0, 0, 0L, 6000L, 3000.0D, 0.3D, 42);
		SatelliteOrbit.Look end = SatelliteOrbit.passOver(0, 0, 0, 0, 0L, 6000L, 6000.0D, 0.3D, 42);
		org.junit.jupiter.api.Assertions.assertTrue(start.up() && end.up(), "up from start to end");
		org.junit.jupiter.api.Assertions.assertTrue(start.elevation() < 10.0D && end.elevation() < 10.0D, "low at rise and set");
		org.junit.jupiter.api.Assertions.assertTrue(middle.elevation() > 45.0D, "high in the middle: " + middle.elevation());
		org.junit.jupiter.api.Assertions.assertTrue(Math.abs(start.azimuth() - end.azimuth()) > 90.0D, "rises and sets on opposite sides");
		SatelliteOrbit.Look other = SatelliteOrbit.passOver(0, 0, 0, 0, 0L, 6000L, 3000.0D, 1.7D, 977);
		org.junit.jupiter.api.Assertions.assertTrue(Math.abs(other.subX() - middle.subX()) + Math.abs(other.subZ() - middle.subZ()) > 100.0D,
				"two satellites in one place");
	}
}
