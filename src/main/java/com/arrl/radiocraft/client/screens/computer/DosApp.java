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

	/** Shows an error from the server in a box. */
	protected void showError(ComputerResponse response) {
		showDialog(DosDialog.message(Dos.tr("error"), response.errorText()));
	}

	// -------------------- DISPATCH (the screen calls these) --------------------

	final void draw(TextGrid g, long now) {
		render(g, now);
		if(dialog != null)
			dialog.render(g, now);
	}

	final boolean key(int key, int scanCode, int modifiers) {
		if(dialog != null) {
			if(key == GLFW.GLFW_KEY_ESCAPE)
				dialog.cancel();
			else
				dialog.keyPressed(key, scanCode, modifiers);
			return true;
		}
		if(key == GLFW.GLFW_KEY_ESCAPE)
			return onEscape();
		return onKey(key, scanCode, modifiers);
	}

	final boolean typed(char c) {
		if(dialog != null) {
			dialog.charTyped(c);
			return true;
		}
		return onChar(c);
	}

	final boolean click(int col, int row, int button) {
		if(dialog != null) {
			dialog.mouseClicked(col, row, button);
			return true;
		}
		return onClick(col, row, button);
	}

	final boolean scroll(int col, int row, double amount) {
		if(dialog != null) {
			dialog.mouseScrolled(col, row, amount);
			return true;
		}
		return onScroll(col, row, amount);
	}
}
