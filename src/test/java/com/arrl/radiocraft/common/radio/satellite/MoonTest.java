package com.arrl.radiocraft.common.radio.satellite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoonTest {

	@Test
	void overheadAtMidnightAndDownAtNoon() {
		assertEquals(90.0D, Moon.look(0.5D).elevation(), 1e-6);
		assertEquals(-90.0D, Moon.look(0.0D).elevation(), 1e-6);
		assertTrue(Moon.usable(0.5D));
		assertFalse(Moon.usable(0.0D));
	}

	@Test
	void risesInTheEastAndSetsInTheWest() {
		// Minecraft's sun rises at a celestial angle of 0.75 (dawn), so the Moon rises at 0.25 (dusk).
		SatelliteOrbit.Look rising = Moon.look(0.3D), setting = Moon.look(0.7D);
		assertTrue(rising.elevation() > 0.0D && setting.elevation() > 0.0D);
		assertEquals(90.0D, rising.azimuth(), 1e-9, "east in the evening");
		assertEquals(270.0D, setting.azimuth(), 1e-9, "west before dawn");
		assertFalse(Moon.usable(0.255D), "too low just after moonrise");
	}

	@Test
	void theEchoIsWeakAndFades() {
		double low = 1.0D, high = 0.0D;
		for(int t = 0; t < 20_000; t += 7) {
			double e = Moon.echo(t, 12345L);
			assertTrue(e >= Moon.FLOOR - 1e-9 && e <= Moon.PEAK + 1e-9, "out of range at " + t + ": " + e);
			low = Math.min(low, e);
			high = Math.max(high, e);
		}
		assertTrue(high - low > (Moon.PEAK - Moon.FLOOR) * 0.6D, "it hardly fades: " + low + ".." + high);
		assertTrue(Moon.PEAK < 0.3D, "it is not weak");
	}
}
