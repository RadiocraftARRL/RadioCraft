package com.arrl.radiocraft.common.radio.ground;

import com.arrl.radiocraft.common.radio.ground.GroundQuality.Soil;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundQualityTest {

	private static List<Soil> rod(Soil soil, int depth) {
		return Collections.nCopies(depth, soil);
	}

	@Test
	void wetClayBeatsDesertSand() {
		double swamp = GroundQuality.rod(rod(Soil.CLAY, 3), GroundQuality.moisture(0.9D, false, true), false);
		double desert = GroundQuality.rod(rod(Soil.SAND, 3), GroundQuality.moisture(0.0D, false, false), false);
		assertTrue(swamp > 0.5D, "A rod in wet clay is a good ground: " + swamp);
		assertTrue(desert < 0.15D, "A rod in desert sand is poor: " + desert);
	}

	@Test
	void deeperAndMoreRodsHelp() {
		double m = GroundQuality.moisture(0.0D, false, false);
		double one = GroundQuality.rod(rod(Soil.SAND, 1), m, false), three = GroundQuality.rod(rod(Soil.SAND, 3), m, false), six = GroundQuality.rod(rod(Soil.SAND, 6), m, false);
		assertTrue(one < three && three < six, "Deeper is better");
		assertTrue(GroundQuality.combine(three, three, three) > three, "More rods are better");
		assertTrue(GroundQuality.combine(three, three) < 2 * three, "Each extra rod does a little less");
	}

	@Test
	void frozenGroundIsPoorAndAirIsNothing() {
		double m = GroundQuality.moisture(0.5D, false, false);
		assertTrue(GroundQuality.rod(rod(Soil.LOAM, 2), m, true) < GroundQuality.rod(rod(Soil.LOAM, 2), m, false) / 3.0D);
		assertEquals(0.0D, GroundQuality.rod(rod(Soil.NONE, 4), m, false));
		assertEquals(0.0D, GroundQuality.combine());
	}

	@Test
	void rainAndWaterWetTheGround() {
		assertTrue(GroundQuality.moisture(0.2D, true, false) > GroundQuality.moisture(0.2D, false, false));
		assertEquals(1.0D, GroundQuality.moisture(1.0D, true, true));
	}

	@Test
	void aGroundCutsNoise() {
		assertEquals(1.0D, GroundQuality.noiseFactor(0.0D));
		assertEquals(1.0D - GroundQuality.MAX_NOISE_CUT, GroundQuality.noiseFactor(1.0D), 1e-9);
		assertTrue(GroundQuality.localNoiseFactor(0.5D) < GroundQuality.noiseFactor(0.5D), "Local noise is cut harder");
		assertEquals("none", GroundQuality.grade(0.0D));
		assertEquals("excellent", GroundQuality.grade(0.9D));
	}
}
