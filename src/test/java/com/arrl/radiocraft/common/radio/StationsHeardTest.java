package com.arrl.radiocraft.common.radio;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stations a radio heard lately, for the logbook (1.12 step 6). */
class StationsHeardTest {

	private static UUID player(int n) {
		return new UUID(0L, n);
	}

	@Test
	void remembersTheBestStrengthOfATransmission() {
		StationsHeard heard = new StationsHeard();
		heard.heard(player(1), 146_520_000.0F, 0.3D, 100L);
		heard.heard(player(1), 146_520_000.0F, 0.8D, 101L);
		heard.heard(player(1), 146_520_000.0F, 0.5D, 102L);
		List<StationsHeard.Heard> recent = heard.recent(110L, 100L);
		assertEquals(1, recent.size());
		assertEquals(0.8D, recent.getFirst().strength());
		assertEquals(100L, recent.getFirst().firstGameTime());
		assertEquals(102L, recent.getFirst().lastGameTime());

		// A long gap later, a new transmission starts its best strength again.
		heard.heard(player(1), 146_520_000.0F, 0.2D, 102L + StationsHeard.NEW_OVER_TICKS + 1L);
		assertEquals(0.2D, heard.recent(102L + StationsHeard.NEW_OVER_TICKS + 1L, 100L).getFirst().strength());
	}

	@Test
	void newestFirstAndOnlyRecent() {
		StationsHeard heard = new StationsHeard();
		heard.heard(player(1), 7_200_000.0F, 0.5D, 10L);
		heard.heard(player(2), 7_200_000.0F, 0.5D, 50L);
		List<StationsHeard.Heard> recent = heard.recent(60L, 100L);
		assertEquals(player(2), recent.get(0).player());
		assertEquals(player(1), recent.get(1).player());
		assertEquals(1, heard.recent(60L, 20L).size(), "player 1 was heard too long ago");
		heard.heard(null, 7_200_000.0F, 1.0D, 61L);
		assertEquals(2, heard.recent(61L, 100L).size(), "a frame with nobody talking is not a station");
	}

	@Test
	void theOldestMakesRoom() {
		StationsHeard heard = new StationsHeard();
		for(int i = 0; i < StationsHeard.SLOTS; i++)
			heard.heard(player(i), 146_520_000.0F, 0.5D, i);
		heard.heard(player(0), 146_520_000.0F, 0.5D, 100L); // Player 0 is heard again: player 1 is now the oldest.
		heard.heard(player(99), 146_520_000.0F, 0.5D, 101L);
		List<StationsHeard.Heard> recent = heard.recent(101L, 1000L);
		assertEquals(StationsHeard.SLOTS, recent.size());
		assertTrue(recent.stream().anyMatch(h -> h.player().equals(player(99))));
		assertTrue(recent.stream().anyMatch(h -> h.player().equals(player(0))));
		assertFalse(recent.stream().anyMatch(h -> h.player().equals(player(1))));
		heard.clear();
		assertTrue(heard.recent(101L, 1000L).isEmpty());
	}

	/** Computer batch 3C: the snapshot of where the transmitting antenna was is taken when the station is heard. */
	@Test
	void keepsTheSnapshotOfWhereTheStationWasHeardFrom() {
		StationsHeard heard = new StationsHeard();
		java.util.UUID who = java.util.UUID.randomUUID();
		heard.heard(who, 147_180_000F, 0.5D, 100L, true, 1000, -2000, "minecraft:overworld");
		StationsHeard.Heard h = heard.recent(100L, 20L).get(0);
		assertTrue(h.hasPos());
		assertEquals(1000, h.x());
		assertEquals(-2000, h.z());
		assertEquals("minecraft:overworld", h.dimension());
		heard.heard(who, 147_180_000F, 0.5D, 120L);
		assertFalse(heard.recent(120L, 20L).get(0).hasPos(), "a frame with no antenna position says so");
	}
}
