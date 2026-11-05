package com.arrl.radiocraft.common.photo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A photo coming up from a client in chunks: only the asked-for one, each chunk once, in time (camera, 1.12 step 4). */
class PhotoUploadTest {

	private static byte[] picture() {
		byte[] pixels = new byte[PhotoImage.PIXELS];
		for(int i = 0; i < pixels.length; i++)
			pixels[i] = (byte)(i * 31);
		return pixels;
	}

	@Test
	void chunksAddUpToAMapPicture() {
		assertEquals(PhotoImage.PIXELS, PhotoUpload.CHUNKS * PhotoUpload.CHUNK_BYTES);
		assertTrue(PhotoUpload.CHUNK_BYTES < 32767, "a chunk must fit a custom payload");
	}

	@Test
	void chunksInAnyOrderMakeThePicture() {
		byte[] pixels = picture();
		PhotoUpload upload = new PhotoUpload(42, true, 100);
		int[] order = { 2, 0, 3, 1 };
		for(int i = 0; i < order.length; i++) {
			PhotoUpload.Result result = upload.accept(42, order[i], PhotoUpload.chunk(pixels, order[i]), 110);
			assertEquals(i == order.length - 1 ? PhotoUpload.Result.COMPLETE : PhotoUpload.Result.PARTIAL, result);
		}
		assertTrue(upload.isComplete());
		assertArrayEquals(pixels, upload.pixels());
		assertTrue(upload.colour());
	}

	@Test
	void badChunksAreRejected() {
		byte[] pixels = picture();
		PhotoUpload upload = new PhotoUpload(42, false, 100);
		assertEquals(PhotoUpload.Result.REJECTED, upload.accept(41, 0, PhotoUpload.chunk(pixels, 0), 101), "wrong nonce");
		assertEquals(PhotoUpload.Result.REJECTED, upload.accept(42, -1, PhotoUpload.chunk(pixels, 0), 101), "negative index");
		assertEquals(PhotoUpload.Result.REJECTED, upload.accept(42, PhotoUpload.CHUNKS, PhotoUpload.chunk(pixels, 0), 101), "index past the end");
		assertEquals(PhotoUpload.Result.REJECTED, upload.accept(42, 0, new byte[10], 101), "short chunk");
		assertEquals(PhotoUpload.Result.REJECTED, upload.accept(42, 0, null, 101), "no data");
		assertEquals(PhotoUpload.Result.PARTIAL, upload.accept(42, 0, PhotoUpload.chunk(pixels, 0), 101));
		assertEquals(PhotoUpload.Result.REJECTED, upload.accept(42, 0, PhotoUpload.chunk(pixels, 1), 101), "a repeat");
		assertFalse(upload.isComplete());
	}

	@Test
	void lateChunksExpire() {
		byte[] pixels = picture();
		PhotoUpload upload = new PhotoUpload(7, true, 1000);
		assertFalse(upload.expired(1000 + PhotoUpload.TIMEOUT_TICKS));
		assertTrue(upload.expired(1001 + PhotoUpload.TIMEOUT_TICKS));
		assertEquals(PhotoUpload.Result.EXPIRED, upload.accept(7, 0, PhotoUpload.chunk(pixels, 0), 1001 + PhotoUpload.TIMEOUT_TICKS));
	}
}
