package com.arrl.radiocraft.common.computer.games;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A game's best scores on one radio computer (1.12 step 8): the top {@link #SIZE}, highest first, each with who made
 * it. Kept on the computer's disk as a {@code .DAT} file ({@code SNAKE.DAT}, {@code PILEUP.DAT}), compressed NBT as
 * {@code ComputerFileFormats.nbt} writes it, so everyone at that computer shares one table, as on an arcade cabinet.
 */
public final class HighScores {

	public static final int SIZE = 10;
	/** Longest name kept. */
	public static final int NAME_LENGTH = 16;

	/** One score. */
	public record Entry(String name, int score) {}

	private final List<Entry> entries = new ArrayList<>();

	/** The scores, highest first. */
	public List<Entry> entries() {
		return Collections.unmodifiableList(entries);
	}

	/** The best score, or 0. */
	public int best() {
		return entries.isEmpty() ? 0 : entries.getFirst().score();
	}

	/** True if {@code score} would make the table. */
	public boolean qualifies(int score) {
		return score > 0 && (entries.size() < SIZE || score > entries.getLast().score());
	}

	/**
	 * Adds a score; returns its place (0 the best) or -1 if it did not make the table. A tie goes below the scores
	 * already there.
	 */
	public int add(String name, int score) {
		if(!qualifies(score))
			return -1;
		String trimmed = name == null ? "" : name.strip();
		if(trimmed.length() > NAME_LENGTH)
			trimmed = trimmed.substring(0, NAME_LENGTH);
		int at = 0;
		while(at < entries.size() && entries.get(at).score() >= score)
			at++;
		entries.add(at, new Entry(trimmed, score));
		while(entries.size() > SIZE)
			entries.removeLast();
		return at;
	}

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for(Entry entry : entries) {
			CompoundTag e = new CompoundTag();
			e.putString("name", entry.name());
			e.putInt("score", entry.score());
			list.add(e);
		}
		tag.put("scores", list);
		return tag;
	}

	/** The table in {@code tag} (empty for null or a tag of another layout). */
	public static HighScores fromTag(CompoundTag tag) {
		HighScores scores = new HighScores();
		if(tag == null)
			return scores;
		ListTag list = tag.getList("scores", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size(); i++) {
			CompoundTag e = list.getCompound(i);
			scores.add(e.getString("name"), e.getInt("score"));
		}
		return scores;
	}
}
