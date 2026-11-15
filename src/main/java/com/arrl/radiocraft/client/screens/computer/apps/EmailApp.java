package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosFormDialog;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.DosList;
import com.arrl.radiocraft.client.screens.computer.DosMenuBar;
import com.arrl.radiocraft.client.screens.computer.DosTextArea;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.apps.EmailHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * LAN EMAIL (2026-11-13): this player's inbox on this computer and the mail of theirs still waiting to go. Enter reads,
 * N writes (To, then Subject, then the body in an editor; Ctrl+S sends), R replies, Del deletes, F3 shows the outbox,
 * F6 sets your username here (the one LAN RELAY uses too).
 */
public final class EmailApp extends DosApp {

	private static final long STATUS_MS = 2000L;
	private static final int LIST = 0, READ = 1, WRITE = 2;

	@Nullable
	private CompoundTag status;
	private long lastStatus;
	private int mode = LIST;
	private boolean outbox = false;
	private final DosList mails = new DosList();
	private final DosTextArea body = new DosTextArea(EmailHandler.MAX_BODY);
	private String writeTo = "", writeSubject = "";
	@Nullable
	private CompoundTag reading;
	private final List<String> readLines = new ArrayList<>();
	private int readTop = 0;

	public EmailApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		mails.onActivate(index -> open());
		BooleanSupplier list = () -> mode == LIST;
		BooleanSupplier inbox = () -> mode == LIST && !outbox;
		setMenuBar(new DosMenuBar(List.of(
				new DosMenuBar.Menu(Dos.tr("menu.file"), List.of(
						item("menu.email.send", "^S", this::send).when(() -> mode == WRITE),
						item("menu.refresh", "F5", GLFW.GLFW_KEY_F5).when(list),
						item("menu.email.back", "Esc", GLFW.GLFW_KEY_ESCAPE).when(() -> mode != LIST),
						item("menu.exit", "Esc", GLFW.GLFW_KEY_ESCAPE).when(list))),
				new DosMenuBar.Menu(Dos.tr("menu.email.mail"), List.of(
						item("menu.email.read", "Enter", GLFW.GLFW_KEY_ENTER).when(inbox),
						item("menu.email.new", "N", () -> onChar('N')).when(() -> mode != WRITE),
						item("menu.email.reply", "R", () -> onChar('R')).when(() -> mode == READ || inbox.getAsBoolean()),
						item("menu.delete", "Del", GLFW.GLFW_KEY_DELETE).when(() -> mode == READ || inbox.getAsBoolean()),
						item("menu.email.name", "F6", GLFW.GLFW_KEY_F6).when(() -> mode != WRITE))),
				new DosMenuBar.Menu(Dos.tr("menu.view"), List.of(
						item("menu.email.folder", "F3", GLFW.GLFW_KEY_F3).when(list),
						item("menu.scroll_up", "Up", GLFW.GLFW_KEY_UP).when(() -> mode != WRITE),
						item("menu.scroll_down", "Down", GLFW.GLFW_KEY_DOWN).when(() -> mode != WRITE))))));
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
	public void tick() {
		if(!hasDialog() && mode == LIST && System.currentTimeMillis() - lastStatus > STATUS_MS)
			refresh();
	}

	@Override
	public boolean holdsUnsavedWork() {
		return mode == WRITE && !body.text().isBlank();
	}

	private void refresh() {
		lastStatus = System.currentTimeMillis();
		session.app(app, "status", new CompoundTag(), response -> {
			if(response.ok()) {
				status = response.data();
				List<String> lines = new ArrayList<>();
				for(CompoundTag mail : folder())
					lines.add(outbox ? line(mail.getString("sent"), "-> " + mail.getString("to"), mail.getString("subject"), true)
							: line(mail.getString("sent"), mail.getString("from"), mail.getString("subject"), mail.getBoolean("read")));
				int was = mails.selected();
				mails.setItems(lines);
				if(was >= 0 && was < lines.size())
					mails.select(was);
			}
		});
	}

	private static String line(String sent, String who, String subject, boolean read) {
		return (read ? "  " : "* ") + Dos.fit(sent, 10) + "  " + Dos.fit(who, 26) + "  " + subject;
	}

	private List<CompoundTag> folder() {
		List<CompoundTag> out = new ArrayList<>();
		if(status != null) {
			ListTag list = status.getList(outbox ? "outbox" : "inbox", Tag.TAG_COMPOUND);
			for(int i = 0; i < list.size(); i++)
				out.add(list.getCompound(i));
		}
		return out;
	}

