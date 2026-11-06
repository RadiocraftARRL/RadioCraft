package com.arrl.radiocraft.common.computer;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * The rules of a data floppy (computer batch 3A): a blank floppy disk in a radio computer's drive is drive {@code A:},
 * and files copied onto it go wherever the disk goes. Pure, so it is unit tested.
 * <ul>
 * <li>Capacity: {@link #CAPACITY} bytes (a 3.5" high-density disk's 1.44 MB as DOS formats it) and at most
 * {@link #MAX_FILES} files. One file is still at most {@link ComputerStorage#MAX_FILE_BYTES}.</li>
 * <li>Data files only: documents, logs, pictures, codeplugs, frequency lists. A program never goes on a data floppy
 * ({@link #isProgramName}): programs are installed from their own disks, which are write-protected.</li>
 * <li>A label of up to {@link #LABEL_MAX} characters ({@link #cleanLabel}), as DOS's {@code LABEL} command set, shown in
 * the item's tooltip.</li>
 * </ul>
 * The files themselves are kept by the server ({@link FloppyFileStore}, named by the id in the item's
 * {@code radiocraft:floppy_data} component), so the item stays small enough to send in any inventory packet.
 */
public final class FloppyDisk {

	/** Bytes a data floppy holds: 1,457,664, what DOS reports free on a formatted 1.44 MB disk. */
	public static final long CAPACITY = 1_457_664L;
	/** Most files on one floppy. */
	public static final int MAX_FILES = 64;
	/** Longest volume label (DOS: 11). */
	public static final int LABEL_MAX = 11;
	/** Extensions that would be programs: never written to a floppy. */
	public static final Set<String> PROGRAM_EXTENSIONS = Set.of("EXE", "COM", "BAT", "SYS", "BIN", "DLL", "OVL");

	private FloppyDisk() {}

	/** True if {@code name} looks like a program ({@code .EXE}, {@code .COM}...): it may not go on a data floppy. */
	public static boolean isProgramName(String name) {
		return name != null && PROGRAM_EXTENSIONS.contains(ComputerStorage.extension(name));
	}

	/**
	 * {@code label} as a volume label: upper case, letters, digits, spaces and {@code -_!#$%&'()@^`{}~} kept, others
	 * dropped, trimmed, at most {@link #LABEL_MAX}. Empty for none.
	 */
	public static String cleanLabel(String label) {
		if(label == null)
			return "";
		StringBuilder out = new StringBuilder();
		String upper = label.toUpperCase(Locale.ROOT);
		for(int i = 0; i < upper.length() && out.length() < LABEL_MAX; i++) {
			char c = upper.charAt(i);
			if(c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == ' ' || "-_!#$%&'()@^`{}~".indexOf(c) >= 0)
				out.append(c);
		}
		return out.toString().trim();
	}

	/**
	 * Writes {@code name} onto a floppy's files {@code disk}: refused for a program ({@link ComputerStorage.Problem#PROGRAM}),
	 * else within the floppy's caps. Null if written.
	 */
	@Nullable
	public static ComputerStorage.Problem write(ComputerStorage disk, String name, byte[] data, long now) {
		if(isProgramName(name))
			return ComputerStorage.Problem.PROGRAM;
		return disk.write(name, data, now, CAPACITY, MAX_FILES);
	}

	/** {@code bytes} as the tooltip shows a disk's use: "12 KB", "1.2 MB". */
	public static String size(long bytes) {
		if(bytes < 1024L)
			return bytes + " bytes";
		if(bytes < 1024L * 1024L)
			return (bytes + 1023L) / 1024L + " KB";
		return String.format(Locale.ROOT, "%.2f MB", bytes / (1024.0D * 1000.0D)); // DOS's "1.44 MB" is 1024 x 1000.
	}
}
