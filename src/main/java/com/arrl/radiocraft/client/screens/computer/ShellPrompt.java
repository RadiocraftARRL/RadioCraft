package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerSettings;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.DrivePath;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The launcher's command-line view: the bare RC-DOS prompt, {@code C:\>}, on a black screen. Commands: {@code DIR}
 * ({@code [pattern] [/W]}), {@code COPY src dst [/Y]}, {@code DEL}/{@code ERASE} (wildcards; {@code *.*} asks first),
 * {@code REN}, {@code TYPE}, {@code PRINT}, {@code CLS}, {@code VER}, {@code TIME}, {@code HELP}, {@code SETTINGS} /
 * {@code SETUP} (SETUP, where the launcher view is changed back) and {@code EXIT} (gets up from the computer). A
 * program runs by its name ({@code RTTY} or {@code RTTY.EXE}), with a file to open after it if wanted
 * ({@code EDIT NOTES.TXT}); when it exits, the prompt comes back. Up and down step through the lines typed (as
 * DOSKEY did), Tab completes file and program names, Page Up/Down and the wheel scroll back, Esc clears the line.
 * The words parsed are {@link DosCommands}'.
 * <p>
 * Drive A: (computer batch 3A): {@code A:} and {@code C:} change the current drive (A: needs a blank or data floppy in
 * the drive), and every file command takes a drive in its names ({@link DrivePath}): {@code DIR A:}, {@code TYPE A:X},
 * {@code DEL A:X}, {@code COPY X A:}, {@code COPY A:*.LOG C:}. {@code LABEL [A:] [name]} names the floppy. Copies run on
 * the server ({@code fs.copy}); a program is never copied onto a floppy.
 */
final class ShellPrompt {

	private static final int MAX_LINES = 300, MAX_INPUT = 76;

	private record Out(String text, int colour) {}

	private final ShellApp shell;
	private final Deque<Out> lines = new ArrayDeque<>();
	private final DosCommands.History history = new DosCommands.History(30);
	private final StringBuilder input = new StringBuilder();
	private int cursor = 0;
	/** Lines scrolled back from the bottom. */
	private int back = 0;
	/** While a command waits for the disk: no prompt, typing is ignored. */
	private boolean busy = false;
	/** A yes/no question waiting for its answer (DEL *.*), or null. */
	private Consumer<Boolean> question;
	/** File names last read from the disk, for Tab. */
	private final List<String> knownFiles = new ArrayList<>();
	private boolean greeted = false;
	/** The current drive: C:, or A: (the floppy). */
	private char drive = DrivePath.COMPUTER;

	ShellPrompt(ShellApp shell) {
		this.shell = shell;
	}

	private ComputerSession session() {
		return shell.session();
	}

	/** {@code C:\>} or {@code A:\>}. */
	private String prompt() {
		return drive + ":\\>";
	}

	// -------------------- OUTPUT --------------------

	private void print(String text) {
		print(text, DosColours.LIGHT_GREY);
	}

	private void print(String text, int colour) {
		for(String line : text.split("\n", -1)) {
			do {
				String part = line.length() > TextGrid.COLS ? line.substring(0, TextGrid.COLS) : line;
				lines.addLast(new Out(part, colour));
				line = line.substring(part.length());
			}
			while(!line.isEmpty());
		}
		while(lines.size() > MAX_LINES)
			lines.removeFirst();
		back = 0;
	}

	private void blank() {
		print("");
	}

	/** Back from a program: the prompt again on a fresh line. */
	void resumed() {
		busy = false;
		blank();
	}

