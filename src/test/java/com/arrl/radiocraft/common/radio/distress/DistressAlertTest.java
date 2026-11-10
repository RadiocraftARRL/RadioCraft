package com.arrl.radiocraft.common.radio.distress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The distress alert record (2026-11-09): one line on the air that a relay can send on unchanged. */
class DistressAlertTest {

	private static final DistressAlert CALL = new DistressAlert(0xF00DCAFE12345678L, DistressAlert.Type.DSC, DistressAlert.State.ACTIVE,
			"366123456", DistressNature.MAN_OVERBOARD, -1234, 63, 98765, 120_000L, "");

	@Test
	void encodesAndDecodesWhole() {
		assertEquals(CALL, DistressAlert.decode(CALL.encode()));
		DistressAlert ack = CALL.acknowledgedBy("338000001");
		DistressAlert back = DistressAlert.decode(ack.encode());
		assertEquals(ack, back);
		assertEquals(DistressAlert.State.ACKNOWLEDGED, back.state());
		assertEquals("338000001", back.ackBy());
		DistressAlert beacon = new DistressAlert(7L, DistressAlert.Type.BEACON, DistressAlert.State.SPENT, "Steve_The_Sailor", null, 1, 2, 3, 4L, "");
		assertEquals(beacon, DistressAlert.decode(beacon.encode()));
		assertEquals(DistressNature.UNDESIGNATED, beacon.nature());
	}

	@Test
	void fitsInAPacket() {
		DistressAlert worst = new DistressAlert(-1L, DistressAlert.Type.BEACON, DistressAlert.State.ACKNOWLEDGED, "ABCDEFGHIJKLMNOP",
				DistressNature.MAN_OVERBOARD, -30_000_000, -64, -30_000_000, 99_999_999_999L, "366123456"); // More game time than any world will have.
		assertTrue(worst.encode().length() <= 106, worst.encode()); // TncPacket.load keeps 106 characters of text.
		assertEquals(worst, DistressAlert.decode(worst.encode()));
	}

	@Test
	void ownerCannotBreakTheLine() {
		DistressAlert odd = new DistressAlert(1L, DistressAlert.Type.BEACON, DistressAlert.State.ACTIVE, "a|béc", null, 0, 0, 0, 0L, "");
		assertEquals("abc", odd.owner());
		assertEquals(odd, DistressAlert.decode(odd.encode()));
	}

	@Test
	void garbageIsNotAnAlert() {
		assertNull(DistressAlert.decode(null));
		assertNull(DistressAlert.decode(""));
		assertNull(DistressAlert.decode("HELLO{42"));
		assertNull(DistressAlert.decode(CALL.encode().replace("DA1", "DA9")));
		assertNull(DistressAlert.decode(CALL.encode().replace("|-1234|", "|x|")));
	}

	@Test
	void statesOnlyMoveOn() {
		assertTrue(DistressAlert.State.ACKNOWLEDGED.follows(DistressAlert.State.ACTIVE));
		assertTrue(DistressAlert.State.CANCELLED.follows(DistressAlert.State.ACTIVE));
		assertTrue(DistressAlert.State.CANCELLED.follows(DistressAlert.State.ACKNOWLEDGED));
		assertTrue(DistressAlert.State.SPENT.follows(DistressAlert.State.ACTIVE));
		assertFalse(DistressAlert.State.ACTIVE.follows(DistressAlert.State.ACKNOWLEDGED));
		assertFalse(DistressAlert.State.ACTIVE.follows(DistressAlert.State.CANCELLED));
		assertFalse(DistressAlert.State.ACKNOWLEDGED.follows(DistressAlert.State.CANCELLED));
	}

	@Test
	void natureCyclesRound() {
		DistressNature n = DistressNature.UNDESIGNATED;
		for(int i = 0; i < DistressNature.values().length; i++)
			n = n.next();
		assertEquals(DistressNature.UNDESIGNATED, n);
		for(DistressNature nature : DistressNature.values())
			assertTrue(nature.shortLabel().length() <= 7, nature.shortLabel());
	}
}
