package com.arrl.radiocraft.common.radio.scada;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * A SCADA master's rules (2026-11-10), a line each. Pure.
 * <pre>
 *   IF 3.N &gt;= 10 THEN 7.E = 15    when RTU 3's north face reads 10 or more, RTU 7's east face puts out 15
 *   COPY 3.N TO 0.U                the master's top face puts out whatever RTU 3's north face reads
 *   POLL 2                         the master polls an RTU every 2 seconds (1 to 3)
 * </pre>
 * A point is {@code unit.face}: unit 0 is the master itself, faces are N S E W U D (or NORTH, UP, TOP...). Comparisons:
 * {@code >= <= > < = != <>}. An output a rule drives is the largest value any true rule gives it, and 0 when none is
 * true; outputs no rule names stay at 0. A rule whose input is unknown (its RTU not answering, its face not IN) is not
 * true. {@code #} or {@code //} starts a comment.
 */
public final class ScadaRules {

	/** Face letters in the order of {@code Direction#get3DDataValue}: down, up, north, south, west, east. */
	public static final String FACES = "DUNSWE";
	/** Seconds the master spends on each RTU's poll (2026-11-11: set on the master, 1 to 3). */
	public static final int MIN_POLL = 1, MAX_POLL = 3, DEFAULT_POLL = 1;

	/** One face of one unit. */
	public record Point(int unit, int face) {
		@Override
		public String toString() {
			return unit + "." + FACES.charAt(face);
		}
	}

	public enum Op {
		GE(">="), LE("<="), GT(">"), LT("<"), EQ("="), NE("!=");

		public final String symbol;

		Op(String symbol) {
			this.symbol = symbol;
		}

		public boolean test(int a, int b) {
			return switch(this) {
				case GE -> a >= b;
				case LE -> a <= b;
				case GT -> a > b;
				case LT -> a < b;
				case EQ -> a == b;
				case NE -> a != b;
			};
		}
	}

	/** An IF rule ({@code op} non-null) or a COPY ({@code op} null, {@code value} unused). */
	public record Rule(Point source, Op op, int threshold, Point target, int value) {
		public boolean copy() {
			return op == null;
		}

		@Override
		public String toString() {
			return copy() ? "COPY " + source + " TO " + target : "IF " + source + " " + op.symbol + " " + threshold + " THEN " + target + " = " + value;
		}
	}

	/** What a rule book says: its rules, the hop limit it sets (0 if none), and the lines it could not read. */
	/** The rules, the POLL line's seconds (0 if none) and the lines that could not be read. */
	public record Parsed(List<Rule> rules, int pollSeconds, List<String> errors) {}

	private ScadaRules() {}

	public static Parsed parse(List<String> lines) {
		List<Rule> rules = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		int poll = 0;
		for(String raw : lines) {
			String line = raw;
			int comment = line.indexOf('#');
			if(comment >= 0)
				line = line.substring(0, comment);
			comment = line.indexOf("//");
			if(comment >= 0)
				line = line.substring(0, comment);
			line = line.trim().toUpperCase(Locale.ROOT);
			if(line.isEmpty())
				continue;
			String[] words = spaced(line).trim().split("\\s+");
			try {
				switch(words[0]) {
					case "POLL" -> {
						int n = Integer.parseInt(words[1]);
						if(words.length != 2 || n < MIN_POLL || n > MAX_POLL)
							throw new IllegalArgumentException();
						poll = n;
					}
					case "COPY" -> {
						if(words.length != 4 || !words[2].equals("TO"))
							throw new IllegalArgumentException();
						rules.add(new Rule(point(words[1]), null, 0, point(words[3]), 0));
					}
					case "IF" -> {
						// IF a OP n THEN b = n
						if(words.length != 8 || !words[4].equals("THEN") || !words[6].equals("="))
							throw new IllegalArgumentException();
						rules.add(new Rule(point(words[1]), op(words[2]), level(words[3]), point(words[5]), level(words[7])));
					}
					default -> throw new IllegalArgumentException();
				}
			}
			catch(RuntimeException e) {
				errors.add(raw.trim());
			}
		}
		return new Parsed(rules, poll, errors);
	}

	/** Spaces round the comparison and '=' so "3.N>=10" reads as "3.N >= 10". */
	private static String spaced(String line) {
		StringBuilder out = new StringBuilder();
		for(int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if(c == '>' || c == '<' || c == '=' || c == '!') {
				out.append(' ').append(c);
				char next = i + 1 < line.length() ? line.charAt(i + 1) : ' ';
				if(next == '=' || (c == '<' && next == '>')) {
					out.append(next);
					i++;
				}
				out.append(' ');
			}
			else
				out.append(c);
		}
		return out.toString();
	}

	public static Point point(String word) {
		int dot = word.indexOf('.');
		if(dot <= 0 || dot == word.length() - 1)
			throw new IllegalArgumentException(word);
		int unit = Integer.parseInt(word.substring(0, dot));
		if(unit < 0 || unit > 9999)
			throw new IllegalArgumentException(word);
		return new Point(unit, face(word.substring(dot + 1)));
	}

	/** A face's index from its letter or name; -1 if none. */
	public static int faceIndex(String name) {
		return switch(name.toUpperCase(Locale.ROOT)) {
			case "D", "DOWN", "BOTTOM" -> 0;
			case "U", "UP", "TOP" -> 1;
			case "N", "NORTH" -> 2;
			case "S", "SOUTH" -> 3;
			case "W", "WEST" -> 4;
			case "E", "EAST" -> 5;
			default -> -1;
		};
	}

	private static int face(String name) {
		int face = faceIndex(name);
		if(face < 0)
			throw new IllegalArgumentException(name);
		return face;
	}

	private static Op op(String word) {
		return switch(word) {
			case ">=", "=>" -> Op.GE;
			case "<=", "=<" -> Op.LE;
			case ">" -> Op.GT;
			case "<" -> Op.LT;
			case "=", "==" -> Op.EQ;
			case "!=", "<>" -> Op.NE;
			default -> throw new IllegalArgumentException(word);
		};
	}

	private static int level(String word) {
		int n = Integer.parseInt(word);
		if(n < 0 || n > 15)
			throw new IllegalArgumentException(word);
		return n;
	}

	/**
	 * The outputs {@code rules} drive, given what each point reads ({@code read}: -1 for unknown). Only points some
	 * rule names are in the map.
	 */
	public static Map<Point, Integer> evaluate(List<Rule> rules, ToIntFunction<Point> read) {
		Map<Point, Integer> out = new HashMap<>();
		for(Rule rule : rules) {
			out.putIfAbsent(rule.target(), 0);
			int value = read.applyAsInt(rule.source());
			if(value < 0)
				continue;
			int give = rule.copy() ? value : rule.op().test(value, rule.threshold()) ? rule.value() : -1;
			if(give >= 0)
				out.merge(rule.target(), Math.min(15, give), Math::max);
		}
		return out;
	}

	/** Six levels as the air carries them: a hex digit each, '-' for none (-1). */
	public static String encodeLevels(int[] levels) {
		StringBuilder out = new StringBuilder(6);
		for(int i = 0; i < 6; i++) {
			int v = i < levels.length ? levels[i] : -1;
			out.append(v < 0 ? '-' : Character.forDigit(Math.min(15, v), 16));
		}
		return out.toString();
	}

	/** The levels {@link #encodeLevels} wrote; -1 for '-' or anything unreadable. */
	public static int[] decodeLevels(String text) {
		int[] out = new int[6];
		for(int i = 0; i < 6; i++)
			out[i] = i < text.length() ? Character.digit(text.charAt(i), 16) : -1;
		return out;
	}
}
