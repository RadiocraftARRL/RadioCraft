package com.arrl.radiocraft.datagen.lang;

import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** The weather alert blocks' words (1.12 weather alerts, 2026-11-06): the generator, the receiver and their sounds. */
public final class WeatherAlertLang {

	private WeatherAlertLang() {}

	public static void add(LanguageProvider provider) {
		provider.addBlock(RadiocraftBlocks.WEATHER_ALERT_GENERATOR, "Simple Weather Alert Generator");
		provider.addBlock(RadiocraftBlocks.WEATHER_ALERT_RECEIVER, "Simple Weather Alert Receiver");
		provider.add("tooltip.radiocraft.weather_alert_generator", "Touch it to an APRS station (an H19 or a radio computer running APRS) that hears weather beacons, and it broadcasts weather alerts for their area on 162.500 MHz: one station warns of what is at it, two (150+ blocks apart) of storms heading in, three add watches. Needs power and a VHF antenna on coax.");
		provider.add("tooltip.radiocraft.weather_alert_receiver", "Battery weather radio for 162.500 MHz: flashes, beeps, speaks and gives one redstone pulse on an alert for where it stands. Shift + right-click with a Small Alkaline (or NiCd) Battery to change it.");

		java.util.function.BiConsumer<String, String> g = (key, text) -> provider.add("message.radiocraft.weather_alert_generator." + key, text);
		g.accept("no_power", "No power: run a power wire to it.");
		g.accept("no_aprs", "No APRS station: touch it to an H19 APRS terminal or a radio computer with APRS installed, or cable it to one with data wire.");
		g.accept("too_few", "No weather station heard yet: it needs at least one APRS weather beacon (more, each %s+ blocks apart, do more).");
		g.accept("no_antenna", "No antenna: coax a VHF antenna to it.");
		g.accept("two_antennas", "More than one antenna on its coax: it can send through only one.");
		g.accept("ready", "On the air on 162.500 MHz: %s, an area about %s blocks across.");
		g.accept("tier.1", "1 station, warnings for what is at it now");
		g.accept("tier.2", "2 stations, warnings for storms heading in too");
		g.accept("tier.3", "3 stations, warnings, watches and tropical statements");
		g.accept("next.1", "Next: one weather station.");
		g.accept("next.2", "Next: a second station %s+ blocks from the first would warn of storms heading in.");
		g.accept("next.3", "Next: a third station %s+ blocks from both would add watches and tropical statements.");
		g.accept("station", "%s: %s blocks%s, %s min ago");
		g.accept("station.too_close", "; within %s blocks of another, adds no reach");
		g.accept("station.stale", "; not heard for over %s min, does not count");
		g.accept("station.same_call", "; this callsign is heard from two places: give each station its own SSID (W1ABC-1, -2...)");
		g.accept("more", "...and %s more");
		g.accept("no_alerts", "No alerts.");

		java.util.function.BiConsumer<String, String> r = (key, text) -> provider.add("message.radiocraft.weather_alert_receiver." + key, text);
		r.accept("alert", "WEATHER ALERT: %s");
		r.accept("silenced", "Alert silenced");
		r.accept("no_battery", "No battery: shift + right-click it with a Small Alkaline (or NiCd) Battery.");
		r.accept("flat", "The battery is flat.");
		r.accept("battery", "Battery %s%% (about %s days left).");
		r.accept("battery_low", "Battery low: %s%% (about %s days left).");
		r.accept("station", "Listening to a weather alert generator %s blocks away.");
		r.accept("no_station", "No weather alert generator heard on 162.500 MHz yet.");
		r.accept("last", "Last alert, %s minutes ago: %s");

		provider.add("config.jade.plugin_radiocraft.weather_alert", "Weather Alerts");
		provider.add("subtitles.radiocraft.weather_alert.tone", "Weather alert tone");
		provider.add("subtitles.radiocraft.weather_alert.voice", "Weather radio speaks");
	}
}
