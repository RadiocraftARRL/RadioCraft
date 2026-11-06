package com.arrl.radiocraft.common.computer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the RTTY program has printed (Computer apps 2): the receive window as a teleprinter roll, a line per sender as
 * the Teletype Machine prints it. Characters from one station run on in its line until a newline, a full line, or
 * another station's characters; our own sent characters print too (the local copy), marked as ours. Keeps the last
 * {@link #HISTORY} lines. Pure (NBT only), so it is unit tested.
 */
public final class RttyCopy {

	/** Lines kept. */
	public static final int HISTORY = 300;
	/** Characters a line holds before the printer starts a new one by itself (the screen is 80 wide; the call goes first). */
	public static final int LINE_MAX = 64;

	/** One printed line: who sent it, the text, and whether it was ours. */
	public record Line(String from, String text, boolean own) {}

	private final List<Line> lines = new ArrayList<>(); // Closed lines, oldest first.
	private String openFrom = null; // The line being printed, or null.
	private boolean openOwn = false;
	private final StringBuilder open = new StringBuilder();

	/** Prints {@code text} from {@code from} ({@code own} for our local copy). A newline ends the line. */
	public void print(String from, String text, boolean own) {
		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if(c == '\n') {
				if(openFrom == null) // A newline on its own: an empty line from that station.
					start(from, own);
				close();
				continue;
			}
			if(openFrom == null || !openFrom.equals(from) || openOwn != own || open.length() >= LINE_MAX) {
				close();
				start(from, own);
			}
			open.append(c);
		}
	}

	private void start(String from, boolean own) {
		openFrom = from;
		openOwn = own;
		open.setLength(0);
	}

	private void close() {
		if(openFrom == null)
			return;
		lines.add(new Line(openFrom, open.toString(), openOwn));
		while(lines.size() > HISTORY)
			lines.removeFirst();
		openFrom = null;
		open.setLength(0);
	}

	/** Every line, oldest first, the one being printed last. */
	public List<Line> lines() {
		List<Line> out = new ArrayList<>(lines);
		if(openFrom != null)
			out.add(new Line(openFrom, open.toString(), openOwn));
		return out;
	}

	public boolean isEmpty() {
		return lines.isEmpty() && openFrom == null;
	}

	public void clear() {
		lines.clear();
		openFrom = null;
		open.setLength(0);
	}

	/** The roll as a text file: a line each, "CALL: text", ours marked with a '>' as RTTY programs do. */
	public String text() {
		StringBuilder out = new StringBuilder();
		for(Line line : lines())
			out.append(line.own() ? "> " : "").append(line.from()).append(": ").append(line.text()).append('\n');
		return out.toString();
	}

	public ListTag toTag() {
		ListTag out = new ListTag();
		for(Line line : lines()) {
			CompoundTag tag = new CompoundTag();
			tag.putString("from", line.from());
			tag.putString("text", line.text());
			if(line.own())
				tag.putBoolean("own", true);
			out.add(tag);
		}
		return out;
	}

	/** Reads what {@link #toTag} wrote (all lines closed). */
	public void load(ListTag tag) {
		clear();
		for(Tag entry : tag) {
			CompoundTag line = (CompoundTag)entry;
			lines.add(new Line(line.getString("from"), line.getString("text"), line.getBoolean("own")));
		}
		while(lines.size() > HISTORY)
			lines.removeFirst();
	}

	// -------------------- MACROS --------------------

	/** The three macros, as they come before the player edits them: a CQ, a callsign exchange and the sign-off. */
	public static final List<String> DEFAULT_MACROS = List.of(
			"CQ CQ CQ DE {MYCALL} {MYCALL} {MYCALL} K\n",
			"{CALL} DE {MYCALL} ",
			"TU 73 {CALL} DE {MYCALL} SK\n");
	/** Characters a macro holds. */
	public static final int MACRO_MAX = 120;

	/**
	 * A macro as it goes out: {@code {MYCALL}} is our callsign, {@code {CALL}} the other station's (nothing when it is
	 * not set), {@code \n} (typed as a backslash and n, or a real newline) ends the line. Upper case, like everything a
	 * teleprinter sends.
	 */
	public static String expand(String macro, String myCall, String theirCall) {
		String out = macro.replace("\\n", "\n").toUpperCase(Locale.ROOT);
		return out.replace("{MYCALL}", myCall.toUpperCase(Locale.ROOT)).replace("{CALL}", theirCall.toUpperCase(Locale.ROOT));
	}
}
