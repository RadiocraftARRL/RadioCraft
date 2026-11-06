package com.arrl.radiocraft.common.computer.license;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicensePoolTest {

	@Test
	void readsLettersAndIndexes() {
		LicensePool pool = LicensePool.parse("""
				{"name": "Test", "pass_percent": 70, "subelements": {"x1": "First"},
				 "questions": [
				  {"id": "X1A01", "question": "Q1?", "answers": ["a", "b", "c", "d"], "correct": "c"},
				  {"id": "x1a02", "question": "Q2?", "answers": ["a", "b"], "correct": 1, "refs": "[97.1]"},
				  {"id": "X2A01", "question": "Q3?", "answers": ["a", "b", "c"], "correct": "A", "figure": "X-1"}]}""", "mypool");
		assertEquals("mypool", pool.id());
		assertEquals("Test", pool.name());
		assertEquals(70, pool.passPercent());
		assertEquals(2, pool.question("X1A01").correct());
		assertEquals(1, pool.question("X1A02").correct());
		assertEquals("[97.1]", pool.question("X1A02").refs());
		assertEquals("First", pool.subelementName("X1"));
		assertEquals("", pool.subelementName("X2"));
		assertTrue(pool.question("X2A01").hasFigure());
		assertEquals(2, pool.studyable().size());
		assertEquals(2, pool.subelements().size());
		assertEquals(1, pool.examSize()); // X2A has only a figure question.
		assertEquals(java.util.List.of("X2A"), pool.skippedGroups());
		assertTrue(pool.problems().isEmpty());
	}

	@Test
	void leavesOutBadQuestions() {
		LicensePool pool = LicensePool.parse("""
				{"id": "Bad", "questions": [
				  {"id": "B1A01", "question": "Fine?", "answers": ["a", "b"], "correct": "B"},
				  {"id": "B1A02", "question": "Wrong letter?", "answers": ["a", "b"], "correct": "E"},
				  {"id": "B1A03", "question": "One answer?", "answers": ["a"], "correct": 0},
				  {"id": "B1A04", "answers": ["a", "b"], "correct": 0},
				  {"id": "B1A01", "question": "Twice?", "answers": ["a", "b"], "correct": 0},
				  "not even an object"]}""", "ignored");
		assertEquals("bad", pool.id());
		assertEquals(1, pool.questions().size());
		assertEquals(5, pool.problems().size());
		assertNull(pool.question("B1A02"));
	}

	@Test
	void refusesAPoolWithNothingUsable() {
		assertThrows(IllegalArgumentException.class, () -> LicensePool.parse("{\"questions\": []}", "x"));
		assertThrows(IllegalArgumentException.class, () -> LicensePool.parse("{\"name\": \"no list\"}", "x"));
		assertThrows(IllegalArgumentException.class, () -> LicensePool.parse("[1, 2]", "x"));
		assertThrows(IllegalArgumentException.class, () -> LicensePool.parse("{\"pass_percent\": 0, \"questions\": []}", "x"));
	}

	@Test
	void passMarkRoundsUp() {
		assertEquals(26, LicensePool.passMark(35, 74));
		assertEquals(37, LicensePool.passMark(50, 74));
		assertEquals(10, LicensePool.passMark(10, 100));
	}

	@Test
	void theShippedPoolsParse() throws IOException {
		check("technician", 409, 35, 26);
		check("general", 423, 35, 26);
		check("extra", 599, 50, 37);
	}

	private static void check(String id, int questions, int groups, int passMark) throws IOException {
		Path file = projectRoot().resolve("src/main/resources/data/radiocraft/license_pools/" + id + ".json");
		LicensePool pool = LicensePool.parse(Files.readString(file, StandardCharsets.UTF_8), "file");
		assertEquals(id, pool.id());
		assertEquals(questions, pool.questions().size(), id);
		assertTrue(pool.problems().isEmpty(), id + ": " + pool.problems());
		assertEquals(groups, pool.groups().size(), id);
		assertEquals(groups, pool.examSize(), id + ": a group has only figure questions");
		assertEquals(passMark, pool.passMark(), id);
		assertEquals(10, pool.subelements().size(), id);
		for(String sub : pool.subelements())
			assertFalse(pool.subelementName(sub).isEmpty(), id + " " + sub + " has no name");
		assertTrue(pool.questions().stream().anyMatch(LicensePool.Question::hasFigure), id + " lost its figure marks");
	}

	private static Path projectRoot() {
		for(Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent())
			if(Files.isDirectory(dir.resolve("src/main/resources/data/radiocraft")))
				return dir;
		throw new IllegalStateException("project root not found");
	}
}
