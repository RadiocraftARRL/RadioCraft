package com.arrl.radiocraft.common.radio.ft8;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FT8's slot clock, thresholds, dial frequencies and grid squares (computer batch 3B). */
class Ft8Test {

	@Test
	void slotsAreFifteenSecondsOfGameTime() {
		assertEquals(0L, Ft8.slot(0L));
		assertEquals(0L, Ft8.slot(299L));
		assertEquals(1L, Ft8.slot(300L));
		assertEquals(-1L, Ft8.slot(-1L), "before tick 0 is the slot before");
		assertEquals(0, Ft8.tickInSlot(600L));
		assertEquals(Ft8.DECODE_AT, Ft8.tickInSlot(Ft8.SLOT_TICKS + Ft8.DECODE_AT));
		assertTrue(Ft8.isEven(0L) && !Ft8.isEven(1L) && Ft8.isEven(2L) && !Ft8.isEven(-1L));
		assertEquals(600L, Ft8.slotStart(2L));
	}

	@Test
	void theTransmissionEndsBeforeTheDecode() {
		assertTrue(Ft8.TX_TICKS < Ft8.DECODE_AT && Ft8.DECODE_AT < Ft8.SLOT_TICKS);
		assertEquals(12.64D, Ft8.SYMBOLS * Ft8.SYMBOL_SECONDS, 1.0E-9);
		assertEquals(Math.ceil(12.64D * 20.0D), Ft8.TX_TICKS, 0.5D);
	}

	@Test
	void ticksToTheNextSlotOfAParity() {
		assertEquals(300L, Ft8.ticksToNext(0L, false), "from the start of an even slot, the next odd one");
		assertEquals(600L, Ft8.ticksToNext(0L, true), "the next even one is two slots on");
		assertEquals(1L, Ft8.ticksToNext(299L, false));
		assertEquals(301L, Ft8.ticksToNext(299L, true));
	}

	@Test
	void theClockReadsLikeWsjtx() {
		assertEquals("000000", Ft8.clock(0L));
		assertEquals("000015", Ft8.clock(1L));
		assertEquals("000100", Ft8.clock(4L));
		assertEquals("010000", Ft8.clock(240L));
		assertEquals("000000", Ft8.clock(86_400L / 15L), "wraps at 24 hours");
	}

	@Test
	void ft8DecodesTwelveDbBelowTheCopyThreshold() {
		assertTrue(Ft8.decodes(1.0D), "at the copy threshold");
		assertTrue(Ft8.decodes(0.1D), "10 dB under, where RTTY is long lost");
		assertTrue(Ft8.decodes(Ft8.DECODE_SHARE));
		assertFalse(Ft8.decodes(Ft8.DECODE_SHARE * 0.99D));
		assertFalse(Ft8.decodes(0.0D));
		assertEquals(Math.pow(10.0D, -1.2D), Ft8.DECODE_SHARE, 1.0E-12);
		assertTrue(Ft8.DECODE_SHARE < com_arrl_rttyFloor(), "FT8's floor is under RTTY's garble floor");
	}

	/** RadioBlockEntity.RTTY_GARBLE_FLOOR (the radio class cannot load without the game). */
	private static double com_arrl_rttyFloor() {
		return 0.5D;
	}

	@Test
	void reportsAreDbIn2500Hz() {
		assertEquals(Ft8.COPY_THRESHOLD_SNR, Ft8.snr(1.0D));
		assertEquals(-20, Ft8.snr(Ft8.DECODE_SHARE), "the weakest decode shows -20 dB");
		assertEquals(2, Ft8.snr(10.0D));
		assertEquals(Ft8.MAX_SNR, Ft8.snr(1.0E9D));
		assertEquals(Ft8.MIN_SNR, Ft8.snr(0.0D));
	}

	@Test
	void dialFrequencies() {
		assertEquals(14_074_000.0F, Ft8.dialIn(14_000_000.0D, 14_350_000.0D));
		assertEquals(3_573_000.0F, Ft8.dialIn(3_412_500.0D, 4_125_000.0D));
		assertEquals(0.0F, Ft8.dialIn(30_000_000.0D, 50_000_000.0D), "no FT8 on the commercial low band");
		assertTrue(Ft8.isDial(7_074_000.0D) && Ft8.isDial(7_074_400.0D) && !Ft8.isDial(7_076_000.0D));
		assertEquals(Ft8.MIN_OFFSET_HZ, Ft8.clampOffset(10));
		assertEquals(Ft8.MAX_OFFSET_HZ, Ft8.clampOffset(9000));
	}

	@Test
	void gridSquaresFromBlocks() {
		assertEquals("JJ00", Ft8.grid(0, -1), "just north-east of the centre");
		assertEquals("JJ00", Ft8.grid(255, -256));
		assertEquals("JJ10", Ft8.grid(256, -1), "a square east");
		assertEquals("JJ01", Ft8.grid(0, -257), "a square north");
		assertEquals("II99", Ft8.grid(-1, 0), "south-west of the centre");
		assertEquals("AA00", Ft8.grid(-1_000_000, 1_000_000), "stays on the edge");
		assertEquals("RR99", Ft8.grid(1_000_000, -1_000_000));
		assertTrue(Ft8.isGrid("FN31") && !Ft8.isGrid("SS00") && !Ft8.isGrid("FN3") && !Ft8.isGrid("-12"));
		assertEquals(256, Ft8.gridDistance("JJ00", "JJ10"));
		assertEquals(-1, Ft8.gridDistance("JJ00", "XX"));
	}

	/** Computer batch 3C: a grid square's centre is in that square, so the Awards place a station by it. */
	@Test
	void gridCentresAreInTheirSquares() {
		for(String grid : new String[] { "JJ00", "AA00", "RR99", "JK01", "IJ99" }) {
			int[] centre = Ft8.gridCentre(grid);
			assertEquals(grid, Ft8.grid(centre[0], centre[1]), grid + " centre " + centre[0] + "," + centre[1]);
		}
		assertEquals(null, Ft8.gridCentre("RR73"));
	}
}
