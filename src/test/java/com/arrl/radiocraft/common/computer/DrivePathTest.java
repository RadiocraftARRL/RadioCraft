package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RC-DOS paths with a drive (computer batch 3A): A: is the floppy, C: the computer's disk. */
class DrivePathTest {

	@Test
	void aDriveLetterNamesTheDrive() {
		assertEquals(new DrivePath('A', "NOTES.TXT"), DrivePath.parse("A:NOTES.TXT", 'C'));
		assertEquals(new DrivePath('A', "NOTES.TXT"), DrivePath.parse("a:\\notes.txt", 'C'));
		assertEquals(new DrivePath('C', "LOG.LOG"), DrivePath.parse("C:\\LOG.LOG", 'A'));
		assertEquals(new DrivePath('C', "X.TXT"), DrivePath.parse("  c:/x.txt ", 'A'));
	}

	@Test
	void noLetterIsTheCurrentDrive() {
		assertEquals(new DrivePath('C', "NOTES.TXT"), DrivePath.parse("notes.txt", 'C'));
		assertEquals(new DrivePath('A', "NOTES.TXT"), DrivePath.parse("NOTES.TXT", 'a'));
		assertEquals(new DrivePath('A', "*.LOG"), DrivePath.parse("*.log", 'A'));
	}

	@Test
	void aDriveAloneHasNoName() {
		DrivePath a = DrivePath.parse("A:", 'C');
		assertEquals('A', a.drive());
		assertTrue(a.isDriveOnly());
		assertTrue(a.onFloppy());
		assertTrue(DrivePath.parse("a:\\", 'C').isDriveOnly());
		assertFalse(DrivePath.parse("C:", 'A').onFloppy());
	}

	@Test
	void otherDrivesAreNotThere() {
		assertNull(DrivePath.parse("B:X.TXT", 'C'));
		assertNull(DrivePath.parse("D:", 'C'));
		assertNull(DrivePath.parse("", 'C'));
		assertNull(DrivePath.parse("   ", 'C'));
		assertNull(DrivePath.parse(null, 'C'));
	}

	@Test
	void requestFieldsAndPrinting() {
		assertEquals('A', DrivePath.driveOf("A"));
		assertEquals('A', DrivePath.driveOf(" a "));
		assertEquals('C', DrivePath.driveOf(""));
		assertEquals('C', DrivePath.driveOf(null));
		assertEquals('C', DrivePath.driveOf("B"));
		assertEquals("A:NOTES.TXT", new DrivePath('a', "notes.txt").toString());
		assertEquals("C:", new DrivePath('C', "").toString());
	}
}
