package com.arrl.radiocraft.common.computer.games;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SNAKE's rules (1.12 step 8): moving, turning, growing, the walls and its own tail. */
class SnakeGameTest {

	@Test
	void startsHeadingRightAtItsLength() {
		SnakeGame game = new SnakeGame(20, 10, 1L);
		assertEquals(SnakeGame.START_LENGTH, game.length());
		assertEquals(SnakeGame.Heading.RIGHT, game.heading());
		assertEquals(SnakeGame.Cell.HEAD, game.at(game.headX(), game.headY()));
		assertTrue(game.foodX() >= 0);
		assertTrue(game.alive());
	}

	@Test
	void movesOneCellAStepAndKeepsItsLength() {
		SnakeGame game = new SnakeGame(20, 10, 1L);
		game.foodAt(0, 0);
		int x = game.headX();
		assertTrue(game.step());
		assertEquals(x + 1, game.headX());
		assertEquals(SnakeGame.START_LENGTH, game.length());
	}

	@Test
	void cannotTurnStraightBack() {
		SnakeGame game = new SnakeGame(20, 10, 1L);
		game.foodAt(0, 0);
		game.turn(SnakeGame.Heading.LEFT);
		int x = game.headX();
		game.step();
		assertEquals(SnakeGame.Heading.RIGHT, game.heading());
		assertEquals(x + 1, game.headX());
	}

	@Test
	void twoQuickTurnsBetweenStepsAreBothKept() {
		SnakeGame game = new SnakeGame(20, 10, 1L);
		game.foodAt(0, 0);
		int x = game.headX(), y = game.headY();
		game.turn(SnakeGame.Heading.UP);
		game.turn(SnakeGame.Heading.LEFT);
		game.step();
		game.step();
		assertEquals(x - 1, game.headX());
		assertEquals(y - 1, game.headY());
	}

	@Test
	void eatingScoresAndGrows() {
		SnakeGame game = new SnakeGame(20, 10, 1L);
		game.foodAt(game.headX() + 1, game.headY());
		game.step();
		assertEquals(SnakeGame.POINTS, game.score());
		assertEquals(1, game.eaten());
		for(int i = 0; i < 3; i++) {
			game.foodAt(0, 0);
			game.step();
		}
		assertEquals(SnakeGame.START_LENGTH + SnakeGame.GROWTH, game.length());
	}

	@Test
	void theWallKills() {
		SnakeGame game = new SnakeGame(12, 6, 1L);
		game.foodAt(0, 0);
		int steps = 0;
		while(game.step())
			steps++;
		assertFalse(game.alive());
		assertEquals(12 - 1 - (12 / 4 + SnakeGame.START_LENGTH - 1), steps);
		assertFalse(game.step());
	}

	@Test
	void bitingItsOwnBodyKills() {
		SnakeGame game = new SnakeGame(20, 10, 1L);
		// Grow it long enough to bite itself: eat three times in a row.
		for(int i = 0; i < 3; i++) {
			game.foodAt(game.headX() + 1, game.headY());
			game.step();
		}
		game.foodAt(0, 0);
		game.turn(SnakeGame.Heading.DOWN);
		game.step();
		game.turn(SnakeGame.Heading.LEFT);
		game.step();
		game.turn(SnakeGame.Heading.UP);
		assertFalse(game.step());
		assertFalse(game.alive());
	}

	@Test
	void speedsUpAsItEats() {
		SnakeGame game = new SnakeGame(40, 10, 1L);
		int start = game.stepTicks();
		for(int i = 0; i < 10; i++) {
			game.foodAt(game.headX() + 1, game.headY());
			game.step();
		}
		assertTrue(game.stepTicks() < start);
		assertTrue(game.stepTicks() >= 2);
	}
}
