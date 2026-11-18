package com.arrl.radiocraft.common.radio.antenna;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A frame handed to many receivers is shared until one asks for it (2026-11-18): each then gets its own copy, so one
 * receiver working on its audio in place changes nobody else's, nor the sender's frame.
 */
class SharedAudioTest {

	@Test
	void eachReceiverGetsItsOwnCopy() {
		short[] frame = { 1, 2, 3, 4 };
		AntennaVoicePacket a = new AntennaVoicePacket(null, frame, null, 146_520_000.0F, 1.0D, null, null).shareAudio();
		AntennaVoicePacket b = new AntennaVoicePacket(null, frame, null, 146_520_000.0F, 1.0D, null, null).shareAudio();
		short[] mine = a.getRawAudio();
		assertNotSame(frame, mine, "the shared frame was handed out");
		assertSame(mine, a.getRawAudio(), "copied twice");
		mine[0] = 99;
		assertArrayEquals(new short[] { 1, 2, 3, 4 }, b.getRawAudio(), "another receiver's audio changed");
		assertEquals(1, frame[0], "the sender's frame changed");
	}

	@Test
	void unsharedAudioIsTheArrayGiven() {
		short[] frame = { 5, 6 };
		assertSame(frame, new AntennaVoicePacket(null, frame, null, 7_100_000.0F, 1.0D, null, null).getRawAudio());
	}
}
