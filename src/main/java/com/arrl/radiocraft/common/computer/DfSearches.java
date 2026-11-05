package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.radio.DirectionFinding;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The direction finding program's past searches (1.12 step 7), kept in {@code DFSEARCH.DAT} on the computer: each the
 * frequency hunted, when, a note, the bearings it was worked out from and the fix they gave (if any). Newest first, at
 * most {@link #MAX}. The NBT layout ({@link #toTag} / {@link #fromTag}) is the file's. Pure (no game registries), so it
 * is unit tested.
 */
public final class DfSearches {

	/** The file on the computer's disk. */
	public static final String FILE = "DFSEARCH.DAT";
	/** Most searches kept; the oldest goes. */
	public static final int MAX = 50;
	/** Characters of a search's note. */
	public static final int NOTE_MAX = 40;

	/** Where a bearing came from: this direction finder, typed in from another, or over a microwave link. */
	public enum Source { OWN, TYPED, LINKED }

	/** One bearing of a search. */
	public record Bearing(double x, double z, double bearing, double sigma, Source source) {

		public DirectionFinding.Line line() {
			return new DirectionFinding.Line(x, z, bearing, sigma);
		}

		CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			tag.putDouble("x", x);
			tag.putDouble("z", z);
			tag.putDouble("bearing", bearing);
			tag.putDouble("sigma", sigma);
			tag.putString("source", source.name());
			return tag;
		}

		static Bearing fromTag(CompoundTag tag) {
			Source source;
			try {
				source = Source.valueOf(tag.getString("source"));
			}
			catch(IllegalArgumentException e) {
				source = Source.TYPED;
			}
			return new Bearing(tag.getDouble("x"), tag.getDouble("z"), DirectionFinding.normalize(tag.getDouble("bearing")), Math.max(0.1D, tag.getDouble("sigma")), source);
		}
	}

	/** A search: when (real time, ms, and game time), the frequency, a note, the bearings and the fix they gave. */
	public record Search(long utcMillis, long gameTime, float frequencyHz, String note, List<Bearing> bearings) {

		public Search {
			note = note == null ? "" : note.length() > NOTE_MAX ? note.substring(0, NOTE_MAX) : note;
			bearings = List.copyOf(bearings);
		}

		/** The fix the bearings give (worked out again: the arithmetic is cheap). */
		public DirectionFinding.Fix fix() {
			List<DirectionFinding.Line> lines = new ArrayList<>();
			for(Bearing bearing : bearings)
				lines.add(bearing.line());
			return DirectionFinding.triangulate(lines);
		}

		public CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("utc", utcMillis);
			tag.putLong("gameTime", gameTime);
			tag.putFloat("frequency", frequencyHz);
			tag.putString("note", note);
			ListTag list = new ListTag();
			for(Bearing bearing : bearings)
				list.add(bearing.toTag());
			tag.put("bearings", list);
			return tag;
		}

		public static Search fromTag(CompoundTag tag) {
			List<Bearing> bearings = new ArrayList<>();
			ListTag list = tag.getList("bearings", Tag.TAG_COMPOUND);
			for(int i = 0; i < list.size() && i < 32; i++)
				bearings.add(Bearing.fromTag(list.getCompound(i)));
			return new Search(tag.getLong("utc"), tag.getLong("gameTime"), tag.getFloat("frequency"), tag.getString("note"), bearings);
		}
	}

	private final List<Search> searches = new ArrayList<>();

	/** The searches, newest first. */
	public List<Search> all() {
		return Collections.unmodifiableList(searches);
	}

	public int size() {
		return searches.size();
	}

	/** Keeps {@code search} as the newest; the oldest goes past {@link #MAX}. */
	public void add(Search search) {
		searches.addFirst(search);
		while(searches.size() > MAX)
			searches.removeLast();
	}

	/** Removes the search at {@code index} (newest first); false if there is none. */
	public boolean remove(int index) {
		if(index < 0 || index >= searches.size())
			return false;
		searches.remove(index);
		return true;
	}

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for(Search search : searches)
			list.add(search.toTag());
		tag.put("searches", list);
		return tag;
	}

	public static DfSearches fromTag(CompoundTag tag) {
		DfSearches out = new DfSearches();
		ListTag list = tag.getList("searches", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size() && out.searches.size() < MAX; i++)
			out.searches.add(Search.fromTag(list.getCompound(i)));
		return out;
	}
}
