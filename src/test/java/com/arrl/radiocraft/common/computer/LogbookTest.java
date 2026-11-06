package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The station logbook (1.12 step 6): contacts in time order, its file, ADIF out, reports and UTC times. */
class LogbookTest {

	private static final long NOV_4_1430 = Logbook.parseUtc("20261104", "1430");

	private static Logbook.Entry qso(long utc, String call) {
		return new Logbook.Entry(utc, "D12 0800", call, 14.225D, "USB", "59", "57", "Op Bob");
	}

	@Test
	void readsUtcDatesAndTimes() {
		assertEquals(1793802600000L, NOV_4_1430, "2026-11-04 14:30 UTC");
		assertEquals(NOV_4_1430, Logbook.parseUtc("2026-11-04", "14:30"));
		assertEquals("20261104", Logbook.utcDate(NOV_4_1430));
		assertEquals("1430", Logbook.utcTime(NOV_4_1430));
		assertEquals(-1L, Logbook.parseUtc("20261332", "1430"), "no 13th month");
		assertEquals(-1L, Logbook.parseUtc("20261104", "2561"));
		assertEquals(-1L, Logbook.parseUtc("tomorrow", "1430"));
	}

	@Test
	void keepsContactsInTimeOrderAndCleansThem() {
		Logbook log = new Logbook();
		log.add(qso(NOV_4_1430 + 60_000L, "w1aw"));
		log.add(qso(NOV_4_1430, "K1ABC"));
		assertEquals("K1ABC", log.get(0).call(), "the earlier contact first");
		assertEquals("W1AW", log.get(1).call(), "calls are upper case");
		assertEquals(1, log.countWith("w1aw"));
		log.set(0, qso(NOV_4_1430 + 120_000L, "K1ABC"));
		assertEquals("K1ABC", log.get(1).call(), "an edit that moves the time moves the contact");
		Logbook.Entry odd = new Logbook.Entry(0L, "", "W1<X>", -5.0D, "usb", "5 9", "", "a\tb");
		assertEquals("W1X", odd.call());
		assertEquals(0.0D, odd.frequencyMhz());
		assertEquals("USB", odd.mode());
		assertEquals("59", odd.rstSent());
		assertEquals("ab", odd.notes());
	}

	@Test
	void savesAndLoads() {
		Logbook log = new Logbook();
		log.add(qso(NOV_4_1430, "W1AW"));
		Logbook back = Logbook.fromTag(ComputerFileFormats.nbt(ComputerFileFormats.nbt(log.toTag())));
		assertEquals(log.entries(), back.entries());
	}

	@Test
	void writesAdif() {
		Logbook log = new Logbook();
		log.add(qso(NOV_4_1430, "W1AW"));
		String adif = log.adif("1.12");
		assertTrue(adif.contains("<ADIF_VER:5>3.1.4"), adif);
		assertTrue(adif.contains("<EOH>"), adif);
		assertTrue(adif.contains("<CALL:4>W1AW "), adif);
		assertTrue(adif.contains("<QSO_DATE:8>20261104 "), adif);
		assertTrue(adif.contains("<TIME_ON:4>1430 "), adif);
		assertTrue(adif.contains("<FREQ:9>14.225000 "), adif);
		assertTrue(adif.contains("<BAND:3>20m "), adif);
		assertTrue(adif.contains("<MODE:3>SSB <SUBMODE:3>USB "), adif);
		assertTrue(adif.contains("<RST_SENT:2>59 <RST_RCVD:2>57 "), adif);
		assertTrue(adif.contains("<COMMENT:22>Op Bob (game D12 0800) "), adif);
		assertTrue(adif.trim().endsWith("<EOR>"), adif);
		assertEquals("DIGITALVOICE", Logbook.adifMode("DIG")[0]);
		assertEquals("", Logbook.adifBand(155.0D), "commercial bands have no ADIF band");
		assertEquals("2m", Logbook.adifBand(146.52D));
	}

	@Test
	void reportsFromWhatTheRadioHeard() {
		assertEquals("59", Logbook.rst(1.0D, 0.1D, false));
		assertEquals("599", Logbook.rst(1.0D, 0.1D, true));
		assertEquals("31", Logbook.rst(0.1D, 0.1D, false), "barely above the noise");
		assertEquals("15", Logbook.rst(0.55D, 2.0D, false), "strong but buried");
	}

	/**
	 * Computer batch 3C: where the other station was is kept coarsely only, never as coordinates (a log is a client
	 * file): a grid square, dimension, biome and a distance rounded to 100 blocks.
	 */
	@Test
	void whereIsCoarseAndCarriesNoCoordinates() {
		Logbook.Where where = new Logbook.Where("jj00", "minecraft:overworld", "minecraft:plains", 1234);
		assertEquals("JJ00", where.grid());
		assertEquals(1200, where.distance(), "rounded to 100 blocks");
		net.minecraft.nbt.CompoundTag tag = where.toTag();
		assertEquals(java.util.Set.of("grid", "dim", "biome", "dist"), tag.getAllKeys(), "no x or z ever");
		assertEquals(where, Logbook.Where.fromTag(tag));
		assertEquals("", new Logbook.Where("RR73", "", "", -1).grid(), "RR73 is no grid");
		assertEquals("", new Logbook.Where("", "Not An Id!", "", -1).dimension());
		Logbook.Entry entry = new Logbook.Entry(0L, "", "W1AW", 14.2D, "USB", "59", "59", "", where);
		assertEquals(where, Logbook.Entry.fromTag(entry.toTag()).where());
		assertEquals(Logbook.Where.UNKNOWN, Logbook.Entry.fromTag(new Logbook.Entry(0L, "", "W1AW", 14.2D, "USB", "59", "59", "").toTag()).where());
	}
}
