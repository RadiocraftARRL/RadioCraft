package com.arrl.radiocraft.common.radio.satellite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Transit delays through a satellite (2026-11-12). */
class SatelliteDelayTest {

	@Test
	void voiceIsAFewHundredMillisecondsAndHalfThroughAGeostationaryOne() {
		int leo = SatelliteDelay.voiceMillis(false), geo = SatelliteDelay.voiceMillis(true);
		assertTrue(leo >= 100 && leo <= 500, "voice should be a usable few hundred ms: " + leo);
		assertEquals(leo / 2, geo);
		assertEquals(1_300L, SatelliteDelay.voiceDue(1_000L, false));
		assertEquals(1_150L, SatelliteDelay.voiceDue(1_000L, true));
		assertEquals(15, SatelliteDelay.voiceFrames(false));
		assertEquals(8, SatelliteDelay.voiceFrames(true), "rounded up to whole 20 ms frames");
		assertEquals(6, SatelliteDelay.ticks(SatelliteDelay.Kind.VOICE, false));
		assertEquals(3, SatelliteDelay.ticks(SatelliteDelay.Kind.VOICE, true));
	}

	@Test
	void mailboxIsSlowerThanDigipeatIsSlowerThanVoice() {
		for(boolean geo : new boolean[] { false, true }) {
			int voice = SatelliteDelay.ticks(SatelliteDelay.Kind.VOICE, geo);
			int digi = SatelliteDelay.ticks(SatelliteDelay.Kind.DIGIPEAT, geo);
			int mail = SatelliteDelay.ticks(SatelliteDelay.Kind.MAILBOX, geo);
			assertTrue(voice < digi && digi < mail, voice + " < " + digi + " < " + mail + " (geo " + geo + ")");
		}
	}

	@Test
	void geostationaryHalvesDataButNeverBelowTheSendersDeafTime() {
		assertEquals(SatelliteDelay.DIGIPEAT_TICKS / 2, SatelliteDelay.ticks(SatelliteDelay.Kind.DIGIPEAT, true));
		assertEquals(SatelliteDelay.MAILBOX_TICKS / 2, SatelliteDelay.ticks(SatelliteDelay.Kind.MAILBOX, true));
		for(SatelliteDelay.Kind kind : new SatelliteDelay.Kind[] { SatelliteDelay.Kind.DIGIPEAT, SatelliteDelay.Kind.MAILBOX })
			for(boolean geo : new boolean[] { false, true })
				assertTrue(SatelliteDelay.ticks(kind, geo) >= SatelliteDelay.MIN_REPLY_TICKS, kind + " geo " + geo);
	}

	@Test
	void distressIsNeverHeld() {
		assertEquals(0, SatelliteDelay.ticks(SatelliteDelay.Kind.DISTRESS, false));
		assertEquals(0, SatelliteDelay.ticks(SatelliteDelay.Kind.DISTRESS, true));
	}
}
