package com.arrl.radiocraft.common.radio.distress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The distress beacon's battery (2026-11-09): a day of transmitting, a burst every 50 s, then spent. */
class BeaconBatteryTest {

	@Test
	void countsDownInSteps() {
		int remaining = BeaconBattery.LIFE_TICKS;
		int bursts = 0, steps = 0;
		while(!BeaconBattery.spent(remaining)) {
			int after = BeaconBattery.drain(remaining, BeaconBattery.STEP_TICKS);
			if(BeaconBattery.burstDue(remaining, after))
				bursts++;
			remaining = after;
			steps++;
		}
		assertEquals(BeaconBattery.LIFE_TICKS / BeaconBattery.STEP_TICKS, steps, "one day of ticks");
		// One at switch-on (sent by the item) plus one every 1000 ticks after, none on the last step (that is SPENT).
		assertEquals(BeaconBattery.LIFE_TICKS / BeaconBattery.BURST_TICKS - 1, bursts);
	}

	@Test
	void firstRepeatFiftySecondsAfterSwitchOn() {
		int remaining = BeaconBattery.LIFE_TICKS, ticks = 0;
		while(true) {
			int after = BeaconBattery.drain(remaining, BeaconBattery.STEP_TICKS);
			ticks += BeaconBattery.STEP_TICKS;
			if(BeaconBattery.burstDue(remaining, after))
				break;
			remaining = after;
		}
		assertEquals(BeaconBattery.BURST_TICKS, ticks);
	}

	@Test
	void neverBelowZero() {
		assertEquals(0, BeaconBattery.drain(10, 20));
		assertEquals(10, BeaconBattery.drain(10, -5));
		assertTrue(BeaconBattery.spent(0));
		assertFalse(BeaconBattery.spent(1));
		assertFalse(BeaconBattery.burstDue(20, 0), "the last step is the spent alert, not a burst");
		assertFalse(BeaconBattery.burstDue(500, 500));
	}

	@Test
	void remainingTimeAndShare() {
		assertEquals("0:20", BeaconBattery.remainingText(BeaconBattery.LIFE_TICKS)); // 20 real minutes.
		assertEquals("0:01", BeaconBattery.remainingText(1));
		assertEquals("0:00", BeaconBattery.remainingText(0));
		assertEquals(1.0F, BeaconBattery.share(BeaconBattery.LIFE_TICKS));
		assertEquals(0.5F, BeaconBattery.share(BeaconBattery.LIFE_TICKS / 2));
	}
}
