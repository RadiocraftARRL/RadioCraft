package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The radio computer's disk (1.12 step 5): DOS names, the caps, and what each change does to the space used. */
class ComputerStorageTest {

	private static final long CAPACITY = 1024L;
	private static final int MAX_FILES = 3;

	@Test
	void namesAreDosEightDotThree() {
		assertEquals("LOGBOOK.TXT", ComputerStorage.normalise(" logbook.txt "));
		assertEquals("A-B_1.IMG", ComputerStorage.normalise("a-b_1.img"));
		assertNull(ComputerStorage.normalise("TOOLONGNAME.TXT"));
		assertNull(ComputerStorage.normalise("NOEXT"));
		assertNull(ComputerStorage.normalise(".TXT"));
		assertNull(ComputerStorage.normalise("A.B.C"));
		assertNull(ComputerStorage.normalise("A.TEXT"));
		assertNull(ComputerStorage.normalise("SP ACE.TXT"));
		assertNull(ComputerStorage.normalise("../X.TXT"));
		assertNull(ComputerStorage.normalise(null));
		assertEquals("TXT", ComputerStorage.extension("NOTES.txt"));
		assertEquals("", ComputerStorage.extension("NOTES"));
	}

	@Test
	void suggestedNamesAreAlwaysValid() {
		assertEquals("MYNOTES.TXT", ComputerStorage.suggest("my notes!", "txt"));
		assertEquals("ABCDEFGH.IMG", ComputerStorage.suggest("abcdefghijk.png", "IMG"));
		assertEquals("UNTITLED.TXT", ComputerStorage.suggest("", "TXT"));
		assertEquals("UNTITLED.TXT", ComputerStorage.suggest(null, "TXT"));
		assertTrue(ComputerStorage.isValidName(ComputerStorage.suggest("??..//", "LOG")));
	}

	@Test
	void writeReadReplaceDelete() {
		ComputerStorage disk = new ComputerStorage();
		assertNull(disk.write("a.txt", new byte[] { 1, 2, 3 }, 10L, CAPACITY, MAX_FILES));
		assertArrayEquals(new byte[] { 1, 2, 3 }, disk.read("A.TXT"));
		assertEquals(3L, disk.used());
		assertNull(disk.write("A.TXT", new byte[10], 11L, CAPACITY, MAX_FILES)); // Replacing counts the new size only.
		assertEquals(10L, disk.used());
		assertEquals(1, disk.count());
		assertEquals(11L, disk.entry("a.txt").modified());
		assertNull(disk.delete("a.txt"));
		assertEquals(0L, disk.used());
		assertEquals(ComputerStorage.Problem.NOT_FOUND, disk.delete("a.txt"));
		assertNull(disk.read("a.txt"));
	}

	@Test
	void readGivesACopy() {
		ComputerStorage disk = new ComputerStorage();
		byte[] data = { 5, 6 };
		disk.write("X.DAT", data, 0L, CAPACITY, MAX_FILES);
		data[0] = 9; // Changing the array written does not change the file...
		byte[] read = disk.read("X.DAT");
		read[1] = 9; // ...nor does changing what was read.
		assertArrayEquals(new byte[] { 5, 6 }, disk.read("X.DAT"));
	}

	@Test
	void capsAreKept() {
		ComputerStorage disk = new ComputerStorage();
		assertEquals(ComputerStorage.Problem.BAD_NAME, disk.write("no name", new byte[1], 0L, CAPACITY, MAX_FILES));
		assertEquals(ComputerStorage.Problem.TOO_BIG, disk.write("BIG.DAT", new byte[ComputerStorage.MAX_FILE_BYTES + 1], 0L, Long.MAX_VALUE, MAX_FILES));
		assertNull(disk.write("A.DAT", new byte[600], 0L, CAPACITY, MAX_FILES));
		assertEquals(ComputerStorage.Problem.DISK_FULL, disk.write("B.DAT", new byte[500], 0L, CAPACITY, MAX_FILES));
		assertNull(disk.write("A.DAT", new byte[1024], 0L, CAPACITY, MAX_FILES), "replacing may fill the disk exactly");
		assertNull(disk.write("A.DAT", new byte[1], 0L, CAPACITY, MAX_FILES));
		assertNull(disk.write("B.DAT", new byte[1], 0L, CAPACITY, MAX_FILES));
		assertNull(disk.write("C.DAT", new byte[1], 0L, CAPACITY, MAX_FILES));
		assertEquals(ComputerStorage.Problem.TOO_MANY_FILES, disk.write("D.DAT", new byte[1], 0L, CAPACITY, MAX_FILES));
		assertNull(disk.write("C.DAT", new byte[2], 0L, CAPACITY, MAX_FILES), "replacing one is fine when the directory is full");
	}

