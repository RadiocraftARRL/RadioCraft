package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Awards program's rules (computer batch 3C): every logbook contact counts. */
class AwardsTest {

	private static Awards.Contact contact(String call, String band, String mode, String biome, String dim, String grid, int distance, int hour) {
		return new Awards.Contact(call, band, Awards.modeClass(mode), biome, dim, grid, distance, hour, "2026-11-06");
	}

	private static final Awards.Universe UNIVERSE = new Awards.Universe(
			List.of("minecraft:desert", "minecraft:forest", "minecraft:nether_wastes", "minecraft:plains", "minecraft:the_end"),
			List.of("minecraft:overworld", "minecraft:the_end", "minecraft:the_nether"));

	private static Awards.Progress find(List<Awards.Progress> all, String id) {
		return all.stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
	}

	@Test
	void modesGroupAsTheAwardsCountThem() {
		assertEquals(Awards.ModeClass.VOICE, Awards.modeClass("USB"));
		assertEquals(Awards.ModeClass.VOICE, Awards.modeClass("fm"));
		assertEquals(Awards.ModeClass.VOICE, Awards.modeClass("DIG"));
		assertEquals(Awards.ModeClass.CW, Awards.modeClass("CW"));
		assertEquals(Awards.ModeClass.RTTY, Awards.modeClass("RTTY"));
		assertEquals(Awards.ModeClass.FT8, Awards.modeClass("FT8"));
		assertEquals(Awards.ModeClass.SSTV, Awards.modeClass("SSTV"));
		assertEquals(Awards.ModeClass.DATA, Awards.modeClass("PKT"));
	}

	@Test
	void everyContactCountsAndWorkedAllListsWhatIsMissing() {
		List<Awards.Contact> log = new ArrayList<>();
		log.add(contact("W1AW", "20m", "USB", "minecraft:plains", "minecraft:overworld", "JJ00", 300, 12));
		log.add(contact("K2XY", "40m", "CW", "minecraft:desert", "minecraft:overworld", "JK01", 2600, 22));
		log.add(contact("K2XY", "80m", "FT8", "minecraft:nether_wastes", "minecraft:the_nether", "", -1, 3));
		log.add(contact("N3ZZ", "", "SSTV", "", "", "", -1, -1));
		List<Awards.Progress> all = Awards.compute(log, UNIVERSE);
		Awards.Progress biomes = find(all, "biomes");
		assertEquals(3, biomes.count());
		assertEquals(List.of("minecraft:forest", "minecraft:the_end"), biomes.missing());
		assertEquals(List.of(5), biomes.tiers(), "a world of five biomes has one level: all of them");
		assertEquals(0, biomes.level());
		Awards.Progress dims = find(all, "dimensions");
		assertEquals(2, dims.count());
		assertEquals(1, dims.level());
		assertEquals(List.of("minecraft:the_end"), dims.missing());
		Awards.Progress bands = find(all, "bands");
		assertEquals(List.of("80m", "40m", "20m"), bands.worked());
		assertEquals(List.of("10m", "2m"), bands.missing());
		assertEquals(1, bands.level(), "three bands is the first level");
		Awards.Progress modes = find(all, "modes");
		assertEquals(4, modes.count());
		assertEquals(List.of("RTTY"), modes.missing());
		assertEquals(4, find(all, "contacts").count(), "unconfirmed contacts count too");
		assertEquals(3, find(all, "stations").count());
		assertEquals(2, find(all, "grids").count());
		Awards.Progress distance = find(all, "distance");
		assertEquals(2600, distance.count());
		assertEquals(3, distance.level(), "500, 1000 and 2500 reached");
		assertEquals(2, find(all, "night").count(), "22:00 and 03:00 are night, noon is not");
		assertEquals(1, find(all, "mode_cw").count());
	}

	@Test
	void levelsAndNextGoal() {
		Awards.Progress p = new Awards.Progress("contacts", 30, List.of(10, 25, 50), Awards.level(30, List.of(10, 25, 50)), List.of(), List.of());
		assertEquals(2, p.level());
		assertEquals(25, p.reached());
		assertEquals(50, p.next());
		assertFalse(p.complete());
		assertEquals(0.6D, p.fraction(), 1e-9);
		assertEquals("25 CONTACTS", Awards.levelText(p));
		Awards.Progress all = new Awards.Progress("bands", 5, List.of(3, 5), 2, List.of(), List.of());
		assertTrue(all.complete());
		assertEquals("ALL 5 BANDS", Awards.levelText(all));
		assertEquals(List.of(3, 5), Awards.tiersUpTo(5, 3, 10));
		assertEquals(List.of(1), Awards.tiersUpTo(0, 2), "an empty world still has a goal");
	}

	@Test
	void gameHourAndNight() {
		assertEquals(14, Awards.gameHour("D12 1430"));
		assertEquals(-1, Awards.gameHour(""));
		assertEquals(-1, Awards.gameHour("D1 99"));
		assertTrue(Awards.isNight(19));
		assertTrue(Awards.isNight(0));
		assertFalse(Awards.isNight(5));
		assertFalse(Awards.isNight(-1));
	}

	@Test
	void recordsKeepTheLongestPerBandAndMode() {
		List<Awards.Contact> log = List.of(contact("A1A", "20m", "USB", "", "", "", 1200, 1), contact("B2B", "20m", "CW", "", "", "", 3400, 1),
				contact("C3C", "2m", "FM", "", "", "", 100, 1), contact("D4D", "40m", "USB", "", "", "", -1, 1));
		List<Awards.Record> records = Awards.records(log);
		assertEquals("B2B", records.stream().filter(r -> r.key().equals("20m")).findFirst().orElseThrow().call());
		assertEquals("A1A", records.stream().filter(r -> r.key().equals("VOICE")).findFirst().orElseThrow().call());
		assertTrue(records.stream().noneMatch(r -> r.key().equals("40m")), "an unknown distance is no record");
	}

	@Test
	void certificateIsAWholePicture() {
		byte[] picture = AwardCertificate.render("WORKED ALL BANDS", "ALL 5 BANDS", "W1BTR", "2026-11-06", "5 SO FAR");
		assertEquals(128 * 128, picture.length);
		int yellow = 0;
		for(byte b : picture)
			if(b == PixelCanvas.YELLOW)
				yellow++;
		assertTrue(yellow > 100, "the title and the callsign are drawn in yellow");
		assertEquals(List.of("WORKED ALL", "DIMENSIONS"), AwardCertificate.wrap("WORKED ALL DIMENSIONS", 12, 2));
		assertEquals("CALL   SIGN", AwardCertificate.clean(" call ~ sign "), "what the font lacks becomes a space");
	}
}
