package com.arrl.radiocraft.datagen.lang;

import net.neoforged.neoforge.common.data.LanguageProvider;

/** The screensaver's words (computer batch 3A, 2026-11-06): the program, and SETUP's Screensaver page. */
public final class SaverLang {

	private SaverLang() {}

	public static void add(LanguageProvider provider) {
		java.util.function.BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("app.saver", "Screensaver");
		s.accept("app.saver.description", "Stars, your callsign or a band waterfall when the computer sits idle; it draws less power then.");
		s.accept("setup.page.saver", "Screensaver");
		s.accept("setup.saver.intro", "When nobody touches the keys or the mouse:");
		s.accept("setup.saver.missing", "Install the screensaver program (SAVER.EXE) first: its disk is found, not made.");
		s.accept("setup.saver.wait", "Start after");
		s.accept("setup.saver.never", "Never");
		s.accept("setup.saver.minutes", "%s min");
		s.accept("setup.saver.style", "Show");
		s.accept("setup.saver.style.starfield", "Starfield");
		s.accept("setup.saver.style.marquee", "Callsign");
		s.accept("setup.saver.style.waterfall", "Band waterfall");
		s.accept("setup.saver.preview", "Preview");
		s.accept("setup.saver.preview_now", "Enter: show it now");
		s.accept("setup.saver.about", "Any key, click or mouse move wakes it. While it shows, the computer draws a quarter of its power, and the monitor in the world shows it too. Background programs (the scheduler, the BBS) keep running.");
	}
}