	@Nullable
	private CompoundTag selected() {
		List<CompoundTag> all = folder();
		int index = mails.selected();
		return index >= 0 && index < all.size() ? all.get(index) : null;
	}

	private boolean named() {
		return status != null && !status.getString("me").isEmpty();
	}

	private void open() {
		CompoundTag mail = selected();
		if(outbox || mail == null)
			return;
		CompoundTag data = new CompoundTag();
		data.putLong("id", mail.getLong("id"));
		session.app(app, "read", data, response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			reading = response.data();
			readLines.clear();
			readLines.add(Dos.tr("email.from", reading.getString("from")));
			readLines.add(Dos.tr("email.to", reading.getString("to")));
			readLines.add(Dos.tr("email.subject", reading.getString("subject")));
			readLines.add(Dos.tr("email.sent", reading.getString("sent")));
			readLines.add("");
			for(String paragraph : reading.getString("body").split("\n", -1))
				readLines.addAll(paragraph.isEmpty() ? List.of("") : AboutApp.wrap(paragraph, TextGrid.COLS - 6));
			readTop = 0;
			mode = READ;
			refresh();
		});
	}

	/** Asks for the address and subject, then opens the editor for the body. */
	private void compose(String to, String subject) {
		if(!named()) {
			askName();
			return;
		}
		DosInput toField = new DosInput(to, 40, c -> c >= 33 && c < 127);
		DosInput subjectField = new DosInput(subject, EmailHandler.MAX_SUBJECT, c -> c >= 32 && c < 127);
		showDialog(new DosFormDialog(Dos.tr("email.compose_title"), Dos.tr("email.compose_hint"), List.of(
				new DosFormDialog.Field(Dos.tr("email.to_label"), toField),
				new DosFormDialog.Field(Dos.tr("email.subject_label"), subjectField)), values -> {
			if(EmailHandler.parse(values.get(0).trim()) == null)
				return Dos.tr("err.email_bad_address");
			writeTo = values.get(0).trim();
			writeSubject = values.get(1).trim();
			body.setText("");
			mode = WRITE;
			return null;
		}).focus(to.isEmpty() ? 0 : 1));
	}

	private void reply() {
		CompoundTag mail = mode == READ ? reading : selected();
		if(mail == null || outbox)
			return;
		String subject = mail.getString("subject");
		compose(mail.getString("from"), subject.regionMatches(true, 0, "Re:", 0, 3) ? subject : Dos.fit("Re: " + subject, EmailHandler.MAX_SUBJECT).trim());
	}

	private void send() {
		if(mode != WRITE)
			return;
		CompoundTag data = new CompoundTag();
		data.putString("to", writeTo);
		data.putString("subject", writeSubject);
		data.putString("body", body.text());
		session.app(app, "send", data, response -> {
			if(response.ok()) {
				session.message(Dos.tr(response.data().getBoolean("waiting") ? "email.waiting" : "email.sent_ok"), false);
				mode = LIST;
				refresh();
			}
			else
				showError(response);
		});
	}

	private void delete() {
		CompoundTag mail = mode == READ ? reading : selected();
		if(mail == null || outbox)
			return;
		showDialog(DosDialog.confirm(Dos.tr("email.delete_title"), Dos.tr("email.delete_ask", mail.getString("subject")), () -> {
			CompoundTag data = new CompoundTag();
			data.putLong("id", mail.getLong("id"));
			session.app(app, "delete", data, response -> {
				mode = LIST;
				refresh();
			});
		}));
	}

	private void askName() {
		DosInput field = new DosInput(status == null ? "" : status.getString("me"), 16, c -> Character.isLetterOrDigit(c) && c < 128 || c == '-' || c == '_');
		showDialog(DosDialog.ask(Dos.tr("relay.name_title"), Dos.tr("relay.name_hint"), field, text -> {
			CompoundTag data = new CompoundTag();
			data.putString("name", text);
			session.app(com.arrl.radiocraft.common.computer.ComputerApps.RELAY, "name", data, response -> { // One username for the LAN programs.
				if(response.ok())
					refresh();
				else
					showError(response);
			});
		}));
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(mode == WRITE) {
			if(Dos.isSaveKey(key)) {
				send();
				return true;
			}
			return body.keyPressed(key);
		}
		switch(key) {
			case GLFW.GLFW_KEY_F5 -> refresh();
			case GLFW.GLFW_KEY_F6 -> askName(); // Not F2: Minecraft takes that for screenshots.
			case GLFW.GLFW_KEY_DELETE -> delete();
			case GLFW.GLFW_KEY_F3 -> {
				if(mode == LIST) {
					outbox = !outbox;
					mails.setItems(List.of());
					refresh();
				}
			}
			default -> {
				if(mode == READ)
					return readKey(key);
				return mails.keyPressed(key);
			}
		}
		return true;
	}

	private boolean readKey(int key) {
		int last = Math.max(0, readLines.size() - 1);
		switch(key) {
			case GLFW.GLFW_KEY_UP -> readTop = Math.max(0, readTop - 1);
			case GLFW.GLFW_KEY_DOWN -> readTop = Math.min(last, readTop + 1);
			case GLFW.GLFW_KEY_PAGE_UP -> readTop = Math.max(0, readTop - 15);
			case GLFW.GLFW_KEY_PAGE_DOWN -> readTop = Math.min(last, readTop + 15);
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	protected boolean onChar(char c) {
		if(mode == WRITE)
			return body.charTyped(c);
		if(c == 'n' || c == 'N') {
			compose("", "");
			return true;
		}
		if(c == 'r' || c == 'R') {
			reply();
			return true;
		}
		return false;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(mode == LIST)
			return mails.mouseClicked(col, row);
		if(mode == WRITE)
			return body.mouseClicked(col, row);
		return false;
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		if(mode == READ) {
			readTop = Math.max(0, Math.min(Math.max(0, readLines.size() - 1), readTop - (int)Math.signum(amount) * 3));
			return true;
		}
		if(mode == WRITE)
			return body.mouseScrolled(col, row, amount);
		return mails.mouseScrolled(col, row, amount);
	}

	@Override
	protected boolean onEscape() {
		if(mode == WRITE) {
			if(body.text().isBlank())
				mode = LIST;
			else
				showDialog(DosDialog.confirm(Dos.tr("email.discard_title"), Dos.tr("email.discard_ask"), () -> mode = LIST));
			return true;
		}
		if(mode == READ) {
			mode = LIST;
			return true;
		}
		return super.onEscape();
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("email.title"), Dos.tr(mode == WRITE ? "email.keys_write" : mode == READ ? "email.keys_read" : "email.keys"));
		int bg = DosColours.BODY_BG;
		CompoundTag s = status;
		if(s == null) {
			g.put(3, 3, Dos.tr("email.reading"), DosColours.BODY_FG, bg);
			return;
		}
		String me = s.getString("me"), computer = s.getString("computer");
		String address = me.isEmpty() ? Dos.tr("relay.no_name") : me + "@" + (computer.isEmpty() ? "?" : computer);
		g.put(2, 1, Dos.fit(Dos.tr("email.head", address), 77), DosColours.TEXT_FG, bg);
		if(mode == WRITE) {
			g.put(2, 3, Dos.fit(Dos.tr("email.writing", writeTo, writeSubject), TextGrid.COLS - 4), DosColours.YELLOW, bg);
			g.put(2, 4, Dos.tr("email.length", body.length(), EmailHandler.MAX_BODY), DosColours.BODY_FG, bg);
			body.render(g, 1, 5, TextGrid.COLS - 2, 17, DosColours.WHITE, DosColours.BLACK, !hasDialog());
			return;
		}
		if(mode == READ) {
			g.box(1, 2, TextGrid.COLS - 2, 21, false, "", DosColours.BODY_FG, bg);
			for(int i = 0; i < 19; i++) {
				int index = readTop + i;
				if(index < readLines.size())
					g.put(3, 3 + i, Dos.fit(readLines.get(index), TextGrid.COLS - 6), index < 4 ? DosColours.YELLOW : DosColours.WHITE, bg);
			}
			return;
		}
		g.box(1, 2, TextGrid.COLS - 2, 20, false, Dos.tr(outbox ? "email.outbox" : "email.inbox", folder().size()), DosColours.TEXT_FG, bg);
		if(me.isEmpty())
			g.put(3, 4, Dos.tr("email.set_name"), DosColours.YELLOW, bg);
		else if(mails.isEmpty())
			g.put(3, 4, Dos.tr(outbox ? "email.outbox_empty" : "email.inbox_empty"), DosColours.DIM, bg);
		else
			mails.render(g, 2, 3, TextGrid.COLS - 4, 18, DosColours.WHITE, bg, !hasDialog());
		if(computer.isEmpty())
			g.put(2, 22, Dos.fit(Dos.tr("email.no_computer_name"), 77), DosColours.YELLOW, bg);
		else if(!s.getList("outbox", Tag.TAG_COMPOUND).isEmpty() && !outbox)
			g.put(2, 22, Dos.fit(Dos.tr("email.outbox_waiting", s.getList("outbox", Tag.TAG_COMPOUND).size()), 77), DosColours.YELLOW, bg);
	}
}
