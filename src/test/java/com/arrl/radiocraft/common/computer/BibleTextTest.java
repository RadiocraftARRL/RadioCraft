package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The BIBLE program's text (1.12 step 8): parsing, references, search, wrapping, and the gospels the mod ships. */
class BibleTextTest {

	private static final String SAMPLE = """
			#B MAT Matthew
			1:1 The book of the genealogy of Jesus Christ.
			1:2 Abraham became the father of Isaac.
			2:1 Now when Jesus was born in Bethlehem of Judea,
			#B JHN John
			1:1 In the beginning was the Word.
			3:16 For God so loved the world, that he gave his only born Son.
			3:17 For God didn't send his Son into the world to judge the world.
			""";

	@Test
	void readsBooksChaptersAndVerses() {
		BibleText bible = BibleText.parse(SAMPLE);
		assertEquals(2, bible.books().size());
		BibleText.Book matthew = bible.books().getFirst();
		assertEquals("MAT", matthew.code());
		assertEquals("Matthew", matthew.name());
		assertEquals(2, matthew.chapters().size());
		assertEquals(2, matthew.chapter(1).verses().size());
		assertEquals("Abraham became the father of Isaac.", matthew.chapter(1).verses().get(1).text());
		// John's chapters are kept in file order (1, then 3), each with the number written.
		assertEquals(3, bible.books().get(1).chapters().get(1).number());
	}

	@Test
	void findsReferencesTyped() {
		BibleText bible = BibleText.parse(SAMPLE);
		assertEquals(new BibleText.Reference(0, 2, 0), bible.reference("Matthew 2"));
		assertEquals(new BibleText.Reference(0, 1, 2), bible.reference("mat 1:2"));
		assertEquals(new BibleText.Reference(0, 1, 0), bible.reference("Mt"));
		assertEquals(new BibleText.Reference(1, 1, 1), bible.reference("jn 1:1"));
		assertNull(bible.reference("Revelation 1"));
		assertNull(bible.reference("Matthew 9"));
		assertNull(bible.reference("Matthew 1:40"));
		assertEquals("Matthew 1:2", bible.name(new BibleText.Reference(0, 1, 2)));
		assertEquals("Matthew 2", bible.name(new BibleText.Reference(0, 2, 0)));
	}

	@Test
	void searchNeedsEveryWordInAnyOrder() {
		BibleText bible = BibleText.parse(SAMPLE);
		List<BibleText.Hit> hits = bible.search("world GOD", 10);
		assertEquals(2, hits.size());
		assertEquals(new BibleText.Reference(1, 3, 16), hits.getFirst().reference());
		assertEquals(1, bible.search("world god", 1).size());
		assertTrue(bible.search("", 10).isEmpty());
		assertTrue(bible.search("locust", 10).isEmpty());
	}

	@Test
	void chaptersTurnOverIntoTheNextBook() {
		BibleText bible = BibleText.parse(SAMPLE);
		assertEquals(new BibleText.Reference(1, 1, 0), bible.nextChapter(new BibleText.Reference(0, 2, 0)));
		assertEquals(new BibleText.Reference(0, 2, 0), bible.previousChapter(new BibleText.Reference(1, 1, 0)));
		assertNull(bible.previousChapter(new BibleText.Reference(0, 1, 0)));
	}

	@Test
	void wrapsUnderTheTextWithTheNumberInTheMargin() {
		BibleText bible = BibleText.parse(SAMPLE);
		List<Integer> starts = new ArrayList<>();
		BibleText.Chapter three = bible.books().get(1).chapters().get(1);
		List<String> lines = BibleText.wrap(three, 30, starts);
		assertEquals(2, starts.size());
		assertEquals(0, starts.getFirst());
		assertTrue(lines.getFirst().startsWith("16 For God"), lines.getFirst());
		for(String line : lines)
			assertTrue(line.length() <= 30, line);
		assertTrue(lines.get(1).startsWith("   "), "a carried-on line is indented under the text");
	}

	/** The file the mod ships: the four gospels, whole, ASCII only. */
	@Test
	void theShippedGospelsAreWhole() throws IOException {
		Path file = projectRoot().resolve("src/main/resources/assets/radiocraft/computer/bible/web_gospels.txt.gz");
		String text;
		try(InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
			text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		for(int i = 0; i < text.length(); i++)
			assertTrue(text.charAt(i) == '\n' || text.charAt(i) >= ' ' && text.charAt(i) < 127, "not ASCII at " + i);
		BibleText bible = BibleText.parse(text);
		assertEquals(List.of("Matthew", "Mark", "Luke", "John"), bible.books().stream().map(BibleText.Book::name).toList());
		assertEquals(List.of(28, 16, 24, 21), bible.books().stream().map(b -> b.chapters().size()).toList());
		assertNotNull(bible.reference("John 3:16"));
		assertTrue(bible.books().get(3).chapter(3).verses().get(15).text().startsWith("For God so loved the world"));
		assertTrue(bible.search("mustard seed", 100).size() >= 3); // The WEB has "farmer" for the sower.
	}

	private static Path projectRoot() {
		for(Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent())
			if(Files.isDirectory(dir.resolve("src/main/resources/assets/radiocraft")))
				return dir;
		return Path.of("");
	}
}
