package com.arrl.radiocraft.common.contest;

import com.arrl.radiocraft.common.computer.BookPages;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

/**
 * The scoring and the report book of a {@code /contest} (2026-11-09), pure so it is unit tested. A contest's log is a
 * list of {@link Qso}s, each a verified two-way contact made while it ran (see {@link Contests}). From it come the four
 * winners (longest contact, most unique contacts, most biomes activated, most biomes reached; everyone tied is listed)
 * and the pages of a written book: the contest's name and times, the winners, then each player's contacts.
 * <p>
 * Pages keep to a written book's 14 lines of 19 characters ({@link BookPages}); a contact's lines are never split
 * across two pages, and each player starts a new page. A book holds at most 100 pages: a longer report ends with a
 * page saying the rest is in the server log ({@link Report#fullText}).
 */
public final class ContestReport {

	public static final String AUTHOR = "RadioCraft";
	public static final String DEFAULT_TITLE = "Contest Report";
	/**
	 * The same two stations on the same band again within this many ticks (10 minutes) is the same contact still going
	 * on: the contact book re-verifies a pair every few seconds while they talk. Later than that it is logged again, as a
	 * dupe (it counts once for unique contacts).
	 */
	public static final long REPEAT_TICKS = 10L * 60L * 20L;
	/** The last page of a report too long for one book. */
	public static final String CUT_SHORT = "The report goes on past what a book holds. The full list is in the server log.";

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
	private static final DateTimeFormatter ZONE = DateTimeFormatter.ofPattern("z", Locale.ROOT);

	private ContestReport() {}

	/**
	 * A contact between players {@code a} and {@code b}: what each heard the other on ({@code aHeardHz} is the frequency A
	 * heard B on), each station's height and the mode each sent in (FM, AM, USB, LSB, FT8; "" if not known), the biome
	 * id at each station ("" if not known), the blocks between them (-1 between dimensions), the band and the game time.
	 */
	public record Qso(UUID a, String aName, UUID b, String bName, float aHeardHz, float bHeardHz, int aY, int bY,
			String aMode, String bMode, String aBiome, String bBiome, double distance, String band, long gameTime) {

		/** The contact as player {@code a} sees it. */
		public Side sideA() {
			return new Side(a, aName, b, bName, aHeardHz, aY, bY, aMode, bMode, aBiome, bBiome, distance, band);
		}

		/** The contact as player {@code b} sees it. */
		public Side sideB() {
			return new Side(b, bName, a, aName, bHeardHz, bY, aY, bMode, aMode, bBiome, aBiome, distance, band);
		}
	}

	/** One player's end of a contact: who they worked, what they heard them on, both heights, both modes, both biomes. */
	public record Side(UUID me, String myName, UUID other, String otherName, float heardHz, int myY, int otherY,
			String myMode, String otherMode, String myBiome, String otherBiome, double distance, String band) {

		/** The mode as a log writes it: "USB", or "USB/FM" (theirs after the slash) if the two ends differed. */
		public String mode() {
			if(myMode.isEmpty() && otherMode.isEmpty())
				return "?";
			if(myMode.isEmpty() || otherMode.isEmpty() || myMode.equals(otherMode))
				return myMode.isEmpty() ? otherMode : myMode;
			return myMode + "/" + otherMode;
		}
	}

	/** A player's contacts in order, and what they add up to. {@code longest} is -1 with no contact in one dimension. */
	public record PlayerLog(UUID id, String name, List<Side> contacts, Set<UUID> worked, Set<String> activated, Set<String> reached, double longest) {}

	/** Who won one category, with what (everyone tied); nobody if no one scored. */
	public record Standing(List<String> names, double value) {

		public boolean nobody() {
			return names.isEmpty();
		}
	}

	public record Score(int contacts, List<PlayerLog> players, Standing longest, Standing unique, Standing activated, Standing reached) {}

	/**
	 * The book's pages, the summary for the log (times and winners), the whole report as plain lines, and how many
	 * contacts and stations it has.
	 */
	public record Report(List<String> pages, List<String> summary, List<String> fullText, boolean cutShort, int contacts, int stations) {}

	// -------------------- THE LOG --------------------

	/**
	 * True if the pair {@code a} and {@code b} on {@code band} at {@code time} is a contact already in {@code logged}
	 * (oldest first): the same pair and band less than {@link #REPEAT_TICKS} before.
	 */
	public static boolean isRepeat(List<Qso> logged, UUID a, UUID b, String band, long time) {
		for(int i = logged.size() - 1; i >= 0; i--) {
			Qso q = logged.get(i);
			if(time - q.gameTime() >= REPEAT_TICKS)
				return false;
			boolean pair = (q.a().equals(a) && q.b().equals(b)) || (q.a().equals(b) && q.b().equals(a));
			if(pair && q.band().equals(band))
				return true;
		}
		return false;
	}

