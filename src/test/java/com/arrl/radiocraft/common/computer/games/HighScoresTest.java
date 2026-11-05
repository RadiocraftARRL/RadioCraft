package com.arrl.radiocraft.common.computer.games;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A game's high score table (1.12 step 8): the best ten, highest first, kept through NBT. */
class HighScoresTest {

	@Test
	void keepsTheBestTenHighestFirst() {
		HighScores scores = new HighScores();
		for(int i = 1; i <= 15; i++)
			scores.add("P" + i, i * 10);
		assertEquals(HighScores.SIZE, scores.entries().size());
		assertEquals(150, scores.best());
		assertEquals("P15", scores.entries().getFirst().name());
		assertEquals(60, scores.entries().getLast().score());
		assertFalse(scores.qualifies(60));
		assertTrue(scores.qualifies(61));
	}

	@Test
	void aTieGoesBelowAndZeroNeverCounts() {
		HighScores scores = new HighScores();
		assertEquals(0, scores.add("W1BTR", 100));
		assertEquals(1, scores.add("K1ABC", 100));
		assertEquals(-1, scores.add("N0ONE", 0));
		assertEquals("W1BTR", scores.entries().getFirst().name());
	}

	@Test
	void longNamesAreCut() {
		HighScores scores = new HighScores();
		scores.add("A_VERY_LONG_PLAYER_NAME", 5);
		assertEquals(HighScores.NAME_LENGTH, scores.entries().getFirst().name().length());
	}

	@Test
	void survivesTheDisk() {
		HighScores scores = new HighScores();
		scores.add("W1BTR", 300);
		scores.add("K1ABC", 200);
		HighScores back = HighScores.fromTag(scores.toTag());
		assertEquals(scores.entries(), back.entries());
		assertTrue(HighScores.fromTag(null).entries().isEmpty());
	}
}
