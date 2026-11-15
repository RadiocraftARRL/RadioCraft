package com.arrl.radiocraft.client.screens.mesh;

import com.arrl.radiocraft.common.radio.scada.ScadaRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the SCADA master's green screen shows (2026-11-11): a tab line (POINTS, RULES, I/O, SETUP), the frequency line,
 * and the page. POINTS: the master and each RTU's six faces, what the rules want of them, COMM FAIL. RULES: the rule
 * book. I/O: the master's own faces (unit 0), each OFF, IN or OUT with its level, and how they work. SETUP: frequency,
 * callsign, poll speed and link, and ENTER to edit. When the console's screen opens it boots first ({@link #boot}),
 * with the maker's logo.
 */
public final class ScadaCrt {

	public static final int COLS = 32, ROWS = 12;
	public static final String[] TABS = { "POINTS", "RULES", "I/O", "SETUP" };
	private static final int[] TAB_COLS = { 1, 8, 14, 18 };
	/** The page's first row and how many rows it has. */
	public static final int BODY = 2, BODY_ROWS = ROWS - BODY;
	/** The I/O page's first face row. */
	public static final int IO_FIRST_ROW = BODY + 1;

	/** The boot screen, and how long it runs. */
	public static final int BOOT_COLS = 54, BOOT_ROWS = 16;
	public static final long BOOT_MS = 3200L;
	private static final String[] LOGO = {
			"  _      __                                        ",
			" /_) )   )_)                                       ",
			"/ / (__ / \\                                        ",
			"                                                   ",
			"___ ___  _            _       _   _   ___ ___  __  ",
			" )  )_  / ` )_) )\\ ) / ) )   / ) / _   )  )_  (_ ` ",
			"(  (__ (_. ( ( (  ( (_/ (__ (_/ (__/ _(_ (__ .__)  ",
	};
	private static final String[] BOOT_LINES = {
			"SCADA MASTER STATION  REV 2.1",
			"MEMORY TEST ....... 64K OK",
			"MODEM ............. OK",
			"RTU POLLER ........ READY",
	};

	private ScadaCrt() {}

	/** The tab under column {@code col} of the tab line, or -1. */
	public static int tabAt(int col, int row) {
		if(row != 0)
			return -1;
		for(int i = 0; i < TABS.length; i++)
			if(col >= TAB_COLS[i] - 1 && col <= TAB_COLS[i] + TABS[i].length())
				return i;
		return -1;
	}

	/** The boot, {@code ms} after the screen opened: the logo, then the self-test lines one by one. */
	public static void boot(Crt crt, long ms) {
		crt.clear();
		int top = 2;
		for(int i = 0; i < LOGO.length; i++)
			if(ms > 150L + i * 90L)
				crt.put(1, top + i, LOGO[i]);
		for(int i = 0; i < BOOT_LINES.length; i++)
			if(ms > 1100L + i * 450L)
				crt.put(1, top + LOGO.length + 1 + i, BOOT_LINES[i]);
		if((ms / 300L) % 2L == 0L)
			crt.put(1, top + LOGO.length + 1 + Math.min(BOOT_LINES.length, (int)Math.max(0L, (ms - 1100L) / 450L + 1)), "_", Crt.BRIGHT);
	}

	/**
	 * The SETUP page's fields as the console is editing them (2026-11-11): typed straight in, no separate window.
	 *
	 * @param values   FREQ (MHz), CALL and NAME as typed.
	 * @param selected The row picked: 0 FREQ, 1 CALL, 2 NAME, 3 POLL.
	 * @param cursor   True while the cursor block shows (it blinks).
	 * @param dirty    True if something typed is not saved yet.
	 * @param note     A line under the fields (SAVED, BAD CALLSIGN...), or empty.
	 */
	public record SetupView(String[] values, int selected, boolean cursor, boolean dirty, String note) {}

	/** SETUP's rows: the fields, then POLL. */
	public static final int SETUP_FIELDS = 3, SETUP_ROWS = 4;
	/** Field widths on the screen. */
	private static final int[] SETUP_WIDTH = { 8, 10, 24 };
	private static final String[] SETUP_NAMES = { "FREQ", "CALL", "NAME" };

	/** The screen for {@code s} (the master's status; null while waiting) on page {@code tab}, scrolled {@code scroll}. */
	public static void fill(Crt crt, @Nullable CompoundTag s, int tab, int scroll) {
		fill(crt, s, tab, scroll, null);
	}

