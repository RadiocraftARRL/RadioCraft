package com.arrl.radiocraft.common.radio.satellite;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Geostationary satellites, their decay and decommissioning (2026-11-12). */
class SatelliteGeoTest {

	@Test
	void parksNearWhereItPointsButNeverOnIt() {
		Random random = new Random(5L);
		for(int i = 0; i < 200; i++) {
			SatelliteGeo geo = SatelliteGeo.park(1000.0D, -2000.0D, random);
			assertEquals(1000.0D, geo.aimX());
			assertEquals(-2000.0D, geo.aimZ());
			assertTrue(geo.offset() >= SatelliteGeo.PARK_MIN - 1.0E-6 && geo.offset() <= SatelliteGeo.PARK_MAX + 1.0E-6, "offset " + geo.offset());
		}
		SatelliteGeo a = SatelliteGeo.park(0.0D, 0.0D, new Random(1L)), b = SatelliteGeo.park(0.0D, 0.0D, new Random(2L));
		SatelliteOrbit.Look la = a.look(0.0D, 0.0D), lb = b.look(0.0D, 0.0D);
		assertTrue(Math.abs(la.azimuth() - lb.azimuth()) > 1.0D || Math.abs(la.elevation() - lb.elevation()) > 1.0D,
				"two activated in one place sit apart in the sky");
	}

	@Test
	void alwaysUpInItsCoverageNeverOutsideIt() {
		double C = SatelliteOrbit.COVERAGE;
		SatelliteGeo geo = new SatelliteGeo(0.0D, 0.0D, SatelliteGeo.PARK_MAX, 0.0D); // Parked the most it can be, east.
		for(int deg = 0; deg < 360; deg += 15) {
			double rad = Math.toRadians(deg);
			for(double r : new double[] { 0.0D, 0.2D * C, 0.6D * C, C - 10.0D }) {
				SatelliteOrbit.Look look = geo.look(Math.cos(rad) * r, Math.sin(rad) * r);
				assertTrue(look.up(), "down at " + r + " blocks, bearing " + deg);
				assertTrue(look.elevation() >= 0.0D, "below the horizon inside its coverage: " + look.elevation() + " at " + r + "/" + deg);
			}
			SatelliteOrbit.Look out = geo.look(Math.cos(rad) * (C + 10.0D), Math.sin(rad) * (C + 10.0D));
			assertFalse(out.up(), "up outside its coverage at bearing " + deg);
		}
		// Centred on where it points, not where it parks: 0.9 of the coverage west of the aim is further from the parked point, and up.
		assertTrue(geo.look(-0.9D * C, 0.0D).up());
		assertFalse(geo.look(1.1D * C, 0.0D).up(), "inside the coverage of where it parks, but outside where it points: not up");
	}

	@Test
	void staysPutInTheSky() {
		SatelliteGeo geo = SatelliteGeo.park(300.0D, 400.0D, new Random(9L));
		SatelliteOrbit.Look look = geo.look(300.0D, 400.0D);
		assertEquals(look, geo.look(300.0D, 400.0D));
		assertTrue(look.elevation() > 40.0D && look.elevation() < 85.0D, "where it points it is high, but not straight up: " + look.elevation());
		// Ahead of it the azimuth points at where it parks.
		SatelliteGeo east = new SatelliteGeo(0.0D, 0.0D, 1_000.0D, 0.0D);
		assertEquals(90.0D, east.look(0.0D, 0.0D).azimuth(), 1.0E-6);
		assertEquals(1_000.0D, east.look(0.0D, 0.0D).subX(), 1.0E-6);
	}

	@Test
	void imagingScansAStripThroughWhereItPoints() {
		SatelliteGeo geo = new SatelliteGeo(100.0D, 200.0D, 900.0D, 200.0D);
		double speed = 6.0D;
		for(int t = 0; t < 10_000; t += 37) {
			double[] at = geo.scan(t, 0.0D, speed);
			assertEquals(200.0D, at[1], 1.0E-6, "along the heading (+X) only");
			assertTrue(Math.abs(at[0] - 100.0D) <= SatelliteGeo.SCAN_HALF + 1.0E-6, "within the strip: " + at[0]);
		}
		double[] a = geo.scan(10.0D, 0.0D, speed), b = geo.scan(11.0D, 0.0D, speed);
		assertEquals(speed, b[0] - a[0], 1.0E-6, "moves at the scan speed");
	}

	@Test
	void geostationaryOrbitsDecayTwiceAsFast() {
		long day = 86_400_000L, launch = 1_000_000L;
		assertEquals(launch + 90 * day, SatelliteRules.decayMillis(launch, 90, false));
		assertEquals(launch + 45 * day, SatelliteRules.decayMillis(launch, 90, true));
		assertEquals(90, SatelliteRules.daysLeft(launch, 90, false, launch));
		assertEquals(45, SatelliteRules.daysLeft(launch, 90, true, launch));
		assertEquals(0, SatelliteRules.daysLeft(launch, 90, true, launch + 45 * day));
		assertTrue(SatelliteRules.daysLeft(launch, 90, false, launch + 45 * day) > 0);
		assertTrue(SatelliteRules.decayNear(launch, 90, true, launch + 40 * day));
		assertFalse(SatelliteRules.decayNear(launch, 90, false, launch + 40 * day));
		assertEquals(SatelliteRules.decayMillis(launch, 90), SatelliteRules.decayMillis(launch, 90, false), "the old sum unchanged");
	}

	private static Satellite satellite(SatelliteGeo geo) {
		return new Satellite("RO-7", true, 145_200_000.0F, 145_800_000.0F, 145_990_000.0F, 67.0F, new UUID(1L, 2L), "Owner", 123L, "2026-11-12",
				new SatelliteOrbit(0.5D, 10.0D, 20.0D, 0.01D), new UUID(3L, 4L), geo);
	}

	@Test
	void geoAndDecommissionAreSavedAndLoaded() {
		SatelliteGeo geo = new SatelliteGeo(10.0D, 20.0D, 510.0D, 20.0D);
		Satellite s = satellite(geo);
		assertTrue(s.geostationary());
		assertFalse(s.decommissioned());
		assertTrue(s.nextPasses(0.0D, 0.0D, 0L, 4).isEmpty(), "a geostationary one has no passes");
		assertEquals(geo.look(0.0D, 0.0D), s.look(0.0D, 0.0D, 0L));
		assertEquals(s.look(0.0D, 0.0D, 0L), s.look(0.0D, 0.0D, 50_000L), "it does not move");
		s.decommissioned = true;
		Satellite loaded = Satellite.fromTag(s.toTag());
		assertEquals(geo, loaded.geo);
		assertTrue(loaded.decommissioned());

		Satellite plain = satellite(null);
		CompoundTag tag = plain.toTag();
		assertFalse(tag.contains("geo") || tag.contains("decommissioned"), "nothing new saved for an ordinary one");
		Satellite back = Satellite.fromTag(tag);
		assertFalse(back.geostationary() || back.decommissioned());
		assertFalse(back.nextPasses(0.0D, 0.0D, 0L, 4).isEmpty(), "an orbiting one passes");
	}
}
