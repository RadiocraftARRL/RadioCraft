package com.arrl.radiocraft.common.computer.license;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One licence exam question pool for the License Study program (computer apps 2, 2026-11-06), as read from a pool file:
 * the shipped US NCVEC pools in {@code data/radiocraft/license_pools/}, or a server's own in a data pack or
 * {@code config/radiocraft/license_pools/} (see {@link LicensePools}). The format, one JSON object per file:
 * <pre>
 * {"id": "technician", "name": "Technician", "element": "Element 2", "valid": "July 1, 2026 to June 30, 2030",
 *  "pass_percent": 74, "order": 1, "subelements": {"T1": "Commission's rules", ...},
 *  "questions": [{"id": "T1A01", "question": "...", "answers": ["...", "...", "...", "..."], "correct": "C",
 *                 "refs": "[97.1]", "figure": "T-1"}]}
 * </pre>
 * Only {@code questions} is required (each with {@code id}, {@code question}, two to six {@code answers} and
 * {@code correct}, a letter "A".. or an index 0..); the id defaults to the file's name. A question's subelement is the
 * first two characters of its id (T1), its group the first three (T1A): a practice exam draws one question per group.
 * A question with a {@code figure} needs a picture the computer cannot show, so study and exams skip it. Bad questions
 * are left out (see {@link #problems}). Pure (Gson only), so it is unit tested.
 */
public final class LicensePool {

	/** One question. {@code correct} is the index of the right answer. {@code figure} is "" when it needs none. */
	public record Question(String id, String text, List<String> answers, int correct, String refs, String figure) {

		public String subelement() {
			return id.substring(0, Math.min(2, id.length()));
		}

		public String group() {
			return id.substring(0, Math.min(3, id.length()));
		}

		public boolean hasFigure() {
			return !figure.isEmpty();
		}

		/** The letter of answer {@code index}: A, B, C... */
		public static char letter(int index) {
			return (char)('A' + index);
		}
	}

	public static final int MAX_ANSWERS = 6;
	public static final int DEFAULT_PASS_PERCENT = 74;

	private final String id, name, element, valid;
	private final int passPercent, order;
	private final Map<String, String> subelementNames;
	private final List<Question> questions;
	private final Map<String, Question> byId = new LinkedHashMap<>();
	private final List<String> subelements = new ArrayList<>();
	private final Map<String, List<Question>> groups = new LinkedHashMap<>();
	private final List<String> problems;

	public LicensePool(String id, String name, String element, String valid, int passPercent, int order, Map<String, String> subelementNames,
	                   List<Question> questions, List<String> problems) {
		this.id = id;
		this.name = name;
		this.element = element;
		this.valid = valid;
		this.passPercent = passPercent;
		this.order = order;
		this.subelementNames = Collections.unmodifiableMap(new LinkedHashMap<>(subelementNames));
		this.questions = List.copyOf(questions);
		this.problems = List.copyOf(problems);
		for(Question question : this.questions) {
			byId.put(question.id(), question);
			if(!subelements.contains(question.subelement()))
				subelements.add(question.subelement()); // In the order the pool lists them (NCVEC puts T0, safety, last).
			groups.computeIfAbsent(question.group(), g -> new ArrayList<>()).add(question);
		}
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String element() {
		return element;
	}

	public String valid() {
		return valid;
	}

	public int passPercent() {
		return passPercent;
	}

	public int order() {
		return order;
	}

	public List<Question> questions() {
		return questions;
	}

	/** Questions study and exams use: those needing no figure. */
	public List<Question> studyable() {
		return questions.stream().filter(q -> !q.hasFigure()).toList();
	}

	@Nullable
	public Question question(String questionId) {
		return byId.get(questionId);
	}

	/** The subelements, in the pool's order. */
	public List<String> subelements() {
		return Collections.unmodifiableList(subelements);
	}

	/** A subelement's name ("Commission's rules"), or "" if the file names none. */
	public String subelementName(String code) {
		return subelementNames.getOrDefault(code, "");
	}

	/** Each group's questions, groups in the pool's order. */
	public Map<String, List<Question>> groups() {
		return Collections.unmodifiableMap(groups);
	}

	/** The groups an exam draws from: those with at least one question needing no figure. */
	public List<String> examGroups() {
		List<String> out = new ArrayList<>();
		groups.forEach((group, list) -> {
			if(list.stream().anyMatch(q -> !q.hasFigure()))
				out.add(group);
		});
		return out;
	}

	/** Groups an exam has to leave out: every question in them needs a figure. */
	public List<String> skippedGroups() {
		List<String> out = new ArrayList<>(groups.keySet());
		out.removeAll(examGroups());
		return out;
	}

	/** Questions on a practice exam: one per {@link #examGroups() group}. */
	public int examSize() {
		return examGroups().size();
	}

	/** Correct answers needed to pass: {@link #passPercent} of {@link #examSize}, rounded up (26 of 35, 37 of 50). */
	public int passMark() {
		return passMark(examSize(), passPercent);
	}

	public static int passMark(int questions, int percent) {
		return (questions * percent + 99) / 100;
	}

	/** What was wrong with the file: questions left out, and why. */
	public List<String> problems() {
		return problems;
	}

	// -------------------- PARSING --------------------

	public static LicensePool parse(String json, String fallbackId) {
		JsonElement element = JsonParser.parseString(json);
		if(!element.isJsonObject())
			throw new IllegalArgumentException("a pool file is a JSON object");
		return parse(element.getAsJsonObject(), fallbackId);
	}

	/**
	 * Reads a pool from {@code json}; {@code fallbackId} names it when the file does not. Throws
	 * {@link IllegalArgumentException} if the file has no usable question at all; single bad questions are only left
	 * out and noted in {@link #problems}.
	 */
	public static LicensePool parse(JsonObject json, String fallbackId) {
		String id = string(json, "id", fallbackId).toLowerCase(Locale.ROOT).trim();
		if(id.isEmpty())
			throw new IllegalArgumentException("the pool has no id");
		String name = string(json, "name", id);
		int passPercent = json.has("pass_percent") ? json.get("pass_percent").getAsInt() : DEFAULT_PASS_PERCENT;
		if(passPercent < 1 || passPercent > 100)
			throw new IllegalArgumentException("pass_percent must be 1 to 100");
		int order = json.has("order") ? json.get("order").getAsInt() : 100;
		Map<String, String> names = new LinkedHashMap<>();
		if(json.has("subelements") && json.get("subelements").isJsonObject())
			for(Map.Entry<String, JsonElement> entry : json.getAsJsonObject("subelements").entrySet())
				names.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue().getAsString());
		if(!json.has("questions") || !json.get("questions").isJsonArray())
			throw new IllegalArgumentException("the pool has no questions list");
		List<Question> questions = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		int index = 0;
		for(JsonElement entry : json.getAsJsonArray("questions")) {
			index++;
			try {
				Question question = question(entry);
				if(!seen.add(question.id()))
					throw new IllegalArgumentException("id " + question.id() + " is used twice");
				questions.add(question);
			}
			catch(RuntimeException e) { // IllegalArgument, IllegalState and UnsupportedOperation (wrong JSON types).
				problems.add("question " + index + ": " + e.getMessage());
			}
		}
		if(questions.isEmpty())
			throw new IllegalArgumentException("the pool has no usable question" + (problems.isEmpty() ? "" : " (" + problems.getFirst() + ")"));
		return new LicensePool(id, name, string(json, "element", ""), string(json, "valid", ""), passPercent, order, names, questions, problems);
	}

	private static Question question(JsonElement element) {
		if(!element.isJsonObject())
			throw new IllegalArgumentException("not an object");
		JsonObject json = element.getAsJsonObject();
		String id = string(json, "id", "").toUpperCase(Locale.ROOT).trim();
		if(id.length() < 3)
			throw new IllegalArgumentException("no id (like T1A01)");
		String text = string(json, "question", "").trim();
		if(text.isEmpty())
			throw new IllegalArgumentException(id + " has no question");
		if(!json.has("answers") || !json.get("answers").isJsonArray())
			throw new IllegalArgumentException(id + " has no answers");
		JsonArray array = json.getAsJsonArray("answers");
		List<String> answers = new ArrayList<>();
		for(JsonElement answer : array)
			answers.add(answer.getAsString().trim());
		if(answers.size() < 2 || answers.size() > MAX_ANSWERS)
			throw new IllegalArgumentException(id + " needs 2 to " + MAX_ANSWERS + " answers");
		if(!json.has("correct"))
			throw new IllegalArgumentException(id + " has no correct answer");
		int correct = correct(json.get("correct"));
		if(correct < 0 || correct >= answers.size())
			throw new IllegalArgumentException(id + "'s correct answer is not one of its answers");
		return new Question(id, text, List.copyOf(answers), correct, string(json, "refs", ""), string(json, "figure", "").trim());
	}

	/** "C", "c" or 2. */
	private static int correct(JsonElement element) {
		if(element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber())
			return element.getAsInt();
		String letter = element.getAsString().trim().toUpperCase(Locale.ROOT);
		return letter.length() == 1 ? letter.charAt(0) - 'A' : -1;
	}

	private static String string(JsonObject json, String key, String fallback) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
	}
}
