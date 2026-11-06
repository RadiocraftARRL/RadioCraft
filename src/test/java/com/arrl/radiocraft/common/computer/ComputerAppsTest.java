package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The radio computer's program registry (1.12 step 5): seven built in, the rest on floppies, names that fit DOS. */
class ComputerAppsTest {

	@Test
	void sevenBuiltInProgramsWithNoFloppy() {
		List<ComputerApp> builtIn = ComputerApps.builtIn();
		assertEquals(List.of(ComputerApps.EDITOR, ComputerApps.SYSINFO, ComputerApps.BAND_PLAN, ComputerApps.INSTALL,
				ComputerApps.SETUP, ComputerApps.ABOUT, ComputerApps.LICENSE), builtIn);
		for(ComputerApp app : builtIn) {
			assertFalse(app.hasFloppy());
			assertNull(app.floppyItemId());
		}
	}

	@Test
	void everyOtherProgramHasItsOwnFloppy() {
		Set<String> items = new HashSet<>(), commands = new HashSet<>();
		for(ComputerApp app : ComputerApps.all()) {
			assertTrue(commands.add(app.command()), "two programs run as " + app.command());
			if(app.hasFloppy())
				assertTrue(items.add(app.floppyItemId()), "two programs on " + app.floppyItemId());
		}
		assertEquals(ComputerApps.all().size() - 7, ComputerApps.onFloppies().size());
		assertEquals("floppy_logbook", ComputerApps.LOGBOOK.floppyItemId());
		assertEquals(ComputerApp.Kind.SECRET, ComputerApps.SNAKE.kind());
		assertEquals("CPS.EXE", ComputerApps.PROGRAMMER.fileName());
		assertEquals("screen.radiocraft.computer.app.logbook", ComputerApps.LOGBOOK.nameKey());
	}

	@Test
	void availableIsBuiltInPlusInstalledInShellOrder() {
		List<ComputerApp> apps = ComputerApps.available(List.of("sstv", "logbook", "edit", "nonsense"));
		assertEquals(List.of(ComputerApps.EDITOR, ComputerApps.SYSINFO, ComputerApps.BAND_PLAN, ComputerApps.INSTALL,
				ComputerApps.SETUP, ComputerApps.ABOUT, ComputerApps.LICENSE, ComputerApps.LOGBOOK, ComputerApps.SSTV), apps);
		assertNull(ComputerApps.get("nonsense"));
		assertNull(ComputerApps.get(null));
	}

	@Test
	void badIdsAndClashesAreRefused() {
		assertThrows(IllegalArgumentException.class, () -> new ComputerApp("Bad Id", "BAD", ComputerApp.Kind.CRAFTED));
		assertThrows(IllegalArgumentException.class, () -> new ComputerApp("ok", "TOOLONGNAME", ComputerApp.Kind.CRAFTED));
		assertThrows(IllegalArgumentException.class, () -> new ComputerApp("ok", "lower", ComputerApp.Kind.CRAFTED));
		assertThrows(IllegalArgumentException.class, () -> ComputerApps.add("edit", "EDIT2", ComputerApp.Kind.CRAFTED));
		assertThrows(IllegalArgumentException.class, () -> ComputerApps.add("edit_two", "EDIT", ComputerApp.Kind.CRAFTED));
	}
}
