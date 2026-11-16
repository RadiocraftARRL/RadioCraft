package com.arrl.radiocraft.common.radio.voice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagerAudioTest {

	@Test
	void lastsAsLongAsItsBitsAt2400Baud() {
		List<short[]> burst = PagerAudio.burst(100, 7L);
		assertEquals(PagerAudio.frameCount(100), burst.size());
		double seconds = (PagerAudio.PREAMBLE_BITS + 800) / PagerAudio.BAUD;
		assertEquals(seconds / 0.02D, burst.size(), 1.0D);
	}

	@Test
	void isASquareishBuzzNotTones() {
		short[] frame = PagerAudio.burst(100, 7L).get(20); // In the data, past the preamble.
		int flips = 0, loud = 0;
		for(int i = 1; i < frame.length; i++) {
			if((frame[i] >= 0) != (frame[i - 1] >= 0))
				flips++;
			if(Math.abs(frame[i]) > 0.25D * 32767.0D)
				loud++;
		}
		assertTrue(loud > frame.length * 0.7D, "mostly at full level, as a square wave is: " + loud);
		assertTrue(flips > 5 && flips < 48, "changes level at the bit edges only: " + flips); // 48 bits in a 20 ms frame at 2400 baud.
	}

	@Test
	void sameSeedSameBurst() {
		assertArrayEquals(PagerAudio.burst(30, 3L).get(12), PagerAudio.burst(30, 3L).get(12));
	}
}
