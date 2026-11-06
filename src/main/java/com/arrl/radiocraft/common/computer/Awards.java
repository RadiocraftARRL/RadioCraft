package com.arrl.radiocraft.common.computer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The Awards program's rules (computer batch 3C): Minecraft-flavoured operating awards worked out from a logbook.
 * <b>Every contact in the log counts</b>; nothing needs confirming (the maintainer's decision: a paper QSL is easy to
 * fake, so the awards trust the log). Each award has levels (its tiers); the progress pages show what has been worked
 * and what is still missing. Pure (no game classes), so it is unit tested; the server side resolves each logbook entry
 * into a {@link Contact} first (its biome and dimension, its distance), see {@code AwardsHandler}.
 * <p>
 * The awards: Worked All Biomes, Worked All Dimensions, Worked All Bands, Worked All Modes, the contact ladder,
 * stations worked, grid squares, the longest contact, contacts made at night (game time), and a ladder for each mode
 * (phone, CW, RTTY, FT8, SSTV). {@link #records} keeps the longest contact for each band and each mode.
 */
public final class Awards {

	/** The ham bands a radio can work, as {@link Logbook#adifBand} names them: Worked All Bands needs every one. */
	public static final List<String> HAM_BANDS = List.of("80m", "40m", "20m", "10m", "2m");
	/** The modes Worked All Modes needs, as {@link ModeClass} names. */
	public static final List<ModeClass> AWARD_MODES = List.of(ModeClass.VOICE, ModeClass.CW, ModeClass.RTTY, ModeClass.FT8, ModeClass.SSTV);
	/** Most names a progress page lists as worked or missing. */
	public static final int LIST_MAX = 400;
	/** The game clock's night ("D3 1900" to "D4 0459"; the clock starts at 06:00), when hostile mobs are out. */
	public static final int NIGHT_FROM = 19, NIGHT_TO = 5;

	private Awards() {}

	/** What kind of operating a logged mode is. */
	public enum ModeClass {
		VOICE, CW, RTTY, FT8, SSTV, DATA;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** The class of a mode as the logbook keeps it ("USB", "CW", "FT8"...). */
	public static ModeClass modeClass(String mode) {
		String m = mode == null ? "" : mode.strip().toUpperCase(Locale.ROOT);
		return switch(m) {
			case "USB", "LSB", "SSB", "FM", "AM", "DIG", "P25", "DIGITALVOICE", "PHONE", "" -> ModeClass.VOICE;
			case "CW" -> ModeClass.CW;
			case "RTTY" -> ModeClass.RTTY;
			case "FT8", "FT4" -> ModeClass.FT8;
			case "SSTV" -> ModeClass.SSTV;
			default -> ModeClass.DATA;
		};
	}

	/**
	 * One contact as the awards see it.
	 *
	 * @param call      the other station
	 * @param band      its ADIF band ("20m"), "" if none
	 * @param mode      the mode's class
	 * @param biome     the biome the other station was in, "" if unknown
	 * @param dimension the dimension it was in, "" if unknown
	 * @param grid      its grid square, "" if unknown
	 * @param distance  blocks to it, -1 if unknown
	 * @param gameHour  the game clock's hour it was made (0-23), -1 if unknown
	 * @param date      the UTC date ("2026-11-06"), for the records
	 */
	public record Contact(String call, String band, ModeClass mode, String biome, String dimension, String grid, int distance, int gameHour, String date) {}

	/**
	 * What there is to work: every biome ids the world's dimensions can generate, and every dimension. Worked All
	 * Biomes and Worked All Dimensions count only these, and list the rest as missing.
	 */
	public record Universe(List<String> biomes, List<String> dimensions) {}

	/**
	 * An award's standing.
	 *
	 * @param id      the award ({@code screen.radiocraft.computer.awards.<id>} names it)
	 * @param count   how far along it is (contacts, biomes, blocks...)
	 * @param tiers   the levels, lowest first
	 * @param level   how many levels are reached (0 = none yet)
	 * @param worked  what was worked (names, sorted), for the progress page
	 * @param missing what is left (Worked All lists only), for the progress page
	 */
	public record Progress(String id, long count, List<Integer> tiers, int level, List<String> worked, List<String> missing) {

		/** The level reached's goal, or 0 if none. */
		public int reached() {
			return level == 0 ? 0 : tiers.get(level - 1);
		}

		/** The next goal, or the top one when all are reached. */
		public int next() {
			return tiers.get(Math.min(level, tiers.size() - 1));
		}

		public boolean complete() {
			return level >= tiers.size();
		}

		/** How far to the next goal, 0 to 1. */
		public double fraction() {
			return complete() ? 1.0D : Math.min(1.0D, count / (double)Math.max(1, next()));
		}
	}

	/** The longest contact on a band or in a mode. */
	public record Record(String key, String call, int distance, String date) {}

	// -------------------- THE AWARDS --------------------

	/** Every award's standing for {@code contacts}, in the order the program lists them. */
	public static List<Progress> compute(List<Contact> contacts, Universe universe) {
		List<Progress> out = new ArrayList<>();
		Set<String> allBiomes = new LinkedHashSet<>(universe.biomes());
		Set<String> biomes = distinct(contacts, Contact::biome);
		biomes.retainAll(allBiomes);
		int biomeCount = allBiomes.size();
		out.add(progress("biomes", biomes.size(), tiersUpTo(biomeCount, 5, 10, 25, 40), biomes, missing(allBiomes, biomes)));

		Set<String> allDims = new LinkedHashSet<>(universe.dimensions());
		Set<String> dims = distinct(contacts, Contact::dimension);
		dims.retainAll(allDims);
		out.add(progress("dimensions", dims.size(), tiersUpTo(allDims.size(), 2), dims, missing(allDims, dims)));

		Set<String> bands = distinct(contacts, Contact::band);
		bands.retainAll(HAM_BANDS);
		out.add(progress("bands", bands.size(), tiersUpTo(HAM_BANDS.size(), 3), ordered(HAM_BANDS, bands), missing(new LinkedHashSet<>(HAM_BANDS), bands)));

		List<String> modeNames = AWARD_MODES.stream().map(ModeClass::name).toList();
		Set<String> modes = distinct(contacts, contact -> contact.mode().name());
		modes.retainAll(modeNames);
		out.add(progress("modes", modes.size(), tiersUpTo(modeNames.size(), 3), ordered(modeNames, modes), missing(new LinkedHashSet<>(modeNames), modes)));

		out.add(progress("contacts", contacts.size(), List.of(10, 25, 50, 100, 250, 500, 1000, 2000), Set.of(), List.of()));

		Set<String> calls = distinct(contacts, Contact::call);
		out.add(progress("stations", calls.size(), List.of(10, 25, 50, 100, 250), calls, List.of()));

		Set<String> grids = distinct(contacts, Contact::grid);
		out.add(progress("grids", grids.size(), List.of(5, 10, 25, 50, 100), grids, List.of()));

		int longest = 0;
		List<String> farthest = new ArrayList<>();
		List<Contact> byDistance = contacts.stream().filter(c -> c.distance() >= 0).sorted(Comparator.comparingInt(Contact::distance).reversed()).toList();
		for(Contact contact : byDistance) {
			longest = Math.max(longest, contact.distance());
			if(farthest.size() < 10)
				farthest.add(contact.call() + " " + contact.distance());
		}
		out.add(new Progress("distance", longest, List.of(500, 1000, 2500, 5000, 10000), level(longest, List.of(500, 1000, 2500, 5000, 10000)), farthest, List.of()));

		long night = contacts.stream().filter(c -> isNight(c.gameHour())).count();
		out.add(progress("night", night, List.of(10, 50, 100, 250), Set.of(), List.of()));

		for(ModeClass mode : AWARD_MODES) {
			long count = contacts.stream().filter(c -> c.mode() == mode).count();
			out.add(progress("mode_" + mode.id(), count, List.of(10, 50, 100, 500), Set.of(), List.of()));
		}
		return out;
	}

	/** True for an hour of the game's night ({@link #NIGHT_FROM} to {@link #NIGHT_TO}). */
	public static boolean isNight(int hour) {
		return hour >= 0 && (hour >= NIGHT_FROM || hour < NIGHT_TO);
	}

	/** The game clock's hour of a logbook game time ("D12 1430" is 14), or -1. */
	public static int gameHour(String gameTime) {
		if(gameTime == null)
			return -1;
		String t = gameTime.strip();
		int space = t.lastIndexOf(' ');
		String clock = space < 0 ? t : t.substring(space + 1);
		if(clock.length() != 4)
			return -1;
		try {
			int hour = Integer.parseInt(clock.substring(0, 2));
			return hour >= 0 && hour < 24 ? hour : -1;
		}
		catch(NumberFormatException e) {
			return -1;
		}
	}

	/**
	 * The longest contact on each band ({@link #HAM_BANDS} order, then any other) and in each mode, of the contacts
	 * whose distance is known. Keys are the band ("20m") or the mode class ("FT8").
	 */
	public static List<Record> records(List<Contact> contacts) {
		Map<String, Record> bands = new LinkedHashMap<>();
		Map<String, Record> modes = new LinkedHashMap<>();
		for(String band : HAM_BANDS)
			bands.put(band, null);
		for(ModeClass mode : ModeClass.values())
			modes.put(mode.name(), null);
		for(Contact contact : contacts) {
			if(contact.distance() < 0)
				continue;
			Record record = new Record("", contact.call(), contact.distance(), contact.date());
			if(!contact.band().isEmpty())
				bands.merge(contact.band(), withKey(record, contact.band()), Awards::longer);
			modes.merge(contact.mode().name(), withKey(record, contact.mode().name()), Awards::longer);
		}
		List<Record> out = new ArrayList<>();
		bands.values().stream().filter(java.util.Objects::nonNull).forEach(out::add);
		modes.values().stream().filter(java.util.Objects::nonNull).forEach(out::add);
		return out;
	}

	private static Record withKey(Record record, String key) {
		return new Record(key, record.call(), record.distance(), record.date());
	}

	private static Record longer(Record a, Record b) {
		if(a == null)
			return b;
		return b.distance() > a.distance() ? b : a;
	}

	// -------------------- CERTIFICATES --------------------

	/** An award's name as a certificate prints it (in English: a print is made on the server). */
	public static String title(String id) {
		return switch(id) {
			case "biomes" -> "WORKED ALL BIOMES";
			case "dimensions" -> "WORKED ALL DIMENSIONS";
			case "bands" -> "WORKED ALL BANDS";
			case "modes" -> "WORKED ALL MODES";
			case "contacts" -> "CONTACT LADDER";
			case "stations" -> "STATIONS WORKED";
			case "grids" -> "GRID CHASER";
			case "distance" -> "LONG HAUL";
			case "night" -> "NIGHT OWL";
			case "mode_voice" -> "PHONE OPERATOR";
			case "mode_cw" -> "CW OPERATOR";
			case "mode_rtty" -> "RTTY OPERATOR";
			case "mode_ft8" -> "FT8 OPERATOR";
			case "mode_sstv" -> "SSTV OPERATOR";
			default -> id.toUpperCase(Locale.ROOT);
		};
	}

	/** What an award counts, as a certificate prints it ("BIOMES"). */
	public static String unit(String id) {
		return switch(id) {
			case "biomes" -> "BIOMES";
			case "dimensions" -> "DIMENSIONS";
			case "bands" -> "BANDS";
			case "modes" -> "MODES";
			case "stations" -> "STATIONS";
			case "grids" -> "GRID SQUARES";
			case "distance" -> "BLOCKS";
			case "night" -> "CONTACTS AT NIGHT";
			default -> "CONTACTS";
		};
	}

	/** The level line of a certificate: "ALL 5 BANDS" for a complete Worked All, else "25 BIOMES"; "" if none reached. */
	public static String levelText(Progress progress) {
		if(progress.level() == 0)
			return "";
		boolean workedAll = progress.id().equals("biomes") || progress.id().equals("dimensions") || progress.id().equals("bands") || progress.id().equals("modes");
		return (workedAll && progress.complete() ? "ALL " : "") + progress.reached() + " " + unit(progress.id());
	}

	// -------------------- HELPERS --------------------

	/** Levels for a "worked all" award: the {@code steps} below {@code all}, then {@code all} itself. */
	static List<Integer> tiersUpTo(int all, int... steps) {
		List<Integer> out = new ArrayList<>();
		for(int step : steps)
			if(step < all)
				out.add(step);
		out.add(Math.max(1, all));
		return out;
	}

	/** How many of {@code tiers} {@code count} reaches. */
	static int level(long count, List<Integer> tiers) {
		int level = 0;
		for(int tier : tiers)
			if(count >= tier)
				level++;
		return level;
	}

	private static Progress progress(String id, long count, List<Integer> tiers, Set<String> worked, List<String> missing) {
		List<String> shown = new ArrayList<>(worked);
		shown.sort(null);
		return new Progress(id, count, List.copyOf(tiers), level(count, tiers), cap(shown), cap(missing));
	}

	private static Progress progress(String id, long count, List<Integer> tiers, List<String> worked, List<String> missing) {
		return new Progress(id, count, List.copyOf(tiers), level(count, tiers), cap(worked), cap(missing));
	}

	private static List<String> cap(List<String> list) {
		return List.copyOf(list.size() > LIST_MAX ? list.subList(0, LIST_MAX) : list);
	}

	private static Set<String> distinct(List<Contact> contacts, Function<Contact, String> key) {
		Set<String> out = new LinkedHashSet<>();
		for(Contact contact : contacts) {
			String value = key.apply(contact);
			if(value != null && !value.isEmpty())
				out.add(value);
		}
		return out;
	}

	private static List<String> missing(Set<String> all, Set<String> worked) {
		List<String> out = new ArrayList<>();
		for(String one : all)
			if(!worked.contains(one))
				out.add(one);
		return out;
	}

	private static List<String> ordered(List<String> order, Set<String> worked) {
		List<String> out = new ArrayList<>();
		for(String one : order)
			if(worked.contains(one))
				out.add(one);
		return out;
	}
}
