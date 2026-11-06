package com.arrl.radiocraft.client.screens.computer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * The RC-DOS command line's pure parts (1.12, launcher and SETUP): splitting a typed line into a command and its
 * arguments, DOS wildcards ({@code *.TXT}, {@code LOG?.LOG}), finding a program by the name typed ({@code RTTY} or
 * {@code RTTY.EXE}), Tab completion and the command history (up and down). No game classes, so it is unit tested;
 * {@link ShellPrompt} is the screen.
 */
public final class DosCommands {

	private DosCommands() {}

	/** A typed line: its command in capitals ("" for an empty line) and its arguments as typed. */
	public record Line(String command, List<String> args) {

		/** Argument {@code i}, or "" if there are not that many. */
		public String arg(int i) {
			return i < args.size() ? args.get(i) : "";
		}

		/** True if {@code flag} ("/Y") is among the arguments, in any case. */
		public boolean has(String flag) {
			for(String arg : args)
				if(arg.equalsIgnoreCase(flag))
					return true;
			return false;
		}

		/** The arguments that are not switches ("/Y", "/P"). */
		public List<String> names() {
			List<String> out = new ArrayList<>();
			for(String arg : args)
				if(!arg.startsWith("/"))
					out.add(arg);
			return out;
		}
	}

	/**
	 * Splits {@code text} on spaces; "quoted words" stay together. As in DOS, a command runs straight into a switch or
	 * a dot ({@code DIR/W}, {@code CD..}) and those are split off.
	 */
	public static Line parse(String text) {
		List<String> words = new ArrayList<>();
		StringBuilder word = new StringBuilder();
		boolean quoted = false;
		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if(c == '"')
				quoted = !quoted;
			else if(c == ' ' && !quoted) {
				if(!word.isEmpty())
					words.add(word.toString());
				word.setLength(0);
			}
			else
				word.append(c);
		}
		if(!word.isEmpty())
			words.add(word.toString());
		if(words.isEmpty())
			return new Line("", List.of());
		String first = words.removeFirst();
		int cut = first.indexOf('/');
		if(cut > 0) { // DIR/W
			words.addFirst(first.substring(cut));
			first = first.substring(0, cut);
		}
		return new Line(first.toUpperCase(Locale.ROOT), List.copyOf(words));
	}

	/** True if {@code name} matches the DOS wildcard {@code pattern} ({@code *} any run, {@code ?} one character), any case. */
	public static boolean matches(String pattern, String name) {
		String p = pattern.toUpperCase(Locale.ROOT), n = name.toUpperCase(Locale.ROOT);
		if(p.equals("*.*") || p.equals("*"))
			return true;
		if(p.indexOf('.') < 0 && n.indexOf('.') >= 0 && p.indexOf('*') < 0 && p.indexOf('?') < 0)
			return false;
		return glob(p, 0, n, 0);
	}

	private static boolean glob(String p, int pi, String n, int ni) {
		while(pi < p.length()) {
			char c = p.charAt(pi);
			if(c == '*') {
				for(int k = ni; k <= n.length(); k++)
					if(glob(p, pi + 1, n, k))
						return true;
				return false;
			}
			if(ni >= n.length() || c != '?' && c != n.charAt(ni))
				return false;
			pi++;
			ni++;
		}
		return ni == n.length();
	}

	/** True if {@code word} holds a wildcard. */
	public static boolean isWildcard(String word) {
		return word.indexOf('*') >= 0 || word.indexOf('?') >= 0;
	}

	/**
	 * The program command among {@code commands} (as the registry has them, "RTTY") that {@code word} runs: the command
	 * itself or with ".EXE", any case. Null if none.
	 */
	public static String program(String word, Collection<String> commands) {
		String w = word.toUpperCase(Locale.ROOT);
		if(w.endsWith(".EXE"))
			w = w.substring(0, w.length() - 4);
		for(String command : commands)
			if(command.equalsIgnoreCase(w))
				return command;
		return null;
	}

	/**
	 * Tab completion of the last word of {@code line} from {@code candidates} (file and program names): the line with
	 * that word made as long as every candidate starting with it agrees on (the whole name if only one does), or the line
	 * unchanged if none does.
	 */
	public static String complete(String line, Collection<String> candidates) {
		int start = line.lastIndexOf(' ') + 1;
		String word = line.substring(start).toUpperCase(Locale.ROOT);
		String common = null;
		for(String candidate : candidates) {
			String c = candidate.toUpperCase(Locale.ROOT);
			if(!c.startsWith(word))
				continue;
			if(common == null)
				common = c;
			else {
				int i = 0;
				while(i < common.length() && i < c.length() && common.charAt(i) == c.charAt(i))
					i++;
				common = common.substring(0, i);
			}
		}
		return common == null || common.length() <= word.length() ? line : line.substring(0, start) + common;
	}

	/** The command lines typed, newest last, for up and down (DOSKEY). */
	public static final class History {

		private final int max;
		private final List<String> lines = new ArrayList<>();
		/** Where up and down are: lines.size() is the line being typed. */
		private int at = 0;

		public History(int max) {
			this.max = max;
		}

		/** A line was run: kept (not twice in a row, never empty), and up starts from it. */
		public void add(String line) {
			if(!line.isBlank() && (lines.isEmpty() || !lines.getLast().equals(line))) {
				lines.add(line);
				if(lines.size() > max)
					lines.removeFirst();
			}
			at = lines.size();
		}

		/** The line before the one shown, or null at the oldest. */
		public String up() {
			if(at <= 0)
				return null;
			return lines.get(--at);
		}

		/** The line after the one shown; "" past the newest (a fresh line), or null if already there. */
		public String down() {
			if(at >= lines.size())
				return null;
			at++;
			return at == lines.size() ? "" : lines.get(at);
		}

		public List<String> lines() {
			return List.copyOf(lines);
		}
	}
}
