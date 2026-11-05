package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerResponse;
import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosFilePicker;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.DosTextArea;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.BookPages;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * EDIT, the built-in text editor (in the manner of MS-DOS Editor): one {@code .TXT} file at a time, up to 64K. Ctrl+S saves
 * (asking for a name the first time), F3 opens, F4 starts a new file, F5 prints it as a written book on the scanner,
 * F6 reads the book on the scanner's glass in. It asks before throwing away changes.
 */
public final class EditorApp extends DosApp {

	/** Most characters a document may hold (EDIT's 64K, near enough). */
	public static final int MAX_CHARS = 64 * 1024;
	/** Longest text printed straight from the editor (one packet); longer ones are printed from their saved file. */
	private static final int PRINT_DIRECT_CHARS = 20_000;

	private final DosTextArea area = new DosTextArea(MAX_CHARS);
	@Nullable
	private String fileName;

	public EditorApp(ComputerSession session, ComputerApp app) {
		super(session, app);
	}

	@Override
	public void openFile(String name) {
		load(name);
	}

	private void load(String name) {
		session.files().readText(name, (text, response) -> {
			if(text == null) {
				showError(response);
				return;
			}
			area.setText(text);
			fileName = ComputerStorage.normalise(name);
			session.message(Dos.tr("edit.opened", fileName), false);
		});
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		String name = fileName == null ? Dos.tr("edit.untitled") : fileName;
		Dos.frame(g, Dos.tr("edit.title", name + (area.isModified() ? "*" : "")), Dos.tr("edit.keys"));
		area.render(g, 0, 1, TextGrid.COLS, 22, DosColours.TEXT_FG, DosColours.BODY_BG, !hasDialog());
		String status = Dos.tr("edit.status", area.cursorLine() + 1, area.cursorColumn() + 1, area.length(), MAX_CHARS);
		String message = session.message();
		g.putField(0, 23, " " + (message.isEmpty() ? status : message), TextGrid.COLS, DosColours.BLACK, DosColours.LIGHT_GREY);
	}

	// -------------------- COMMANDS --------------------

	/** Saves, asking for a name if it has none; then runs {@code after}, if given. */
	private void save(@Nullable Runnable after) {
		if(fileName == null) {
			showDialog(DosDialog.ask(Dos.tr("edit.save_title"), Dos.tr("edit.save_as"), DosInput.fileName(""), typed -> {
				fileName = ComputerStorage.suggest(typed, ComputerFileFormats.TEXT);
				save(after);
			}));
			return;
		}
		String name = fileName;
		session.files().writeText(name, area.text(), response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			area.setModified(false);
			session.message(Dos.tr("edit.saved", name), false);
			if(after != null)
				after.run();
		});
	}

	/** Runs {@code then} once unsaved changes are saved or let go (asks), or at once if there are none. */
	private void whenSafe(Runnable then) {
		if(!area.isModified()) {
			then.run();
			return;
		}
		showDialog(DosDialog.choice(Dos.tr("edit.unsaved_title"), Dos.tr("edit.unsaved"),
				new String[] { Dos.tr("button.save"), Dos.tr("button.discard"), Dos.tr("button.cancel") }, index -> {
					if(index == 0)
						save(then);
					else if(index == 1)
						then.run();
				}));
	}

	private void print() {
		java.util.function.Consumer<ComputerResponse> done = response -> {
			if(response.ok())
				session.message(Dos.tr("edit.printed"), false);
			else
				showError(response);
		};
		if(fileName != null && !area.isModified())
			session.scanner().printTextFile(fileName, done);
		else if(area.length() <= PRINT_DIRECT_CHARS)
			session.scanner().printText(fileName == null ? "" : fileName.substring(0, fileName.indexOf('.')), area.text(), done);
		else
			session.message(Dos.tr("edit.save_to_print"), true);
	}

	private void scan() {
		session.scanner().read(response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			if(!"book".equals(response.data().getString("kind"))) {
				session.message(Dos.tr("edit.not_a_book"), true);
				return;
			}
			String text = BookPages.join(ComputerServer.strings(response.data().getList("pages", Tag.TAG_STRING)));
			whenSafe(() -> {
				area.setText(text);
				area.setModified(true);
				fileName = null;
				session.message(Dos.tr("edit.scanned", response.data().getString("title")), false);
			});
		});
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onEscape() {
		whenSafe(this::exit);
		return true;
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(Dos.isSaveKey(key)) { // Ctrl+S (F2 is Minecraft's screenshot key).
			save(null);
			return true;
		}
		switch(key) {
			case GLFW.GLFW_KEY_F3 -> whenSafe(() -> showDialog(new DosFilePicker(session, Dos.tr("edit.open_title"), ComputerFileFormats.TEXT, this::load)));
			case GLFW.GLFW_KEY_F4 -> whenSafe(() -> {
				area.setText("");
				fileName = null;
			});
			case GLFW.GLFW_KEY_F5 -> print();
			case GLFW.GLFW_KEY_F6 -> scan();
			default -> {
				return area.keyPressed(key);
			}
		}
		return true;
	}

	@Override
	protected boolean onChar(char c) {
		return area.charTyped(c);
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		return area.mouseClicked(col, row);
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		return area.mouseScrolled(col, row, amount);
	}
}
