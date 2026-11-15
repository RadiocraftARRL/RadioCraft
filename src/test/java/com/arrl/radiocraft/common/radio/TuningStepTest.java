package com.arrl.radiocraft.common.radio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A VHF radio's STEP (2026-11-14): the maintainer's satellite on 164.4875 could not be tuned on 20 kHz steps. */
class TuningStepTest {

	private static final double BOTTOM = 144_000_000.0D, LOW = 136_000_000.0D, HIGH = 174_000_000.0D;

	private static float tune(double hz, int step, int clicks) {
		return TuningStep.tune(BOTTOM, LOW, HIGH, hz, step, clicks);
	}

	@Test
	void twelveAndAHalfReachesTheSatellite() {
		assertEquals(164_487_500.0D, tune(164_480_000.0D, 12_500, 1), 20.0D);
		assertEquals(164_475_000.0D, tune(164_480_000.0D, 12_500, -1), 20.0D);
		assertEquals(164_487_500.0D, tune(164_475_000.0D, 12_500, 1), 20.0D, "on the grid it moves a whole step");
	}

	@Test
	void offTheGridTheFirstClickLandsTheWayItIsTurned() {
		assertEquals(164_500_000.0D, tune(164_487_500.0D, 20_000, 1), 20.0D);
		assertEquals(164_480_000.0D, tune(164_487_500.0D, 20_000, -1), 20.0D, "down does not skip 164.480");
		assertEquals(164_520_000.0D, tune(164_487_500.0D, 20_000, 2), 20.0D);
		assertEquals(164_460_000.0D, tune(164_487_500.0D, 20_000, -2), 20.0D);
	}

	@Test
	void aFloatFrequencyAFewHertzOffStillCountsAsOnTheGrid() {
		float hz = 164_487_500.0F; // Stored as a float: 4 Hz out.
		assertEquals(164_500_000.0D, tune(hz, 12_500, 1), 20.0D);
		assertEquals(164_475_000.0D, tune(hz, 12_500, -1), 20.0D);
	}

	@Test
	void itStaysInsideTheDial() {
		assertEquals(HIGH, tune(173_990_000.0D, 25_000, 5), 1.0D);
		assertEquals(LOW, tune(136_005_000.0D, 5_000, -10), 1.0D);
	}

	@Test
	void stepsCycleAndAreSanitised() {
		assertEquals(25_000, TuningStep.next(20_000));
		assertEquals(5_000, TuningStep.next(25_000));
		assertEquals(25_000, TuningStep.previous(5_000));
		assertEquals(10_000, TuningStep.previous(12_500));
		assertEquals(12_500, TuningStep.sanitize(12_500));
		assertEquals(0, TuningStep.sanitize(7_000));
		assertEquals(20_000, TuningStep.effective(0, 20_000));
		assertEquals(6_250, TuningStep.effective(6_250, 20_000));
	}

	@Test
	void labelsAndFrequencies() {
		assertEquals("12.5", TuningStep.label(12_500));
		assertEquals("6.25", TuningStep.label(6_250));
		assertEquals("20", TuningStep.label(20_000));
		assertEquals("164.4875", TuningStep.mhz(164_487_500.0F));
		assertEquals("164.480", TuningStep.mhz(164_480_000.0F));
		assertEquals("146.520", TuningStep.mhz(146_520_000.0F));
	}

	@org.junit.jupiter.api.Test
	void aFrequencyFindsAStepItSitsOn() {
		org.junit.jupiter.api.Assertions.assertEquals(12_500, TuningStep.fitting(164_487_500.0D));
		org.junit.jupiter.api.Assertions.assertEquals(25_000, TuningStep.fitting(146_500_000.0D));
		org.junit.jupiter.api.Assertions.assertEquals(6_250, TuningStep.fitting(145_506_250.0D));
		org.junit.jupiter.api.Assertions.assertEquals(0, TuningStep.fitting(145_501_000.0D));
	}
}
