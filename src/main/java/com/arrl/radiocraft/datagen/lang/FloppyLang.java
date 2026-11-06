package com.arrl.radiocraft.datagen.lang;

import com.arrl.radiocraft.Radiocraft;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * Data floppies' words (computer batch 3A, 2026-11-06): drive A: in the shell, the File Manager and the RC-DOS prompt,
 * the errors, and the Data Floppy item's name and tooltip.
 */
public final class FloppyLang {

	private FloppyLang() {}

	public static void add(LanguageProvider provider) {
		provider.add("item.radiocraft.floppy_disk.data", "Data Floppy");
		provider.add(Radiocraft.translationKey("tooltip", "floppy_data.label"), "Label: %s");
		provider.add(Radiocraft.translationKey("tooltip", "floppy_data.files"), "%s files, %s of 1.44 MB");
		provider.add(Radiocraft.translationKey("tooltip", "floppy_data.empty"), "No files");
		provider.add(Radiocraft.translationKey("tooltip", "floppy_data.use"), "Drive A: in a radio computer: copy files on and off it in the File Manager, or with COPY.");
		provider.add(Radiocraft.translationKey("message", "computer.floppy_data"), "Data disk in drive A: (%s files)");

		java.util.function.BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("drive.data", "Data disk");
		s.accept("drive.data_label", "Data disk %s");
		s.accept("install.data", "%s in drive A: files, not a program (copy them with F8 or COPY)");
		s.accept("err.no_disk", "Not ready reading drive A: there is no disk in the drive");
		s.accept("err.write_protected", "Write protect error: a program's disk cannot take files");
		s.accept("err.program", "Programs cannot go on a data floppy: install them from their own disks");
		s.accept("err.copy_onto_itself", "File cannot be copied onto itself");

		// The shell's files list and the File Manager.
		s.accept("shell.no_files_a", "No files on the floppy yet (F8 on C: copies one here).");
		s.accept("shell.drive_keys_c", "F3=Show A:  F8=Copy to A:  F4=Label");
		s.accept("shell.drive_keys_a", "F3=Show C:  F8=Copy to C:  F4=Label");
		s.accept("shell.program_disk", "%s is a program's disk: INSTALL installs it; it takes no files");
		s.accept("shell.copy_title", "Copy");
		s.accept("shell.copy_replace", "%s is already there. Replace it?");
		s.accept("shell.copied", "%s copied to %s");
		s.accept("shell.label_title", "Label");
		s.accept("shell.label", "The floppy's label (up to %s letters; blank for none):");
		s.accept("shell.labelled", "Drive A: %s");
		s.accept("shell.open_from_a", "%s is on the floppy: copy it to C: (F8) to open or print it");

		// The prompt.
		s.accept("prompt.invalid_drive", "Invalid drive specification");
		s.accept("prompt.write_protected", "Write protected: a program's disk (INSTALL installs it)");
		s.accept("prompt.copy_to_drive", "With * or ? in the name, copy to a drive: COPY *.LOG A:");
		s.accept("prompt.program_not_copied", "Programs cannot be copied to a floppy: install them from their own disks");
		s.accept("prompt.files_copied", "%s file(s) copied");
		s.accept("prompt.print_from_c", "Copy the file to C: first: the printer prints from C:");
		s.accept("prompt.label_c", "Drive C:'s label is the computer's name: set it in SETUP");
		s.accept("prompt.label_usage", "LABEL A: name   names the floppy (up to 11 letters)");
		s.accept("prompt.help.drive", "A: or C:             Change drive (A: is the floppy in the drive)");
		s.accept("prompt.help.label", "LABEL [A:] name      Name the floppy disk");
	}
}
