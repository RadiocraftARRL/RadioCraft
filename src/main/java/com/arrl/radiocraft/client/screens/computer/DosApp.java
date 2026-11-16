package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.common.computer.ComputerApp;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * One program on the radio computer's screen, client side (1.12 step 5). Subclass it, draw the whole screen into the
 * {@link TextGrid} each frame in {@link #render}, and handle the keyboard and mouse in {@link #onKey}, {@link #onChar},
 * {@link #onClick} and {@link #onScroll} (grid cells, not pixels). Talk to the server through {@link #session}
 * ({@link ComputerSession#request}, {@link ComputerSession#app}, {@link ComputerSession#files()}); answers come back
 * on the client thread. Register the class's factory in {@link ComputerAppScreens}.
 * <p>
 * A program can show one modal {@link DosDialog} at a time ({@link #showDialog}); while it is up it takes the input and
 * is drawn over the program. Escape closes the dialog, or else calls {@link #onEscape} (by default the program exits
 * back to the shell). Programs stay alive while the screen is closed (for {@link ComputerSession#KEEP_MS}), so an
 * editor keeps its text; {@link #closed()} is called when the screen closes.
 */
public abstract class DosApp {

	protected final ComputerSession session;
	/** The program this is, or null for the shell. */
	@Nullable
	protected final ComputerApp app;
	@Nullable
	private DosDialog dialog;
	/** This program's pull-down menus in its title row ({@link #setMenuBar}), or null. */
	@Nullable
	private DosMenuBar menuBar;

	protected DosApp(ComputerSession session, @Nullable ComputerApp app) {
		this.session = session;
		this.app = app;
	}

	@Nullable
	public final ComputerApp app() {
		return app;
	}

	// -------------------- FOR PROGRAMS TO OVERRIDE --------------------

	/** Started (pushed on the screen). Ask the server for what it shows here. */
	public void opened() {}

	/** Shown again after a program it started has exited. */
	public void resumed() {}

	/** Every client tick while the screen is open. */
	public void tick() {}

	/** Draws the whole screen. {@code now} is the time in ms (for blinking and animation). */
	public abstract void render(TextGrid g, long now);

	/**
	 * True for a program that is a background service (2026-11-11, DISTRESS): its computer's session, with it, is kept
	 * however long the screen stays shut, as unsaved work keeps an editor's.
	 */
	public boolean keepsRunning() {
		return false;
	}

	/** A key (GLFW key code); true if it was used. */
	/**
	 * True while this program has work not saved yet (an edited text file, book or codeplug): the computer's session is
	 * then kept past {@link ComputerSession#KEEP_MS}, with the program on its screen, as a playing tune keeps it (2026-11-09).
	 */
	public boolean holdsUnsavedWork() {
		return false;
	}

	/** A key let go (2026-11-09: RIGCTL's PTT is held). False if the program does not use it. */
	protected boolean onKeyUp(int key) {
		return false;
	}

	final boolean keyUp(int key) {
		return dialog == null && onKeyUp(key);
	}

	protected boolean onKey(int key, int scanCode, int modifiers) {
		return false;
	}

	/** A character typed; true if it was used. */
	protected boolean onChar(char c) {
		return false;
	}

	/** A click on a cell; true if it was used. */
	protected boolean onClick(int col, int row, int button) {
		return false;
	}

	/** The wheel over a cell ({@code amount} positive is up); true if it was used. */
	protected boolean onScroll(int col, int row, double amount) {
		return false;
	}

	/**
	 * The mouse dragged with {@code button} held, to grid pixel {@code x, y} ({@link TextGrid#CELL_W} pixels a cell);
	 * true if it was used. Every move while held comes here; {@link #onRelease} ends it.
	 */
	protected boolean onDrag(double x, double y, int button) {
		return false;
	}

	/** The mouse button let go at grid pixel {@code x, y}. */
	protected boolean onRelease(double x, double y, int button) {
		return false;
	}

	/** Escape with no dialog up. By default the program exits. */
	protected boolean onEscape() {
		exit();
		return true;
	}

	/** An event this program's server side pushed ({@code RadioComputerBlockEntity#push}). */
	public void event(String kind, CompoundTag data) {}

	/** Started to open a file (the shell's Files list, for an extension registered with {@link ComputerAppScreens#registerOpener}). */
	public void openFile(String name) {}

	/** The computer's screen closed (the program is kept; see the class comment). */
	public void closed() {}

	/** Another program was started in front of this one (it keeps running behind). */
	public void covered() {}

	// -------------------- HELPERS --------------------

	/** Leaves this program, back to the one that started it (the shell). */
	protected void exit() {
		session.exit(this);
	}

	/** Shows {@code d} over the program, replacing any dialog up now. */
	public void showDialog(DosDialog d) {
		dialog = d;
		d.owner = this;
	}

	public void closeDialog() {
		dialog = null;
	}

	public boolean hasDialog() {
		return dialog != null;
	}

	/**
	 * Gives this program pull-down menus in its title row (2026-11-12, every program as the CPS has them). The program
	 * draws as before; its {@link Dos#frame} title moves right of the menu names, the menus are drawn over the program
	 * (under any dialog), and they take the clicks on their names, F10 (unless {@link DosMenuBar#withoutF10}) and,
	 * while one is open, every key. Build the items with {@link #item} so a click does just what the key does.
	 */
	protected void setMenuBar(DosMenuBar bar) {
		menuBar = bar.withItemBeforeExit(new DosMenuBar.Item(Dos.tr("menu.leave"), "Shift+Esc", DosApp::leaveScreen));
	}

	/** Closes the computer's screen, leaving the programs running on it (Shift+Esc). */
	static void leaveScreen() {
		if(net.minecraft.client.Minecraft.getInstance().screen instanceof RadioComputerScreen screen)
			screen.onClose();
	}

	/**
	 * True if this program keeps running when the player is not at it (2026-11-15, the maintainer: "all the apps should
	 * keep running"): with the computer's screen closed (Shift+Esc or the close button) while its monitor is in sight
	 * ({@link ComputerSession#tickClosed}; its server side then answers its read-only requests), or behind another
	 * program. Games say no: they pause.
	 */
	public boolean runsUnattended() {
		return true;
	}

	/** True while one of this program's menus is pulled down (a program drawing pictures over its text leaves them out then). */
	protected boolean menuOpen() {
		return menuBar != null && menuBar.isOpen();
	}

	/** The menus' first column free for words of the program's own on row 0 (1 with no menus). */
	protected int titleCol() {
		return menuBar == null ? 1 : menuBar.end() + 1;
	}

	/** Does what pressing {@code key} (no Ctrl or Shift) does: the same way in as the keyboard, so Esc is {@link #onEscape}. */
	protected final void press(int key) {
		key(key, 0, 0);
	}

	/** A menu item ({@code screen.radiocraft.computer.<labelKey>}) that does what pressing {@code key} does. */
	protected DosMenuBar.Item item(String labelKey, String keyName, int key) {
		return new DosMenuBar.Item(Dos.tr(labelKey), keyName, () -> press(key));
	}

	/** A menu item ({@code screen.radiocraft.computer.<labelKey>}) that runs {@code action}, shown with the key that does the same. */
	protected DosMenuBar.Item item(String labelKey, String keyName, Runnable action) {
		return new DosMenuBar.Item(Dos.tr(labelKey), keyName, action);
	}

	/** Shows an error from the server in a box. */
	protected void showError(ComputerResponse response) {
		showDialog(DosDialog.message(Dos.tr("error"), response.errorText()));
	}

	// -------------------- DISPATCH (the screen calls these) --------------------

	final void draw(TextGrid g, long now) {
		Dos.titleCol(titleCol());
		try {
			render(g, now);
		}
		finally {
			Dos.titleCol(1);
		}
		if(menuBar != null) {
			menuBar.clearUnder(g, 0); // Pictures are drawn over the text: none over the open menu.
			menuBar.render(g, 0);
		}
		if(dialog != null)
			dialog.render(g, now);
	}

	final boolean key(int key, int scanCode, int modifiers) {
		// Shift+Esc, in every program (2026-11-15, the maintainer): step away from the computer, the program left running
		// on its screen (as the screen's close button does; Esc alone leaves the program).
		if(key == GLFW.GLFW_KEY_ESCAPE && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
			leaveScreen();
			return true;
		}
		if(dialog != null) {
			if(key == GLFW.GLFW_KEY_ESCAPE)
				dialog.cancel();
			else
				dialog.keyPressed(key, scanCode, modifiers);
			return true;
		}
		if(menuBar != null && menuBar.keyPressed(key))
			return true; // F10 opens them; an open menu takes every key, Esc closing it.
		if(key == GLFW.GLFW_KEY_ESCAPE)
			return onEscape();
		return onKey(key, scanCode, modifiers);
	}

	final boolean typed(char c) {
		if(dialog != null) {
			dialog.charTyped(c);
			return true;
		}
		if(menuBar != null && menuBar.isOpen())
			return true;
		return onChar(c);
	}

	final boolean click(int col, int row, int button) {
		if(dialog != null) {
			dialog.mouseClicked(col, row, button);
			return true;
		}
		if(menuBar != null && menuBar.mouseClicked(col, row, 0))
			return true;
		return onClick(col, row, button);
	}

	final boolean drag(double x, double y, int button) {
		if(dialog != null || menuBar != null && menuBar.isOpen())
			return true;
		return onDrag(x, y, button);
	}

	final boolean release(double x, double y, int button) {
		return dialog == null && onRelease(x, y, button);
	}

	final boolean scroll(int col, int row, double amount) {
		if(dialog != null) {
			dialog.mouseScrolled(col, row, amount);
			return true;
		}
		if(menuBar != null && menuBar.isOpen())
			return true;
		return onScroll(col, row, amount);
	}
}
