package com.arrl.radiocraft.common.radio.ft8;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FT8's tones (computer batch 3B): 79 symbols with the Costas sync, 12.64 s of frames, made once and kept. */
class Ft8AudioTest {

	@Test
	void theSymbolsCarryTheCostasSync() {
		int[] symbols = Ft8Audio.symbols("CQ W1BTR FN31");
		assertEquals(79, symbols.length);
		for(int k = 0; k < 7; k++) {
			assertEquals(Ft8.COSTAS[k], symbols[k]);
			assertEquals(Ft8.COSTAS[k], symbols[36 + k]);
			assertEquals(Ft8.COSTAS[k], symbols[72 + k]);
		}
		for(int symbol : symbols)
			assertTrue(symbol >= 0 && symbol < 8);
		assertArrayEquals(symbols, Ft8Audio.symbols("CQ W1BTR FN31"), "the same message sounds the same");
		assertTrue(!java.util.Arrays.equals(symbols, Ft8Audio.symbols("CQ K1ABC FN42")), "another sounds different");
	}

	@Test
	void aTransmissionIsTwelvePointSixFourSeconds() {
		Ft8Audio.clear();
		List<short[]> frames = Ft8Audio.frames("CQ W1BTR FN31", 1500);
		assertEquals(632, frames.size());
		assertEquals(Ft8Audio.frameCount(), frames.size());
		assertTrue(frames.size() / 2.5D <= Ft8.TX_TICKS + 1, "fits the ticks the radio sends it in");
		int peak = 0;
		for(short[] frame : frames)
			for(short sample : frame)
				peak = Math.max(peak, Math.abs(sample));
		assertTrue(peak > 8000 && peak < 12000, "a steady tone at 0.3: " + peak);
	}

	@Test
	void recentTransmissionsAreKept() {
		Ft8Audio.clear();
		List<short[]> first = Ft8Audio.frames("CQ W1BTR FN31", 1500);
		assertSame(first, Ft8Audio.frames("CQ W1BTR FN31", 1500), "a CQ every other slot is made once");
		assertNotSame(first, Ft8Audio.frames("CQ W1BTR FN31", 1600), "another offset is another sound");
		for(int i = 0; i < Ft8Audio.CACHE; i++)
			Ft8Audio.frames("CQ W1BTR FN31", 2000 + i * 25);
		assertNotSame(first, Ft8Audio.frames("CQ W1BTR FN31", 1500), "only the last few are kept");
	}
}
