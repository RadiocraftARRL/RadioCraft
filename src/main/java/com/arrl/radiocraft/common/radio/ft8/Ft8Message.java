package com.arrl.radiocraft.common.radio.ft8;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * One FT8 message, the standard exchange of a contact (computer batch 3B), as WSJT-X shows them:
 * <pre>
 * CQ W1BTR FN31        a CQ with the caller's grid       (type CQ, from W1BTR)
 * W1BTR K1ABC JJ00     K1ABC answers W1BTR with its grid (GRID)
 * K1ABC W1BTR -12      W1BTR's report on K1ABC           (REPORT)
 * W1BTR K1ABC R-08     K1ABC: report received, and its own (R_REPORT)
 * K1ABC W1BTR RR73     W1BTR: received, best regards     (RR73; RRR is taken the same way)
 * W1BTR K1ABC 73       K1ABC signs off                   (SIGNOFF)
 * </pre>
 * Free text is not sent. Callsigns are upper case letters, digits and '/', at most 12 characters (the mod's callsigns),
 * without an APRS SSID.
 *
 * @param type   What the message is.
 * @param to     The station it is for ("" for a CQ).
 * @param from   The station sending it.
 * @param grid   The four-character locator (CQ and GRID), else "".
 * @param report The report in dB (REPORT and R_REPORT), else 0.
 */
public record Ft8Message(Type type, String to, String from, String grid, int report) {

	public enum Type { CQ, GRID, REPORT, R_REPORT, RR73, SIGNOFF }

	public static final int CALL_MAX = 12;

	public Ft8Message {
		to = cleanCall(to);
		from = cleanCall(from);
		grid = Ft8.isGrid(grid) ? grid : "";
		report = Math.max(Ft8.MIN_SNR, Math.min(Ft8.MAX_SNR, report));
	}

	public static Ft8Message cq(String from, String grid) {
		return new Ft8Message(Type.CQ, "", from, grid, 0);
	}

	public static Ft8Message reply(String to, String from, String grid) {
		return new Ft8Message(Type.GRID, to, from, grid, 0);
	}

	public static Ft8Message report(String to, String from, int report) {
		return new Ft8Message(Type.REPORT, to, from, "", report);
	}

	public static Ft8Message rReport(String to, String from, int report) {
		return new Ft8Message(Type.R_REPORT, to, from, "", report);
	}

	public static Ft8Message rr73(String to, String from) {
		return new Ft8Message(Type.RR73, to, from, "", 0);
	}

	public static Ft8Message signoff(String to, String from) {
		return new Ft8Message(Type.SIGNOFF, to, from, "", 0);
	}

	/** True if the message is for {@code call}. */
	public boolean isFor(String call) {
		return !to.isEmpty() && to.equals(cleanCall(call));
	}

	/** The message as it goes on the air and shows in the decode list. */
	public String encode() {
		return switch(type) {
			case CQ -> "CQ " + from + (grid.isEmpty() ? "" : " " + grid);
			case GRID -> to + " " + from + (grid.isEmpty() ? "" : " " + grid);
			case REPORT -> to + " " + from + " " + report(report);
			case R_REPORT -> to + " " + from + " R" + report(report);
			case RR73 -> to + " " + from + " RR73";
			case SIGNOFF -> to + " " + from + " 73";
		};
	}

	/** A report as sent: always signed, two digits ("-08", "+05"). */
	public static String report(int db) {
		return (db < 0 ? "-" : "+") + String.format(Locale.ROOT, "%02d", Math.abs(db));
	}

	/** Reads a message as {@link #encode} writes it (and "RRR" for RR73); null if it is not one. */
	@Nullable
	public static Ft8Message parse(String text) {
		if(text == null)
			return null;
		String[] words = text.strip().toUpperCase(Locale.ROOT).split("\\s+");
		if(words.length < 2 || words.length > 3)
			return null;
		if(words[0].equals("CQ")) {
			if(!isCall(words[1]))
				return null;
			String grid = words.length == 3 ? words[2] : "";
			if(!grid.isEmpty() && !Ft8.isGrid(grid))
				return null;
			return cq(words[1], grid);
		}
		if(!isCall(words[0]) || !isCall(words[1]))
			return null;
		if(words.length == 2)
			return reply(words[0], words[1], "");
		String last = words[2];
		if(last.equals("RR73") || last.equals("RRR")) // Before the grid: RR73 would be a square in the Arctic.
			return rr73(words[0], words[1]);
		if(Ft8.isGrid(last))
			return reply(words[0], words[1], last);
		if(last.equals("73"))
			return signoff(words[0], words[1]);
		boolean r = last.startsWith("R") && last.length() > 1;
		Integer db = parseReport(r ? last.substring(1) : last);
		if(db == null)
			return null;
		return r ? rReport(words[0], words[1], db) : report(words[0], words[1], db);
	}

	@Nullable
	private static Integer parseReport(String text) {
		if(text.length() != 3 || (text.charAt(0) != '-' && text.charAt(0) != '+')) // Always a sign and two digits.
			return null;
		for(int i = 1; i < text.length(); i++)
			if(!Character.isDigit(text.charAt(i)))
				return null;
		int value = Integer.parseInt(text.substring(1));
		return text.charAt(0) == '-' ? -value : value;
	}

	/** A callsign as FT8 carries it: letters, digits and '/', upper case, at most {@link #CALL_MAX}. */
	public static String cleanCall(String call) {
		if(call == null)
			return "";
		int dash = call.indexOf('-'); // An APRS SSID (W1BTR-9) is not part of the call on FT8.
		if(dash >= 0)
			call = call.substring(0, dash);
		StringBuilder out = new StringBuilder();
		for(char c : call.toUpperCase(Locale.ROOT).toCharArray()) {
			if((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '/')
				out.append(c);
			if(out.length() >= CALL_MAX)
				break;
		}
		return out.toString();
	}

	/** True if {@code word} can be a callsign: 3 or more characters with a letter and a digit. */
	static boolean isCall(String word) {
		if(word.length() < 3 || word.length() > CALL_MAX || !cleanCall(word).equals(word))
			return false;
		boolean letter = false, digit = false;
		for(char c : word.toCharArray()) {
			letter |= Character.isLetter(c);
			digit |= Character.isDigit(c);
		}
		return letter && digit;
	}
}
