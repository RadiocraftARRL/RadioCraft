package com.arrl.radiocraft.datagen.lang;

import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.function.BiConsumer;

/** The RTTY program's words (Computer apps 2): keys under {@code screen.radiocraft.computer.rtty.} and its errors. */
public final class RttyLang {

	private RttyLang() {}

	public static void add(LanguageProvider provider) {
		BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("rtty.title", "RTTY  45.45 baud Baudot, 170 Hz shift");
		s.accept("rtty.keys", "F3=CQ  F4=Exch  F5=73  F6=File  F7=Save  F8=Clr  F9=Macro  F10=DX  ^X=Abort");
		s.accept("rtty.station", "%s on %s MHz %s");
		s.accept("rtty.listening", "Listening. Type to send; what you type goes out at about six characters a second.");
		s.accept("rtty.scrolled", " PgDn / End ");
		s.accept("rtty.buffer", "Type ahead: %s to send");
		s.accept("rtty.hint", "Backspace takes back what has not gone out. Esc leaves; sending carries on.");
		s.accept("rtty.clear_title", "Clear");
		s.accept("rtty.clear", "Clear the receive window? Save it first with F7 to keep it.");
		s.accept("rtty.send_title", "Send a text file");
		s.accept("rtty.sending_file", "Sending %s");
		s.accept("rtty.macro_cq", "F3 CQ");
		s.accept("rtty.macro_call", "F4 Exchange");
		s.accept("rtty.macro_73", "F5 Sign-off");
		s.accept("rtty.macros_title", "Macros");
		s.accept("rtty.macros_help", "{MYCALL} is your call, {CALL} the DX call (F10), \\n ends a line.");
		s.accept("rtty.call_title", "DX call");
		s.accept("rtty.call_prompt", "The station you are working ({CALL} in the macros):");
		s.accept("err.rtty_nothing_to_save", "Nothing received yet");
		s.accept("err.rtty_nothing_to_send", "Nothing a teleprinter can send");
		s.accept("err.rtty_buffer_full", "The send buffer is full: wait for it to go out");
	}
}
