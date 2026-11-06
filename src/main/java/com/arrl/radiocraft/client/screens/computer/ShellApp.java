package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerSettings;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.photo.PhotoImage;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The radio computer's launcher, at the bottom of the program stack. SETUP chooses one of three views
 * ({@link ComputerSettings.View}):
 * <ul>
 * <li><b>List</b>: RC-DOS Shell (in the manner of MS-DOS Shell): the programs on the left, the files on the disk on the
 * right, the disk's use and the floppy drive below. Tab moves between the two lists.</li>
 * <li><b>Icons</b>: the Program Manager ({@link ShellIcons}): a window of program icons; Tab turns it into the File
 * Manager (the files list).</li>
 * <li><b>Command line</b>: the RC-DOS prompt ({@link ShellPrompt}): DIR, COPY, DEL, TYPE... and programs run by name.</li>
 * </ul>
 * In the list and icon views, Enter runs a program or opens a file in the program registered for its extension
 * ({@link ComputerAppScreens#registerOpener}); pictures ({@code .IMG}) open in a viewer here. On a file: Del deletes,
 * F7 (or F2) renames, F5 prints, and F6 scans what is on the scanner's glass into a new file. Escape closes the screen
 * (at the prompt, EXIT does). The password is SETUP's now (it was F8 here).
 * <p>
 * Drive A: (computer batch 3A): with a blank or data floppy in the drive, F3 shows its files instead of C:'s (and back),
 * F8 copies the file in the list to the other drive, and F4 labels the floppy. Files on A: open only as pictures here;
 * copy them to C: for the programs. Programs never go on a floppy.
 */
final class ShellApp extends DosApp {

	private final DosList programs = new DosList();
	private final DosList files = new DosList();
	final List<ComputerApp> programList = new ArrayList<>();
	final List<ComputerFiles.Info> fileList = new ArrayList<>();
	boolean filesFocused = false;
	private final ShellIcons icons = new ShellIcons(this);
	private final ShellPrompt prompt = new ShellPrompt(this);
	long used = 0L, capacity = 0L;
	int count = 0;
	/** The drive the files list shows: C:, or A: (the floppy). */
	char drive = com.arrl.radiocraft.common.computer.DrivePath.COMPUTER;
	// The picture being viewed, if any.
	@Nullable
	private String viewing;
	@Nullable
	private byte[] picture;

	ShellApp(ComputerSession session) {
		super(session, null);
		programs.onActivate(index -> {
			if(index >= 0 && index < programList.size())
				session.launch(programList.get(index));
		});
		files.onActivate(index -> {
			if(index >= 0 && index < fileList.size())
				openFile(fileList.get(index).name());
		});
	}

	@Override
	public void opened() {
		refreshFiles();
	}

	@Override
	public void resumed() {
		session.refresh();
		refreshFiles();
		if(view() == ComputerSettings.View.COMMAND)
			prompt.resumed();
	}

	/** The launcher SETUP chose. */
	ComputerSettings.View view() {
		return session.settings().view();
	}

	ComputerSession session() {
		return session;
	}

	DosList filesList() {
		return files;
	}

	/** The drive the files list shows. */
	ComputerFiles volume() {
		return session.files(drive);
	}

	boolean onFloppy() {
		return drive == com.arrl.radiocraft.common.computer.DrivePath.FLOPPY;
	}

	/** F3: the files list shows the other drive (A: only with a blank or data floppy in it). */
	void switchDrive() {
		if(!onFloppy() && !session.hasDataFloppy()) {
			session.message(Dos.tr(session.hasFloppy() ? "shell.program_disk" : "prompt.no_drive_a", Dos.drive(session)), true);
			return;
		}
		drive = onFloppy() ? com.arrl.radiocraft.common.computer.DrivePath.COMPUTER : com.arrl.radiocraft.common.computer.DrivePath.FLOPPY;
		files.setItems(List.of());
		fileList.clear();
		refreshFiles();
	}

	/** F8: copies {@code file} to the other drive, asking before it replaces one there. */
	void copyAcross(ComputerFiles.Info file, boolean overwrite) {
		char other = onFloppy() ? com.arrl.radiocraft.common.computer.DrivePath.COMPUTER : com.arrl.radiocraft.common.computer.DrivePath.FLOPPY;
		if(other == com.arrl.radiocraft.common.computer.DrivePath.FLOPPY && !session.hasDataFloppy()) {
			session.message(Dos.tr(session.hasFloppy() ? "shell.program_disk" : "prompt.no_drive_a", Dos.drive(session)), true);
			return;
		}
		volume().copy(file.name(), other + ":", overwrite, response -> {
			if(!overwrite && !response.ok() && "exists".equals(response.error()))
				showDialog(DosDialog.confirm(Dos.tr("shell.copy_title"), Dos.tr("shell.copy_replace", other + ":" + file.name()), () -> copyAcross(file, true)));
			else
				done(response, Dos.tr("shell.copied", file.name(), other + ":"));
		});
	}

	/** F4: names the floppy. */
	void labelFloppy() {
		if(!session.hasDataFloppy()) {
			session.message(Dos.tr(session.hasFloppy() ? "shell.program_disk" : "prompt.no_drive_a", Dos.drive(session)), true);
			return;
		}
		showDialog(DosDialog.ask(Dos.tr("shell.label_title"), Dos.tr("shell.label", com.arrl.radiocraft.common.computer.FloppyDisk.LABEL_MAX),
				new DosInput(session.floppyLabel(), com.arrl.radiocraft.common.computer.FloppyDisk.LABEL_MAX, c -> c >= ' ' && c <= '~'),
				typed -> session.floppyFiles().label(typed, response -> done(response, Dos.tr("shell.labelled", Dos.drive(session))))));
	}

	void refreshFiles() {
		if(onFloppy() && !session.hasDataFloppy()) // The floppy came out: back to C:.
			drive = com.arrl.radiocraft.common.computer.DrivePath.COMPUTER;
		char asked = drive;
		volume().list("", response -> {
			if(asked != drive)
				return; // Switched drives meanwhile.
			if(!response.ok()) {
				if(onFloppy()) { // No disk any more.
					drive = com.arrl.radiocraft.common.computer.DrivePath.COMPUTER;
					refreshFiles();
				}
				return;
			}
			fileList.clear();
			fileList.addAll(ComputerFiles.infos(response));
			used = response.data().getLong("used");
			capacity = response.data().getLong("capacity");
			count = response.data().getInt("count");
			List<String> lines = new ArrayList<>();
			for(ComputerFiles.Info file : fileList)
				lines.add(String.format("%-12s %8s", file.name(), Dos.size(file.size())));
			files.setItems(lines);
		});
	}

	@Nullable
	ComputerFiles.Info selectedFile() {
		int index = files.selected();
		return index >= 0 && index < fileList.size() ? fileList.get(index) : null;
	}

	/** Opens {@code name}: in its program, or a picture in the viewer. */
	@Override
	public void openFile(String name) {
		String extension = ComputerStorage.extension(name);
		if(ComputerFileFormats.IMAGE.equals(extension)) {
			volume().read(name, response -> {
				if(!response.ok()) {
					showError(response);
					return;
				}
				byte[] data = response.data().getByteArray("data");
				if(data.length == PhotoImage.PIXELS) {
					viewing = name;
					picture = data;
				}
			});
			return;
		}
		ComputerApp opener = ComputerAppScreens.opener(extension);
		if(onFloppy()) // Programs open files on C:.
			session.message(Dos.tr("shell.open_from_a", name), true);
		else if(opener != null && session.isInstalled(opener))
			session.launch(opener, name);
		else
			session.message(Dos.tr("shell.no_opener", extension), true);
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		if(viewing != null && picture != null) {
			renderPicture(g);
			return;
		}
		programList.clear();
		programList.addAll(session.apps());
		switch(view()) {
			case ICONS -> {
				icons.render(g, now);
				return;
			}
			case COMMAND -> {
				prompt.render(g, now);
				return;
			}
			default -> {}
		}
		List<String> lines = new ArrayList<>();
		for(ComputerApp app : programList)
			lines.add(String.format("%-12s %s", app.fileName(), Dos.name(app)));
		programs.setItems(lines);

		Dos.frame(g, Dos.tr("shell.title"), filesFocused ? Dos.tr("shell.keys_files") : Dos.tr("shell.keys_programs"));
		String serial = session.serial().isEmpty() ? "" : Dos.tr("shell.serial", session.serial());
		g.put(Math.min(TextGrid.COLS - 1, Dos.statusStart() - 2) - serial.length(), 0, serial, DosColours.TITLE_FG, DosColours.TITLE_BG);

		g.box(1, 2, 38, 18, !filesFocused, Dos.tr("shell.programs"), DosColours.TEXT_FG, DosColours.BODY_BG);
		programs.render(g, 2, 3, 36, 16, DosColours.TEXT_FG, DosColours.BODY_BG, !filesFocused);
		g.box(41, 2, 38, 18, filesFocused, Dos.tr("shell.files", drive), DosColours.TEXT_FG, DosColours.BODY_BG);
		if(fileList.isEmpty())
			g.put(43, 4, Dos.fit(Dos.tr(onFloppy() ? "shell.no_files_a" : "shell.no_files"), 35), DosColours.BODY_FG, DosColours.BODY_BG);
		else
			files.render(g, 42, 3, 36, 16, DosColours.TEXT_FG, DosColours.BODY_BG, filesFocused);

		// What is selected.
		String about;
		if(filesFocused) {
			ComputerFiles.Info file = selectedFile();
			about = file == null ? "" : Dos.tr("shell.file_info", file.name(), Dos.size(file.size()));
		}
		else {
			int index = programs.selected();
			about = index >= 0 && index < programList.size() ? Dos.description(programList.get(index)) : "";
		}
		g.put(2, 20, Dos.fit(about, TextGrid.COLS - 4), DosColours.ACCENT, DosColours.BODY_BG);
		renderDisk(g, 21);
		renderMessage(g);
	}

	/** The disk's use (row {@code row}) and the floppy drive (the row below). */
	void renderDisk(TextGrid g, int row) {
		String disk = Dos.tr("shell.disk", drive, Dos.size(used), Dos.size(capacity), count);
		g.put(2, row, disk, DosColours.BODY_FG, DosColours.BODY_BG);
		g.bar(4 + disk.length(), row, 20, capacity <= 0 ? 0.0D : used / (double)capacity, DosColours.LIGHT_CYAN, DosColours.BODY_BG);
		String floppy = Dos.tr("shell.drive", Dos.drive(session));
		g.put(2, row + 1, floppy, DosColours.BODY_FG, DosColours.BODY_BG);
		if(session.hasDataFloppy()) // The drive keys, where the floppy is named.
			g.put(4 + floppy.length(), row + 1, Dos.tr(onFloppy() ? "shell.drive_keys_a" : "shell.drive_keys_c"), DosColours.DIM, DosColours.BODY_BG);
	}

	/** The session's fresh message on row 23. */
	void renderMessage(TextGrid g) {
		String message = session.message();
		if(!message.isEmpty())
			g.putField(0, 23, " " + message, TextGrid.COLS, session.messageIsError() ? DosColours.ERROR_FG : DosColours.BLACK,
					session.messageIsError() ? DosColours.ERROR_BG : DosColours.LIGHT_GREY);
	}

	private void renderPicture(TextGrid g) {
		Dos.frame(g, Dos.tr("view.title", viewing), Dos.tr("view.keys"));
		int size = 200;
		g.box((TextGrid.COLS - 36) / 2, 1, 36, 23, false, "", DosColours.TEXT_FG, DosColours.BODY_BG);
		g.image((TextGrid.WIDTH - size) / 2, (TextGrid.HEIGHT - size) / 2, size, size, "shell:" + viewing, picture, PhotoImage.SIZE, PhotoImage.SIZE);
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onEscape() {
		if(viewing != null) {
			viewing = null;
			picture = null;
			return true;
		}
		if(view() == ComputerSettings.View.COMMAND)
			return prompt.escape(); // Esc clears the line, as in DOS; EXIT leaves.
		return false; // The screen closes.
	}

	@Override
	protected boolean onChar(char c) {
		return viewing == null && view() == ComputerSettings.View.COMMAND && prompt.typed(c);
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(viewing != null) {
			if(key == GLFW.GLFW_KEY_F5)
				print(viewing);
			return true;
		}
		if(view() == ComputerSettings.View.COMMAND)
			return prompt.key(key);
		if(view() == ComputerSettings.View.ICONS && key == GLFW.GLFW_KEY_TAB) {
			filesFocused = !filesFocused;
			return true;
		}
		if(view() == ComputerSettings.View.ICONS && !filesFocused && key != GLFW.GLFW_KEY_F6)
			return icons.key(key);
		if(key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_LEFT && filesFocused || key == GLFW.GLFW_KEY_RIGHT && !filesFocused) {
			filesFocused = !filesFocused;
			return true;
		}
		if(key == GLFW.GLFW_KEY_F6) {
			scan();
			return true;
		}
		if(!filesFocused)
			return programs.keyPressed(key);
		ComputerFiles.Info file = selectedFile();
		ComputerFiles volume = volume();
		switch(key) {
			case GLFW.GLFW_KEY_DELETE -> {
				if(file != null)
					showDialog(DosDialog.confirm(Dos.tr("shell.delete_title"), Dos.tr("shell.delete", file.name()),
							() -> volume.delete(file.name(), response -> done(response, Dos.tr("shell.deleted", file.name())))));
			}
			case GLFW.GLFW_KEY_F2, GLFW.GLFW_KEY_F7 -> { // F7: Minecraft keeps F2 for screenshots.
				if(file != null)
					showDialog(DosDialog.ask(Dos.tr("shell.rename_title"), Dos.tr("shell.rename", file.name()), DosInput.fileName(file.name()),
							to -> volume.rename(file.name(), to, response -> done(response, Dos.tr("shell.renamed", to.toUpperCase(java.util.Locale.ROOT))))));
			}
			case GLFW.GLFW_KEY_F5 -> {
				if(file != null && onFloppy())
					session.message(Dos.tr("shell.open_from_a", file.name()), true);
				else if(file != null)
					print(file.name());
			}
			case GLFW.GLFW_KEY_F3 -> switchDrive();
			case GLFW.GLFW_KEY_F4 -> labelFloppy();
			case GLFW.GLFW_KEY_F8 -> {
				if(file != null)
					copyAcross(file, false);
			}
			default -> {
				return files.keyPressed(key);
			}
		}
		return true;
	}

	void print(String name) {
		String extension = ComputerStorage.extension(name);
		if(ComputerFileFormats.IMAGE.equals(extension))
			session.scanner().printImage(name, response -> done(response, Dos.tr("shell.printed", name)));
		else if(ComputerFileFormats.TEXT.equals(extension))
			session.scanner().printTextFile(name, response -> done(response, Dos.tr("shell.printed", name)));
		else
			session.message(Dos.tr("shell.cannot_print", extension), true);
	}

	void scan() {
		showDialog(DosDialog.ask(Dos.tr("shell.scan_title"), Dos.tr("shell.scan"), DosInput.fileName("SCAN"),
				name -> session.scanner().save(name, response -> done(response, Dos.tr("shell.scanned", response.data().getString("name"))))));
	}

	/** After a change to the files: the message, or the error, and the list read again. */
	void done(ComputerResponse response, String success) {
		if(response.ok())
			session.message(success, false);
		else
			session.message(response.errorText(), true);
		if(onFloppy())
			session.refresh(); // The floppy may be a data floppy now, or blank again, or have a new label.
		refreshFiles();
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(viewing != null || view() == ComputerSettings.View.COMMAND)
			return true;
		if(view() == ComputerSettings.View.ICONS && !filesFocused)
			return icons.click(col, row);
		if(programs.contains(col, row)) {
			filesFocused = false;
			return programs.mouseClicked(col, row);
		}
		if(files.contains(col, row)) {
			filesFocused = true;
			return files.mouseClicked(col, row);
		}
		return false;
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		if(viewing == null && view() == ComputerSettings.View.COMMAND)
			return prompt.scroll(amount);
		if(viewing == null && view() == ComputerSettings.View.ICONS && !filesFocused)
			return icons.scroll(amount);
		return programs.mouseScrolled(col, row, amount) || files.mouseScrolled(col, row, amount);
	}
}
