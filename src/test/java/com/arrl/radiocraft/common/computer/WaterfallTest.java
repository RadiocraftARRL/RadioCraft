package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rig control waterfall's bins, levels, zoom and signal jumps (computer batch 3B). */
class WaterfallTest {

	private static final double LO = 14_000_000.0D, HI = 14_350_000.0D;

	@Test
	void frequenciesFallInBins() {
		assertEquals(0, Waterfall.bin(LO, LO, HI, 76));
		assertEquals(75, Waterfall.bin(HI, LO, HI, 76), "the top edge is in the last bin");
		assertEquals(-1, Waterfall.bin(LO - 1.0D, LO, HI, 76));
		assertEquals(-1, Waterfall.bin(HI + 1.0D, LO, HI, 76));
		int ft8 = Waterfall.bin(14_074_000.0D, LO, HI, 76);
		assertEquals(16, ft8);
		double centre = Waterfall.centre(ft8, LO, HI, 76);
		assertEquals(ft8, Waterfall.bin(centre, LO, HI, 76), "a bin's centre is in it");
		assertTrue(Math.abs(centre - 14_074_000.0D) < (HI - LO) / 76.0D);
		assertEquals(-1, Waterfall.bin(5.0D, 10.0D, 10.0D, 76), "an empty span has no bins");
	}

	@Test
	void levelsRiseThreeDbAtATime() {
		assertEquals(0, Waterfall.level(0.0D, 0.1D), "nothing heard");
		assertEquals(1, Waterfall.level(0.001D, 0.1D), "20 dB under the noise still shows");
		assertEquals(1, Waterfall.level(1.0E-9D, 0.1D), "anything at all is at least 1");
		int atNoise = Waterfall.level(0.1D, 0.1D);
		assertEquals(8, atNoise);
		assertEquals(atNoise + 1, Waterfall.level(0.2D, 0.1D));
		assertEquals(14, Waterfall.level(1.0D, 0.0001D), "+20 dB over the minimum noise");
		assertEquals(Waterfall.MAX_LEVEL, Waterfall.level(1000.0D, 0.01D), "capped");
		assertEquals(Waterfall.level(0.1D, Waterfall.MIN_NOISE), Waterfall.level(0.1D, 0.0D), "a silent receiver is measured against the minimum");
	}

	@Test
	void zoomNarrowsAroundTheDial() {
		assertArrayEquals(new double[] { LO, HI }, Waterfall.span(0, 14_074_000.0D, LO, HI));
		double[] quarter = Waterfall.span(1, 14_174_000.0D, LO, HI);
		assertEquals((HI - LO) / 4.0D, quarter[1] - quarter[0], 1.0E-6);
		assertTrue(quarter[0] < 14_174_000.0D && quarter[1] > 14_174_000.0D);
		double[] edge = Waterfall.span(2, LO, LO, HI);
		assertEquals(LO, edge[0], 1.0E-6, "kept inside the band at its edge");
		assertEquals((HI - LO) / 16.0D, edge[1] - edge[0], 1.0E-6);
	}

	@Test
	void jumpsToTheNextSignalsStrongestBin() {
		int[] levels = new int[20];
		levels[3] = 4;
		levels[4] = 9;
		levels[5] = 6; // A signal over bins 3-5, strongest at 4.
		levels[12] = 3;
		levels[13] = 5;
		assertEquals(13, Waterfall.nextSignal(levels, 4, 1, 2), "off this signal and on to the next, at its peak");
		assertEquals(4, Waterfall.nextSignal(levels, 10, -1, 2), "down to the one below, at its peak");
		assertEquals(-1, Waterfall.nextSignal(levels, 13, 1, 2), "nothing above");
		assertEquals(-1, Waterfall.nextSignal(levels, 4, -1, 10), "nothing that strong");
	}
}
