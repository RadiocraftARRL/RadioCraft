package com.arrl.radiocraft.common.radio.voice;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The analog unit ID burst (1.12 playtest fixes 2) and the unit ID a receiver reads. */
class MdcBurstTest {

	private static final long FRAME_NANOS = 20_000_000L;

	private static short[] voice() {
		short[] pcm = new short[960];
		Arrays.fill(pcm, (short)1000);
		return pcm;
	}

	@Test
	void theBurstIsAboutAHundredAndFiftyMilliseconds() {
		short[] burst = MdcBurst.build(1205);
		assertEquals(MdcBurst.LENGTH, burst.length);
		double ms = burst.length / 48.0D;
		assertTrue(ms > 120.0D && ms < 200.0D, ms + " ms");
		int crossings = 0;
		for(int i = 1; i < burst.length; i++)
			if(burst[i - 1] < 0 != burst[i] < 0)
				crossings++;
		double hz = crossings / 2.0D / (burst.length / 48_000.0D);
		assertTrue(hz > 1150.0D && hz < 1850.0D, "between the two tones: " + hz + " Hz");
	}

	@Test
	void differentUnitsSoundDifferent() {
		assertFalse(Arrays.equals(MdcBurst.build(1205), MdcBurst.build(1206)));
		assertTrue(Arrays.equals(MdcBurst.build(1205), MdcBurst.build(1205)));
	}

	@Test
	void eachKeyUpStartsWithTheBurst() {
		MdcBurst.Sender sender = new MdcBurst.Sender();
		long now = 1_000_000_000L;
		short[] first = sender.frame(voice(), 1205, now);
		assertTrue(first[0] != 1000 || first[500] != 1000, "the first frame is the burst");
		int frames = 1;
		while(sender.sending()) {
			now += FRAME_NANOS;
			sender.frame(voice(), 1205, now);
			frames++;
		}
		assertEquals((MdcBurst.LENGTH + 959) / 960, frames, "the burst covers the first frames");
		short[] talk = voice();
		now += FRAME_NANOS;
		assertSame(talk, sender.frame(talk, 1205, now), "then the voice goes through untouched");
		now += MdcBurst.KEY_UP_GAP_NANOS + 1;
		assertNotSame(talk, sender.frame(talk, 1205, now), "a new key-up bursts again");
	}

	@Test
	void noUnitNoBurst() {
		MdcBurst.Sender sender = new MdcBurst.Sender();
		short[] talk = voice();
		assertSame(talk, sender.frame(talk, 0, 1_000_000_000L));
		assertFalse(sender.sending());
	}

	@Test
	void theUnitRidesOnTheMode() {
		DigitalVoice.Mode analog = new DigitalVoice.Mode(false, 0, 1205);
		assertEquals(1205, DigitalVoice.unitHeard(analog, DigitalVoice.Reception.ANALOG), "an MDC decoder reads it");
		DigitalVoice.Mode encrypted = new DigitalVoice.Mode(true, 100, 2001);
		assertEquals(2001, DigitalVoice.unitHeard(encrypted, DigitalVoice.Reception.SCRAMBLED), "shown without the key, as P25 does");
		assertEquals(2001, DigitalVoice.unitHeard(encrypted, DigitalVoice.Reception.CLEAR));
		assertEquals(0, DigitalVoice.unitHeard(encrypted, DigitalVoice.Reception.BUZZ), "an analog radio hears only a buzz");
		assertEquals(0, DigitalVoice.unitHeard(encrypted, DigitalVoice.Reception.DROPPED));
		assertEquals(0, DigitalVoice.unitHeard(DigitalVoice.Mode.ANALOG, DigitalVoice.Reception.ANALOG));
		assertEquals(new DigitalVoice.Mode(true, 5), new DigitalVoice.Mode(true, 5, 0), "no unit by default");
		assertEquals(77, DigitalVoice.Mode.DIGITAL_CLEAR.withUnit(77).unitId());
	}
}
