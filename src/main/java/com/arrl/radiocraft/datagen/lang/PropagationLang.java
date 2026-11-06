package com.arrl.radiocraft.datagen.lang;

import net.neoforged.neoforge.common.data.LanguageProvider;

/** The Propagation Predictor's words (computer apps 2, 2026-11-06): keys under {@code screen.radiocraft.computer.prop.}. */
public final class PropagationLang {

	private PropagationLang() {}

	public static void add(LanguageProvider provider) {
		java.util.function.BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("prop.title", "PREDICT - Propagation Predictor");
		s.accept("prop.keys", "Tab=Target  Enter=Predict  F5=Callsigns  F6=Print  F7=Save  Esc=Exit");
		s.accept("prop.target", "Target:");
		s.accept("prop.mode_coords", "Coordinates");
		s.accept("prop.mode_call", "Callsign");
		s.accept("prop.mode_bearing", "Distance and bearing");
		s.accept("prop.field_coords", "X and Z (e.g. 1200 -340):");
		s.accept("prop.field_call", "Callsign:");
		s.accept("prop.field_bearing", "Blocks and degrees (4000 90):");
		s.accept("prop.enter", "Enter predicts");
		s.accept("prop.hint", "Pick a target with Tab, type it and press Enter: each HF band for the next 24 hours.");
		s.accept("prop.hint2", "F5 lists the callsigns the computer can find (players online, APRS stations heard).");
		s.accept("prop.bad_coords", "Type the X and Z coordinates, two whole numbers");
		s.accept("prop.bad_call", "Type a callsign");
		s.accept("prop.bad_bearing", "Type the distance in blocks and the bearing in degrees (0 north, 90 east)");
		s.accept("prop.no_targets", "No callsigns known: no players with a callsign online, no APRS stations heard");
		s.accept("prop.kind_aprs", "APRS");
		s.accept("prop.pick_title", "Callsigns");
		s.accept("prop.pick_message", "Call, how it is known, blocks and bearing:");
		s.accept("prop.low_band", "Low bnd");
		s.accept("prop.no_event", "No solar event yet");
		s.accept("prop.to", "To %s: %s blocks, bearing %s");
		s.accept("prop.sky", "Sky: %s, noise %s percent.");
		s.accept("prop.col_band", "Band");
		s.accept("prop.print_header", "HF PROPAGATION PREDICTION");
		s.accept("prop.print_title", "Prediction");
		s.accept("prop.legend", "G good  F fair  P poor (data and CW)  - closed  c CW only  L line of sight");
		s.accept("prop.legend2", "lower case: sky not known yet  ** day  .. night  dipoles, full power, no hills");
		s.accept("prop.best_none", "No band is open to the target now.");
		s.accept("prop.best_now", "Best band now: %s");
		s.accept("prop.best_hour", "%s best at %s:00");
		s.accept("err.prop_bad_distance", "Distance must be 1 to %s blocks");
		s.accept("err.prop_no_call", "Type a callsign");
		s.accept("err.prop_other_level", "%s is in another dimension: no path");
		s.accept("err.prop_unknown_call", "Cannot find %s: not online, and not an APRS station this computer heard");
	}
}
