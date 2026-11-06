package com.arrl.radiocraft.datagen.lang;

import com.arrl.radiocraft.Radiocraft;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** The scheduler's words (computer batch 3A, 2026-11-06), under {@code screen.radiocraft.computer.sched.} and its errors. */
public final class SchedulerLang {

	private SchedulerLang() {}

	public static void add(LanguageProvider provider) {
		provider.add(Radiocraft.translationKey("message", "computer.reminder"), "[%s] %s");
		java.util.function.BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("app.scheduler", "Scheduler");
		s.accept("app.scheduler.description", "Timed jobs with nobody at the screen: station ID, WEFAX broadcasts, beacons, net reminders.");
		s.accept("sched.title", "Scheduler - %s of %s jobs");
		s.accept("sched.keys", "Ins=Add  Enter=Edit  Del=Delete  Spc=On/Off  F5=Run now  Esc=Leave");
		s.accept("sched.station", "Station %s on %s");
		s.accept("sched.radio_off", "no radio on");
		s.accept("sched.no_interface", "No radio interface on this computer: the jobs that transmit will fail");
		s.accept("sched.look_only", "Only the owner changes jobs");
		s.accept("sched.col.on", "On");
		s.accept("sched.col.what", "What");
		s.accept("sched.col.when", "When");
		s.accept("sched.col.next", "Next");
		s.accept("sched.col.details", "Details");
		s.accept("sched.on", "on");
		s.accept("sched.off", "off");
		s.accept("sched.now", "now");
		s.accept("sched.every_min", "every %s min");
		s.accept("sched.every_h", "every %s h");
		s.accept("sched.daily_game", "%s world");
		s.accept("sched.daily_real", "%s real");
		s.accept("sched.kind.station_id", "Station ID");
		s.accept("sched.kind.wefax", "WEFAX chart");
		s.accept("sched.kind.beacon", "APRS beacon");
		s.accept("sched.kind.reminder", "Reminder");
		s.accept("sched.details.id", "callsign in Morse");
		s.accept("sched.details.beacon", "position, APRS comment");
		s.accept("sched.reading", "Reading the schedule...");
		s.accept("sched.empty", "No jobs yet.");
		s.accept("sched.empty_hint", "Ins adds one: a station ID every 10 minutes, a chart at 06:00, a beacon every 30, a net reminder at 20:00.");
		s.accept("sched.log", "Last runs");
		s.accept("sched.log_empty", "Nothing has run yet.");
		s.accept("sched.done", "done");
		s.accept("sched.ran_now", "Ran it now");
		s.accept("sched.notice", "Reminder: %s");
		s.accept("sched.add_title", "Add a job");
		s.accept("sched.add_which", "What should the computer do?");
		s.accept("sched.add_kind", "Add: %s");
		s.accept("sched.edit_kind", "Edit: %s");
		s.accept("sched.form_hint", "When: every 30, every 2h, at 20:00 (the world's clock) or at 20:00 real (the server's).");
		s.accept("sched.f.when", "When");
		s.accept("sched.f.file", "Chart (.IMG)");
		s.accept("sched.f.text", "Text");
		s.accept("sched.f.to", "To (blank=here)");
		s.accept("sched.bad_when", "Not a time: try every 30, at 20:00 or at 8:00p real");
		s.accept("sched.delete_title", "Delete");
		s.accept("sched.delete", "Delete job %s (%s)?");
		s.accept("err.not_owner_sched", "Only the computer's owner (or an operator) can change its schedule");
		s.accept("err.sched_full", "The schedule holds %s jobs: delete one first");
		s.accept("err.sched_no_job", "That job is gone");
		s.accept("err.sched_need_image", "A WEFAX job needs a chart: a .IMG file on C:");
		s.accept("err.sched_need_text", "A reminder needs its text");
		s.accept("err.sched_need_wefax", "Needs the WEFAX program installed");
		s.accept("err.sched_need_aprs", "Needs the APRS program installed");
		s.accept("err.sched_no_transmit", "No radio would send: a radio interface's transmit switch is off, or its radio is a receiver");
		s.accept("err.sched_nobody", "Nobody near the computer to remind");
	}
}
