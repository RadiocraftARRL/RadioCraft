package com.arrl.radiocraft.common.computer;

import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A radio computer's disk (1.12 step 5): one flat directory of DOS-style {@code NAME.EXT} files, each a byte array, with
 * a size cap per file, a capacity and a file count limit (the last two given by the caller, from the server config).
 * Pure, so it is unit tested; {@link ComputerFileStore} keeps one per computer in the world save.
 * <p>
 * The extension says what a file holds (see {@link ComputerFileFormats}): {@code .TXT} text, {@code .IMG} a map picture,
 * {@code .LOG} logbook entries, {@code .FRQ} a frequency list, {@code .BOK} a book, {@code .DAT} anything else. The disk
 * itself does not care.
 */
public final class ComputerStorage {

	/** The biggest one file may be, bytes. */
	public static final int MAX_FILE_BYTES = 256 * 1024;
	/** Longest name before the dot, and extension after it (DOS 8.3). */
	public static final int NAME_MAX = 8, EXTENSION_MAX = 3;

	/** One file: its name, contents and when it was last written (ms since the epoch, real time). */
	public record FileEntry(String name, byte[] data, long modified) {
		public int size() {
			return data.length;
		}

		public String extension() {
			return ComputerStorage.extension(name);
		}
	}

	/** Why a change was refused. {@link #key()} is the lang key suffix under {@code screen.radiocraft.computer.err.}. */
	public enum Problem {
		BAD_NAME, TOO_BIG, DISK_FULL, TOO_MANY_FILES, NOT_FOUND, EXISTS, BAD_CONTENTS;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private final Map<String, FileEntry> files = new TreeMap<>();
	private long used = 0L;

	// -------------------- NAMES --------------------

	/**
	 * {@code name} as the disk keeps it (trimmed, upper case), or null if it is not a valid 8.3 name: 1-8 of A-Z, 0-9,
	 * {@code _} and {@code -}, a dot, and a 1-3 letter or digit extension.
	 */
	@Nullable
	public static String normalise(@Nullable String name) {
		if(name == null)
			return null;
		String upper = name.trim().toUpperCase(Locale.ROOT);
		int dot = upper.indexOf('.');
		if(dot < 1 || dot != upper.lastIndexOf('.'))
			return null;
		String base = upper.substring(0, dot), ext = upper.substring(dot + 1);
		if(base.length() > NAME_MAX || ext.isEmpty() || ext.length() > EXTENSION_MAX)
			return null;
		if(!base.matches("[A-Z0-9_-]+") || !ext.matches("[A-Z0-9]+"))
			return null;
		return upper;
	}

	public static boolean isValidName(@Nullable String name) {
		return normalise(name) != null;
	}

	/** The extension of {@code name} without the dot, upper case; empty if it has none. */
	public static String extension(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toUpperCase(Locale.ROOT);
	}

	/**
	 * A valid file name made from whatever a player typed and the extension to use: letters and digits kept, others
	 * dropped, cut to 8; "UNTITLED" if nothing is left. {@code extension} must be valid.
	 */
	public static String suggest(String typed, String extension) {
		StringBuilder base = new StringBuilder();
		String upper = typed == null ? "" : typed.toUpperCase(Locale.ROOT);
		int dot = upper.indexOf('.');
		if(dot >= 0)
			upper = upper.substring(0, dot);
		for(int i = 0; i < upper.length() && base.length() < NAME_MAX; i++) {
			char c = upper.charAt(i);
			if(c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '-')
				base.append(c);
		}
		return (base.isEmpty() ? "UNTITLED" : base.toString()) + "." + extension.toUpperCase(Locale.ROOT);
	}

	/**
	 * The first free numbered name {@code PREFIX0001.EXT} (as many digits as the prefix leaves of 8) that
	 * {@code taken} says is not there: how programs name what they receive (SSTV0001.IMG, FAX0001.TXT). Null if every
	 * number is taken. {@code prefix} is 1-7 valid name characters, {@code extension} a valid extension.
	 */
	@Nullable
	public static String nextName(String prefix, String extension, java.util.function.Predicate<String> taken) {
		int digits = NAME_MAX - prefix.length();
		if(digits < 1)
			throw new IllegalArgumentException("A numbered name needs room for digits: " + prefix);
		int max = digits >= 9 ? Integer.MAX_VALUE : (int)Math.pow(10, digits) - 1;
		String format = "%s%0" + digits + "d.%s";
		for(int n = 1; n <= max && n <= 100_000; n++) {
			String name = String.format(Locale.ROOT, format, prefix.toUpperCase(Locale.ROOT), n, extension.toUpperCase(Locale.ROOT));
			if(!taken.test(name))
				return name;
		}
		return null;
	}

