package com.arrl.radiocraft.common.radio.wxalert;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The receiver's battery drain, its lamp and its one redstone pulse. */
class ReceiverSignalsTest {

	private static final int CAPACITY = 60000; // small_battery_capacity's default.

	@Test
	void aFullBatteryLastsThreeWeeks() {
		double perTick = ReceiverSignals.drainPerTick(CAPACITY, ReceiverSignals.BATTERY_DAYS);
		assertTrue(perTick < 1.0);
		long ticks = (long)ReceiverSignals.BATTERY_DAYS * ReceiverSignals.TICKS_PER_DAY;
		long drained = 0;
		for(long t = 0; t < ticks; t++)
			drained += ReceiverSignals.drainAt(perTick, t);
		assertEquals(CAPACITY, drained, 1);
		assertEquals(21.0, ReceiverSignals.daysLeft(CAPACITY, perTick), 1e-6);
		assertEquals(10.5, ReceiverSignals.daysLeft(CAPACITY / 2, perTick), 1e-6);
	}

	@Test
	void drainIsAtMostOneFeATick() {
		double perTick = ReceiverSignals.drainPerTick(CAPACITY, ReceiverSignals.BATTERY_DAYS);
		for(long t = 0; t < 1000; t++) {
			int take = ReceiverSignals.drainAt(perTick, t);
			assertTrue(take == 0 || take == 1);
		}
	}

	@Test
	void lowBelowATenth() {
		assertTrue(ReceiverSignals.isLow(5999, CAPACITY));
		assertFalse(ReceiverSignals.isLow(6000, CAPACITY));
		assertFalse(ReceiverSignals.isLow(0, CAPACITY)); // Flat, not low.
	}

	@Test
	void lowBatteryBlinksEveryTenSeconds() {
		int lit = 0;
		for(long t = 0; t < 2000; t++)
			if(ReceiverSignals.lampLit(false, true, false, t))
				lit++;
		assertEquals(10 * ReceiverSignals.LOW_BLINK_TICKS, lit); // Ten blinks in 100 s.
		assertTrue(ReceiverSignals.lampLit(false, true, false, 400));
		assertFalse(ReceiverSignals.lampLit(false, true, false, 400 + ReceiverSignals.LOW_BLINK_TICKS));
		assertFalse(ReceiverSignals.lampLit(false, false, false, 400));
		assertFalse(ReceiverSignals.lampLit(true, true, true, 0)); // Flat: dark.
	}

	@Test
	void anAlertFlashes() {
		assertTrue(ReceiverSignals.lampLit(true, false, false, 0));
		assertFalse(ReceiverSignals.lampLit(true, false, false, ReceiverSignals.ALERT_FLASH_TICKS));
		assertTrue(ReceiverSignals.lampLit(true, false, false, 2L * ReceiverSignals.ALERT_FLASH_TICKS));
	}

	@Test
	void thePulseIsOneShortPulse() {
		ReceiverSignals.Pulse pulse = new ReceiverSignals.Pulse();
		assertFalse(pulse.tick());
		pulse.trigger();
		pulse.trigger(); // A second batch while high: still one pulse.
		int high = 0;
		for(int t = 0; t < 100; t++)
			if(pulse.tick())
				high++;
		assertEquals(ReceiverSignals.PULSE_TICKS, high);
		assertFalse(pulse.isHigh());
	}
}
