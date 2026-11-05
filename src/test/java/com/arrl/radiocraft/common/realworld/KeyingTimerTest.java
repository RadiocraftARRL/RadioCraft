package com.arrl.radiocraft.common.realworld;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** VOX/COS gating and PTT keying: hang time and the repeater-style time-out timer. */
class KeyingTimerTest {

	@Test
	void keysAtOnceAndHangsAfter() {
		KeyingTimer timer = new KeyingTimer(300, 0);
		assertFalse(timer.update(false, 0));
		assertTrue(timer.update(true, 100));
		assertEquals(KeyingTimer.State.KEYED, timer.state());
		assertTrue(timer.update(false, 200)); // In the hang time.
		assertEquals(KeyingTimer.State.HANG, timer.state());
		assertTrue(timer.update(false, 400)); // 300 ms after the last activity: still hanging.
		assertFalse(timer.update(false, 401)); // Then it drops.
		assertEquals(KeyingTimer.State.IDLE, timer.state());
	}

	@Test
	void activityInTheHangTimeKeepsItKeyed() {
		KeyingTimer timer = new KeyingTimer(300, 0);
		timer.update(true, 0);
		timer.update(false, 250);
		assertTrue(timer.update(true, 290));
		assertTrue(timer.update(false, 580));
		assertFalse(timer.update(false, 600));
	}

	@Test
	void noHangDropsWithTheActivity() {
		KeyingTimer timer = new KeyingTimer(0, 0);
		assertTrue(timer.update(true, 0));
		assertFalse(timer.update(false, 1));
	}

	@Test
	void timesOutAndWaitsForQuietBeforeKeyingAgain() {
		KeyingTimer timer = new KeyingTimer(500, 180_000); // A repeater's three minutes.
		for(long t = 0; t < 180_000; t += 20)
			assertTrue(timer.update(true, t), "dropped early at " + t);
		assertFalse(timer.update(true, 180_000)); // Time out.
		assertTrue(timer.isTimedOut());
		assertFalse(timer.update(true, 200_000)); // Still talking: stays off.
		assertFalse(timer.update(false, 200_500)); // Quiet, but not long enough to reset.
		assertFalse(timer.update(true, 200_600)); // Talking again restarts the wait.
		assertFalse(timer.update(false, 201_000));
		assertTrue(timer.isTimedOut());
		assertFalse(timer.update(false, 201_600)); // A second of quiet re-arms it...
		assertEquals(KeyingTimer.State.IDLE, timer.state());
		assertTrue(timer.update(true, 201_700)); // ...and the next transmission keys with a fresh timer.
		assertEquals(0L, timer.keyedFor(201_700));
	}

	@Test
	void theHangTimeCountsTowardsTheTimeout() {
		KeyingTimer timer = new KeyingTimer(10_000, 5_000);
		timer.update(true, 0);
		assertTrue(timer.update(false, 4_999));
		assertFalse(timer.update(false, 5_000));
		assertTrue(timer.isTimedOut());
	}

	@Test
	void resetDropsWithoutTimingOut() {
		KeyingTimer timer = new KeyingTimer(1_000, 0);
		timer.update(true, 0);
		timer.reset();
		assertFalse(timer.isKeyed());
		assertFalse(timer.isTimedOut());
		assertTrue(timer.update(true, 10));
	}
}
