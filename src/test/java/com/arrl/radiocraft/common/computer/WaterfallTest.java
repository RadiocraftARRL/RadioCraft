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

	/** The External CRT Monitor's spans (2026-11-12): a few kHz on HF, wider on VHF, centred, slid inside the band. */
	@Test
	void monitorSpansAreNarrowAndCentred() {
		double[] narrow = Waterfall.monitorSpan(false, 14_200_000.0D, LO, HI);
		assertArrayEquals(new double[] { 14_197_000.0D, 14_203_000.0D }, narrow, 0.001D, "6 kHz round the dial on HF");
		double[] wide = Waterfall.monitorSpan(true, 14_200_000.0D, LO, HI);
		assertEquals(24_000.0D, wide[1] - wide[0], 0.001D);
		assertTrue(wide[1] - wide[0] < (HI - LO) / 10.0D, "WIDE is far narrower than rig control's whole band");
		assertTrue(wide[1] - wide[0] > 3.0D * (narrow[1] - narrow[0]), "WIDE is several times NARROW");
		assertEquals(6_000.0D, Waterfall.monitorWidth(false, 27_185_000.0D), 0.001D, "CB takes the HF spans");
		assertEquals(50_000.0D, Waterfall.monitorWidth(false, 146_520_000.0D), 0.001D, "VHF is wider");
		assertEquals(200_000.0D, Waterfall.monitorWidth(true, 146_520_000.0D), 0.001D);
	}

	@Test
	void monitorSpansStayInTheBand() {
		assertArrayEquals(new double[] { LO, LO + 24_000.0D }, Waterfall.monitorSpan(true, LO + 1_000.0D, LO, HI), 0.001D, "slid up off the bottom edge");
		assertArrayEquals(new double[] { HI - 6_000.0D, HI }, Waterfall.monitorSpan(false, HI, LO, HI), 0.001D, "slid down off the top edge");
		assertArrayEquals(new double[] { 100.0D, 10_100.0D }, Waterfall.monitorSpan(true, 5_000.0D, 100.0D, 10_100.0D), 0.001D, "a band narrower than the span: all of it");
		assertArrayEquals(new double[] { 7_097_000.0D, 7_103_000.0D }, Waterfall.monitorSpan(false, 7_100_000.0D, 0.0D, 0.0D), 0.001D, "no band: centred");
	}
}
