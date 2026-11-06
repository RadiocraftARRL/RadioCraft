package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerMidi;
import com.arrl.radiocraft.client.screens.computer.ComputerMusic;
import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosList;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.MusicTracks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MUSIC, a secret program (1.12 step 8): a jukebox of freely licensed tunes ({@link MusicTracks}, MIDI files in
 * {@code assets/radiocraft/computer/music}) played on this computer's "sound card", Java's software synthesizer
 * ({@link ComputerMidi}), at the game's master and jukebox volume, through the computer's small speakers. Enter plays
 * the track picked, Space pauses, S stops, Right and Left (or N and B) go to the next and the last tune, R repeats one,
 * H shuffles, and it plays on down the list (or round the shuffle). The visualizer is a spectrum analyser on the sound.
 * Only this player hears it.
 * <p>
 * The tune plays on after this page is left (Esc goes back with the music still on, a note in the top bar comes back
 * here) and after the screen is closed with C (Close), which leaves this page open for when the computer is used
 * again: {@link ComputerMusic} holds the player and stops it when the player walks away, changes dimension, logs out,
 * or the computer is broken or loses power (1.12 playtest).
 */
public final class MusicApp extends DosApp {

	private static final int LIST_ROW = 3, LIST_ROWS = 12;
	/** Where the Close button is drawn (row, first and last column). */
	private static final int CLOSE_ROW = LIST_ROWS + 5, CLOSE_COL = 68;

	private final DosList list = new DosList();
	private List<MusicTracks.Track> tracks = List.of();

