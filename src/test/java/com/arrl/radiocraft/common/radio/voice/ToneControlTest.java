package com.arrl.radiocraft.common.radio.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The FM broadcast receiver's BASS and TREBLE knobs (2026-11-13). */
class ToneControlTest {

	/** The level (RMS) of a sine at {@code hz} after {@code tone}, past its first frame. */
	private static double level(ToneControl tone, double hz) {
		double sum = 0.0D;
		int n = 0;
		for(int frame = 0; frame < 10; frame++) {
			short[] pcm = new short[960];
			for(int i = 0; i < pcm.length; i++)
				pcm[i] = (short)(8000 * Math.sin(2 * Math.PI * hz * (frame * 960 + i) / 48000.0D));
			short[] out = tone.process(pcm);
			if(frame < 2)
				continue;
			for(short s : out) {
				sum += (double)s * s;
				n++;
			}
		}
		return Math.sqrt(sum / n);
	}

	@Test
	void flatLeavesTheSoundAlone() {
		ToneControl tone = new ToneControl(48000);
		short[] pcm = {1, 2, 3, -4};
		assertSame(pcm, tone.process(pcm));
	}

	@Test
	void bassBoostsLowsAndLeavesHighs() {
		ToneControl flat = new ToneControl(48000), boosted = new ToneControl(48000);
		boosted.set(1.0D, 0.0D);
		assertTrue(level(boosted, 80) > level(flat, 80) * 2.0D, "about +10 dB at 80 Hz");
		assertEquals(level(flat, 6000), level(boosted, 6000), level(flat, 6000) * 0.1D, "6 kHz unchanged");
	}

	@Test
	void trebleCutDullsHighs() {
		ToneControl flat = new ToneControl(48000), cut = new ToneControl(48000);
		cut.set(0.0D, -1.0D);
		assertTrue(level(cut, 8000) < level(flat, 8000) * 0.5D);
		assertEquals(level(flat, 100), level(cut, 100), level(flat, 100) * 0.1D);
	}
}
