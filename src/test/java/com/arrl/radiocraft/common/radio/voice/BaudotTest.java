package com.arrl.radiocraft.common.radio.voice;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaudotTest {

	@Test
	void lettersAreTheRealIta2Codes() {
		// E is 1, A is 3, space 4, T 16, O 24: the five bits of the US teleprinter.
		assertArrayEquals(new int[] { 1, 3, 4, 16, 24 }, Baudot.encode("EA TO"));
	}

	@Test
	void figuresShiftAndUnshiftOnSpace() {
		// W1AW: W, FIGS 1, LTRS A W. "599 TU": FIGS 5 9 9, space (unshifts), T U with no LTRS needed.
		assertArrayEquals(new int[] { 19, Baudot.FIGS, 23, Baudot.LTRS, 3, 19 }, Baudot.encode("W1AW"));
		assertArrayEquals(new int[] { Baudot.FIGS, 16, 24, 24, Baudot.SPACE, 16, 7 }, Baudot.encode("599 TU"));
	}

	@Test
	void newlineIsCarriageReturnLineFeed() {
		assertArrayEquals(new int[] { 14, Baudot.CR, Baudot.LF, 15 }, Baudot.encode("C\nK"));
	}

	@Test
	void decodesWhatItEncodes() {
		String text = "CQ CQ DE W1BTR W1BTR K\nRST 599 599 - QTH: BOSTON, MA? 73!";
		assertEquals(text, Baudot.decode(Baudot.encode(text)));
	}

	@Test
	void unsendableCharactersAreLeftOut() {
		assertEquals("AB", Baudot.decode(Baudot.encode("A+=B")));
		assertFalse(Baudot.canSend('+'));
		assertTrue(Baudot.canSend('$'));
	}

	@Test
	void aLostFiguresShiftPrintsLetters() {
		int[] codes = Baudot.encode("73");
		codes[0] = Baudot.LTRS; // The FIGS was lost in the noise.
		assertEquals("UE", Baudot.decode(codes)); // 7 and 3 share their keys with U and E.
	}

	@Test
	void misprintsAreOneBitAwayAndPrintable() {
		Random random = new Random(42L);
		for(int i = 0; i < 500; i++) {
			char c = "ETAOIN SHRDLU0123456789?-.".charAt(i % 26);
			char m = Baudot.misprint(c, random);
			assertNotEquals('\n', m);
			assertTrue(Baudot.canSend(m), "misprinted " + c + " as unsendable " + (int)m);
			if(c >= 'A' && c <= 'Z' && m >= 'A' && m <= 'Z') // A letter misprinted as a letter differs in exactly one bit.
				assertEquals(1, Integer.bitCount(Baudot.letterCode(c) ^ Baudot.letterCode(m)), c + " -> " + m);
		}
	}
}
