package com.arrl.radiocraft.client.screens.computer;

import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A DOS program's pull-down menus in its title row (2026-11-11, "File  Program  Edit" in the CPS): click a name to
 * open it, click an item (or arrow to it and Enter) to do it; Esc or a click elsewhere closes it, F10 opens the first.
 * Each item shows the key that does the same, which still works on its own.
 */
public final class DosMenuBar {

	/**
	 * One thing to do: its words, the key that does it too ("F5", "^S", or ""), the doing, and when it can be done
	 * (2026-11-12: an item that only works on one page or in one mode shows grey there, and clicking it does nothing).
	 */
	public record Item(String label, String key, Runnable action, BooleanSupplier enabled) {

		public Item(String label, String key, Runnable action) {
			this(label, key, action, () -> true);
		}

		/** This item, usable only while {@code when} is true. */
		public Item when(BooleanSupplier when) {
			return new Item(label, key, action, when);
		}

		boolean usable() {
			return enabled.getAsBoolean();
		}
	}

	public record Menu(String name, List<Item> items) {}

	private static final int FIRST_COL = 1, GAP = 2;

	private final List<Menu> menus;
	/** The open menu, or -1. */
	private int open = -1;
	private int highlighted = 0;
	/** False where the program's own F10 does something else: its menus then open only with a click. */
	private boolean f10Opens = true;

	public DosMenuBar(List<Menu> menus) {
		this.menus = menus;
	}

	/** Leaves F10 to the program (BOOKEDIT, RTTY and LICENSE use it): the menus then open only with a click. */
	public DosMenuBar withoutF10() {
		f10Opens = false;
		return this;
	}

	/**
	 * A copy with {@code item} added just above the Exit item (the one done by Esc), or at the end of the first menu
	 * when there is none: the shell switch every program has (2026-11-15).
	 */
	public DosMenuBar withItemBeforeExit(Item item) {
		List<Menu> out = new java.util.ArrayList<>(menus);
		int menu = 0, at = -1;
		for(int m = 0; m < out.size() && at < 0; m++)
			for(int i = 0; i < out.get(m).items().size(); i++)
				if(out.get(m).items().get(i).key().equals("Esc")) {
					menu = m;
					at = i;
					break;
				}
		if(out.isEmpty())
			return this;
		List<Item> items = new java.util.ArrayList<>(out.get(menu).items());
		items.add(at < 0 ? items.size() : at, item);
		out.set(menu, new Menu(out.get(menu).name(), List.copyOf(items)));
		DosMenuBar copy = new DosMenuBar(List.copyOf(out));
		copy.f10Opens = f10Opens;
		return copy;
	}

	public boolean isOpen() {
		return open >= 0;
	}

	public void close() {
		open = -1;
	}

	/** Where menu {@code index}'s name starts in the bar. */
	private int colOf(int index) {
		int col = FIRST_COL;
		for(int i = 0; i < index; i++)
			col += menus.get(i).name().length() + GAP;
		return col;
	}

	/** The column just past the last name: where the program's title can go. */
	public int end() {
		return colOf(menus.size());
	}

	private int width(Menu menu) {
		int w = 0;
		for(Item item : menu.items())
			w = Math.max(w, item.label().length() + (item.key().isEmpty() ? 0 : item.key().length() + 3));
		return w + 4;
	}

