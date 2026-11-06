package com.arrl.radiocraft.common.computer;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One drive of a radio computer as the file requests see it (computer batch 3A): {@code C:}, the computer's own disk
 * ({@link ComputerFileStore}), or {@code A:}, the floppy in its drive ({@link FloppyDrive#volume}). Server side. The
 * {@code fs.*} requests take a {@code drive} ("A" or "C") and work on either the same way; programs that keep files of
 * their own use {@link ComputerContext#files()} (C:) as before, or {@link ComputerContext#floppy()} for A:.
 * <p>
 * Every change answers null when done, or the {@link ComputerStorage.Problem} that stopped it.
 */
public interface ComputerVolume {

	/** The files, to read. Change them only through this volume, so they are saved. */
	ComputerStorage storage();

	/** Writes (creates or replaces) {@code name}. */
	@Nullable
	ComputerStorage.Problem write(String name, byte[] data);

	/** The contents of {@code name} (a copy), or null. */
	@Nullable
	default byte[] read(String name) {
		return storage().read(name);
	}

	@Nullable
	ComputerStorage.Problem delete(String name);

	@Nullable
	ComputerStorage.Problem rename(String from, String to);

	/** Every file with {@code extension} (all if null or empty), by name. */
	default List<ComputerStorage.FileEntry> list(@Nullable String extension) {
		return storage().list(extension);
	}

	/** Bytes this volume may hold. */
	long totalBytes();

	/** Most files it may hold. */
	int fileLimit();

	/** Its volume label: the computer's name for C:, the floppy's label for A:. */
	String label();

	/** The drive letter, {@link DrivePath#COMPUTER} or {@link DrivePath#FLOPPY}. */
	char drive();
}
