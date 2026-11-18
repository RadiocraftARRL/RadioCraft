package com.arrl.radiocraft.common.radio;

import com.arrl.radiocraft.common.radio.terrain.TerrainMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Multi-hop skip, each hop's takeoff angle, the skip scale moving the whole window, and the radio horizon (2026-11-18). */
class SkipPathTest {

	private final Band twenty = new Band("20m", 500, 1600, 8000, 3000, 8000, 14_000_000, 14_350_000);

	@Test
	void oneHopIsAsBefore() {
		assertEquals(1.0D, SkipPath.best(4800, 1600, 8000, 1, 0.5D).share(), 1e-9, "the middle of the window");
		assertEquals(0.0D, SkipPath.best(12000, 1600, 8000, 1, 0.5D).share(), "one hop reached past the window");
	}

	@Test
	void moreHopsReachFurtherButWeaker() {
		SkipPath.Hops two = SkipPath.best(9600, 1600, 8000, 3, 0.5D);
		assertEquals(2, two.count());
		assertEquals(0.5D, two.share(), 1e-9, "two hops in the middle of their windows lose one hop's loss");
		SkipPath.Hops three = SkipPath.best(14400, 1600, 8000, 3, 0.5D);
		assertEquals(3, three.count());
		assertEquals(0.25D, three.share(), 1e-9);
		assertEquals(0, SkipPath.best(30000, 1600, 8000, 3, 0.5D).count(), "past three hops");
		assertEquals(1, SkipPath.best(5000, 1600, 8000, 3, 0.5D).count(), "took more hops than it needed");
	}

	@Test
	void shortHopsLeaveSteeplyLongOnesLow() {
		double nvis = Math.toDegrees(SkipPath.takeoffRadians(200, 300)); // 100 km.
		double mid = Math.toDegrees(SkipPath.takeoffRadians(2000, 300)); // 1000 km.
		double longest = Math.toDegrees(SkipPath.takeoffRadians(8000, 300)); // 4000 km.
		assertTrue(nvis > 75, "a short hop leaves at " + nvis);
		assertTrue(mid > 20 && mid < 35, "a 1000 km hop leaves at " + mid);
		assertEquals(SkipPath.LOWEST_TAKEOFF_DEGREES, longest, 1e-9, "the longest hop leaves at the lowest angle");
	}

	@Test
	void theSkipScaleMovesTheWholeWindow() {
		double[] w = BandUtils.skipWindow(twenty, true, 1.5D, 1.0D);
		assertEquals(2400, w[0], 1e-9, "the skip zone did not stretch with the reach");
		assertEquals(12000, w[1], 1e-9);
		assertEquals(0.0D, BandUtils.getBaseStrength(twenty, 2000, 1, 1, true, 1, 1.5D, 1, 1, 0.5D), "heard inside the stretched skip zone");
	}

	@Test
	void higherAntennasSeeFurther() {
		assertEquals(1.0D, TerrainMath.horizonScale(6, 6, 6, 8), 1e-9, "the reference height");
		assertTrue(TerrainMath.horizonScale(1.5, 1.5, 6, 8) < 0.6D, "two handhelds reach as far as two masts");
		double hill = TerrainMath.horizonScale(60, 6, 6, 8);
		assertTrue(hill > 1.9D && hill < 2.2D, "a hilltop 60 up reaches x" + hill);
		assertEquals(8.0D, TerrainMath.horizonScale(10000, 10000, 6, 8), "past the cap");
	}
}
