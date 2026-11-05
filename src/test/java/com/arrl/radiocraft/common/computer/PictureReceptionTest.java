package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A picture coming in line by line (1.12 step 7): the clocks, clean lines while copying, noise while not. */
class PictureReceptionTest {

	private static final int SIZE = PictureReception.SIZE;
	private static final byte PIC = 10, BLANK = 0, DARK = 1, LIGHT = 2;

	private static PictureReception reception(PictureReception.Clock clock, int ticks) {
		byte[] picture = new byte[SIZE * SIZE];
		Arrays.fill(picture, PIC);
		return new PictureReception(42L, "W1AW", "SSTV TEST", picture, 100L, ticks, clock, BLANK, DARK, LIGHT);
	}

	@Test
	void sstvLinesComeEvenlyAcrossTheTones() {
		assertEquals(0, PictureReception.rowsAt(PictureReception.Clock.SSTV, 0, 100, SIZE));
		assertEquals(SIZE / 2, PictureReception.rowsAt(PictureReception.Clock.SSTV, 50, 100, SIZE));
		assertEquals(SIZE, PictureReception.rowsAt(PictureReception.Clock.SSTV, 100, 100, SIZE));
		assertEquals(SIZE, PictureReception.rowsAt(PictureReception.Clock.SSTV, 500, 100, SIZE));
	}

	@Test
	void wefaxWaitsForTheStartToneAndPhasing() {
		assertEquals(0, PictureReception.rowsAt(PictureReception.Clock.WEFAX, 14, 100, SIZE), "lines during phasing");
		assertTrue(PictureReception.rowsAt(PictureReception.Clock.WEFAX, 20, 100, SIZE) > 0);
		assertEquals(SIZE, PictureReception.rowsAt(PictureReception.Clock.WEFAX, 97, 100, SIZE), "all in before the stop tone");
	}

	@Test
	void copiedLinesAreCleanAndLostOnesNoise() {
		PictureReception reception = reception(PictureReception.Clock.SSTV, 128);
		assertFalse(reception.advance(100L, true), "nothing at the start");
		assertTrue(reception.advance(164L, true));
		assertEquals(64, reception.rowsDone());
		assertEquals(PIC, reception.shown()[63 * SIZE + 5]);
		assertEquals(BLANK, reception.shown()[64 * SIZE + 5], "a line not come yet");
		reception.advance(196L, false);
		assertEquals(96, reception.rowsDone());
		assertEquals(32, reception.rowsLost());
		int noise = 0;
		for(int x = 0; x < SIZE; x++)
			if(reception.shown()[70 * SIZE + x] != PIC)
				noise++;
		assertTrue(noise > SIZE / 2, "a lost line is mostly noise: " + noise);
		assertFalse(reception.complete());
		reception.finish(true);
		assertTrue(reception.complete());
		assertEquals(PIC, reception.shown()[127 * SIZE]);
		assertEquals(32, reception.rowsLost());
	}

	@Test
	void overdueAfterTheTonesAndSlack() {
		PictureReception reception = reception(PictureReception.Clock.SSTV, 50);
		assertFalse(reception.overdue(150L, 0));
		assertTrue(reception.overdue(151L, 0));
		assertFalse(reception.overdue(151L, 20));
		reception.advance(125L, true);
		assertEquals(0.5D, reception.progress(), 0.01D);
	}
}
