package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MUSIC's shuffle (1.12 music player fixes): every tune once a round, never twice in a row, Back goes back. */
class MusicShuffleTest {

	@Test
	void neverRepeatsAndPlaysEveryTuneEachRound() {
		MusicShuffle shuffle = new MusicShuffle();
		Random random = new Random(42L);
		int size = 7, current = 3;
		shuffle.played(-1, current);
		for(int round = 0; round < 50; round++) {
			Set<Integer> heard = new HashSet<>();
			for(int i = 0; i < size - 1; i++) {
				int next = shuffle.next(current, size, random);
				assertNotEquals(current, next, "the same tune twice");
				assertTrue(next >= 0 && next < size);
				shuffle.played(current, next);
				heard.add(next);
				current = next;
			}
			assertEquals(size - 1, heard.size(), "a tune came twice in one round");
		}
	}

	@Test
	void aTunePickedByHandLeavesTheBag() {
		MusicShuffle shuffle = new MusicShuffle();
		Random random = new Random(1L);
		int first = shuffle.next(-1, 5, random);
		shuffle.played(-1, first);
		int picked = (first + 2) % 5;
		shuffle.played(first, picked);
		Set<Integer> rest = new HashSet<>();
		int current = picked;
		for(int i = 0; i < 3; i++) {
			int next = shuffle.next(current, 5, random);
			shuffle.played(current, next);
			rest.add(next);
			current = next;
		}
		assertTrue(!rest.contains(picked) && !rest.contains(first), rest + " after " + first + ", " + picked);
	}

	@Test
	void backGoesThroughWhatWasHeard() {
		MusicShuffle shuffle = new MusicShuffle();
		shuffle.played(-1, 4);
		shuffle.played(4, 1);
		shuffle.played(1, 6);
		assertEquals(1, shuffle.back(6, 8));
		assertEquals(4, shuffle.back(1, 8));
		assertEquals(-1, shuffle.back(4, 8));
	}

	@Test
	void oneTuneOrNone() {
		MusicShuffle shuffle = new MusicShuffle();
		assertEquals(0, shuffle.next(0, 1, new Random()));
		assertEquals(-1, shuffle.next(-1, 0, new Random()));
	}
}
