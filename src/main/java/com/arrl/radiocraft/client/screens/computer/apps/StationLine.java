package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * What the radio programs (1.12 step 7) show about the station the computer is cabled to, from the {@code status} their
 * server sides answer (see {@code RadioStation.status}): the radio interface, the callsign, the radio on and its
 * frequency, or what is missing, in red. And times "ago" as a ham log shows them.
 */
public final class StationLine {

	private StationLine() {}

	/** The station in words, or what is missing. */
	public static String text(@Nullable CompoundTag status, boolean needsVhf) {
		if(status == null)
			return Dos.tr("station.reading");
		if(!status.getBoolean("interface"))
			return Dos.tr("station.no_interface");
		String call = status.getString("callsign");
		if(!status.getBoolean("radioOn"))
			return Dos.tr("station.no_radio", call.isEmpty() ? Dos.tr("station.no_call") : call);
		if(needsVhf && !status.getBoolean("vhf"))
			return Dos.tr("station.no_vhf", call.isEmpty() ? Dos.tr("station.no_call") : call);
		String mhz = String.format(Locale.ROOT, "%.3f", status.getFloat("mhz"));
		return call.isEmpty() ? Dos.tr("station.ready_no_call", mhz) : Dos.tr("station.ready", call, mhz);
	}

	/** True if the station can send (as far as the screen knows). */
	public static boolean ready(@Nullable CompoundTag status, boolean needsVhf) {
		return status != null && status.getBoolean("interface") && status.getBoolean("radioOn") && !status.getString("callsign").isEmpty()
				&& (!needsVhf || status.getBoolean("vhf"));
	}

	/** Draws the station line at {@code row}: green when ready, red when something is missing. */
	public static void render(TextGrid g, int row, @Nullable CompoundTag status, boolean needsVhf) {
		boolean ready = ready(status, needsVhf);
		g.putField(1, row, " " + Dos.fit(text(status, needsVhf), TextGrid.COLS - 4), TextGrid.COLS - 2, ready ? DosColours.LIGHT_GREEN : DosColours.YELLOW, DosColours.BODY_BG);
	}

	/** Seconds as a short age: "12s", "5m", "3h", "2d". */
	public static String ago(long seconds) {
		if(seconds < 0)
			return "-";
		if(seconds < 60)
			return seconds + "s";
		if(seconds < 3600)
			return seconds / 60 + "m";
		if(seconds < 86400)
			return seconds / 3600 + "h";
		return seconds / 86400 + "d";
	}

	/** A real-time stamp (ms) as UTC "MM-DD HH:MM", as hams log. */
	public static String utc(long millis) {
		java.time.ZonedDateTime time = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC);
		return String.format(Locale.ROOT, "%02d-%02d %02d:%02dZ", time.getMonthValue(), time.getDayOfMonth(), time.getHour(), time.getMinute());
	}

	/** The status line's message (bottom row), if there is one. */
	public static void message(TextGrid g, com.arrl.radiocraft.client.screens.computer.ComputerSession session) {
		String message = session.message();
		if(!message.isEmpty())
			g.putField(0, 23, " " + message, TextGrid.COLS, session.messageIsError() ? DosColours.ERROR_FG : DosColours.BLACK,
					session.messageIsError() ? DosColours.ERROR_BG : DosColours.LIGHT_GREY);
	}
}
