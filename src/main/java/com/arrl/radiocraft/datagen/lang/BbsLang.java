package com.arrl.radiocraft.datagen.lang;

import net.neoforged.neoforge.common.data.LanguageProvider;

/** The Packet BBS program's words (computer apps 2): {@code screen.radiocraft.computer.bbs.*} and its errors. */
public final class BbsLang {

	private BbsLang() {}

	public static void add(LanguageProvider provider) {
		java.util.function.BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("bbs.title", "PACKET BBS - %s");
		s.accept("bbs.tab_sysop", "SYSOP");
		s.accept("bbs.tab_terminal", "TERMINAL");
		s.accept("bbs.keys_sysop", "Enter=Read  N=New  B=Bulletin  Del=Delete  F7=Setup  Tab=Terminal  Esc=Exit");
		s.accept("bbs.keys_read", "Up/Dn=Scroll  Esc=Back");
		s.accept("bbs.keys_write", "^S=Post  Esc=Cancel");
		s.accept("bbs.keys_terminal", "Enter=Send  F7=Connect to  F8=Clear  PgUp/PgDn=Scroll  Tab=Sysop  Esc=Exit");
		s.accept("bbs.state_online", "Mailbox %s ONLINE: callers send it APRS messages.  %s of %s messages");
		s.accept("bbs.state_offline", "Mailbox %s OFFLINE (F7 to switch it on).  %s of %s messages");
		s.accept("bbs.columns", "  No.    From      To        Age    Subject");
		s.accept("bbs.no_messages", "No messages yet. N writes one; callers can send them too.");
		s.accept("bbs.activity", "Traffic");
		s.accept("bbs.delete_title", "Delete message");
		s.accept("bbs.delete_ask", "Delete message %s from the mailbox?");
		s.accept("bbs.deleted", "Message %s deleted");
		s.accept("bbs.compose_title", "New message");
		s.accept("bbs.bulletin_title", "New bulletin");
		s.accept("bbs.compose_hint", "Who it is for and its subject; the text comes next.");
		s.accept("bbs.to", "To call");
		s.accept("bbs.topic", "Topic");
		s.accept("bbs.subject", "Subject");
		s.accept("bbs.need_to", "Type the callsign it is for");
		s.accept("bbs.need_topic", "Type a topic (ALL, NET...)");
		s.accept("bbs.writing", "To %s: %s");
		s.accept("bbs.writing_bulletin", "Bulletin to %s: %s");
		s.accept("bbs.length", "%s of %s characters");
		s.accept("bbs.posted", "Message saved in the mailbox");
		s.accept("bbs.discard_title", "Cancel message");
		s.accept("bbs.discard_ask", "Throw this message away?");
		s.accept("bbs.setup_title", "Mailbox setup");
		s.accept("bbs.setup_hint", "The mailbox answers the station's callsign with this SSID (1 to 15): W1BTR-1. Online Y or N.");
		s.accept("bbs.ssid", "SSID");
		s.accept("bbs.online", "Online (Y/N)");
		s.accept("bbs.bad_ssid", "The SSID is a number from 1 to 15");
		s.accept("bbs.connect_title", "Connect to a BBS");
		s.accept("bbs.connect_hint", "The mailbox's callsign with its SSID (W1BTR-1). Type ? to it for its commands.");
		s.accept("bbs.not_connected", "Not connected: F7 to pick a BBS, then type its commands (? for help).");
		s.accept("bbs.connected", "Talking to %s: each line goes as an APRS message, its answers print below.");
		s.accept("err.bbs_not_sysop", "Only the computer's owner (or an operator) runs this mailbox");
		s.accept("err.bbs_no_message", "No such message");
		s.accept("err.bbs_no_recipient", "Type a callsign");
		s.accept("err.bbs_full", "The mailbox is full: delete some messages first");
	}
}
