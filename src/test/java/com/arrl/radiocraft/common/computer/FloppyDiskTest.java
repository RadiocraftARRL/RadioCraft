package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A data floppy's rules (computer batch 3A): 1.44 MB, a file count cap, data files only, an 11-letter label. */
class FloppyDiskTest {

	@Test
	void itHoldsAFormattedHighDensityDisk() {
		assertEquals(1_457_664L, FloppyDisk.CAPACITY);
		ComputerStorage disk = new ComputerStorage();
		byte[] big = new byte[ComputerStorage.MAX_FILE_BYTES];
		for(int i = 0; i < 5; i++)
			assertNull(FloppyDisk.write(disk, "BIG" + i + ".DAT", big, 0L), "file " + i);
		assertEquals(5L * ComputerStorage.MAX_FILE_BYTES, disk.used());
		// 5 x 256 KB = 1,310,720; a sixth would pass 1,457,664.
		assertEquals(ComputerStorage.Problem.DISK_FULL, FloppyDisk.write(disk, "BIG5.DAT", big, 0L));
		assertNull(FloppyDisk.write(disk, "REST.DAT", new byte[(int)(FloppyDisk.CAPACITY - disk.used())], 0L), "the last byte fits");
		assertEquals(FloppyDisk.CAPACITY, disk.used());
		assertEquals(ComputerStorage.Problem.DISK_FULL, FloppyDisk.write(disk, "ONE.TXT", new byte[1], 0L));
		// Replacing a file counts only the difference.
		assertNull(FloppyDisk.write(disk, "REST.DAT", new byte[10], 0L));
	}

	@Test
	void itHoldsAtMostSoManyFiles() {
		ComputerStorage disk = new ComputerStorage();
		for(int i = 0; i < FloppyDisk.MAX_FILES; i++)
			assertNull(FloppyDisk.write(disk, "F" + i + ".TXT", new byte[] { 1 }, 0L));
		assertEquals(ComputerStorage.Problem.TOO_MANY_FILES, FloppyDisk.write(disk, "ONEMORE.TXT", new byte[] { 1 }, 0L));
		assertNull(FloppyDisk.write(disk, "F0.TXT", new byte[] { 2 }, 0L), "an old one can still be written");
	}

	@Test
	void programsNeverGoOnIt() {
		ComputerStorage disk = new ComputerStorage();
		for(String name : new String[] { "LOGBOOK.EXE", "x.com", "AUTOEXEC.BAT", "IO.SYS", "ROM.BIN" }) {
			assertTrue(FloppyDisk.isProgramName(name), name);
			assertEquals(ComputerStorage.Problem.PROGRAM, FloppyDisk.write(disk, name, new byte[] { 1 }, 0L), name);
		}
		assertEquals(0, disk.count());
		for(String name : new String[] { "NOTES.TXT", "LOG.LOG", "PIC.IMG", "CH.FRQ", "RADIO.CPG", "EXE.TXT", "ADIF.ADI" })
			assertFalse(FloppyDisk.isProgramName(name), name);
		assertFalse(FloppyDisk.isProgramName(null));
	}

	@Test
	void labelsAreElevenDosCharacters() {
		assertEquals("FIELD DAY", FloppyDisk.cleanLabel("field day"));
		assertEquals("ABCDEFGHIJK", FloppyDisk.cleanLabel("abcdefghijklmnop"));
		assertEquals("W1BTRLOGS", FloppyDisk.cleanLabel("  W1BTR/LOGS "));
		assertEquals("W1BTR-LOGS", FloppyDisk.cleanLabel("w1btr-logs"));
		assertEquals("", FloppyDisk.cleanLabel(null));
		assertEquals("", FloppyDisk.cleanLabel("   "));
		assertEquals("CQ", FloppyDisk.cleanLabel("\u00e9CQ*"));
	}

	@Test
	void sizesReadAsATooltip() {
		assertEquals("12 bytes", FloppyDisk.size(12L));
		assertEquals("1 KB", FloppyDisk.size(1024L));
		assertEquals("2 KB", FloppyDisk.size(1025L));
		assertEquals("1.42 MB", FloppyDisk.size(FloppyDisk.CAPACITY));
	}
}
