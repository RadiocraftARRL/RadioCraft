package com.arrl.radiocraft.datagen.lang;

import com.arrl.radiocraft.common.qsl.QslArt;
import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.Map;
import java.util.function.BiConsumer;

/** QSL cards (computer batch 3C): the card and album items, their screens, and the QSL Designer program. */
public final class QslLang {

	private QslLang() {}

	public static void add(LanguageProvider provider) {
		provider.add("item.radiocraft.qsl_card", "QSL Card");
		provider.add("item.radiocraft.qsl_card.printout", "QSL card of %s");
		provider.add("item.radiocraft.qsl_album", "QSL Album");
		provider.add("tooltip.radiocraft.qsl_card.from", "From %s");
		provider.add("tooltip.radiocraft.qsl_card.to", "To %s");
		provider.add("tooltip.radiocraft.qsl_card.date", "%s");
		provider.add("tooltip.radiocraft.qsl_card.band", "%s %s");
		provider.add("tooltip.radiocraft.qsl_card.signed", "Signed by %s");
		provider.add("tooltip.radiocraft.qsl_card.unsigned", "Not signed yet: right click to finish it");
		provider.add("tooltip.radiocraft.qsl_card.blank", "Blank: right click to fill it in. Sneak and right click to turn it over.");
		provider.add("tooltip.radiocraft.qsl_card.copy", "A copy (fax or print)");
		provider.add("tooltip.radiocraft.qsl_album", "%s of %s cards. Right click with cards to put them in; right click to page through.");
		provider.add("message.radiocraft.qsl.signed", "This card is signed: it cannot be changed");
		provider.add("message.radiocraft.qsl.need_to", "Write who the card is for before signing it");
		provider.add("message.radiocraft.qsl.need_from", "Write your callsign on the card before signing it");
		provider.add("message.radiocraft.qsl.no_card", "Hold the QSL card in your hand");
		provider.add("screen.radiocraft.qsl_fill.title", "QSL Card");
		provider.add("screen.radiocraft.qsl_fill.hint", "The date and time go in when you save. Signing locks the card.");
		provider.add("screen.radiocraft.qsl_fill.done", "Done");
		provider.add("screen.radiocraft.qsl_fill.sign", "Sign");
		provider.add("screen.radiocraft.qsl_fill.from", "Your callsign");
		provider.add("screen.radiocraft.qsl_fill.to", "To radio");
		provider.add("screen.radiocraft.qsl_fill.band", "Band");
		provider.add("screen.radiocraft.qsl_fill.freq", "MHz");
		provider.add("screen.radiocraft.qsl_fill.mode", "Mode");
		provider.add("screen.radiocraft.qsl_fill.rst", "Report");
		provider.add("screen.radiocraft.qsl_fill.notes", "Notes");
		provider.add("screen.radiocraft.qsl_album.title", "QSL Album");
		provider.add("screen.radiocraft.qsl_album.flip", "Turn over");
		provider.add("screen.radiocraft.qsl_album.take", "Take out");
		provider.add("screen.radiocraft.qsl_album.empty", "No cards yet: right click the album with cards in your inventory");
		provider.add("screen.radiocraft.qsl_album.page", "Card %s of %s");

		BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("app.qsl", "QSL Designer");
		s.accept("app.qsl.description", "Design QSL cards, print runs of blank cards, and print the cards you receive.");
		s.accept("qsl.title", "QSL Designer");
		s.accept("qsl.keys", "Ins=New  Enter=Edit/View  Tab=Turn over  F5=Print  F6=Scan card  F7=Send to BBS  Del=Delete");
		s.accept("qsl.edit_keys", "Enter=Change  Del=No picture  Tab=Turn over  Esc=Back");
		s.accept("qsl.view_keys", "Tab=Turn over  Esc=Back");
		s.accept("qsl.edit_title", "QSL Designer - %s");
		s.accept("qsl.edit_hint", "Every change is saved. F5 in the list prints blank cards of it.");
		s.accept("qsl.view_hint", "A card that was filled in: it can be printed (a copy) but not changed.");
		s.accept("qsl.files", "Templates and cards (%s)");
		s.accept("qsl.empty", "No QSL files. Ins designs a template.");
		s.accept("qsl.kind.template", "template");
		s.accept("qsl.kind.card", "card from %s");
		s.accept("qsl.front", "Front");
		s.accept("qsl.back", "Back");
		s.accept("qsl.showing_front", "Showing the front. Tab turns the card over.");
		s.accept("qsl.showing_back", "Showing the back. Tab turns the card over.");
		s.accept("qsl.received", "QSL card received: %s from %s");
		s.accept("qsl.new_title", "New template");
		s.accept("qsl.new_prompt", "File name:");
		s.accept("qsl.exists", "%s already exists");
		s.accept("qsl.print_title", "Print cards");
		s.accept("qsl.print_prompt", "How many blank cards (1 to 64)? One paper or photo paper each.");
		s.accept("qsl.print_bad", "From 1 to 64 cards");
		s.accept("qsl.printed", "%s cards printed: take them from the scanner");
		s.accept("qsl.scanned", "The card on the glass is saved as %s");
		s.accept("qsl.bbs_unsigned", "Only a signed card can go to a BBS: scan a signed card first (F6)");
		s.accept("qsl.bbs_title", "Send to a Packet BBS");
		s.accept("qsl.bbs_prompt", "The BBS callsign (W1BTR-1). It keeps the card for %s.");
		s.accept("qsl.bbs_sent", "Card sent to %s");
		s.accept("qsl.delete_title", "Delete");
		s.accept("qsl.delete", "Delete %s?");
		s.accept("qsl.not_template", "A card that was filled in cannot be changed");
		s.accept("qsl.pick_colour", "Pick a colour");
		s.accept("qsl.pick_picture", "A picture for the card");
		s.accept("qsl.call_prompt", "The callsign printed large (empty: a card anyone writes their call on):");
		s.accept("qsl.text_prompt", "Text:");
		s.accept("qsl.blank_call", "(blank)");
		s.accept("qsl.picture_yes", "yes");
		s.accept("qsl.picture_none", "none");
		s.accept("qsl.side_left", "left");
		s.accept("qsl.side_right", "right");
		s.accept("qsl.layout_table", "table");
		s.accept("qsl.layout_lines", "lines");
		s.accept("qsl.border.0", "none");
		s.accept("qsl.border.1", "thin");
		s.accept("qsl.border.2", "double");
		s.accept("qsl.border.3", "thick");
		s.accept("qsl.need_designer", "Install the QSL Designer (QSL) to view and print QSL cards");
		String[][] properties = { { "call", "Callsign" }, { "line1", "Line 1" }, { "line2", "Line 2" }, { "line3", "Line 3" }, { "line4", "Line 4" },
				{ "background", "Background" }, { "border", "Border" }, { "border_style", "Border style" }, { "call_colour", "Call colour" },
				{ "text_colour", "Text colour" }, { "picture", "Picture" }, { "picture_side", "Picture side" }, { "back_header", "Back heading" },
				{ "back_layout", "Back layout" }, { "back_line1", "Back line 1" }, { "back_line2", "Back line 2" } };
		for(String[] p : properties)
			s.accept("qsl.p." + p[0], p[1]);
		for(Map.Entry<String, Byte> colour : QslArt.COLOURS.entrySet()) {
			String words = colour.getKey().replace('_', ' ');
			s.accept("qsl.colour." + colour.getKey(), Character.toUpperCase(words.charAt(0)) + words.substring(1));
		}
		s.accept("err.qsl_not_a_card", "That is not a QSL card file (.QSL)");
		s.accept("err.qsl_glass_no_card", "No QSL card on the scanner's glass");
		s.accept("err.qsl_need_bbs", "Type the BBS's callsign");
	}
}
