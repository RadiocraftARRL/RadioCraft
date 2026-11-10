package com.arrl.radiocraft.common.radio.distress;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** What a marine radio's DSC receiver keeps (2026-11-09): one entry per alert, kept up to date, never going back. */
class DistressLogTest {

	private static DistressAlert call(long id, DistressAlert.State state) {
		return new DistressAlert(id, DistressAlert.Type.DSC, state, "366123456", DistressNature.FIRE, 0, 64, 0, 0L, "");
	}

	@Test
	void newThenRepeatThenAckThenCancel() {
		DistressLog log = new DistressLog();
		assertEquals(DistressLog.Change.NEW, log.hear(call(1, DistressAlert.State.ACTIVE), 10L));
		assertEquals(DistressLog.Change.REPEAT, log.hear(call(1, DistressAlert.State.ACTIVE).at(5, 64, 5), 20L));
		assertEquals(5, log.get(1).alert().x(), "a repeat moves it");
		assertEquals(20L, log.get(1).heardAt());
		assertEquals(DistressLog.Change.STATE, log.hear(call(1, DistressAlert.State.ACTIVE).acknowledgedBy("338000001"), 30L));
		assertEquals(DistressAlert.State.ACKNOWLEDGED, log.get(1).alert().state());
		assertEquals(DistressLog.Change.NONE, log.hear(call(1, DistressAlert.State.ACTIVE), 40L), "a late repeat does not undo the ack");
		assertEquals(DistressAlert.State.ACKNOWLEDGED, log.get(1).alert().state());
		assertEquals(DistressLog.Change.STATE, log.hear(call(1, DistressAlert.State.CANCELLED), 50L));
		assertEquals(DistressAlert.State.CANCELLED, log.get(1).alert().state(), "cancelled calls stay listed");
		assertEquals(1, log.entries().size());
	}

	@Test
	void newestFirstAndAtMostSixteen() {
		DistressLog log = new DistressLog();
		for(int i = 1; i <= 20; i++)
			log.hear(call(i, DistressAlert.State.ACTIVE), i);
		List<DistressLog.Entry> entries = log.entries();
		assertEquals(DistressLog.MAX, entries.size());
		assertEquals(20L, entries.getFirst().alert().id());
		assertNull(log.get(1L), "the oldest went");
		log.hear(call(10, DistressAlert.State.ACTIVE), 99L);
		assertEquals(10L, log.entries().getFirst().alert().id(), "a repeat comes to the top");
	}

	@Test
	void aCallAndABeaconWithTheSameNumberAreApart() {
		DistressLog log = new DistressLog();
		log.hear(call(5, DistressAlert.State.ACTIVE), 1L);
		DistressAlert beacon = new DistressAlert(5, DistressAlert.Type.BEACON, DistressAlert.State.ACTIVE, "Alex", null, 0, 0, 0, 0L, "");
		assertEquals(DistressLog.Change.NEW, log.hear(beacon, 2L));
		assertEquals(2, log.entries().size());
	}

	@Test
	void theDisplayShowsOneForAWhile() {
		DistressLog log = new DistressLog();
		log.hear(call(3, DistressAlert.State.ACTIVE), 0L);
		log.show(3L, 2400L);
		assertEquals(3L, log.shown(100L).alert().id());
		assertNull(log.shown(2400L), "gone after its time");
		log.show(3L, 5000L);
		log.dismiss();
		assertNull(log.shown(100L));
	}

	@Test
	void ourOwnAckMarksIt() {
		DistressLog log = new DistressLog();
		log.hear(call(8, DistressAlert.State.ACTIVE), 0L);
		log.setState(8L, DistressAlert.State.ACKNOWLEDGED, "338000001");
		assertEquals(DistressAlert.State.ACKNOWLEDGED, log.get(8L).alert().state());
		assertEquals("338000001", log.get(8L).alert().ackBy());
		log.setState(8L, DistressAlert.State.ACTIVE, "");
		assertEquals(DistressAlert.State.ACKNOWLEDGED, log.get(8L).alert().state(), "never back to active");
	}

	@Test
	void restoresWhatItSaved() {
		DistressLog log = new DistressLog();
		log.hear(call(1, DistressAlert.State.ACTIVE), 5L);
		log.hear(call(2, DistressAlert.State.CANCELLED), 6L);
		log.show(2L, 99L);
		DistressLog copy = new DistressLog();
		copy.restore(log.entries(), log.shownId(), log.shownUntil());
		assertEquals(log.entries(), copy.entries());
		assertEquals(2L, copy.shown(50L).alert().id());
	}

	/** The DISTRESS program merges its radios' logs (2026-11-09): one entry per alert, the furthest on, heard at the latest. */
	@Test
	void mergeKeepsOneEntryPerAlertAndNeverGoesBack() {
		DistressLog a = new DistressLog(), b = new DistressLog();
		a.hear(call(1, DistressAlert.State.ACTIVE), 10L);
		b.hear(call(1, DistressAlert.State.ACTIVE), 12L);
		b.hear(call(1, DistressAlert.State.ACTIVE).acknowledgedBy("338000001"), 20L);
		a.hear(call(1, DistressAlert.State.ACTIVE).at(9, 64, 9), 30L); // A missed the ack and heard a late repeat.
		DistressAlert beacon = new DistressAlert(2L, DistressAlert.Type.BEACON, DistressAlert.State.ACTIVE, "Castaway", null, 0, 64, 0, 0L, "");
		b.hear(beacon, 25L);

		List<DistressLog.Entry> merged = DistressLog.merge(List.of(a.entries(), b.entries()));
		assertEquals(2, merged.size(), "one entry per alert");
		assertEquals(1L, merged.getFirst().alert().id(), "newest heard first");
		assertEquals(DistressAlert.State.ACKNOWLEDGED, merged.getFirst().alert().state(), "the ack one radio heard wins over a late repeat");
		assertEquals(30L, merged.getFirst().heardAt(), "heard at the latest any radio heard it");
		assertEquals(2L, merged.get(1).alert().id());

		assertEquals(List.of(), DistressLog.merge(List.of()));
		DistressLog c = new DistressLog();
		c.hear(call(3, DistressAlert.State.ACTIVE), 5L);
		c.hear(call(3, DistressAlert.State.ACTIVE).at(7, 64, 7), 8L);
		DistressLog d = new DistressLog();
		d.hear(call(3, DistressAlert.State.ACTIVE), 6L);
		assertEquals(7, DistressLog.merge(List.of(d.entries(), c.entries())).getFirst().alert().x(), "same state: the latest burst's position");
	}
}
