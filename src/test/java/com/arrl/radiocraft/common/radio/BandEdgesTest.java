package com.arrl.radiocraft.common.radio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where ham radios tune and transmit, with and without a MARS mod (maintainer, 2026-11-04). */
class BandEdgesTest {

	private static final Band TWENTY = new Band("20m", 300, 2000, 6000, 800, 1000, 14_000_000, 14_350_000);
	private static final Band EIGHTY = new Band("80m", 600, 700, 800, 600, 2000, 3_500_000, 4_000_000);
	private static final Band TWO = new Band("2m", 500, 0, 0, 0, 0, 144_000_000, 148_000_000);
	private static final Band LOW = new Band(Band.LOW_BAND, 650, 3000, 7000, 0, 0, 30_000_000, 50_000_000);

	@Test
	void hfBandsGrowByHalfTheirWidthAQuarterEachSide() {
		assertEquals(13_912_500.0F, BandEdges.tuneMin(TWENTY), 1.0F);
		assertEquals(14_437_500.0F, BandEdges.tuneMax(TWENTY, 174_000_000.0F), 1.0F);
		assertEquals(3_375_000.0F, BandEdges.tuneMin(EIGHTY), 1.0F);
		assertEquals(4_125_000.0F, BandEdges.tuneMax(EIGHTY, 174_000_000.0F), 1.0F);
	}

	@Test
	void vhfTunesFromAQuarterBelowTwoMetresToTheTopOfCommercialVhf() {
		assertEquals(143_000_000.0F, BandEdges.tuneMin(TWO), 1.0F);
		assertEquals(174_000_000.0F, BandEdges.tuneMax(TWO, 174_000_000.0F), 1.0F);
	}

	@Test
	void commercialBandsTuneOnlyTheirBand() {
		assertEquals(30_000_000.0F, BandEdges.tuneMin(LOW), 1.0F);
		assertEquals(50_000_000.0F, BandEdges.tuneMax(LOW, 174_000_000.0F), 1.0F);
	}

	@Test
	void outOfBandNeedsAMarsMod() {
		assertTrue(BandEdges.canTransmit(TWENTY, 14_200_000, false));
		assertTrue(BandEdges.canTransmit(TWENTY, 14_350_000, false), "the band edge itself is in band");
		assertFalse(BandEdges.canTransmit(TWENTY, 14_400_000, false));
		assertTrue(BandEdges.canTransmit(TWENTY, 14_400_000, true));
		assertFalse(BandEdges.canTransmit(TWENTY, 14_500_000, true), "past the MARS range");
		assertTrue(BandEdges.canTransmit(null, 1, false), "no band, nothing to check");
	}

	@Test
	void outOfBandSplattersFurther() {
		assertEquals(1.0D, BandEdges.splatterReach(TWENTY, 14_200_000), 1e-9);
		assertEquals(BandEdges.OUT_OF_BAND_SPLATTER, BandEdges.splatterReach(TWENTY, 13_950_000), 1e-9);
		assertTrue(BandEdges.isOutOfBand(TWO, 155_000_000));
		assertFalse(BandEdges.isOutOfBand(TWO, 146_520_000));
	}

	@Test
	void snappingStaysOnTheBandGridAndInsideTheTuningRange() {
		// One step down from the bottom of 20m goes below the band, on the 1 kHz grid.
		assertEquals(13_999_000.0F, BandEdges.snap(14_000_000, 13_912_500, 14_437_500, 14_000_000, 1000, -1), 1.0F);
		// Far down stops at the first whole step inside the range (13.913, not 13.9125).
		assertEquals(13_913_000.0F, BandEdges.snap(14_000_000, 13_912_500, 14_437_500, 14_000_000, 1000, -1000), 1.0F);
		assertEquals(14_437_000.0F, BandEdges.snap(14_000_000, 13_912_500, 14_437_500, 14_000_000, 1000, 1000), 1.0F);
		// 2m on 20 kHz steps reaches the MURS channel 151.820 MHz.
		assertEquals(151_820_000.0F, BandEdges.snap(144_000_000, 143_000_000, 174_000_000, 151_800_000, 20_000, 1), 16.0F);
	}

	@Test
	void bandsKnowWhatTheyAre() {
		assertTrue(TWO.isVhf());
		assertFalse(LOW.isVhf(), "low band is on the HF antenna network");
		assertFalse(LOW.isAmateur());
		assertTrue(TWENTY.isAmateur());
		assertEquals("10m", LOW.conditionName(), "low band follows 10m's solar weather");
		assertEquals("Low Band", LOW.displayName());
	}
}
