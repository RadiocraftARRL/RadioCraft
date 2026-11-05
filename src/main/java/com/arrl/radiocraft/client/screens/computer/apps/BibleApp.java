package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.DosList;
import com.arrl.radiocraft.client.screens.computer.DosPickDialog;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.BibleText;
import com.arrl.radiocraft.common.computer.ComputerApp;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * BIBLE, a secret program (1.12 step 8): the four gospels of the World English Bible ({@link BibleText}), a chapter at a
 * time with the verse numbers in the margin. Up, Down and the page keys scroll, Left and Right turn the chapter (on
 * into the next book), F3 goes to a reference typed ("John 3:16", "Mk 4"), F4 picks a book and chapter, F5 finds
 * verses holding every word typed, F6 shows the last search's verses again.
 */
public final class BibleApp extends DosApp {

	private static final int TEXT_COL = 2, TEXT_WIDTH = 76, TEXT_TOP = 2, TEXT_ROWS = 21;
	/** Most verses a search lists. */
	private static final int MAX_HITS = 500;

	/** The text, read once per game (it is the same for every computer). */
	@Nullable
	private static BibleText text;
	private static boolean failed;

	private BibleText.Reference at = new BibleText.Reference(0, 1, 0);
	private List<String> lines = List.of();
	private final List<Integer> verseStarts = new ArrayList<>();
	private int scroll;
	/** The verse to highlight (from a reference or a search), 0 for none. */
	private int marked;

	private final DosList results = new DosList();
	private List<BibleText.Hit> hits = List.of();
	private String query = "";
	private boolean showingResults;

