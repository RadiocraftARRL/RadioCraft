package com.arrl.radiocraft.common.computer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A station logbook (1.12 step 6): the contacts (QSOs) a ham keeps, with the fields of an ADIF record: the date and
 * time on in UTC, the other station's call, the frequency, the mode, the signal reports sent and received, and notes.
 * The in-game day and time is kept too, for players who think in game days. Stored as a {@code .LOG} file and written
 * out as ADIF ({@link #adif}) for real logging programs. Pure (only NBT and {@code java.time}), so it is unit tested.
 * <p>
 * File layout (compressed NBT, {@link ComputerFileFormats#LOG}): {@code qsos}, a list of compounds with {@code utc}
 * (long, ms), {@code game} (string, "D12 1430"), {@code call}, {@code mhz} (double), {@code mode}, {@code sent},
 * {@code rcvd}, {@code notes}, and (computer batch 3C) {@code where} when anything is known of where the other station
 * was ({@link Where}: grid square, dimension, biome, rounded distance; never coordinates). Old files simply have no {@code where}.
 */
public final class Logbook {

	/** Most contacts one log file holds (well within a file's 256K). */
	public static final int MAX_ENTRIES = 2000;
	public static final int CALL_MAX = 12, MODE_MAX = 8, RST_MAX = 3, NOTES_MAX = 120;

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT).withZone(ZoneOffset.UTC);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm", Locale.ROOT).withZone(ZoneOffset.UTC);
	private static final DateTimeFormatter SHOWN_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT).withZone(ZoneOffset.UTC);

	/**
	 * Where the other station was, coarsely (computer batch 3C, for the Awards program): its grid square (FT8 sends it,
	 * a player can type it, or the server works it out from where the station's antenna was when it was heard), its
	 * dimension, the biome at the grid square's centre, and the distance to it rounded to {@link #DISTANCE_STEP} blocks.
	 * <b>Never exact coordinates</b>: a log file is a client-readable file, and another player's position must not leak
	 * through it. Every part may be empty.
	 *
	 * @param grid      four-character locator ({@code Ft8.grid}, squares of 256 blocks), or ""
	 * @param dimension the dimension's id ("minecraft:the_nether"), or ""
	 * @param biome     the biome's id ("minecraft:plains") at the grid square's centre, or ""
	 * @param distance  blocks from the logging station, a multiple of {@link #DISTANCE_STEP}; -1 if unknown
	 */
	public record Where(String grid, String dimension, String biome, int distance) {

		public static final Where UNKNOWN = new Where("", "", "", -1);
		public static final int ID_MAX = 64;
		/** Distances are kept to the nearest this many blocks. */
		public static final int DISTANCE_STEP = 100;

		public Where {
			grid = grid == null ? "" : grid.strip().toUpperCase(Locale.ROOT);
			if(!com.arrl.radiocraft.common.radio.ft8.Ft8.isGrid(grid))
				grid = "";
			dimension = id(dimension);
			biome = id(biome);
			distance = distance < 0 ? -1 : coarse(distance);
		}

		/** Only a grid square (typed, or sent by FT8). */
		public static Where grid(String grid) {
			return new Where(grid, "", "", -1);
		}

		/** {@code blocks} rounded to the nearest {@link #DISTANCE_STEP}. */
		public static int coarse(double blocks) {
			return (int)(Math.round(blocks / DISTANCE_STEP) * DISTANCE_STEP);
		}

		public boolean known() {
			return !grid.isEmpty() || !dimension.isEmpty() || !biome.isEmpty() || distance >= 0;
		}

		public CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			if(!grid.isEmpty())
				tag.putString("grid", grid);
			if(!dimension.isEmpty())
				tag.putString("dim", dimension);
			if(!biome.isEmpty())
				tag.putString("biome", biome);
			if(distance >= 0)
				tag.putInt("dist", distance);
			return tag;
		}

		public static Where fromTag(CompoundTag tag) {
			return new Where(tag.getString("grid"), tag.getString("dim"), tag.getString("biome"), tag.contains("dist") ? tag.getInt("dist") : -1);
		}

		/** A resource id as it may be kept: lower case {@code [a-z0-9_.-/:]}, at most {@link #ID_MAX}; else "". */
		private static String id(String text) {
			if(text == null)
				return "";
			String t = text.strip().toLowerCase(Locale.ROOT);
			if(t.length() > ID_MAX)
				return "";
			for(int i = 0; i < t.length(); i++) {
				char c = t.charAt(i);
				if(!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-' || c == '/' || c == ':'))
					return "";
			}
			return t;
		}
	}

	/**
	 * One contact.
	 *
	 * @param utcMillis    When it started, real time (ms since the epoch); logs are kept in UTC.
	 * @param gameTime     The in-game day and time it started ("D12 1430"), or "".
	 * @param call         The other station's callsign, upper case.
	 * @param frequencyMhz Where it was, MHz; 0 if not noted.
	 * @param mode         USB, LSB, CW, FM, AM, DIG...
	 * @param rstSent      The report given ("59", "599").
	 * @param rstReceived  The report received.
	 * @param notes        Name, place, anything else.
	 * @param where        Where the other station was, as far as is known (computer batch 3C).
	 */
	public record Entry(long utcMillis, String gameTime, String call, double frequencyMhz, String mode, String rstSent, String rstReceived, String notes,
			Where where) {

		/** A contact with nothing known of where the other station was. */
		public Entry(long utcMillis, String gameTime, String call, double frequencyMhz, String mode, String rstSent, String rstReceived, String notes) {
			this(utcMillis, gameTime, call, frequencyMhz, mode, rstSent, rstReceived, notes, Where.UNKNOWN);
		}

		public Entry {
			where = where == null ? Where.UNKNOWN : where;
			gameTime = cut(ascii(gameTime), 16);
			call = cut(ascii(call).toUpperCase(Locale.ROOT).replace(" ", ""), CALL_MAX);
			frequencyMhz = Double.isFinite(frequencyMhz) && frequencyMhz > 0.0D ? Math.min(frequencyMhz, 300_000.0D) : 0.0D;
			mode = cut(ascii(mode).toUpperCase(Locale.ROOT).replace(" ", ""), MODE_MAX);
			rstSent = cut(ascii(rstSent).replace(" ", ""), RST_MAX);
			rstReceived = cut(ascii(rstReceived).replace(" ", ""), RST_MAX);
			notes = cut(ascii(notes), NOTES_MAX);
		}

		public CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("utc", utcMillis);
			tag.putString("game", gameTime);
			tag.putString("call", call);
			tag.putDouble("mhz", frequencyMhz);
			tag.putString("mode", mode);
			tag.putString("sent", rstSent);
			tag.putString("rcvd", rstReceived);
			tag.putString("notes", notes);
			if(where.known())
				tag.put("where", where.toTag());
			return tag;
		}

		public static Entry fromTag(CompoundTag tag) {
			return new Entry(tag.getLong("utc"), tag.getString("game"), tag.getString("call"), tag.getDouble("mhz"), tag.getString("mode"),
					tag.getString("sent"), tag.getString("rcvd"), tag.getString("notes"),
					tag.contains("where", Tag.TAG_COMPOUND) ? Where.fromTag(tag.getCompound("where")) : Where.UNKNOWN);
		}

		/** This contact with {@code where} instead. */
		public Entry withWhere(Where where) {
			return new Entry(utcMillis, gameTime, call, frequencyMhz, mode, rstSent, rstReceived, notes, where);
		}

		/** "2026-11-04". */
		public String date() {
			return SHOWN_DATE.format(Instant.ofEpochMilli(utcMillis));
		}

		/** "1430" (UTC). */
		public String time() {
			return TIME.format(Instant.ofEpochMilli(utcMillis));
		}

		/** The ADIF band name ("20m"), or "" if the frequency is in no band ADIF knows. */
		public String band() {
			return adifBand(frequencyMhz);
		}
	}

	private final List<Entry> entries = new ArrayList<>();

	public List<Entry> entries() {
		return Collections.unmodifiableList(entries);
	}

	public int size() {
		return entries.size();
	}

	public Entry get(int index) {
		return entries.get(index);
	}

	/** Adds a contact in time order (the newest last); false if the log is full. */
	public boolean add(Entry entry) {
		if(entries.size() >= MAX_ENTRIES)
			return false;
		int at = entries.size();
		while(at > 0 && entries.get(at - 1).utcMillis() > entry.utcMillis())
			at--;
		entries.add(at, entry);
		return true;
	}

	/** Replaces contact {@code index} (kept in time order). */
	public void set(int index, Entry entry) {
		if(index < 0 || index >= entries.size())
			return;
		entries.remove(index);
		add(entry);
	}

	public void remove(int index) {
		if(index >= 0 && index < entries.size())
			entries.remove(index);
	}

	/** How many contacts with {@code call} the log holds (worked before?). */
	public int countWith(String call) {
		String c = call.toUpperCase(Locale.ROOT).strip();
		int n = 0;
		for(Entry entry : entries)
			if(entry.call().equals(c))
				n++;
		return n;
	}

	// -------------------- FILE --------------------

	public CompoundTag toTag() {
		ListTag list = new ListTag();
		for(Entry entry : entries)
			list.add(entry.toTag());
		CompoundTag tag = new CompoundTag();
		tag.put("qsos", list);
		return tag;
	}

	public static Logbook fromTag(CompoundTag tag) {
		Logbook log = new Logbook();
		ListTag list = tag.getList("qsos", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size(); i++)
			log.add(Entry.fromTag(list.getCompound(i)));
		return log;
	}

	// -------------------- ADIF --------------------

	/**
	 * The log as an ADIF 3 text file (the format every logging program imports): a header, then one record per contact
	 * with QSO_DATE, TIME_ON, CALL, FREQ, BAND, MODE (and SUBMODE for USB/LSB), RST_SENT, RST_RCVD, GRIDSQUARE and
	 * COMMENT.
	 */
	public String adif(String programVersion) {
		StringBuilder out = new StringBuilder();
		out.append("RadioCraft logbook export\n");
		field(out, "ADIF_VER", "3.1.4");
		field(out, "PROGRAMID", "RadioCraft");
		if(!programVersion.isEmpty())
			field(out, "PROGRAMVERSION", ascii(programVersion));
		out.append("<EOH>\n");
		for(Entry entry : entries) {
			field(out, "CALL", entry.call());
			field(out, "QSO_DATE", DATE.format(Instant.ofEpochMilli(entry.utcMillis())));
			field(out, "TIME_ON", entry.time());
			if(entry.frequencyMhz() > 0.0D)
				field(out, "FREQ", String.format(Locale.ROOT, "%.6f", entry.frequencyMhz()));
			field(out, "BAND", entry.band());
			String[] mode = adifMode(entry.mode());
			field(out, "MODE", mode[0]);
			field(out, "SUBMODE", mode[1]);
			field(out, "RST_SENT", entry.rstSent());
			field(out, "RST_RCVD", entry.rstReceived());
			field(out, "GRIDSQUARE", entry.where().grid());
			String comment = entry.notes() + (entry.gameTime().isEmpty() ? "" : (entry.notes().isEmpty() ? "" : " ") + "(game " + entry.gameTime() + ")");
			field(out, "COMMENT", comment);
			out.append("<EOR>\n");
		}
		return out.toString();
	}

	/** One ADIF field, {@code <NAME:length>value }, or nothing for an empty value. */
	private static void field(StringBuilder out, String name, String value) {
		if(value == null || value.isEmpty())
			return;
		out.append('<').append(name).append(':').append(value.length()).append('>').append(value).append(' ');
	}

	/** The ADIF MODE and SUBMODE for a mode as the radios show it: USB/LSB are SSB, DIG is digital voice. */
	public static String[] adifMode(String mode) {
		return switch(mode) {
			case "USB", "LSB" -> new String[] { "SSB", mode };
			case "DIG", "P25" -> new String[] { "DIGITALVOICE", "" };
			default -> new String[] { mode, "" };
		};
	}

	/** The ADIF band of {@code mhz}: the ham bands, "" for the commercial ones (ADIF has none). */
	public static String adifBand(double mhz) {
		if(mhz >= 3.5D && mhz <= 4.0D)
			return "80m";
		if(mhz >= 7.0D && mhz <= 7.3D)
			return "40m";
		if(mhz >= 14.0D && mhz <= 14.35D)
			return "20m";
		if(mhz >= 28.0D && mhz <= 29.7D)
			return "10m";
		if(mhz >= 50.0D && mhz <= 54.0D)
			return "6m";
		if(mhz >= 144.0D && mhz <= 148.0D)
			return "2m";
		return "";
	}

	// -------------------- REPORTS AND TIMES --------------------

	/**
	 * A signal report from what the radio heard: strength (S1-S9, the S-meter's 0..1 times nine) and readability (R1-R5,
	 * how far the signal stood above the noise). "59" on phone, "599" on CW (tone always 9).
	 */
	public static String rst(double strength, double noise, boolean cw) {
		int s = (int)Math.max(1, Math.min(9, Math.round(strength * 9.0D)));
		double ratio = strength / Math.max(0.01D, noise);
		int r = ratio >= 4.0D ? 5 : ratio >= 2.0D ? 4 : ratio >= 1.0D ? 3 : ratio >= 0.5D ? 2 : 1;
		return "" + r + s + (cw ? "9" : "");
	}

	/** {@code date} ("20261104" or "2026-11-04") and {@code time} ("1430" or "14:30") in UTC as ms, or -1 if either is bad. */
	public static long parseUtc(String date, String time) {
		try {
			String d = date.strip().replace("-", "").replace("/", "");
			String t = time.strip().replace(":", "");
			if(d.length() != 8 || t.length() != 4)
				return -1L;
			LocalDate day = LocalDate.parse(d, DateTimeFormatter.BASIC_ISO_DATE);
			LocalTime at = LocalTime.of(Integer.parseInt(t.substring(0, 2)), Integer.parseInt(t.substring(2)));
			return day.atTime(at).toInstant(ZoneOffset.UTC).toEpochMilli();
		}
		catch(DateTimeException | NumberFormatException e) {
			return -1L;
		}
	}

	/** "20261104" for a UTC instant. */
	public static String utcDate(long millis) {
		return DATE.format(Instant.ofEpochMilli(millis));
	}

	/** "1430" for a UTC instant. */
	public static String utcTime(long millis) {
		return TIME.format(Instant.ofEpochMilli(millis));
	}

	private static String ascii(String text) {
		if(text == null)
			return "";
		StringBuilder out = new StringBuilder(text.length());
		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if(c >= 32 && c < 127 && c != '<' && c != '>')
				out.append(c);
		}
		return out.toString().strip();
	}

	private static String cut(String text, int max) {
		return text.length() > max ? text.substring(0, max) : text;
	}
}
