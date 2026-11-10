package com.arrl.radiocraft.datagen.lang;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.function.BiConsumer;

/**
 * Distress alerting (2026-11-09): the distress beacon, the DSC sounds, and the DISTRESS program (keys under
 * {@code screen.radiocraft.computer.distress.} and its errors). The marine radio's own DSC keys show their help as
 * the panel's other keys do, in MarineRadioScreen.
 */
public final class DistressLang {

	private DistressLang() {}

	public static void add(LanguageProvider provider) {
		provider.addItem(RadiocraftItems.DISTRESS_BEACON, "Distress Beacon");
		provider.addItem(RadiocraftItems.SPENT_DISTRESS_BEACON, "Spent Distress Beacon");
		provider.add(Radiocraft.translationKey("tooltip", "distress_beacon"), "Sends your position on 161.975 MHz every 50 s, and a homing tone a direction finder can follow. A commercial VHF radio on 161.975 hears it, and the DISTRESS program through one.");
		provider.add(Radiocraft.translationKey("tooltip", "distress_beacon.one_use"), "One use: once on, it cannot be switched off, and lasts one day.");
		provider.add(Radiocraft.translationKey("tooltip", "distress_beacon.safe"), "Right click to open the arming cover.");
		provider.add(Radiocraft.translationKey("tooltip", "distress_beacon.armed"), "Armed: hold right click to switch it on. Sneak and right click to shut the cover.");
		provider.add(Radiocraft.translationKey("tooltip", "distress_beacon.active"), "TRANSMITTING: %s left");
		provider.add(Radiocraft.translationKey("tooltip", "distress_beacon.owner"), "Owner: %s");
		provider.add(Radiocraft.translationKey("tooltip", "spent_distress_beacon"), "Its battery is flat. A beacon is used once.");
		provider.add("subtitles.radiocraft.dsc_alarm", "DSC distress alarm");
		provider.add("subtitles.radiocraft.dsc_beep", "DSC beep");

		BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("app.distress", "Distress");
		s.accept("app.distress.description", "DSC distress calls and distress beacons on a map: acknowledge them, print where they are.");
		s.accept("distress.title", "DISTRESS - DSC channel 70 and beacons");
		s.accept("distress.title_mmsi", "DISTRESS - DSC channel 70 and beacons - your MMSI %s");
		s.accept("distress.keys", "Up/Dn=Pick  F5=ACK  F6=Print  F7=Send SOS  F8=Cancel SOS  Esc=Exit");
		s.accept("distress.map", "Map");
		s.accept("distress.alerts", "Alerts");
		s.accept("distress.reading", "Reading the radio...");
		s.accept("distress.no_radio", "No marine or commercial VHF radio cabled.");
		s.accept("distress.no_radio_hint", "Touch one to the computer, or run a data wire.");
		// What it hears through (2026-11-09).
		s.accept("distress.hear.dsc", "DSC: %s marine radio(s)");
		s.accept("distress.hear.dsc_off", "DSC: the marine radio is off");
		s.accept("distress.hear.dsc_none", "DSC: no marine radio cabled");
		s.accept("distress.hear.beacons", "BEACONS: %s radio(s) on 161.975");
		s.accept("distress.hear.beacons_tune", "BEACONS: no commercial radio on 161.975");
		s.accept("distress.hear.beacons_off", "BEACONS: the commercial radio is off");
		s.accept("distress.hear.beacons_none", "BEACONS: no commercial VHF radio cabled");
		// Sending an SOS (2026-11-09).
		s.accept("distress.sos.head", "F7 SOS");
		s.accept("distress.sos.marine", "MARINE");
		s.accept("distress.sos.personal", "PERSONAL");
		s.accept("distress.sos.ready", "ready");
		s.accept("distress.sos.marine.line", "MARINE SOS - DSC distress call on channel 70");
		s.accept("distress.sos.personal.line", "PERSONAL SOS - beacon alert on 161.975 MHz");
		s.accept("distress.sos.title", "Send SOS");
		s.accept("distress.sos.pick_kind", "Which distress alert?");
		s.accept("distress.sos.pick_nature", "Nature of distress:");
		s.accept("distress.sos.confirm_title.marine", "SEND MARINE SOS?");
		s.accept("distress.sos.confirm_title.personal", "SEND PERSONAL SOS?");
		s.accept("distress.sos.confirm.marine", "A DSC distress call (%s) goes out on channel 70 from the marine radio, with its position and your MMSI, and repeats every 4 minutes until a station acknowledges it. The radio goes to channel 16 for your MAYDAY. Send it?");
		s.accept("distress.sos.confirm.personal", "A distress alert (%s) goes out on 161.975 MHz from the commercial radio, with its position and your name, every 50 seconds until you cancel it here. Send it?");
		s.accept("distress.sos.sent.marine", "MARINE SOS sent on channel 70. Give your MAYDAY on channel 16.");
		s.accept("distress.sos.sent.personal", "PERSONAL SOS on the air on 161.975 MHz");
		s.accept("distress.sos.cancel_title", "Cancel SOS");
		s.accept("distress.sos.pick_cancel", "Cancel which?");
		s.accept("distress.sos.cancel_confirm.marine", "Send a cancel for your DSC distress call? Every station that heard it marks it cancelled.");
		s.accept("distress.sos.cancel_confirm.personal", "Stop the personal SOS and send a cancel on 161.975 MHz?");
		s.accept("distress.sos.cancelled.marine", "DSC distress call cancelled");
		s.accept("distress.sos.cancelled.personal", "Personal SOS cancelled");
		s.accept("distress.out.dsc", "MARINE SOS ON THE AIR (%s), repeat in %s");
		s.accept("distress.out.dsc_acked", "MARINE SOS (%s) ACKNOWLEDGED by %s");
		s.accept("distress.out.personal", "PERSONAL SOS ON THE AIR (%s), repeat in %s");
		s.accept("distress.out.personal_stopped.no_power", "PERSONAL SOS STOPPED: its radio lost power");
		s.accept("distress.out.personal_stopped.unlinked", "PERSONAL SOS STOPPED: its radio was uncabled");
		s.accept("distress.out.cancel", "F8=Cancel");
		s.accept("distress.head", "WHO       NATURE   AGE BRG DIST STATE");
		s.accept("distress.none", "No distress alerts heard.");
		s.accept("distress.more", "and %s older");
		s.accept("distress.pick", "Up and Down pick an alert.");
		s.accept("distress.detail_dsc", "DSC DISTRESS from MMSI %s");
		s.accept("distress.detail_beacon", "BEACON of %s");
		s.accept("distress.detail_nature", "Nature: %s");
		s.accept("distress.detail_where", "BRG %s   %s blocks   heard %s s ago");
		s.accept("distress.ack_by", "by %s");
		s.accept("distress.state.active", "ACTIVE");
		s.accept("distress.state.acknowledged", "ACKNOWLEDGED");
		s.accept("distress.state.cancelled", "CANCELLED");
		s.accept("distress.state.spent", "SPENT");
		s.accept("distress.state_short.active", "ACTIVE");
		s.accept("distress.state_short.acknowledged", "ACKED");
		s.accept("distress.state_short.cancelled", "CANCELLED");
		s.accept("distress.state_short.spent", "SPENT");
		s.accept("err.distress_no_radio", "No marine or commercial VHF radio cabled to the computer");
		s.accept("err.distress_no_marine", "no marine radio cabled");
		s.accept("err.distress_marine_off", "the marine radio is off");
		s.accept("err.distress_marine_busy", "a DSC distress call is already on (F8 cancels it)");
		s.accept("err.distress_no_commercial", "no commercial VHF radio cabled");
		s.accept("err.distress_commercial_off", "the commercial radio is off");
		s.accept("err.distress_personal_busy", "a personal SOS is already on (F8 cancels it)");
		s.accept("err.distress_nothing_to_cancel", "No SOS of yours is on the air");
		s.accept("err.distress_ack_unknown", "The radio has no such alert");
		s.accept("err.distress_ack_not_dsc", "A beacon cannot be acknowledged: only DSC calls can");
		s.accept("err.distress_ack_not_active", "That call is already acknowledged or cancelled");
		s.accept("err.distress_ack_no_power", "No marine radio that heard it is on");
	}
}
