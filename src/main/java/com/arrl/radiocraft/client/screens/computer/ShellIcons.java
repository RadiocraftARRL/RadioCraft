package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.common.computer.ComputerApp;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The launcher's icon view, the RC-DOS Program Manager (in the manner of Windows 3.1's): one framed window of program
 * icons (16-colour pictures, {@link DosIcons}, in the computer's colour scheme) with each program's name under its
 * icon. The arrow keys or a click move the highlight, Enter or a double-click runs the program, the wheel or Page
 * Up/Down scroll. Tab turns the window into the File Manager (the shell's files list, handled by {@link ShellApp}).
 */
final class ShellIcons {

	/** The window: its box and the tiles inside it. */
	private static final int WIN_COL = 1, WIN_ROW = 1, WIN_W = TextGrid.COLS - 2, WIN_H = 20;
	private static final int TILE_W = 12, TILE_H = 6, TILES_COL = 4, TILES_ROW = 2;
	/** The icon's size in screen pixels. */
	static final int ICON = 32;
	/** Two clicks on the same icon within this many ms run it. */
	private static final long DOUBLE_CLICK_MS = 450L;

	private final ShellApp shell;
	private final IconGrid grid = new IconGrid((WIN_W - 4) / TILE_W, 3);
	private int lastClicked = -1;
	private long lastClickAt = 0L;

	ShellIcons(ShellApp shell) {
		this.shell = shell;
	}

	private List<ComputerApp> programs() {
		return shell.programList;
	}

	// -------------------- DRAWING --------------------

	void render(TextGrid g, long now) {
		ComputerSession session = shell.session();
		boolean files = shell.filesFocused;
		Dos.frame(g, Dos.tr(files ? "shell.files_title" : "shell.icons_title"),
				Dos.tr(files ? "shell.keys_files" : "shell.keys_icons"));
		String title = Dos.tr(files ? "shell.file_manager" : "shell.program_manager");
		g.box(WIN_COL, WIN_ROW, WIN_W, WIN_H, true, title, DosColours.TEXT_FG, DosColours.BODY_BG);
		if(files)
			renderFiles(g);
		else
			renderIcons(g, session);
		shell.renderMessage(g);
	}

	private void renderIcons(TextGrid g, ComputerSession session) {
		List<ComputerApp> programs = programs();
		grid.setCount(programs.size());
		boolean pictures = !shell.hasDialog(); // Icons are drawn over the cells: none under a dialog.
		for(int i = 0; i < programs.size(); i++) {
			int[] tile = grid.tile(i);
			if(tile == null)
				continue;
			ComputerApp app = programs.get(i);
			int col = TILES_COL + tile[0] * TILE_W, row = TILES_ROW + tile[1] * TILE_H;
			if(pictures) {
				int x = col * TextGrid.CELL_W + (TILE_W * TextGrid.CELL_W - ICON) / 2;
				int y = row * TextGrid.CELL_H + 2;
				g.icon(x, y, ICON, ICON, DosIcons.forProgram(app.id()));
			}
			boolean selected = i == grid.selected();
			List<String> name = wrap(Dos.name(app), TILE_W - 1, 2);
			for(int line = 0; line < name.size(); line++) {
				String text = name.get(line);
				int at = col + (TILE_W - text.length()) / 2;
				g.put(at, row + 4 + line, text, selected ? DosColours.HIGHLIGHT_FG : DosColours.TEXT_FG,
						selected ? DosColours.HIGHLIGHT_BG : DosColours.BODY_BG);
			}
		}
		// The scroll marks, when there is more above or below.
		if(grid.top() > 0)
			g.set(WIN_COL + WIN_W - 2, TILES_ROW, TextGrid.ARROW_UP, DosColours.TEXT_FG, DosColours.BODY_BG);
		if(grid.top() + grid.rows() < grid.totalRows())
			g.set(WIN_COL + WIN_W - 2, TILES_ROW + grid.rows() * TILE_H - 1, TextGrid.ARROW_DOWN, DosColours.TEXT_FG, DosColours.BODY_BG);
		int index = grid.selected();
		String about = index >= 0 && index < programs.size()
				? programs.get(index).fileName() + "  " + Dos.description(programs.get(index)) : "";
		g.put(WIN_COL + 1, WIN_ROW + WIN_H, Dos.fit(about, WIN_W - 2), DosColours.ACCENT, DosColours.BODY_BG);
		shell.renderDisk(g, WIN_ROW + WIN_H + 1);
	}

	private void renderFiles(TextGrid g) {
		DosList files = shell.filesList();
		if(shell.fileList.isEmpty())
			g.put(WIN_COL + 3, WIN_ROW + 2, Dos.tr("shell.no_files"), DosColours.BODY_FG, DosColours.BODY_BG);
		else
			files.render(g, WIN_COL + 2, WIN_ROW + 1, WIN_W - 4, WIN_H - 2, DosColours.TEXT_FG, DosColours.BODY_BG, true);
		ComputerFiles.Info file = shell.selectedFile();
		String about = file == null ? "" : Dos.tr("shell.file_info", file.name(), Dos.size(file.size()));
		g.put(WIN_COL + 1, WIN_ROW + WIN_H, Dos.fit(about, WIN_W - 2), DosColours.ACCENT, DosColours.BODY_BG);
		shell.renderDisk(g, WIN_ROW + WIN_H + 1);
	}

	/** {@code text} in at most {@code lines} lines of {@code width}, broken at spaces where it can be. */
	static List<String> wrap(String text, int width, int lines) {
		List<String> out = new ArrayList<>();
		String rest = text.trim();
		while(!rest.isEmpty() && out.size() < lines) {
			if(rest.length() <= width) {
				out.add(rest);
				break;
			}
			if(out.size() == lines - 1) {
				out.add(Dos.fit(rest, width));
				break;
			}
			int cut = rest.lastIndexOf(' ', width);
			if(cut <= 0)
				cut = width;
			out.add(rest.substring(0, cut).trim());
			rest = rest.substring(cut).trim();
		}
		return out;
	}

	// -------------------- INPUT --------------------

	boolean key(int key) {
		switch(key) {
			case GLFW.GLFW_KEY_LEFT -> grid.move(-1, 0);
			case GLFW.GLFW_KEY_RIGHT -> grid.move(1, 0);
			case GLFW.GLFW_KEY_UP -> grid.move(0, -1);
			case GLFW.GLFW_KEY_DOWN -> grid.move(0, 1);
			case GLFW.GLFW_KEY_PAGE_UP -> grid.page(-1);
			case GLFW.GLFW_KEY_PAGE_DOWN -> grid.page(1);
			case GLFW.GLFW_KEY_HOME -> grid.home();
			case GLFW.GLFW_KEY_END -> grid.end();
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> run(grid.selected());
			default -> {
				return false;
			}
		}
		return true;
	}

	private void run(int index) {
		if(index >= 0 && index < programs().size())
			shell.session().launch(programs().get(index));
	}

	boolean click(int col, int row) {
		int c = col - TILES_COL, r = row - TILES_ROW;
		if(c < 0 || r < 0)
			return false;
		int index = grid.at(c / TILE_W, r / TILE_H);
		if(index < 0)
			return false;
		long now = System.currentTimeMillis();
		if(index == lastClicked && now - lastClickAt < DOUBLE_CLICK_MS) {
			lastClicked = -1;
			run(index);
		}
		else {
			grid.select(index);
			lastClicked = index;
			lastClickAt = now;
		}
		return true;
	}

	boolean scroll(double amount) {
		grid.scroll(amount > 0 ? -1 : 1);
		return true;
	}
}
