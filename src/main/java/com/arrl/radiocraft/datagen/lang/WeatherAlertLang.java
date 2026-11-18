package com.arrl.radiocraft.datagen.lang;

import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** The weather alert blocks' words (1.12 weather alerts, 2026-11-06): the generator, the receiver and their sounds. */
public final class WeatherAlertLang {

	private WeatherAlertLang() {}

	public static void add(LanguageProvider provider) {
		provider.addBlock(RadiocraftBlocks.WEATHER_ALERT_GENERATOR, "Simple Weather Alert Generator");
		provider.addBlock(RadiocraftBlocks.WEATHER_ALERT_RECEIVER, "Simple Weather Alert Receiver");
		provider.addBlock(RadiocraftBlocks.WEATHER_OBSERVER, "Weather Observer");
		// The Local Weather Station Display (2026-11-18).
		provider.addBlock(RadiocraftBlocks.WEATHER_DISPLAY, "Local Weather Station Display");
		provider.add("tooltip.radiocraft.weather_display", "A desk weather display: within 64 blocks of a Local Weather Station its red LCD steps through the temperature, humidity, pressure, wind and rain. Right click for them all.");
		provider.add("message.radiocraft.weather_display.no_station", "No Local Weather Station within 64 blocks.");
		provider.add("message.radiocraft.weather_display.readings", "%s °F, %s%% humidity, %s inHg, wind from %s at %s mph gusting %s, rain %s in last hour");
		// The Headless APRS TNC (2026-11-18).
		provider.addBlock(RadiocraftBlocks.HEADLESS_TNC, "Headless APRS TNC");
		provider.add("container.radiocraft.headless_tnc", "Headless APRS TNC");
		provider.add("tooltip.radiocraft.headless_tnc", "A packet modem with no screen: touch it to a radio (or cable it with data wire). It beacons (with a Local Weather Station's weather within 64 blocks), digipeats if you switch DIGI on, and feeds a Weather Alert Generator the beacons it hears. Next to an H19 it stands in for the Radio Interface (not for a computer or fax).");
		java.util.function.BiConsumer<String, String> t = (key, text) -> provider.add("screen.radiocraft.headless_tnc." + key, text);
		t.accept("front", "Front");
		t.accept("back", "Turn around");
		t.accept("callsign_hint", "The station's callsign; Enter saves it, empty takes yours. Each APRS station needs its own SSID (W1ABC-1, -2...).");
		t.accept("ssid_tip", "SSID: click the top to go up, the bottom to go down (or scroll). 0 is none.");
		t.accept("digi_tip", "DIGI: repeat the packets it hears (a digipeater).");
		t.accept("beacon_tip", "BEACON: send its position (and weather) every few minutes.");
		t.accept("turn_on", "Switch it on");
		t.accept("turn_off", "Switch it off: nothing in or out");
		t.accept("lamp.dcd", "DCD: a packet decoded");
		t.accept("lamp.ptt", "PTT: its radio is sending");
		t.accept("lamp.sta", "STA: a repeat waiting, or a beacon just sent");
		t.accept("lamp.con", "CON: an H19 is connected");
		t.accept("lamp.pwr", "PWR: on");
		provider.add("tooltip.radiocraft.weather_observer", "Touch it to a radio (or cable it with data wire) within 64 blocks of a Local Weather Station: every 5 minutes, and on a redstone pulse, it sends the station's readings on the radio's frequency. A Weather Alert Generator with a radio on that frequency counts it as a station. One callsign does for all your observers.");
		java.util.function.BiConsumer<String, String> o = (key, text) -> provider.add("message.radiocraft.weather_observer." + key, text);
		o.accept("no_callsign", "No callsign: right-click it once you have one.");
		o.accept("no_station", "No Local Weather Station within %s blocks.");
		o.accept("no_radio", "No radio: touch it to one, or cable it to one with data wire.");
		o.accept("radio_off", "Its radio is off.");
		o.accept("ready", "Sending as %s on %s MHz, from a Local Weather Station %s blocks away.");
		o.accept("last", "Last report %s min ago; every %s min.");
		provider.add("tooltip.radiocraft.weather_alert_generator", "Touch it to an APRS station (an H19 or a radio computer running APRS) that hears weather beacons, and it broadcasts weather alerts for their area on 162.500 MHz: one station warns of what is at it, two (150+ blocks apart) of storms heading in, three add watches. Needs power and a VHF antenna on coax.");
		provider.add("tooltip.radiocraft.weather_alert_receiver", "Battery weather radio for 162.500 MHz: flashes, beeps, speaks and gives one redstone pulse on an alert for where it stands. Shift + right-click with a Small Alkaline (or NiCd) Battery to change it.");

		java.util.function.BiConsumer<String, String> g = (key, text) -> provider.add("message.radiocraft.weather_alert_generator." + key, text);
		g.accept("no_power", "No power: run a power wire to it.");
		g.accept("no_source", "Nothing to hear weather stations with: touch it to an APRS station (an H19, a Headless TNC or a radio computer running APRS) or to a radio for Weather Observers, or cable it to one with data wire.");
		g.accept("observer", "%s observer");
		g.accept("too_few", "No weather station heard yet: it needs at least one APRS weather beacon or Weather Observer (more, each %s+ blocks apart, do more).");
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
