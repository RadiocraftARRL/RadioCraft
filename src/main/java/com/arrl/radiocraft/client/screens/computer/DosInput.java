package com.arrl.radiocraft.client.screens.computer;

import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

/**
 * A one-line text field in the radio computer's text mode: white on black, the cursor where typing goes, scrolling
 * sideways when the text is longer than the field. Keys: arrows, Home, End, Backspace, Delete.
 */
public final class DosInput {

	private final StringBuilder text = new StringBuilder();
	private final int maxLength;
	private final Predicate<Character> allowed;
	private int cursor = 0;
	private int scroll = 0;
	private boolean masked = false;

	/** A field taking up to {@code maxLength} characters that {@code allowed} lets through (and {@link Dos#typeable}). */
	public DosInput(String initial, int maxLength, Predicate<Character> allowed) {
		this.maxLength = maxLength;
		this.allowed = allowed;
		set(initial);
	}

	public DosInput(String initial, int maxLength) {
		this(initial, maxLength, c -> true);
	}

	/** A field for a DOS file name: letters, digits, {@code _ - .}, upper case. */
	public static DosInput fileName(String initial) {
		return new DosInput(initial, 12, c -> Character.isLetterOrDigit(c) && c < 128 || c == '_' || c == '-' || c == '.');
	}

	/** Shows stars instead of the text (a password). */
	public DosInput masked() {
		masked = true;
		return this;
	}

	public String text() {
		return text.toString();
	}

	public void set(String value) {
		text.setLength(0);
		for(int i = 0; i < value.length() && text.length() < maxLength; i++)
			if(Dos.typeable(value.charAt(i)))
				text.append(value.charAt(i));
		cursor = text.length();
	}

	public boolean keyPressed(int key) {
		switch(key) {
			case GLFW.GLFW_KEY_LEFT -> cursor = Math.max(0, cursor - 1);
			case GLFW.GLFW_KEY_RIGHT -> cursor = Math.min(text.length(), cursor + 1);
			case GLFW.GLFW_KEY_HOME -> cursor = 0;
			case GLFW.GLFW_KEY_END -> cursor = text.length();
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if(cursor > 0) {
					text.deleteCharAt(--cursor);
					Dos.click();
				}
			}
			case GLFW.GLFW_KEY_DELETE -> {
				if(cursor < text.length())
					text.deleteCharAt(cursor);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	public boolean charTyped(char c) {
		if(!Dos.typeable(c) || !allowed.test(c) || text.length() >= maxLength)
			return false;
		text.insert(cursor++, c);
		Dos.click();
		return true;
	}

	/** Draws the field {@code width} cells wide at {@code col, row}; with the cursor if {@code focused}. */
	public void render(TextGrid g, int col, int row, int width, boolean focused) {
		if(cursor < scroll)
			scroll = cursor;
		if(cursor - scroll >= width)
			scroll = cursor - width + 1;
		String shown = masked ? "*".repeat(text.length()) : text.toString();
		g.putField(col, row, shown.substring(Math.min(scroll, shown.length())), width, DosColours.FIELD_FG, DosColours.FIELD_BG);
		if(focused)
			g.cursor(col + cursor - scroll, row);
	}
}
