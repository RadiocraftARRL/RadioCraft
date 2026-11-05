package com.arrl.radiocraft.client.screens.computer;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * A DOS dialog box over a program ({@link DosApp#showDialog}): a grey double-lined box with a shadow, a message, an
 * optional text field and a row of buttons ({@code < OK >}). Left/Right or Tab pick a button, Enter presses it, a
 * button's first letter presses it too, Escape cancels. Make one with {@link #message}, {@link #confirm},
 * {@link #ask} or {@link #choice}; subclass it for more (see {@link DosFilePicker}).
 */
public class DosDialog {

	/** The program showing it (set by {@link DosApp#showDialog}). */
	DosApp owner;
	protected final String title;
	protected final List<String> lines = new ArrayList<>();
	protected final String[] buttons;
	@Nullable
	protected final DosInput input;
	private final IntConsumer onButton;
	private final Runnable onCancel;
	protected int button = 0;
	private int boxCol, boxRow, boxWidth, boxHeight, buttonsRow;
	private final int[] buttonCols;

	protected DosDialog(String title, String message, @Nullable DosInput input, String[] buttons, IntConsumer onButton, Runnable onCancel) {
		this.title = title;
		this.input = input;
		this.buttons = buttons;
		this.onButton = onButton;
		this.onCancel = onCancel;
		this.buttonCols = new int[buttons.length];
		for(String paragraph : message.split("\n"))
			wrap(paragraph, 56, lines);
	}

	/** A message with an OK button. */
	public static DosDialog message(String title, String message) {
		return new DosDialog(title, message, null, new String[] { Dos.tr("button.ok") }, index -> {}, () -> {});
	}

	/** A Yes / No question; {@code yes} runs on Yes. */
	public static DosDialog confirm(String title, String message, Runnable yes) {
		return new DosDialog(title, message, null, new String[] { Dos.tr("button.yes"), Dos.tr("button.no") }, index -> {
			if(index == 0)
				yes.run();
		}, () -> {});
	}

	/** A question with buttons of its own; {@code chosen} gets the index pressed, or -1 for Escape. */
	public static DosDialog choice(String title, String message, String[] buttons, IntConsumer chosen) {
		return new DosDialog(title, message, null, buttons, chosen, () -> chosen.accept(-1));
	}

	/** Asks for a line of text in {@code field}; {@code ok} gets it on OK (or Enter). */
	public static DosDialog ask(String title, String prompt, DosInput field, Consumer<String> ok) {
		return new DosDialog(title, prompt, field, new String[] { Dos.tr("button.ok"), Dos.tr("button.cancel") }, index -> {
			if(index == 0)
				ok.accept(field.text());
		}, () -> {});
	}

	/** Splits {@code text} into lines of at most {@code width}. */
	static void wrap(String text, int width, List<String> out) {
		StringBuilder line = new StringBuilder();
		for(String word : text.split(" ")) {
			if(!line.isEmpty() && line.length() + 1 + word.length() > width) {
				out.add(line.toString());
				line.setLength(0);
			}
			if(!line.isEmpty())
				line.append(' ');
			line.append(word.length() > width ? word.substring(0, width) : word);
		}
		out.add(line.toString());
	}

	/** Closes it and does what button {@code index} does. */
	protected void press(int index) {
		close();
		onButton.accept(index);
	}

	/** Escape: closes it and runs the cancel action. */
	public void cancel() {
		close();
		onCancel.run();
	}

	protected void close() {
		if(owner != null && owner.hasDialog())
			owner.closeDialog();
	}

	/** The rows its body needs between the title and the buttons (subclasses with more to show say more). */
	protected int bodyRows() {
		return lines.size() + (input != null ? 2 : 0);
	}

	/** The box's width inside its frame. */
	protected int bodyWidth() {
		int width = Math.max(30, title.length() + 4);
		for(String line : lines)
			width = Math.max(width, line.length() + 2);
		int buttonsWidth = 0;
		for(String b : buttons)
			buttonsWidth += b.length() + 6;
		return Math.min(TextGrid.COLS - 6, Math.max(width, buttonsWidth));
	}

	public void render(TextGrid g, long now) {
		int inner = bodyWidth();
		boxWidth = inner + 4;
		boxHeight = bodyRows() + 5;
		boxCol = (TextGrid.COLS - boxWidth) / 2;
		boxRow = Math.max(1, (TextGrid.ROWS - boxHeight) / 2);
		g.box(boxCol, boxRow, boxWidth, boxHeight, true, title, DosColours.DIALOG_FG, DosColours.DIALOG_BG);
		g.shadow(boxCol, boxRow, boxWidth, boxHeight);
		int r = boxRow + 2;
		for(String line : lines)
			g.put(boxCol + 2, r++, line, DosColours.DIALOG_FG, DosColours.DIALOG_BG);
		renderBody(g, boxCol + 2, r, inner, now);
		buttonsRow = boxRow + boxHeight - 2;
		int total = 0;
		for(String b : buttons)
			total += b.length() + 4 + 2;
		int c = boxCol + (boxWidth - total + 2) / 2;
		for(int i = 0; i < buttons.length; i++) {
			String label = "< " + buttons[i] + " >";
			buttonCols[i] = c;
			boolean sel = i == button;
			g.put(c, buttonsRow, label, sel ? DosColours.WHITE : DosColours.DIALOG_FG, sel ? DosColours.BLACK : DosColours.DIALOG_BG);
			g.set(c + 2, buttonsRow, buttons[i].charAt(0), DosColours.HOTKEY_FG, sel ? DosColours.BLACK : DosColours.DIALOG_BG);
			c += label.length() + 2;
		}
	}

	/** Draws what is between the message and the buttons, from {@code row}; by default the text field. */
	protected void renderBody(TextGrid g, int col, int row, int width, long now) {
		if(input != null)
			input.render(g, col, row + 1, width, true);
	}

	public void keyPressed(int key, int scanCode, int modifiers) {
		if(input != null && input.keyPressed(key))
			return;
		switch(key) {
			case GLFW.GLFW_KEY_LEFT -> button = (button + buttons.length - 1) % buttons.length;
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_TAB -> button = (button + 1) % buttons.length;
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> press(input != null ? 0 : button);
			default -> {}
		}
	}

	public void charTyped(char c) {
		if(input != null) {
			input.charTyped(c);
			return;
		}
		for(int i = 0; i < buttons.length; i++) // A button's first letter presses it (Y, N, O, S...).
			if(!buttons[i].isEmpty() && Character.toUpperCase(buttons[i].charAt(0)) == Character.toUpperCase(c)) {
				press(i);
				return;
			}
	}

	public void mouseClicked(int col, int row, int mouseButton) {
		if(row != buttonsRow)
			return;
		for(int i = 0; i < buttons.length; i++)
			if(col >= buttonCols[i] && col < buttonCols[i] + buttons[i].length() + 4) {
				press(i);
				return;
			}
	}

	public void mouseScrolled(int col, int row, double amount) {}

	/** The box's top left cell and size, as last drawn (for subclasses' clicks). */
	protected int boxCol() {
		return boxCol;
	}

	protected int boxRow() {
		return boxRow;
	}
}
