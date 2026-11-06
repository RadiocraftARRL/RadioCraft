package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.license.LicenseExam;
import com.arrl.radiocraft.common.computer.license.LicensePool;
import com.arrl.radiocraft.common.computer.license.LicensePool.Question;
import com.arrl.radiocraft.common.computer.license.LicensePools;
import com.arrl.radiocraft.common.computer.license.LicenseProgress;
import com.arrl.radiocraft.common.computer.license.StudyDeck;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The License Study program's server side (computer apps 2, 2026-11-06): flash cards and practice exams from the
 * question pools ({@link LicensePools}), with each player's progress in {@link LicenseProgress} (server wide, so it
 * follows them to any computer). The pools stay on the server; the screen asks for one card or one exam at a time.
 * <ul>
 * <li>{@code pools}: {@code pools}, each {@code id}, {@code name}, {@code element}, {@code valid}, {@code questions}
 * (to study), {@code figures} (left out), {@code examSize}, {@code passMark}, {@code seen}, {@code mastered},
 * {@code expected}, {@code chance} (0..1).</li>
 * <li>{@code progress} {pool}: {@code subs} (each {@code code}, {@code name}, {@code total}, {@code seen},
 * {@code mastered}), {@code expected}, {@code total}, {@code passMark}, {@code chance}, {@code answered},
 * {@code correct}, {@code wrong}, {@code idk}.</li>
 * <li>{@code next} {pool, sub ("" for all)}: a card: {@code id}, {@code sub}, {@code subName}, {@code question},
 * {@code answers}, {@code refs}, {@code level}, {@code seen} (answers before), {@code new}.</li>
 * <li>{@code answer} {pool, id, choice (-1 = I don't know)}: {@code correct} (the right index), {@code right},
 * {@code level}, {@code again} (cards until it comes back).</li>
 * <li>{@code exam} {pool}: {@code questions} (each {@code id}, {@code sub}, {@code question}, {@code answers}: no
 * correct answer), {@code passMark}, {@code skipped} (groups left out: all their questions need a figure).</li>
 * <li>{@code grade} {pool, ids, choices}: {@code correct} (right indexes), {@code score}, {@code total},
 * {@code passMark}, {@code passed}. Each answer also counts on its card (no answer as "I don't know").</li>
 * <li>{@code reset} {pool}: forgets the player's cards for the pool.</li>
 * </ul>
 * Errors: {@code study_no_pools}, {@code study_unknown_pool}, {@code study_unknown_question}, {@code study_nothing}.
 */
public final class LicenseStudyHandler implements ComputerAppHandler {

	/** Most questions a grade request may carry. */
	public static final int MAX_EXAM = 100;

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		if(LicensePools.all().isEmpty()) {
			reply.error("study_no_pools");
			return;
		}
		if(action.equals("pools")) {
			reply.ok(pools(context));
			return;
		}
		LicensePool pool = LicensePools.get(data.getString("pool"));
		if(pool == null) {
			reply.error("study_unknown_pool");
			return;
		}
		LicenseProgress progress = LicenseProgress.get(context.server());
		StudyDeck deck = progress.deck(context.player().getUUID(), pool.id());
		Random random = new Random(context.level().getRandom().nextLong());
		switch(action) {
			case "progress" -> reply.ok(progress(pool, deck));
			case "next" -> {
				String sub = data.getString("sub");
				Question question = deck.next(pool, sub.isEmpty() ? null : sub, random);
				if(question == null) {
					reply.error("study_nothing");
					return;
				}
				CompoundTag out = question(question, pool);
				StudyDeck.Card card = deck.card(question.id());
				out.putInt("level", card == null ? 0 : card.level());
				out.putInt("seen", card == null ? 0 : card.answers());
				out.putBoolean("new", card == null);
				reply.ok(out);
			}
			case "answer" -> {
				Question question = pool.question(data.getString("id"));
				if(question == null) {
					reply.error("study_unknown_question");
					return;
				}
				int choice = data.getInt("choice");
				StudyDeck.Result result = choice < 0 ? StudyDeck.Result.IDK : choice == question.correct() ? StudyDeck.Result.CORRECT : StudyDeck.Result.WRONG;
				StudyDeck.Card card = deck.answer(question.id(), result);
				progress.setDirty();
				CompoundTag out = new CompoundTag();
				out.putInt("correct", question.correct());
				out.putBoolean("right", result == StudyDeck.Result.CORRECT);
				out.putInt("level", card.level());
				out.putLong("again", card.due() - deck.step());
				reply.ok(out);
			}
			case "exam" -> {
				CompoundTag out = new CompoundTag();
				ListTag questions = new ListTag();
				for(Question question : LicenseExam.draw(pool, random)) {
					CompoundTag entry = question(question, pool);
					entry.remove("refs"); // The reference gives the rule away no more than the question; kept out like the answer.
					questions.add(entry);
				}
				out.put("questions", questions);
				out.putInt("passMark", pool.passMark());
				out.put("skipped", strings(pool.skippedGroups()));
				reply.ok(out);
			}
			case "grade" -> {
				ListTag ids = data.getList("ids", Tag.TAG_STRING);
				int[] choices = data.getIntArray("choices");
				if(ids.isEmpty() || ids.size() > MAX_EXAM) {
					reply.error("study_unknown_question");
					return;
				}
				List<Question> questions = new ArrayList<>();
				for(int i = 0; i < ids.size(); i++) {
					Question question = pool.question(ids.getString(i));
					if(question == null) {
						reply.error("study_unknown_question");
						return;
					}
					questions.add(question);
				}
				LicenseExam.Score score = LicenseExam.grade(pool, questions, choices);
				int[] correct = new int[questions.size()];
				for(int i = 0; i < questions.size(); i++) {
					Question question = questions.get(i);
					correct[i] = question.correct();
					int choice = i < choices.length ? choices[i] : -1;
					deck.answer(question.id(), choice < 0 ? StudyDeck.Result.IDK : choice == question.correct() ? StudyDeck.Result.CORRECT : StudyDeck.Result.WRONG);
				}
				progress.setDirty();
				CompoundTag out = new CompoundTag();
				out.putIntArray("correct", correct);
				out.putInt("score", score.correct());
				out.putInt("total", score.total());
				out.putInt("passMark", score.passMark());
				out.putBoolean("passed", score.passed());
				reply.ok(out);
			}
			case "reset" -> {
				deck.clear();
				progress.setDirty();
				reply.ok();
			}
			default -> reply.error("unknown_request", action);
		}
	}

	private static CompoundTag pools(ComputerContext context) {
		LicenseProgress progress = LicenseProgress.get(context.server());
		ListTag list = new ListTag();
		for(LicensePool pool : LicensePools.all()) {
			StudyDeck deck = progress.has(context.player().getUUID(), pool.id()) ? progress.deck(context.player().getUUID(), pool.id()) : new StudyDeck();
			CompoundTag entry = new CompoundTag();
			entry.putString("id", pool.id());
			entry.putString("name", pool.name());
			entry.putString("element", pool.element());
			entry.putString("valid", pool.valid());
			int studyable = pool.studyable().size();
			entry.putInt("questions", studyable);
			entry.putInt("figures", pool.questions().size() - studyable);
			entry.putInt("examSize", pool.examSize());
			entry.putInt("passMark", pool.passMark());
			int seen = 0, mastered = 0;
			for(LicenseExam.SubelementProgress sub : LicenseExam.progress(pool, deck)) {
				seen += sub.seen();
				mastered += sub.mastered();
			}
			entry.putInt("seen", seen);
			entry.putInt("mastered", mastered);
			LicenseExam.Estimate estimate = LicenseExam.estimate(pool, deck);
			entry.putDouble("expected", estimate.expected());
			entry.putDouble("chance", estimate.passChance());
			list.add(entry);
		}
		CompoundTag out = new CompoundTag();
		out.put("pools", list);
		return out;
	}

	private static CompoundTag progress(LicensePool pool, StudyDeck deck) {
		CompoundTag out = new CompoundTag();
		ListTag subs = new ListTag();
		int correct = 0, wrong = 0, idk = 0;
		for(LicenseExam.SubelementProgress sub : LicenseExam.progress(pool, deck)) {
			CompoundTag entry = new CompoundTag();
			entry.putString("code", sub.code());
			entry.putString("name", sub.name());
			entry.putInt("total", sub.total());
			entry.putInt("seen", sub.seen());
			entry.putInt("mastered", sub.mastered());
			subs.add(entry);
		}
		for(Question question : pool.questions()) {
			StudyDeck.Card card = deck.card(question.id());
			if(card != null) {
				correct += card.correct();
				wrong += card.wrong();
				idk += card.idk();
			}
		}
		out.put("subs", subs);
		LicenseExam.Estimate estimate = LicenseExam.estimate(pool, deck);
		out.putDouble("expected", estimate.expected());
		out.putInt("total", estimate.total());
		out.putInt("passMark", estimate.passMark());
		out.putDouble("chance", estimate.passChance());
		out.putLong("answered", deck.step());
		out.putInt("correct", correct);
		out.putInt("wrong", wrong);
		out.putInt("idk", idk);
		return out;
	}

	private static CompoundTag question(Question question, LicensePool pool) {
		CompoundTag out = new CompoundTag();
		out.putString("id", question.id());
		out.putString("sub", question.subelement());
		out.putString("subName", pool.subelementName(question.subelement()));
		out.putString("question", question.text());
		out.put("answers", strings(question.answers()));
		out.putString("refs", question.refs());
		return out;
	}

	private static ListTag strings(List<String> values) {
		ListTag out = new ListTag();
		for(String value : values)
			out.add(StringTag.valueOf(value));
		return out;
	}
}
