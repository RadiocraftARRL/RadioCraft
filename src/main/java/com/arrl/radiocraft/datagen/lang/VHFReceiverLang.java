package com.arrl.radiocraft.datagen.lang;

import net.neoforged.neoforge.common.data.LanguageProvider;

/** The VHF receiver's words (2026-11-09): its tooltip, its cell, and what each control on its screen does. */
public final class VHFReceiverLang {

	private VHFReceiverLang() {}

	public static void add(LanguageProvider provider) {
		provider.add("tooltip.radiocraft.vhf_receiver", "Listens on 2m FM (receive only), with six memory channels that it can scan. Hears a few blocks with no antenna; coax a VHF antenna to it for range.");
		provider.add("tooltip.radiocraft.vhf_receiver.cell", "Runs from a power wire, or from a Small NiCd or Small Alkaline Battery: shift + right-click it with one to put it in, with an empty hand to take it out.");

		provider.add("message.radiocraft.vhf_receiver.cell_in", "Cell in");
		provider.add("message.radiocraft.vhf_receiver.cell_out", "Cell out");

		java.util.function.BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.vhf_receiver." + key, text);
		s.accept("power", "Power. Runs from a power wire, or from a cell in it when the wire has nothing (shift + right-click the receiver with a Small NiCd or Alkaline Battery).");
		s.accept("tuning", "Tuning: drag sideways or scroll to change the frequency. Turning it leaves a memory channel or a scan.");
		s.accept("volume", "Volume: drag sideways or scroll.");
		s.accept("mute", "Mute the speaker. Lamps and scan still see signals.");
		s.accept("muted", "Muted: click to hear again.");
		s.accept("memory_empty", "Memory %s: empty. Click to store the frequency shown and listen on it.");
		s.accept("memory", "Memory %s: %s MHz. Click to select it or let it go; press two or more in to scan them. Shift + click stores the frequency shown here instead.");
		s.accept("lamp", "Lights while a signal comes in on memory %s.");
		s.accept("display", "Frequency. DIAL: listening where the knob is; MEM n: on memory n; SCAN: scanning the pressed memories.");
		s.accept("display_cell", "Frequency, and the cell: %s%% left. A cell lasts about ten days of listening, much less while it plays signals.");
	}
}
