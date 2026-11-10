package com.arrl.radiocraft.common.contest;

import com.arrl.radiocraft.common.computer.BookPages;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The /contest scoring and report book (2026-11-09). */
class ContestReportTest {

	private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());
	private static final UUID BOB = UUID.nameUUIDFromBytes("bob".getBytes());
	private static final UUID CAROL = UUID.nameUUIDFromBytes("carol".getBytes());
	private static final UUID DAVE = UUID.nameUUIDFromBytes("dave".getBytes());
	private static final ZoneId UTC = ZoneId.of("UTC");
	/** 2026-11-09 14:00 UTC. */
	private static final long START = 1_794_232_800_000L;

	private static String name(UUID id) {
		return id.equals(ALICE) ? "Alice" : id.equals(BOB) ? "Bob" : id.equals(CAROL) ? "Carol" : "Dave";
	}

	private static ContestReport.Qso qso(UUID a, UUID b, double distance, String aBiome, String bBiome, String band, long time) {
		return new ContestReport.Qso(a, name(a), b, name(b), 14_230_000.0F, 14_230_000.0F, 64, 112, "USB", "USB", aBiome, bBiome, distance, band, time);
	}

	@Test
	void winnersAndTies() {
		List<ContestReport.Qso> log = List.of(
				qso(ALICE, BOB, 1500.0D, "minecraft:plains", "minecraft:desert", "20m", 100L),
				qso(ALICE, CAROL, 800.0D, "minecraft:forest", "minecraft:taiga", "20m", 200L),
				qso(BOB, CAROL, 1500.0D, "minecraft:desert", "minecraft:taiga", "2m", 300L),
				qso(DAVE, BOB, -1.0D, "minecraft:nether_wastes", "minecraft:desert", "20m", 400L)); // Another dimension: no distance.
		ContestReport.Score score = ContestReport.score(log);
		assertEquals(4, score.players().size());
		assertEquals(List.of("Alice", "Bob", "Carol"), score.longest().names(), "everyone with a 1,500 block contact is tied");
		assertEquals(1500.0D, score.longest().value());
		assertEquals(List.of("Bob"), score.unique().names());
		assertEquals(3.0D, score.unique().value());
		assertEquals(List.of("Alice"), score.activated().names(), "Alice operated from plains and forest");
		assertEquals(2.0D, score.activated().value());
		assertEquals(List.of("Bob"), score.reached().names(), "Bob reached plains, taiga and the nether");
		assertEquals(3.0D, score.reached().value());
		ContestReport.PlayerLog dave = score.players().get(3);
		assertEquals(-1.0D, dave.longest(), "no contact in one dimension");
	}

	@Test
	void dupesCountOnceButAreListed() {
		List<ContestReport.Qso> log = List.of(
				qso(ALICE, BOB, 100.0D, "minecraft:plains", "minecraft:plains", "20m", 0L),
				qso(BOB, ALICE, 100.0D, "minecraft:plains", "minecraft:plains", "20m", 50_000L),
				qso(ALICE, BOB, 100.0D, "minecraft:plains", "minecraft:plains", "2m", 60_000L));
		ContestReport.Score score = ContestReport.score(log);
		assertEquals(1, score.players().getFirst().worked().size());
		assertEquals(3, score.players().getFirst().contacts().size());
		String all = String.join("\n", ContestReport.build("", START, START + 3_600_000L, UTC, log).fullText());
		assertTrue(all.contains("2. Bob (dupe)"), all);
		assertFalse(all.contains("3. Bob (dupe)"), "another band is not a dupe: " + all);
	}

	@Test
	void theSameContactGoingOnIsARepeat() {
		List<ContestReport.Qso> log = new ArrayList<>(List.of(qso(ALICE, BOB, 100.0D, "", "", "20m", 1000L)));
		assertTrue(ContestReport.isRepeat(log, BOB, ALICE, "20m", 1200L), "either way round");
		assertFalse(ContestReport.isRepeat(log, ALICE, BOB, "40m", 1200L), "another band");
		assertFalse(ContestReport.isRepeat(log, ALICE, CAROL, "20m", 1200L), "another pair");
		assertFalse(ContestReport.isRepeat(log, ALICE, BOB, "20m", 1000L + ContestReport.REPEAT_TICKS), "long after: a new contact");
	}

	@Test
	void sidesAndModes() {
		ContestReport.Qso q = new ContestReport.Qso(ALICE, "Alice", BOB, "Bob", 146_520_000.0F, 146_550_000.0F, 64, 200, "USB", "FM", "a", "b", 10.0D, "2m", 0L);
		assertEquals("USB/FM", q.sideA().mode());
		assertEquals("FM/USB", q.sideB().mode());
		assertEquals(200, q.sideB().myY());
		assertEquals(146_550_000.0F, q.sideB().heardHz());
		assertEquals("FT8", new ContestReport.Side(ALICE, "", BOB, "", 0.0F, 0, 0, "FT8", "FT8", "", "", 0.0D, "").mode());
		assertEquals("LSB", new ContestReport.Side(ALICE, "", BOB, "", 0.0F, 0, 0, "", "LSB", "", "", 0.0D, "").mode());
	}

	@Test
	void theBook() {
		List<ContestReport.Qso> log = List.of(
				qso(ALICE, BOB, 12_345.0D, "minecraft:plains", "minecraft:desert", "20m", 100L),
				qso(ALICE, CAROL, 800.0D, "minecraft:forest", "minecraft:taiga", "20m", 200L));
		ContestReport.Report report = ContestReport.build("Field Day", START, START + 2 * 3_600_000L + 7 * 60_000L, UTC, log);
		String first = report.pages().getFirst();
		assertTrue(first.startsWith("Contest Report\nField Day"), first);
		assertTrue(first.contains("2026-11-09 14:00") && first.contains("2026-11-09 16:07") && first.contains("Lasted 2h 07m"), first);
		assertTrue(first.contains("2 contacts") && first.contains("3 stations"), first);
		String winners = report.pages().get(1);
		assertTrue(winners.startsWith("Winners"), winners);
		assertTrue(winners.contains("Longest contact\n Alice\n Bob\n 12,345 blocks\n (12.3 km)"), winners);
		assertTrue(winners.contains("Unique contacts\n Alice\n 2 stations"), winners);
		assertTrue(String.join("\n", report.pages()).contains("Biomes activated\n Alice\n 2 biomes"), "on the next page");
		int at = 0;
		while(at < report.pages().size() && !report.pages().get(at).startsWith("Alice\n"))
			at++;
		String alice = report.pages().get(at);
		assertTrue(alice.startsWith("Alice\n2 contacts\n2 unique\nFrom 2 biomes\nReached 2 biomes\nBest 12,345 blocks"), alice);
		assertTrue(alice.contains("1. Bob\n 14.230 MHz USB\n Y64 to Y112"), alice);
		assertTrue(report.pages().get(at + 1).startsWith("Bob\n"), "each player starts a page");
		assertEquals(2, report.contacts());
		assertEquals(3, report.stations());
		assertFalse(report.cutShort());
		for(String page : report.pages()) {
			String[] lines = page.split("\n");
			assertTrue(lines.length <= BookPages.PAGE_LINES, "too many lines: " + page);
			for(String line : lines)
				assertTrue(line.length() <= BookPages.LINE_CHARS, "too long: '" + line + "'");
		}
		assertTrue(String.join("\n", report.summary()).contains("Longest contact"), "the log's summary has the winners");
	}

	@Test
	void nobodyMadeAContact() {
		ContestReport.Report report = ContestReport.build("", START, START + 30_000L, UTC, List.of());
		assertEquals(1, report.pages().size());
		assertTrue(report.pages().getFirst().contains("No contacts were\nmade during this\ncontest."),report.pages().getFirst());
		assertTrue(report.pages().getFirst().contains("Lasted under 1m"));
		assertEquals(0, report.contacts());
	}

	@Test
	void aHugeContestIsCutToOneBook() {
		List<ContestReport.Qso> log = new ArrayList<>();
		for(int i = 0; i < 600; i++)
			log.add(qso(ALICE, BOB, 10.0D, "", "", "20m", i * 20_000L));
		ContestReport.Report report = ContestReport.build("", START, START + 60_000L, UTC, log);
		assertTrue(report.cutShort());
		assertEquals(BookPages.MAX_PAGES, report.pages().size());
		assertTrue(report.pages().getLast().startsWith("The report goes on"), report.pages().getLast());
		assertTrue(report.fullText().contains("600. Bob (dupe)"), "the full text has everything");
	}

	@Test
	void longLinesWrap() {
		List<String> out = new ArrayList<>();
		ContestReport.wrap("No contacts were made during this contest.", out);
		for(String line : out)
			assertTrue(line.length() <= BookPages.LINE_CHARS, line);
		assertEquals("No contacts were", out.getFirst());
	}
}
