package com.arrl.radiocraft.datagen.lang;

import net.neoforged.neoforge.common.data.LanguageProvider;

/** The Repeater Directory program's words (computer apps 2, 2026-11-06), under {@code screen.radiocraft.computer.rptdir.}. */
public final class RepeaterLang {

	private RepeaterLang() {}

	public static void add(LanguageProvider provider) {
		java.util.function.BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("rptdir.title", "Repeater Directory - %s repeaters");
		s.accept("rptdir.keys", "Ins=Add  Del=Delete  Spc=Mark  F3=Sort  F5=Collect  F6=Print  F7=Export");
		s.accept("rptdir.col.call", "Call");
		s.accept("rptdir.col.output", "Output");
		s.accept("rptdir.col.offset", "Offset");
		s.accept("rptdir.col.tone", "Tone");
		s.accept("rptdir.col.distance", "Dist");
		s.accept("rptdir.col.bearing", "Brg");
		s.accept("rptdir.col.heard", "Heard");
		s.accept("rptdir.col.notes", "Notes");
		s.accept("rptdir.collecting", "Asking the radios what they have heard...");
		s.accept("rptdir.empty", "No repeaters yet.");
		s.accept("rptdir.empty_hint", "Tune a wired radio to a repeater's output and listen for its ID or courtesy beep, then F5. Or Ins to add one.");
		s.accept("rptdir.collected", "%s new or updated, from %s radios");
		s.accept("rptdir.manual", "manual");
		s.accept("rptdir.ago_s", "%ss ago");
		s.accept("rptdir.ago_m", "%sm ago");
		s.accept("rptdir.ago_h", "%sh ago");
		s.accept("rptdir.ago_d", "%sd ago");
		s.accept("rptdir.elsewhere", "other dim");
		s.accept("rptdir.input", "Transmit on %s %s");
		s.accept("rptdir.with_tone", "with tone %s Hz");
		s.accept("rptdir.no_tone", "(no tone)");
		s.accept("rptdir.at", "   at X %s, Z %s");
		s.accept("rptdir.status", "Sorted by %s   %s marked   %s radios checked");
		s.accept("rptdir.sort.distance", "distance");
		s.accept("rptdir.sort.frequency", "frequency");
		s.accept("rptdir.sort.heard", "last heard");
		s.accept("rptdir.add_title", "Add Repeater");
		s.accept("rptdir.edit_title", "Edit Repeater");
		s.accept("rptdir.form_hint", "Offset in kHz (-600) or MHz (-0.6). Leave X and Z empty if you do not know where it is.");
		s.accept("rptdir.f.call", "Callsign");
		s.accept("rptdir.f.output", "Output MHz");
		s.accept("rptdir.f.offset", "Offset");
		s.accept("rptdir.f.tone", "Tone Hz");
		s.accept("rptdir.f.x", "X");
		s.accept("rptdir.f.z", "Z");
		s.accept("rptdir.f.notes", "Notes");
		s.accept("rptdir.bad_output", "Type the output frequency in MHz (146.940)");
		s.accept("rptdir.bad_offset", "The offset is kHz (-600, +5000) or MHz with a point (-0.6)");
		s.accept("rptdir.bad_tone", "The tone is in Hz (100.0), or empty for none");
		s.accept("rptdir.bad_place", "X and Z are whole block numbers");
		s.accept("rptdir.delete_title", "Delete Repeater");
		s.accept("rptdir.delete", "Delete %s from the directory?");
		s.accept("rptdir.nothing", "Nothing to export: mark repeaters with Space, or select one");
		s.accept("rptdir.export_title", "Export to Frequency Manager");
		s.accept("rptdir.export", "Add %s repeaters to which frequency list?");
		s.accept("rptdir.exported_title", "Exported");
		s.accept("rptdir.exported", "%s added to %s (%s already there or no room)");
		s.accept("rptdir.open_freqman", "Open FREQMAN");
		s.accept("rptdir.print_title", "Repeater Directory");
		s.accept("err.rptdir_bad_frequency", "A repeater needs an output frequency");
		s.accept("err.rptdir_full", "The directory is full: delete some repeaters first");
		s.accept("err.rptdir_changed", "The directory changed meanwhile: look again and retry");
		s.accept("err.rptdir_bad_file", "Export to a .FRQ frequency list");
		s.accept("err.rptdir_nothing_chosen", "Nothing chosen to export");
	}
}
