package com.arrl.radiocraft.datagen.lang;

import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.function.BiConsumer;

/** Rig control and the waterfall (computer batch 3B): keys under {@code screen.radiocraft.computer.rig.} and its errors. */
public final class RigControlLang {

	private RigControlLang() {}

	public static void add(LanguageProvider provider) {
		BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("app.rigctl", "Rig Control");
		s.accept("app.rigctl.description", "Tune, scan and watch the band: the radio from the keyboard, with a live waterfall.");
		s.accept("rig.title", "Rig Control  %s");
		s.accept("rig.keys", "<>=Tune  F=Freq  PgUp/Dn=Band  M=Mode  P=Pwr  T=PTT  S=Scan  F3=Mem  Z=Zoom  Esc=Exit");
		s.accept("rig.hint", "Ctrl+Left/Right: next signal  Up/Down: memories  Q: squelch  Space: hold  X: stop scan  R: radio");
		s.accept("rig.help", "Click the waterfall to tune there; Shift+Left/Right tunes ten steps. F4 stores the dial, F5 loads memories.");
		s.accept("rig.station", "CAT through the radio interface. Station %s, %s radio(s) on the bus.");
		s.accept("rig.need_interface", "Put a Radio Interface beside the computer (or on its data wire) and a radio beside that.");
		s.accept("rig.no_signal", "No signal that way on the waterfall");
		s.accept("rig.memory_tuned", "Memory %s: %s");
		s.accept("rig.freq_title", "Frequency");
		s.accept("rig.freq_prompt", "Tune to (MHz):");
		s.accept("rig.scan_title", "Scan a range");
		s.accept("rig.scan_help", "Steps at the radio's tuning step and stops on a signal over the squelch.");
		s.accept("rig.scan_from", "From MHz");
		s.accept("rig.scan_to", "To MHz");
		s.accept("rig.store_title", "Store memory");
		s.accept("rig.store_prompt", "Name for this frequency (added to %s):");
		s.accept("rig.stored", "Stored in %s");
		s.accept("rig.load_title", "Memory channels from");
		s.accept("rig.one_radio", "Only one radio on the radio interface");
		s.accept("rig.pick_title", "Radio");
		s.accept("rig.pick_prompt", "Which radio to control:");
		s.accept("rig.low_band", "Low band");
		s.accept("rig.channelized", "channel radio");
		s.accept("rig.power_on", " PWR ON ");
		s.accept("rig.power_off", " PWR OFF");
		s.accept("rig.zoom", "x%s");
		s.accept("rig.squelch", "SQL %s");
		s.accept("rig.scanning", "SCANNING");
		s.accept("rig.scan_stopped", "STOPPED ON SIGNAL");
		s.accept("rig.scan_held", "HELD");
		s.accept("rig.scan_memory", "MEM");
		s.accept("rig.scan_range", "RANGE");
		s.accept("rig.memories", "%s memories (%s)");
		s.accept("rig.waiting", "Waiting for the band scope...");
		s.accept("err.rig_no_radio", "No radio on the radio interface");
		s.accept("err.rig_power_op_only", "Only an operator may switch this radio");
		s.accept("err.rig_channelized", "A channel radio: program it with CPS");
		s.accept("err.rig_out_of_range", "The radio does not tune there");
		s.accept("err.rig_one_band", "This radio has one band");
		s.accept("err.rig_no_more_bands", "No more bands that way");
		s.accept("err.rig_one_mode", "This radio has one mode");
		s.accept("err.rig_receiver", "A receiver has no transmitter");
		s.accept("err.rig_out_of_band", "Out of band: the transmitter will not key");
		s.accept("err.rig_no_memories", "No memory channels this radio tunes (F5 loads a .FRQ)");
		s.accept("err.rig_bad_range", "Nothing to scan between those frequencies");
		s.accept("err.rig_not_scanning", "Not scanning");
		s.accept("err.rig_memories_full", "The memory list is full");
	}
}
