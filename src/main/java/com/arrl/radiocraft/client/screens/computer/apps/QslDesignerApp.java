package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerFiles;
import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosFilePicker;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.DosList;
import com.arrl.radiocraft.client.screens.computer.DosPickDialog;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.qsl.QslArt;
import com.arrl.radiocraft.common.qsl.QslCard;
import com.arrl.radiocraft.common.qsl.QslCards;
import net.minecraft.nbt.CompoundTag;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * QSL, the QSL Designer (computer batch 3C): QSL card templates and the cards that came in, as {@code .QSL} files,
 * with a live preview of the card on the right (Tab turns it over).
 * <ul>
 * <li>The list: Ins makes a template, Enter edits one (or looks at a received card), Del deletes, F5 prints (a
 * template: a run of N blank cards, one paper or photo paper each; a received card: one copy of it), F6 reads the card
 * on the scanner's glass into a file (to fax it from FAX or send it to a BBS), F7 sends a card to a Packet BBS for the
 * station it is for.</li>
 * <li>The editor: the front's callsign (blank for a card anyone can write their call on, for example one made for
 * another player), four lines, the colours and border, a picture from an {@code .IMG} file (an SSTV picture, a photo,
 * a WEFAX chart; shrunk to fit and kept in the card) and its side; the back's heading, layout and two lines. Every
 * change is saved at once.</li>
 * </ul>
 */
public final class QslDesignerApp extends DosApp {

	private enum Page { LIST, EDIT }

	private static final int PREVIEW_X = 217, PREVIEW_Y = 40, PREVIEW_SCALE = 2;
	private static final String[] PROPERTIES = { "call", "line1", "line2", "line3", "line4", "background", "border", "border_style", "call_colour",
			"text_colour", "picture", "picture_side", "back_header", "back_layout", "back_line1", "back_line2" };

	private final DosList files = new DosList();
	private final DosList properties = new DosList();
	private final List<String> names = new ArrayList<>();
	private final Map<String, QslCard> cards = new HashMap<>();
	private Page page = Page.LIST;
	private String editing = "";
	private QslCard card;
	private boolean back = false;
	private byte[] preview;
	private QslCard previewOf;
	private boolean previewBack;
	private String bbs = "";

