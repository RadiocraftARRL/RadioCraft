package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosFilePicker;
import com.arrl.radiocraft.client.screens.computer.DosFormDialog;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.DosTextArea;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.BookDocument;
import com.arrl.radiocraft.common.computer.BookPages;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * BOOKEDIT, the book editor (1.12 step 6): a book of any length, a page at a time, kept on the computer as a
 * {@code .BOK} file and printed through the scanner as written books ({@link BookDocument#PAGES_PER_VOLUME} pages a
 * volume, a sheet of paper a page). The page is edited in a box the width of the text area; the side panel counts the
 * lines it takes in a real book (14 fit) and warns when a page runs over. Ctrl+S saves, F3 opens, F4 starts a new book,
 * F5 prints (saving first, asking which volume), F6 reads the book on the scanner's glass in, F7/F8 turn the page,
 * F9 adds a page after this one, F10 takes this one out, F12 sets the title and author. It asks before throwing away
 * changes.
 */
public final class BookEditorApp extends DosApp {

	private static final int PAGE_COL = 2, PAGE_ROW = 3, PAGE_COLS = 44, PAGE_ROWS = 18;
	private static final int SIDE_COL = PAGE_COL + PAGE_COLS + 3;

	private BookDocument book = BookDocument.empty();
	private int page = 0;
	private final DosTextArea area = new DosTextArea(BookPages.PAGE_CHARS);
	/** Changed since saved (pages other than the one in the editor, or the title). */
	private boolean bookModified = false;
	@Nullable
	private String fileName;

	public BookEditorApp(ComputerSession session, ComputerApp app) {
		super(session, app);
	}

	@Override
	public void opened() {
		newBook();
	}

	@Override
	public void openFile(String name) {
		load(name);
	}

	// -------------------- THE BOOK --------------------

	private boolean isModified() {
		return bookModified || area.isModified();
	}

	/** Puts the text in the editor back into the book. */
	private void commitPage() {
		if(area.isModified()) {
			book.setPage(page, area.text());
			bookModified = true;
			area.setModified(false);
		}
	}

	private void showPage(int index) {
		commitPage();
		page = Math.max(0, Math.min(book.pageCount() - 1, index));
		area.setText(book.page(page));
	}

	private void newBook() {
		book = BookDocument.empty();
		if(Minecraft.getInstance().player != null)
			book.setAuthor(Minecraft.getInstance().player.getGameProfile().getName());
		fileName = null;
		bookModified = false;
		page = 0;
		area.setText("");
	}

	private void load(String name) {
		session.files().read(name, response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			CompoundTag tag = ComputerFileFormats.nbt(response.data().getByteArray("data"));
			if(tag == null) {
				session.message(Dos.tr("err.bad_contents"), true);
				return;
			}
			book = BookDocument.fromTag(tag);
			fileName = ComputerStorage.normalise(name);
			bookModified = false;
			page = 0;
			area.setText(book.page(0));
			session.message(Dos.tr("bookedit.opened", fileName, book.pageCount()), false);
		});
	}

	/** Saves, asking for a name the first time; then runs {@code after}, if given. */
	private void save(@Nullable Runnable after) {
		commitPage();
		if(fileName == null) {
			String suggested = book.title().isBlank() ? "" : ComputerStorage.suggest(book.title(), ComputerFileFormats.BOOK);
			showDialog(DosDialog.ask(Dos.tr("bookedit.save_title"), Dos.tr("bookedit.save_as"), DosInput.fileName(suggested), typed -> {
				fileName = ComputerStorage.suggest(typed, ComputerFileFormats.BOOK);
				save(after);
			}));
			return;
		}
		String name = fileName;
		session.files().write(name, ComputerFileFormats.nbt(book.toTag()), response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			bookModified = false;
			session.message(Dos.tr("bookedit.saved", name), false);
			if(after != null)
				after.run();
		});
	}

	@Override
	public boolean holdsUnsavedWork() {
		return isModified();
	}

	/** Runs {@code then} once unsaved changes are saved or let go (asks), or at once if there are none. */
	private void whenSafe(Runnable then) {
		if(!isModified()) {
			then.run();
			return;
		}
		showDialog(DosDialog.choice(Dos.tr("edit.unsaved_title"), Dos.tr("bookedit.unsaved"),
				new String[] { Dos.tr("button.save"), Dos.tr("button.discard"), Dos.tr("button.cancel") }, index -> {
					if(index == 0)
						save(then);
					else if(index == 1)
						then.run();
				}));
	}

	// -------------------- PRINTING AND SCANNING --------------------

	/** Saves, then prints a volume (asking which when there are several). */
	private void print() {
		save(() -> {
			int volumes = book.volumes();
			if(volumes <= 1) {
				printVolume(0);
				return;
			}
			showDialog(DosDialog.ask(Dos.tr("bookedit.print_title"), Dos.tr("bookedit.which_volume", volumes, BookDocument.PAGES_PER_VOLUME),
					new DosInput(String.valueOf(BookDocument.volumeOf(page) + 1), 3, c -> c >= '0' && c <= '9'), typed -> {
						int volume;
						try {
							volume = Integer.parseInt(typed.strip()) - 1;
						}
						catch(NumberFormatException e) {
							volume = -1;
						}
						if(volume < 0 || volume >= volumes)
							session.message(Dos.tr("bookedit.no_volume", volumes), true);
						else
							printVolume(volume);
					}));
		});
	}

	private void printVolume(int volume) {
		if(fileName == null)
			return;
		CompoundTag data = new CompoundTag();
		data.putString("name", fileName);
		data.putInt("volume", volume);
		int pages = book.volumePages(volume).size();
		session.app(app, "print", data, response -> {
			if(response.ok())
				session.message(Dos.tr("bookedit.printed", volume + 1, pages), false);
			else
				showError(response);
		});
	}

	private void scan() {
		session.scanner().read(response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			if(!"book".equals(response.data().getString("kind"))) {
				session.message(Dos.tr("edit.not_a_book"), true);
				return;
			}
			List<String> pages = ComputerServer.strings(response.data().getList("pages", Tag.TAG_STRING));
			String title = response.data().getString("title");
			whenSafe(() -> {
				book = new BookDocument(title, "", pages);
				fileName = null;
				page = 0;
				area.setText(book.page(0));
				bookModified = true;
				session.message(Dos.tr("bookedit.scanned", title, book.pageCount()), false);
			});
		});
	}

	private void editInfo() {
		showDialog(new DosFormDialog(Dos.tr("bookedit.info_title"), "", List.of(
				new DosFormDialog.Field(Dos.tr("bookedit.title_field"), new DosInput(book.title(), BookDocument.TITLE_MAX)),
				new DosFormDialog.Field(Dos.tr("bookedit.author_field"), new DosInput(book.author(), BookDocument.AUTHOR_MAX))), values -> {
			book.setTitle(values.get(0).strip());
			book.setAuthor(values.get(1).strip());
			bookModified = true;
			return null;
		}));
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		String name = fileName == null ? Dos.tr("edit.untitled") : fileName;
		Dos.frame(g, Dos.tr("bookedit.title", name + (isModified() ? "*" : "")), Dos.tr("bookedit.keys"));
		int fg = DosColours.BODY_FG, bg = DosColours.BODY_BG;
		String title = book.title().isBlank() ? Dos.tr("bookedit.no_title") : book.title();
		String author = book.author().isBlank() ? "-" : book.author();
		g.put(2, 1, Dos.fit(Dos.tr("bookedit.heading", title, author), TextGrid.COLS - 4), DosColours.YELLOW, bg);

		g.box(PAGE_COL - 1, PAGE_ROW - 1, PAGE_COLS + 2, PAGE_ROWS + 2, true, Dos.tr("bookedit.page", page + 1, book.pageCount()), DosColours.TEXT_FG, bg);
		area.render(g, PAGE_COL, PAGE_ROW, PAGE_COLS, PAGE_ROWS, DosColours.TEXT_FG, bg, !hasDialog());

		// The side panel: where the page is in the book, and whether it fits a book page.
		int r = PAGE_ROW;
		int volume = BookDocument.volumeOf(page);
		g.put(SIDE_COL, r++, Dos.tr("bookedit.side_page", page + 1, book.pageCount()), DosColours.TEXT_FG, bg);
		g.put(SIDE_COL, r++, Dos.tr("bookedit.side_volume", volume + 1, book.volumes()), fg, bg);
		int last = Math.min(book.pageCount(), BookDocument.volumeStart(volume) + BookDocument.PAGES_PER_VOLUME - 1);
		g.put(SIDE_COL, r++, Dos.tr("bookedit.side_volume_pages", BookDocument.volumeStart(volume), last), fg, bg);
		r++;
		String text = area.text();
		int lines = BookDocument.bookLines(text);
		boolean over = lines > BookPages.PAGE_LINES;
		g.put(SIDE_COL, r++, Dos.tr("bookedit.side_lines", lines, BookPages.PAGE_LINES), over ? DosColours.LIGHT_RED : fg, bg);
		g.bar(SIDE_COL, r++, 26, lines / (double)BookPages.PAGE_LINES, over ? DosColours.LIGHT_RED : DosColours.LIGHT_CYAN, bg);
		if(over)
			g.put(SIDE_COL, r, Dos.tr("bookedit.side_over"), DosColours.LIGHT_RED, bg);
		r += 2;
		g.put(SIDE_COL, r++, Dos.tr("bookedit.side_chars", area.length(), BookPages.PAGE_CHARS), fg, bg);
		r++;
		g.put(SIDE_COL, r++, Dos.tr("bookedit.side_help1"), DosColours.DARK_GREY, bg);
		g.put(SIDE_COL, r++, Dos.tr("bookedit.side_help2"), DosColours.DARK_GREY, bg);
		g.put(SIDE_COL, r, Dos.tr("bookedit.side_help3"), DosColours.DARK_GREY, bg);

		String message = session.message();
		String status = Dos.tr("bookedit.status", area.cursorLine() + 1, area.cursorColumn() + 1);
		g.putField(0, 23, " " + (message.isEmpty() ? status : message), TextGrid.COLS, session.messageIsError() && !message.isEmpty() ? DosColours.RED : DosColours.BLACK, DosColours.LIGHT_GREY);
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onEscape() {
		whenSafe(this::exit);
		return true;
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(Dos.isSaveKey(key)) {
			save(null);
			return true;
		}
		switch(key) {
			case GLFW.GLFW_KEY_F3 -> whenSafe(() -> showDialog(new DosFilePicker(session, Dos.tr("bookedit.open_title"), ComputerFileFormats.BOOK, this::load)));
			case GLFW.GLFW_KEY_F4 -> whenSafe(this::newBook);
			case GLFW.GLFW_KEY_F5 -> print();
			case GLFW.GLFW_KEY_F6 -> scan();
			case GLFW.GLFW_KEY_F7 -> showPage(page - 1);
			case GLFW.GLFW_KEY_F8 -> showPage(page + 1);
			case GLFW.GLFW_KEY_F9 -> {
				commitPage();
				if(book.insertPage(page + 1)) {
					bookModified = true;
					showPage(page + 1);
				}
				else
					session.message(Dos.tr("bookedit.full", BookDocument.MAX_PAGES), true);
			}
			case GLFW.GLFW_KEY_F10 -> showDialog(DosDialog.confirm(Dos.tr("bookedit.delete_title"), Dos.tr("bookedit.delete", page + 1), () -> {
				area.setModified(false); // Its text is going anyway.
				book.removePage(page);
				bookModified = true;
				page = Math.min(page, book.pageCount() - 1);
				area.setText(book.page(page));
			}));
			case GLFW.GLFW_KEY_F12 -> editInfo();
			default -> {
				return area.keyPressed(key);
			}
		}
		return true;
	}

	@Override
	protected boolean onChar(char c) {
		return area.charTyped(c);
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		return area.mouseClicked(col, row);
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		return area.mouseScrolled(col, row, amount);
	}
}