	void render(TextGrid g, long now) {
		if(!greeted) {
			greeted = true;
			print(Dos.tr("boot.version"));
			print(Dos.tr("prompt.hello"));
			blank();
			refreshNames();
		}
		g.clear(DosColours.LIGHT_GREY, DosColours.BLACK);
		List<Out> shown = new ArrayList<>(lines);
		if(!busy)
			shown.add(new Out(question != null ? Dos.tr("prompt.yes_no") + input : prompt() + input, DosColours.LIGHT_GREY));
		int rows = TextGrid.ROWS;
		back = Math.max(0, Math.min(back, Math.max(0, shown.size() - rows)));
		int first = Math.max(0, shown.size() - rows - back);
		for(int row = 0; row < rows && first + row < shown.size(); row++) {
			Out out = shown.get(first + row);
			g.put(0, row, out.text(), out.colour(), DosColours.BLACK);
		}
		if(!busy && back == 0) {
			int promptWidth = question != null ? Dos.tr("prompt.yes_no").length() : prompt().length();
			int row = Math.min(rows, shown.size()) - 1;
			g.cursor(Math.min(TextGrid.COLS - 1, promptWidth + cursor), row);
		}
	}

	// -------------------- INPUT --------------------

	boolean typed(char c) {
		if(busy || !Dos.typeable(c) || input.length() >= MAX_INPUT)
			return true;
		input.insert(cursor++, c);
		back = 0;
		return true;
	}

	boolean escape() {
		input.setLength(0);
		cursor = 0;
		return true;
	}

	boolean scroll(double amount) {
		back = Math.max(0, back + (amount > 0 ? 3 : -3));
		return true;
	}

