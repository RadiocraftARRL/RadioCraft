package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputerLockTest {

	private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Test
	void theRightPasswordMatches() {
		String hash = ComputerLock.hash(A, "hunter2");
		assertTrue(ComputerLock.matches(A, hash, "hunter2"));
		assertFalse(ComputerLock.matches(A, hash, "hunter3"));
		assertFalse(ComputerLock.matches(A, hash, ""));
		assertFalse(hash.contains("hunter2"), "kept as a hash");
	}

	@Test
	void theComputerIsTheSalt() {
		assertNotEquals(ComputerLock.hash(A, "same"), ComputerLock.hash(B, "same"));
		assertFalse(ComputerLock.matches(B, ComputerLock.hash(A, "same"), "same"), "a hash copied to another computer does not open it");
	}

	@Test
	void noPasswordOpensForAnyone() {
		assertEquals("", ComputerLock.hash(A, ""));
		assertTrue(ComputerLock.matches(A, "", "anything"));
	}

	@Test
	void passwordsAreShortPrintableAscii() {
		assertTrue(ComputerLock.valid(""));
		assertTrue(ComputerLock.valid("Pa$$ w0rd!"));
		assertTrue(ComputerLock.valid("x".repeat(ComputerLock.MAX_LENGTH)));
		assertFalse(ComputerLock.valid("x".repeat(ComputerLock.MAX_LENGTH + 1)));
		assertFalse(ComputerLock.valid("tab\there"));
		assertFalse(ComputerLock.valid("café"));
		assertFalse(ComputerLock.valid(null));
	}
}
