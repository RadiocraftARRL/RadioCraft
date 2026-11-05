package com.arrl.radiocraft.client.screens.computer;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A scrolling list in the radio computer's text mode: one line per item, the selected one in the highlight bar, a
 * scroll bar on the right when it does not all fit. Keys: Up, Down, Page Up, Page Down, Home, End, Enter (activates).
 * A click selects; a second click on the selected item activates it. The caller lays it out each frame with
 * {@link #render} and passes it the keys and clicks.
 */
public final class DosList {

	private final List<String> items = new ArrayList<>();
	private int selected = 0;
	private int top = 0;
	// Where it was last drawn, for clicks.
	private int col, row, width, height;
	private IntConsumer onActivate = index -> {};

	public void setItems(List<String> lines) {
		items.clear();
		items.addAll(lines);
		selected = Math.max(0, Math.min(selected, items.size() - 1));
	}

	public int size() {
		return items.size();
	}

	public boolean isEmpty() {
		return items.isEmpty();
	}

	/** The selected index, or -1 if the list is empty. */
	public int selected() {
		return items.isEmpty() ? -1 : selected;
	}

	/** The first item in view, as last drawn (for colouring a column of the lines shown, 1.12 step 7). */
	public int top() {
		return top;
	}

	public void select(int index) {
		selected = Math.max(0, Math.min(index, items.size() - 1));
	}

	/** What Enter (or a second click) does with the selected index. */
	public DosList onActivate(IntConsumer action) {
		onActivate = action;
		return this;
	}

	public boolean keyPressed(int key) {
		if(items.isEmpty())
			return false;
		int page = Math.max(1, height - 1);
		switch(key) {
			case GLFW.GLFW_KEY_UP -> select(selected - 1);
			case GLFW.GLFW_KEY_DOWN -> select(selected + 1);
			case GLFW.GLFW_KEY_PAGE_UP -> select(selected - page);
			case GLFW.GLFW_KEY_PAGE_DOWN -> select(selected + page);
			case GLFW.GLFW_KEY_HOME -> select(0);
			case GLFW.GLFW_KEY_END -> select(items.size() - 1);
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> onActivate.accept(selected);
			default -> {
				return false;
			}
		}
		return true;
	}

	/** True if the cell is on the list as last drawn. */
	public boolean contains(int c, int r) {
		return c >= col && c < col + width && r >= row && r < row + height;
	}

	public boolean mouseClicked(int c, int r) {
		if(!contains(c, r))
			return false;
		int index = top + (r - row);
		if(index >= 0 && index < items.size()) {
			if(index == selected)
				onActivate.accept(index);
			else
				select(index);
		}
		return true;
	}

	public boolean mouseScrolled(int c, int r, double amount) {
		if(!contains(c, r) || items.isEmpty())
			return false;
		select(selected - (int)Math.signum(amount));
		return true;
	}

	/**
	 * Draws the list in {@code w} by {@code h} cells at {@code c, r}, items in {@code fg} on {@code bg}, the selected
	 * one highlighted (only while {@code focused}; dimmer otherwise).
	 */
	public void render(TextGrid g, int c, int r, int w, int h, int fg, int bg, boolean focused) {
		col = c;
		row = r;
		width = w;
		height = h;
		if(selected < top)
			top = selected;
		if(selected >= top + h)
			top = selected - h + 1;
		top = Math.max(0, Math.min(top, Math.max(0, items.size() - h)));
		boolean bar = items.size() > h;
		int textWidth = bar ? w - 1 : w;
		for(int i = 0; i < h; i++) {
			int index = top + i;
			String line = index < items.size() ? items.get(index) : "";
			boolean sel = index == selected && index < items.size();
			int f = sel ? focused ? DosColours.HIGHLIGHT_FG : fg : fg;
			int b = sel ? focused ? DosColours.HIGHLIGHT_BG : DosColours.DARK_GREY : bg;
			g.putField(c, r + i, line, textWidth, f, b);
		}
		if(bar) { // The scroll bar: arrows at the ends, the thumb where the view is.
			int x = c + w - 1;
			g.set(x, r, TextGrid.ARROW_UP, fg, bg);
			g.set(x, r + h - 1, TextGrid.ARROW_DOWN, fg, bg);
			for(int i = 1; i < h - 1; i++)
				g.set(x, r + i, TextGrid.SHADE_LIGHT, fg, bg);
			if(h > 2) {
				int thumb = 1 + (int)Math.round((h - 3) * (double)selected / Math.max(1, items.size() - 1));
				g.set(x, r + thumb, TextGrid.FULL, fg, bg);
			}
		}
	}
}