	boolean key(int key) {
		if(key == GLFW.GLFW_KEY_PAGE_UP) {
			back += TextGrid.ROWS - 2;
			return true;
		}
		if(key == GLFW.GLFW_KEY_PAGE_DOWN) {
			back = Math.max(0, back - (TextGrid.ROWS - 2));
			return true;
		}
		if(busy)
			return true;
		switch(key) {
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> enter();
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if(cursor > 0)
					input.deleteCharAt(--cursor);
			}
			case GLFW.GLFW_KEY_DELETE -> {
				if(cursor < input.length())
					input.deleteCharAt(cursor);
			}
			case GLFW.GLFW_KEY_LEFT -> cursor = Math.max(0, cursor - 1);
			case GLFW.GLFW_KEY_RIGHT -> cursor = Math.min(input.length(), cursor + 1);
			case GLFW.GLFW_KEY_HOME -> cursor = 0;
			case GLFW.GLFW_KEY_END -> cursor = input.length();
			case GLFW.GLFW_KEY_UP -> recall(history.up());
			case GLFW.GLFW_KEY_DOWN -> recall(history.down());
			case GLFW.GLFW_KEY_TAB -> {
				List<String> names = new ArrayList<>(knownFiles);
				for(ComputerApp app : session().apps())
					names.add(app.command());
				set(DosCommands.complete(input.toString(), names));
			}
			default -> {
				return false;
			}
		}
		back = 0;
		return true;
	}

	private void recall(String line) {
		if(line != null)
			set(line);
	}

	private void set(String line) {
		input.setLength(0);
		input.append(line.length() > MAX_INPUT ? line.substring(0, MAX_INPUT) : line);
		cursor = input.length();
	}

	private void enter() {
		String typed = input.toString();
		input.setLength(0);
		cursor = 0;
		if(question != null) {
			Consumer<Boolean> asked = question;
			question = null;
			print(Dos.tr("prompt.yes_no") + typed);
			asked.accept(typed.trim().toUpperCase(Locale.ROOT).startsWith("Y"));
			return;
		}
		print(prompt() + typed);
		history.add(typed);
		run(DosCommands.parse(typed));
	}

	// -------------------- COMMANDS --------------------

	private void run(DosCommands.Line line) {
		String command = line.command();
		switch(command) {
			case "" -> {}
			case "HELP", "?" -> help();
			case "CLS" -> lines.clear();
			case "VER" -> {
				blank();
				print(Dos.tr("boot.version"));
				print(Dos.tr("prompt.ver", version()));
				blank();
			}
			case "TIME", "DATE" -> time();
			case "DIR" -> dir(line);
			case "TYPE" -> type(line);
			case "COPY" -> copy(line);
			case "DEL", "ERASE" -> delete(line);
			case "REN", "RENAME" -> rename(line);
			case "PRINT" -> printFile(line);
			case "SETTINGS" -> session().launch(ComputerApps.SETUP);
			case "EXIT" -> {
				if(Minecraft.getInstance().screen != null)
					Minecraft.getInstance().screen.onClose();
			}
			case "CD", "CHDIR" -> {
				if(!line.arg(0).isEmpty() && !line.arg(0).equals("\\") && !line.arg(0).matches("(?i)[AC]:\\\\?"))
					print(Dos.tr("prompt.no_directories"));
			}
			case "C:", "C:\\" -> drive = DrivePath.COMPUTER;
			case "A:", "A:\\" -> {
				if(floppyReady())
					drive = DrivePath.FLOPPY;
			}
			case "LABEL" -> label(line);
			default -> program(line);
		}
	}

	private void help() {
		blank();
		print(Dos.tr("prompt.help_title"), DosColours.WHITE);
		for(String key : List.of("dir", "copy", "del", "ren", "type", "print", "drive", "label", "cls", "time", "ver", "settings", "exit"))
			print("  " + Dos.tr("prompt.help." + key));
		print(Dos.tr("prompt.help_programs"));
		print(Dos.tr("prompt.help_keys"));
		blank();
	}

	private static String version() {
		return ModList.get().getModContainerById(Radiocraft.MOD_ID).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
	}

	private void time() {
		ComputerSettings settings = session().settings();
		java.time.LocalTime real = java.time.LocalTime.now();
		print(Dos.tr("prompt.time_real", ComputerSettings.time(real.getHour(), real.getMinute(), settings.hour24())));
		if(Minecraft.getInstance().level != null) {
			int[] game = ComputerSettings.gameTime(Minecraft.getInstance().level.getDayTime());
			print(Dos.tr("prompt.time_game", ComputerSettings.time(game[0], game[1], settings.hour24()), Minecraft.getInstance().level.getDayTime() / 24000L + 1L));
		}
	}

	/** A program by name, with an optional file for it to open. */
	private void program(DosCommands.Line line) {
		Map<String, ComputerApp> commands = new java.util.LinkedHashMap<>();
		for(ComputerApp app : session().apps())
			commands.put(app.command(), app);
		String found = DosCommands.program(line.command(), commands.keySet());
		if(found == null) {
			ComputerApp elsewhere = ComputerApps.all().stream()
					.filter(app -> DosCommands.program(line.command(), List.of(app.command())) != null).findFirst().orElse(null);
			print(elsewhere != null ? Dos.tr("prompt.not_installed", elsewhere.fileName()) : Dos.tr("prompt.bad_command"));
			return;
		}
		ComputerApp app = commands.get(found);
		String file = line.names().isEmpty() ? "" : line.names().getFirst();
		if(file.isEmpty())
			session().launch(app);
		else
			session().launch(app, file.toUpperCase(Locale.ROOT));
	}

	/** True if drive A: can be used (a blank or data floppy in it); else says why not. */
	private boolean floppyReady() {
		if(session().hasDataFloppy())
			return true;
		print(session().hasFloppy() ? Dos.tr("shell.program_disk", Dos.drive(session())) : Dos.tr("prompt.no_drive_a"));
		return false;
	}

	/** Reads C:'s file names (for Tab and DIR), then {@code then} with the files. */
	private void withFiles(Consumer<List<ComputerFiles.Info>> then) {
		withFiles(DrivePath.COMPUTER, (files, data) -> then.accept(files));
	}

	/** Reads drive {@code on}'s files, then {@code then} with them and the listing's answer (its use and label). */
	private void withFiles(char on, java.util.function.BiConsumer<List<ComputerFiles.Info>, net.minecraft.nbt.CompoundTag> then) {
		busy = true;
		session().files(on).list("", response -> {
			busy = false;
			if(!response.ok()) {
				print(response.errorText(), DosColours.LIGHT_RED);
				if(on == DrivePath.FLOPPY)
					drive = DrivePath.COMPUTER; // No disk any more.
				return;
			}
			List<ComputerFiles.Info> files = ComputerFiles.infos(response);
			if(on == DrivePath.COMPUTER) {
				knownFiles.clear();
				for(ComputerFiles.Info file : files)
					knownFiles.add(file.name());
				shell.used = response.data().getLong("used");
				shell.capacity = response.data().getLong("capacity");
			}
			then.accept(files, response.data());
		});
	}

	private void refreshNames() {
		withFiles(files -> {});
	}

	/** {@code text} as a path on the current drive; null (and said) if it names a drive there is none of. */
	private DrivePath path(String text) {
		DrivePath path = DrivePath.parse(text, drive);
		if(path == null)
			print(Dos.tr("prompt.invalid_drive"));
		else if(path.onFloppy() && !floppyReady())
			return null;
		return path;
	}

	private void dir(DosCommands.Line line) {
		DrivePath path = line.names().isEmpty() ? new DrivePath(drive, "") : DrivePath.parse(line.names().getFirst(), drive);
		if(path == null) {
			print(Dos.tr("prompt.invalid_drive"));
			return;
		}
		if(path.onFloppy() && !session().hasFloppy()) {
			print(Dos.tr("prompt.no_drive_a"));
			return;
		}
		String pattern = path.isDriveOnly() ? "*.*" : path.name();
		boolean wide = line.has("/W");
		// A program's disk: its program, write-protected; no files to list.
		ComputerApp onDisk = path.onFloppy() ? ComputerApps.get(session().floppy()) : null;
		if(onDisk != null) {
			blank();
			print(Dos.tr("prompt.dir_volume", "A", onDisk.command()));
			blank();
			print(Dos.tr("prompt.dir_of", "A"));
			blank();
			print(String.format(Locale.ROOT, "%-8s %-3s  %10s  %s", onDisk.command(), "EXE", "<PROG>", Dos.name(onDisk)));
			print(Dos.tr("prompt.write_protected"));
			blank();
			return;
		}
		withFiles(path.drive(), (files, listing) -> {
			String name = path.onFloppy() ? listing.getString("label") : session().settings().name();
			String letter = String.valueOf(path.drive());
			blank();
			print(name.isEmpty() ? Dos.tr("prompt.dir_volume_none", letter) : Dos.tr("prompt.dir_volume", letter, name.toUpperCase(Locale.ROOT)));
			print(Dos.tr("prompt.dir_serial", session().serial()));
			blank();
			print(Dos.tr("prompt.dir_of", letter));
			blank();
			List<String> wideNames = new ArrayList<>();
			int count = 0;
			long bytes = 0L;
			for(ComputerApp app : path.onFloppy() ? List.<ComputerApp>of() : session().apps())
				if(DosCommands.matches(pattern, app.fileName())) {
					count++;
					if(wide)
						wideNames.add(app.fileName());
					else
						print(String.format(Locale.ROOT, "%-8s %-3s  %10s  %s", app.command(), "EXE", "<PROG>", Dos.name(app)));
				}
			for(ComputerFiles.Info file : files)
				if(DosCommands.matches(pattern, file.name())) {
					count++;
					bytes += file.size();
					if(wide)
						wideNames.add(file.name());
					else {
						String base = file.name(), ext = ComputerStorage.extension(file.name());
						if(!ext.isEmpty())
							base = base.substring(0, base.length() - ext.length() - 1);
						print(String.format(Locale.ROOT, "%-8s %-3s  %,10d  %s", base, ext, file.size(), date(file.modified())));
					}
				}
			if(wide)
				for(int i = 0; i < wideNames.size(); i += 5) {
					StringBuilder row = new StringBuilder();
					for(int j = i; j < Math.min(i + 5, wideNames.size()); j++)
						row.append(String.format(Locale.ROOT, "%-16s", wideNames.get(j)));
					print(row.toString());
				}
			if(count == 0)
				print(Dos.tr("prompt.file_not_found"));
			print(Dos.tr("prompt.dir_total", String.format(Locale.ROOT, "%9d", count), String.format(Locale.ROOT, "%,14d", bytes)));
			long free = Math.max(0L, listing.getLong("capacity") - listing.getLong("used"));
			print(Dos.tr("prompt.dir_free", String.format(Locale.ROOT, "%,14d", free)));
			blank();
		});
	}

	private static String date(long millis) {
		if(millis <= 0L)
			return "";
		java.time.LocalDateTime at = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault());
		return String.format(Locale.ROOT, "%02d-%02d-%02d  %2d:%02d%s", at.getMonthValue(), at.getDayOfMonth(), at.getYear() % 100,
				at.getHour() % 12 == 0 ? 12 : at.getHour() % 12, at.getMinute(), at.getHour() < 12 ? "a" : "p");
	}

	private boolean needs(DosCommands.Line line, int names) {
		if(line.names().size() < names) {
			print(Dos.tr("prompt.required_parameter"));
			return false;
		}
		return true;
	}

	private void type(DosCommands.Line line) {
		if(!needs(line, 1))
			return;
		DrivePath path = path(line.names().getFirst());
		if(path == null)
			return;
		busy = true;
		session().files(path.drive()).readText(path.name(), (text, response) -> {
			busy = false;
			if(!response.ok())
				print(response.errorText());
			else
				print(text.replace("\r", ""));
		});
	}

	private void copy(DosCommands.Line line) {
		if(!needs(line, 1))
			return;
		DrivePath from = path(line.names().get(0));
		if(from == null)
			return;
		// COPY A:NOTES.TXT alone copies to the current drive, as DOS does.
		DrivePath to = line.names().size() < 2 ? new DrivePath(drive, "") : line.names().get(1).equals(".") ? new DrivePath(drive, "") : path(line.names().get(1));
		if(to == null)
			return;
		if(from.isDriveOnly()) {
			print(Dos.tr("prompt.required_parameter"));
			return;
		}
		boolean overwrite = line.has("/Y");
		boolean wildcard = from.name().contains("*") || from.name().contains("?");
		if(wildcard && !to.isDriveOnly()) {
			print(Dos.tr("prompt.copy_to_drive"));
			return;
		}
		withFiles(from.drive(), (files, listing) -> {
			List<String> names = new ArrayList<>();
			for(ComputerFiles.Info file : files)
				if(DosCommands.matches(from.name(), file.name()))
					names.add(file.name());
			if(names.isEmpty()) {
				boolean program = !from.onFloppy() && session().apps().stream().anyMatch(app -> DosCommands.matches(from.name(), app.fileName()));
				print(Dos.tr(program ? to.onFloppy() ? "prompt.program_not_copied" : "prompt.access_denied" : "prompt.file_not_found"));
				return;
			}
			if(!wildcard && from.drive() == to.drive() && (to.isDriveOnly() || to.name().equals(names.getFirst()))) {
				print(Dos.tr("prompt.copy_onto_itself"));
				return;
			}
			copyAll(names, 0, from.drive(), to, overwrite, 0);
		});
	}

	/** Copies {@code names} from drive {@code fromDrive} to {@code to} one after the other, then says how many went. */
	private void copyAll(List<String> names, int index, char fromDrive, DrivePath to, boolean overwrite, int copied) {
		if(index >= names.size()) {
			print(Dos.tr("prompt.files_copied", String.format(Locale.ROOT, "%9d", copied)));
			shell.refreshFiles();
			session().refresh(); // A blank floppy is a data floppy now.
			refreshNames();
			return;
		}
		String name = names.get(index);
		String target = to.isDriveOnly() ? to.drive() + ":" : to.toString();
		busy = true;
		session().files(fromDrive).copy(fromDrive + ":" + name, target, overwrite, response -> {
			busy = false;
			int done = copied;
			if(response.ok()) {
				done++;
				if(names.size() > 1)
					print(fromDrive + ":" + name);
			}
			else if("exists".equals(response.error()))
				print(Dos.tr("prompt.copy_exists", to.isDriveOnly() ? to.drive() + ":" + name : to.toString()));
			else
				print(name + ": " + response.errorText());
			copyAll(names, index + 1, fromDrive, to, overwrite, done);
		});
	}

	private void delete(DosCommands.Line line) {
		if(!needs(line, 1))
			return;
		DrivePath path = path(line.names().getFirst());
		if(path == null)
			return;
		String pattern = path.isDriveOnly() ? "*.*" : path.name();
		Runnable go = () -> withFiles(path.drive(), (files, listing) -> {
			List<String> doomed = new ArrayList<>();
			for(ComputerFiles.Info file : files)
				if(DosCommands.matches(pattern, file.name()))
					doomed.add(file.name());
			if(doomed.isEmpty()) {
				boolean program = !path.onFloppy() && session().apps().stream().anyMatch(app -> DosCommands.matches(pattern, app.fileName()));
				print(Dos.tr(program ? "prompt.access_denied" : "prompt.file_not_found"));
				return;
			}
			deleteAll(path.drive(), doomed, 0);
		});
		if(pattern.equals("*.*") || pattern.equals("*")) {
			print(Dos.tr("prompt.delete_all"));
			question = yes -> {
				if(yes)
					go.run();
			};
		}
		else
			go.run();
	}

	private void deleteAll(char on, List<String> names, int index) {
		if(index >= names.size()) {
			shell.refreshFiles();
			if(on == DrivePath.FLOPPY)
				session().refresh(); // An emptied floppy is a blank one again.
			return;
		}
		busy = true;
		session().files(on).delete(names.get(index), response -> {
			busy = false;
			if(!response.ok())
				print(names.get(index) + ": " + response.errorText());
			deleteAll(on, names, index + 1);
		});
	}

	private void rename(DosCommands.Line line) {
		if(!needs(line, 2))
			return;
		DrivePath path = path(line.names().get(0));
		if(path == null)
			return;
		String to = line.names().get(1).toUpperCase(Locale.ROOT);
		if(to.length() >= 2 && to.charAt(1) == ':')
			to = to.substring(2); // REN keeps a file on its drive.
		busy = true;
		session().files(path.drive()).rename(path.name(), to, response -> {
			busy = false;
			if(!response.ok())
				print(response.errorText());
			shell.refreshFiles();
			refreshNames();
		});
	}

	/** {@code LABEL [A:] [name]}: names the floppy; with no name, says its label. C:'s label is SETUP's computer name. */
	private void label(DosCommands.Line line) {
		List<String> words = new ArrayList<>(line.names());
		char on = drive;
		if(!words.isEmpty() && words.getFirst().matches("(?i)[A-Z]:.*")) { // LABEL A: NAME, or LABEL A:NAME
			String first = words.removeFirst();
			on = Character.toUpperCase(first.charAt(0));
			if(first.length() > 2)
				words.addFirst(first.substring(2));
			if(on != DrivePath.COMPUTER && on != DrivePath.FLOPPY) {
				print(Dos.tr("prompt.invalid_drive"));
				return;
			}
		}
		if(on == DrivePath.COMPUTER) {
			print(Dos.tr("prompt.label_c"));
			return;
		}
		if(!floppyReady())
			return;
		String label = String.join(" ", words);
		if(label.isEmpty()) {
			String now = session().floppyLabel();
			print(now.isEmpty() ? Dos.tr("prompt.dir_volume_none", "A") : Dos.tr("prompt.dir_volume", "A", now));
			print(Dos.tr("prompt.label_usage"));
			return;
		}
		busy = true;
		session().floppyFiles().label(label, response -> {
			busy = false;
			if(!response.ok())
				print(response.errorText());
			shell.refreshFiles();
		});
	}

	private void printFile(DosCommands.Line line) {
		if(!needs(line, 1))
			return;
		DrivePath path = path(line.names().getFirst());
		if(path == null)
			return;
		if(path.onFloppy()) {
			print(Dos.tr("prompt.print_from_c"));
			return;
		}
		String name = path.name();
		String extension = ComputerStorage.extension(name);
		Consumer<ComputerResponse> done = response -> {
			busy = false;
			print(response.ok() ? Dos.tr("shell.printed", name) : response.errorText());
		};
		if(com.arrl.radiocraft.common.computer.ComputerFileFormats.IMAGE.equals(extension)) {
			busy = true;
			session().scanner().printImage(name, done);
		}
		else if(com.arrl.radiocraft.common.computer.ComputerFileFormats.TEXT.equals(extension)) {
			busy = true;
			session().scanner().printTextFile(name, done);
		}
		else
			print(Dos.tr("shell.cannot_print", extension));
	}
}
