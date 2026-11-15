package com.arrl.radiocraft.common.radio.satellite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Eclipses on a flat battery (2026-11-12): only at night, rare, fixed by the satellite and the slot, a low battery after. */
class SatelliteEclipseTest {

	private static final long NIGHT = 18_000L, DAY = 6_000L;

	@Test
	void nightOnly() {
		assertTrue(SatelliteEclipse.night(NIGHT));
		assertTrue(SatelliteEclipse.night(NIGHT + 24_000L * 5));
		assertFalse(SatelliteEclipse.night(DAY));
		assertFalse(SatelliteEclipse.night(23_500L));
		long seed = SatelliteEclipse.seed("RO-1", 0L);
		for(long slot = 0; slot < 2_000; slot++)
			assertFalse(SatelliteEclipse.eclipsed(seed, slot * SatelliteEclipse.SLOT_TICKS, DAY), "eclipsed by day");
	}

	@Test
	void rareAndDeterministic() {
		long seed = SatelliteEclipse.seed("RO-7", 1_700_000_000_000L);
		int hits = 0, slots = 20_000;
		for(long slot = 0; slot < slots; slot++)
			if(SatelliteEclipse.unlucky(seed, slot))
				hits++;
		double share = hits / (double)slots;
		assertEquals(SatelliteEclipse.CHANCE, share, 0.01D, "share of night slots eclipsed");
		for(long slot = 0; slot < 200; slot++)
			assertEquals(SatelliteEclipse.unlucky(seed, slot), SatelliteEclipse.unlucky(seed, slot), "the same each time");
		assertNotEquals(SatelliteEclipse.seed("RO-7", 0L), SatelliteEclipse.seed("RO-8", 0L));
	}

	@Test
	void lowBatteryForAWhileAfter() {
		long seed = SatelliteEclipse.seed("RO-2", 42L);
		long slot = 0;
		while(!SatelliteEclipse.unlucky(seed, slot) || SatelliteEclipse.unlucky(seed, slot + 1))
			slot++;
		long during = slot * SatelliteEclipse.SLOT_TICKS + 10;
		assertTrue(SatelliteEclipse.eclipsed(seed, during, NIGHT));
		assertEquals(0, SatelliteEclipse.recovering(seed, during, NIGHT), "not recovering while eclipsed");
		long after = during + SatelliteEclipse.SLOT_TICKS;
		assertEquals(1, SatelliteEclipse.recovering(seed, after, NIGHT + SatelliteEclipse.SLOT_TICKS));
		boolean later = false;
		for(int back = 1; back <= SatelliteEclipse.RECOVERY_SLOTS; back++)
			later |= SatelliteEclipse.unlucky(seed, slot + SatelliteEclipse.RECOVERY_SLOTS + 1 - back);
		if(!later) // Recovered once the slots have passed (unless another eclipse came).
			assertEquals(0, SatelliteEclipse.recovering(seed, during + (SatelliteEclipse.RECOVERY_SLOTS + 1) * SatelliteEclipse.SLOT_TICKS, NIGHT));
	}
}
