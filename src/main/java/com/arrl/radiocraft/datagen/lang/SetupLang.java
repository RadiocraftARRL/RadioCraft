package com.arrl.radiocraft.datagen.lang;

import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * The radio computer's launcher views (list, Program Manager, RC-DOS prompt), SETUP and ABOUT (1.12, launcher and
 * SETUP, 2026-11-06), under {@code screen.radiocraft.computer.}. No {@code %} other than {@code %s} ({@code Dos.tr}
 * formats).
 */
public final class SetupLang {

	private SetupLang() {}

	public static void add(LanguageProvider provider) {
		java.util.function.BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("app.setup", "Setup");
		s.accept("app.setup.description", "The launcher view, colour scheme, password, computer name and clock.");
		s.accept("app.about", "About RadioCraft");
		s.accept("app.about.description", "What RadioCraft is, where it came from, its version and who made it.");
		s.accept("err.not_owner_settings", "Only the computer's owner (who placed it) or an operator can change its setup");

		// The icon view and the File Manager.
		s.accept("shell.icons_title", "RC-DOS Shell");
		s.accept("shell.files_title", "RC-DOS Shell - File Manager");
		s.accept("shell.program_manager", "RC-DOS Program Manager");
		s.accept("shell.file_manager", "RC-DOS File Manager");
		s.accept("shell.keys_icons", "Arrows=Move  Enter=Run  Tab=Files  F6=Scan  Esc=Leave");

		// The command line.
		s.accept("prompt.hello", "Type HELP for the commands, or a program's name to run it.");
		s.accept("prompt.yes_no", "Are you sure (Y/N)? ");
		s.accept("prompt.bad_command", "Bad command or file name");
		s.accept("prompt.not_installed", "%s is not installed on this computer (INSTALL it from its floppy disk)");
		s.accept("prompt.file_not_found", "File not found");
		s.accept("prompt.access_denied", "Access denied");
		s.accept("prompt.required_parameter", "Required parameter missing");
		s.accept("prompt.no_directories", "Invalid directory (RC-DOS keeps every file in C:\\)");
		s.accept("prompt.no_drive_a", "Floppy disks hold programs: run INSTALL to install one");
		s.accept("prompt.delete_all", "All files in directory will be deleted!");
		s.accept("prompt.copy_onto_itself", "File cannot be copied onto itself");
		s.accept("prompt.copy_exists", "%s already exists: add /Y to overwrite it");
		s.accept("prompt.copied", "        1 file(s) copied");
		s.accept("prompt.ver", "RadioCraft %s");
		s.accept("prompt.time_real", "Current time is %s");
		s.accept("prompt.time_game", "World time is %s, day %s");
		s.accept("prompt.dir_volume", " Volume in drive C is %s");
		s.accept("prompt.dir_volume_none", " Volume in drive C has no label");
		s.accept("prompt.dir_serial", " Volume Serial Number is %s");
		s.accept("prompt.dir_of", " Directory of C:\\");
		s.accept("prompt.dir_total", "%s file(s) %s bytes");
		s.accept("prompt.dir_free", "             %s bytes free");
		s.accept("prompt.help_title", "RC-DOS commands:");
		s.accept("prompt.help.dir", "DIR [pattern] [/W]   List the programs and files (*.TXT, LOG?.LOG; /W wide)");
		s.accept("prompt.help.copy", "COPY from to [/Y]    Copy a file (/Y overwrites)");
		s.accept("prompt.help.del", "DEL or ERASE name    Delete files (wildcards too)");
		s.accept("prompt.help.ren", "REN from to          Rename a file");
		s.accept("prompt.help.type", "TYPE name            Show a text file");
		s.accept("prompt.help.print", "PRINT name           Print a .TXT or .IMG on the scanner");
		s.accept("prompt.help.cls", "CLS                  Clear the screen");
		s.accept("prompt.help.time", "TIME                 The real and the world's time");
		s.accept("prompt.help.ver", "VER                  The RC-DOS and RadioCraft versions");
		s.accept("prompt.help.settings", "SETTINGS or SETUP    Setup: launcher view, colours, password, name, clock");
		s.accept("prompt.help.exit", "EXIT                 Get up from the computer");
		s.accept("prompt.help_programs", "Run a program by its name (RTTY or RTTY.EXE), with a file after it to open (EDIT NOTES.TXT).");
		s.accept("prompt.help_keys", "Up/Down: lines typed before.  Tab: complete a name.  PgUp/PgDn: scroll.  Esc: clear.");

		// SETUP.
		s.accept("setup.title", "RC-DOS Setup");
		s.accept("setup.keys_menu", "Up/Down=Page  Enter=Open  Esc=Exit");
		s.accept("setup.keys_page", "Up/Down=Move  Enter=Choose  Left/Right=Change  Tab=Pages  Esc=Back");
		s.accept("setup.pages", "Setup");
		s.accept("setup.page.launcher", "Launcher");
		s.accept("setup.page.colours", "Colours");
		s.accept("setup.page.password", "Password");
		s.accept("setup.page.name_clock", "Name and Clock");
		s.accept("setup.read_only", "Only the owner or an operator can change these settings.");
		s.accept("setup.saved", "Setup saved: it stays with this computer");
		s.accept("setup.launcher.intro", "How the computer shows its programs:");
		s.accept("setup.launcher.list", "RC-DOS Shell (list)");
		s.accept("setup.launcher.list.about", "Programs on the left, files on the right.");
		s.accept("setup.launcher.icons", "Program Manager (icons)");
		s.accept("setup.launcher.icons.about", "A window of program icons; Tab for the files.");
		s.accept("setup.launcher.command", "RC-DOS prompt (command line)");
		s.accept("setup.launcher.command.about", "Type commands: DIR, TYPE, COPY... SETUP brings you back here.");
		s.accept("setup.colours.intro", "Move to see a scheme, Enter to keep it:");
		s.accept("setup.colours.sample", "Sample:");
		s.accept("setup.sample.title", "RC-DOS Program");
		s.accept("setup.sample.text", "Text on the screen");
		s.accept("setup.sample.body", "Body text,");
		s.accept("setup.sample.dim", "a dim hint");
		s.accept("setup.sample.selected", "The selected line");
		s.accept("setup.sample.accent", "Heading");
		s.accept("setup.sample.good", "Sent");
		s.accept("setup.sample.bad", "No radio");
		s.accept("setup.sample.error", "Error");
		s.accept("setup.sample.keys", "Help");
		s.accept("setup.scheme.classic", "Classic Blue");
		s.accept("setup.scheme.teal", "RC-DOS Teal");
		s.accept("setup.scheme.high_contrast", "High Contrast");
		s.accept("setup.scheme.green", "Green Phosphor");
		s.accept("setup.scheme.amber", "Amber");
		s.accept("setup.scheme.white", "Paper White");
		s.accept("setup.scheme.plasma", "Gas Plasma");
		s.accept("setup.password.set", "This computer has a password.");
		s.accept("setup.password.none", "This computer has no password.");
		s.accept("setup.password.change", "Change or remove the password...");
		s.accept("setup.password.choose", "Set a password...");
		s.accept("setup.password.about", "Everyone who sits at the computer types it first, you too, and nothing on it can be read until they do. Server operators are never asked, and can clear a forgotten one here. Up to 16 characters; leave it empty to remove it. It stays with the computer when it is broken and placed again.");
		s.accept("setup.password.title", "Password");
		s.accept("setup.password.prompt", "New password for this computer (empty for none):");
		s.accept("setup.password.done_set", "Password set: everyone is asked for it when they open the computer");
		s.accept("setup.password.done_cleared", "Password removed: anyone can use the computer");
		s.accept("setup.name_clock.intro", "The top bar shows the computer's name and a clock.");
		s.accept("setup.name", "Computer name");
		s.accept("setup.name.none", "(none)");
		s.accept("setup.name.prompt", "A name for this computer (up to %s characters, empty for none):");
		s.accept("setup.clock", "Clock");
		s.accept("setup.clock.game", "World time");
		s.accept("setup.clock.real", "Real time");
		s.accept("setup.clock.off", "Off");
		s.accept("setup.hours", "Hours");
		s.accept("setup.hours.24", "24-hour");
		s.accept("setup.hours.12", "12-hour");
		s.accept("setup.name_clock.about", "World time is the Minecraft day (sunrise at 6:00). Real time is your own computer's clock. The name also labels drive C: in DIR.");

		// ABOUT: the website's words (radiocraft.w1btr.com), fitted to the text screen.
		s.accept("about.title", "About RadioCraft");
		s.accept("about.keys", "Up/Down=Scroll  PgUp/PgDn=Page  Esc=Exit");
		s.accept("about.banner", "R A D I O C R A F T   -   Amateur Radio for Minecraft");
		s.accept("about.version", "RadioCraft %s  -  RC-DOS Version 6.22");
		s.accept("about.h.what", "CQ CQ DE RADIOCRAFT");
		s.accept("about.what", "Developed with a $20,000 grant from the ARRL, RadioCraft brings real amateur radio into Minecraft Java Edition. Build working HF and VHF transceivers, string wire dipoles between trees, tune your SWR with baluns and antenna analyzers, and talk across your server using authentic radio mechanics.");
		s.accept("about.what2", "It is not a reskinned chat box. Getting on the air means crafting crystals and ferrite cores, setting up off-grid solar or battery power, and matching your antenna to the band you want to work. Every transmission draws energy, and the sun decides which bands are open.");
		s.accept("about.h.story", "THE STORY");
		s.accept("about.story", "RadioCraft started in 2022 when Lucas (W1BTR) noticed that while Minecraft had mods for nuclear physics, spaceflight and complex automation, there was not a single mod for amateur radio.");
		s.accept("about.story2", "Sponsored by the Police Amateur Radio Team (PART) of Westford, MA (WB1GOF), the project was funded with a $20,000 grant from the ARRL Foundation in early 2023, to bring a playable amateur radio simulation into Minecraft and introduce younger generations to the hobby.");
		s.accept("about.story3", "In August 2023 RadioCraft was unveiled with live, playable demos at the Northeast HamXposition (the ARRL New England Division Convention) in Marlborough, MA, where young players and veteran hams alike sat down with headsets to build stations and make contacts in game.");
		s.accept("about.h.computer", "THIS COMPUTER");
		s.accept("about.computer", "The radio computer is the shack's PC: logbooks, frequency lists, radio programming, APRS, fax, SSTV, RTTY, a packet BBS and more. Its manual is the Handbook's Computer chapter. SETUP changes how it looks.");
		s.accept("about.h.credits", "CREDITS");
		s.accept("about.credits", "RadioCraft is free and open source, made by W1BTR and the RadioCraft contributors, with thanks to the ARRL Foundation and PART of Westford. The music, the Bible text and the licence question pools are public domain; their sources are listed in the Credits files that come with the mod's source.");
		s.accept("about.credits2", "Everyone is welcome on the Discord: licensed Extra class operators, Minecraft modders, server admins, and anyone who just wants to see how ham radio works.");
		s.accept("about.h.more", "READ MORE");
		s.accept("about.more", "The website has the news, the wiki (the Handbook online) and the downloads:");
		s.accept("about.url", "    https://radiocraft.w1btr.com/");
		s.accept("about.discord", "    Discord: discord.gg/tZFWE2jYQX     Modrinth: modrinth.com/mod/radiocraft");
		s.accept("about.source", "    Source code and issues: github.com/RadiocraftARRL/RadioCraft");
	}
}
