package com.arrl.radiocraft.common.radio.distress;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A player's MMSI (2026-11-09): nine digits, a US MID first, the same every time, different between players. */
class MmsiTest {

	@Test
	void nineDigitsStartingWithAUsMid() {
		for(int i = 0; i < 500; i++) {
			String mmsi = Mmsi.of(UUID.nameUUIDFromBytes(("player" + i).getBytes()));
			assertTrue(Mmsi.isShip(mmsi), mmsi);
			assertEquals('3', mmsi.charAt(0), mmsi);
			assertTrue(Set.of("338", "366", "367", "368", "369").contains(mmsi.substring(0, 3)), mmsi);
		}
	}

	@Test
	void stableForTheSameUuid() {
		UUID uuid = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
		assertEquals(Mmsi.of(uuid), Mmsi.of(UUID.fromString(uuid.toString())));
	}

	@Test
	void playersMostlyDiffer() {
		Set<String> seen = new HashSet<>();
		for(int i = 0; i < 1000; i++)
			seen.add(Mmsi.of(UUID.randomUUID()));
		assertTrue(seen.size() > 990, "too many clashes: " + seen.size());
	}

	@Test
	void shipNumbersOnly() {
		assertFalse(Mmsi.isShip("036612345")); // A leading 0 is a group or coast station.
		assertFalse(Mmsi.isShip("36612345"));
		assertFalse(Mmsi.isShip("36612345X"));
		assertFalse(Mmsi.isShip(null));
		assertTrue(Mmsi.isShip("366123456"));
	}
}
