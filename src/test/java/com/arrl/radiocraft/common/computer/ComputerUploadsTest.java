package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A file written from the screen goes up in parts and is only whole once the last part is in (1.12 step 5). */
class ComputerUploadsTest {

	private static byte[] data(int length) {
		byte[] out = new byte[length];
		for(int i = 0; i < length; i++)
			out[i] = (byte)(i * 7);
		return out;
	}

	@Test
	void partsFitAPacket() {
		assertTrue(ComputerUploads.PART_BYTES + 1024 < 32767, "a part and its fields must fit a client's custom payload");
		assertEquals(1, ComputerUploads.parts(0));
		assertEquals(1, ComputerUploads.parts(ComputerUploads.PART_BYTES));
		assertEquals(2, ComputerUploads.parts(ComputerUploads.PART_BYTES + 1));
		assertEquals(ComputerUploads.MAX_PARTS, ComputerUploads.parts(ComputerStorage.MAX_FILE_BYTES));
	}

	@Test
	void partsInOrderMakeTheFile() {
		byte[] file = data(ComputerUploads.PART_BYTES * 2 + 100);
		int parts = ComputerUploads.parts(file.length);
		ComputerUploads.Assembler<String> uploads = new ComputerUploads.Assembler<>();
		ComputerUploads.Result result = null;
		for(int i = 0; i < parts; i++) {
			result = uploads.accept("player", "A.DAT", i, parts, ComputerUploads.part(file, i), 5L);
			assertEquals(i + 1 < parts ? ComputerUploads.Status.PARTIAL : ComputerUploads.Status.DONE, result.status());
		}
		assertArrayEquals(file, result.data());
		assertEquals(0, uploads.size(), "a finished upload is forgotten");
	}

	@Test
	void anEmptyFileIsOnePart() {
		ComputerUploads.Assembler<String> uploads = new ComputerUploads.Assembler<>();
		ComputerUploads.Result result = uploads.accept("p", "E.TXT", 0, 1, ComputerUploads.part(new byte[0], 0), 0L);
		assertEquals(ComputerUploads.Status.DONE, result.status());
		assertEquals(0, result.data().length);
	}

	@Test
	void outOfOrderOrMixedUpPartsAreRefused() {
		byte[] file = data(ComputerUploads.PART_BYTES * 3);
		ComputerUploads.Assembler<String> uploads = new ComputerUploads.Assembler<>();
		assertEquals(ComputerUploads.Status.BAD, uploads.accept("p", "A.DAT", 1, 3, ComputerUploads.part(file, 1), 0L).status(), "a part before the first");
		uploads.accept("p", "A.DAT", 0, 3, ComputerUploads.part(file, 0), 0L);
		assertEquals(ComputerUploads.Status.BAD, uploads.accept("p", "B.DAT", 1, 3, ComputerUploads.part(file, 1), 0L).status(), "another file's part");
		uploads.accept("p", "A.DAT", 0, 3, ComputerUploads.part(file, 0), 0L);
		assertEquals(ComputerUploads.Status.BAD, uploads.accept("p", "A.DAT", 2, 3, ComputerUploads.part(file, 2), 0L).status(), "a part skipped");
		assertEquals(ComputerUploads.Status.BAD, uploads.accept("p", "A.DAT", 0, ComputerUploads.MAX_PARTS + 1, new byte[1], 0L).status(), "too many parts");
		assertEquals(ComputerUploads.Status.BAD, uploads.accept("p", "A.DAT", 0, 1, new byte[ComputerUploads.PART_BYTES + 1], 0L).status(), "a part too big");
	}

	@Test
	void playersUploadSeparately() {
		byte[] a = data(ComputerUploads.PART_BYTES + 5), b = data(ComputerUploads.PART_BYTES + 9);
		ComputerUploads.Assembler<String> uploads = new ComputerUploads.Assembler<>();
		uploads.accept("one", "A.DAT", 0, 2, ComputerUploads.part(a, 0), 0L);
		uploads.accept("two", "B.DAT", 0, 2, ComputerUploads.part(b, 0), 0L);
		assertArrayEquals(a, uploads.accept("one", "A.DAT", 1, 2, ComputerUploads.part(a, 1), 0L).data());
		assertArrayEquals(b, uploads.accept("two", "B.DAT", 1, 2, ComputerUploads.part(b, 1), 0L).data());
	}

	@Test
	void slowUploadsTimeOut() {
		byte[] file = data(ComputerUploads.PART_BYTES * 2);
		ComputerUploads.Assembler<String> uploads = new ComputerUploads.Assembler<>();
		uploads.accept("p", "A.DAT", 0, 2, ComputerUploads.part(file, 0), 0L);
		assertEquals(ComputerUploads.Status.BAD, uploads.accept("p", "A.DAT", 1, 2, ComputerUploads.part(file, 1), ComputerUploads.TIMEOUT_TICKS + 1).status());
		uploads.accept("q", "A.DAT", 0, 2, ComputerUploads.part(file, 0), 0L);
		uploads.expire(ComputerUploads.TIMEOUT_TICKS + 1);
		assertEquals(0, uploads.size());
		uploads.accept("r", "A.DAT", 0, 2, ComputerUploads.part(file, 0), 0L);
		uploads.cancel("r");
		assertEquals(0, uploads.size());
	}
}
