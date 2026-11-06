package com.arrl.radiocraft.common.computer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The Repeater Directory program's list (computer apps 2, 2026-11-06): repeaters the computer's radios have heard and
 * ones typed in, kept on the computer's disk as {@link #FILE}. A heard repeater is known by its place (and dimension);
 * hearing it again updates its callsign, frequencies, tone and when it was last heard, but never the name or notes a
 * player wrote. Typed-in ones are known by callsign and output frequency. Entries go on to the frequency manager as
 * {@link FrequencyList} channels ({@link #exportInto}). Pure, so it is unit tested.
 */
public final class RepeaterDirectory {

	/** The file on the computer's disk. */
	public static final String FILE = "REPEATER.DAT";
	/** Most entries kept; the oldest heard (never-touched manual ones last) go first when full. */
	public static final int MAX_ENTRIES = 300;
	public static final int CALLSIGN_MAX = 12, NOTES_MAX = 40;

	/** How an entry came in. */
	public enum Source { HEARD, MANUAL }

	/** How the list is ordered on screen. */
	public enum Sort {
		DISTANCE, FREQUENCY, HEARD;

		public Sort next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	/**
	 * One repeater.
	 *
	 * @param callsign   Its ID callsign ("" if it sends none).
	 * @param notes      Name or notes a player wrote.
	 * @param outputMhz  What it transmits on: users listen here.
	 * @param offsetKhz  Input minus output, kHz (-600 for a 2m repeater on the low side; 0 simplex).
	 * @param tone       The CTCSS tone users must send, Hz; 0 for none.
	 * @param hasPos     True if its place is known.
	 * @param dimension  Its dimension id ("minecraft:overworld"), "" if unknown.
	 * @param x          Its place.
	 * @param firstHeard Game time first heard, -1 never.
	 * @param lastHeard  Game time last heard, -1 never.
	 */
	public record Entry(String callsign, String notes, double outputMhz, int offsetKhz, float tone, boolean hasPos, String dimension,
	                    int x, int y, int z, Source source, long firstHeard, long lastHeard) {

		public Entry {
			callsign = cut(clean(callsign).toUpperCase(Locale.ROOT), CALLSIGN_MAX);
			notes = cut(clean(notes), NOTES_MAX);
			outputMhz = Double.isFinite(outputMhz) && outputMhz > 0.0D ? Math.min(outputMhz, 3000.0D) : 0.0D;
			offsetKhz = Math.max(-100_000, Math.min(100_000, offsetKhz));
			tone = Float.isFinite(tone) && tone > 0.0F ? Math.min(tone, 300.0F) : 0.0F;
			dimension = dimension == null ? "" : dimension;
			source = source == null ? Source.MANUAL : source;
		}

		/** A typed-in repeater. */
		public static Entry manual(String callsign, String notes, double outputMhz, int offsetKhz, float tone, boolean hasPos, int x, int z) {
			return new Entry(callsign, notes, outputMhz, offsetKhz, tone, hasPos, "", x, 0, z, Source.MANUAL, -1L, -1L);
		}

		/** Where users transmit, MHz. */
		public double inputMhz() {
			return outputMhz + offsetKhz / 1000.0D;
		}

		public boolean wasHeard() {
			return lastHeard >= 0L;
		}

		/** "+0.600", "-5.000", "0.000": the offset in MHz with its sign, as repeater lists print it. */
		public String offsetText() {
			return RepeaterDirectory.offsetText(offsetKhz);
		}

		/** Blocks from {@code fromX, fromZ}, or -1 if its place is not known. */
		public double distanceFrom(double fromX, double fromZ) {
			return hasPos ? Math.hypot(x + 0.5D - fromX, z + 0.5D - fromZ) : -1.0D;
		}

		/**
		 * Degrees from {@code fromX, fromZ}, 0 north and clockwise (as {@code DirectionFinding.bearing}), or -1 if its
		 * place is not known.
		 */
		public double bearingFrom(double fromX, double fromZ) {
			if(!hasPos)
				return -1.0D;
			double degrees = Math.toDegrees(Math.atan2(x + 0.5D - fromX, -(z + 0.5D - fromZ))) % 360.0D;
			return degrees < 0.0D ? degrees + 360.0D : degrees;
		}

		/** The name it goes into a frequency list under: the callsign, else the notes, else "RPT 146.940". */
		public String channelName() {
			if(!callsign.isEmpty())
				return callsign;
			if(!notes.isEmpty())
				return notes;
			return String.format(Locale.ROOT, "RPT %.3f", outputMhz);
		}

		/** This repeater as a frequency list channel: receive on the output, transmit on the input, FM, its tone. */
		public FrequencyList.Entry toFrequency() {
			String note = notes.isEmpty() || notes.equals(channelName()) ? "" : notes;
			return new FrequencyList.Entry(channelName(), outputMhz, inputMhz(), FrequencyList.Mode.FM, tone, note);
		}

		public CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			tag.putString("call", callsign);
			tag.putString("notes", notes);
			tag.putDouble("out", outputMhz);
			tag.putInt("offset", offsetKhz);
			tag.putFloat("tone", tone);
			tag.putBoolean("hasPos", hasPos);
			tag.putString("dim", dimension);
			tag.putInt("x", x);
			tag.putInt("y", y);
			tag.putInt("z", z);
			tag.putString("source", source.name());
			tag.putLong("first", firstHeard);
			tag.putLong("last", lastHeard);
			return tag;
		}

		public static Entry fromTag(CompoundTag tag) {
			Source source;
			try {
				source = Source.valueOf(tag.getString("source"));
			}
			catch(IllegalArgumentException e) {
				source = Source.MANUAL;
			}
			return new Entry(tag.getString("call"), tag.getString("notes"), tag.getDouble("out"), tag.getInt("offset"), tag.getFloat("tone"),
					tag.getBoolean("hasPos"), tag.getString("dim"), tag.getInt("x"), tag.getInt("y"), tag.getInt("z"), source,
					tag.contains("first") ? tag.getLong("first") : -1L, tag.contains("last") ? tag.getLong("last") : -1L);
		}

		Entry heardAgain(Heard heard) {
			return new Entry(heard.callsign(), notes, heard.outputMhz(), heard.offsetKhz(), heard.tone(), true, heard.dimension(), heard.x(),
					heard.y(), heard.z(), Source.HEARD, firstHeard < 0L ? heard.gameTime() : Math.min(firstHeard, heard.gameTime()),
					Math.max(lastHeard, heard.gameTime()));
		}
	}

	/** A repeater one of the computer's radios heard, as the server knows it. */
	public record Heard(String callsign, double outputMhz, int offsetKhz, float tone, String dimension, int x, int y, int z, long gameTime) {

		public Entry toEntry() {
			return new Entry(callsign, "", outputMhz, offsetKhz, tone, true, dimension, x, y, z, Source.HEARD, gameTime, gameTime);
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

	/** The index of the entry {@code heard} is (same place and dimension, or same callsign and output), or -1. */
	public int find(Heard heard) {
		for(int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			if(entry.hasPos() && entry.x() == heard.x() && entry.y() == heard.y() && entry.z() == heard.z()
					&& (entry.dimension().isEmpty() || entry.dimension().equals(heard.dimension())))
				return i;
		}
		for(int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			if(!entry.callsign().isEmpty() && entry.callsign().equals(clean(heard.callsign()).toUpperCase(Locale.ROOT))
					&& sameMhz(entry.outputMhz(), heard.outputMhz()))
				return i;
		}
		return -1;
	}

	/**
	 * Takes in a repeater heard: a new entry, or the known one brought up to date (callsign, frequencies, tone, place,
	 * last heard), its notes kept. True if anything changed.
	 */
	public boolean merge(Heard heard) {
		int index = find(heard);
		if(index < 0) {
			makeRoom();
			entries.add(heard.toEntry());
			return true;
		}
		Entry before = entries.get(index);
		Entry after = before.heardAgain(heard);
		if(after.equals(before))
			return false;
		entries.set(index, after);
		return true;
	}

	/** Adds a typed-in entry; false if the list is full of entries it will not drop. */
	public boolean add(Entry entry) {
		if(entries.size() >= MAX_ENTRIES && !makeRoom())
			return false;
		entries.add(entry);
		return true;
	}

	/** Replaces entry {@code index} (an edit keeps when it was heard). */
	public void set(int index, Entry entry) {
		if(index >= 0 && index < entries.size())
			entries.set(index, entry);
	}

	public void remove(int index) {
		if(index >= 0 && index < entries.size())
			entries.remove(index);
	}

	/** At the cap, drops the entry heard longest ago; false if there is none to drop (all typed in). */
	private boolean makeRoom() {
		if(entries.size() < MAX_ENTRIES)
			return true;
		int oldest = -1;
		for(int i = 0; i < entries.size(); i++)
			if(entries.get(i).wasHeard() && (oldest < 0 || entries.get(i).lastHeard() < entries.get(oldest).lastHeard()))
				oldest = i;
		if(oldest < 0)
			return false;
		entries.remove(oldest);
		return true;
	}

	/**
	 * The entries' indices in {@code sort} order: nearest first (unknown places last), lowest output first, or most
	 * recently heard first (never heard last). Ties by output frequency.
	 */
	public List<Integer> order(Sort sort, double fromX, double fromZ) {
		List<Integer> out = new ArrayList<>();
		for(int i = 0; i < entries.size(); i++)
			out.add(i);
		Comparator<Integer> byFrequency = Comparator.comparingDouble(i -> entries.get(i).outputMhz());
		Comparator<Integer> comparator = switch(sort) {
			case DISTANCE -> Comparator.<Integer>comparingDouble(i -> {
				double d = entries.get(i).distanceFrom(fromX, fromZ);
				return d < 0.0D ? Double.MAX_VALUE : d;
			}).thenComparing(byFrequency);
			case FREQUENCY -> byFrequency;
			case HEARD -> Comparator.<Integer>comparingLong(i -> -entries.get(i).lastHeard()).thenComparing(byFrequency);
		};
		out.sort(comparator);
		return out;
	}

	/** What {@link #exportInto} did: channels added, and skipped (already in the list, or the list full). */
	public record ExportResult(int added, int duplicates, int full) {}

	/**
	 * Adds {@code chosen} to {@code list} as channels, skipping any whose receive and transmit frequencies the list
	 * already has (and any past the list's size).
	 */
	public static ExportResult exportInto(FrequencyList list, List<Entry> chosen) {
		int added = 0, duplicates = 0, full = 0;
		for(Entry entry : chosen) {
			FrequencyList.Entry channel = entry.toFrequency();
			boolean known = false;
			for(FrequencyList.Entry existing : list.entries())
				if(sameMhz(existing.rxMhz(), channel.rxMhz()) && sameMhz(existing.txMhz(), channel.txMhz())) {
					known = true;
					break;
				}
			if(known)
				duplicates++;
			else if(list.add(channel))
				added++;
			else
				full++;
		}
		return new ExportResult(added, duplicates, full);
	}

	public CompoundTag toTag() {
		ListTag list = new ListTag();
		for(Entry entry : entries)
			list.add(entry.toTag());
		CompoundTag tag = new CompoundTag();
		tag.put("repeaters", list);
		return tag;
	}

	public static RepeaterDirectory fromTag(@Nullable CompoundTag tag) {
		RepeaterDirectory out = new RepeaterDirectory();
		if(tag == null)
			return out;
		ListTag list = tag.getList("repeaters", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size() && i < MAX_ENTRIES; i++)
			out.entries.add(Entry.fromTag(list.getCompound(i)));
		return out;
	}

	// -------------------- WORDS --------------------

	/** "+0.600", "-5.000", "0.000". */
	public static String offsetText(int offsetKhz) {
		String mhz = String.format(Locale.ROOT, "%.3f", Math.abs(offsetKhz) / 1000.0D);
		return offsetKhz > 0 ? "+" + mhz : offsetKhz < 0 ? "-" + mhz : mhz;
	}

	/**
	 * An offset typed in kHz ("-600", "+5000") or MHz with a point ("-0.6", "+5.0"), as kHz; null if it is not one.
	 * Empty is 0.
	 */
	@Nullable
	public static Integer parseOffsetKhz(String text) {
		String t = text == null ? "" : text.strip();
		if(t.isEmpty())
			return 0;
		try {
			double value = Double.parseDouble(t);
			if(!Double.isFinite(value))
				return null;
			double khz = t.contains(".") ? value * 1000.0D : value;
			if(Math.abs(khz) > 100_000.0D)
				return null;
			return (int)Math.round(khz);
		}
		catch(NumberFormatException e) {
			return null;
		}
	}

	private static boolean sameMhz(double a, double b) {
		return Math.abs(a - b) < 0.0005D;
	}

	private static String clean(String text) {
		if(text == null)
			return "";
		StringBuilder out = new StringBuilder(text.length());
		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if(c >= 32 && c < 127)
				out.append(c);
		}
		return out.toString().strip();
	}

	private static String cut(String text, int max) {
		return text.length() <= max ? text : text.substring(0, max);
	}
}