	// -------------------- SCORING --------------------

	public static Score score(List<Qso> qsos) {
		Map<UUID, List<Side>> sides = new LinkedHashMap<>();
		Map<UUID, String> names = new LinkedHashMap<>();
		for(Qso q : qsos) {
			for(Side side : new Side[] { q.sideA(), q.sideB() }) {
				sides.computeIfAbsent(side.me(), id -> new ArrayList<>()).add(side);
				names.put(side.me(), side.myName());
			}
		}
		List<PlayerLog> players = new ArrayList<>();
		for(Map.Entry<UUID, List<Side>> entry : sides.entrySet()) {
			Set<UUID> worked = new LinkedHashSet<>();
			Set<String> activated = new LinkedHashSet<>();
			Set<String> reached = new LinkedHashSet<>();
			double longest = -1.0D;
			for(Side side : entry.getValue()) {
				worked.add(side.other());
				if(!side.myBiome().isEmpty())
					activated.add(side.myBiome());
				if(!side.otherBiome().isEmpty())
					reached.add(side.otherBiome());
				if(side.distance() >= 0.0D && Double.isFinite(side.distance()))
					longest = Math.max(longest, side.distance());
			}
			players.add(new PlayerLog(entry.getKey(), names.get(entry.getKey()), entry.getValue(), worked, activated, reached, longest));
		}
		players.sort(Comparator.comparing((PlayerLog p) -> p.name().toLowerCase(Locale.ROOT)).thenComparing(p -> p.id().toString()));
		return new Score(qsos.size(), players,
				best(players, PlayerLog::longest, 0.0D),
				best(players, p -> p.worked().size(), 1.0D),
				best(players, p -> p.activated().size(), 1.0D),
				best(players, p -> p.reached().size(), 1.0D));
	}

	/** The players with the highest {@code value}, if it is at least {@code min}; their names in alphabetical order. */
	private static Standing best(List<PlayerLog> players, ToDoubleFunction<PlayerLog> value, double min) {
		double top = Double.NEGATIVE_INFINITY;
		for(PlayerLog p : players)
			top = Math.max(top, value.applyAsDouble(p));
		if(top < min)
			return new Standing(List.of(), 0.0D);
		List<String> names = new ArrayList<>();
		for(PlayerLog p : players) // Already in name order.
			if(value.applyAsDouble(p) == top)
				names.add(p.name());
		return new Standing(names, top);
	}

	// -------------------- THE BOOK --------------------

	private record Block(boolean newPage, List<String> lines) {}

	/**
	 * The report of contest {@code name} ("" for none) that ran from {@code startMillis} to {@code endMillis} (the real
	 * clock, shown in {@code zone}), whose log is {@code qsos}.
	 */
	public static Report build(String name, long startMillis, long endMillis, ZoneId zone, List<Qso> qsos) {
		Score score = score(qsos);
		List<Block> blocks = new ArrayList<>();
		List<String> head = new ArrayList<>();
		head.add(DEFAULT_TITLE);
		if(!name.isBlank())
			head.add(name.strip());
		head.add("");
		head.add("Started");
		head.add(" " + WHEN.format(Instant.ofEpochMilli(startMillis).atZone(zone)));
		head.add("Ended");
		head.add(" " + WHEN.format(Instant.ofEpochMilli(endMillis).atZone(zone)));
		head.add(" (" + ZONE.format(Instant.ofEpochMilli(endMillis).atZone(zone)) + ")");
		head.add("Lasted " + duration(endMillis - startMillis));
		head.add("");
		if(qsos.isEmpty()) { // In place of the counts, so it stays on the one page.
			head.add("No contacts were made during this contest.");
			blocks.add(new Block(true, head));
			return paginate(blocks, blocks, score);
		}
		head.add(score.contacts() + (score.contacts() == 1 ? " contact" : " contacts"));
		head.add(score.players().size() + (score.players().size() == 1 ? " station" : " stations"));
		blocks.add(new Block(true, head));
		List<Block> winners = new ArrayList<>();
		winners.add(new Block(true, List.of("Winners", "")));
		winners.add(new Block(false, standing("Longest contact", score.longest(), distanceLines(score.longest().value()))));
		winners.add(new Block(false, standing("Unique contacts", score.unique(), List.of(" " + count(score.unique().value(), "station")))));
		winners.add(new Block(false, standing("Biomes activated", score.activated(), List.of(" " + count(score.activated().value(), "biome")))));
		winners.add(new Block(false, standing("Biomes reached", score.reached(), List.of(" " + count(score.reached().value(), "biome")))));
		blocks.addAll(winners);

		for(PlayerLog p : score.players()) {
			List<String> top = new ArrayList<>();
			top.add(p.name());
			top.add(count(p.contacts().size(), "contact"));
			top.add(p.worked().size() + " unique");
			top.add("From " + count(p.activated().size(), "biome"));
			top.add("Reached " + count(p.reached().size(), "biome"));
			if(p.longest() >= 0.0D)
				top.add("Best " + blocks(p.longest()));
			top.add("");
			blocks.add(new Block(true, top));
			Set<String> seen = new HashSet<>();
			int n = 1;
			for(Side side : p.contacts()) {
				boolean dupe = !seen.add(side.other() + "|" + side.band());
				blocks.add(new Block(false, List.of(
						n++ + ". " + side.otherName() + (dupe ? " (dupe)" : ""),
						" " + String.format(Locale.ROOT, "%.3f MHz", side.heardHz() / 1_000_000.0D) + " " + side.mode(),
						" Y" + side.myY() + " to Y" + side.otherY())));
			}
		}
		List<Block> summary = new ArrayList<>();
		summary.add(blocks.getFirst());
		summary.addAll(winners);
		return paginate(blocks, summary, score);
	}

