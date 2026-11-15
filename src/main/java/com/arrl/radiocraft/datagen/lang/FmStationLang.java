package com.arrl.radiocraft.datagen.lang;

import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.function.BiConsumer;

/** The FM broadcast station (2026-11-14): the transmitter's station panel. */
public final class FmStationLang {

	private FmStationLang() {}

	public static void add(LanguageProvider provider) {
		BiConsumer<String, String> m = (key, text) -> provider.add("radiocraft.fm_station." + key, text);
		m.accept("title", "FM BROADCAST STATION");
		m.accept("tape", "Tape");
		m.accept("stream", "Stream");
		m.accept("program", "Program:");
		m.accept("url", "Stream:");
		m.accept("url_hint", "http(s):// MP3, Ogg, .m3u or .pls");
		m.accept("set", "Set");
		m.accept("streams_off", "Internet streams are off on this server");
		m.accept("not_owner", "Only the station's owner can set its stream");
		m.accept("fallback", "Stream down: tape standing in (%s)");
		m.accept("playing", "Playing: %s");
		m.accept("stream_status", "Stream: %s");
		m.accept("tape_idle", "Tape player linked, nothing playing");
		m.accept("no_tape", "No tape player linked (data wire or beside it)");
		m.accept("forecast", "Forecast:");
		m.accept("off", "off");
		m.accept("every", "every %s min");
		m.accept("next", "next in %s");
		m.accept("wx_waiting", "%s weather warning(s) waiting for the mic");
		m.accept("wx_decoder", "Weather alert decoder linked");
		m.accept("wx_none", "No weather alert receiver linked");
		m.accept("wx_station", "+ weather station");
		m.accept("station_id", "Station ID:");
		m.accept("callsign", "Station name or callsign");
		m.accept("callsign_hint", "Name / callsign");
		m.accept("duck", "Program under mic:");
		m.accept("mic", "MIC (hold)");
	}
}
