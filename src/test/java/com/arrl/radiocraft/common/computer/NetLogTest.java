package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Net Control logger's list (computer batch 3C): check-ins, rechecks, roll call, the report and the logbook. */
class NetLogTest {

	private static final long T0 = 1_793_966_400_000L; // 2026-11-06 12:00 UTC.

	private static NetLog net() {
		NetLog net = new NetLog();
		net.setup("Tuesday Net", "147.180", "fm", "w1btr");
		net.open(T0);
		return net;
	}

	@Test
	void checkInRecheckAndBackIn() {
		NetLog net = net();
		assertEquals(NetLog.Result.ADDED, net.checkIn("k2xyz", "Bob", "JJ00", false, false, T0 + 60_000L));
		assertEquals(NetLog.Result.RECHECKED, net.checkIn("K2XYZ", "", "", true, false, T0 + 120_000L));
		NetLog.CheckIn bob = net.get(0);
		assertEquals("Bob", bob.name(), "a recheck keeps what was known");
		assertTrue(bob.traffic(), "a recheck can raise a flag");
		assertEquals(1, bob.rechecks());
		net.toggleOut(0, T0 + 180_000L);
		assertFalse(net.get(0).in());
		assertEquals(NetLog.Result.BACK_IN, net.checkIn("K2XYZ", "", "", false, false, T0 + 240_000L));
		assertTrue(net.get(0).in());
		assertEquals(NetLog.Result.NO_CALL, net.checkIn("  ", "", "", false, false, T0));
		assertEquals(1, net.size());
	}

	@Test
	void rollCallTakesShortTimeThenTrafficThenTheRest() {
		NetLog net = net();
		net.checkIn("A1A", "", "", false, false, T0);
		net.checkIn("B2B", "", "", true, false, T0);
		net.checkIn("C3C", "", "", false, true, T0);
		net.checkIn("D4D", "", "", false, false, T0);
		net.toggleOut(3, T0 + 1000L);
		assertEquals(List.of(2, 1, 0), net.rollCall(), "D4D checked out; C3C short time first, then B2B with traffic");
		assertEquals(2, net.nextInRollCall(-1));
		assertEquals(1, net.nextInRollCall(2));
		assertEquals(2, net.nextInRollCall(0), "it wraps round");
		assertEquals(1, net.move(0, 1));
		assertEquals("B2B", net.get(0).call());
		assertEquals(0, net.move(0, -1), "the top cannot go higher");
	}

	@Test
	void closingChecksEveryoneOutAndTheReportSaysSo() {
		NetLog net = net();
		net.checkIn("K2XYZ", "Bob", "Lake town", true, false, T0 + 60_000L);
		net.close(T0 + 30L * 60_000L);
		assertFalse(net.isOpen());
		assertFalse(net.get(0).in());
		List<String> report = net.summary();
		assertEquals("NET REPORT: TUESDAY NET", report.get(0));
		assertTrue(report.get(1).contains("147.180 FM") && report.get(1).contains("NCS W1BTR"), report.get(1));
		assertTrue(report.get(2).contains("30 MIN"), report.get(2));
		assertTrue(report.stream().anyMatch(l -> l.contains("K2XYZ") && l.contains("1201-1230") && l.contains("TFC") && l.contains("Bob, Lake town")), report.toString());
	}

	@Test
	void checkInsBecomeLogbookContacts() {
		NetLog net = net();
		net.checkIn("K2XYZ", "Bob", "jk01", false, false, T0 + 60_000L);
		net.checkIn("N3ZZ", "", "Hilltop", false, false, T0 + 120_000L);
		List<Logbook.Entry> contacts = net.asContacts("D3 1200");
		assertEquals(2, contacts.size());
		Logbook.Entry bob = contacts.get(0);
		assertEquals("K2XYZ", bob.call());
		assertEquals(147.18D, bob.frequencyMhz(), 1e-9);
		assertEquals("FM", bob.mode());
		assertEquals("59", bob.rstSent());
		assertEquals("JK01", bob.where().grid(), "a grid typed as the location goes in as the grid");
		assertTrue(bob.notes().startsWith("NET Tuesday Net"), bob.notes());
		assertEquals("", contacts.get(1).where().grid());
	}

	@Test
	void fileRoundTrip() {
		NetLog net = net();
		net.checkIn("K2XYZ", "Bob", "JJ00", true, true, T0);
		net.recheck(0);
		NetLog back = NetLog.fromTag(net.toTag());
		assertEquals(net.name(), back.name());
		assertEquals(net.ncs(), back.ncs());
		assertEquals(net.checkIns(), back.checkIns());
		assertTrue(back.isOpen());
	}
}
