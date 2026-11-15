package com.arrl.radiocraft.client.screens.mesh;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * What the fire alarm controller's green screen shows (2026-11-11), after a DMP-704's: one event at a time (the kind
 * up top with the time boxed, the ACCOUNT boxed, the box's location, ZONE boxed and the line R01L1), the soft keys
 * NEXT, SILENCE, FAST and E along the bottom with the printer's state and the clock beside them. E is the menu: the box
 * list (service mode) and setup. Drawn on the terminal's GUI and on the block.
 */
public final class FireAlarmCrt {

	public static final int COLS = 40, ROWS = 15;
	/** The soft keys' columns on rows {@link #KEY_ROW} and the row under it. */
	public static final int[] KEY_COLS = { 1, 7, 16, 22 };
	public static final String[] KEYS = { "NEXT", "SILENCE", "FAST", "E" };
	public static final int KEY_ROW = 12;

	public enum View { EVENT, MENU, BOXES }

	private FireAlarmCrt() {}

	/** Which soft key the cell {@code col, row} is on: 0 NEXT, 1 SILENCE, 2 FAST, 3 E, or -1. */
	public static int keyAt(int col, int row) {
		if(row != KEY_ROW && row != KEY_ROW + 1)
			return -1;
		for(int i = 0; i < KEY_COLS.length; i++)
			if(col >= KEY_COLS[i] && col < KEY_COLS[i] + Math.max(3, KEYS[i].length()))
				return i;
		return -1;
	}

	/**
	 * Fills {@code crt} from {@code s} (the controller's status; null while waiting for it) in {@code view}, the event
	 * {@code event} back from the newest, the box {@code selected} picked on the box list, {@code blink} for flashing.
	 */
	public static void fill(Crt crt, @Nullable CompoundTag s, View view, int event, int selected, boolean blink) {
		crt.clear();
		if(s == null) {
			crt.put(1, 1, "RC-704 RADIO BOX RECEIVER");
			crt.put(1, 3, "WAITING FOR RECEIVER...");
			return;
		}
		switch(view) {
			case EVENT -> event(crt, s, event, blink);
			case MENU -> menu(crt, s);
			case BOXES -> boxes(crt, s, selected);
		}
		keys(crt, s, blink);
	}

	private static void event(Crt crt, CompoundTag s, int index, boolean blink) {
		ListTag log = s.getList("log", Tag.TAG_COMPOUND);
		if(s.getString("mhz").isEmpty()) {
			crt.put(1, 0, "NO FREQUENCY", Crt.BRIGHT);
			crt.put(1, 2, "PRESS E, THEN 3 SETUP:");
			crt.put(1, 3, "FREQUENCY AND CALLSIGN.");
			return;
		}
		if(log.isEmpty()) {
			crt.put(1, 0, "SYSTEM NORMAL", Crt.BRIGHT);
			crt.put(1, 2, s.getString("mhz") + " MHZ  " + s.getString("license"));
			crt.put(1, 3, "NO EVENTS LOGGED");
			return;
		}
		index = Math.max(0, Math.min(index, log.size() - 1));
		CompoundTag e = log.getCompound(index);
		boolean attention = e.getBoolean("attention") && s.getBoolean("attention");
		String head = switch(e.getString("type")) {
			case "FIRE" -> "FIRE ALARM";
			case "RESTORE" -> "RESTORAL";
			case "TEST" -> "TEST";
			case "TAMPER" -> "TAMPER";
			case "MISSING" -> "MISSING BOX";
			case "CHECKIN" -> "CHECK-IN";
			default -> e.getString("type");
		};
		crt.put(1, 0, head, attention && blink ? Crt.INVERSE : Crt.BRIGHT);
		crt.right(0, " " + e.getString("clock") + " ", Crt.INVERSE);
		crt.put(1, 1, "ACCOUNT: " + e.getString("account"), Crt.INVERSE);
		crt.right(1, String.format(Locale.ROOT, " DAY %03d ", e.getLong("day")), Crt.INVERSE);
		String label = e.getString("label").toUpperCase(Locale.ROOT);
		if(label.isEmpty())
			label = "BOX " + e.getString("account");
		crt.put(1, 2, label.length() > COLS - 2 ? label.substring(0, COLS - 2) : label);
		if(label.length() > COLS - 2)
			crt.put(1, 3, label.substring(COLS - 2));
		crt.put(1, 4, e.getString("what"));
		int zone = e.getInt("zone");
		if(e.getString("type").equals("FIRE") || e.getString("type").equals("RESTORE") || e.getString("type").equals("TEST")) {
			crt.put(1, 6, String.format(Locale.ROOT, "ZONE: %04d", zone), Crt.INVERSE);
			String station = e.getString("zoneName"); // The pull station's name, from the box's panel.
			if(!station.isEmpty())
				crt.put(12, 6, station.length() > COLS - 19 ? station.substring(0, COLS - 19) : station, Crt.BRIGHT);
		}
		crt.right(6, "R01L1 ", Crt.NORMAL);
		int hops = e.getInt("hops");
		crt.put(1, 7, hops == 0 ? "DIRECT" : "VIA " + hops + (hops == 1 ? " RELAY" : " RELAYS"), Crt.DIM);
		crt.put(1, 8, "TIME SINCE " + since(s.getLong("dayTimeNow") - e.getLong("dayTime")), Crt.NORMAL);
		crt.put(1, 9, String.format(Locale.ROOT, "EVENT %d OF %d", index + 1, log.size()), Crt.DIM);
		if(s.getBoolean("attention"))
			crt.put(1, 10, blink ? "** PRESS SILENCE **" : "", Crt.BRIGHT);
		else if(s.getBoolean("silenced"))
			crt.put(1, 10, "SILENCED: STILL IN ALARM UNTIL RESET", Crt.DIM);
	}

	/**
	 * How long ago an event was, on the game's clock (2026-11-11): "00:42" (hours and minutes), "2D 03:15" past a day,
	 * "JUST NOW" under a minute.
	 */
	public static String since(long ticks) {
		long minutes = Math.max(0L, ticks) * 1440L / 24000L;
		if(minutes < 1L)
			return "JUST NOW";
		long days = minutes / 1440L, hours = minutes / 60L % 24L, mins = minutes % 60L;
		return days > 0L ? String.format(Locale.ROOT, "%dD %02d:%02d", days, hours, mins) : String.format(Locale.ROOT, "%02d:%02d", hours, mins);
	}

	private static void menu(Crt crt, CompoundTag s) {
		crt.put(0, 0, " MAIN MENU" + " ".repeat(COLS - 10), Crt.INVERSE);
		crt.put(2, 2, "1  EVENT DISPLAY");
		crt.put(2, 3, "2  BOX LIST / SERVICE MODE");
		crt.put(2, 4, "3  SETUP: FREQUENCY, CALLSIGN");
		crt.put(2, 5, "4  CLEAR HEAD JAM");
		crt.put(2, 6, "5  SILENCE");
		String freq = s.getString("mhz").isEmpty() ? "FREQ ---.---" : "FREQ " + s.getString("mhz");
		crt.put(2, 8, freq + "   CALL " + (s.getString("license").isEmpty() ? "-" : s.getString("license")), Crt.DIM);
		crt.put(2, 9, s.getList("boxes", Tag.TAG_COMPOUND).size() + " BOXES KNOWN", Crt.DIM);
	}

	private static void boxes(Crt crt, CompoundTag s, int selected) {
		crt.put(0, 0, " BOX LIST     ENTER = IN/OUT OF SERVICE ", Crt.INVERSE);
		ListTag boxes = s.getList("boxes", Tag.TAG_COMPOUND);
		if(boxes.isEmpty()) {
			crt.put(1, 2, "NO BOX HEARD YET.");
			return;
		}
		int rows = KEY_ROW - 2;
		int first = Math.max(0, Math.min(selected - rows + 1, boxes.size() - rows));
		first = Math.max(0, first);
		for(int i = first; i < boxes.size() && i - first < rows; i++) {
			CompoundTag b = boxes.getCompound(i);
			String status = b.getBoolean("out") ? "OUT SVC" : b.getString("status");
			String line = String.format(Locale.ROOT, "%04d %-8s%s", b.getInt("box"), status, b.getString("label").toUpperCase(Locale.ROOT));
			if(line.length() > COLS - 2)
				line = line.substring(0, COLS - 2);
			byte attr = i == selected ? Crt.INVERSE : b.getString("status").equals("ALARM") ? Crt.BRIGHT : b.getBoolean("out") ? Crt.DIM : Crt.NORMAL;
			crt.put(1, 1 + i - first, i == selected ? String.format(Locale.ROOT, "%-" + (COLS - 2) + "s", line) : line, attr);
		}
	}

	private static void keys(Crt crt, CompoundTag s, boolean blink) {
		for(int i = 0; i < KEYS.length; i++) {
			crt.put(KEY_COLS[i], KEY_ROW, KEYS[i]);
			crt.put(KEY_COLS[i], KEY_ROW + 1, i == 3 ? "[E]" : "[ ]");
		}
		String printer = s.getBoolean("jammed") ? "HEAD JAM" : s.getInt("paper") <= 0 ? "PAPER OUT" : "PAPER " + s.getInt("paper");
		crt.right(KEY_ROW, printer + " ", s.getBoolean("jammed") || s.getInt("paper") <= 0 ? (blink ? Crt.BRIGHT : Crt.NORMAL) : Crt.DIM);
		crt.right(KEY_ROW + 1, s.getString("clockNow") + " ", Crt.NORMAL);
		crt.right(KEY_ROW + 2, String.format(Locale.ROOT, "DAY %03d ", s.getLong("dayNow")), Crt.NORMAL);
	}
}
