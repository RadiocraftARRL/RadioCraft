package com.arrl.radiocraft.datagen.lang;

import com.arrl.radiocraft.common.init.RadiocraftItems;
import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.function.BiConsumer;

/** Commercial frequency licenses (2026-11-10): the items, their tooltips, messages, commands and the programmer's prompt. */
public final class LicenseLang {

	private LicenseLang() {}

	public static void add(LanguageProvider provider) {
		provider.addItem(RadiocraftItems.BLANK_VHF_LICENSE, "Blank Commercial VHF License");
		provider.addItem(RadiocraftItems.BLANK_LOW_BAND_LICENSE, "Blank Commercial Low Band License");
		provider.addItem(RadiocraftItems.COMMERCIAL_LICENSE, "Commercial Frequency License");
		provider.addItem(RadiocraftItems.COMMERCIAL_LICENSE_UPGRADE, "Commercial License Upgrade");
		provider.add("item.radiocraft.commercial_license.vhf", "Commercial VHF Frequency License");
		provider.add("item.radiocraft.commercial_license.low_band", "Commercial Low Band Frequency License");
		provider.add("item.radiocraft.commercial_license.copy", "%s (Copy)");

		BiConsumer<String, String> t = (key, text) -> provider.add("tooltip.radiocraft." + key, text);
		t.accept("blank_license", "Right-click to claim it: a random free frequency and a WQ callsign, in your name. Allows 10 mobiles and portables.");
		t.accept("commercial_license_upgrade", "Craft with a commercial license: the first adds a base (repeater, SCADA master or fire alarm controller) and a repeater input frequency; each one doubles the units and bases.");
		t.accept("commercial_license.callsign", "Call sign: %s");
		t.accept("commercial_license.holder", "Licensee: %s");
		t.accept("commercial_license.frequency", "Frequency: %s MHz");
		t.accept("commercial_license.input", "Repeater input: %s MHz");
		t.accept("commercial_license.telemetry", "Satellite telemetry: %s MHz");
		t.accept("commercial_license.units", "Units: %s, of which bases: %s");
		t.accept("commercial_license.issued", "Granted %s");
		t.accept("commercial_license.is_copy", "COPY: shows the license, but does not authorize programming");
		t.accept("commercial_license.upgrade_hint", "Craft with an empty map for a copy, or with a License Upgrade");

		BiConsumer<String, String> m = (key, text) -> provider.add("message.radiocraft.license." + key, text);
		m.accept("off", "Commercial licensing is turned off on this server.");
		m.accept("admin_only", "On this server, commercial licenses are issued by the operators.");
		m.accept("band_full", "Every frequency in that band is already licensed.");
		m.accept("granted", "Licensed: %s on %s MHz, for %s units. Carry the license (or put it on the radio computer's scanner) when programming radios.");

		BiConsumer<String, String> c = (key, text) -> provider.add("commands.radiocraft.license." + key, text);
		c.accept("granted", "Gave %s license %s on %s MHz");
		c.accept("unknown", "No license %s");
		c.accept("revoked", "Revoked license %s");
		c.accept("none", "No commercial licenses have been issued");
		c.accept("devices", "  Fire alarm and SCADA devices set to it: %s, of which bases (controllers, masters): %s");
		c.accept("info", "%s: %s, %s MHz (input %s), %s units, %s bases, granted %s");

		BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("cps.license_title", "License Check");
		s.accept("cps.license", "This codeplug transmits on %s MHz.");
		s.accept("cps.license_none", "No license that covers them is in your inventory or on the scanner. Confirm you are licensed to use these frequencies.");
		s.accept("cps.license_copy", "The scanner holds a copy of %s, which does not authorize programming, and no original you carry covers them. Confirm you are licensed to use these frequencies.");
		s.accept("cps.license_other", "License %s on the scanner does not cover them, nor does any license you carry. Confirm you are licensed to use these frequencies.");
		s.accept("cps.license_yes", "I am licensed");

		// The FCC program.
		s.accept("app.fcc", "FCC Monitor");
		s.accept("app.fcc.description", "Spectrum monitoring with a direction finder: who is licensed on the frequency it hears, and UNLICENSED when no one is.");
		s.accept("fcc.title", "FCC - Spectrum Monitoring");
		s.accept("fcc.keys", "F3=MHz  <>=Channel  PgUp/Dn=10 channels  F8=Clear log");
		s.accept("fcc.monitor", "Monitor");
		s.accept("fcc.log", "Signals heard");
		s.accept("fcc.reading", "Reading the direction finder...");
		s.accept("fcc.no_finder", "No direction finder.");
		s.accept("fcc.no_finder_hint", "Put one touching the computer,");
		s.accept("fcc.no_finder_hint2", "or run data wire to it.");
		s.accept("fcc.finder_off", "The direction finder is off.");
		s.accept("fcc.signal", "Signal");
		s.accept("fcc.bearing", "Bearing %s deg (+/- %s)");
		s.accept("fcc.no_bearing", "No bearing yet");
		s.accept("fcc.status", "License status:");
		s.accept("fcc.verdict.not_checked", "NOT CHECKED (licensing is off)");
		s.accept("fcc.verdict.amateur", "AMATEUR BAND");
		s.accept("fcc.verdict.cb", "CB: NO LICENSE NEEDED");
		s.accept("fcc.verdict.cb_amplified", "CB AMPLIFIED: ILLEGAL");
		s.accept("fcc.verdict.marine", "MARINE / WEATHER");
		s.accept("fcc.verdict.murs", "MURS: NO LICENSE NEEDED");
		s.accept("fcc.verdict.licensed", "LICENSED");
		s.accept("fcc.verdict.unlicensed", "UNLICENSED");
		s.accept("fcc.verdict.out_of_band", "OUT OF BAND");
		s.accept("fcc.short.not_checked", "-");
		s.accept("fcc.short.amateur", "AMATEUR");
		s.accept("fcc.short.cb", "CB");
		s.accept("fcc.short.cb_amplified", "CB AMP!");
		s.accept("fcc.short.marine", "MARINE");
		s.accept("fcc.short.murs", "MURS");
		s.accept("fcc.short.unlicensed", "UNLICENSED");
		s.accept("fcc.short.out_of_band", "OUT BAND");
		s.accept("fcc.licensee", "%s, licensed to %s");
		s.accept("fcc.unlicensed_on_air", "Someone is transmitting here with no license.");
		s.accept("fcc.cb_amplified_on_air", "A CB station is running a linear amplifier.");
		s.accept("fcc.unlicensed_quiet", "No license grants this frequency.");
		s.accept("fcc.log_head", "Ago  MHz       Who        Brg");
		s.accept("fcc.log_empty", "Nothing heard yet.");
		s.accept("fcc.freq_title", "Frequency");
		s.accept("fcc.freq_hint", "Frequency in MHz:");
		s.accept("fcc.freq_bad", "Type a frequency in MHz, like 154.4375");
		s.accept("fcc.help", "Tune the finder to a commercial\nfrequency. Bearings come as its\nmodules lock on: take them from\nmore than one place to find the\ntransmitter (DFIND).");
	}
}