	public QslDesignerApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		files.onActivate(index -> open());
		properties.onActivate(this::editProperty);
	}

	@Override
	public void opened() {
		refresh();
	}

	@Override
	public void resumed() {
		refresh();
	}

	@Override
	public void openFile(String name) {
		read(name, () -> {
			editing = ComputerStorage.normalise(name);
			card = cards.get(editing);
			page = Page.EDIT;
			rebuildProperties();
		});
	}

	@Override
	public void event(String kind, CompoundTag data) {
		if(kind.equals("received")) {
			session.message(Dos.tr("qsl.received", data.getString("name"), data.getString("from")), false);
			refresh();
		}
	}

	// -------------------- FILES --------------------

	private void refresh() {
		session.files().list(QslCards.EXTENSION, response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			names.clear();
			for(ComputerFiles.Info info : ComputerFiles.infos(response))
				names.add(info.name());
			rebuildFiles();
		});
	}

	private void rebuildFiles() {
		List<String> lines = new ArrayList<>();
		for(String name : names) {
			QslCard c = cards.get(name);
			String kind = c == null ? "" : c.blank() ? Dos.tr("qsl.kind.template") : Dos.tr("qsl.kind.card", c.from());
			lines.add(String.format(Locale.ROOT, "%-12s %s", name, kind));
		}
		int keep = files.selected();
		files.setItems(lines);
		if(keep >= 0 && keep < lines.size())
			files.select(keep);
		for(String name : names)
			if(!cards.containsKey(name))
				read(name, this::rebuildFiles);
	}

	private void read(String name, Runnable then) {
		session.files().read(name, response -> {
			if(!response.ok())
				return;
			QslCard c = QslCards.fromFile(response.data().getByteArray("data"));
			if(c != null) {
				cards.put(ComputerStorage.normalise(name), c);
				then.run();
			}
		});
	}

	private String selectedName() {
		int at = files.selected();
		return at >= 0 && at < names.size() ? names.get(at) : null;
	}

	private void write(String name, QslCard made, Runnable then) {
		session.files().write(name, QslCards.file(made), response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			cards.put(name, made);
			then.run();
		});
	}

	// -------------------- THE LIST --------------------

	private void newTemplate() {
		showDialog(DosDialog.ask(Dos.tr("qsl.new_title"), Dos.tr("qsl.new_prompt"), DosInput.fileName(""), typed -> {
			String name = ComputerStorage.suggest(typed.isBlank() ? "MYQSL" : typed, QslCards.EXTENSION);
			if(names.contains(name)) {
				session.message(Dos.tr("qsl.exists", name), true);
				return;
			}
			write(name, QslCard.BLANK, () -> {
				names.add(name);
				editing = name;
				card = QslCard.BLANK;
				page = Page.EDIT;
				rebuildProperties();
				rebuildFiles();
			});
		}));
	}

	private void open() {
		String name = selectedName();
		if(name == null || cards.get(name) == null)
			return;
		editing = name;
		card = cards.get(name);
		page = Page.EDIT;
		rebuildProperties();
	}

	private void print() {
		String name = selectedName();
		QslCard c = name == null ? null : cards.get(name);
		if(c == null)
			return;
		java.util.function.IntConsumer go = count -> {
			CompoundTag data = new CompoundTag();
			data.putString("name", name);
			data.putInt("count", count);
			session.app(app, "print", data, response -> {
				if(response.ok())
					session.message(Dos.tr("qsl.printed", response.data().getInt("printed")), false);
				else
					showError(response);
			});
		};
		if(!c.blank()) {
			go.accept(1);
			return;
		}
		showDialog(DosDialog.ask(Dos.tr("qsl.print_title"), Dos.tr("qsl.print_prompt"), new DosInput("16", 2, ch -> ch >= '0' && ch <= '9'), typed -> {
			int count;
			try {
				count = Integer.parseInt(typed.strip());
			}
			catch(NumberFormatException e) {
				count = 0;
			}
			if(count < 1 || count > 64)
				session.message(Dos.tr("qsl.print_bad"), true);
			else
				go.accept(count);
		}));
	}

	private void scan() {
		session.app(app, "scan", new CompoundTag(), response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			String name = response.data().getString("name");
			cards.remove(name);
			session.message(Dos.tr("qsl.scanned", name), false);
			refresh();
		});
	}

	private void sendToBbs() {
		String name = selectedName();
		QslCard c = name == null ? null : cards.get(name);
		if(c == null)
			return;
		if(!c.signed()) {
			session.message(Dos.tr("qsl.bbs_unsigned"), true);
			return;
		}
		showDialog(DosDialog.ask(Dos.tr("qsl.bbs_title"), Dos.tr("qsl.bbs_prompt", c.fill().to()), new DosInput(bbs, 12,
				ch -> Character.isLetterOrDigit(ch) && ch < 128 || ch == '-'), typed -> {
			bbs = typed.strip().toUpperCase(Locale.ROOT);
			CompoundTag data = new CompoundTag();
			data.putString("name", name);
			data.putString("bbs", bbs);
			session.app(app, "bbs", data, response -> {
				if(response.ok())
					session.message(Dos.tr("qsl.bbs_sent", bbs), false);
				else
					showError(response);
			});
		}));
	}

	private void delete() {
		String name = selectedName();
		if(name == null)
			return;
		showDialog(DosDialog.confirm(Dos.tr("qsl.delete_title"), Dos.tr("qsl.delete", name), () -> session.files().delete(name, response -> {
			if(!response.ok())
				showError(response);
			cards.remove(name);
			refresh();
		})));
	}

	// -------------------- THE EDITOR --------------------

	private boolean editable() {
		return card != null && card.blank();
	}

	private void rebuildProperties() {
		List<String> lines = new ArrayList<>();
		if(card == null)
			return;
		QslCard.Design d = card.design();
		for(int i = 0; i < PROPERTIES.length; i++)
			lines.add(String.format(Locale.ROOT, "%-12s %s", Dos.tr("qsl.p." + PROPERTIES[i]), Dos.fit(value(d, i), 21)));
		int keep = properties.selected();
		properties.setItems(lines);
		if(keep >= 0)
			properties.select(keep);
	}

	private static String line(List<String> lines, int i) {
		return i < lines.size() ? lines.get(i) : "";
	}

	private static String colour(byte c) {
		return Dos.tr("qsl.colour." + QslArt.colourName(c));
	}

	private String value(QslCard.Design d, int i) {
		return switch(PROPERTIES[i]) {
			case "call" -> d.call().isEmpty() ? Dos.tr("qsl.blank_call") : d.call();
			case "line1" -> line(d.lines(), 0);
			case "line2" -> line(d.lines(), 1);
			case "line3" -> line(d.lines(), 2);
			case "line4" -> line(d.lines(), 3);
			case "background" -> colour(d.background());
			case "border" -> colour(d.border());
			case "border_style" -> Dos.tr("qsl.border." + d.borderStyle());
			case "call_colour" -> colour(d.callColour());
			case "text_colour" -> colour(d.textColour());
			case "picture" -> d.hasPicture() ? Dos.tr("qsl.picture_yes") : Dos.tr("qsl.picture_none");
			case "picture_side" -> Dos.tr(d.picturePlace() == QslCard.PICTURE_RIGHT ? "qsl.side_right" : "qsl.side_left");
			case "back_header" -> d.backHeader();
			case "back_layout" -> Dos.tr(d.backLayout() == QslCard.BACK_TABLE ? "qsl.layout_table" : "qsl.layout_lines");
			case "back_line1" -> line(d.backLines(), 0);
			case "back_line2" -> line(d.backLines(), 1);
			default -> "";
		};
	}

	/** {@code d} with property {@code i} set: a String for text, a Byte for a colour, an Integer for a choice, a byte[] picture. */
	private static QslCard.Design with(QslCard.Design d, int i, Object v) {
		List<String> lines = new ArrayList<>(d.lines());
		List<String> backLines = new ArrayList<>(d.backLines());
		String call = d.call(), header = d.backHeader();
		byte bg = d.background(), border = d.border(), callColour = d.callColour(), textColour = d.textColour();
		int style = d.borderStyle(), place = d.picturePlace(), layout = d.backLayout();
		byte[] picture = d.picture();
		switch(PROPERTIES[i]) {
			case "call" -> call = (String)v;
			case "line1", "line2", "line3", "line4" -> set(lines, i - 1, (String)v);
			case "background" -> bg = (Byte)v;
			case "border" -> border = (Byte)v;
			case "border_style" -> style = (Integer)v;
			case "call_colour" -> callColour = (Byte)v;
			case "text_colour" -> textColour = (Byte)v;
			case "picture" -> picture = (byte[])v;
			case "picture_side" -> place = (Integer)v;
			case "back_header" -> header = (String)v;
			case "back_layout" -> layout = (Integer)v;
			case "back_line1" -> set(backLines, 0, (String)v);
			case "back_line2" -> set(backLines, 1, (String)v);
			default -> {}
		}
		return new QslCard.Design(call, lines, bg, border, style, callColour, textColour, picture, place, header, layout, backLines);
	}

	private static void set(List<String> lines, int index, String value) {
		while(lines.size() <= index)
			lines.add("");
		lines.set(index, value);
	}

	private void change(int property, Object value) {
		QslCard made = new QslCard(with(card.design(), property, value), card.fill(), card.copy());
		write(editing, made, () -> {
			card = made;
			rebuildProperties();
		});
	}

	private void editProperty(int i) {
		if(!editable()) {
			session.message(Dos.tr("qsl.not_template"), true);
			return;
		}
		QslCard.Design d = card.design();
		String name = PROPERTIES[i];
		switch(name) {
			case "background", "border", "call_colour", "text_colour" -> {
				List<String> names = new ArrayList<>();
				List<Byte> colours = new ArrayList<>(QslArt.COLOURS.values());
				for(byte c : colours)
					names.add(colour(c));
				showDialog(new DosPickDialog(Dos.tr("qsl.p." + name), Dos.tr("qsl.pick_colour"), names, index -> change(i, colours.get(index))));
			}
			case "border_style" -> {
				List<String> styles = List.of(Dos.tr("qsl.border.0"), Dos.tr("qsl.border.1"), Dos.tr("qsl.border.2"), Dos.tr("qsl.border.3"));
				showDialog(new DosPickDialog(Dos.tr("qsl.p." + name), "", styles, index -> change(i, index)));
			}
			case "picture_side" -> change(i, d.picturePlace() == QslCard.PICTURE_RIGHT ? QslCard.PICTURE_LEFT : QslCard.PICTURE_RIGHT);
			case "back_layout" -> change(i, d.backLayout() == QslCard.BACK_TABLE ? QslCard.BACK_LINES_LAYOUT : QslCard.BACK_TABLE);
			case "picture" -> showDialog(new DosFilePicker(session, Dos.tr("qsl.pick_picture"), ComputerFileFormats.IMAGE, file -> session.files().read(file, response -> {
				if(!response.ok()) {
					showError(response);
					return;
				}
				byte[] small = QslArt.picture(response.data().getByteArray("data"), MapPalettes.vanilla());
				if(small == null)
					session.message(Dos.tr("err.bad_contents"), true);
				else
					change(i, small);
			})));
			default -> {
				int max = name.equals("call") ? QslCard.CALL_MAX : name.equals("back_header") ? QslCard.HEADER_MAX : QslCard.LINE_MAX;
				String now = name.equals("call") ? d.call() : value(d, i);
				showDialog(DosDialog.ask(Dos.tr("qsl.p." + name), Dos.tr(name.equals("call") ? "qsl.call_prompt" : "qsl.text_prompt"), new DosInput(now, max),
						typed -> change(i, typed)));
			}
		}
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		int bg = DosColours.BODY_BG;
		if(page == Page.LIST) {
			Dos.frame(g, Dos.tr("qsl.title"), Dos.tr("qsl.keys"));
			g.put(1, 1, Dos.tr("qsl.files", names.size()), DosColours.YELLOW, bg);
			if(names.isEmpty())
				g.put(2, 3, Dos.tr("qsl.empty"), DosColours.TEXT_FG, bg);
			else
				files.render(g, 1, 3, 34, 19, DosColours.TEXT_FG, bg, !hasDialog());
			String name = selectedName();
			drawPreview(g, name == null ? null : cards.get(name));
		}
		else {
			Dos.frame(g, Dos.tr("qsl.edit_title", editing), Dos.tr(editable() ? "qsl.edit_keys" : "qsl.view_keys"));
			g.put(1, 1, Dos.tr(editable() ? "qsl.edit_hint" : "qsl.view_hint"), DosColours.YELLOW, bg);
			properties.render(g, 1, 3, 34, PROPERTIES.length, editable() ? DosColours.TEXT_FG : DosColours.DIM, bg, !hasDialog());
			drawPreview(g, card);
		}
		String message = session.message();
		g.putField(0, 23, " " + (message.isEmpty() ? Dos.tr(back ? "qsl.showing_back" : "qsl.showing_front") : message), TextGrid.COLS,
				!message.isEmpty() && session.messageIsError() ? DosColours.RED : DosColours.BLACK, DosColours.LIGHT_GREY);
	}

	private void drawPreview(TextGrid g, QslCard shown) {
		g.box(35, 3, 45, 18, false, Dos.tr(back ? "qsl.back" : "qsl.front"), DosColours.TEXT_FG, DosColours.BODY_BG);
		if(shown == null || hasDialog())
			return;
		if(shown != previewOf || back != previewBack) { // A new array only when it changes (the texture repaints on that).
			preview = back ? QslArt.back(shown) : QslArt.front(shown);
			previewOf = shown;
			previewBack = back;
		}
		g.image(PREVIEW_X, PREVIEW_Y, QslArt.WIDTH * PREVIEW_SCALE, QslArt.HEIGHT * PREVIEW_SCALE, "qsl:preview", preview, QslArt.WIDTH, QslArt.HEIGHT);
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onEscape() {
		if(page == Page.EDIT) {
			page = Page.LIST;
			rebuildFiles();
			return true;
		}
		return super.onEscape();
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(key == GLFW.GLFW_KEY_TAB) {
			back = !back;
			return true;
		}
		if(page == Page.EDIT) {
			if(key == GLFW.GLFW_KEY_DELETE && properties.selected() == 10 && editable()) {
				change(10, new byte[0]);
				return true;
			}
			return properties.keyPressed(key);
		}
		switch(key) {
			case GLFW.GLFW_KEY_INSERT -> newTemplate();
			case GLFW.GLFW_KEY_DELETE -> delete();
			case GLFW.GLFW_KEY_F4 -> refresh();
			case GLFW.GLFW_KEY_F5 -> print();
			case GLFW.GLFW_KEY_F6 -> scan();
			case GLFW.GLFW_KEY_F7 -> sendToBbs();
			default -> {
				return files.keyPressed(key);
			}
		}
		return true;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(col >= 35) {
			back = !back;
			return true;
		}
		return page == Page.LIST ? files.mouseClicked(col, row) : properties.mouseClicked(col, row);
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		return page == Page.LIST ? files.mouseScrolled(col, row, amount) : properties.mouseScrolled(col, row, amount);
	}
}
