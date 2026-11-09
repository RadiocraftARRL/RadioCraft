package com.arrl.radiocraft.common.radio.voice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A carrier with nobody talking, and SSB that is only there while someone speaks (2026-11-09). */
class DeadAirTest {

	@Test
	void deadAirIsNotDigitalSilence() {
		boolean crackled = false;
		for(long seed = 1; seed < 60; seed++) {
			short[] frame = RepeaterAudio.deadAir(seed);
			int peak = 0;
			for(short s : frame)
				peak = Math.max(peak, Math.abs((int)s));
			assertTrue(peak > 0, "a carrier carries some hiss");
			assertTrue(peak < 32768 * 0.4, "but faint");
			crackled |= peak > 32768 * 0.06;
		}
		assertTrue(crackled, "now and then it crackles");
	}

	@Test
	void deadAirOnSsbSendsNothingButItsKerchunks() {
		StationProgram fm = new StationProgram(StationProgram.Kind.DEAD_AIR, 42L, "X0X", List::of, false);
		StationProgram ssb = new StationProgram(StationProgram.Kind.DEAD_AIR, 42L, "X0X", List::of, true);
		assertEquals(3, fm.frames(10L, 3).size(), "FM dead air: a carrier, every frame");
		assertTrue(fm.onAir());
		assertTrue(ssb.frames(10L, 3).isEmpty(), "SSB with nobody talking sends nothing");
		assertFalse(ssb.onAir());
	}

	@Test
	void ssbEnvelopeFollowsTheVoice() {
		assertEquals(0.0D, ReceiverMix.ssbEnvelope(new short[960]), "a pause: no signal");
		short[] loud = new short[960];
		loud[100] = 16000;
		assertEquals(1.0D, ReceiverMix.ssbEnvelope(loud));
		short[] soft = new short[960];
		soft[100] = 1600;
		double half = ReceiverMix.ssbEnvelope(soft);
		assertTrue(half > ReceiverMix.SSB_SILENT && half < 0.5D, "a soft syllable: some signal, " + half);
	}
}