	/** As above, with SETUP's fields as the console is editing them (null: as the master has them). */
	public static void fill(Crt crt, @Nullable CompoundTag s, int tab, int scroll, @Nullable SetupView view) {
		crt.clear();
		for(int i = 0; i < TABS.length; i++)
			crt.put(TAB_COLS[i], 0, TABS[i], i == tab ? Crt.INVERSE : Crt.DIM);
		if(s == null) {
			crt.put(1, BODY, "READING...");
			return;
		}
		if(tab == 3) { // SETUP draws its own fields.
			setupPage(crt, s, view);
			return;
		}
		boolean on = !s.getString("mhz").isEmpty();
		crt.put(1, 1, on ? String.format(Locale.ROOT, "%s %s POLL %dS", s.getString("mhz"), s.getString("license"), s.getInt("poll")) : "NO FREQUENCY: SEE SETUP", Crt.DIM);
		List<String> lines = new ArrayList<>();
		List<Byte> attrs = new ArrayList<>();
		switch(tab) {
			case 0 -> points(s, on, lines, attrs);
			case 1 -> rules(s, lines, attrs);
			case 2 -> io(s, lines, attrs);
			default -> setup(s, on, lines, attrs);
		}
		int fixed = tab == 0 && on ? 1 : 0; // The points' column heading stays put.
		int max = Math.max(0, lines.size() - BODY_ROWS);
		scroll = tab == 2 || tab == 3 ? 0 : Math.max(0, Math.min(scroll, max));
		int row = BODY;
		for(int i = 0; i < lines.size() && row < ROWS; i++) {
			if(i >= fixed && i < fixed + scroll)
				continue;
			crt.put(0, row++, lines.get(i), attrs.get(i));
		}
	}

	/** How many lines {@code tab} scrolls through, for clamping the scroll. */
	public static int scrollMax(@Nullable CompoundTag s, int tab) {
		if(s == null || tab > 1)
			return 0;
		List<String> lines = new ArrayList<>();
		List<Byte> attrs = new ArrayList<>();
		if(tab == 0)
			points(s, !s.getString("mhz").isEmpty(), lines, attrs);
		else
			rules(s, lines, attrs);
		return Math.max(0, lines.size() - BODY_ROWS);
	}

	private static void add(List<String> lines, List<Byte> attrs, String line, byte attr) {
		lines.add(line.length() > COLS ? line.substring(0, COLS) : line);
		attrs.add(attr);
	}

	private static void points(CompoundTag s, boolean on, List<String> lines, List<Byte> attrs) {
		if(!on)
			return;
		add(lines, attrs, "UNIT   D  U  N  S  W  E  LAST", Crt.BRIGHT);
		add(lines, attrs, row("0 MST", s.getString("levels"), ""), Crt.NORMAL);
		ListTag units = s.getList("units", Tag.TAG_COMPOUND);
		for(int i = 0; i < units.size(); i++) {
			CompoundTag u = units.getCompound(i);
			long ago = u.getLong("ago");
			boolean fail = u.getBoolean("fail");
			add(lines, attrs, row(String.format(Locale.ROOT, "%d RTU", u.getInt("id")), u.getString("inputs"), fail ? "FAIL" : ago < 0 ? "--" : (ago / 20L) + "S"),
					fail ? Crt.INVERSE : Crt.NORMAL);
			String wanted = u.getString("outputs");
			if(!wanted.equals("------"))
				add(lines, attrs, row("  SET", wanted, ""), Crt.DIM);
		}
		if(units.isEmpty())
			add(lines, attrs, "NO RTU ON THIS FREQUENCY.", Crt.DIM);
	}

	private static String row(String name, String levels, String note) {
		StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%-5s", name));
		for(int i = 0; i < 6; i++) {
			char c = i < levels.length() ? levels.charAt(i) : '-';
			int value = Character.digit(c, 16);
			line.append(value < 0 ? "  -" : String.format(Locale.ROOT, "%3d", value));
		}
		return line.append("  ").append(note).toString();
	}

	private static void rules(CompoundTag s, List<String> lines, List<Byte> attrs) {
		ListTag rules = s.getList("rules", Tag.TAG_STRING);
		ListTag errors = s.getList("errors", Tag.TAG_STRING);
		for(int i = 0; i < rules.size(); i++) {
			String line = rules.getString(i);
			boolean bad = false;
			for(int e = 0; e < errors.size(); e++)
				bad |= errors.getString(e).equals(line.trim());
			add(lines, attrs, String.format(Locale.ROOT, "%2d %s", i + 1, line.toUpperCase(Locale.ROOT)), bad ? Crt.INVERSE : Crt.NORMAL);
		}
		if(rules.isEmpty()) {
			add(lines, attrs, "NO RULES. WRITE THEM IN A BOOK", Crt.DIM);
			add(lines, attrs, "AND QUILL AND USE IT ON THE", Crt.DIM);
			add(lines, attrs, "MASTER: IF 3.N >= 10 THEN 7.E = 15", Crt.DIM);
		}
	}

