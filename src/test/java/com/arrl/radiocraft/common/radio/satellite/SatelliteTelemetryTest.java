package com.arrl.radiocraft.common.radio.satellite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Telemetry values and frames (2026-11-12). */
class SatelliteTelemetryTest {

	private static final long SEED = SatelliteEclipse.seed("RO-1", 0L);

	@Test
	void sunlightChargesAndWarmsShadowDrainsAndCools() {
		SatelliteTelemetry noon = SatelliteTelemetry.at(SEED, 1, 1_000L, 6_000L, "REPEATER", 80, 0);
		SatelliteTelemetry lateNight = SatelliteTelemetry.at(SEED, 2, 1_000L, 22_500L, "REPEATER", 80, 0);
		assertTrue(noon.sunlit() && !lateNight.sunlit());
		assertTrue(noon.volts() > lateNight.volts(), noon + " vs " + lateNight);
		assertTrue(noon.amps() > 0.0D && lateNight.amps() < 0.0D, "charging by day, discharging at night");
		assertTrue(noon.tempC() > 15 && lateNight.tempC() < -5, noon.tempC() + " / " + lateNight.tempC());
		assertFalse(noon.low() || lateNight.low());
		assertEquals("REPEATER", lateNight.mode());
		for(long day = 0; day < 24_000; day += 250) { // Plausible all round the day.
			SatelliteTelemetry t = SatelliteTelemetry.at(SEED, 0, day * 7, day, "APRS", 10, 0);
			assertTrue(t.volts() > SatelliteTelemetry.LOW_VOLTS && t.volts() < 14.5D, "volts " + t.volts() + " at " + day);
			assertTrue(t.tempC() > -30 && t.tempC() < 40, "temp " + t.tempC() + " at " + day);
		}
	}

	@Test
	void lowAfterAnEclipseAndSafeMode() {
		SatelliteTelemetry low = SatelliteTelemetry.at(SEED, 3, 5_000L, 20_000L, "MAILBOX", 50, 1);
		assertTrue(low.low());
		assertTrue(low.volts() < SatelliteTelemetry.LOW_VOLTS);
		assertEquals(SatelliteTelemetry.SAFE, low.mode());
		SatelliteTelemetry better = SatelliteTelemetry.at(SEED, 3, 5_000L, 20_000L, "MAILBOX", 50, 3);
		assertTrue(better.volts() > low.volts(), "recovering");
	}

	@Test
	void framesRoundTripAndFitAPacket() {
		SatelliteTelemetry t = SatelliteTelemetry.at(SEED, 1_042, 777L, 9_000L, "MAILBOX", 3_650, 0);
		String text = t.encode();
		assertTrue(text.length() <= 100, text);
		assertTrue(text.startsWith("SEQ=042 "), text);
		assertEquals(t, SatelliteTelemetry.decode(text));
		assertNull(SatelliteTelemetry.decode("HELLO WORLD"));
		assertNull(SatelliteTelemetry.decode(null));
		assertNull(SatelliteTelemetry.decode("SEQ=x BAT=1V MODE=A"));
	}

	@Test
	void eclipsedFrameIsPowerSaving() {
		SatelliteTelemetry t = SatelliteTelemetry.eclipsed(SEED, 7, 1_234L, 40);
		assertTrue(t.powerSaving() && t.low() && !t.sunlit());
		assertEquals(SatelliteTelemetry.SAFE, t.mode());
		assertTrue(t.volts() < SatelliteTelemetry.LOW_VOLTS && t.amps() < 0.0D);
		String text = t.encode();
		assertTrue(text.endsWith("PWR=SAVING") && text.length() <= 100, text);
		assertEquals(t, SatelliteTelemetry.decode(text));
		assertFalse(SatelliteTelemetry.decode(SatelliteTelemetry.at(SEED, 1, 0L, 6_000L, "APRS", 5, 0).encode()).powerSaving());
	}
}
