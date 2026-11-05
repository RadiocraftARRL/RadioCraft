package com.arrl.radiocraft.common.realworld;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sound card formats, frames and levels around the resampler. */
class PcmCodecTest {

	@Test
	void monoRoundTripsBothEndians() {
		short[] pcm = { 0, 1, -1, 12345, -12345, Short.MAX_VALUE, Short.MIN_VALUE };
		for(boolean big : new boolean[] { false, true }) {
			byte[] bytes = new byte[pcm.length * 2];
			assertEquals(bytes.length, PcmCodec.encode(pcm, pcm.length, 1, big, bytes));
			short[] back = new short[pcm.length];
			assertEquals(pcm.length, PcmCodec.decode(bytes, bytes.length, 1, big, back));
			assertArrayEquals(pcm, back);
		}
	}

	@Test
	void littleEndianByteOrder() {
		byte[] bytes = new byte[2];
		PcmCodec.encode(new short[] { 0x1234 }, 1, 1, false, bytes);
		assertEquals(0x34, bytes[0]);
		assertEquals(0x12, bytes[1]);
	}

	@Test
	void stereoIsAveragedInAndCopiedOut() {
		short[] pcm = { 1000, -2000 };
		byte[] bytes = new byte[8];
		assertEquals(8, PcmCodec.encode(pcm, 2, 2, false, bytes));
		short[] back = new short[2];
		assertEquals(2, PcmCodec.decode(bytes, 8, 2, false, back));
		assertArrayEquals(pcm, back);
		// Left 1000, right 3000: the mono sample is 2000.
		byte[] lr = { (byte)0xE8, 0x03, (byte)0xB8, 0x0B };
		short[] mono = new short[1];
		PcmCodec.decode(lr, 4, 2, false, mono);
		assertEquals(2000, mono[0]);
	}

	@Test
	void aPartialFrameIsLeftOut() {
		short[] out = new short[4];
		assertEquals(1, PcmCodec.decode(new byte[] { 1, 0, 2 }, 3, 1, false, out));
		assertEquals(1, PcmCodec.decode(new byte[] { 1, 0, 2, 0, 3, 0 }, 6, 2, false, out));
	}

	@Test
	void chunkerMakesWholeFramesInOrder() {
		FrameChunker chunker = new FrameChunker(4);
		assertTrue(chunker.push(new short[] { 1, 2, 3 }).isEmpty());
		assertEquals(3, chunker.pending());
		List<short[]> frames = chunker.push(new short[] { 4, 5, 6, 7, 8, 9, 10 });
		assertEquals(2, frames.size());
		assertArrayEquals(new short[] { 1, 2, 3, 4 }, frames.get(0));
		assertArrayEquals(new short[] { 5, 6, 7, 8 }, frames.get(1));
		assertEquals(2, chunker.pending());
		assertArrayEquals(new short[] { 9, 10, 11, 12 }, chunker.push(new short[] { 11, 12 }).get(0));
	}

	@Test
	void levelsInDbfs() {
		assertEquals(AudioLevels.FLOOR_DB, AudioLevels.rmsDb(new short[960]));
		short[] square = new short[960];
		for(int i = 0; i < square.length; i++)
			square[i] = (short)(i % 2 == 0 ? 16384 : -16384); // Half scale.
		assertEquals(-6.02D, AudioLevels.rmsDb(square), 0.01D);
		AudioLevels.gain(square, square.length, 4.0D); // Clips at full scale.
		assertEquals(Short.MAX_VALUE, square[0]);
		assertEquals(Short.MIN_VALUE, square[1]);
		assertEquals(-10.0D, AudioLevels.meter(-20.0D, -10.0D, 1.5D)); // Rises at once...
		assertEquals(-21.5D, AudioLevels.meter(-20.0D, -60.0D, 1.5D)); // ...falls back slowly.
	}
}
