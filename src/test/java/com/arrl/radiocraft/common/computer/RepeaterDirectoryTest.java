package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeaterDirectoryTest {

	private static RepeaterDirectory.Heard heard(String call, double out, int offset, float tone, int x, int z, long time) {
		return new RepeaterDirectory.Heard(call, out, offset, tone, "minecraft:overworld", x, 64, z, time);
	}

	@Test
	void hearingAgainUpdatesButKeepsTheNotes() {
		RepeaterDirectory directory = new RepeaterDirectory();
		assertTrue(directory.merge(heard("W1RPT", 146.94D, -600, 100.0F, 10, 20, 100L)));
		RepeaterDirectory.Entry first = directory.get(0);
		directory.set(0, new RepeaterDirectory.Entry(first.callsign(), "Club machine", first.outputMhz(), first.offsetKhz(), first.tone(), true,
				first.dimension(), first.x(), first.y(), first.z(), first.source(), first.firstHeard(), first.lastHeard()));
		assertTrue(directory.merge(heard("W1RPT", 146.94D, -600, 123.0F, 10, 20, 500L)));
		assertEquals(1, directory.size());
		RepeaterDirectory.Entry entry = directory.get(0);
		assertEquals("Club machine", entry.notes());
		assertEquals(123.0F, entry.tone());
		assertEquals(500L, entry.lastHeard());
		assertEquals(100L, entry.firstHeard());
		assertFalse(directory.merge(heard("W1RPT", 146.94D, -600, 123.0F, 10, 20, 500L)), "nothing new");
	}

	@Test
	void aTypedInRepeaterIsFoundByCallsignAndOutput() {
		RepeaterDirectory directory = new RepeaterDirectory();
		directory.add(RepeaterDirectory.Entry.manual("w1abc", "Hilltop", 147.06D, 600, 0.0F, false, 0, 0));
		assertEquals(RepeaterDirectory.Source.MANUAL, directory.get(0).source());
		assertFalse(directory.get(0).wasHeard());
		directory.merge(heard("W1ABC", 147.06D, 600, 0.0F, 300, -40, 77L));
		assertEquals(1, directory.size());
		assertEquals("Hilltop", directory.get(0).notes());
		assertTrue(directory.get(0).hasPos());
		assertEquals(RepeaterDirectory.Source.HEARD, directory.get(0).source());
		directory.merge(heard("W1ABC", 444.0D, 5000, 0.0F, 900, 900, 80L)); // Same call, another machine.
		assertEquals(2, directory.size());
		directory.remove(1);
		assertEquals(1, directory.size());
	}

	@Test
	void sortsByDistanceFrequencyAndHeard() {
		RepeaterDirectory directory = new RepeaterDirectory();
		directory.merge(heard("FAR", 146.61D, -600, 0.0F, 1000, 0, 50L));
		directory.merge(heard("NEAR", 147.30D, 600, 0.0F, 10, 0, 10L));
		directory.add(RepeaterDirectory.Entry.manual("NOWHERE", "", 145.11D, -600, 0.0F, false, 0, 0));
		assertEquals(List.of(1, 0, 2), directory.order(RepeaterDirectory.Sort.DISTANCE, 0.0D, 0.0D));
		assertEquals(List.of(2, 0, 1), directory.order(RepeaterDirectory.Sort.FREQUENCY, 0.0D, 0.0D));
		assertEquals(List.of(0, 1, 2), directory.order(RepeaterDirectory.Sort.HEARD, 0.0D, 0.0D));
		assertEquals(RepeaterDirectory.Sort.FREQUENCY, RepeaterDirectory.Sort.DISTANCE.next());
		assertEquals(RepeaterDirectory.Sort.DISTANCE, RepeaterDirectory.Sort.HEARD.next());
	}

	@Test
	void distanceAndBearing() {
		RepeaterDirectory.Entry east = RepeaterDirectory.Entry.manual("E", "", 146.94D, -600, 0.0F, true, 99, -1);
		assertEquals(90.0D, east.bearingFrom(0.5D, -0.5D), 1e-6);
		assertEquals(99.0D, east.distanceFrom(0.5D, -0.5D), 1e-6);
		RepeaterDirectory.Entry north = RepeaterDirectory.Entry.manual("N", "", 146.94D, -600, 0.0F, true, 0, -50);
		assertEquals(0.0D, north.bearingFrom(0.5D, 0.5D), 1e-6);
		RepeaterDirectory.Entry unknown = RepeaterDirectory.Entry.manual("U", "", 146.94D, -600, 0.0F, false, 0, 0);
		assertEquals(-1.0D, unknown.distanceFrom(0.0D, 0.0D));
	}

	@Test
	void offsetsKeepTheirSign() {
		assertEquals("-0.600", RepeaterDirectory.offsetText(-600));
		assertEquals("+5.000", RepeaterDirectory.offsetText(5000));
		assertEquals("0.000", RepeaterDirectory.offsetText(0));
		assertEquals(-600, RepeaterDirectory.parseOffsetKhz("-600"));
		assertEquals(-600, RepeaterDirectory.parseOffsetKhz("-0.6"));
		assertEquals(5000, RepeaterDirectory.parseOffsetKhz("+5.0"));
		assertEquals(0, RepeaterDirectory.parseOffsetKhz(""));
		assertNull(RepeaterDirectory.parseOffsetKhz("abc"));
		RepeaterDirectory.Entry entry = RepeaterDirectory.Entry.manual("W1X", "", 146.94D, -600, 0.0F, false, 0, 0);
		assertEquals(146.34D, entry.inputMhz(), 1e-9);
	}

	@Test
	void exportsAsChannelsAndSkipsDuplicates() {
		RepeaterDirectory.Entry club = RepeaterDirectory.Entry.manual("W1CLUB", "Club repeater", 146.94D, -600, 100.0F, false, 0, 0);
		RepeaterDirectory.Entry plain = RepeaterDirectory.Entry.manual("", "", 442.10D, 5000, 0.0F, false, 0, 0);
		FrequencyList list = new FrequencyList();
		list.add(FrequencyList.Entry.simplex("CALL", 146.52D, FrequencyList.Mode.FM));
		RepeaterDirectory.ExportResult result = RepeaterDirectory.exportInto(list, List.of(club, plain));
		assertEquals(2, result.added());
		assertEquals(3, list.size());
		FrequencyList.Entry channel = list.get(1);
		assertEquals("W1CLUB", channel.name());
		assertEquals(146.94D, channel.rxMhz(), 1e-9);
		assertEquals(146.34D, channel.txMhz(), 1e-9);
		assertEquals(-0.6D, channel.offsetMhz(), 1e-9);
		assertEquals(100.0F, channel.tone());
		assertEquals(FrequencyList.Mode.FM, channel.mode());
		assertEquals("Club repeater", channel.notes());
		assertEquals("RPT 442.100", list.get(2).name());
		RepeaterDirectory.ExportResult again = RepeaterDirectory.exportInto(list, List.of(club));
		assertEquals(0, again.added());
		assertEquals(1, again.duplicates());
	}

	@Test
	void saveAndLoad() {
		RepeaterDirectory directory = new RepeaterDirectory();
		directory.merge(heard("W1RPT", 146.94D, -600, 100.0F, 10, 20, 100L));
		directory.add(RepeaterDirectory.Entry.manual("K1MAN", "Typed", 224.94D, -1600, 0.0F, true, -5, 7));
		RepeaterDirectory loaded = RepeaterDirectory.fromTag(directory.toTag());
		assertEquals(directory.entries(), loaded.entries());
		assertEquals(0, RepeaterDirectory.fromTag(null).size());
	}

	@Test
	void fullDropsTheOldestHeard() {
		RepeaterDirectory directory = new RepeaterDirectory();
		for(int i = 0; i < RepeaterDirectory.MAX_ENTRIES; i++)
			directory.merge(heard("R" + i, 146.0D + i * 0.001D, 0, 0.0F, i, 0, 1000L + i));
		assertEquals(RepeaterDirectory.MAX_ENTRIES, directory.size());
		assertTrue(directory.add(RepeaterDirectory.Entry.manual("NEW", "", 440.0D, 5000, 0.0F, false, 0, 0)));
		assertEquals(RepeaterDirectory.MAX_ENTRIES, directory.size());
		assertTrue(directory.entries().stream().noneMatch(e -> e.callsign().equals("R0")), "the oldest heard went");
	}
}
