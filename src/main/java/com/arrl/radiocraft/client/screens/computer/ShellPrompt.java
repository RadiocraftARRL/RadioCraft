package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerSettings;
import com.arrl.radiocraft.common.computer.ComputerStorage;
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
 */
final class ShellPrompt {

	private static final int MAX_LINES = 300, MAX_INPUT = 76;
	private static final String PROMPT = "C:\\>";

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

	ShellPrompt(ShellApp shell) {
		this.shell = shell;
	}

	private ComputerSession session() {
		return shell.session();
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
			shown.add(new Out(question != null ? Dos.tr("prompt.yes_no") + input : PROMPT + input, DosColours.LIGHT_GREY));
		int rows = TextGrid.ROWS;
		back = Math.max(0, Math.min(back, Math.max(0, shown.size() - rows)));
		int first = Math.max(0, shown.size() - rows - back);
		for(int row = 0; row < rows && first + row < shown.size(); row++) {
			Out out = shown.get(first + row);
			g.put(0, row, out.text(), out.colour(), DosColours.BLACK);
		}
		if(!busy && back == 0) {
			int promptWidth = question != null ? Dos.tr("prompt.yes_no").length() : PROMPT.length();
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
		print(PROMPT + typed);
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
			case "C:", "CD", "CHDIR" -> {
				if(!command.equals("C:") && !line.arg(0).isEmpty() && !line.arg(0).equals("\\"))
					print(Dos.tr("prompt.no_directories"));
			}
			case "A:" -> print(Dos.tr("prompt.no_drive_a"));
			default -> program(line);
		}
	}

	private void help() {
		blank();
		print(Dos.tr("prompt.help_title"), DosColours.WHITE);
		for(String key : List.of("dir", "copy", "del", "ren", "type", "print", "cls", "time", "ver", "settings", "exit"))
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

	/** Reads the disk's file names (for Tab and DIR), then {@code then} with the files. */
	private void withFiles(Consumer<List<ComputerFiles.Info>> then) {
		busy = true;
		session().files().list("", response -> {
			busy = false;
			if(!response.ok()) {
				print(response.errorText(), DosColours.LIGHT_RED);
				return;
			}
			List<ComputerFiles.Info> files = ComputerFiles.infos(response);
			knownFiles.clear();
			for(ComputerFiles.Info file : files)
				knownFiles.add(file.name());
			shell.used = response.data().getLong("used");
			shell.capacity = response.data().getLong("capacity");
			then.accept(files);
		});
	}

	private void refreshNames() {
		withFiles(files -> {});
	}

	private void dir(DosCommands.Line line) {
		String pattern = line.names().isEmpty() ? "*.*" : line.names().getFirst();
		boolean wide = line.has("/W");
		withFiles(files -> {
			String name = session().settings().name();
			blank();
			print(Dos.tr(name.isEmpty() ? "prompt.dir_volume_none" : "prompt.dir_volume", name.toUpperCase(Locale.ROOT)));
			print(Dos.tr("prompt.dir_serial", session().serial()));
			blank();
			print(Dos.tr("prompt.dir_of"));
			blank();
			List<String> wideNames = new ArrayList<>();
			int count = 0;
			long bytes = 0L;
			for(ComputerApp app : session().apps())
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
			print(Dos.tr("prompt.dir_free", String.format(Locale.ROOT, "%,14d", Math.max(0L, shell.capacity - shell.used))));
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
		String name = line.names().getFirst().toUpperCase(Locale.ROOT);
		busy = true;
		session().files().readText(name, (text, response) -> {
			busy = false;
			if(!response.ok())
				print(response.errorText());
			else
				print(text.replace("\r", ""));
		});
	}

	private void copy(DosCommands.Line line) {
		if(!needs(line, 2))
			return;
		String from = line.names().get(0).toUpperCase(Locale.ROOT), to = line.names().get(1).toUpperCase(Locale.ROOT);
		if(to.equals("C:") || to.equals("C:\\") || to.equals(".")) {
			print(Dos.tr("prompt.copy_onto_itself"));
			return;
		}
		boolean overwrite = line.has("/Y");
		withFiles(files -> {
			if(files.stream().noneMatch(f -> f.name().equals(from))) {
				print(Dos.tr("prompt.file_not_found"));
				return;
			}
			if(files.stream().anyMatch(f -> f.name().equals(to)) && !overwrite) {
				print(Dos.tr("prompt.copy_exists", to));
				return;
			}
			busy = true;
			session().files().read(from, read -> {
				if(!read.ok()) {
					busy = false;
					print(read.errorText());
					return;
				}
				session().files().write(to, read.data().getByteArray("data"), written -> {
					busy = false;
					print(written.ok() ? Dos.tr("prompt.copied") : written.errorText());
					shell.refreshFiles();
				});
			});
		});
	}

	private void delete(DosCommands.Line line) {
		if(!needs(line, 1))
			return;
		String pattern = line.names().getFirst();
		Runnable go = () -> withFiles(files -> {
			List<String> doomed = new ArrayList<>();
			for(ComputerFiles.Info file : files)
				if(DosCommands.matches(pattern, file.name()))
					doomed.add(file.name());
			if(doomed.isEmpty()) {
				boolean program = session().apps().stream().anyMatch(app -> DosCommands.matches(pattern, app.fileName()));
				print(Dos.tr(program ? "prompt.access_denied" : "prompt.file_not_found"));
				return;
			}
			deleteAll(doomed, 0);
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

	private void deleteAll(List<String> names, int index) {
		if(index >= names.size()) {
			shell.refreshFiles();
			return;
		}
		busy = true;
		session().files().delete(names.get(index), response -> {
			busy = false;
			if(!response.ok())
				print(names.get(index) + ": " + response.errorText());
			deleteAll(names, index + 1);
		});
	}

	private void rename(DosCommands.Line line) {
		if(!needs(line, 2))
			return;
		busy = true;
		session().files().rename(line.names().get(0).toUpperCase(Locale.ROOT), line.names().get(1).toUpperCase(Locale.ROOT), response -> {
			busy = false;
			if(!response.ok())
				print(response.errorText());
			shell.refreshFiles();
			refreshNames();
		});
	}

	private void printFile(DosCommands.Line line) {
		if(!needs(line, 1))
			return;
		String name = line.names().getFirst().toUpperCase(Locale.ROOT);
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
