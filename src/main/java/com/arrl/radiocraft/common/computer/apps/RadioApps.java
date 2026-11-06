package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.computer.ComputerAppHandlers;
import com.arrl.radiocraft.common.computer.ComputerApps;

/**
 * The server sides of the radio computer's radio programs (1.12 step 7): APRS, WEFAX, the radio fax, SSTV and direction
 * finding, registered at common setup ({@code Radiocraft}'s constructor adds the listener). All but direction finding
 * go on the air through the radio interfaces on the computer's station bus ({@link RadioStation}).
 */
public final class RadioApps {

	private RadioApps() {}

	public static void register() {
		ComputerAppHandlers.register(ComputerApps.APRS, new AprsHandler());
		ComputerAppHandlers.register(ComputerApps.WEFAX, new WefaxHandler());
		ComputerAppHandlers.register(ComputerApps.RADIO_FAX, new FaxHandler());
		ComputerAppHandlers.register(ComputerApps.SSTV, new SstvHandler());
		ComputerAppHandlers.register(ComputerApps.DIRECTION_FINDING, new DfHandler());
		ComputerAppHandlers.register(ComputerApps.ADSB, new AdsbHandler()); // 1.12 step 9: reads an ADS-B receiver.
		// Computer apps 2 (2026-11-06).
		ComputerAppHandlers.register(ComputerApps.RTTY, new RttyHandler());
		ComputerAppHandlers.register(ComputerApps.BBS, new BbsHandler());
		ComputerAppHandlers.register(ComputerApps.PROPAGATION, new PropagationHandler());
		ComputerAppHandlers.register(ComputerApps.REPEATERS, new RepeaterDirectoryHandler());
	}
}
