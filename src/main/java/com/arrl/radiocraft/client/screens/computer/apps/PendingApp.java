package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;

/**
 * What a program shows when it is installed but its screen is not written yet in this version of the mod: its name
 * and a note, in a box. Escape goes back.
 */
public final class PendingApp extends DosApp {

	public PendingApp(ComputerSession session, ComputerApp app) {
		super(session, app);
	}

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, app.fileName(), Dos.tr("keys.back"));
		int width = 56, col = (TextGrid.COLS - width) / 2;
		g.box(col, 7, width, 9, true, Dos.name(app), DosColours.DIALOG_FG, DosColours.DIALOG_BG);
		g.shadow(col, 7, width, 9);
		g.put(col + 3, 9, Dos.fit(Dos.description(app), width - 6), DosColours.DIALOG_FG, DosColours.DIALOG_BG);
		g.put(col + 3, 11, Dos.fit(Dos.tr("pending.line1"), width - 6), DosColours.RED, DosColours.DIALOG_BG);
		g.put(col + 3, 12, Dos.fit(Dos.tr("pending.line2"), width - 6), DosColours.DIALOG_FG, DosColours.DIALOG_BG);
	}
}
