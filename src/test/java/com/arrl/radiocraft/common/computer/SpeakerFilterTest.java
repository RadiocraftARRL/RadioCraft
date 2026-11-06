package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The radio computer's small speakers (1.12 music player fixes): no bass, no treble, a little bump, a soft clip. */
class SpeakerFilterTest {

	private static final double RATE = 44100.0D;

	@Test
	void passesTheMidrangeAndCutsBassAndTreble() {
		double mid = gain(1000.0D), bass = gain(60.0D), lowMid = gain(150.0D), treble = gain(12000.0D);
		assertTrue(mid > 0.9D && mid < 1.6D, "1 kHz " + mid);
		assertTrue(bass < 0.08D, "60 Hz " + bass);
		assertTrue(lowMid < 0.45D, "150 Hz " + lowMid);
		assertTrue(treble < 0.15D, "12 kHz " + treble);
		assertTrue(gain(1500.0D) > gain(400.0D), "the bump");
	}

	@Test
	void theClipIsSoftAndBounded() {
		assertEquals(0.1D, SpeakerFilter.softClip(0.1D), 0.005D);
		assertTrue(SpeakerFilter.softClip(0.8D) < 0.8D);
		assertTrue(SpeakerFilter.softClip(10.0D) <= 1.0D);
		assertEquals(-SpeakerFilter.softClip(0.5D), SpeakerFilter.softClip(-0.5D), 1.0E-9D);
		SpeakerFilter filter = new SpeakerFilter(RATE);
		for(int i = 0; i < 44100; i++) // A full-scale square wave never comes out past full scale.
			assertTrue(Math.abs(filter.process((i / 50) % 2 == 0 ? 1.0F : -1.0F)) <= 1.0F);
	}

	/** Peak out over peak in for a quiet sine at {@code hz}, once the filters have settled. */
	private static double gain(double hz) {
		SpeakerFilter filter = new SpeakerFilter(RATE);
		double amp = 0.05D, peak = 0.0D;
		for(int i = 0; i < (int)RATE; i++) {
			float out = filter.process((float)(amp * Math.sin(2.0D * Math.PI * hz * i / RATE)));
			if(i > RATE / 2)
				peak = Math.max(peak, Math.abs(out));
		}
		return peak / amp;
	}
}
