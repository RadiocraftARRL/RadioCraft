package com.arrl.radiocraft.client.screens.computer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The RC-DOS prompt's parsing, wildcards, program names, Tab completion and history. */
class DosCommandsTest {

	@Test
	void linesSplitIntoACommandAndArguments() {
		DosCommands.Line line = DosCommands.parse("  copy notes.txt  \"backup.txt\" /y ");
		assertEquals("COPY", line.command());
		assertEquals(List.of("notes.txt", "backup.txt", "/y"), line.args());
		assertTrue(line.has("/Y"));
		assertEquals(List.of("notes.txt", "backup.txt"), line.names());
		assertEquals("", DosCommands.parse("   ").command());
		DosCommands.Line wide = DosCommands.parse("dir/w");
		assertEquals("DIR", wide.command());
		assertTrue(wide.has("/W"));
		assertEquals("", wide.arg(3));
	}

	@Test
	void wildcardsMatchAsInDos() {
		assertTrue(DosCommands.matches("*.TXT", "notes.txt"));
		assertFalse(DosCommands.matches("*.TXT", "NOTES.LOG"));
		assertTrue(DosCommands.matches("LOG?.LOG", "LOG1.LOG"));
		assertFalse(DosCommands.matches("LOG?.LOG", "LOG12.LOG"));
		assertTrue(DosCommands.matches("*.*", "ANYTHING.DAT"));
		assertTrue(DosCommands.matches("SSTV*", "SSTV0001.IMG"));
		assertTrue(DosCommands.matches("NOTES.TXT", "notes.txt"));
		assertFalse(DosCommands.matches("NOTES", "NOTES.TXT"), "no extension typed: not the file with one");
		assertTrue(DosCommands.isWildcard("A*"));
		assertFalse(DosCommands.isWildcard("A.TXT"));
	}

	@Test
	void programsRunByNameWithOrWithoutExe() {
		List<String> commands = List.of("EDIT", "RTTY", "SETUP");
		assertEquals("RTTY", DosCommands.program("rtty", commands));
		assertEquals("RTTY", DosCommands.program("RTTY.EXE", commands));
		assertNull(DosCommands.program("RTTY.TXT", commands));
		assertNull(DosCommands.program("NOPE", commands));
	}

	@Test
	void tabCompletesTheLastWord() {
		List<String> names = List.of("LOGBOOK.LOG", "LOGBOOK.ADI", "NOTES.TXT", "RTTY");
		assertEquals("TYPE NOTES.TXT", DosCommands.complete("TYPE no", names));
		assertEquals("DEL LOGBOOK.", DosCommands.complete("DEL log", names), "as far as they agree");
		assertEquals("rt", DosCommands.complete("rt", List.of()), "nothing to complete");
		assertEquals("RTTY", DosCommands.complete("rt", names));
	}

	@Test
	void historyStepsBackAndForth() {
		DosCommands.History history = new DosCommands.History(3);
		history.add("DIR");
		history.add("DIR");
		history.add("");
		history.add("TYPE A.TXT");
		assertEquals(List.of("DIR", "TYPE A.TXT"), history.lines(), "no repeats, no empty lines");
		assertEquals("TYPE A.TXT", history.up());
		assertEquals("DIR", history.up());
		assertNull(history.up(), "the oldest");
		assertEquals("TYPE A.TXT", history.down());
		assertEquals("", history.down(), "back to a fresh line");
		assertNull(history.down());
		history.add("CLS");
		history.add("VER");
		history.add("HELP");
		assertEquals(3, history.lines().size(), "kept to its size");
	}
}
