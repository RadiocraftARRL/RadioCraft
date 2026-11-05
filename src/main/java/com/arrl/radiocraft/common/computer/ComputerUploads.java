package com.arrl.radiocraft.common.computer;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A file written from a player's screen goes up in parts: a packet from the client may hold at most 32 KB, a file up to
 * {@link ComputerStorage#MAX_FILE_BYTES}. The client cuts it with {@link #part}; the server puts the parts back
 * together per player with an {@link Assembler}, in order, and writes the file only once the last part is in, so a
 * file is never left half written. Pure, so it is unit tested.
 */
public final class ComputerUploads {

	/** Bytes in one part (with the packet's other fields it stays well under the 32 KB limit). */
	public static final int PART_BYTES = 16 * 1024;
	/** Most parts one file takes. */
	public static final int MAX_PARTS = (ComputerStorage.MAX_FILE_BYTES + PART_BYTES - 1) / PART_BYTES;
	/** Ticks an upload may take from its first part to its last before it is dropped. */
	public static final long TIMEOUT_TICKS = 20L * 30L;

	private ComputerUploads() {}

	/** How many parts {@code length} bytes go up in (at least one: an empty file is one empty part). */
	public static int parts(int length) {
		return Math.max(1, (length + PART_BYTES - 1) / PART_BYTES);
	}

	/** Part {@code index} of {@code data}. */
	public static byte[] part(byte[] data, int index) {
		int from = Math.min(data.length, index * PART_BYTES), to = Math.min(data.length, from + PART_BYTES);
		return Arrays.copyOfRange(data, from, to);
	}

	/** What happened to a part. */
	public enum Status {
		/** Taken; more to come. */
		PARTIAL,
		/** The last part: {@link Result#data()} is the whole file. */
		DONE,
		/** Out of order, too big, or for an upload that timed out: the upload is dropped. */
		BAD
	}

	public record Result(Status status, @Nullable byte[] data) {}

	/** The uploads in progress, one per key (a player). */
	public static final class Assembler<K> {

		private static final class Upload {
			final String name;
			final int total;
			final long started;
			int next = 0;
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			Upload(String name, int total, long started) {
				this.name = name;
				this.total = total;
				this.started = started;
			}
		}

		private final Map<K, Upload> uploads = new HashMap<>();

		/**
		 * Part {@code index} of {@code total} of file {@code name} from {@code key}, at game time {@code now}. Part 0
		 * starts a new upload (dropping any unfinished one); the others must follow in order, for the same file.
		 */
		public synchronized Result accept(K key, String name, int index, int total, byte[] part, long now) {
			if(total < 1 || total > MAX_PARTS || index < 0 || index >= total || part.length > PART_BYTES) {
				uploads.remove(key);
				return new Result(Status.BAD, null);
			}
			Upload upload = uploads.get(key);
			if(index == 0) {
				upload = new Upload(name, total, now);
				uploads.put(key, upload);
			}
			else if(upload == null || upload.next != index || upload.total != total || !upload.name.equals(name) || now - upload.started > TIMEOUT_TICKS) {
				uploads.remove(key);
				return new Result(Status.BAD, null);
			}
			upload.bytes.writeBytes(part);
			upload.next++;
			if(upload.bytes.size() > ComputerStorage.MAX_FILE_BYTES) {
				uploads.remove(key);
				return new Result(Status.BAD, null);
			}
			if(upload.next < upload.total)
				return new Result(Status.PARTIAL, null);
			uploads.remove(key);
			return new Result(Status.DONE, upload.bytes.toByteArray());
		}

		/** Drops {@code key}'s upload, if any (the screen closed). */
		public synchronized void cancel(K key) {
			uploads.remove(key);
		}

		/** Drops uploads started more than {@link #TIMEOUT_TICKS} before {@code now}. */
		public synchronized void expire(long now) {
			for(Iterator<Upload> it = uploads.values().iterator(); it.hasNext(); )
				if(now - it.next().started > TIMEOUT_TICKS)
					it.remove();
		}

		public synchronized int size() {
			return uploads.size();
		}
	}
}
