package com.arrl.radiocraft.common.radio.distress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A marine radio's own DSC distress call (2026-11-09): repeats every 3.5 to 4.5 minutes until acked or cancelled. */
class DscCallTest {

	private static final long ID = 42L;

	@Test
	void repeatWaitIsThreeAndAHalfToFourAndAHalfMinutes() {
		assertEquals(4200, DscCall.repeatTicks(0.0D));
		assertEquals(5400, DscCall.repeatTicks(1.0D));
		assertEquals(4800, DscCall.repeatTicks(0.5D));
		assertEquals(4200, DscCall.repeatTicks(-3.0D));
		for(double r = 0.0D; r < 1.0D; r += 0.01D) {
			int ticks = DscCall.repeatTicks(r);
			assertTrue(ticks >= 3.5 * 1200 && ticks <= 4.5 * 1200, "wait " + ticks);
		}
	}

	@Test
	void repeatsWhenDueUntilAcknowledged() {
		DscCall call = DscCall.send(ID, "366123456", DistressNature.FIRE, 1000L, 4500);
		assertEquals(DscCall.State.ACTIVE, call.state());
		assertFalse(call.canSend());
		assertTrue(call.canCancel());
		assertFalse(call.repeatDue(5499L));
		assertEquals(1L, call.ticksToRepeat(5499L));
		assertTrue(call.repeatDue(5500L));
		call = call.repeated(5500L, 5000);
		assertFalse(call.repeatDue(10_499L));
		assertTrue(call.repeatDue(10_500L));
		assertEquals(1000L, call.sentAt(), "the alert keeps the time it was raised");

		DscCall acked = call.acknowledged(ID, "338000001");
		assertEquals(DscCall.State.ACKNOWLEDGED, acked.state());
		assertEquals("338000001", acked.ackBy());
		assertFalse(acked.repeatDue(1_000_000L), "acknowledged: no more repeats");
		assertTrue(acked.canCancel());
		assertSame(acked, acked.repeated(1_000_000L, 4200));
	}

	@Test
	void anAckForAnotherCallIsIgnored() {
		DscCall call = DscCall.send(ID, "366123456", DistressNature.SINKING, 0L, 4200);
		assertSame(call, call.acknowledged(ID + 1, "338000001"));
		assertSame(DscCall.IDLE, DscCall.IDLE.acknowledged(0L, "338000001"));
	}

	@Test
	void cancelStopsItAndANewCallMayFollow() {
		DscCall call = DscCall.send(ID, "366123456", DistressNature.UNDESIGNATED, 0L, 4200).cancelled();
		assertEquals(DscCall.State.CANCELLED, call.state());
		assertFalse(call.repeatDue(100_000L));
		assertFalse(call.canCancel());
		assertTrue(call.canSend());
		assertSame(call, call.cancelled());
		assertSame(DscCall.IDLE, DscCall.IDLE.cancelled(), "nothing to cancel");
		DscCall acked = DscCall.send(ID, "366123456", DistressNature.UNDESIGNATED, 0L, 4200).acknowledged(ID, "338000001").cancelled();
		assertEquals(DscCall.State.CANCELLED, acked.state());
	}

	@Test
	void itsAlertCarriesTheCall() {
		DscCall call = DscCall.send(ID, "366123456", DistressNature.GROUNDING, 777L, 4200);
		DistressAlert alert = call.alert(10, 64, -20);
		assertEquals(new DistressAlert(ID, DistressAlert.Type.DSC, DistressAlert.State.ACTIVE, "366123456", DistressNature.GROUNDING, 10, 64, -20, 777L, ""), alert);
		assertEquals(DistressAlert.State.CANCELLED, call.cancelled().alert(0, 0, 0).state());
		assertEquals(DistressAlert.State.ACKNOWLEDGED, call.acknowledged(ID, "x").alert(0, 0, 0).state());
	}
}
