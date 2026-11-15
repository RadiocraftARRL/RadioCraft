package com.arrl.radiocraft.common.radio.antenna;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The yagi's 16 directions (2026-11-12): the step from a bearing, its split into FACING and TURN, and the beam. */
class YagiAimTest {

	@Test
	void bearingsSnapToSixteenSteps() {
		assertEquals(0, YagiAim.step(0.0D));
		assertEquals(0, YagiAim.step(11.0D));
		assertEquals(1, YagiAim.step(12.0D));
		assertEquals(4, YagiAim.step(90.0D));
		assertEquals(15, YagiAim.step(-22.5D));
		assertEquals(0, YagiAim.step(355.0D));
		assertEquals(8, YagiAim.step(180.0D + 720.0D));
	}

	@Test
	void everyStepSplitsIntoTheNearestFacingAndATurn() {
		Set<String> seen = new HashSet<>();
		for(int step = 0; step < 16; step++) {
			int[] split = YagiAim.split(step);
			assertTrue(split[0] >= 0 && split[0] < 4, "quadrant " + split[0]);
			assertTrue(split[1] >= 0 && split[1] <= 3, "turn " + split[1]);
			double back = split[0] * 90.0D + (split[1] - YagiAim.STRAIGHT) * 22.5D;
			assertEquals(step * 22.5D, (back % 360.0D + 360.0D) % 360.0D, 1e-9, "step " + step);
			assertTrue(seen.add(split[0] + "/" + split[1]), "two steps share " + split[0] + "/" + split[1]);
		}
		assertArrayEquals(new int[] { 0, YagiAim.STRAIGHT }, YagiAim.split(0));
		assertArrayEquals(new int[] { 1, YagiAim.STRAIGHT }, YagiAim.split(4));
	}

	@Test
	void theBeamFollowsTheBearing() {
		// North is -Z, east +X.
		assertEquals(1.0D, YagiAim.efficiency(0.0D, 0.0D, -100.0D), 1e-9);
		assertEquals(1.0D, YagiAim.efficiency(90.0D, 100.0D, 0.0D), 1e-9);
		assertTrue(YagiAim.efficiency(0.0D, 0.0D, 100.0D) < 0.15D, "the back");
		// 22.5 degrees off: a turned yagi does better than the nearest of the four would.
		double turned = YagiAim.efficiency(22.5D, 38.0D, -92.0D);
		double north = YagiAim.efficiency(0.0D, 38.0D, -92.0D);
		assertTrue(turned > north, turned + " vs " + north);
	}
}
