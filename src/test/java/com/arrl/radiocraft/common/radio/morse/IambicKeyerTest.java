package com.arrl.radiocraft.common.radio.morse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IambicKeyerTest {

	/** The key line over {@code ticks} ticks with the paddles held as given ("1" down, "0" up), as a string. */
	private static String run(IambicKeyer keyer, int ticks, boolean dit, boolean dah) {
		StringBuilder out = new StringBuilder();
		for(int i = 0; i < ticks; i++)
			out.append(keyer.tick(dit, dah) ? '1' : '0');
		return out.toString();
	}

	@Test
	void ditsAndDahsAtOneTickADit() {
		assertEquals("101010", run(new IambicKeyer(IambicKeyer.Mode.IAMBIC_A, 1), 6, true, false));
		assertEquals("11101110", run(new IambicKeyer(IambicKeyer.Mode.IAMBIC_A, 1), 8, false, true));
		assertEquals("11001100", run(new IambicKeyer(IambicKeyer.Mode.IAMBIC_A, 2), 8, true, false), "two ticks a dit");
	}

	@Test
	void aSqueezeAlternatesStartingWithADit() {
		assertEquals("1011101", run(new IambicKeyer(IambicKeyer.Mode.IAMBIC_A, 1), 7, true, true));
	}

	@Test
	void modeBAddsOneElementAfterASqueezeIsReleased() {
		IambicKeyer a = new IambicKeyer(IambicKeyer.Mode.IAMBIC_A, 1), b = new IambicKeyer(IambicKeyer.Mode.IAMBIC_B, 1);
		String heldA = run(a, 2, true, true), heldB = run(b, 2, true, true); // A dit and its gap, squeezed.
		assertEquals(heldA, heldB);
		assertEquals("0000", run(a, 4, false, false), "mode A stops when let go");
		assertEquals("1110", run(b, 4, false, false), "mode B sends the dah it owed");
	}

	@Test
	void letGoStopsAfterTheElement() {
		IambicKeyer keyer = new IambicKeyer(IambicKeyer.Mode.IAMBIC_A, 1);
		assertEquals("1", run(keyer, 1, false, true));
		assertEquals("110000", run(keyer, 6, false, false), "the dah finishes, then silence");
	}

	@Test
	void bugAndStraight() {
		IambicKeyer bug = new IambicKeyer(IambicKeyer.Mode.BUG, 1);
		assertEquals("1010", run(bug, 4, true, false), "automatic dits");
		assertEquals("11111", run(bug, 5, false, true), "the dah side is held by hand");
		IambicKeyer straight = new IambicKeyer(IambicKeyer.Mode.STRAIGHT, 3);
		assertEquals("111", run(straight, 3, true, false));
		assertEquals("00", run(straight, 2, false, false));
	}

	@Test
	void wordsAMinute() {
		assertEquals(24, IambicKeyer.wpm(1));
		assertEquals(12, IambicKeyer.wpm(2));
		assertEquals(8, IambicKeyer.wpm(3));
		assertEquals(6, IambicKeyer.wpm(4));
	}
}
