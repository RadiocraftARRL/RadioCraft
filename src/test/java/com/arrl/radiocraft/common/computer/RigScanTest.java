package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rig control scanner's stepping (computer batch 3B): dwell, stop on a signal, carrier delay, hold, ranges. */
class RigScanTest {

	private static final float[] CHANNELS = { 146_400_000.0F, 146_420_000.0F, 146_440_000.0F, 146_460_000.0F };

	@Test
	void stepsThroughTheChannelsAndWrapsAround() {
		RigScan scan = new RigScan();
		scan.start(RigScan.Mode.MEMORY, CHANNELS, 146_400_000.0F, 100L);
		assertEquals(146_420_000.0F, scan.tick(100L, false), "moves on at once from where the radio was");
		for(long t = 101L; t < 100L + RigScan.DWELL_TICKS; t++)
			assertTrue(Float.isNaN(scan.tick(t, false)), "dwells");
		assertEquals(146_440_000.0F, scan.tick(100L + RigScan.DWELL_TICKS, false));
		assertEquals(146_460_000.0F, scan.tick(100L + 2 * RigScan.DWELL_TICKS, false));
		assertEquals(146_400_000.0F, scan.tick(100L + 3 * RigScan.DWELL_TICKS, false), "wraps to the first");
		assertEquals(4, scan.size());
	}

	@Test
	void stopsOnASignalAndGoesOnAfterTheCarrierDelay() {
		RigScan scan = new RigScan();
		scan.start(RigScan.Mode.RANGE, CHANNELS, 146_400_000.0F, 0L);
		scan.tick(0L, false); // 146.420
		assertTrue(Float.isNaN(scan.tick(2L, true)), "a signal: it stops");
		assertTrue(scan.isStopped());
		assertEquals(146_420_000.0F, scan.current());
		for(long t = 3L; t < 50L; t++)
			assertTrue(Float.isNaN(scan.tick(t, t < 40L)), "stays while the signal is there and for a while after");
		assertTrue(Float.isNaN(scan.tick(39L + RigScan.RESUME_TICKS - 1, false)));
		assertEquals(146_440_000.0F, scan.tick(39L + RigScan.RESUME_TICKS, false), "moves on once the carrier delay is over");
		assertFalse(scan.isStopped());
	}

	@Test
	void holdKeepsItWhereItIs() {
		RigScan scan = new RigScan();
		scan.start(RigScan.Mode.RANGE, CHANNELS, 146_400_000.0F, 0L);
		scan.tick(0L, false);
		scan.toggleHold(1L);
		assertTrue(scan.isHeld());
		for(long t = 1L; t < 200L; t++)
			assertTrue(Float.isNaN(scan.tick(t, false)));
		scan.toggleHold(200L);
		assertEquals(146_440_000.0F, scan.tick(200L, false), "released, it goes on at once");
		scan.stop();
		assertFalse(scan.isScanning());
		assertTrue(Float.isNaN(scan.tick(300L, false)));
	}

	@Test
	void anEmptyListDoesNotScan() {
		RigScan scan = new RigScan();
		scan.start(RigScan.Mode.MEMORY, new float[0], 0.0F, 0L);
		assertFalse(scan.isScanning());
	}

	@Test
	void rangesStepAtTheTuningStep() {
		assertArrayEquals(new float[] { 14_000_000.0F, 14_001_000.0F, 14_002_000.0F }, RigScan.range(14_000_000.0D, 14_002_000.0D, 1000));
		assertEquals(0, RigScan.range(14_002_000.0D, 14_000_000.0D, 1000).length, "backwards is nothing");
		assertEquals(0, RigScan.range(1.0D, 2.0D, 0).length);
		assertEquals(RigScan.MAX_CHANNELS, RigScan.range(0.0D, 1.0E9D, 1).length, "capped");
	}
}
