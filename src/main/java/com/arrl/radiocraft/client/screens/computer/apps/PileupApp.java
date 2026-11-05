package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.games.HighScores;
import com.arrl.radiocraft.common.computer.games.PileupGame;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * PILEUP, a secret program (1.12 step 8): RadioCraft's own falling-blocks game ({@link PileupGame}). The board is a
 * band on a dark spectrum display, the pieces callers drawn as {@code []} in their colours, where each lands shown as
 * {@code ::}. Left and right move, Up or X turns clockwise, Z back, Down drops a row, Space drops all the way, P
 * pauses. The best ten scores on this computer are kept in {@code PILEUP.DAT}.
 */
public final class PileupApp extends DosApp {

	private static final int BOARD_COL = 28, BOARD_ROW = 2;
	private static final int FLASH_TICKS = 6;

	private enum State { TITLE, PLAYING, PAUSED, OVER }

	private final HighScoreFile scores;
	@Nullable
	private PileupGame game;
	private State state = State.TITLE;
	private int place = -1;
	private int flash;

	public PileupApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		this.scores = new HighScoreFile(session, "PILEUP.DAT");
	}

	@Override
	public void opened() {
		scores.load();
	}

	@Override
	public void closed() {
		if(state == State.PLAYING)
			state = State.PAUSED;
	}

	private void start() {
		game = new PileupGame(System.nanoTime());
		state = State.PLAYING;
		place = -1;
		flash = 0;
	}

	@Override
	public void tick() {
		if(flash > 0)
			flash--;
		if(state != State.PLAYING || game == null)
			return;
		game.tick();
		after();
	}

	/** After anything that may land a piece: the flash for contacts, the end of the game. */
	private void after() {
		if(game == null)
			return;
		if(game.takeCleared() > 0)
			flash = FLASH_TICKS;
		if(game.over() && state == State.PLAYING) {
			state = State.OVER;
			place = scores.submit(game.score());
		}
	}

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("pileup.title"), Dos.tr(state == State.PLAYING ? "pileup.keys_play" : "pileup.keys"));
		int bg = DosColours.BODY_BG, fg = DosColours.BODY_FG;
		int border = flash > 0 && flash % 2 == 0 ? DosColours.YELLOW : DosColours.LIGHT_CYAN;
		g.box(BOARD_COL - 1, BOARD_ROW - 1, PileupGame.WIDTH * 2 + 2, PileupGame.HEIGHT + 2, true,
				game == null ? "" : " " + game.bandName() + " ", border, DosColours.BLACK);
		// The band: a dark spectrum display with a faint grid.
		for(int y = 0; y < PileupGame.HEIGHT; y++)
			for(int x = 0; x < PileupGame.WIDTH; x++) {
				int col = BOARD_COL + x * 2, row = BOARD_ROW + y;
				g.set(col, row, x % 3 == 0 && y % 2 == 1 ? '.' : ' ', DosColours.DARK_GREY, DosColours.BLACK);
				g.set(col + 1, row, ' ', DosColours.DARK_GREY, DosColours.BLACK);
			}
		if(game != null) {
			for(int y = 0; y < PileupGame.HEIGHT; y++)
				for(int x = 0; x < PileupGame.WIDTH; x++) {
					int colour = game.cell(x, y);
					if(colour >= 0)
						cell(g, x, y, "[]", colour);
				}
			if(state == State.PLAYING || state == State.PAUSED) {
				int[] ghost = game.ghost().cells();
				for(int i = 0; i < ghost.length; i += 2)
					cell(g, ghost[i], ghost[i + 1], "::", DosColours.DARK_GREY);
				int[] cells = game.piece().cells();
				for(int i = 0; i < cells.length; i += 2)
					cell(g, cells[i], cells[i + 1], "[]", game.piece().shape().colour());
			}
		}

		// Left: the log.
		int r = 2;
		g.box(1, 1, 25, 11, false, Dos.tr("pileup.log"), DosColours.TEXT_FG, bg);
		g.put(3, ++r, Dos.tr("pileup.band", game == null ? PileupGame.BANDS[0] : game.bandName()), fg, bg);
		g.put(3, ++r, Dos.tr("pileup.qsos", game == null ? 0 : game.qsos()), fg, bg);
		g.put(3, ++r, Dos.tr("pileup.score", game == null ? 0 : game.score()), DosColours.YELLOW, bg);
		g.put(3, ++r, Dos.tr("pileup.best", scores.scores().best()), fg, bg);
		r++;
		int toGo = game == null ? PileupGame.QSOS_PER_BAND : PileupGame.QSOS_PER_BAND - game.qsos() % PileupGame.QSOS_PER_BAND;
		g.put(3, ++r, Dos.tr("pileup.next_band", toGo), fg, bg);
		g.bar(3, ++r, 21, 1.0D - toGo / (double)PileupGame.QSOS_PER_BAND, DosColours.LIGHT_GREEN, bg);
		// Under it, the high scores.
		g.box(1, 12, 25, 12, false, Dos.tr("game.high_scores"), DosColours.TEXT_FG, bg);
		HighScores table = scores.scores();
		if(table.entries().isEmpty())
			g.put(3, 13, Dos.tr("game.no_scores"), DosColours.LIGHT_GREY, bg);
		for(int i = 0; i < table.entries().size() && i < 10; i++) {
			HighScores.Entry e = table.entries().get(i);
			int colour = i == place && state == State.OVER ? DosColours.YELLOW : fg;
			g.put(3, 13 + i, String.format(Locale.ROOT, "%2d %-12s%7d", i + 1, Dos.fit(e.name(), 12), e.score()), colour, bg);
		}

		// Right: the next caller and the keys.
		g.box(54, 1, 25, 8, false, Dos.tr("pileup.next"), DosColours.TEXT_FG, bg);
		if(game != null) {
			PileupGame.Shape next = game.next();
			int[] c = new PileupGame.Piece(next, 0, 0, 0).cells();
			for(int i = 0; i < c.length; i += 2)
				g.put(62 + c[i] * 2, 3 + c[i + 1], "[]", next.colour(), DosColours.BLACK);
		}
		g.box(54, 9, 25, 15, false, Dos.tr("pileup.help"), DosColours.TEXT_FG, bg);
		for(int i = 0; i < 9; i++)
			g.put(56, 11 + i, Dos.fit(Dos.tr("pileup.help." + i), 22), fg, bg);

		switch(state) {
			case TITLE -> panel(g, Dos.tr("pileup.name"), Dos.tr("pileup.start"));
			case PAUSED -> panel(g, Dos.tr("game.paused"), Dos.tr("game.resume"));
			case OVER -> panel(g, Dos.tr("game.over", game == null ? 0 : game.score()),
					place >= 0 ? Dos.tr("game.new_score", place + 1) : Dos.tr("game.again"));
			default -> {}
		}
	}

	private static void cell(TextGrid g, int x, int y, String text, int colour) {
		if(x < 0 || y < 0 || x >= PileupGame.WIDTH || y >= PileupGame.HEIGHT)
			return;
		g.put(BOARD_COL + x * 2, BOARD_ROW + y, text, colour, DosColours.BLACK);
	}

	private static void panel(TextGrid g, String heading, String line) {
		int w = 26, col = BOARD_COL - 1, row = 9;
		g.box(col, row, w, 5, false, "", DosColours.BLACK, DosColours.LIGHT_GREY);
		g.put(col + Math.max(1, (w - heading.length()) / 2), row + 1, Dos.fit(heading, w - 2), DosColours.BLUE, DosColours.LIGHT_GREY);
		g.put(col + Math.max(1, (w - line.length()) / 2), row + 3, Dos.fit(line, w - 2), DosColours.BLACK, DosColours.LIGHT_GREY);
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			if(state == State.PAUSED)
				state = State.PLAYING;
			else if(state != State.PLAYING)
				start();
			return true;
		}
		if(key == GLFW.GLFW_KEY_P) {
			if(state == State.PLAYING)
				state = State.PAUSED;
			else if(state == State.PAUSED)
				state = State.PLAYING;
			return true;
		}
		if(state != State.PLAYING || game == null) {
			if(key == GLFW.GLFW_KEY_SPACE && state != State.PAUSED) {
				start();
				return true;
			}
			return false;
		}
		switch(key) {
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> game.left();
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> game.right();
			case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_W -> game.rotate(1);
			case GLFW.GLFW_KEY_Z -> game.rotate(-1);
			case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> game.softDrop();
			case GLFW.GLFW_KEY_SPACE -> game.hardDrop();
			default -> {
				return false;
			}
		}
		after();
		return true;
	}

	@Override
	protected boolean onChar(char c) {
		return state == State.PLAYING;
	}
}
