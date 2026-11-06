package com.arrl.radiocraft.common.computer.license;

import com.arrl.radiocraft.common.computer.license.LicensePool.Question;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Practice exams and the aptitude estimate for License Study (computer apps 2). An exam draws one question from each
 * group of the pool (T1A, T1B... : 35 for Technician and General, 50 for Extra, as the VEs' exams do), leaving out
 * questions that need a figure; 74 percent passes (26 of 35, 37 of 50). The estimate turns each question's card into a
 * chance of answering it right ({@link StudyDeck#chance}), averages it per group (one question per group comes up), and
 * works out the chance of reaching the pass mark with those odds (a Poisson binomial, by dynamic programming). Pure, so
 * it is unit tested.
 */
public final class LicenseExam {

	private LicenseExam() {}

	/** One question per exam group, in the pool's order (figure questions never drawn). */
	public static List<Question> draw(LicensePool pool, RandomGenerator random) {
		List<Question> out = new ArrayList<>();
		for(String group : pool.examGroups()) {
			List<Question> usable = pool.groups().get(group).stream().filter(q -> !q.hasFigure()).toList();
			out.add(usable.get(random.nextInt(usable.size())));
		}
		return out;
	}

	/** An exam marked: how many right, the pass mark, and whether it passed. */
	public record Score(int correct, int total, int passMark) {

		public boolean passed() {
			return correct >= passMark;
		}
	}

	/** Marks {@code choices} (answer indexes, -1 for none) against {@code questions}. */
	public static Score grade(LicensePool pool, List<Question> questions, int[] choices) {
		int right = 0;
		for(int i = 0; i < questions.size(); i++)
			if(i < choices.length && choices[i] == questions.get(i).correct())
				right++;
		return new Score(right, questions.size(), LicensePool.passMark(questions.size(), pool.passPercent()));
	}

	// -------------------- APTITUDE --------------------

	/**
	 * How ready a player is: {@code expected} score out of {@code total} questions, {@code passMark} needed, and the
	 * chance (0 to 1) of passing an exam drawn now.
	 */
	public record Estimate(double expected, int total, int passMark, double passChance) {}

	public static Estimate estimate(LicensePool pool, StudyDeck deck) {
		List<String> groups = pool.examGroups();
		double[] chances = new double[groups.size()];
		double expected = 0.0D;
		for(int i = 0; i < chances.length; i++) {
			double sum = 0.0D;
			int n = 0;
			for(Question question : pool.groups().get(groups.get(i))) {
				if(question.hasFigure())
					continue;
				sum += deck.chance(question.id());
				n++;
			}
			chances[i] = n == 0 ? 0.0D : sum / n;
			expected += chances[i];
		}
		int mark = LicensePool.passMark(chances.length, pool.passPercent());
		return new Estimate(expected, chances.length, mark, atLeast(chances, mark));
	}

	/** The chance that at least {@code needed} of independent events with chances {@code p} happen. */
	public static double atLeast(double[] p, int needed) {
		double[] ways = new double[p.length + 1]; // ways[k]: chance of exactly k so far.
		ways[0] = 1.0D;
		for(int i = 0; i < p.length; i++)
			for(int k = i + 1; k >= 0; k--)
				ways[k] = ways[k] * (1.0D - p[i]) + (k > 0 ? ways[k - 1] * p[i] : 0.0D);
		double out = 0.0D;
		for(int k = Math.max(0, needed); k <= p.length; k++)
			out += ways[k];
		return Math.max(0.0D, Math.min(1.0D, out));
	}

	// -------------------- PROGRESS --------------------

	/** One subelement's progress: questions to study, how many seen and mastered. */
	public record SubelementProgress(String code, String name, int total, int seen, int mastered) {}

	public static List<SubelementProgress> progress(LicensePool pool, StudyDeck deck) {
		List<SubelementProgress> out = new ArrayList<>();
		for(String sub : pool.subelements()) {
			int total = 0, seen = 0, mastered = 0;
			for(Question question : pool.studyable()) {
				if(!question.subelement().equals(sub))
					continue;
				total++;
				StudyDeck.Card card = deck.card(question.id());
				if(card != null) {
					seen++;
					if(card.mastered())
						mastered++;
				}
			}
			out.add(new SubelementProgress(sub, pool.subelementName(sub), total, seen, mastered));
		}
		return out;
	}
}
