package com.arrl.radiocraft.common.radio.tape;

import com.arrl.radiocraft.common.items.TapeItem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TapeAudioTest {

	/**
	 * A file whose header claims a sample rate of 1 Hz (or 200 MHz) is refused before any buffer is sized from it
	 * (2026-11-15: the rate sized the decode buffers, so a tiny file could ask for gigabytes).
	 */
	@Test
	void refusesAHeaderWithAnAbsurdRate() throws IOException {
		byte[] file;
		try(InputStream in = getClass().getResourceAsStream("/tape/tone_440.ogg")) {
			assertNotNull(in, "the test tape is missing");
			file = in.readAllBytes();
		}
		{ // The checksum is worked out right (else a refused page, not the rate, would fail it).
			byte[] same = file.clone();
			int segments = same[26] & 0xFF, length = 27 + segments;
			for(int i = 0; i < segments; i++)
				length += same[27 + i] & 0xFF;
			int stored = (same[22] & 0xFF) | (same[23] & 0xFF) << 8 | (same[24] & 0xFF) << 16 | (same[25] & 0xFF) << 24;
			for(int i = 22; i < 26; i++)
				same[i] = 0;
			assertEquals(stored, oggCrc(same, length), "the Ogg page checksum");
		}
		for(int rate : new int[] {1, 200_000_000}) {
			byte[] bad = file.clone();
			// The first page holds only the identification header: 27 header bytes, its segment table, then
			// type (1), "vorbis" (6), version (4), channels (1), rate (4, little-endian).
			int segments = bad[26] & 0xFF, at = 27 + segments + 1 + 6 + 4 + 1;
			for(int i = 0; i < 4; i++)
				bad[at + i] = (byte)(rate >> (8 * i));
			int length = 27 + segments;
			for(int i = 0; i < segments; i++)
				length += bad[27 + i] & 0xFF;
			for(int i = 22; i < 26; i++)
				bad[i] = 0;
			int crc = oggCrc(bad, length);
			for(int i = 0; i < 4; i++)
				bad[22 + i] = (byte)(crc >> (8 * i));
			assertThrows(IOException.class, () -> OggDecoder.decode(new java.io.ByteArrayInputStream(bad)), "rate " + rate);
		}
	}

	private static int oggCrc(byte[] data, int length) {
		int crc = 0;
		for(int i = 0; i < length; i++) {
			crc ^= (data[i] & 0xFF) << 24;
			for(int k = 0; k < 8; k++)
				crc = (crc & 0x80000000) != 0 ? (crc << 1) ^ 0x04C11DB7 : crc << 1;
		}
		return crc;
	}

	/** A second of a 440 Hz tone, stereo at 44.1 kHz, comes out a second long at 48 kHz, mono, still at 440 Hz. */
	@Test
	void decodesVorbisToTheRadiosFormat() throws IOException {
		short[] audio;
		try(InputStream in = getClass().getResourceAsStream("/tape/tone_440.ogg")) {
			assertNotNull(in, "the test tape is missing");
			audio = OggDecoder.decode(in);
		}
		assertEquals(48000, audio.length, 480, "a second at 48 kHz");
		int crossings = 0;
		for(int i = 1; i < audio.length; i++)
			if(audio[i - 1] < 0 && audio[i] >= 0)
				crossings++;
		assertEquals(440, crossings, 10, "the tone's pitch");
		int peak = 0;
		for(short s : audio)
			peak = Math.max(peak, Math.abs(s));
		assertTrue(peak > 12000 && peak < 20000, "half scale, mixed down without doubling: " + peak);
	}

	@Test
	void muLawRoundTripIsClose() {
		short[] audio = new short[4800];
		for(int i = 0; i < audio.length; i++)
			audio[i] = (short)(12000 * Math.sin(2 * Math.PI * 300 * i / 48000.0));
		short[] back = TapeRecordings.expand(TapeRecordings.compress(audio));
		assertEquals(audio.length, back.length);
		double error = 0;
		for(int i = 0; i < audio.length; i++)
			error += Math.abs(audio[i] - back[i]);
		assertTrue(error / audio.length < 600, "telephone quality, not noise: " + error / audio.length);
	}

	@Test
	void labelsFromFileNames() {
		assertEquals("Station A", TapeItem.label("numbers/station_a.ogg"));
		assertEquals("Old Broadcast", TapeItem.label("old-broadcast.OGG"));
	}
}