	/** Draws the names on {@code row} (the title bar), then the open menu over everything under it. Draw it last. */
	public void render(TextGrid g, int row) {
		for(int i = 0; i < menus.size(); i++) {
			Menu menu = menus.get(i);
			int col = colOf(i);
			boolean on = i == open;
			int fg = on ? DosColours.WHITE : DosColours.TITLE_FG, bg = on ? DosColours.BLACK : DosColours.TITLE_BG;
			g.put(col - 1, row, " " + menu.name() + " ", fg, bg);
			g.set(col, row, menu.name().charAt(0), on ? DosColours.WHITE : DosColours.RED, bg); // The first letter, as DOS menus showed it.
		}
		if(open < 0)
			return;
		Menu menu = menus.get(open);
		int col = Math.max(0, Math.min(colOf(open) - 1, TextGrid.COLS - width(menu) - 1));
		int w = width(menu), h = menu.items().size() + 2;
		g.box(col, row + 1, w, h, false, "", DosColours.BLACK, DosColours.LIGHT_GREY);
		g.shadow(col, row + 1, w, h);
		for(int i = 0; i < menu.items().size(); i++) {
			Item item = menu.items().get(i);
			boolean on = i == highlighted, usable = item.usable();
			int fg = !usable ? DosColours.DARK_GREY : on ? DosColours.WHITE : DosColours.BLACK, bg = on ? DosColours.BLACK : DosColours.LIGHT_GREY;
			g.putField(col + 1, row + 2 + i, " " + item.label(), w - 2, fg, bg);
			if(!item.key().isEmpty())
				g.put(col + w - 2 - item.key().length(), row + 2 + i, item.key(), on ? DosColours.WHITE : DosColours.DARK_GREY, bg);
		}
	}

	/**
	 * Takes the pictures and icons out from under the open menu (with its shadow), as they are drawn over the text and
	 * would hide it. Call after the program has drawn, with the bar on {@code row}.
	 */
	public void clearUnder(TextGrid g, int row) {
		if(open < 0)
			return;
		Menu menu = menus.get(open);
		int w = width(menu), col = Math.max(0, Math.min(colOf(open) - 1, TextGrid.COLS - w - 1));
		int px0 = col * TextGrid.CELL_W, py0 = (row + 1) * TextGrid.CELL_H;
		int px1 = (col + w + 2) * TextGrid.CELL_W, py1 = (row + 2 + menu.items().size() + 1) * TextGrid.CELL_H;
		g.icons().removeIf(icon -> icon.x() < px1 && icon.x() + icon.w() > px0 && icon.y() < py1 && icon.y() + icon.h() > py0);
		g.images().removeIf(image -> image.x() < px1 && image.x() + image.w() > px0 && image.y() < py1 && image.y() + image.h() > py0);
	}

	/** A click at {@code col}, {@code row} with the bar on {@code barRow}: true if the menus took it. */
	public boolean mouseClicked(int col, int row, int barRow) {
		if(row == barRow)
			for(int i = 0; i < menus.size(); i++) {
				int start = colOf(i) - 1;
				if(col >= start && col < start + menus.get(i).name().length() + 2) {
					open = open == i ? -1 : i;
					highlighted = 0;
					return true;
				}
			}
		if(open < 0)
			return false;
		Menu menu = menus.get(open);
		int left = Math.max(0, Math.min(colOf(open) - 1, TextGrid.COLS - width(menu) - 1));
		int index = row - barRow - 2;
		open = -1;
		if(col > left && col < left + width(menu) - 1 && index >= 0 && index < menu.items().size() && menu.items().get(index).usable())
			menu.items().get(index).action().run();
		return true; // A click elsewhere only closes it.
	}

	/** A key: true if the menus took it (F10 opens them; an open menu takes every key). */
	public boolean keyPressed(int key) {
		if(open < 0) {
			if(key != GLFW.GLFW_KEY_F10 || !f10Opens)
				return false;
			open = 0;
			highlighted = 0;
			return true;
		}
		Menu menu = menus.get(open);
		switch(key) {
			case GLFW.GLFW_KEY_UP -> highlighted = Math.floorMod(highlighted - 1, menu.items().size());
			case GLFW.GLFW_KEY_DOWN -> highlighted = Math.floorMod(highlighted + 1, menu.items().size());
			case GLFW.GLFW_KEY_LEFT -> {
				open = Math.floorMod(open - 1, menus.size());
				highlighted = 0;
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				open = Math.floorMod(open + 1, menus.size());
				highlighted = 0;
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				open = -1;
				if(menu.items().get(highlighted).usable())
					menu.items().get(highlighted).action().run();
			}
			case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_F10 -> open = -1;
			default -> {}
		}
		return true;
	}
}
