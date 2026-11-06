package com.arrl.radiocraft.datagen.lang;

import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** The weather alert blocks' words (1.12 weather alerts, 2026-11-06): the generator, the receiver and their sounds. */
public final class WeatherAlertLang {

	private WeatherAlertLang() {}

	public static void add(LanguageProvider provider) {
		provider.addBlock(RadiocraftBlocks.WEATHER_ALERT_GENERATOR, "Simple Weather Alert Generator");
		provider.addBlock(RadiocraftBlocks.WEATHER_ALERT_RECEIVER, "Simple Weather Alert Receiver");
		provider.add("tooltip.radiocraft.weather_alert_generator", "Touch it to an APRS station (an H19 or a radio computer running APRS) that hears 3 weather beacons 150+ blocks apart, and it broadcasts weather alerts for their area on 162.500 MHz. Needs power and a VHF antenna on coax.");
		provider.add("tooltip.radiocraft.weather_alert_receiver", "Battery weather radio for 162.500 MHz: flashes, beeps, speaks and gives one redstone pulse on an alert for where it stands. Shift + right-click with a Small Alkaline Battery to change it.");

		java.util.function.BiConsumer<String, String> g = (key, text) -> provider.add("message.radiocraft.weather_alert_generator." + key, text);
		g.accept("no_power", "No power: run a power wire to it.");
		g.accept("no_aprs", "No APRS station: touch it to an H19 APRS terminal or a radio computer with APRS installed, or cable it to one with data wire.");
		g.accept("too_few", "Hears %s weather beacons; it needs %s, each at least %s blocks from the others.");
		g.accept("too_close", "Hears %s weather beacons, but only %s of them are far enough apart: it needs %s, each at least %s blocks from the others.");
		g.accept("no_antenna", "No antenna: coax a VHF antenna to it.");
		g.accept("two_antennas", "More than one antenna on its coax: it can send through only one.");
		g.accept("ready", "On the air on 162.500 MHz: %s weather stations, an area about %s blocks across.");
		g.accept("no_alerts", "No alerts.");

		java.util.function.BiConsumer<String, String> r = (key, text) -> provider.add("message.radiocraft.weather_alert_receiver." + key, text);
		r.accept("alert", "WEATHER ALERT: %s");
		r.accept("silenced", "Alert silenced");
		r.accept("no_battery", "No battery: shift + right-click it with a Small Alkaline Battery.");
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
