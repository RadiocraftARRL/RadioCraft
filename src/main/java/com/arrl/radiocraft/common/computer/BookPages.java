package com.arrl.radiocraft.common.computer;

import java.util.ArrayList;
import java.util.List;

/**
 * Text laid out as the pages of a Minecraft book, for printing a document through the scanner and for reading a
 * scanned book back as one text. A book page shows about 14 lines of 19 characters; words are wrapped, long words cut,
 * and a form feed ({@code \f}) starts a new page. Pure, so it is unit tested.
 */
public final class BookPages {

	/** A written book's most pages (the game's limit). */
	public static final int MAX_PAGES = 100;
	/** Most characters kept on one page (a book and quill's limit). */
	public static final int PAGE_CHARS = 1024;
	public static final int LINE_CHARS = 19, PAGE_LINES = 14;
	/** Starts a new page in a document, as on a line printer. */
	public static final char PAGE_BREAK = '\f';

	private BookPages() {}

	/** {@code text} as book pages (at least one, even if empty), at most {@link #MAX_PAGES}. */
	public static List<String> paginate(String text) {
		List<String> pages = new ArrayList<>();
		List<String> lines = new ArrayList<>();
		String normal = text.replace("\r\n", "\n").replace('\r', '\n');
		for(String paragraph : normal.split("\n", -1)) {
			int feed;
			while((feed = paragraph.indexOf(PAGE_BREAK)) >= 0) { // A form feed ends the page here.
				if(feed > 0 || !lines.isEmpty())
					wrap(paragraph.substring(0, feed), lines, pages);
				flush(lines, pages, false);
				paragraph = paragraph.substring(feed + 1);
			}
			wrap(paragraph, lines, pages);
		}
		flush(lines, pages, pages.isEmpty());
		while(pages.size() > 1 && pages.getLast().isBlank()) // No blank pages at the end.
			pages.removeLast();
		return pages.size() > MAX_PAGES ? new ArrayList<>(pages.subList(0, MAX_PAGES)) : pages;
	}

	/** Book pages read back as one text: pages joined with a form feed between them, as {@link #paginate} reads them. */
	public static String join(List<String> pages) {
		return String.join(String.valueOf(PAGE_BREAK), pages);
	}

	private static void wrap(String paragraph, List<String> lines, List<String> pages) {
		if(paragraph.isEmpty()) {
			addLine("", lines, pages);
			return;
		}
		StringBuilder line = new StringBuilder();
		for(String word : paragraph.split(" ", -1)) {
			while(word.length() > LINE_CHARS) { // Longer than a line: cut it.
				if(!line.isEmpty()) {
					addLine(line.toString(), lines, pages);
					line.setLength(0);
				}
				addLine(word.substring(0, LINE_CHARS), lines, pages);
				word = word.substring(LINE_CHARS);
			}
			int length = line.isEmpty() ? word.length() : line.length() + 1 + word.length();
			if(length > LINE_CHARS) {
				addLine(line.toString(), lines, pages);
				line.setLength(0);
			}
			if(!line.isEmpty())
				line.append(' ');
			line.append(word);
		}
		addLine(line.toString(), lines, pages);
	}

	private static void addLine(String line, List<String> lines, List<String> pages) {
		lines.add(line);
		if(lines.size() >= PAGE_LINES)
			flush(lines, pages, true);
	}

	private static void flush(List<String> lines, List<String> pages, boolean evenIfEmpty) {
		if(lines.isEmpty() && !evenIfEmpty)
			return;
		String page = String.join("\n", lines);
		pages.add(page.length() > PAGE_CHARS ? page.substring(0, PAGE_CHARS) : page);
		lines.clear();
	}
}
