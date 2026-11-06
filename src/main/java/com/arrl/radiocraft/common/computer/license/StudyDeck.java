package com.arrl.radiocraft.common.computer.license;

import com.arrl.radiocraft.common.computer.license.LicensePool.Question;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * One player's flash cards for one question pool (License Study, computer apps 2): spaced repetition counted in cards,
 * not days, since a study session is short. Every answer moves the deck's {@link #step} on by one. A card answered right
 * climbs a {@link Card#level level} and is not due again for {@link #INTERVALS} more cards (1, 3, 8, 20, 50, 120); a
 * wrong one drops to level 0 and comes back after {@link #WRONG_AGAIN} cards; "I don't know" drops it too and brings it
 * back sooner still ({@link #IDK_AGAIN}), counted apart from wrong answers. {@link #next} picks the most overdue card,
 * else a card never seen (from the subelement least studied), else the one due soonest. Pure apart from its NBT form,
 * so it is unit tested.
 */
public final class StudyDeck {

	/** How an answer went. */
	public enum Result { CORRECT, WRONG, IDK }

	/** Cards before a card answered right comes back, by its new level (1 to 6). */
	public static final int[] INTERVALS = { 1, 3, 8, 20, 50, 120 };
	public static final int MAX_LEVEL = INTERVALS.length;
	/** Level from which a card counts as mastered. */
	public static final int MASTERED_LEVEL = 3;
	/** Cards before a card answered wrong comes back. */
	public static final int WRONG_AGAIN = 3;
	/** Cards before a card answered "I don't know" comes back. */
	public static final int IDK_AGAIN = 2;

	/** One question's history for this player. */
	public static final class Card {
		int level;
		long due;
		int correct, wrong, idk;

		public int level() {
			return level;
		}

		public long due() {
			return due;
		}

		public int correct() {
			return correct;
		}

		public int wrong() {
			return wrong;
		}

		public int idk() {
			return idk;
		}

		public int answers() {
			return correct + wrong + idk;
		}

		public boolean mastered() {
			return level >= MASTERED_LEVEL;
		}
	}

	private final Map<String, Card> cards = new HashMap<>();
	private long step = 0L;

	/** Answers given so far in this pool. */
	public long step() {
		return step;
	}

	/** The card for {@code questionId}, or null if it was never answered. */
	@Nullable
	public Card card(String questionId) {
		return cards.get(questionId);
	}

	public boolean isEmpty() {
		return cards.isEmpty();
	}

	public void clear() {
		cards.clear();
		step = 0L;
	}

	/** Records an answer to {@code questionId}; returns its card. */
	public Card answer(String questionId, Result result) {
		step++;
		Card card = cards.computeIfAbsent(questionId, id -> new Card());
		switch(result) {
			case CORRECT -> {
				card.correct++;
				card.level = Math.min(MAX_LEVEL, card.level + 1);
				card.due = step + INTERVALS[card.level - 1];
			}
			case WRONG -> {
				card.wrong++;
				card.level = 0;
				card.due = step + WRONG_AGAIN;
			}
			case IDK -> {
				card.idk++;
				card.level = 0;
				card.due = step + IDK_AGAIN;
			}
		}
		return card;
	}

	/**
	 * The next card to study from {@code pool} (figure questions left out), only from subelement {@code subelement}
	 * when it is not null: the most overdue card due now; else a new card, from the subelement with the smallest share
	 * seen; else the card due soonest. Null only if there are no questions to study.
	 */
	@Nullable
	public Question next(LicensePool pool, @Nullable String subelement, RandomGenerator random) {
		List<Question> candidates = new ArrayList<>();
		for(Question question : pool.studyable())
			if(subelement == null || question.subelement().equals(subelement))
				candidates.add(question);
		if(candidates.isEmpty())
			return null;
		Question overdue = null;
		Question soonest = null;
		Map<String, int[]> bySub = new HashMap<>(); // seen, total
		for(Question question : candidates) {
			Card card = cards.get(question.id());
			int[] counts = bySub.computeIfAbsent(question.subelement(), s -> new int[2]);
			counts[1]++;
			if(card == null)
				continue;
			counts[0]++;
			if(card.due <= step && (overdue == null || isBefore(card, cards.get(overdue.id()))))
				overdue = question;
			if(soonest == null || isBefore(card, cards.get(soonest.id())))
				soonest = question;
		}
		if(overdue != null)
			return overdue;
		String leastSeen = null;
		double share = Double.MAX_VALUE;
		for(String sub : pool.subelements()) {
			int[] counts = bySub.get(sub);
			if(counts == null || counts[0] >= counts[1])
				continue;
			double s = counts[0] / (double)counts[1];
			if(s < share) {
				share = s;
				leastSeen = sub;
			}
		}
		if(leastSeen != null) {
			List<Question> fresh = new ArrayList<>();
			for(Question question : candidates)
				if(question.subelement().equals(leastSeen) && !cards.containsKey(question.id()))
					fresh.add(question);
			return fresh.get(random.nextInt(fresh.size()));
		}
		return soonest;
	}

	/** Due first, then the lower level. */
	private static boolean isBefore(Card a, Card b) {
		return a.due < b.due || a.due == b.due && a.level < b.level;
	}

	/**
	 * The chance this player answers {@code questionId} right, from its card: a guess (1 in 4) if never seen, 0.35 just
	 * after a miss, rising with the level to 0.97.
	 */
	public double chance(String questionId) {
		Card card = cards.get(questionId);
		if(card == null)
			return 0.25D;
		return LEVEL_CHANCE[Math.min(card.level, LEVEL_CHANCE.length - 1)];
	}

	private static final double[] LEVEL_CHANCE = { 0.35D, 0.6D, 0.75D, 0.85D, 0.92D, 0.95D, 0.97D };

	// -------------------- SAVING --------------------

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("step", step);
		CompoundTag all = new CompoundTag();
		cards.forEach((id, card) -> all.putIntArray(id, new int[] { card.level, (int)(card.due - step), card.correct, card.wrong, card.idk }));
		tag.put("cards", all);
		return tag;
	}

	public static StudyDeck load(CompoundTag tag) {
		StudyDeck deck = new StudyDeck();
		deck.step = Math.max(0L, tag.getLong("step"));
		CompoundTag all = tag.getCompound("cards");
		for(String id : all.getAllKeys()) {
			int[] values = all.getIntArray(id);
			if(values.length < 5)
				continue;
			Card card = new Card();
			card.level = Math.max(0, Math.min(MAX_LEVEL, values[0]));
			card.due = deck.step + values[1];
			card.correct = values[2];
			card.wrong = values[3];
			card.idk = values[4];
			deck.cards.put(id, card);
		}
		return deck;
	}
}
