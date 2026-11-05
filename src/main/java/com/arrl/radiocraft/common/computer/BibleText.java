package com.arrl.radiocraft.common.computer;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The scripture the radio computer's BIBLE program reads (1.12 step 8): the four gospels of the World English Bible
 * (public domain; see {@code doc/Credits-Music.md}), from {@code assets/radiocraft/computer/bible/web_gospels.txt.gz}.
 * The file is a {@code #B <code> <name>} line per book, then a {@code <chapter>:<verse> <text>} line per verse, in
 * order ({@code tools/bible_gospels.py} makes it). Pure (no game classes), so it is unit tested; the client loads the
 * resource and hands its text to {@link #parse}.
 */
public final class BibleText {

	/** One verse. */
	public record Verse(int number, String text) {}

	/** One chapter: its verses in order. */
	public record Chapter(int number, List<Verse> verses) {}

	/** One book: its three-letter code (MAT), its name and chapters. */
	public record Book(String code, String name, List<Chapter> chapters) {
		/** Chapter {@code number} (1 the first), or null. */
		@Nullable
		public Chapter chapter(int number) {
			return number >= 1 && number <= chapters.size() ? chapters.get(number - 1) : null;
		}
	}

	/** A place in the text: a book (index in {@link #books()}), chapter (1 up) and verse (1 up, 0 for the whole chapter). */
	public record Reference(int book, int chapter, int verse) {}

	/** A verse a search found. */
	public record Hit(Reference reference, String text) {}

	private static final Pattern VERSE = Pattern.compile("(\\d+):(\\d+) (.*)");
	/** "John 3:16", "jn 3", "Mark 4:3-9" (the end is ignored), "Luke". */
	private static final Pattern REFERENCE = Pattern.compile("\\s*([1-3]?\\s*[a-zA-Z]+)\\.?\\s*(?:(\\d+)(?:\\s*[:.]\\s*(\\d+))?)?.*");

	private final List<Book> books;

	private BibleText(List<Book> books) {
		this.books = List.copyOf(books);
	}

	public List<Book> books() {
		return books;
	}

	/** The text of {@code file} (see the class comment). Lines that are neither a book nor a verse are skipped. */
	public static BibleText parse(String file) {
		List<Book> books = new ArrayList<>();
		String code = null, name = null;
		List<Chapter> chapters = new ArrayList<>();
		List<Verse> verses = null;
		int chapterNumber = 0;
		for(String line : file.split("\n")) {
			line = line.strip();
			if(line.startsWith("#B ")) {
				if(code != null)
					books.add(new Book(code, name, List.copyOf(closeChapter(chapters, chapterNumber, verses))));
				String[] parts = line.substring(3).split(" ", 2);
				code = parts[0];
				name = parts.length > 1 ? parts[1] : parts[0];
				chapters = new ArrayList<>();
				verses = null;
				chapterNumber = 0;
				continue;
			}
			Matcher m = VERSE.matcher(line);
			if(code == null || !m.matches())
				continue;
			int c = Integer.parseInt(m.group(1));
			if(c != chapterNumber) {
				closeChapter(chapters, chapterNumber, verses);
				chapterNumber = c;
				verses = new ArrayList<>();
			}
			verses.add(new Verse(Integer.parseInt(m.group(2)), m.group(3)));
		}
		if(code != null)
			books.add(new Book(code, name, List.copyOf(closeChapter(chapters, chapterNumber, verses))));
		return new BibleText(books);
	}

	private static List<Chapter> closeChapter(List<Chapter> chapters, int number, @Nullable List<Verse> verses) {
		if(verses != null && !verses.isEmpty())
			chapters.add(new Chapter(number, List.copyOf(verses)));
		return chapters;
	}

	/** The book whose name or code starts with {@code name} (case and spaces ignored; "jn", "jhn" and "john" find John), or -1. */
	public int book(String name) {
		String wanted = name.toLowerCase(Locale.ROOT).replace(" ", "");
		if(wanted.isEmpty())
			return -1;
		for(int i = 0; i < books.size(); i++) {
			Book book = books.get(i);
			if(book.name().toLowerCase(Locale.ROOT).startsWith(wanted) || book.code().toLowerCase(Locale.ROOT).equals(wanted))
				return i;
		}
		// Common short forms: Mt, Mk, Lk, Jn.
		for(int i = 0; i < books.size(); i++) {
			String n = books.get(i).name().toLowerCase(Locale.ROOT);
			if(wanted.length() == 2 && n.charAt(0) == wanted.charAt(0) && n.indexOf(wanted.charAt(1), 1) > 0)
				return i;
		}
		return -1;
	}

	/**
	 * A typed reference ("John 3:16", "Mk 4", "luke"), or null if it names no book, chapter or verse there is. A book
	 * alone is its chapter 1; a chapter alone is verse 0 (the top of the chapter).
	 */
	@Nullable
	public Reference reference(String typed) {
		Matcher m = REFERENCE.matcher(typed);
		if(!m.matches())
			return null;
		int book = book(m.group(1));
		if(book < 0)
			return null;
		int chapter = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
		Chapter c = books.get(book).chapter(chapter);
		if(c == null)
			return null;
		int verse = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
		if(verse > 0 && verse > c.verses().getLast().number())
			return null;
		return new Reference(book, chapter, verse);
	}

	/** "John 3:16" (or "John 3" for verse 0). */
	public String name(Reference reference) {
		Book book = books.get(reference.book());
		return book.name() + " " + reference.chapter() + (reference.verse() > 0 ? ":" + reference.verse() : "");
	}

	/**
	 * Verses holding every word of {@code query} (case ignored, any order), in book order, at most {@code max}.
	 * Words shorter than two letters are ignored; an empty query finds nothing.
	 */
	public List<Hit> search(String query, int max) {
		List<String> words = new ArrayList<>();
		for(String word : query.toLowerCase(Locale.ROOT).split("[^a-z0-9']+"))
			if(word.length() >= 2)
				words.add(word);
		List<Hit> hits = new ArrayList<>();
		if(words.isEmpty())
			return hits;
		for(int b = 0; b < books.size(); b++)
			for(Chapter chapter : books.get(b).chapters())
				for(Verse verse : chapter.verses()) {
					String text = verse.text().toLowerCase(Locale.ROOT);
					boolean all = true;
					for(String word : words)
						if(!text.contains(word)) {
							all = false;
							break;
						}
					if(all) {
						hits.add(new Hit(new Reference(b, chapter.number(), verse.number()), verse.text()));
						if(hits.size() >= max)
							return hits;
					}
				}
		return hits;
	}

	/** The chapter after {@code r}'s (the next book's first after a book's last), or null at the end of John. */
	@Nullable
	public Reference nextChapter(Reference r) {
		if(r.chapter() < books.get(r.book()).chapters().size())
			return new Reference(r.book(), r.chapter() + 1, 0);
		return r.book() + 1 < books.size() ? new Reference(r.book() + 1, 1, 0) : null;
	}

	/** The chapter before {@code r}'s (the last of the book before), or null at Matthew 1. */
	@Nullable
	public Reference previousChapter(Reference r) {
		if(r.chapter() > 1)
			return new Reference(r.book(), r.chapter() - 1, 0);
		return r.book() > 0 ? new Reference(r.book() - 1, books.get(r.book() - 1).chapters().size(), 0) : null;
	}

	/**
	 * A chapter as screen lines {@code width} wide: each verse starts a line with its number, the text word-wrapped
	 * after it with the following lines indented under the text. {@code starts} gets, for each verse in order, the
	 * index of its first line.
	 */
	public static List<String> wrap(Chapter chapter, int width, List<Integer> starts) {
		List<String> lines = new ArrayList<>();
		for(Verse verse : chapter.verses()) {
			starts.add(lines.size());
			String number = String.valueOf(verse.number());
			int indent = Math.max(3, number.length() + 1);
			String pad = " ".repeat(indent);
			StringBuilder line = new StringBuilder(number).append(" ".repeat(indent - number.length()));
			for(String word : verse.text().split(" ")) {
				if(word.isEmpty())
					continue;
				if(line.length() > indent && line.length() + 1 + word.length() > width) {
					lines.add(line.toString());
					line.setLength(0);
					line.append(pad);
				}
				else if(line.length() > indent)
					line.append(' ');
				if(word.length() > width - indent)
					word = word.substring(0, width - indent);
				line.append(word);
			}
			lines.add(line.toString());
		}
		return lines;
	}
}
