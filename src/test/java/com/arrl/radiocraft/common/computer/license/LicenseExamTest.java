package com.arrl.radiocraft.common.computer.license;

import com.arrl.radiocraft.common.computer.license.LicensePool.Question;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseExamTest {

	/** {@code groups} groups (S1A, S1B...) of three questions; group 0's first two need a figure, group 1 only figures. */
	private static LicensePool pool(int groups) {
		List<Question> questions = new ArrayList<>();
		for(int g = 0; g < groups; g++)
			for(int q = 1; q <= 3; q++) {
				String figure = g == 0 && q < 3 || g == 1 ? "F-1" : "";
				questions.add(new Question("S" + (g / 20 + 1) + (char)('A' + g % 20) + "0" + q, "Q", List.of("a", "b", "c", "d"), q % 4, "", figure));
			}
		return new LicensePool("test", "Test", "", "", 74, 1, Map.of(), questions, List.of());
	}

	@Test
	void drawsOneQuestionPerGroupWithoutFigures() {
		LicensePool pool = pool(10);
		List<Question> exam = LicenseExam.draw(pool, new Random(5L));
		assertEquals(9, exam.size()); // Group 1 has only figure questions: skipped.
		assertEquals(List.of("S1B"), pool.skippedGroups());
		Set<String> groups = new HashSet<>();
		for(Question question : exam) {
			assertFalse(question.hasFigure());
			assertTrue(groups.add(question.group()));
		}
		assertEquals("S1A03", exam.getFirst().id()); // The only one in group 0 with no figure.
		assertEquals(exam, LicenseExam.draw(pool, new Random(5L)), "the same seed draws the same exam");
	}

	@Test
	void gradesAgainstThePassMark() {
		LicensePool pool = pool(10);
		List<Question> exam = LicenseExam.draw(pool, new Random(2L));
		int[] choices = new int[exam.size()];
		for(int i = 0; i < choices.length; i++)
			choices[i] = i < 7 ? exam.get(i).correct() : -1;
		LicenseExam.Score score = LicenseExam.grade(pool, exam, choices);
		assertEquals(7, score.correct());
		assertEquals(9, score.total());
		assertEquals(7, score.passMark()); // 74 percent of 9 is 6.66: 7.
		assertTrue(score.passed());
		choices[0] = (choices[0] + 1) % 4;
		assertFalse(LicenseExam.grade(pool, exam, choices).passed());
		assertEquals(0, LicenseExam.grade(pool, exam, new int[0]).correct());
	}

	@Test
	void atLeastIsAPoissonBinomial() {
		assertEquals(1.0D, LicenseExam.atLeast(new double[] { 0.3D, 0.7D }, 0), 1e-12);
		assertEquals(0.3D * 0.7D, LicenseExam.atLeast(new double[] { 0.3D, 0.7D }, 2), 1e-12);
		assertEquals(1.0D - 0.7D * 0.3D, LicenseExam.atLeast(new double[] { 0.3D, 0.7D }, 1), 1e-12);
		double[] coin = new double[10];
		java.util.Arrays.fill(coin, 0.5D);
		assertEquals(386.0D / 1024.0D, LicenseExam.atLeast(coin, 6), 1e-12); // C(10,6..10) = 210+120+45+10+1.
		assertEquals(0.0D, LicenseExam.atLeast(coin, 11), 1e-12);
	}

	@Test
	void studyRaisesThePassChance() {
		LicensePool pool = StudyDeckTest.pool(2, 40);
		StudyDeck deck = new StudyDeck();
		LicenseExam.Estimate fresh = LicenseExam.estimate(pool, deck);
		assertEquals(2, fresh.total()); // Groups S1A and S2A.
		for(Question question : pool.studyable())
			for(int i = 0; i < 4; i++)
				deck.answer(question.id(), StudyDeck.Result.CORRECT);
		LicenseExam.Estimate studied = LicenseExam.estimate(pool, deck);
		assertTrue(studied.expected() > fresh.expected());
		assertTrue(studied.passChance() > 0.8D);
		assertTrue(fresh.passChance() < 0.3D);
		List<LicenseExam.SubelementProgress> progress = LicenseExam.progress(pool, deck);
		assertEquals(2, progress.size());
		assertEquals(39, progress.getFirst().total()); // One figure question left out.
		assertEquals(39, progress.getFirst().mastered());
	}
}
