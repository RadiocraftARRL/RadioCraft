package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RttyCopyTest {

	@Test
	void aLinePerSenderUntilANewline() {
		RttyCopy copy = new RttyCopy();
		copy.print("W1AAA", "CQ ", false);
		copy.print("W1AAA", "CQ\nDE", false);
		copy.print("W1BBB", "QRZ", false);
		List<RttyCopy.Line> lines = copy.lines();
		assertEquals(List.of(new RttyCopy.Line("W1AAA", "CQ CQ", false), new RttyCopy.Line("W1AAA", "DE", false),
				new RttyCopy.Line("W1BBB", "QRZ", false)), lines);
	}

	@Test
	void ourOwnCopyIsMarked() {
		RttyCopy copy = new RttyCopy();
		copy.print("W1ME", "TEST", true);
		copy.print("W1ME", "X", false); // The same call heard back (another station using it) is a new line.
		assertTrue(copy.lines().get(0).own());
		assertFalse(copy.lines().get(1).own());
		assertEquals("> W1ME: TEST\nW1ME: X\n", copy.text());
	}

	@Test
	void longLinesBreakAndHistoryIsCapped() {
		RttyCopy copy = new RttyCopy();
		copy.print("K", "A".repeat(RttyCopy.LINE_MAX + 5), false);
		assertEquals(RttyCopy.LINE_MAX, copy.lines().get(0).text().length());
		assertEquals(5, copy.lines().get(1).text().length());
		for(int i = 0; i < RttyCopy.HISTORY + 20; i++)
			copy.print("K", i + "\n", false);
		assertEquals(RttyCopy.HISTORY, copy.lines().size());
		assertEquals(String.valueOf(RttyCopy.HISTORY + 19), copy.lines().getLast().text());
	}

	@Test
	void savesAndLoads() {
		RttyCopy copy = new RttyCopy();
		copy.print("W1AAA", "HELLO\n", false);
		copy.print("W1ME", "HI", true);
		RttyCopy back = new RttyCopy();
		back.load(copy.toTag());
		assertEquals(copy.lines(), back.lines());
		back.clear();
		assertTrue(back.isEmpty());
	}

	@Test
	void macrosExpand() {
		assertEquals("CQ CQ CQ DE W1BTR W1BTR W1BTR K\n", RttyCopy.expand(RttyCopy.DEFAULT_MACROS.get(0), "W1BTR", ""));
		assertEquals("TU 73 K2ABC DE W1BTR SK\n", RttyCopy.expand(RttyCopy.DEFAULT_MACROS.get(2), "w1btr", "k2abc"));
		assertEquals("QRZ? DE W1BTR\n", RttyCopy.expand("qrz? de {mycall}\\n", "W1BTR", ""));
		assertEquals(" DE W1BTR ", RttyCopy.expand("{CALL} DE {MYCALL} ", "W1BTR", "")); // No DX call set: nothing.
	}
}
