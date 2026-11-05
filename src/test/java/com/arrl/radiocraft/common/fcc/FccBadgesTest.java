package com.arrl.radiocraft.common.fcc;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The FCC badge's number and name rules (1.12 step 10). */
class FccBadgesTest {

	@Test
	void numbersAreFourDigitsAndStable() {
		Set<Integer> seen = new HashSet<>();
		for(int i = 0; i < 2000; i++) {
			UUID agent = UUID.nameUUIDFromBytes(("agent" + i).getBytes());
			int number = FccBadges.numberFor(agent);
			assertTrue(number >= FccBadges.FIRST_NUMBER && number <= FccBadges.LAST_NUMBER, "Out of range: " + number);
			assertEquals(number, FccBadges.numberFor(new UUID(agent.getMostSignificantBits(), agent.getLeastSignificantBits())), "Not stable");
			seen.add(number);
		}
		assertTrue(seen.size() > 1500, "Badge numbers bunch up: " + seen.size() + " different for 2000 agents");
	}

	@Test
	void extremeIdsStayInRange() {
		for(UUID agent : new UUID[] { new UUID(0, 0), new UUID(-1, -1), new UUID(Long.MIN_VALUE, Long.MAX_VALUE), new UUID(Long.MAX_VALUE, Long.MIN_VALUE) }) {
			int number = FccBadges.numberFor(agent);
			assertTrue(number >= FccBadges.FIRST_NUMBER && number <= FccBadges.LAST_NUMBER, agent + " gave " + number);
		}
	}

	@Test
	void namesAreTrimmedAndCut() {
		assertEquals("", FccBadges.cleanName(null));
		assertEquals("W1BTR", FccBadges.cleanName("  W1BTR "));
		assertEquals(FccBadges.NAME_MAX, FccBadges.cleanName("x".repeat(100)).length());
	}

	@Test
	void ownBadgeIsTheAgents() {
		UUID agent = UUID.nameUUIDFromBytes("agent".getBytes());
		UUID other = UUID.nameUUIDFromBytes("other".getBytes());
		assertTrue(FccBadges.isOwnBadge(agent, agent));
		assertFalse(FccBadges.isOwnBadge(agent, other));
		assertFalse(FccBadges.isOwnBadge(null, other));
	}
}
