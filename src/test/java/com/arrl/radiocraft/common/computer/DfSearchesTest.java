package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.radio.DirectionFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The direction finding program's saved searches (1.12 step 7): newest first, capped, kept through the file's NBT. */
class DfSearchesTest {

	/** Two stations 200 blocks apart looking at a transmitter at (100, -100). */
	private static DfSearches.Search crossing(String note) {
		return new DfSearches.Search(1_000L, 20L, 146_520_000.0F, note, List.of(
				new DfSearches.Bearing(0.0D, 0.0D, 45.0D, 3.0D, DfSearches.Source.OWN),
				new DfSearches.Bearing(200.0D, 0.0D, 315.0D, 3.0D, DfSearches.Source.TYPED)));
	}

	@Test
	void aSearchWorksItsFixOutAgain() {
		DirectionFinding.Fix fix = crossing("fox").fix();
		assertTrue(fix.found(), "no fix: " + fix.problem());
		assertEquals(100.0D, fix.x(), 1.0D);
		assertEquals(-100.0D, fix.z(), 1.0D);
		assertFalse(new DfSearches.Search(0L, 0L, 0.0F, "", List.of(new DfSearches.Bearing(0, 0, 10, 2, DfSearches.Source.OWN))).fix().found(), "one bearing fixed");
	}

	@Test
	void newestFirstAndCapped() {
		DfSearches searches = new DfSearches();
		for(int i = 0; i < DfSearches.MAX + 5; i++)
			searches.add(crossing("hunt " + i));
		assertEquals(DfSearches.MAX, searches.size());
		assertEquals("hunt " + (DfSearches.MAX + 4), searches.all().getFirst().note());
		assertTrue(searches.remove(0));
		assertFalse(searches.remove(DfSearches.MAX));
		assertEquals(DfSearches.MAX - 1, searches.size());
	}

	@Test
	void roundTripsThroughTheFile() {
		DfSearches searches = new DfSearches();
		searches.add(crossing("x".repeat(100)));
		DfSearches back = DfSearches.fromTag(searches.toTag());
		assertEquals(1, back.size());
		DfSearches.Search search = back.all().getFirst();
		assertEquals(DfSearches.NOTE_MAX, search.note().length(), "the note is cut");
		assertEquals(146_520_000.0F, search.frequencyHz());
		assertEquals(2, search.bearings().size());
		assertEquals(DfSearches.Source.TYPED, search.bearings().get(1).source());
		assertEquals(315.0D, search.bearings().get(1).bearing(), 1.0E-9D);
		assertTrue(search.fix().found());
	}
}
