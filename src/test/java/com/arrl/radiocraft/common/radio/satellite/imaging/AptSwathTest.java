package com.arrl.radiocraft.common.radio.satellite.imaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The scan's geometry along the ground track (2026-11-12). */
class AptSwathTest {

	@Test
	void aLineIsSwathWideAndCentredOnTheTrack() {
		assertEquals(8192, AptSwath.SWATH_BLOCKS);
		assertEquals(-AptSwath.offset(0), AptSwath.offset(AptSwath.PIXELS - 1), 1e-9, "symmetric about the point under it");
		assertEquals(AptSwath.BLOCKS_PER_PIXEL, AptSwath.offset(1) - AptSwath.offset(0), 1e-9);
		assertEquals(AptSwath.SWATH_BLOCKS - AptSwath.BLOCKS_PER_PIXEL, AptSwath.offset(AptSwath.PIXELS - 1) - AptSwath.offset(0), 1e-9);
	}

	@Test
	void rightIsToTheRightOfTheWayItGoes() {
		double[] north = AptSwath.right(0.0D, -6.0D); // Flying north (-z): right is east (+x).
		assertEquals(1.0D, north[0], 1e-9);
		assertEquals(0.0D, north[1], 1e-9);
		double[] east = AptSwath.right(6.0D, 0.0D); // Flying east: right is south (+z).
		assertEquals(0.0D, east[0], 1e-9);
		assertEquals(1.0D, east[1], 1e-9);
		double[] diagonal = AptSwath.right(3.0D, 4.0D);
		assertEquals(1.0D, Math.hypot(diagonal[0], diagonal[1]), 1e-9, "a unit vector");
		assertEquals(0.0D, diagonal[0] * 3.0D + diagonal[1] * 4.0D, 1e-9, "across the track");
		double[] still = AptSwath.right(0.0D, 0.0D);
		assertEquals(1.0D, Math.hypot(still[0], still[1]), 1e-9, "a still satellite still has a line");
		double[] along = AptSwath.along(north[0], north[1]);
		assertEquals(0.0D, along[0], 1e-9);
		assertEquals(-1.0D, along[1], 1e-9, "along points the way it flies");
	}

	@Test
	void pixelsRunLeftToRightAcrossTheTrack() {
		double[] right = AptSwath.right(0.0D, -6.0D);
		assertTrue(AptSwath.x(1000.0D, right[0], 0) < 1000.0D, "pixel 0 is on the left (west, flying north)");
		assertTrue(AptSwath.x(1000.0D, right[0], AptSwath.PIXELS - 1) > 1000.0D);
		assertEquals(500.0D, AptSwath.z(500.0D, right[1], 17), 1e-9, "a line across a north track is all one z");
	}

	@Test
	void linesComeOnceEveryPixelOfTrack() {
		assertEquals(AptSwath.BLOCKS_PER_PIXEL / 6.0D, AptSwath.lineTicks(6.0D), 1e-9);
		assertEquals(AptSwath.lineTicks(AptSwath.DEFAULT_SPEED), AptSwath.lineTicks(0.0D), 1e-9, "a still one scans at the usual rate");
		assertTrue(10_000.0D / AptSwath.BLOCKS_PER_PIXEL >= 500.0D, "a full pass (10,000 blocks of track) makes a long strip");
		assertTrue(AptSwath.SWATH_BLOCKS >= 8_000, "the swath covers most of a pass's 10,000-block coverage across");
	}
}