	public BibleApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		results.onActivate(this::openHit);
	}

	@Nullable
	private static BibleText text() {
		if(text == null && !failed) {
			Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(Radiocraft.id("computer/bible/web_gospels.txt.gz"));
			if(resource.isEmpty())
				failed = true;
			else
				try(InputStream in = new GZIPInputStream(resource.get().open())) {
					text = BibleText.parse(new String(in.readNBytes(4 << 20), StandardCharsets.UTF_8));
				}
				catch(IOException e) {
					failed = true;
					Radiocraft.LOGGER.warn("Could not read the computer's Bible text", e);
				}
		}
		return text;
	}

	@Override
	public void opened() {
		if(text() != null)
			show(at);
	}

	/** Shows {@code r}'s chapter, scrolled to its verse (and marked) if it names one. */
	private void show(BibleText.Reference r) {
		BibleText bible = text();
		if(bible == null)
			return;
		BibleText.Chapter chapter = bible.books().get(r.book()).chapter(r.chapter());
		if(chapter == null)
			return;
		at = r;
		verseStarts.clear();
		lines = BibleText.wrap(chapter, TEXT_WIDTH, verseStarts);
		marked = r.verse();
		scroll = 0;
		if(r.verse() > 0)
			for(int i = 0; i < chapter.verses().size(); i++)
				if(chapter.verses().get(i).number() == r.verse())
					scroll = Math.max(0, verseStarts.get(i) - 2);
		clampScroll();
	}

	private void clampScroll() {
		scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - TEXT_ROWS)));
	}

	@Override
	public void render(TextGrid g, long now) {
		BibleText bible = text();
		if(bible == null) {
			Dos.frame(g, Dos.tr("bible.title", ""), Dos.tr("keys.back"));
			g.put(2, 2, Dos.tr("bible.missing"), DosColours.TEXT_FG, DosColours.BODY_BG);
			return;
		}
		if(showingResults) {
			renderResults(g, bible);
			return;
		}
		BibleText.Book book = bible.books().get(at.book());
		Dos.frame(g, Dos.tr("bible.title", book.name() + " " + at.chapter()), Dos.tr("bible.keys"));
		int bg = DosColours.BODY_BG;
		g.center(1, Dos.tr("bible.heading", book.name().toUpperCase(Locale.ROOT), at.chapter()), DosColours.YELLOW, bg);
		// Which verse each line belongs to, for the margin colour and the mark.
		int verseIndex = 0;
		BibleText.Chapter chapter = book.chapter(at.chapter());
		for(int row = 0; row < TEXT_ROWS; row++) {
			int index = scroll + row;
			if(index >= lines.size())
				break;
			while(verseIndex + 1 < verseStarts.size() && verseStarts.get(verseIndex + 1) <= index)
				verseIndex++;
			boolean mark = chapter != null && marked > 0 && chapter.verses().get(verseIndex).number() == marked;
			int fg = mark ? DosColours.BLACK : DosColours.TEXT_FG, back = mark ? DosColours.CYAN : bg;
			String line = lines.get(index);
			g.putField(TEXT_COL, TEXT_TOP + row, line, TEXT_WIDTH, fg, back);
			if(verseStarts.get(verseIndex) == index) { // The verse number in the margin, in yellow.
				int space = line.indexOf(' ');
				g.put(TEXT_COL, TEXT_TOP + row, line.substring(0, Math.max(0, space)), mark ? DosColours.BLUE : DosColours.YELLOW, back);
			}
		}
		// Where we are, on the last row of the body.
		int chapters = book.chapters().size();
		String where = Dos.tr("bible.where", book.name(), at.chapter(), chapters, Math.min(lines.size(), scroll + TEXT_ROWS), lines.size());
		g.put(TEXT_COL, TextGrid.ROWS - 2, Dos.fit(where, TEXT_WIDTH), DosColours.LIGHT_CYAN, bg);
	}

	private void renderResults(TextGrid g, BibleText bible) {
		Dos.frame(g, Dos.tr("bible.results_title", query), Dos.tr("bible.results_keys"));
		int bg = DosColours.BODY_BG;
		g.put(TEXT_COL, 1, Dos.tr(hits.size() >= MAX_HITS ? "bible.results_many" : "bible.results_count", hits.size()), DosColours.YELLOW, bg);
		if(hits.isEmpty())
			g.put(TEXT_COL, 3, Dos.tr("bible.results_none"), DosColours.TEXT_FG, bg);
		else
			results.render(g, TEXT_COL, 2, TEXT_WIDTH, TextGrid.ROWS - 3, DosColours.TEXT_FG, bg, true);
	}

	private void openHit(int index) {
		if(index < 0 || index >= hits.size())
			return;
		showingResults = false;
		show(hits.get(index).reference());
	}

	private void find(String words) {
		BibleText bible = text();
		if(bible == null)
			return;
		query = words.strip();
		hits = bible.search(query, MAX_HITS);
		List<String> items = new ArrayList<>();
		for(BibleText.Hit hit : hits) {
			String ref = bible.name(hit.reference());
			items.add(Dos.fit(String.format(Locale.ROOT, "%-14s %s", ref, hit.text()), TEXT_WIDTH - 1));
		}
		results.setItems(items);
		if(!items.isEmpty())
			results.select(0);
		showingResults = true;
	}

	private void goTo() {
		DosInput field = new DosInput("", 30);
		showDialog(DosDialog.ask(Dos.tr("bible.goto_title"), Dos.tr("bible.goto_prompt"), field, typed -> {
			BibleText bible = text();
			BibleText.Reference r = bible == null ? null : bible.reference(typed);
			if(r == null)
				showDialog(DosDialog.message(Dos.tr("bible.goto_title"), Dos.tr("bible.goto_bad", typed)));
			else {
				showingResults = false;
				show(r);
			}
		}));
	}

	private void pickBook() {
		BibleText bible = text();
		if(bible == null)
			return;
		List<String> names = new ArrayList<>();
		for(BibleText.Book book : bible.books())
			names.add(Dos.tr("bible.book_line", book.name(), book.chapters().size()));
		showDialog(new DosPickDialog(Dos.tr("bible.books_title"), Dos.tr("bible.books_prompt"), names, book -> {
			List<String> chapters = new ArrayList<>();
			for(BibleText.Chapter chapter : bible.books().get(book).chapters())
				chapters.add(Dos.tr("bible.chapter_line", chapter.number(), chapter.verses().size()));
			showDialog(new DosPickDialog(bible.books().get(book).name(), Dos.tr("bible.chapters_prompt"), chapters, chapter -> {
				showingResults = false;
				show(new BibleText.Reference(book, chapter + 1, 0));
			}));
		}));
	}

	private void ask() {
		DosInput field = new DosInput(query, 40);
		showDialog(DosDialog.ask(Dos.tr("bible.find_title"), Dos.tr("bible.find_prompt"), field, this::find));
	}

	@Override
	protected boolean onEscape() {
		if(showingResults) {
			showingResults = false;
			return true;
		}
		exit();
		return true;
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		BibleText bible = text();
		if(bible == null)
			return false;
		switch(key) {
			case GLFW.GLFW_KEY_F3 -> {
				goTo();
				return true;
			}
			case GLFW.GLFW_KEY_F4 -> {
				pickBook();
				return true;
			}
			case GLFW.GLFW_KEY_F5 -> {
				ask();
				return true;
			}
			case GLFW.GLFW_KEY_F6 -> {
				showingResults = !hits.isEmpty() || !query.isEmpty();
				return true;
			}
			default -> {}
		}
		if(showingResults)
			return results.keyPressed(key);
		switch(key) {
			case GLFW.GLFW_KEY_UP -> scroll--;
			case GLFW.GLFW_KEY_DOWN -> scroll++;
			case GLFW.GLFW_KEY_PAGE_UP -> scroll -= TEXT_ROWS - 1;
			case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_SPACE -> scroll += TEXT_ROWS - 1;
			case GLFW.GLFW_KEY_HOME -> scroll = 0;
			case GLFW.GLFW_KEY_END -> scroll = lines.size();
			case GLFW.GLFW_KEY_LEFT -> {
				BibleText.Reference r = bible.previousChapter(at);
				if(r != null)
					show(r);
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				BibleText.Reference r = bible.nextChapter(at);
				if(r != null)
					show(r);
			}
			default -> {
				return false;
			}
		}
		clampScroll();
		return true;
	}

	@Override
	protected boolean onChar(char c) {
		return true;
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		if(showingResults)
			return results.mouseScrolled(col, row, amount);
		scroll -= (int)Math.signum(amount) * 3;
		clampScroll();
		return true;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		return showingResults && results.mouseClicked(col, row);
	}
}
