package com.arrl.radiocraft.common.computer;

/**
 * One program for the radio computer (1.12): its id, the DOS command it runs as, and how a computer gets it. The ids
 * are also the floppy disks' item ids ({@code floppy_<id>}) and the lang keys
 * ({@code screen.radiocraft.computer.app.<id>} and {@code .<id>.description}). Pure: no game classes, so the registry
 * can be unit tested. See {@link ComputerApps} for the list and {@code doc/Plan-1.12.md} (Step 5 handoff) for how to
 * add one.
 *
 * @param id      lower case letters, digits and underscores
 * @param command what the shell lists it as, without ".EXE" (at most 8 letters, upper case)
 * @param kind    built in, on a craftable floppy, or on a secret one found in structures
 */
public record ComputerApp(String id, String command, Kind kind) {

	/** How a computer gets a program. */
	public enum Kind {
		/** Every computer has it; no floppy. */
		BUILT_IN,
		/** Installed from a floppy disk that is crafted. */
		CRAFTED,
		/** Installed from a floppy disk found only in structure loot (no recipe). */
		SECRET
	}

	public ComputerApp {
		if(id == null || !id.matches("[a-z0-9_]{1,32}"))
			throw new IllegalArgumentException("A computer app id is 1-32 of a-z, 0-9 and _: " + id);
		if(command == null || !command.matches("[A-Z0-9_-]{1,8}"))
			throw new IllegalArgumentException("A computer app command is a DOS name of 1-8 capitals: " + command);
		if(kind == null)
			throw new IllegalArgumentException("A computer app needs a kind");
	}

	public boolean builtIn() {
		return kind == Kind.BUILT_IN;
	}

	/** True if it comes on a floppy disk (crafted or secret). */
	public boolean hasFloppy() {
		return kind != Kind.BUILT_IN;
	}

	/** Its floppy disk's item id (path only, in the radiocraft namespace), or null for a built-in program. */
	public String floppyItemId() {
		return hasFloppy() ? "floppy_" + id : null;
	}

	/** The file name the shell shows, {@code EDIT.EXE}. */
	public String fileName() {
		return command + ".EXE";
	}

	/** Its name's lang key. */
	public String nameKey() {
		return "screen.radiocraft.computer.app." + id;
	}

	/** Its one-line description's lang key (the shell's status line and the floppy's tooltip). */
	public String descriptionKey() {
		return nameKey() + ".description";
	}
}
