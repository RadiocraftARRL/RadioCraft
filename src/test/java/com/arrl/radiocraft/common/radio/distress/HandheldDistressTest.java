package com.arrl.radiocraft.common.radio.distress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The beacon alert a commercial VHF handheld keeps (2026-11-11): shown when a new active one comes in, kept current by
 * its repeats, and once dismissed not brought back by them; a new alert takes its place.
 */
class HandheldDistressTest {

	private static DistressAlert beacon(long id, DistressAlert.State state, int x) {
		return new DistressAlert(id, DistressAlert.Type.BEACON, state, "Castaway", null, x, 64, -340, 1000L, "");
	}

	@Test
	void aNewActiveAlertIsShown() {
		DistressAlert alert = beacon(1L, DistressAlert.State.ACTIVE, 120);
		assertEquals(HandheldDistress.Change.NEW, HandheldDistress.change(null, alert));
		HandheldDistress heard = HandheldDistress.hear(null, alert);
		assertNotNull(heard);
		assertEquals(alert, heard.shown());
		assertEquals("DISTRESS SOS RX 120, 64, -340", heard.shown().sosRx());
		assertEquals("Castaway BEACON", heard.shown().who());
	}

	@Test
	void anAlertAlreadyOverIsNotNews() {
		DistressAlert spent = beacon(1L, DistressAlert.State.SPENT, 120);
		assertEquals(HandheldDistress.Change.NONE, HandheldDistress.change(null, spent));
		assertNull(HandheldDistress.hear(null, spent));
	}

	@Test
	void repeatsKeepItCurrentAndDismissedStaysDismissed() {
		HandheldDistress heard = HandheldDistress.hear(null, beacon(1L, DistressAlert.State.ACTIVE, 120));
		assertSame(heard, HandheldDistress.hear(heard, beacon(1L, DistressAlert.State.ACTIVE, 120)), "the same burst changes nothing");
		HandheldDistress moved = HandheldDistress.hear(heard, beacon(1L, DistressAlert.State.ACTIVE, 130));
		assertEquals(HandheldDistress.Change.UPDATE, HandheldDistress.change(heard, beacon(1L, DistressAlert.State.ACTIVE, 130)));
		assertNotNull(moved);
		assertEquals(130, moved.shown().x());

		HandheldDistress dismissed = moved.dismiss();
		assertNull(dismissed.shown());
		HandheldDistress repeat = HandheldDistress.hear(dismissed, beacon(1L, DistressAlert.State.ACTIVE, 140));
		assertNotNull(repeat);
		assertNull(repeat.shown(), "a repeat brought a dismissed alert back");
		assertEquals(140, repeat.decoded().x(), "the repeat still updates where it is");

		HandheldDistress spent = HandheldDistress.hear(repeat, beacon(1L, DistressAlert.State.SPENT, 140));
		assertNotNull(spent);
		assertEquals(DistressAlert.State.SPENT, spent.decoded().state());
		assertSame(spent, HandheldDistress.hear(spent, beacon(1L, DistressAlert.State.ACTIVE, 140)), "it never goes back to active");
	}

	@Test
	void aNewAlertTakesThePlaceOfADismissedOne() {
		HandheldDistress dismissed = HandheldDistress.hear(null, beacon(1L, DistressAlert.State.ACTIVE, 120)).dismiss();
		HandheldDistress next = HandheldDistress.hear(dismissed, beacon(2L, DistressAlert.State.ACTIVE, -5));
		assertNotNull(next);
		assertTrue(next.shown() != null && next.shown().id() == 2L);
	}
}
