package com.arrl.radiocraft.client.screens.computer;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A form in a DOS dialog box (1.12 step 6): labelled one-line fields under an optional message, with OK and Cancel.
 * Up/Down or Tab move between fields (Shift+Tab back), Enter (or OK) hands every field's text to {@code ok}, which
 * answers null to close the form or a problem to show in red under the fields (the form stays up). A click on a field
 * puts the cursor there. Any program can use it:
 * <pre>{@code
 * showDialog(new DosFormDialog(Dos.tr("x.title"), "", List.of(
 *         new DosFormDialog.Field(Dos.tr("x.call"), new DosInput("", 12))), values -> save(values.get(0))));
 * }</pre>
 */
public final class DosFormDialog extends DosDialog {

	/** One field: its label (left) and its input. */
	public record Field(String label, DosInput input) {}

	private static final int FIELD_MIN_WIDTH = 24;

	private final List<Field> fields;
	private final Function<List<String>, String> ok;
	private int focus = 0;
	private String problem = "";
	private final int labelWidth;
	// Where the fields were last drawn, for clicks.
	private int fieldsCol = -1, fieldsRow = -1;

	public DosFormDialog(String title, String message, List<Field> fields, Function<List<String>, String> ok) {
		super(title, message, null, new String[] { Dos.tr("button.ok"), Dos.tr("button.cancel") }, index -> {}, () -> {});
		this.fields = List.copyOf(fields);
		this.ok = ok;
		int width = 0;
		for(Field field : fields)
			width = Math.max(width, field.label().length());
		this.labelWidth = width;
	}

	/** The fields' current text, in order. */
	public List<String> values() {
		List<String> out = new ArrayList<>(fields.size());
		for(Field field : fields)
			out.add(field.input().text());
		return out;
	}

	/** Puts the cursor in field {@code index}. */
	public DosFormDialog focus(int index) {
		focus = Math.max(0, Math.min(fields.size() - 1, index));
		return this;
	}

	@Override
	protected void press(int index) {
		if(index != 0) {
			close();
			return;
		}
		String refused = ok.apply(values());
		if(refused != null) {
			problem = refused;
			return;
		}
		close();
	}

	@Override
	protected int bodyRows() {
		return super.bodyRows() + fields.size() + 2;
	}

	@Override
	protected int bodyWidth() {
		return Math.min(TextGrid.COLS - 6, Math.max(super.bodyWidth(), labelWidth + 2 + FIELD_MIN_WIDTH));
	}

	@Override
	protected void renderBody(TextGrid g, int col, int row, int width, long now) {
		fieldsCol = col;
		fieldsRow = row + 1;
		int inputCol = col + labelWidth + 2, inputWidth = Math.max(4, width - labelWidth - 2);
		for(int i = 0; i < fields.size(); i++) {
			Field field = fields.get(i);
			int r = fieldsRow + i;
			g.put(col, r, field.label(), i == focus ? DosColours.WHITE : DosColours.DIALOG_FG, i == focus ? DosColours.BLUE : DosColours.DIALOG_BG);
			field.input().render(g, inputCol, r, inputWidth, i == focus);
		}
		if(!problem.isEmpty())
			g.put(col, fieldsRow + fields.size(), Dos.fit(problem, width), DosColours.RED, DosColours.DIALOG_BG);
	}

	@Override
	public void keyPressed(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_UP -> focus(focus - 1);
			case GLFW.GLFW_KEY_DOWN -> focus(focus + 1);
			case GLFW.GLFW_KEY_TAB -> focus((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? (focus + fields.size() - 1) % fields.size() : (focus + 1) % fields.size());
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> press(0);
			default -> {
				if(!fields.isEmpty())
					fields.get(focus).input().keyPressed(key);
			}
		}
	}

	@Override
	public void charTyped(char c) {
		if(!fields.isEmpty())
			fields.get(focus).input().charTyped(c);
	}

	@Override
	public void mouseClicked(int col, int row, int mouseButton) {
		if(fieldsRow >= 0 && row >= fieldsRow && row < fieldsRow + fields.size() && col >= fieldsCol) {
			focus(row - fieldsRow);
			return;
		}
		super.mouseClicked(col, row, mouseButton);
	}
}
