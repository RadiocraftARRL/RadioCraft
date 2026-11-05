package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.photo.PhotoImage;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * RC-DOS Shell, the radio computer's desktop (in the manner of MS-DOS Shell): the programs on the left, the files on
 * the disk on the right, the disk's use and the floppy drive below. Tab moves between the two lists. Enter runs a
 * program, or opens a file in the program registered for its extension ({@link ComputerAppScreens#registerOpener});
 * pictures ({@code .IMG}) open in a viewer here. On a file: Del deletes, F7 (or F2) renames, F5 prints, and F6 scans what is on
 * the scanner's glass into a new file. F8 sets the computer's password (its owner or an operator; empty removes it).
 * Escape here closes the screen.
 */
final class ShellApp extends DosApp {

	private final DosList programs = new DosList();
	private final DosList files = new DosList();
	private final List<ComputerApp> programList = new ArrayList<>();
	private final List<ComputerFiles.Info> fileList = new ArrayList<>();
	private boolean filesFocused = false;
	private long used = 0L, capacity = 0L;
	private int count = 0;
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
	}

	private void refreshFiles() {
		session.files().list("", response -> {
			if(!response.ok())
				return;
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
	private ComputerFiles.Info selectedFile() {
		int index = files.selected();
		return index >= 0 && index < fileList.size() ? fileList.get(index) : null;
	}

	/** Opens {@code name}: in its program, or a picture in the viewer. */
	@Override
	public void openFile(String name) {
		String extension = ComputerStorage.extension(name);
		if(ComputerFileFormats.IMAGE.equals(extension)) {
			session.files().read(name, response -> {
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
		if(opener != null && session.isInstalled(opener))
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
		List<String> lines = new ArrayList<>();
		for(ComputerApp app : programList)
			lines.add(String.format("%-12s %s", app.fileName(), Dos.name(app)));
		programs.setItems(lines);

		Dos.frame(g, Dos.tr("shell.title"), filesFocused ? Dos.tr("shell.keys_files") : Dos.tr("shell.keys_programs"));
		String serial = session.serial().isEmpty() ? "" : Dos.tr("shell.serial", session.serial());
		g.put(TextGrid.COLS - 1 - serial.length(), 0, serial, DosColours.TITLE_FG, DosColours.TITLE_BG);

		g.box(1, 2, 38, 18, !filesFocused, Dos.tr("shell.programs"), DosColours.TEXT_FG, DosColours.BODY_BG);
		programs.render(g, 2, 3, 36, 16, DosColours.TEXT_FG, DosColours.BODY_BG, !filesFocused);
		g.box(41, 2, 38, 18, filesFocused, Dos.tr("shell.files"), DosColours.TEXT_FG, DosColours.BODY_BG);
		if(fileList.isEmpty())
			g.put(43, 4, Dos.tr("shell.no_files"), DosColours.BODY_FG, DosColours.BODY_BG);
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
		g.put(2, 20, Dos.fit(about, TextGrid.COLS - 4), DosColours.YELLOW, DosColours.BODY_BG);

		// The disk and the drive.
		String disk = Dos.tr("shell.disk", Dos.size(used), Dos.size(capacity), count);
		g.put(2, 21, disk, DosColours.BODY_FG, DosColours.BODY_BG);
		g.bar(4 + disk.length(), 21, 20, capacity <= 0 ? 0.0D : used / (double)capacity, DosColours.LIGHT_CYAN, DosColours.BODY_BG);
		g.put(2, 22, Dos.tr("shell.drive", Dos.drive(session)), DosColours.BODY_FG, DosColours.BODY_BG);

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
		return false; // The screen closes.
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(viewing != null) {
			if(key == GLFW.GLFW_KEY_F5)
				print(viewing);
			return true;
		}
		if(key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_LEFT && filesFocused || key == GLFW.GLFW_KEY_RIGHT && !filesFocused) {
			filesFocused = !filesFocused;
			return true;
		}
		if(key == GLFW.GLFW_KEY_F6) {
			scan();
			return true;
		}
		if(key == GLFW.GLFW_KEY_F8) {
			password();
			return true;
		}
		if(!filesFocused)
			return programs.keyPressed(key);
		ComputerFiles.Info file = selectedFile();
		switch(key) {
			case GLFW.GLFW_KEY_DELETE -> {
				if(file != null)
					showDialog(DosDialog.confirm(Dos.tr("shell.delete_title"), Dos.tr("shell.delete", file.name()),
							() -> session.files().delete(file.name(), response -> done(response, Dos.tr("shell.deleted", file.name())))));
			}
			case GLFW.GLFW_KEY_F2, GLFW.GLFW_KEY_F7 -> { // F7: Minecraft keeps F2 for screenshots.
				if(file != null)
					showDialog(DosDialog.ask(Dos.tr("shell.rename_title"), Dos.tr("shell.rename", file.name()), DosInput.fileName(file.name()),
							to -> session.files().rename(file.name(), to, response -> done(response, Dos.tr("shell.renamed", to.toUpperCase(java.util.Locale.ROOT))))));
			}
			case GLFW.GLFW_KEY_F5 -> {
				if(file != null)
					print(file.name());
			}
			default -> {
				return files.keyPressed(key);
			}
		}
		return true;
	}

	private void print(String name) {
		String extension = ComputerStorage.extension(name);
		if(ComputerFileFormats.IMAGE.equals(extension))
			session.scanner().printImage(name, response -> done(response, Dos.tr("shell.printed", name)));
		else if(ComputerFileFormats.TEXT.equals(extension))
			session.scanner().printTextFile(name, response -> done(response, Dos.tr("shell.printed", name)));
		else
			session.message(Dos.tr("shell.cannot_print", extension), true);
	}

	private void scan() {
		showDialog(DosDialog.ask(Dos.tr("shell.scan_title"), Dos.tr("shell.scan"), DosInput.fileName("SCAN"),
				name -> session.scanner().save(name, response -> done(response, Dos.tr("shell.scanned", response.data().getString("name"))))));
	}

	/** Asks for a new password (empty removes it); the server refuses anyone but the owner or an operator. */
	private void password() {
		showDialog(DosDialog.ask(Dos.tr("shell.password_title"), Dos.tr("shell.password"),
				new DosInput("", com.arrl.radiocraft.common.computer.ComputerLock.MAX_LENGTH).masked(), typed -> {
			net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
			data.putString("password", typed);
			session.request("sys.password", data, response -> {
				if(response.ok())
					session.message(Dos.tr(response.data().getBoolean("password") ? "shell.password_set" : "shell.password_cleared"), false);
				else
					session.message(response.errorText(), true);
			});
		}));
	}

	/** After a change to the files: the message, or the error, and the list read again. */
	private void done(ComputerResponse response, String success) {
		if(response.ok())
			session.message(success, false);
		else
			session.message(response.errorText(), true);
		refreshFiles();
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(viewing != null)
			return true;
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
		return programs.mouseScrolled(col, row, amount) || files.mouseScrolled(col, row, amount);
	}
}
