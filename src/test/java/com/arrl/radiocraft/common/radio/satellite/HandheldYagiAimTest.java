package com.arrl.radiocraft.common.radio.satellite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandheldYagiAimTest {

	@Test
	void offDegreesMeasuresTheAngleBetweenLookAndSatellite() {
		assertEquals(0.0D, HandheldYagiAim.offDegrees(90.0D, 30.0D, 90.0D, 30.0D), 1.0E-6D);
		assertEquals(90.0D, HandheldYagiAim.offDegrees(0.0D, 0.0D, 90.0D, 0.0D), 1.0E-6D);
		assertEquals(180.0D, HandheldYagiAim.offDegrees(0.0D, 0.0D, 180.0D, 0.0D), 1.0E-6D);
		assertEquals(45.0D, HandheldYagiAim.offDegrees(123.0D, 90.0D, 10.0D, 45.0D), 1.0E-6D); // Overhead: the bearing does not matter.
		assertEquals(5.0D, HandheldYagiAim.offDegrees(358.0D, 0.0D, 3.0D, 0.0D), 1.0E-6D); // Across north.
	}

	@Test
	void minecraftYawAndPitchBecomeBearingAndElevation() {
		assertEquals(0.0D, HandheldYagiAim.bearingOfYaw(180.0D), 1.0E-9D);   // Facing north (-Z).
		assertEquals(90.0D, HandheldYagiAim.bearingOfYaw(-90.0D), 1.0E-9D);  // East (+X).
		assertEquals(180.0D, HandheldYagiAim.bearingOfYaw(0.0D), 1.0E-9D);   // South.
		assertEquals(270.0D, HandheldYagiAim.bearingOfYaw(90.0D + 720.0D), 1.0E-9D);
		assertEquals(90.0D, HandheldYagiAim.elevationOfPitch(-90.0D), 1.0E-9D);
	}

	@Test
	void fullOnTargetAndFadesAway() {
		assertEquals(1.0D, HandheldYagiAim.aim(0.0D));
		assertEquals(1.0D, HandheldYagiAim.aim(HandheldYagiAim.FULL_DEGREES));
		assertEquals(HandheldYagiAim.OFF_TARGET, HandheldYagiAim.strength(60.0D, 120.0D), 1.0E-9D);
		double previous = 2.0D;
		for(double off = 0.0D; off <= 60.0D; off += 1.0D) {
			double now = HandheldYagiAim.aim(off);
			assertTrue(now <= previous, "rises again at " + off);
			previous = now;
		}
	}

	@Test
	void uplinkGoesWhereTheDocsSay() {
		double lost = 0.0D;
		for(double off = 0.0D; off <= 90.0D; off += 0.1D)
			if(HandheldYagiAim.strength(60.0D, off) >= SatellitePattern.UPLINK_FLOOR)
				lost = off;
		assertEquals(HandheldYagiAim.UPLINK_LOST_ABOUT, lost, 2.0D);
	}

	@Test
	void aimedUpItBeatsAVerticalOverhead() {
		assertTrue(HandheldYagiAim.strength(90.0D, 0.0D) >= SatellitePattern.UPLINK_FLOOR);
		assertTrue(!SatellitePattern.VERTICAL.uplinks(90.0D));
		assertEquals(0.0D, HandheldYagiAim.strength(0.0D, 0.0D), 1.0E-9D); // Still fades at the horizon.
	}

	@Test
	void aHandheldsOwnWhipIsWeakerButForHighUp() {
		assertEquals(SatellitePattern.HANDHELD_WHIP, SatellitePattern.handheld(64.0D), 1.0E-9D);
		assertEquals(SatellitePattern.HANDHELD_WHIP, SatellitePattern.handheld(SatellitePattern.HANDHELD_FROM_Y), 1.0E-9D);
		assertEquals(1.0D, SatellitePattern.handheld(SatellitePattern.HANDHELD_FULL_Y), 1.0E-9D);
		assertEquals(1.0D, SatellitePattern.handheld(300.0D), 1.0E-9D);
		assertTrue(SatellitePattern.handheld(176.0D) > SatellitePattern.HANDHELD_WHIP && SatellitePattern.handheld(176.0D) < 1.0D);
		// Low in the world (2026-11-16, the maintainer's sketch): clean low in a pass, hissy higher up, nothing overhead.
		assertTrue(SatellitePattern.handheldWhip(20.0D, 64.0D) >= SatellitePattern.UPLINK_FLOOR, "20 degrees up");
		assertTrue(SatellitePattern.handheldWhip(50.0D, 64.0D) < SatellitePattern.UPLINK_FLOOR, "50 degrees up");
		assertTrue(SatellitePattern.handheldWhip(90.0D, 64.0D) < 0.01D, "overhead");
		assertTrue(SatellitePattern.handheldWhip(1.0D, 64.0D) < 0.05D, "at the horizon");
		// Up high it is a full whip: clean higher in the pass.
		assertTrue(SatellitePattern.handheldWhip(50.0D, 300.0D) >= SatellitePattern.UPLINK_FLOOR, "50 degrees up, high");
		assertTrue(SatellitePattern.VERTICAL.uplinks(6.0D));
	}

	@Test
	void tenDegreesGoodAndNinetyOffUnderSquelchOne() {
		assertEquals(HandheldYagiAim.ON_TARGET, HandheldYagiAim.strength(60.0D, 10.0D), 1.0E-9D);
		assertTrue(HandheldYagiAim.strength(60.0D, 15.0D) < HandheldYagiAim.ON_TARGET, "still full 5 degrees past the good cone");
		assertTrue(HandheldYagiAim.strength(60.0D, 90.0D) < 0.025D, "squelch 1 opens 90 degrees off");
		assertTrue(HandheldYagiAim.strength(60.0D, 60.0D) > HandheldYagiAim.strength(60.0D, 80.0D), "no more noise further off");
	}
}
