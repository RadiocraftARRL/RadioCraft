package com.arrl.radiocraft.datagen.lang;

import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * The VHF receiver's words: its tooltip, its cell, what each control on its panel does, and the label over the panel
 * that shows the frequency (2026-11-09, the eight-channel scanner).
 */
public final class VHFReceiverLang {

	private VHFReceiverLang() {}

	public static void add(LanguageProvider provider) {
		provider.add("tooltip.radiocraft.vhf_receiver", "An eight-channel FM scanner, 144 to 174 MHz: 2 m ham, marine and commercial VHF (receive only). Switch channels on and it scans them, stopping on a signal; SET and TUNE change a channel's frequency. Its own whip hears as far as a VHF handheld; coax a VHF antenna to it for more.");
		provider.add("tooltip.radiocraft.vhf_receiver.cell", "Runs from a power wire, or from a Small NiCd or Small Alkaline Battery: shift + right-click it with one to put it in, with an empty hand to take it out.");

		provider.add("message.radiocraft.vhf_receiver.cell_in", "Cell in");
		provider.add("message.radiocraft.vhf_receiver.cell_out", "Cell out");

		java.util.function.BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.vhf_receiver." + key, text);
		s.accept("set_out", "SET: press it in to change a channel's frequency. Then switch on just that channel: the lamp lights and TUNE moves it.");
		s.accept("set_in", "SET is in. With just one channel switched on the lamp lights and TUNE moves its frequency. Press again to let it out.");
		s.accept("auto", "AUTO: scans the channels that are on, stopping on a signal. Press for MAN, to hold on the channel it is on.");
		s.accept("man", "MAN: holds on the channel it is on. Press for AUTO, to scan again.");

		s.accept("hint.channel", "CH %s · %s MHz");
		s.accept("hint.set", "SET CH %s · %s MHz");
		s.accept("hint.hold", "HOLD CH %s · %s MHz");
		s.accept("hint.scan", "SCAN");
		s.accept("hint.no_channels", "No channels on: push a switch up");
		s.accept("hint.no_power", "No power: wire it, or put a cell in");
		s.accept("hint.off", "Switched off: turn VOLUME up");
		s.accept("hint.set_off", "Press SET in to change a channel");
		s.accept("hint.set_one", "SET: switch on just one channel");
	}
}
