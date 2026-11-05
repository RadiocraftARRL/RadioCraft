package com.arrl.radiocraft.client.screens.computer;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Pick one line from a list in a DOS dialog box (1.12 step 6): the radio to program, the station heard to log. Enter,
 * a second click or "Choose" hands its index to {@code chosen}; Escape or Cancel closes it without. Like
 * {@link DosFilePicker}, but for any lines a program has already.
 */
public final class DosPickDialog extends DosDialog {

	private static final int MIN_WIDTH = 40;

	private final DosList list = new DosList();
	private final IntConsumer chosen;
	private final int rows;
	private final int listWidth;

	public DosPickDialog(String title, String message, List<String> lines, IntConsumer chosen) {
		super(title, message, null, new String[] { Dos.tr("button.choose"), Dos.tr("button.cancel") }, index -> {}, () -> {});
		this.chosen = chosen;
		this.rows = Math.max(3, Math.min(12, lines.size()));
		int longest = MIN_WIDTH;
		for(String line : lines)
			longest = Math.max(longest, line.length() + 3);
		this.listWidth = Math.min(TextGrid.COLS - 6, longest);
		list.setItems(lines);
		list.onActivate(index -> press(0));
	}

	@Override
	protected void press(int index) {
		if(index == 0 && list.selected() < 0)
			return;
		close();
		if(index == 0)
			chosen.accept(list.selected());
	}

	@Override
	protected int bodyRows() {
		return super.bodyRows() + rows + 2;
	}

	@Override
	protected int bodyWidth() {
		return Math.max(super.bodyWidth(), listWidth);
	}

	@Override
	protected void renderBody(TextGrid g, int col, int row, int width, long now) {
		g.box(col, row, width, rows + 2, false, "", DosColours.DIALOG_FG, DosColours.DIALOG_BG);
		if(list.isEmpty())
			g.put(col + 2, row + 1, Dos.tr("pick.none"), DosColours.DIALOG_FG, DosColours.DIALOG_BG);
		else
			list.render(g, col + 1, row + 1, width - 2, rows, DosColours.BLACK, DosColours.DIALOG_BG, true);
	}

	@Override
	public void keyPressed(int key, int scanCode, int modifiers) {
		if(!list.keyPressed(key))
			super.keyPressed(key, scanCode, modifiers);
	}

	@Override
	public void mouseClicked(int col, int row, int mouseButton) {
		if(!list.mouseClicked(col, row))
			super.mouseClicked(col, row, mouseButton);
	}

	@Override
	public void mouseScrolled(int col, int row, double amount) {
		list.mouseScrolled(col, row, amount);
	}
}
