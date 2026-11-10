package com.arrl.radiocraft.common.radio.distress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The DISTRESS program's personal SOS (2026-11-09): repeats every 50 s while on, ends by a cancel or a lost radio. */
class PersonalSosTest {

	@Test
	void repeatsEveryFiftySecondsUntilCancelled() {
		PersonalSos sos = PersonalSos.start(7L, "Castaway", DistressNature.FIRE, 123L, 100L);
		assertTrue(sos.active());
		assertFalse(sos.repeatDue(100L + BeaconBattery.BURST_TICKS - 1));
		assertTrue(sos.repeatDue(100L + BeaconBattery.BURST_TICKS));
		PersonalSos again = sos.repeated(1100L);
		assertEquals(1100L + BeaconBattery.BURST_TICKS, again.nextAt());
		assertEquals(BeaconBattery.BURST_TICKS, again.ticksToRepeat(1100L));

		DistressAlert alert = again.alert(1, 64, 2);
		assertEquals(DistressAlert.Type.BEACON, alert.type(), "beacon-style, on 161.975");
		assertEquals(DistressAlert.State.ACTIVE, alert.state());
		assertEquals("Castaway", alert.owner());
		assertEquals(DistressNature.FIRE, alert.nature(), "unlike a real beacon it carries a nature");
		assertEquals(100L, alert.time(), "raised when first sent");
		assertEquals(alert, DistressAlert.decode(alert.encode()), "goes over the air whole");

		PersonalSos cancelled = again.cancelled();
		assertFalse(cancelled.active());
		assertFalse(cancelled.repeatDue(Long.MAX_VALUE / 2));
		assertEquals(DistressAlert.State.CANCELLED, cancelled.alert(1, 64, 2).state(), "the cancel burst");
		assertEquals(7L, cancelled.alert(1, 64, 2).id(), "same number as the alert");
		assertSame(cancelled, cancelled.cancelled());
	}

	@Test
	void stopsWhenItsRadioIsLost() {
		PersonalSos stopped = PersonalSos.start(7L, "Castaway", null, 123L, 0L).stopped("no_power");
		assertEquals(PersonalSos.State.STOPPED, stopped.state());
		assertEquals("no_power", stopped.reason());
		assertEquals(DistressNature.UNDESIGNATED, stopped.nature());
		assertFalse(stopped.repeatDue(Long.MAX_VALUE / 2));
		assertSame(PersonalSos.IDLE, PersonalSos.IDLE.stopped("x"), "nothing to stop");
	}
}
