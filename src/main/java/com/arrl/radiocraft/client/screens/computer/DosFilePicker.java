package com.arrl.radiocraft.client.screens.computer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The "Open file" dialog: the computer's files with one extension (or all), name and size, from the server. Enter or
 * Open picks the selected one for {@code chosen}. Any program can use it:
 * {@code showDialog(new DosFilePicker(session, Dos.tr("open"), "LOG", name -> load(name)))}.
 */
public final class DosFilePicker extends DosDialog {

	private static final int ROWS = 12, WIDTH = 40;

	private final DosList list = new DosList();
	private final List<String> names = new ArrayList<>();
	private final Consumer<String> chosen;
	private boolean loaded = false;
	private String problem = "";

	public DosFilePicker(ComputerSession session, String title, String extension, Consumer<String> chosen) {
		super(title, extension.isEmpty() ? Dos.tr("files.all") : Dos.tr("files.of", extension), null,
				new String[] { Dos.tr("button.open"), Dos.tr("button.cancel") }, index -> {}, () -> {});
		this.chosen = chosen;
		list.onActivate(index -> press(0));
		session.files().list(extension, response -> {
			loaded = true;
			if(!response.ok()) {
				problem = response.errorText();
				return;
			}
			List<String> lines = new ArrayList<>();
			for(ComputerFiles.Info file : ComputerFiles.infos(response))
				if(names.add(file.name()))
					lines.add(String.format("%-12s %10s", file.name(), Dos.size(file.size())));
			list.setItems(lines);
		});
	}

	@Override
	protected void press(int index) {
		if(index == 0 && list.selected() < 0)
			return; // Nothing to open.
		close();
		if(index == 0)
			chosen.accept(names.get(list.selected()));
	}

	@Override
	protected int bodyRows() {
		return super.bodyRows() + ROWS + 1;
	}

	@Override
	protected int bodyWidth() {
		return Math.max(super.bodyWidth(), WIDTH);
	}

	@Override
	protected void renderBody(TextGrid g, int col, int row, int width, long now) {
		g.box(col, row, width, ROWS + 1, false, "", DosColours.DIALOG_FG, DosColours.DIALOG_BG);
		if(!loaded)
			g.put(col + 2, row + 1, Dos.tr("files.reading"), DosColours.DIALOG_FG, DosColours.DIALOG_BG);
		else if(!problem.isEmpty())
			g.put(col + 2, row + 1, Dos.fit(problem, width - 4), DosColours.RED, DosColours.DIALOG_BG);
		else if(list.isEmpty())
			g.put(col + 2, row + 1, Dos.tr("files.none"), DosColours.DIALOG_FG, DosColours.DIALOG_BG);
		else
			list.render(g, col + 1, row + 1, width - 2, ROWS - 1, DosColours.BLACK, DosColours.DIALOG_BG, true);
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

	/** The names of {@code ListTag} entries (for programs reading an {@code fs.list} answer themselves). */
	static List<String> namesOf(CompoundTag data) {
		List<String> out = new ArrayList<>();
		ListTag files = data.getList("files", Tag.TAG_COMPOUND);
		for(int i = 0; i < files.size(); i++)
			out.add(files.getCompound(i).getString("name"));
		return out;
	}
}
