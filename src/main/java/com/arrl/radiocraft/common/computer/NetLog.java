package com.arrl.radiocraft.common.computer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A directed net as the net control station (NCS) runs it (the Net Control logger, computer batch 3C): the net's
 * name, frequency, mode and NCS, and the check-in list. Each check-in has a callsign, name, location (or grid), a
 * traffic flag (has a message to pass), a short-time flag (must leave early), the time in and the time out, and how
 * often it was rechecked. The roll call takes the stations still in: short-time first, then those with traffic, then
 * the rest, each group in list order (which the NCS can change). Kept as a {@code .NET} file (compressed NBT);
 * {@link #summary} is the printable net report. Pure (only NBT and {@code java.time}), so it is unit tested.
 */
public final class NetLog {

	public static final int MAX_CHECK_INS = 100;
	public static final int NAME_MAX = 24, FREQ_MAX = 12, MODE_MAX = 8, CALL_MAX = 12, OP_MAX = 16, LOCATION_MAX = 20;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm", Locale.ROOT).withZone(ZoneOffset.UTC);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT).withZone(ZoneOffset.UTC);

	/** One station checked in. {@code outUtc} 0 while it is still in. */
	public record CheckIn(String call, String name, String location, boolean traffic, boolean shortTime, long inUtc, long outUtc, int rechecks) {

		public CheckIn {
			call = clean(call, CALL_MAX).toUpperCase(Locale.ROOT).replace(" ", "");
			name = clean(name, OP_MAX);
			location = clean(location, LOCATION_MAX);
			rechecks = Math.max(0, rechecks);
		}

		public boolean in() {
			return outUtc == 0L;
		}

		CheckIn with(boolean traffic, boolean shortTime, long outUtc, int rechecks) {
			return new CheckIn(call, name, location, traffic, shortTime, inUtc, outUtc, rechecks);
		}

		CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			tag.putString("call", call);
			tag.putString("name", name);
			tag.putString("loc", location);
			tag.putBoolean("traffic", traffic);
			tag.putBoolean("short", shortTime);
			tag.putLong("in", inUtc);
			tag.putLong("out", outUtc);
			tag.putInt("rechecks", rechecks);
			return tag;
		}

		static CheckIn fromTag(CompoundTag tag) {
			return new CheckIn(tag.getString("call"), tag.getString("name"), tag.getString("loc"), tag.getBoolean("traffic"), tag.getBoolean("short"),
					tag.getLong("in"), tag.getLong("out"), tag.getInt("rechecks"));
		}
	}

	/** What {@link #checkIn} did. */
	public enum Result { ADDED, RECHECKED, BACK_IN, FULL, NO_CALL }

	private String name = "";
	private String frequency = "";
	private String mode = "FM";
	private String ncs = "";
	private long openedUtc = 0L;
	private long closedUtc = 0L;
	private final List<CheckIn> list = new ArrayList<>();

	// -------------------- THE NET --------------------

	public String name() {
		return name;
	}

	public String frequency() {
		return frequency;
	}

	public String mode() {
		return mode;
	}

	public String ncs() {
		return ncs;
	}

	public long openedUtc() {
		return openedUtc;
	}

	public long closedUtc() {
		return closedUtc;
	}

	public boolean isOpen() {
		return openedUtc != 0L && closedUtc == 0L;
	}

	public void setup(String name, String frequency, String mode, String ncs) {
		this.name = clean(name, NAME_MAX);
		this.frequency = clean(frequency, FREQ_MAX);
		String m = clean(mode, MODE_MAX).toUpperCase(Locale.ROOT).replace(" ", "");
		this.mode = m.isEmpty() ? "FM" : m;
		this.ncs = clean(ncs, CALL_MAX).toUpperCase(Locale.ROOT).replace(" ", "");
	}

	/** Opens the net at {@code now} (UTC ms); the list starts empty. */
	public void open(long now) {
		openedUtc = Math.max(1L, now);
		closedUtc = 0L;
		list.clear();
	}

	/** Closes the net at {@code now}: everyone still in is checked out then. */
	public void close(long now) {
		if(openedUtc == 0L)
			openedUtc = Math.max(1L, now);
		closedUtc = Math.max(openedUtc, now);
		for(int i = 0; i < list.size(); i++)
			if(list.get(i).in())
				list.set(i, list.get(i).with(list.get(i).traffic(), list.get(i).shortTime(), closedUtc, list.get(i).rechecks()));
	}

	// -------------------- CHECK-INS --------------------

	public List<CheckIn> checkIns() {
		return Collections.unmodifiableList(list);
	}

	public int size() {
		return list.size();
	}

	public CheckIn get(int index) {
		return list.get(index);
	}

	/** The index of {@code call} in the list, or -1. */
	public int indexOf(String call) {
		String c = clean(call, CALL_MAX).toUpperCase(Locale.ROOT).replace(" ", "");
		for(int i = 0; i < list.size(); i++)
			if(list.get(i).call().equals(c))
				return i;
		return -1;
	}

	/**
	 * Checks {@code call} in at {@code now}. A station already in is rechecked (a recheck counted, flags raised if given);
	 * one that had checked out comes back in. The net opens on the first check-in if it is not open.
	 */
	public Result checkIn(String call, String opName, String location, boolean traffic, boolean shortTime, long now) {
		CheckIn made = new CheckIn(call, opName, location, traffic, shortTime, now, 0L, 0);
		if(made.call().isEmpty())
			return Result.NO_CALL;
		if(!isOpen() && closedUtc == 0L && openedUtc == 0L)
			openedUtc = Math.max(1L, now);
		int at = indexOf(made.call());
		if(at >= 0) {
			CheckIn old = list.get(at);
			boolean back = !old.in();
			CheckIn kept = new CheckIn(old.call(), opName.isBlank() ? old.name() : opName, location.isBlank() ? old.location() : location,
					old.traffic() || traffic, old.shortTime() || shortTime, old.inUtc(), 0L, old.rechecks() + (back ? 0 : 1));
			list.set(at, kept);
			return back ? Result.BACK_IN : Result.RECHECKED;
		}
		if(list.size() >= MAX_CHECK_INS)
			return Result.FULL;
		list.add(made);
		return Result.ADDED;
	}

	/** Replaces check-in {@code index}'s call, name and location (flags and times kept). */
	public void edit(int index, String call, String opName, String location) {
		if(index < 0 || index >= list.size())
			return;
		CheckIn old = list.get(index);
		CheckIn made = new CheckIn(call, opName, location, old.traffic(), old.shortTime(), old.inUtc(), old.outUtc(), old.rechecks());
		if(made.call().isEmpty())
			return;
		list.set(index, made);
	}

	public void remove(int index) {
		if(index >= 0 && index < list.size())
			list.remove(index);
	}

	/** Checks {@code index} out at {@code now}, or back in if it was out. */
	public void toggleOut(int index, long now) {
		if(index < 0 || index >= list.size())
			return;
		CheckIn c = list.get(index);
		list.set(index, c.with(c.traffic(), c.shortTime(), c.in() ? Math.max(1L, now) : 0L, c.rechecks()));
	}

	public void toggleTraffic(int index) {
		if(index >= 0 && index < list.size()) {
			CheckIn c = list.get(index);
			list.set(index, c.with(!c.traffic(), c.shortTime(), c.outUtc(), c.rechecks()));
		}
	}

	public void toggleShortTime(int index) {
		if(index >= 0 && index < list.size()) {
			CheckIn c = list.get(index);
			list.set(index, c.with(c.traffic(), !c.shortTime(), c.outUtc(), c.rechecks()));
		}
	}

	/** Counts a recheck of {@code index} (the NCS called it again and it answered). */
	public void recheck(int index) {
		if(index >= 0 && index < list.size()) {
			CheckIn c = list.get(index);
			list.set(index, c.with(c.traffic(), c.shortTime(), c.outUtc(), c.rechecks() + 1));
		}
	}

	/** Moves check-in {@code index} up ({@code by} -1) or down (+1); answers its new index. */
	public int move(int index, int by) {
		int to = index + by;
		if(index < 0 || index >= list.size() || to < 0 || to >= list.size())
			return index;
		Collections.swap(list, index, to);
		return to;
	}

	/** The roll call: indexes of the stations still in, short-time first, then traffic, then the rest, in list order. */
	public List<Integer> rollCall() {
		List<Integer> out = new ArrayList<>();
		for(int pass = 0; pass < 3; pass++)
			for(int i = 0; i < list.size(); i++) {
				CheckIn c = list.get(i);
				if(!c.in())
					continue;
				int group = c.shortTime() ? 0 : c.traffic() ? 1 : 2;
				if(group == pass)
					out.add(i);
			}
		return out;
	}

	/** The roll call's next station after list index {@code current} (-1 starts at the top); -1 if nobody is in. */
	public int nextInRollCall(int current) {
		List<Integer> order = rollCall();
		if(order.isEmpty())
			return -1;
		int at = order.indexOf(current);
		return order.get(at < 0 ? 0 : (at + 1) % order.size());
	}

	public int trafficCount() {
		int n = 0;
		for(CheckIn c : list)
			if(c.traffic())
				n++;
		return n;
	}

	// -------------------- THE REPORT --------------------

	/** The net report, a line each: the net, NCS, times, counts, then every check-in with its times and flags. */
	public List<String> summary() {
		List<String> out = new ArrayList<>();
		out.add("NET REPORT: " + (name.isEmpty() ? "(UNNAMED NET)" : name.toUpperCase(Locale.ROOT)));
		out.add("FREQ " + (frequency.isEmpty() ? "-" : frequency) + " " + mode + "  NCS " + (ncs.isEmpty() ? "-" : ncs));
		if(openedUtc != 0L)
			out.add("OPENED " + DATE.format(Instant.ofEpochMilli(openedUtc)) + " " + TIME.format(Instant.ofEpochMilli(openedUtc)) + "Z"
					+ (closedUtc != 0L ? "  CLOSED " + TIME.format(Instant.ofEpochMilli(closedUtc)) + "Z (" + Math.max(0L, (closedUtc - openedUtc) / 60000L) + " MIN)" : ""));
		out.add("CHECK-INS " + list.size() + "  TRAFFIC " + trafficCount());
		out.add("");
		for(int i = 0; i < list.size(); i++) {
			CheckIn c = list.get(i);
			StringBuilder line = new StringBuilder();
			line.append(String.format(Locale.ROOT, "%2d %-10s", i + 1, c.call()));
			line.append(' ').append(TIME.format(Instant.ofEpochMilli(c.inUtc())));
			line.append('-').append(c.in() ? "    " : TIME.format(Instant.ofEpochMilli(c.outUtc())));
			if(c.traffic())
				line.append(" TFC");
			if(c.shortTime())
				line.append(" ST");
			if(c.rechecks() > 0)
				line.append(" R").append(c.rechecks());
			if(!c.name().isEmpty())
				line.append(' ').append(c.name());
			if(!c.location().isEmpty())
				line.append(", ").append(c.location());
			out.add(line.toString());
		}
		out.add("");
		out.add("73 DE " + (ncs.isEmpty() ? "NCS" : ncs));
		return out;
	}

	/**
	 * The check-ins as logbook contacts: one per station, at its time in, on the net's frequency and mode, reports 59,
	 * notes "NET name, op name, location" (the location as the grid when it is one).
	 */
	public List<Logbook.Entry> asContacts(String gameTime) {
		double mhz = FrequencyList.parseMhz(frequency);
		List<Logbook.Entry> out = new ArrayList<>();
		for(CheckIn c : list) {
			String notes = ("NET " + name + (c.name().isEmpty() ? "" : ", " + c.name()) + (c.location().isEmpty() ? "" : ", " + c.location())).strip();
			String grid = c.location().strip().toUpperCase(Locale.ROOT);
			String report = mode.equals("CW") ? "599" : "59";
			out.add(new Logbook.Entry(c.inUtc(), gameTime, c.call(), Math.max(0.0D, mhz), mode, report, report, notes,
					com.arrl.radiocraft.common.radio.ft8.Ft8.isGrid(grid) ? Logbook.Where.grid(grid) : Logbook.Where.UNKNOWN));
		}
		return out;
	}

	// -------------------- FILE --------------------

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("name", name);
		tag.putString("freq", frequency);
		tag.putString("mode", mode);
		tag.putString("ncs", ncs);
		tag.putLong("opened", openedUtc);
		tag.putLong("closed", closedUtc);
		ListTag checks = new ListTag();
		for(CheckIn c : list)
			checks.add(c.toTag());
		tag.put("checkins", checks);
		return tag;
	}

	public static NetLog fromTag(CompoundTag tag) {
		NetLog net = new NetLog();
		net.setup(tag.getString("name"), tag.getString("freq"), tag.getString("mode"), tag.getString("ncs"));
		net.openedUtc = tag.getLong("opened");
		net.closedUtc = tag.getLong("closed");
		ListTag checks = tag.getList("checkins", Tag.TAG_COMPOUND);
		for(int i = 0; i < checks.size() && net.list.size() < MAX_CHECK_INS; i++) {
			CheckIn c = CheckIn.fromTag(checks.getCompound(i));
			if(!c.call().isEmpty())
				net.list.add(c);
		}
		return net;
	}

	static String clean(String text, int max) {
		if(text == null)
			return "";
		StringBuilder out = new StringBuilder();
		for(int i = 0; i < text.length() && out.length() < max; i++) {
			char c = text.charAt(i);
			if(c >= ' ' && c <= '~')
				out.append(c);
		}
		return out.toString().strip();
	}
}
