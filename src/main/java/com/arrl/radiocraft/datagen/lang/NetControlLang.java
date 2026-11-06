package com.arrl.radiocraft.datagen.lang;

import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.function.BiConsumer;

/** The Net Control logger (computer batch 3C): keys under {@code screen.radiocraft.computer.netlog.} and its errors. */
public final class NetControlLang {

	private NetControlLang() {}

	public static void add(LanguageProvider provider) {
		BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("app.netlog", "Net Control");
		s.accept("app.netlog.description", "Run a directed net: check-ins, roll call, a printed net report, check-ins logged.");
		s.accept("netlog.title", "Net Control - %s  [%s]");
		s.accept("netlog.title_none", "Net Control  [%s]");
		s.accept("netlog.keys", "Ins=Check in O=Out T=Tfc S=ST R=Recheck N=Next F4=New F5=Print F6=Save F7=Setup F8=Close F9=Remind");
		s.accept("netlog.header", "Freq %s %s   NCS %s   %s");
		s.accept("netlog.state_open", "OPEN since %sZ");
		s.accept("netlog.state_closed", "CLOSED at %sZ");
		s.accept("netlog.state_none", "No net open: F4 starts one");
		s.accept("netlog.col.call", "CALL");
		s.accept("netlog.col.name", "NAME");
		s.accept("netlog.col.location", "LOCATION");
		s.accept("netlog.col.in", "IN");
		s.accept("netlog.col.out", "OUT");
		s.accept("netlog.col.flags", "FLAGS");
		s.accept("netlog.empty", "No check-ins. F4 sets up and opens a net, F3 opens a saved one.");
		s.accept("netlog.empty_open", "The net is open. Ins checks a station in.");
		s.accept("netlog.status", "Check-ins %s   still in %s   with traffic %s");
		s.accept("netlog.name", "Net name:");
		s.accept("netlog.freq", "Frequency (MHz):");
		s.accept("netlog.mode", "Mode:");
		s.accept("netlog.ncs", "Net control (NCS):");
		s.accept("netlog.new_title", "New net");
		s.accept("netlog.setup_title", "Net setup");
		s.accept("netlog.setup_hint", "The net report and the logged contacts use these.");
		s.accept("netlog.need_name", "The net needs a name");
		s.accept("netlog.opened", "Net %s open, saved as %s");
		s.accept("netlog.call", "Callsign:");
		s.accept("netlog.op", "Name:");
		s.accept("netlog.location", "Location or grid:");
		s.accept("netlog.traffic", "Traffic (Y/N):");
		s.accept("netlog.short", "Short time (Y/N):");
		s.accept("netlog.checkin_title", "Check in");
		s.accept("netlog.checkin_hint", "A station already in is rechecked.");
		s.accept("netlog.edit_title", "Check-in");
		s.accept("netlog.need_call", "A callsign, please");
		s.accept("netlog.full", "The list holds %s stations");
		s.accept("netlog.result.added", "%s checked in (%s on the list)");
		s.accept("netlog.result.rechecked", "%s rechecked");
		s.accept("netlog.result.back_in", "%s back in the net");
		s.accept("netlog.nobody_in", "Nobody is checked in");
		s.accept("netlog.calling", "Roll call: %s %s");
		s.accept("netlog.printed", "Net report printed on the scanner");
		s.accept("netlog.report", "Net report");
		s.accept("netlog.saved_report", "Net report saved as %s");
		s.accept("netlog.not_open", "The net is not open");
		s.accept("netlog.close_title", "Close the net");
		s.accept("netlog.close_ask", "Close the net and check everyone out? The %s check-ins can go in the logbook (LOGBOOK.LOG) as contacts.");
		s.accept("netlog.close_log", "Close and log");
		s.accept("netlog.close_only", "Close only");
		s.accept("netlog.closed", "Net closed");
		s.accept("netlog.logged", "Net closed: %s contacts logged in LOGBOOK.LOG");
		s.accept("netlog.need_scheduler", "Install the Scheduler (SCHEDULE) for net reminders");
		s.accept("netlog.remind_title", "Net reminder");
		s.accept("netlog.remind_prompt", "When (at 20:00, at 8:05p real, every 60):");
		s.accept("netlog.remind_bad", "Not a time the Scheduler knows");
		s.accept("netlog.reminder_set", "Scheduler reminder set: %s");
		s.accept("netlog.remove_title", "Remove");
		s.accept("netlog.remove", "Take %s off the list?");
		s.accept("netlog.open_title", "Open a net");
		s.accept("err.net_nobody", "Nobody checked in");
		s.accept("err.log_full", "The logbook is full");
	}
}
