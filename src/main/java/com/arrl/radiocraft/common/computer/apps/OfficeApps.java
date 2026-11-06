package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.computer.ComputerAppHandlers;
import com.arrl.radiocraft.common.computer.ComputerApps;

/**
 * The server sides of the radio computer's office programs (1.12 step 6), registered at common setup
 * ({@code Radiocraft}'s constructor adds the listener). The frequency manager's, logbook's and book editor's screens
 * work alone for everything but the parts that reach the world (radios, the scanner, the sky).
 */
public final class OfficeApps {

	private OfficeApps() {}

	public static void register() {
		ComputerAppHandlers.register(ComputerApps.BOOK_EDITOR, new BookEditorHandler());
		ComputerAppHandlers.register(ComputerApps.LOGBOOK, new LogbookHandler());
		ComputerAppHandlers.register(ComputerApps.FREQUENCY_MANAGER, new FrequencyManagerHandler());
		ComputerAppHandlers.register(ComputerApps.PROGRAMMER, new ProgrammerHandler());
		ComputerAppHandlers.register(ComputerApps.SOLAR_WEATHER, new SolarWeatherHandler());
		ComputerAppHandlers.register(ComputerApps.LICENSE, new LicenseStudyHandler()); // Computer apps 2 (2026-11-06).
	}
}