	@Test
	void renameKeepsTheContentsAndRefusesToOverwrite() {
		ComputerStorage disk = new ComputerStorage();
		disk.write("A.TXT", new byte[] { 1 }, 0L, CAPACITY, MAX_FILES);
		disk.write("B.TXT", new byte[] { 2 }, 0L, CAPACITY, MAX_FILES);
		assertEquals(ComputerStorage.Problem.EXISTS, disk.rename("A.TXT", "b.txt"));
		assertNull(disk.rename("a.txt", "c.txt"));
		assertFalse(disk.exists("A.TXT"));
		assertArrayEquals(new byte[] { 1 }, disk.read("C.TXT"));
		assertEquals(2L, disk.used());
		assertEquals(ComputerStorage.Problem.NOT_FOUND, disk.rename("Z.TXT", "Y.TXT"));
	}

	@Test
	void listsByExtensionInNameOrder() {
		ComputerStorage disk = new ComputerStorage();
		disk.write("B.TXT", new byte[0], 0L, CAPACITY, 10);
		disk.write("A.TXT", new byte[0], 0L, CAPACITY, 10);
		disk.write("PIC.IMG", new byte[0], 0L, CAPACITY, 10);
		List<ComputerStorage.FileEntry> text = disk.list("txt");
		assertEquals(2, text.size());
		assertEquals("A.TXT", text.get(0).name());
		assertEquals(3, disk.list("").size());
		assertEquals(3, disk.list(null).size());
	}

	@Test
	void restoreIgnoresTheCapsButCountsTheSpace() {
		ComputerStorage disk = new ComputerStorage();
		disk.restore("BIG.DAT", new byte[5000], 1L);
		disk.restore("bad name", new byte[5], 1L);
		assertEquals(5000L, disk.used());
		assertEquals(1, disk.count());
		assertEquals(ComputerStorage.Problem.DISK_FULL, disk.write("X.DAT", new byte[1], 0L, CAPACITY, MAX_FILES), "over capacity after a cap was lowered");
	}

	@Test
	void textRoundTripsWithUnixNewlines() {
		assertEquals("a\nb\nc", ComputerStorage.text(ComputerStorage.text("a\r\nb\rc")));
		assertEquals("73 de W1BTR é", ComputerStorage.text(ComputerStorage.text("73 de W1BTR é")));
	}

	/** Programs name what they receive SSTV0001.IMG, FAX00001.TXT...: the first number free (1.12 step 7). */
	@Test
	void nextNameIsTheFirstFreeNumber() {
		java.util.Set<String> taken = new java.util.HashSet<>(List.of("SSTV0001.IMG", "SSTV0002.IMG", "SSTV0004.IMG"));
		assertEquals("SSTV0003.IMG", ComputerStorage.nextName("SSTV", "img", taken::contains));
		assertEquals("FAX00001.TXT", ComputerStorage.nextName("FAX", "TXT", taken::contains));
		assertTrue(ComputerStorage.isValidName(ComputerStorage.nextName("WFX", "IMG", name -> false)));
		assertNull(ComputerStorage.nextName("ABCDEFG", "IMG", name -> true), "every number taken");
		assertEquals("ABCDEFG1.IMG", ComputerStorage.nextName("ABCDEFG", "IMG", name -> false));
	}
}
