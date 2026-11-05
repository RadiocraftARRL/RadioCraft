package com.arrl.radiocraft.common.computer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A book being written in the radio computer's book editor (1.12 step 6): a title, an author and any number of pages
 * (up to {@link #MAX_PAGES}, and the 256K a file may hold), kept on the computer as a {@code .BOK} file. A written book
 * holds at most {@link #PAGES_PER_VOLUME} of them, so a long book prints as several volumes. Pure (only NBT), so it is
 * unit tested.
 * <p>
 * File layout (compressed NBT, {@link ComputerFileFormats#BOOK}): {@code title} (string), {@code author} (string),
 * {@code pages} (list of strings).
 */
public final class BookDocument {

	/** Most pages one book file may have. The file's 256K usually runs out first. */
	public static final int MAX_PAGES = 1000;
	/** Longest title and author a written book takes. */
	public static final int TITLE_MAX = 32, AUTHOR_MAX = 32;
	/**
	 * Pages in one printed volume: a full paper tray (64 sheets, a sheet a page), within the game's 100 pages a book.
	 */
	public static final int PAGES_PER_VOLUME = 64;

	private String title;
	private String author;
	private final List<String> pages = new ArrayList<>();

	/** A book of {@code pages} (at least one: an empty book has one blank page). */
	public BookDocument(String title, String author, List<String> pages) {
		this.title = cut(title, TITLE_MAX);
		this.author = cut(author, AUTHOR_MAX);
		for(String page : pages) {
			if(this.pages.size() >= MAX_PAGES)
				break;
			this.pages.add(cleanPage(page));
		}
		if(this.pages.isEmpty())
			this.pages.add("");
	}

	public static BookDocument empty() {
		return new BookDocument("", "", List.of());
	}

	/** A book made from a plain text, laid out on pages as the printer would ({@link BookPages#paginate}). */
	public static BookDocument fromText(String title, String author, String text) {
		return new BookDocument(title, author, BookPages.paginate(text));
	}

	// -------------------- WHAT IT HOLDS --------------------

	public String title() {
		return title;
	}

	public void setTitle(String value) {
		title = cut(value, TITLE_MAX);
	}

	public String author() {
		return author;
	}

	public void setAuthor(String value) {
		author = cut(value, AUTHOR_MAX);
	}

	public int pageCount() {
		return pages.size();
	}

	/** Page {@code index} (0-based), or "" past the end. */
	public String page(int index) {
		return index >= 0 && index < pages.size() ? pages.get(index) : "";
	}

	public List<String> pages() {
		return Collections.unmodifiableList(pages);
	}

	/** Replaces page {@code index}'s text (cut to {@link BookPages#PAGE_CHARS}). */
	public void setPage(int index, String text) {
		if(index >= 0 && index < pages.size())
			pages.set(index, cleanPage(text));
	}

	/** A blank page put in at {@code index} (0 to the page count); false if the book is full. */
	public boolean insertPage(int index) {
		if(pages.size() >= MAX_PAGES)
			return false;
		pages.add(Math.max(0, Math.min(index, pages.size())), "");
		return true;
	}

	/** Takes page {@code index} out; the last page left is only emptied. */
	public void removePage(int index) {
		if(index < 0 || index >= pages.size())
			return;
		if(pages.size() == 1)
			pages.set(0, "");
		else
			pages.remove(index);
	}

	/** The whole book as one text, pages split by a form feed (what EDIT and the printer read). */
	public String toText() {
		return BookPages.join(pages);
	}

	// -------------------- PRINTING --------------------

	/** How many written books it prints as. */
	public int volumes() {
		return Math.max(1, (pages.size() + PAGES_PER_VOLUME - 1) / PAGES_PER_VOLUME);
	}

	/** Volume {@code index}'s pages (0-based; empty past the end). */
	public List<String> volumePages(int index) {
		int from = index * PAGES_PER_VOLUME;
		if(index < 0 || from >= pages.size())
			return List.of();
		return List.copyOf(pages.subList(from, Math.min(pages.size(), from + PAGES_PER_VOLUME)));
	}

	/** The title volume {@code index} is printed under: the title, with "Vol. 2" when there is more than one. */
	public String volumeTitle(int index) {
		String base = title.isBlank() ? "Untitled" : title.strip();
		if(volumes() <= 1)
			return base;
		String suffix = " Vol. " + (index + 1);
		return cut(base, TITLE_MAX - suffix.length()).strip() + suffix;
	}

	/** The first page (1-based) of volume {@code index}, for the screen. */
	public static int volumeStart(int index) {
		return index * PAGES_PER_VOLUME + 1;
	}

	/** Which volume page {@code index} (0-based) prints in. */
	public static int volumeOf(int index) {
		return Math.max(0, index) / PAGES_PER_VOLUME;
	}

	/**
	 * About how many lines {@code page} takes in a written book, as the printer wraps it (19 characters a line). More
	 * than {@link BookPages#PAGE_LINES} and the end of the page is cut off when read in the game.
	 */
	public static int bookLines(String page) {
		List<String> laid = BookPages.paginate(page.replace(BookPages.PAGE_BREAK, '\n'));
		return (laid.size() - 1) * BookPages.PAGE_LINES + laid.getLast().split("\n", -1).length;
	}

	/** True if {@code page} has more lines than a book page shows. */
	public static boolean overflows(String page) {
		return bookLines(page) > BookPages.PAGE_LINES;
	}

	// -------------------- FILE --------------------

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("title", title);
		tag.putString("author", author);
		ListTag list = new ListTag();
		for(String page : pages)
			list.add(StringTag.valueOf(page));
		tag.put("pages", list);
		return tag;
	}

	public static BookDocument fromTag(CompoundTag tag) {
		ListTag list = tag.getList("pages", Tag.TAG_STRING);
		List<String> pages = new ArrayList<>(list.size());
		for(int i = 0; i < list.size(); i++)
			pages.add(list.getString(i));
		return new BookDocument(tag.getString("title"), tag.getString("author"), pages);
	}

	private static String cleanPage(String page) {
		String normal = page == null ? "" : page.replace("\r\n", "\n").replace('\r', '\n').replace(BookPages.PAGE_BREAK, '\n');
		return normal.length() > BookPages.PAGE_CHARS ? normal.substring(0, BookPages.PAGE_CHARS) : normal;
	}

	private static String cut(String text, int max) {
		String clean = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ');
		return clean.length() > max ? clean.substring(0, Math.max(0, max)) : clean;
	}
}