	// -------------------- TEXT --------------------

	/** Text as a file holds it: UTF-8, newlines as {@code \n}. */
	public static byte[] text(String text) {
		return text.replace("\r\n", "\n").replace('\r', '\n').getBytes(StandardCharsets.UTF_8);
	}

	/** A text file's contents as text. */
	public static String text(byte[] data) {
		return new String(data, StandardCharsets.UTF_8);
	}

	// -------------------- FILES --------------------

	/**
	 * Writes (creates or replaces) {@code name} with {@code data}. Null if it was written, else why not: a bad name, too
	 * big, the disk full ({@code capacity} bytes in all) or too many files ({@code maxFiles}).
	 */
	@Nullable
	public Problem write(String name, byte[] data, long now, long capacity, int maxFiles) {
		String key = normalise(name);
		if(key == null)
			return Problem.BAD_NAME;
		if(data.length > MAX_FILE_BYTES)
			return Problem.TOO_BIG;
		FileEntry old = files.get(key);
		if(old == null && files.size() >= maxFiles)
			return Problem.TOO_MANY_FILES;
		long after = used - (old == null ? 0L : old.size()) + data.length;
		if(after > capacity)
			return Problem.DISK_FULL;
		files.put(key, new FileEntry(key, data.clone(), now));
		used = after;
		return null;
	}

	/** The contents of {@code name} (a copy), or null if there is no such file. */
	@Nullable
	public byte[] read(String name) {
		FileEntry entry = entry(name);
		return entry == null ? null : entry.data().clone();
	}

	/** The file {@code name}, or null. Its data is the disk's own: do not change it. */
	@Nullable
	public FileEntry entry(String name) {
		String key = normalise(name);
		return key == null ? null : files.get(key);
	}

	public boolean exists(String name) {
		return entry(name) != null;
	}

	/** Deletes {@code name}; null if it was, else {@link Problem#NOT_FOUND} (or a bad name). */
	@Nullable
	public Problem delete(String name) {
		String key = normalise(name);
		if(key == null)
			return Problem.BAD_NAME;
		FileEntry old = files.remove(key);
		if(old == null)
			return Problem.NOT_FOUND;
		used -= old.size();
		return null;
	}

	/** Renames {@code from} to {@code to}; null if done. A file already called {@code to} is not replaced. */
	@Nullable
	public Problem rename(String from, String to) {
		String a = normalise(from), b = normalise(to);
		if(a == null || b == null)
			return Problem.BAD_NAME;
		FileEntry old = files.get(a);
		if(old == null)
			return Problem.NOT_FOUND;
		if(a.equals(b))
			return null;
		if(files.containsKey(b))
			return Problem.EXISTS;
		files.remove(a);
		files.put(b, new FileEntry(b, old.data(), old.modified()));
		return null;
	}

	/** Every file whose extension is {@code extension} (any if null or empty), by name. */
	public List<FileEntry> list(@Nullable String extension) {
		List<FileEntry> out = new ArrayList<>();
		for(FileEntry entry : files.values())
			if(extension == null || extension.isEmpty() || entry.extension().equalsIgnoreCase(extension))
				out.add(entry);
		return out;
	}

	/** Every file, by name. */
	public Collection<FileEntry> all() {
		return files.values();
	}

	/** Bytes used by every file. */
	public long used() {
		return used;
	}

	public int count() {
		return files.size();
	}

	/** Empties the disk. */
	public void clear() {
		files.clear();
		used = 0L;
	}

	/** Puts a file back as it was saved (loading), whatever the caps. A bad name is skipped. */
	public void restore(String name, byte[] data, long modified) {
		String key = normalise(name);
		if(key == null)
			return;
		FileEntry old = files.put(key, new FileEntry(key, data, modified));
		used += data.length - (old == null ? 0 : old.size());
	}
}
