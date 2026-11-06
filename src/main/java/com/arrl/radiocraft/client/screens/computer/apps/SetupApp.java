package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerResponse;
import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.DosScheme;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerLock;
import com.arrl.radiocraft.common.computer.ComputerSettings;
import net.minecraft.nbt.CompoundTag;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * SETUP, the radio computer's built-in settings program (1.12, launcher and SETUP), in the manner of a DOS setup
 * screen: the pages on the left, the chosen page's settings on the right.
 * <ul>
 * <li><b>Launcher</b>: the shell as a list (RC-DOS Shell), icons (Program Manager) or a bare command line.</li>
 * <li><b>Colours</b>: the colour scheme ({@link DosScheme}); moving through the list shows the whole screen in it
 * (a preview), Enter keeps it.</li>
 * <li><b>Password</b>: sets or removes the computer's password (it was F8 in the shell).</li>
 * <li><b>Name and clock</b>: the name in the top bar, the clock (in-game, real or none) and 12 or 24 hours.</li>
 * </ul>
 * Everything is kept on the computer's disk ({@code sys.settings}, {@code sys.password}). Only the owner, an operator,
 * or anyone while the computer has no owner may change them ({@link ComputerSession#mayConfigure}); others can look.
 */
public final class SetupApp extends DosApp {

	private enum Page { LAUNCHER, COLOURS, PASSWORD, NAME_CLOCK }

	private static final Page[] PAGES = Page.values();
	private static final ComputerSettings.View[] VIEWS = ComputerSettings.View.values();
	private static final int MENU_COL = 1, MENU_W = 22, PANEL_COL = 24, PANEL_W = TextGrid.COLS - 25, TOP = 2, HEIGHT = 20;

	private int page = 0;
	/** True while the keys go to the page on the right rather than the page list. */
	private boolean inPage = false;
	/** The highlighted line on the page. */
	private int line = 0;

	public SetupApp(ComputerSession session, ComputerApp app) {
		super(session, app);
	}

	@Override
	public void opened() {
		session.refresh();
	}

	@Override
	public void closed() {
		session.endPreview();
	}

	@Override
	public void covered() {
		session.endPreview();
	}

	@Override
	protected void exit() {
		session.endPreview();
		super.exit();
	}

	private ComputerSettings settings() {
		return session.settings();
	}

	private Page page() {
		return PAGES[page];
	}

	private int lines() {
		return switch(page()) {
			case LAUNCHER -> VIEWS.length;
			case COLOURS -> DosScheme.ALL.size();
			case PASSWORD -> 1;
			case NAME_CLOCK -> 3;
		};
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("setup.title"), Dos.tr(inPage ? "setup.keys_page" : "setup.keys_menu"));
		int fg = DosColours.BODY_FG, bg = DosColours.BODY_BG;
		g.box(MENU_COL, TOP - 1, MENU_W, HEIGHT + 1, !inPage, Dos.tr("setup.pages"), DosColours.TEXT_FG, bg);
		for(int i = 0; i < PAGES.length; i++) {
			boolean sel = i == page;
			int f = sel ? inPage ? DosColours.TEXT_FG : DosColours.HIGHLIGHT_FG : fg;
			int b = sel ? inPage ? DosColours.DIM : DosColours.HIGHLIGHT_BG : bg;
			g.putField(MENU_COL + 1, TOP + 1 + i * 2, " " + Dos.tr("setup.page." + PAGES[i].name().toLowerCase(java.util.Locale.ROOT)), MENU_W - 2, f, b);
		}
		if(!session.mayConfigure()) {
			List<String> words = wrap(Dos.tr("setup.read_only"), MENU_W - 4);
			for(int i = 0; i < words.size() && i < 6; i++)
				g.put(MENU_COL + 2, TOP + 12 + i, words.get(i), DosColours.ACCENT, bg);
		}

		g.box(PANEL_COL, TOP - 1, PANEL_W, HEIGHT + 1, inPage, Dos.tr("setup.page." + page().name().toLowerCase(java.util.Locale.ROOT)), DosColours.TEXT_FG, bg);
		switch(page()) {
			case LAUNCHER -> renderLauncher(g);
			case COLOURS -> renderColours(g);
			case PASSWORD -> renderPassword(g);
			case NAME_CLOCK -> renderNameClock(g);
		}
		String message = session.message();
		if(!message.isEmpty())
			g.putField(0, TextGrid.ROWS - 2, " " + message, TextGrid.COLS, session.messageIsError() ? DosColours.ERROR_FG : DosColours.DIALOG_FG,
					session.messageIsError() ? DosColours.ERROR_BG : DosColours.DIALOG_BG);
	}

	/** An option line on the page: a radio button or a value, highlighted when it is the line in focus. */
	private void option(TextGrid g, int i, int row, String text, boolean chosen) {
		boolean sel = inPage && i == line;
		int f = sel ? DosColours.HIGHLIGHT_FG : DosColours.TEXT_FG, b = sel ? DosColours.HIGHLIGHT_BG : DosColours.BODY_BG;
		g.putField(PANEL_COL + 2, row, (chosen ? " (*) " : " ( ) ") + text, PANEL_W - 4, f, b);
		if(chosen)
			g.set(PANEL_COL + 4, row, '*', sel ? f : DosColours.ACCENT, b);
	}

	private void renderLauncher(TextGrid g) {
		int row = TOP + 1;
		g.put(PANEL_COL + 2, row, Dos.tr("setup.launcher.intro"), DosColours.BODY_FG, DosColours.BODY_BG);
		row += 2;
		for(int i = 0; i < VIEWS.length; i++) {
			String id = VIEWS[i].name().toLowerCase(java.util.Locale.ROOT);
			option(g, i, row, Dos.tr("setup.launcher." + id), settings().view() == VIEWS[i]);
			List<String> about = wrap(Dos.tr("setup.launcher." + id + ".about"), PANEL_W - 10);
			for(int k = 0; k < about.size() && k < 2; k++)
				g.put(PANEL_COL + 8, row + 1 + k, about.get(k), DosColours.DIM, DosColours.BODY_BG);
			row += 4;
		}
	}

	private void renderColours(TextGrid g) {
		int row = TOP + 1;
		g.put(PANEL_COL + 2, row, Dos.tr("setup.colours.intro"), DosColours.BODY_FG, DosColours.BODY_BG);
		row += 2;
		for(int i = 0; i < DosScheme.ALL.size(); i++) {
			DosScheme scheme = DosScheme.ALL.get(i);
			option(g, i, row + i, Dos.tr(scheme.nameKey()), scheme.id().equals(settings().scheme()));
			// The scheme's sixteen colours as they really look, side by side with the others.
			int x = PANEL_COL + 24;
			for(int c = 0; c < 16; c++)
				g.set(x + c, row + i, TextGrid.FULL, DosColours.RAW | scheme.palette()[c], DosColours.BODY_BG);
			// Its body and text, title bar and highlight, also as they really are.
			int sx = x + 17;
			g.put(sx, row + i, "Ab", DosColours.RAW | scheme.display(DosColours.TEXT_FG), DosColours.RAW | scheme.display(DosColours.BODY_BG));
			g.put(sx + 2, row + i, "Ab", DosColours.RAW | scheme.display(DosColours.HIGHLIGHT_FG), DosColours.RAW | scheme.display(DosColours.HIGHLIGHT_BG));
			g.put(sx + 4, row + i, "Ab", DosColours.RAW | scheme.display(DosColours.TITLE_FG), DosColours.RAW | scheme.display(DosColours.TITLE_BG));
		}
		// A sample window in the scheme on screen now (the one highlighted, while previewing).
		int sr = row + DosScheme.ALL.size() + 1, sc = PANEL_COL + 3, sw = PANEL_W - 6;
		g.put(sc, sr, Dos.tr("setup.colours.sample"), DosColours.DIM, DosColours.BODY_BG);
		sr++;
		g.putField(sc, sr, " " + Dos.tr("setup.sample.title"), sw, DosColours.TITLE_FG, DosColours.TITLE_BG);
		g.putField(sc, sr + 1, " " + Dos.tr("setup.sample.text"), sw, DosColours.TEXT_FG, DosColours.BODY_BG);
		g.putField(sc, sr + 2, " " + Dos.tr("setup.sample.body"), sw, DosColours.BODY_FG, DosColours.BODY_BG);
		g.put(sc + 1 + Dos.tr("setup.sample.body").length() + 1, sr + 2, Dos.tr("setup.sample.dim"), DosColours.DIM, DosColours.BODY_BG);
		g.putField(sc, sr + 3, " " + Dos.tr("setup.sample.selected"), sw, DosColours.HIGHLIGHT_FG, DosColours.HIGHLIGHT_BG);
		g.putField(sc, sr + 4, " ", sw, DosColours.BODY_FG, DosColours.BODY_BG);
		int c = g.put(sc + 1, sr + 4, Dos.tr("setup.sample.accent"), DosColours.ACCENT, DosColours.BODY_BG);
		c = g.put(c + 2, sr + 4, Dos.tr("setup.sample.good"), DosColours.GOOD, DosColours.BODY_BG);
		c = g.put(c + 2, sr + 4, Dos.tr("setup.sample.bad"), DosColours.BAD, DosColours.BODY_BG);
		g.put(c + 2, sr + 4, " " + Dos.tr("setup.sample.error") + " ", DosColours.ERROR_FG, DosColours.ERROR_BG);
		g.putField(sc, sr + 5, "", sw, DosColours.KEYS_FG, DosColours.KEYS_BG);
		int k = g.put(sc + 1, sr + 5, "F1", DosColours.HOTKEY_FG, DosColours.KEYS_BG);
		g.put(k, sr + 5, "=" + Dos.tr("setup.sample.keys"), DosColours.KEYS_FG, DosColours.KEYS_BG);
	}

	private void renderPassword(TextGrid g) {
		int row = TOP + 1;
		g.put(PANEL_COL + 2, row, Dos.tr(session.hasPassword() ? "setup.password.set" : "setup.password.none"), DosColours.ACCENT, DosColours.BODY_BG);
		row += 2;
		option(g, 0, row, Dos.tr(session.hasPassword() ? "setup.password.change" : "setup.password.choose"), false);
		row += 2;
		for(String text : wrap(Dos.tr("setup.password.about"), PANEL_W - 6))
			g.put(PANEL_COL + 3, row++, text, DosColours.BODY_FG, DosColours.BODY_BG);
	}

	private void renderNameClock(TextGrid g) {
		ComputerSettings s = settings();
		int row = TOP + 1;
		g.put(PANEL_COL + 2, row, Dos.tr("setup.name_clock.intro"), DosColours.BODY_FG, DosColours.BODY_BG);
		row += 2;
		value(g, 0, row, Dos.tr("setup.name"), s.name().isEmpty() ? Dos.tr("setup.name.none") : s.name());
		value(g, 1, row + 2, Dos.tr("setup.clock"), Dos.tr("setup.clock." + s.clock().name().toLowerCase(java.util.Locale.ROOT)));
		value(g, 2, row + 4, Dos.tr("setup.hours"), Dos.tr(s.hour24() ? "setup.hours.24" : "setup.hours.12") + "   (" + s.time(13, 5) + ")");
		row += 7;
		for(String text : wrap(Dos.tr("setup.name_clock.about"), PANEL_W - 6))
			g.put(PANEL_COL + 3, row++, text, DosColours.DIM, DosColours.BODY_BG);
	}

	private void value(TextGrid g, int i, int row, String label, String value) {
		boolean sel = inPage && i == line;
		g.putField(PANEL_COL + 2, row, " " + label, 18, DosColours.BODY_FG, DosColours.BODY_BG);
		g.putField(PANEL_COL + 20, row, " " + value + " ", PANEL_W - 23, sel ? DosColours.HIGHLIGHT_FG : DosColours.FIELD_FG,
				sel ? DosColours.HIGHLIGHT_BG : DosColours.FIELD_BG);
	}

	private static List<String> wrap(String text, int width) {
		List<String> out = new java.util.ArrayList<>();
		StringBuilder current = new StringBuilder();
		for(String word : text.split(" ")) {
			if(current.length() + word.length() + (current.isEmpty() ? 0 : 1) > width && !current.isEmpty()) {
				out.add(current.toString());
				current.setLength(0);
			}
			if(!current.isEmpty())
				current.append(' ');
			current.append(word);
		}
		if(!current.isEmpty())
			out.add(current.toString());
		return out;
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onEscape() {
		if(inPage) {
			leavePage();
			return true;
		}
		exit();
		return true;
	}

	private void enterPage() {
		inPage = true;
		line = switch(page()) {
			case LAUNCHER -> settings().view().ordinal();
			case COLOURS -> Math.max(0, DosScheme.ALL.indexOf(DosScheme.byId(settings().scheme())));
			default -> 0;
		};
		previewLine();
	}

	private void leavePage() {
		inPage = false;
		session.endPreview();
	}

	private void previewLine() {
		if(page() == Page.COLOURS && inPage)
			session.preview(DosScheme.ALL.get(line));
		else
			session.endPreview();
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(!inPage) {
			switch(key) {
				case GLFW.GLFW_KEY_UP -> page = (page + PAGES.length - 1) % PAGES.length;
				case GLFW.GLFW_KEY_DOWN -> page = (page + 1) % PAGES.length;
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_TAB -> enterPage();
				default -> {
					return false;
				}
			}
			return true;
		}
		switch(key) {
			case GLFW.GLFW_KEY_UP -> {
				line = (line + lines() - 1) % lines();
				previewLine();
			}
			case GLFW.GLFW_KEY_DOWN -> {
				line = (line + 1) % lines();
				previewLine();
			}
			case GLFW.GLFW_KEY_TAB -> leavePage();
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> choose(1);
			case GLFW.GLFW_KEY_LEFT -> {
				if(page() == Page.NAME_CLOCK && line > 0)
					choose(-1);
				else
					leavePage();
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				if(page() == Page.NAME_CLOCK && line > 0)
					choose(1);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** Enter (or a click) on the line in focus: {@code step} through a value's choices. */
	private void choose(int step) {
		if(!session.mayConfigure()) {
			session.message(Dos.tr("err.not_owner_settings"), true);
			return;
		}
		ComputerSettings s = settings();
		switch(page()) {
			case LAUNCHER -> save(s.withView(VIEWS[line]));
			case COLOURS -> save(s.withScheme(DosScheme.ALL.get(line).id()));
			case PASSWORD -> askPassword();
			case NAME_CLOCK -> {
				switch(line) {
					case 0 -> showDialog(DosDialog.ask(Dos.tr("setup.name"), Dos.tr("setup.name.prompt", ComputerSettings.MAX_NAME),
							new DosInput(s.name(), ComputerSettings.MAX_NAME, c -> c >= ' ' && c <= '~'), typed -> save(settings().withName(typed))));
					case 1 -> {
						ComputerSettings.Clock[] clocks = ComputerSettings.Clock.values();
						save(s.withClock(clocks[Math.floorMod(s.clock().ordinal() + step, clocks.length)]));
					}
					default -> save(s.withHour24(!s.hour24()));
				}
			}
		}
	}

	private void save(ComputerSettings changed) {
		CompoundTag data = new CompoundTag();
		data.put("settings", changed.toTag());
		session.request("sys.settings", data, response -> {
			if(response.ok()) {
				session.learnSettings(ComputerSettings.fromTag(response.data().getCompound("settings")));
				session.message(Dos.tr("setup.saved"), false);
			}
			else
				session.message(response.errorText(), true);
			previewLine();
		});
	}

	/** Asks for a new password (empty removes it); the server refuses anyone but the owner or an operator. */
	private void askPassword() {
		showDialog(DosDialog.ask(Dos.tr("setup.password.title"), Dos.tr("setup.password.prompt"),
				new DosInput("", ComputerLock.MAX_LENGTH).masked(), typed -> {
			CompoundTag data = new CompoundTag();
			data.putString("password", typed);
			session.request("sys.password", data, (ComputerResponse response) -> {
				if(response.ok())
					session.message(Dos.tr(response.data().getBoolean("password") ? "setup.password.done_set" : "setup.password.done_cleared"), false);
				else
					session.message(response.errorText(), true);
			});
		}));
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(col >= MENU_COL && col < MENU_COL + MENU_W) {
			int index = (row - TOP - 1) / 2;
			if(row >= TOP + 1 && (row - TOP - 1) % 2 == 0 && index >= 0 && index < PAGES.length) {
				if(index == page && !inPage)
					enterPage();
				else {
					page = index;
					leavePage();
				}
				return true;
			}
			return false;
		}
		if(col < PANEL_COL || col >= PANEL_COL + PANEL_W)
			return false;
		int hit = switch(page()) {
			case LAUNCHER -> row >= TOP + 3 && (row - TOP - 3) % 4 == 0 ? (row - TOP - 3) / 4 : -1;
			case COLOURS -> row - TOP - 3;
			case PASSWORD -> row == TOP + 3 ? 0 : -1;
			case NAME_CLOCK -> row >= TOP + 3 && (row - TOP - 3) % 2 == 0 ? (row - TOP - 3) / 2 : -1;
		};
		if(hit < 0 || hit >= lines())
			return false;
		if(!inPage)
			enterPage();
		if(hit == line)
			choose(1);
		else {
			line = hit;
			previewLine();
		}
		return true;
	}
}
