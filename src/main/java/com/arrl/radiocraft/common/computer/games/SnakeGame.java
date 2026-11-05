package com.arrl.radiocraft.common.computer.games;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * SNAKE for the radio computer (1.12 step 8): the snake moves one cell a step on a walled board, turns at the arrow
 * keys, grows by {@link #GROWTH} each time it eats, and dies on the wall or its own body. Pure (no game classes), so it
 * is unit tested; the screen ({@code client.screens.computer.apps.SnakeApp}) calls {@link #step} as often as
 * {@link #stepTicks} says and draws {@link #at}.
 * <p>
 * Turns are queued (two at most), so a quick up-left between two steps is not lost; a turn straight back is ignored.
 */
public final class SnakeGame {

	/** A direction the snake can head in. */
	public enum Heading {
		UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

		public final int dx, dy;

		Heading(int dx, int dy) {
			this.dx = dx;
			this.dy = dy;
		}

		public boolean opposite(Heading other) {
			return dx == -other.dx && dy == -other.dy;
		}
	}

	/** What is in a cell. */
	public enum Cell { EMPTY, BODY, HEAD, FOOD }

	/** Cells the snake grows by for each thing it eats. */
	public static final int GROWTH = 2;
	/** Points for each thing eaten. */
	public static final int POINTS = 10;
	/** The length it starts at. */
	public static final int START_LENGTH = 4;

	private final int width, height;
	private final Random random;
	/** The snake, head first, as cell indices (y * width + x). */
	private final Deque<Integer> body = new ArrayDeque<>();
	private final boolean[] occupied;
	private final Deque<Heading> turns = new ArrayDeque<>(2);
	private Heading heading = Heading.RIGHT;
	private int food = -1;
	private int grow;
	private int score;
	private int eaten;
	private boolean alive = true;

	public SnakeGame(int width, int height, long seed) {
		if(width < START_LENGTH + 4 || height < 4)
			throw new IllegalArgumentException("A snake board needs room: " + width + "x" + height);
		this.width = width;
		this.height = height;
		this.random = new Random(seed);
		this.occupied = new boolean[width * height];
		int y = height / 2;
		for(int i = 0; i < START_LENGTH; i++) {
			int cell = y * width + (width / 4 + START_LENGTH - 1 - i);
			body.addLast(cell);
			occupied[cell] = true;
		}
		placeFood();
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public int score() {
		return score;
	}

	public int length() {
		return body.size();
	}

	public int eaten() {
		return eaten;
	}

	public boolean alive() {
		return alive;
	}

	public Heading heading() {
		return heading;
	}

	/** The food's cell (x, y), or -1 when the board is full. */
	public int foodX() {
		return food < 0 ? -1 : food % width;
	}

	public int foodY() {
		return food < 0 ? -1 : food / width;
	}

	/** The head's cell. */
	public int headX() {
		return body.getFirst() % width;
	}

	public int headY() {
		return body.getFirst() / width;
	}

	/** What is at ({@code x}, {@code y}). */
	public Cell at(int x, int y) {
		if(x < 0 || y < 0 || x >= width || y >= height)
			return Cell.EMPTY;
		int cell = y * width + x;
		if(cell == body.getFirst())
			return Cell.HEAD;
		if(occupied[cell])
			return Cell.BODY;
		return cell == food ? Cell.FOOD : Cell.EMPTY;
	}

	/** Asks to turn to {@code to} at the next step (after any turn already asked). A turn back on itself is ignored. */
	public void turn(Heading to) {
		Heading last = turns.isEmpty() ? heading : turns.peekLast();
		if(to == last || to.opposite(last) || turns.size() >= 2)
			return;
		turns.addLast(to);
	}

	/**
	 * Client ticks between two steps: 6 at the start, one fewer for each five things eaten, never under 2
	 * (10 steps a second at 20 ticks).
	 */
	public int stepTicks() {
		return Math.max(2, 6 - eaten / 5);
	}

	/** One step. Returns false once the snake is dead (and from then on). */
	public boolean step() {
		if(!alive)
			return false;
		if(!turns.isEmpty())
			heading = turns.pollFirst();
		int head = body.getFirst();
		int x = head % width + heading.dx, y = head / width + heading.dy;
		if(x < 0 || y < 0 || x >= width || y >= height) {
			alive = false;
			return false;
		}
		int next = y * width + x;
		boolean eats = next == food;
		// The tail moves out of the way this step unless the snake is growing (or eats now: that is one cell of growth).
		if(!eats) {
			if(grow > 0)
				grow--;
			else
				occupied[body.removeLast()] = false;
		}
		if(occupied[next]) {
			alive = false;
			return false;
		}
		body.addFirst(next);
		occupied[next] = true;
		if(eats) {
			score += POINTS;
			eaten++;
			grow += GROWTH - 1; // This step already grew it by one.
			placeFood();
		}
		return true;
	}

	/** Puts the food on a free cell at random (none when the snake fills the board). */
	private void placeFood() {
		int free = width * height - body.size();
		if(free <= 0) {
			food = -1;
			return;
		}
		int pick = random.nextInt(free);
		for(int cell = 0; cell < occupied.length; cell++) {
			if(occupied[cell])
				continue;
			if(pick-- == 0) {
				food = cell;
				return;
			}
		}
		food = -1;
	}

	/** For tests: puts the food at ({@code x}, {@code y}) if that cell is free. */
	void foodAt(int x, int y) {
		int cell = y * width + x;
		if(!occupied[cell])
			food = cell;
	}
}
