package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;

/**
 * SAVER.EXE (computer batch 3A): run by hand it blanks the screen at once, until the next key or click, and goes back
 * to where it was run from. The screensaver itself is the session's ({@code ComputerSession}, drawn by
 * {@code Screensaver}); SETUP's Screensaver page chooses the wait and the style.
 */
public final class SaverApp extends DosApp {

	public SaverApp(ComputerSession session, ComputerApp app) {
		super(session, app);
	}

	@Override
	public void opened() {
		session.blankNow();
		exit();
	}

	@Override
	public void render(TextGrid g, long now) {
		g.clear(0x000000, 0x000000); // Never seen: it exits as it opens.
	}
}
