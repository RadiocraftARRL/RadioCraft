package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.games.HighScores;
import com.arrl.radiocraft.common.computer.games.SnakeGame;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * SNAKE, a secret program (1.12 step 8, floppy found in structure chests): {@link SnakeGame} on a 36 by 20 board, each
 * cell two text columns wide so it comes out square. Arrows (or WASD) steer, P pauses, Enter starts again. The best ten
 * scores on this computer are kept in {@code SNAKE.DAT}. The game only runs while the screen is open.
 */
public final class SnakeApp extends DosApp {

	private static final int W = 36, H = 20, LEFT = 4, TOP = 2;

	private enum State { TITLE, PLAYING, PAUSED, OVER }

	private final HighScoreFile scores;
	@Nullable
	private SnakeGame game;
	private State state = State.TITLE;
	private int ticks;
	private int place = -1;

	public SnakeApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		this.scores = new HighScoreFile(session, "SNAKE.DAT");
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
		game = new SnakeGame(W, H, System.nanoTime());
		state = State.PLAYING;
		ticks = 0;
		place = -1;
	}

	@Override
	public void tick() {
		if(state != State.PLAYING || game == null)
			return;
		if(++ticks < game.stepTicks())
			return;
		ticks = 0;
		if(!game.step()) {
			state = State.OVER;
			place = scores.submit(game.score());
		}
	}

	@Override
	public void render(TextGrid g, long now) {
		String title = Dos.tr("snake.title", game == null ? 0 : game.score(), scores.scores().best());
		Dos.frame(g, title, Dos.tr(state == State.PLAYING ? "snake.keys_play" : "snake.keys"));
		int bg = DosColours.BLACK;
		g.box(LEFT - 1, TOP - 1, W * 2 + 2, H + 2, true, "", DosColours.LIGHT_GREEN, bg);
		if(game != null) {
			for(int y = 0; y < H; y++)
				for(int x = 0; x < W; x++) {
					int colour = switch(game.at(x, y)) {
						case HEAD -> game.alive() ? DosColours.YELLOW : DosColours.LIGHT_RED;
						case BODY -> (x + y) % 2 == 0 ? DosColours.LIGHT_GREEN : DosColours.GREEN;
						case FOOD -> (now / 300L) % 2 == 0 ? DosColours.LIGHT_RED : DosColours.RED;
						case EMPTY -> bg;
					};
					int col = LEFT + x * 2, row = TOP + y;
					g.set(col, row, ' ', DosColours.WHITE, colour);
					g.set(col + 1, row, ' ', DosColours.WHITE, colour);
				}
			g.put(LEFT, TOP + H + 1, Dos.tr("snake.status", game.length(), game.eaten()), DosColours.BODY_FG, DosColours.BODY_BG);
		}
		switch(state) {
			case TITLE -> panel(g, Dos.tr("snake.name"), Dos.tr("snake.start"));
			case PAUSED -> panel(g, Dos.tr("game.paused"), Dos.tr("game.resume"));
			case OVER -> panel(g, Dos.tr("game.over", game == null ? 0 : game.score()),
					place >= 0 ? Dos.tr("game.new_score", place + 1) : Dos.tr("game.again"));
			default -> {}
		}
	}

	/** A box in the middle of the board: a heading, a line, and the high score table under them. */
	private void panel(TextGrid g, String heading, String line) {
		HighScores table = scores.scores();
		int rows = 6 + Math.max(1, table.entries().size());
		int w = 40, col = LEFT + W - w / 2, row = TOP + Math.max(0, (H - rows) / 2);
		g.box(col, row, w, rows, false, "", DosColours.BLACK, DosColours.LIGHT_GREY);
		g.shadow(col, row, w, rows);
		g.put(col + (w - heading.length()) / 2, row + 1, heading, DosColours.BLUE, DosColours.LIGHT_GREY);
		g.put(col + Math.max(1, (w - line.length()) / 2), row + 2, Dos.fit(line, w - 2), DosColours.BLACK, DosColours.LIGHT_GREY);
		g.put(col + 2, row + 4, Dos.tr("game.high_scores"), DosColours.RED, DosColours.LIGHT_GREY);
		if(table.entries().isEmpty())
			g.put(col + 2, row + 5, Dos.tr("game.no_scores"), DosColours.DARK_GREY, DosColours.LIGHT_GREY);
		for(int i = 0; i < table.entries().size(); i++) {
			HighScores.Entry e = table.entries().get(i);
			int fg = i == place && state == State.OVER ? DosColours.RED : DosColours.BLACK;
			g.put(col + 2, row + 5 + i, String.format(java.util.Locale.ROOT, "%2d. %-16s %8d", i + 1, e.name(), e.score()), fg, DosColours.LIGHT_GREY);
		}
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
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
		if(state != State.PLAYING || game == null)
			return false;
		SnakeGame.Heading heading = switch(key) {
			case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> SnakeGame.Heading.UP;
			case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> SnakeGame.Heading.DOWN;
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> SnakeGame.Heading.LEFT;
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> SnakeGame.Heading.RIGHT;
			default -> null;
		};
		if(heading == null)
			return false;
		game.turn(heading);
		return true;
	}

	@Override
	protected boolean onChar(char c) {
		return state == State.PLAYING; // WASD and P are keys here, not typing.
	}
}
