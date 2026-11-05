package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The book editor's book (1.12 step 6): pages, volumes, the line count of a book page, and its file. */
class BookDocumentTest {

	private static List<String> pages(int count) {
		List<String> out = new ArrayList<>();
		for(int i = 0; i < count; i++)
			out.add("Page " + (i + 1));
		return out;
	}

	@Test
	void anEmptyBookHasOneBlankPage() {
		BookDocument book = BookDocument.empty();
		assertEquals(1, book.pageCount());
		assertEquals("", book.page(0));
		book.removePage(0);
		assertEquals(1, book.pageCount(), "the last page is only emptied");
	}

	@Test
	void pagesGoInAndOut() {
		BookDocument book = new BookDocument("T", "A", pages(3));
		assertTrue(book.insertPage(1));
		assertEquals(4, book.pageCount());
		assertEquals("", book.page(1));
		assertEquals("Page 2", book.page(2));
		book.removePage(1);
		assertEquals("Page 2", book.page(1));
		book.setPage(0, "x".repeat(BookPages.PAGE_CHARS + 50));
		assertEquals(BookPages.PAGE_CHARS, book.page(0).length(), "a page is cut to what a book page holds");
		book.setPage(0, "one\ftwo");
		assertEquals("one\ntwo", book.page(0), "a form feed inside a page is a new line");
	}

	@Test
	void aLongBookPrintsInVolumesThatFitTheTray() {
		BookDocument book = new BookDocument("A Very Long Title For The Book!!", "Me", pages(BookDocument.PAGES_PER_VOLUME * 2 + 5));
		assertEquals(3, book.volumes());
		assertEquals(BookDocument.PAGES_PER_VOLUME, book.volumePages(0).size());
		assertEquals(5, book.volumePages(2).size());
		assertEquals("Page " + (BookDocument.PAGES_PER_VOLUME + 1), book.volumePages(1).getFirst());
		assertTrue(book.volumePages(3).isEmpty());
		assertTrue(BookDocument.PAGES_PER_VOLUME <= 64, "a volume must fit one stack of paper in the tray");
		String title = book.volumeTitle(2);
		assertTrue(title.endsWith(" Vol. 3"), title);
		assertTrue(title.length() <= BookDocument.TITLE_MAX, title);
		assertEquals("Short", new BookDocument("Short", "", pages(2)).volumeTitle(0), "one volume has no number");
		assertEquals(1, BookDocument.volumeOf(BookDocument.PAGES_PER_VOLUME));
		assertEquals(BookDocument.PAGES_PER_VOLUME + 1, BookDocument.volumeStart(1));
	}

	@Test
	void countsTheLinesABookPageTakes() {
		assertEquals(1, BookDocument.bookLines(""));
		assertEquals(3, BookDocument.bookLines("the quick brown fox jumps over the lazy dog"));
		assertFalse(BookDocument.overflows("a\n".repeat(13) + "b"));
		assertTrue(BookDocument.overflows("a\n".repeat(14) + "b"));
	}

	@Test
	void savesAndLoads() {
		BookDocument book = new BookDocument("Field Day", "W1BTR", List.of("One", "Two\nlines", ""));
		BookDocument back = BookDocument.fromTag(ComputerFileFormats.nbt(ComputerFileFormats.nbt(book.toTag())));
		assertEquals("Field Day", back.title());
		assertEquals("W1BTR", back.author());
		assertEquals(book.pages(), back.pages());
		assertEquals("One\fTwo\nlines\f", back.toText());
	}

	@Test
	void titlesAreCut() {
		BookDocument book = BookDocument.empty();
		book.setTitle("x".repeat(40));
		assertEquals(BookDocument.TITLE_MAX, book.title().length());
	}
}
