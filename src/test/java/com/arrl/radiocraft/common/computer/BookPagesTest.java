package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A document laid out as book pages for the scanner's printer, and a scanned book read back (1.12 step 5). */
class BookPagesTest {

	@Test
	void anEmptyDocumentIsOneEmptyPage() {
		assertEquals(List.of(""), BookPages.paginate(""));
	}

	@Test
	void linesWrapAtWordsAndLongWordsAreCut() {
		List<String> pages = BookPages.paginate("the quick brown fox jumps over the lazy dog");
		assertEquals(1, pages.size());
		for(String line : pages.getFirst().split("\n"))
			assertTrue(line.length() <= BookPages.LINE_CHARS, "line too long: " + line);
		assertEquals("the quick brown fox\njumps over the lazy\ndog", pages.getFirst());
		String word = "x".repeat(BookPages.LINE_CHARS * 2 + 3);
		assertEquals("x".repeat(BookPages.LINE_CHARS) + "\n" + "x".repeat(BookPages.LINE_CHARS) + "\nxxx", BookPages.paginate(word).getFirst());
	}

	@Test
	void aPageHoldsFourteenLines() {
		StringBuilder text = new StringBuilder();
		for(int i = 0; i < BookPages.PAGE_LINES + 1; i++)
			text.append("line ").append(i).append('\n');
		List<String> pages = BookPages.paginate(text.toString().strip());
		assertEquals(2, pages.size());
		assertEquals(BookPages.PAGE_LINES, pages.getFirst().split("\n").length);
		assertEquals("line 14", pages.get(1));
	}

	@Test
	void formFeedsStartPagesAndJoinReadsBack() {
		List<String> pages = List.of("first page", "second\npage", "third");
		String joined = BookPages.join(pages);
		assertEquals(pages, BookPages.paginate(joined));
	}

	@Test
	void atMostAHundredPages() {
		List<String> many = new ArrayList<>();
		for(int i = 0; i < 150; i++)
			many.add("page " + i);
		assertEquals(BookPages.MAX_PAGES, BookPages.paginate(BookPages.join(many)).size());
	}
}
