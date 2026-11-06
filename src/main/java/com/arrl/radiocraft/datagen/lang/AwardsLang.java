package com.arrl.radiocraft.datagen.lang;

import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.function.BiConsumer;

/**
 * The Awards tracker (computer batch 3C): keys under {@code screen.radiocraft.computer.awards.}, its errors, and the
 * logbook's new grid square field.
 */
public final class AwardsLang {

	private AwardsLang() {}

	public static void add(LanguageProvider provider) {
		BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("app.awards", "Awards");
		s.accept("app.awards.description", "Operating awards from your logbook, every contact counting, with printed certificates.");
		s.accept("awards.title", "Awards - %s");
		s.accept("awards.keys", "Enter=Progress  F3=Other log  F4=Reread  F5=Print certificate  F6=Records  Esc=Leave");
		s.accept("awards.detail_keys", "Up/Down=Scroll  Esc=Back");
		s.accept("awards.summary", "Contacts in the log: %s (every contact counts, no confirmation needed)");
		s.accept("awards.hint", "Levels: * reached, . to go. Certificates print on paper, an empty map or photo paper.");
		s.accept("awards.col.award", "AWARD");
		s.accept("awards.col.level", "LEVEL");
		s.accept("awards.col.count", "COUNT");
		s.accept("awards.col.next", "TO THE NEXT LEVEL");
		s.accept("awards.col.key", "BAND/MODE");
		s.accept("awards.col.call", "CALL");
		s.accept("awards.col.blocks", "BLOCKS");
		s.accept("awards.col.date", "DATE");
		s.accept("awards.done", "%s - DONE");
		s.accept("awards.levels", "Levels: %s");
		s.accept("awards.worked", "Worked (%s):");
		s.accept("awards.missing", "Still to work (%s):");
		s.accept("awards.detail_title", "%s - %s");
		s.accept("awards.records_title", "Distance records: the longest contact per band and per mode");
		s.accept("awards.no_records", "No contact with a known distance yet (it needs a grid square or a station heard on a radio).");
		s.accept("awards.not_yet", "No level of that award reached yet");
		s.accept("awards.printed", "Certificate printed: %s");
		s.accept("awards.open_title", "Read a log");
		String[][] awards = {
				{ "biomes", "Worked All Biomes", "Contacts with stations in every biome the world has. A station heard on a radio is placed by its grid square; a typed or FT8 grid by its centre." },
				{ "dimensions", "Worked All Dimensions", "Contacts made from or with every dimension: the Overworld, the Nether, the End (and any a mod adds)." },
				{ "bands", "Worked All Bands", "A contact on every ham band: 80, 40, 20 and 10 meters, and 2 meters." },
				{ "modes", "Worked All Modes", "A contact in each mode: phone, CW, RTTY, FT8 and SSTV." },
				{ "contacts", "Contact Ladder", "Contacts in the log, any band, any mode." },
				{ "stations", "Stations Worked", "Different callsigns worked." },
				{ "grids", "Grid Chaser", "Different grid squares worked (256 blocks each)." },
				{ "distance", "Long Haul", "The longest contact, in blocks (to the nearest 100)." },
				{ "night", "Night Owl", "Contacts made at night, game time (19:00 to 05:00), when the mobs are out." },
				{ "mode_voice", "Phone Operator", "Contacts on phone: SSB, FM, AM and digital voice." },
				{ "mode_cw", "CW Operator", "Contacts in Morse code." },
				{ "mode_rtty", "RTTY Operator", "Contacts on RTTY." },
				{ "mode_ft8", "FT8 Operator", "Contacts on FT8." },
				{ "mode_sstv", "SSTV Operator", "Contacts on SSTV." } };
		for(String[] award : awards) {
			s.accept("awards." + award[0], award[1]);
			s.accept("awards.about." + award[0], award[2]);
		}
		s.accept("err.awards_not_a_log", "That is not a log file (.LOG)");
		s.accept("err.awards_not_earned", "No level of that award reached yet");
		// The logbook's grid square (computer batch 3C).
		s.accept("logbook.grid", "Grid square:");
		s.accept("logbook.col.grid", "GRID");
		s.accept("logbook.bad_grid", "Not a grid square (four characters, JJ00)");
	}
}