	private static List<String> standing(String title, Standing standing, List<String> value) {
		List<String> lines = new ArrayList<>();
		lines.add(title);
		if(standing.nobody())
			lines.add(" none");
		else {
			for(String name : standing.names())
				lines.add(" " + name);
			lines.addAll(value);
		}
		lines.add("");
		return lines;
	}

	private static List<String> distanceLines(double blocks) {
		if(blocks < 1000.0D)
			return List.of(" " + blocks(blocks));
		return List.of(" " + blocks(blocks), String.format(Locale.ROOT, " (%.1f km)", blocks / 1000.0D));
	}

	private static String blocks(double blocks) {
		long n = Math.round(blocks);
		return String.format(Locale.ROOT, "%,d %s", n, n == 1 ? "block" : "blocks");
	}

	private static String count(double n, String what) {
		long whole = Math.round(n);
		return whole + " " + what + (whole == 1 ? "" : "s");
	}

	/** "2h 07m", "5m", "under 1m". */
	public static String duration(long millis) {
		long minutes = Math.max(0L, millis) / 60_000L;
		if(minutes < 1L)
			return "under 1m";
		if(minutes < 60L)
			return minutes + "m";
		return String.format(Locale.ROOT, "%dh %02dm", minutes / 60L, minutes % 60L);
	}

	private static Report paginate(List<Block> blocks, List<Block> summaryBlocks, Score score) {
		List<String> pages = new ArrayList<>();
		List<String> page = new ArrayList<>();
		for(Block block : blocks) {
			List<String> lines = new ArrayList<>();
			for(String line : block.lines())
				wrap(line, lines);
			if(block.newPage() || (lines.size() <= BookPages.PAGE_LINES && page.size() + lines.size() > BookPages.PAGE_LINES))
				flush(page, pages);
			for(String line : lines) {
				if(page.isEmpty() && line.isEmpty())
					continue; // No blank line at the top of a page.
				page.add(line);
				if(page.size() >= BookPages.PAGE_LINES)
					flush(page, pages);
			}
		}
		flush(page, pages);
		boolean cut = pages.size() > BookPages.MAX_PAGES;
		if(cut) {
			pages = new ArrayList<>(pages.subList(0, BookPages.MAX_PAGES - 1));
			List<String> last = new ArrayList<>();
			wrap(CUT_SHORT, last);
			pages.add(String.join("\n", last));
		}
		return new Report(pages, plain(summaryBlocks), plain(blocks), cut, score.contacts(), score.players().size());
	}

	private static List<String> plain(List<Block> blocks) {
		List<String> out = new ArrayList<>();
		for(Block block : blocks)
			out.addAll(block.lines());
		return out;
	}

	private static void flush(List<String> page, List<String> pages) {
		while(!page.isEmpty() && page.getLast().isEmpty())
			page.removeLast();
		if(page.isEmpty())
			return;
		pages.add(String.join("\n", page));
		page.clear();
	}

	/** {@code line} cut into lines of at most {@link BookPages#LINE_CHARS}, at spaces where it can (an indent is kept). */
	static void wrap(String line, List<String> out) {
		String indent = line.startsWith(" ") ? " " : "";
		String rest = line;
		while(rest.length() > BookPages.LINE_CHARS) {
			int cut = rest.lastIndexOf(' ', BookPages.LINE_CHARS);
			if(cut <= 1) { // No space to break at (a leading indent does not count): cut the word.
				out.add(rest.substring(0, BookPages.LINE_CHARS));
				rest = rest.substring(BookPages.LINE_CHARS);
			}
			else {
				out.add(rest.substring(0, cut));
				rest = indent + rest.substring(cut + 1);
			}
		}
		out.add(rest);
	}
}
