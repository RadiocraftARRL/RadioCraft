package com.arrl.radiocraft.common.radio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A light skip opening (2026-11-18): skip reaching only the near share of its window, as 10m in average weather. */
class SkipReachTest {

	private final Band tenMetres = new Band("10m", 700, 2500, 8000, 2500, 6000, 28_000_000, 29_700_000);

	@Test
	void aLightOpeningReachesOnlyTheNearPart() {
		double reach = 0.18D; // 2500 to 3490 blocks.
		assertTrue(BandUtils.getBaseStrength(tenMetres, 3000, 1, 1, true, 1, 1, reach) > 0.9D, "not heard in the middle of the opening");
		assertEquals(0.0D, BandUtils.getBaseStrength(tenMetres, 4000, 1, 1, true, 1, 1, reach), "heard past the opening");
		assertEquals(0.0D, BandUtils.getBaseStrength(tenMetres, 2000, 1, 1, true, 1, 1, reach), "heard in the skip zone");
	}

	@Test
	void fullReachIsAsBefore() {
		for(int d = 0; d < 9000; d += 250)
			assertEquals(BandUtils.getBaseStrength(tenMetres, d, 1, 1, true, 1, 1),
					BandUtils.getBaseStrength(tenMetres, d, 1, 1, true, 1, 1, 1.0D), 1e-12);
	}
}