	public MusicApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		list.onActivate(this::play);
	}

	@Override
	public void opened() {
		tracks = ComputerMusic.tracks();
		List<String> lines = new ArrayList<>();
		for(int i = 0; i < tracks.size(); i++) {
			MusicTracks.Track t = tracks.get(i);
			lines.add(String.format(Locale.ROOT, "%2d  %-40s %-22s %5s", i + 1, Dos.fit(t.title(), 40), Dos.fit(t.composer(), 22), t.length()));
		}
		list.setItems(lines);
		if(!tracks.isEmpty())
			list.select(playing() >= 0 ? playing() : 0);
	}

	/** The track playing on this computer, or -1 (also when the music is on another computer). */
	private int playing() {
		return ComputerMusic.isPlayingAt(session.pos()) ? ComputerMusic.playing() : -1;
	}

	private void play(int index) {
		ComputerMusic.play(session.pos(), index);
		if(ComputerMusic.isPlayingAt(session.pos()))
			list.select(index);
	}

	private void next() {
		ComputerMusic.next(session.pos(), list.selected());
		if(ComputerMusic.isPlayingAt(session.pos()))
			list.select(ComputerMusic.playing());
	}

	private void back() {
		ComputerMusic.back(session.pos(), list.selected());
		if(ComputerMusic.isPlayingAt(session.pos()))
			list.select(ComputerMusic.playing());
	}

	private void stop() {
		if(playing() >= 0)
			ComputerMusic.stop();
	}

	@Override
	public void tick() {
		int now = playing();
		if(now >= 0 && now != list.selected() && now < list.size())
			list.select(now); // It moved on to the next tune.
	}

	/** Esc: back to where it was started from, the music still playing. */
	@Override
	protected boolean onEscape() {
		exit();
		return true;
	}

	/** Closes the computer's screen, this page left open and the music playing. */
	private void closeScreen() {
		Screen screen = Minecraft.getInstance().screen;
		if(screen != null)
			screen.onClose();
	}

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("music.title"), Dos.tr("music.keys", String.valueOf(TextGrid.KEY_LEFT) + " " + TextGrid.KEY_RIGHT));
		int fg = DosColours.BODY_FG, bg = DosColours.BODY_BG;
		g.box(1, 1, 78, LIST_ROWS + 4, false, Dos.tr("music.tracks"), DosColours.TEXT_FG, bg);
		g.put(3, 2, String.format(Locale.ROOT, "%2s  %-40s %-22s %5s", "#", Dos.tr("music.col_title"), Dos.tr("music.col_composer"), Dos.tr("music.col_time")),
				DosColours.YELLOW, bg);
		list.render(g, 3, LIST_ROW, 74, LIST_ROWS, DosColours.TEXT_FG, bg, true);
		int playing = playing();
		boolean paused = ComputerMusic.paused(), repeat = ComputerMusic.repeat(), shuffle = ComputerMusic.shuffle();
		if(playing >= 0 && playing >= list.top() && playing < list.top() + LIST_ROWS)
			g.set(2, LIST_ROW + playing - list.top(), paused ? '=' : TextGrid.ARROW_RIGHT, DosColours.LIGHT_GREEN, bg);

		// Now playing: the title, a bar with the time, and a level meter per MIDI channel.
		int top = LIST_ROWS + 5;
		g.box(1, top, 78, 24 - top, false, Dos.tr("music.now"), DosColours.TEXT_FG, bg);
		g.put(CLOSE_COL, CLOSE_ROW, Dos.tr("music.close_button"), DosColours.BLACK, DosColours.LIGHT_GREY);
		String line;
		if(tracks.isEmpty())
			line = Dos.tr("music.no_tracks");
		else if(!ComputerMusic.problem().isEmpty())
			line = Dos.tr("music.missing", ComputerMusic.problem());
		else if(ComputerMidi.problem() != null)
			line = Dos.tr("music.no_sound", ComputerMidi.problem());
		else if(ComputerMusic.playing() >= 0 && playing < 0)
			line = Dos.tr("music.elsewhere");
		else if(playing < 0)
			line = Dos.tr("music.stopped");
		else {
			MusicTracks.Track t = tracks.get(playing);
			line = (paused ? Dos.tr("music.paused") + " " : "") + t.title() + " - " + t.composer();
		}
		// Repeat and shuffle at the right of the line, whether anything plays or not.
		String modes = ((repeat ? Dos.tr("music.repeat") + " " : "") + (shuffle ? Dos.tr("music.shuffle") : "")).strip();
		int modesCol = 77 - modes.length();
		g.put(3, top + 1, Dos.fit(line, modes.isEmpty() ? 74 : modesCol - 4), DosColours.TEXT_FG, bg);
		if(!modes.isEmpty())
			g.put(modesCol, top + 1, modes, DosColours.LIGHT_CYAN, bg);
		int seconds = playing < 0 ? 0 : ComputerMidi.seconds();
		String time = String.format(Locale.ROOT, "%d:%02d / %s", seconds / 60, seconds % 60, playing < 0 ? "--:--" : tracks.get(playing).length());
		g.bar(3, top + 2, 60, playing < 0 ? 0.0D : ComputerMidi.position(), DosColours.LIGHT_CYAN, bg);
		g.put(65, top + 2, time, fg, bg);
		// The visualizer: a bar per band of pitch, low on the left, two columns each, from the bottom row of the box up.
		int meterRow = top + 3, height = 24 - top - 4;
		for(int bar = 0; bar < ComputerMidi.BARS && height > 0; bar++) {
			float level = ComputerMidi.level(bar);
			int lit = Math.round(level * height);
			for(int h = 0; h < height; h++) {
				int row = meterRow + height - 1 - h;
				int colour = h < lit ? h >= height - 1 ? DosColours.LIGHT_RED : h >= height / 2 ? DosColours.YELLOW : DosColours.LIGHT_GREEN : DosColours.DARK_GREY;
				g.set(3 + bar * 3, row, h < lit ? TextGrid.FULL : TextGrid.SHADE_LIGHT, colour, bg);
				g.set(4 + bar * 3, row, h < lit ? TextGrid.FULL : TextGrid.SHADE_LIGHT, colour, bg);
			}
		}
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_SPACE -> {
				if(playing() >= 0)
					ComputerMusic.togglePause();
				else
					play(list.selected());
				return true;
			}
			case GLFW.GLFW_KEY_S -> {
				stop();
				return true;
			}
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_N -> {
				next();
				return true;
			}
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_B -> {
				back();
				return true;
			}
			case GLFW.GLFW_KEY_R -> {
				ComputerMusic.toggleRepeat();
				return true;
			}
			case GLFW.GLFW_KEY_H -> {
				ComputerMusic.toggleShuffle();
				return true;
			}
			case GLFW.GLFW_KEY_C -> {
				closeScreen();
				return true;
			}
			default -> {
				return list.keyPressed(key);
			}
		}
	}

	@Override
	protected boolean onChar(char c) {
		return true; // Letters are keys here.
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(row == CLOSE_ROW && col >= CLOSE_COL && col < CLOSE_COL + Dos.tr("music.close_button").length()) {
			closeScreen();
			return true;
		}
		return list.mouseClicked(col, row);
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		return list.mouseScrolled(col, row, amount);
	}
}
