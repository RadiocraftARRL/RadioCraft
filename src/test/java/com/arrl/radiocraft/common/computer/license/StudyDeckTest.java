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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudyDeckTest {

	/** {@code subs} subelements of {@code perSub} questions each (S1A01...), the last question of S1 a figure one. */
	static LicensePool pool(int subs, int perSub) {
		List<Question> questions = new ArrayList<>();
		for(int s = 1; s <= subs; s++)
			for(int q = 1; q <= perSub; q++)
				questions.add(new Question(String.format("S%dA%02d", s, q), "Q", List.of("a", "b", "c", "d"), q % 4, "",
						s == 1 && q == perSub ? "S-1" : ""));
		return new LicensePool("test", "Test", "", "", 74, 1, Map.of(), questions, List.of());
	}

	@Test
	void rightAnswersSpaceTheCardOut() {
		StudyDeck deck = new StudyDeck();
		long[] gaps = new long[StudyDeck.INTERVALS.length + 1];
		for(int i = 0; i < gaps.length; i++) {
			StudyDeck.Card card = deck.answer("Q", StudyDeck.Result.CORRECT);
			gaps[i] = card.due() - deck.step();
		}
		assertEquals(1, gaps[0]);
		assertEquals(3, gaps[1]);
		assertEquals(120, gaps[5]);
		assertEquals(120, gaps[6]); // Stays at the top level.
		assertEquals(StudyDeck.MAX_LEVEL, deck.card("Q").level());
		assertTrue(deck.card("Q").mastered());
	}

	@Test
	void wrongAndIdkBringItBackSoonAndCountApart() {
		StudyDeck deck = new StudyDeck();
		deck.answer("Q", StudyDeck.Result.CORRECT);
		deck.answer("Q", StudyDeck.Result.CORRECT);
		StudyDeck.Card card = deck.answer("Q", StudyDeck.Result.WRONG);
		assertEquals(0, card.level());
		assertEquals(StudyDeck.WRONG_AGAIN, card.due() - deck.step());
		card = deck.answer("Q", StudyDeck.Result.IDK);
		assertEquals(StudyDeck.IDK_AGAIN, card.due() - deck.step());
		assertEquals(2, card.correct());
		assertEquals(1, card.wrong());
		assertEquals(1, card.idk());
		assertTrue(StudyDeck.IDK_AGAIN < StudyDeck.WRONG_AGAIN);
	}

	@Test
	void newCardsComeFromTheLeastStudiedSubelementAndSkipFigures() {
		LicensePool pool = pool(3, 4);
		StudyDeck deck = new StudyDeck();
		Random random = new Random(7L);
		Set<String> subsFirst = new HashSet<>();
		int fresh = 0;
		while(fresh < 3) { // Cards due again come first; count only the new ones.
			Question q = deck.next(pool, null, random);
			assertNotNull(q);
			if(deck.card(q.id()) == null) {
				subsFirst.add(q.subelement());
				fresh++;
			}
			deck.answer(q.id(), StudyDeck.Result.CORRECT);
		}
		assertEquals(3, subsFirst.size(), "the first three new cards spread over the three subelements");
		for(int i = 0; i < 200; i++) {
			Question q = deck.next(pool, null, random);
			assertTrue(!q.hasFigure(), "a figure question came up");
			deck.answer(q.id(), StudyDeck.Result.CORRECT);
		}
		assertEquals(11, pool.studyable().size());
		for(Question q : pool.studyable())
			assertNotNull(deck.card(q.id()), q.id() + " never came up");
	}

	@Test
	void anOverdueCardComesBeforeNewOnes() {
		LicensePool pool = pool(1, 10);
		StudyDeck deck = new StudyDeck();
		Random random = new Random(1L);
		Question missed = deck.next(pool, null, random);
		deck.answer(missed.id(), StudyDeck.Result.WRONG); // Due in 3.
		List<String> order = new ArrayList<>();
		for(int i = 0; i < 4; i++) {
			Question q = deck.next(pool, null, random);
			order.add(q.id());
			deck.answer(q.id(), StudyDeck.Result.CORRECT);
		}
		for(int i = 0; i < 3; i++)
			assertNotEquals(missed.id(), order.get(i));
		// Three cards on it is due, level with the second new card; the lower level (the missed one) goes first. The
		// first new card, right once, was due again after one card and came back third.
		assertEquals(missed.id(), order.get(3), "the missed card came back after three cards: " + order);
		assertEquals(order.get(0), order.get(2));
	}

	@Test
	void aFilterKeepsToOneSubelement() {
		LicensePool pool = pool(3, 4);
		StudyDeck deck = new StudyDeck();
		Random random = new Random(3L);
		for(int i = 0; i < 20; i++) {
			Question q = deck.next(pool, "S2", random);
			assertEquals("S2", q.subelement());
			deck.answer(q.id(), StudyDeck.Result.WRONG);
		}
		assertNull(deck.next(pool, "S9", random));
	}

	@Test
	void savesAndLoads() {
		StudyDeck deck = new StudyDeck();
		deck.answer("A", StudyDeck.Result.CORRECT);
		deck.answer("B", StudyDeck.Result.WRONG);
		deck.answer("A", StudyDeck.Result.CORRECT);
		StudyDeck loaded = StudyDeck.load(deck.save());
		assertEquals(deck.step(), loaded.step());
		assertEquals(deck.card("A").due(), loaded.card("A").due());
		assertEquals(2, loaded.card("A").level());
		assertEquals(1, loaded.card("B").wrong());
		assertEquals(deck.chance("A"), loaded.chance("A"));
		assertEquals(0.25D, loaded.chance("never"));
	}
}
