package com.arrl.radiocraft.common.computer.games;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PILEUP's rules (1.12 step 8): its own pieces, turning, contacts (rows) worked, bands and the end. */
class PileupGameTest {

	private static final PileupGame.Shape DIT = PileupGame.SHAPES.getFirst();

	/** The dit stood on end in column 0 of the board, its foot on row {@code bottom}. */
	private static PileupGame.Piece upright(int bottom) {
		return new PileupGame.Piece(DIT, 1, -1, bottom - 2);
	}

	@Test
	void tenPiecesOfThreeToFiveCellsNoneTheSame() {
		assertEquals(10, PileupGame.SHAPES.size());
		Set<String> names = new HashSet<>();
		int threes = 0, fives = 0;
		for(PileupGame.Shape shape : PileupGame.SHAPES) {
			assertTrue(names.add(shape.name()));
			assertTrue(shape.count() >= 3 && shape.count() <= 5, shape.name());
			for(int i = 0; i < shape.cells().length; i++)
				assertTrue(shape.cells()[i] >= 0 && shape.cells()[i] < shape.size(), shape.name() + " sticks out of its box");
			if(shape.count() == 3)
				threes++;
			if(shape.count() == 5)
				fives++;
		}
		assertEquals(2, threes);
		assertEquals(3, fives);
	}

	@Test
	void fourTurnsComeBackToTheStart() {
		for(PileupGame.Shape shape : PileupGame.SHAPES) {
			PileupGame.Piece piece = new PileupGame.Piece(shape, 0, 3, 3);
			int[] start = sorted(piece.cells());
			assertTrue(Arrays.equals(start, sorted(piece.turned(1).turned(1).turned(1).turned(1).cells())), shape.name());
			PileupGame.Piece turned = new PileupGame.Piece(shape, 1, 3, 3);
			assertEquals(shape.count() * 2, turned.cells().length);
		}
		// The dit lies flat and stands up when turned.
		int[] flat = new PileupGame.Piece(DIT, 0, 0, 0).cells(), upright = new PileupGame.Piece(DIT, 1, 0, 0).cells();
		assertEquals(flat[1], flat[3]);
		assertEquals(upright[0], upright[2]);
	}

	private static int[] sorted(int[] cells) {
		int[] keys = new int[cells.length / 2];
		for(int i = 0; i < keys.length; i++)
			keys[i] = cells[i * 2 + 1] * 100 + cells[i * 2];
		Arrays.sort(keys);
		return keys;
	}

	@Test
	void startsWithAPieceAndTheNextOne() {
		PileupGame game = new PileupGame(7L);
		assertFalse(game.over());
		assertTrue(game.fits(game.piece()));
		assertEquals(0, game.band());
		assertEquals("160m", game.bandName());
	}

	@Test
	void theWallsStopIt() {
		PileupGame game = new PileupGame(7L);
		int moves = 0;
		while(game.left() && moves < 20)
			moves++;
		assertTrue(moves < 20);
		int[] cells = game.piece().cells();
		int left = Integer.MAX_VALUE;
		for(int i = 0; i < cells.length; i += 2)
			left = Math.min(left, cells[i]);
		assertEquals(0, left);
	}

	@Test
	void aFullRowIsAContactAndLeavesTheBand() {
		PileupGame game = new PileupGame(7L);
		game.clearBoard();
		game.fillRow(PileupGame.HEIGHT - 1, 0);
		game.setPiece(upright(PileupGame.HEIGHT - 1));
		game.hardDrop();
		assertEquals(1, game.qsos());
		assertEquals(1, game.takeCleared());
		assertEquals(0, game.takeCleared());
		assertEquals(100, game.score());
		// The rest of the dit came down a row; the rest of the band is empty.
		assertNotEquals(-1, game.cell(0, PileupGame.HEIGHT - 1));
		assertNotEquals(-1, game.cell(0, PileupGame.HEIGHT - 2));
		assertEquals(-1, game.cell(1, PileupGame.HEIGHT - 1));
	}

	@Test
	void eightContactsMoveUpABandAndItFallsFaster() {
		PileupGame game = new PileupGame(7L);
		int slow = game.fallInterval();
		for(int i = 0; i < PileupGame.QSOS_PER_BAND; i++) {
			game.clearBoard();
			game.fillRow(PileupGame.HEIGHT - 1, 0);
			game.setPiece(upright(PileupGame.HEIGHT - 1));
			game.hardDrop();
		}
		assertEquals(PileupGame.QSOS_PER_BAND, game.qsos());
		assertEquals(1, game.band());
		assertEquals("80m", game.bandName());
		assertTrue(game.fallInterval() < slow);
	}

	@Test
	void hardDropScoresTwoARow() {
		PileupGame game = new PileupGame(7L);
		game.clearBoard();
		game.setPiece(upright(5));
		game.hardDrop();
		assertEquals((PileupGame.HEIGHT - 1 - 5) * 2, game.score());
	}

	@Test
	void aFullBandEndsTheGame() {
		PileupGame game = new PileupGame(7L);
		for(int y = 1; y < PileupGame.HEIGHT; y++)
			game.fillRow(y, PileupGame.WIDTH - 1);
		for(int i = 0; i < 5 && !game.over(); i++)
			game.hardDrop();
		assertTrue(game.over());
		int score = game.score();
		game.hardDrop();
		game.tick();
		assertEquals(score, game.score());
	}

	@Test
	void ticksMakeItFall() {
		PileupGame game = new PileupGame(7L);
		game.clearBoard();
		game.setPiece(upright(5));
		for(int i = 0; i < game.fallInterval(); i++)
			game.tick();
		assertEquals(4, game.piece().y());
	}
}
