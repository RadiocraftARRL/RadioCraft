package com.arrl.radiocraft.datagen.lang;

import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.function.BiConsumer;

/** FT8 (computer batch 3B): keys under {@code screen.radiocraft.computer.ft8.} and its errors. */
public final class Ft8Lang {

	private Ft8Lang() {}

	public static void add(LanguageProvider provider) {
		BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("app.ft8", "FT8");
		s.accept("app.ft8.description", "The weak-signal digital mode: 15 second slots, auto-sequenced contacts, logged for you.");
		s.accept("ft8.title", "FT8  %s %s  %s MHz");
		s.accept("ft8.keys", "Enter=Answer  F3=CQ  F4=Halt  F5=TX on/off  F6=Dial  F7=Offset  F8=Even/Odd  F9=Clear");
		s.accept("ft8.hint", "Click a CQ (green) or a call to you (red) to answer. Contacts go in LOGBOOK.LOG.");
		s.accept("ft8.columns", "Time    dB  Offset  Message");
		s.accept("ft8.empty", "Listening. Decodes appear at the end of each 15 second slot.");
		s.accept("ft8.listening", "Receiving FT8 %s");
		s.accept("ft8.off_dial", "The radio is not on an FT8 frequency: F6 tunes it to the band's (14.074 on 20m)");
		s.accept("ft8.even", "even");
		s.accept("ft8.odd", "odd");
		s.accept("ft8.tx_enable", "Enable TX");
		s.accept("ft8.slot_choice", "TX %s slots at %s Hz");
		s.accept("ft8.qsos", "Logged: %s");
		s.accept("ft8.next", "Next TX: %s");
		s.accept("ft8.last", "Last TX: %s");
		s.accept("ft8.step.idle", "Monitoring");
		s.accept("ft8.step.calling_cq", "Calling CQ");
		s.accept("ft8.step.replied", "Answered %s, waiting for a report");
		s.accept("ft8.step.report_sent", "Sent %s a report, waiting for R");
		s.accept("ft8.step.r_report_sent", "Sent %s R and a report, waiting for RR73");
		s.accept("ft8.step.rr73_sent", "Contact with %s complete: RR73 sent");
		s.accept("ft8.step.signoff_sent", "Contact with %s complete: 73 sent");
		s.accept("ft8.answering", "Answering %s in the next slot");
		s.accept("ft8.pick_first", "Pick a CQ in the list first");
		s.accept("ft8.offset_title", "TX offset");
		s.accept("ft8.offset_prompt", "Audio offset in Hz (%s to %s), a clear spot in the passband:");
		s.accept("ft8.logged", "Logged %s in LOGBOOK.LOG");
		s.accept("ft8.log_failed", "Not logged: %s");
		s.accept("ft8.tx_failed", "Did not transmit: %s");
		s.accept("err.ft8_cannot_answer", "That is not a station to answer");
		s.accept("err.ft8_no_dial", "This band has no FT8 frequency the radio tunes");
		s.accept("err.ft8_log_full", "The logbook is full");
		s.accept("err.sched_need_ft8", "FT8 is not installed");
		s.accept("sched.kind.ft8_cq", "FT8 CQ");
		s.accept("sched.details.ft8", "CQ on FT8 for %s min, contacts logged");
	}
}
