package com.arrl.radiocraft.client.screens.computer;

import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A multi-line text editor in the radio computer's text mode (the text editor's, and any program's notes field): lines
 * that scroll both ways, the cursor, and the keys EDIT had: arrows, Home/End (Ctrl for the whole text), Page Up/Down,
 * Enter, Backspace, Delete, Tab (four spaces) and Ctrl+Y to delete a line. Text is kept to {@code maxChars}.
 */
public final class DosTextArea {

	private final List<StringBuilder> lines = new ArrayList<>();
	private final int maxChars;
	private int length = 0; // Characters in all, newlines counted.
	private int line = 0, column = 0;
	private int top = 0, left = 0;
	private boolean modified = false;
	// Where it was last drawn.
	private int col, row, width, height;

	public DosTextArea(int maxChars) {
		this.maxChars = maxChars;
		lines.add(new StringBuilder());
	}

	/** The text, lines joined with {@code \n}. */
	public String text() {
		StringBuilder out = new StringBuilder(length);
		for(int i = 0; i < lines.size(); i++) {
			if(i > 0)
				out.append('\n');
			out.append(lines.get(i));
		}
		return out.toString();
	}

	/** Replaces the text (cut to the limit); the cursor goes to the top and it is not modified. */
	public void setText(String text) {
		lines.clear();
		length = 0;
		String normal = text.replace("\r\n", "\n").replace('\r', '\n');
		if(normal.length() > maxChars)
			normal = normal.substring(0, maxChars);
		for(String l : normal.split("\n", -1))
			lines.add(new StringBuilder(clean(l)));
		for(StringBuilder l : lines)
			length += l.length();
		length += lines.size() - 1;
		line = column = top = left = 0;
		modified = false;
	}

	private static String clean(String text) {
		StringBuilder out = new StringBuilder(text.length());
		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			out.append(c == '\t' ? ' ' : c == '\f' || Dos.typeable(c) ? c : '?');
		}
		return out.toString();
	}

	public boolean isModified() {
		return modified;
	}

	public void setModified(boolean value) {
		modified = value;
	}

	public int lineCount() {
		return lines.size();
	}

	public int cursorLine() {
		return line;
	}

	public int cursorColumn() {
		return column;
	}

	public int length() {
		return length;
	}

	private StringBuilder current() {
		return lines.get(line);
	}

	private void clampColumn() {
		column = Math.max(0, Math.min(column, current().length()));
	}

	public boolean keyPressed(int key) {
		boolean ctrl = Screen.hasControlDown();
		int page = Math.max(1, height - 1);
		switch(key) {
			case GLFW.GLFW_KEY_LEFT -> {
				if(column > 0)
					column--;
				else if(line > 0) {
					line--;
					column = current().length();
				}
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				if(column < current().length())
					column++;
				else if(line < lines.size() - 1) {
					line++;
					column = 0;
				}
			}
			case GLFW.GLFW_KEY_UP -> {
				line = Math.max(0, line - 1);
				clampColumn();
			}
			case GLFW.GLFW_KEY_DOWN -> {
				line = Math.min(lines.size() - 1, line + 1);
				clampColumn();
			}
			case GLFW.GLFW_KEY_PAGE_UP -> {
				line = Math.max(0, line - page);
				clampColumn();
			}
			case GLFW.GLFW_KEY_PAGE_DOWN -> {
				line = Math.min(lines.size() - 1, line + page);
				clampColumn();
			}
			case GLFW.GLFW_KEY_HOME -> {
				if(ctrl)
					line = 0;
				column = 0;
			}
			case GLFW.GLFW_KEY_END -> {
				if(ctrl)
					line = lines.size() - 1;
				column = current().length();
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				if(length >= maxChars)
					return true;
				String rest = current().substring(column);
				current().setLength(column);
				lines.add(line + 1, new StringBuilder(rest));
				line++;
				column = 0;
				length++;
				changed();
			}
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if(column > 0) {
					current().deleteCharAt(--column);
					length--;
					changed();
				}
				else if(line > 0) {
					StringBuilder removed = lines.remove(line--);
					column = current().length();
					current().append(removed);
					length--;
					changed();
				}
			}
			case GLFW.GLFW_KEY_DELETE -> {
				if(column < current().length()) {
					current().deleteCharAt(column);
					length--;
					changed();
				}
				else if(line < lines.size() - 1) {
					current().append(lines.remove(line + 1));
					length--;
					changed();
				}
			}
			case GLFW.GLFW_KEY_TAB -> {
				for(int i = 0; i < 4; i++)
					insert(' ');
			}
			case GLFW.GLFW_KEY_Y -> {
				if(!ctrl)
					return false;
				if(lines.size() == 1) {
					length -= current().length();
					current().setLength(0);
				}
				else {
					length -= lines.remove(line).length() + 1;
					line = Math.min(line, lines.size() - 1);
				}
				column = 0;
				changed();
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	public boolean charTyped(char c) {
		if(!Dos.typeable(c))
			return false;
		insert(c);
		return true;
	}

	private void insert(char c) {
		if(length >= maxChars)
			return;
		current().insert(column++, c);
		length++;
		changed();
	}

	private void changed() {
		modified = true;
		Dos.click();
	}

	public boolean mouseClicked(int c, int r) {
		if(c < col || c >= col + width || r < row || r >= row + height)
			return false;
		line = Math.min(lines.size() - 1, top + (r - row));
		column = Math.min(current().length(), left + (c - col));
		return true;
	}

	public boolean mouseScrolled(int c, int r, double amount) {
		if(c < col || c >= col + width || r < row || r >= row + height)
			return false;
		top = Math.max(0, Math.min(lines.size() - 1, top - (int)Math.signum(amount) * 3));
		line = Math.max(top, Math.min(line, top + height - 1));
		line = Math.min(line, lines.size() - 1);
		clampColumn();
		return true;
	}

	/** Draws it in {@code w} by {@code h} cells at {@code c, r}, with the cursor if {@code focused}. */
	public void render(TextGrid g, int c, int r, int w, int h, int fg, int bg, boolean focused) {
		col = c;
		row = r;
		width = w;
		height = h;
		if(line < top)
			top = line;
		if(line >= top + h)
			top = line - h + 1;
		if(column < left)
			left = column;
		if(column >= left + w)
			left = column - w + 1;
		for(int i = 0; i < h; i++) {
			int index = top + i;
			String text = index < lines.size() ? lines.get(index).toString() : "";
			g.putField(c, r + i, left < text.length() ? text.substring(left) : "", w, fg, bg);
		}
		if(focused)
			g.cursor(c + column - left, r + line - top);
	}
}