	private static void io(CompoundTag s, List<String> lines, List<Byte> attrs) {
		add(lines, attrs, "FACE      MODE LVL  (UNIT 0.x)", Crt.BRIGHT);
		String modes = s.getString("modes"), levels = s.getString("levels");
		for(int i = 0; i < 6; i++) {
			char m = i < modes.length() ? modes.charAt(i) : 'F';
			String mode = m == 'N' ? "IN " : m == 'U' ? "OUT" : "OFF";
			int value = i < levels.length() ? Character.digit(levels.charAt(i), 16) : -1;
			String what = m == 'N' ? "READS" : m == 'U' ? "GIVES" : "";
			add(lines, attrs, String.format(Locale.ROOT, "%c %-7s %s  %s  %s", ScadaRules.FACES.charAt(i), faceName(i), mode, value < 0 ? " -" : String.format(Locale.ROOT, "%2d", value), what),
					m == 'F' ? Crt.DIM : Crt.NORMAL);
		}
		add(lines, attrs, "CLICK A ROW OR TOGGLE: OFF>IN>OUT", Crt.DIM);
		add(lines, attrs, "IN READS REDSTONE, OUT GIVES IT", Crt.DIM);
		add(lines, attrs, "AS THE RULES SAY (0.N = NORTH)", Crt.DIM);
	}

	public static String faceName(int face) {
		return switch(ScadaRules.FACES.charAt(face)) {
			case 'D' -> "BOTTOM";
			case 'U' -> "TOP";
			case 'N' -> "NORTH";
			case 'S' -> "SOUTH";
			case 'W' -> "WEST";
			default -> "EAST";
		};
	}

	/** The SETUP row under the cell row {@code row}, or -1. */
	public static int setupRowAt(int row) {
		int r = row - BODY - 1;
		return r >= 0 && r < SETUP_ROWS ? r : -1;
	}

	private static void setupPage(Crt crt, CompoundTag s, @Nullable SetupView view) {
		String[] values = view != null ? view.values() : new String[] { s.getString("mhz"), s.getString("license"), s.getString("label") };
		int selected = view == null ? -1 : view.selected();
		crt.put(1, 1, "SETUP: TYPE INTO A FIELD", Crt.DIM);
		for(int i = 0; i < SETUP_FIELDS; i++) {
			int row = BODY + 1 + i, w = SETUP_WIDTH[i];
			String value = values[i];
			String shown = value.length() > w ? value.substring(value.length() - w) : value; // The end, where typing goes.
			crt.put(1, row, SETUP_NAMES[i], i == selected ? Crt.BRIGHT : Crt.NORMAL);
			crt.put(6, row, "[" + String.format(Locale.ROOT, "%-" + w + "s", shown) + "]", i == selected ? Crt.INVERSE : Crt.NORMAL);
			if(i == selected && view.cursor())
				crt.put(7 + Math.min(shown.length(), w - 1), row, "_", Crt.BRIGHT);
			if(i == 0)
				crt.put(8 + w, row, "MHZ", Crt.DIM);
		}
		int pollRow = BODY + 1 + SETUP_FIELDS;
		crt.put(1, pollRow, "POLL", selected == 3 ? Crt.BRIGHT : Crt.NORMAL);
		crt.put(6, pollRow, String.format(Locale.ROOT, "< %d S >", Math.max(1, s.getInt("poll"))), selected == 3 ? Crt.INVERSE : Crt.NORMAL);
		crt.put(15, pollRow, "EACH RTU", Crt.DIM);
		crt.put(1, pollRow + 1, "LINK " + s.getInt("link") + "%", Crt.DIM);
		String note = view == null ? "" : view.note();
		if(!note.isEmpty())
			crt.put(1, ROWS - 3, note, Crt.INVERSE);
		else if(view != null && view.dirty())
			crt.put(1, ROWS - 3, "NOT SAVED: ENTER SAVES", Crt.BRIGHT);
		crt.put(1, ROWS - 2, "UP/DN PICK ENTER SAVE ESC UNDO", Crt.DIM);
		crt.put(1, ROWS - 1, "POLL: LEFT/RIGHT  TAB: PAGES", Crt.DIM);
	}

	private static void setup(CompoundTag s, boolean on, List<String> lines, List<Byte> attrs) {
		add(lines, attrs, "FREQ  " + (on ? s.getString("mhz") + " MHZ" : "---.---"), Crt.NORMAL);
		add(lines, attrs, "CALL  " + (s.getString("license").isEmpty() ? "-" : s.getString("license")), Crt.NORMAL);
		add(lines, attrs, "POLL  " + s.getInt("poll") + " S EACH RTU (KNOB)", Crt.NORMAL);
		add(lines, attrs, "LINK  " + s.getInt("link") + "%", Crt.NORMAL);
		add(lines, attrs, "", Crt.NORMAL);
		add(lines, attrs, " ENTER OR CLICK HERE: EDIT ", Crt.INVERSE);
		add(lines, attrs, " FREQUENCY AND CALLSIGN    ", Crt.INVERSE);
	}
}
