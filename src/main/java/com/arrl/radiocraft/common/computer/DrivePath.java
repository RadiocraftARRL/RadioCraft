package com.arrl.radiocraft.common.computer;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A file path as RC-DOS takes it (computer batch 3A, data floppies): an optional drive letter and colon, an optional
 * backslash (there are no directories), and a file name or pattern. {@code C:} is the computer's own disk, {@code A:}
 * the floppy in its drive. Pure, so it is unit tested; used by the prompt ({@code COPY X A:}, {@code DIR A:}) and by
 * the server's {@code fs.copy} request.
 * <pre>{@code
 * DrivePath.parse("A:NOTES.TXT", 'C')  -> A, "NOTES.TXT"
 * DrivePath.parse("a:\\", 'C')         -> A, ""            (the drive alone)
 * DrivePath.parse("NOTES.TXT", 'A')    -> A, "NOTES.TXT"   (the current drive)
 * DrivePath.parse("B:X.TXT", 'C')      -> null            (no such drive)
 * }</pre>
 *
 * @param drive {@link #COMPUTER} or {@link #FLOPPY}
 * @param name  the name after the drive, upper case; empty when only a drive was given
 */
public record DrivePath(char drive, String name) {

	/** The computer's own disk. */
	public static final char COMPUTER = 'C';
	/** The floppy drive. */
	public static final char FLOPPY = 'A';

	public DrivePath {
		drive = Character.toUpperCase(drive);
		name = name == null ? "" : name.toUpperCase(Locale.ROOT);
	}

	/**
	 * {@code text} as a path, with {@code current} the drive a name with no drive letter is on. Null for a drive
	 * other than A: or C:, or an empty text.
	 */
	@Nullable
	public static DrivePath parse(@Nullable String text, char current) {
		if(text == null)
			return null;
		String t = text.trim();
		if(t.isEmpty())
			return null;
		char drive = Character.toUpperCase(current);
		if(t.length() >= 2 && t.charAt(1) == ':') {
			drive = Character.toUpperCase(t.charAt(0));
			t = t.substring(2);
		}
		if(drive != COMPUTER && drive != FLOPPY)
			return null;
		while(t.startsWith("\\") || t.startsWith("/"))
			t = t.substring(1);
		return new DrivePath(drive, t);
	}

	/** The drive letter of a request's {@code drive} field ("A" or anything else for C:). */
	public static char driveOf(@Nullable String field) {
		return field != null && field.trim().equalsIgnoreCase("A") ? FLOPPY : COMPUTER;
	}

	/** True if only a drive was given ({@code A:}), so a copy keeps the file's name. */
	public boolean isDriveOnly() {
		return name.isEmpty();
	}

	public boolean onFloppy() {
		return drive == FLOPPY;
	}

	/** {@code A:NAME} or {@code C:NAME}, as RC-DOS writes it. */
	@Override
	public String toString() {
		return drive + ":" + name;
	}
}
