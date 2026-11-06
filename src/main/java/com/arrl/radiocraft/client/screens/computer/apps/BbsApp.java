package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosFormDialog;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.DosList;
import com.arrl.radiocraft.client.screens.computer.DosTextArea;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.PacketBbs;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Packet BBS (computer apps 2): two pages, Tab between them. SYSOP is the console of the mailbox this computer runs
 * (its callsign, whether it is online, every message, the traffic as it happens): Enter reads a message, N writes one,
 * B posts a bulletin, Del deletes, F7 sets the SSID and switches it on or off. TERMINAL talks to someone else's BBS as a
 * caller: F7 sets its callsign, each line typed goes out as an APRS message and its answers print below. The server side
 * ({@code BbsHandler}) runs the mailbox while nobody is at the computer.
 */
public final class BbsApp extends DosApp {

	private static final long REFRESH_MS = 5_000L;
	private static final int SYSOP = 0, TERMINAL = 1;
	private static final int LIST = 0, READ = 1, WRITE = 2;

	private int page = SYSOP;
	private int mode = LIST;
	@Nullable
	private CompoundTag status;
	private long lastAsked = 0L;
	private final DosList messages = new DosList();
	private List<String> reading = List.of();
	private int readTop = 0;
	// Writing a message.
	private final DosTextArea body = new DosTextArea(PacketBbs.BODY_MAX);
	private String writeTo = "", writeSubject = "";
	private boolean writeBulletin = false;
	// The terminal.
	private final DosInput line = new DosInput("", TncPacket.MESSAGE_TEXT_MAX, c -> c >= 32 && c < 127);
	private int termScroll = 0; // Lines up from the newest.

	public BbsApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		messages.onActivate(index -> read());
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
		if(System.currentTimeMillis() - lastAsked > REFRESH_MS && !hasDialog())
			refresh();
	}

	@Override
	public void event(String kind, CompoundTag data) {
		if("update".equals(kind)) {
			refresh();
			if("term".equals(data.getString("what")))
				termScroll = 0;
		}
	}

	private void refresh() {
		lastAsked = System.currentTimeMillis();
		session.app(app, "status", new CompoundTag(), response -> {
			if(!response.ok())
				return;
			status = response.data();
			List<String> lines = new ArrayList<>();
			for(Tag tag : messageTags()) {
				CompoundTag m = (CompoundTag)tag;
				lines.add(String.format(Locale.ROOT, "%5d %s%s %-9s %-9s %-6s %s", m.getInt("n"), m.getBoolean("bulletin") ? "B" : "P",
						m.getBoolean("bulletin") || m.getBoolean("read") ? " " : "*", m.getString("from"), m.getString("to"),
						StationLine.ago(m.getLong("ago")), m.getString("subject")));
			}
			messages.setItems(lines);
		});
	}

	private ListTag messageTags() {
		return status == null ? new ListTag() : status.getList("messages", Tag.TAG_COMPOUND);
	}

	private List<String> strings(String key) {
		return status == null ? List.of() : ComputerServer.strings(status.getList(key, Tag.TAG_STRING));
	}

	// -------------------- ACTIONS --------------------

	private int selectedNumber() {
		ListTag list = messageTags();
		int index = messages.selected();
		return index >= 0 && index < list.size() ? list.getCompound(index).getInt("n") : -1;
	}

	private void read() {
		int n = selectedNumber();
		if(n < 0)
			return;
		CompoundTag data = new CompoundTag();
		data.putInt("n", n);
		session.app(app, "read", data, response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			reading = ComputerServer.strings(response.data().getList("lines", Tag.TAG_STRING));
			readTop = 0;
			mode = READ;
		});
	}

	private void delete() {
		int n = selectedNumber();
		if(n < 0)
			return;
		showDialog(DosDialog.confirm(Dos.tr("bbs.delete_title"), Dos.tr("bbs.delete_ask", n), () -> {
			CompoundTag data = new CompoundTag();
			data.putInt("n", n);
			session.app(app, "delete", data, response -> {
				if(response.ok()) {
					session.message(Dos.tr("bbs.deleted", n), false);
					refresh();
				}
				else
					showError(response);
			});
		}));
	}

	/** Asks who it is for and the subject, then opens the editor for the body. */
	private void compose(boolean bulletin) {
		DosInput to = new DosInput(bulletin ? "ALL" : "", TncPacket.CALLSIGN_MAX, c -> Character.isLetterOrDigit(c) && c < 128 || c == '-' || c == '/');
		DosInput subject = new DosInput("", PacketBbs.SUBJECT_MAX, c -> c >= 32 && c < 127);
		showDialog(new DosFormDialog(Dos.tr(bulletin ? "bbs.bulletin_title" : "bbs.compose_title"), Dos.tr("bbs.compose_hint"), List.of(
				new DosFormDialog.Field(Dos.tr(bulletin ? "bbs.topic" : "bbs.to"), to),
				new DosFormDialog.Field(Dos.tr("bbs.subject"), subject)), values -> {
			if(values.get(0).isBlank())
				return Dos.tr(bulletin ? "bbs.need_topic" : "bbs.need_to");
			writeTo = values.get(0).toUpperCase(Locale.ROOT);
			writeSubject = values.get(1);
			writeBulletin = bulletin;
			body.setText("");
			mode = WRITE;
			return null;
		}).focus(bulletin ? 1 : 0));
	}

	private void post() {
		CompoundTag data = new CompoundTag();
		data.putString("to", writeTo);
		data.putString("subject", writeSubject);
		data.putString("body", body.text());
		data.putBoolean("bulletin", writeBulletin);
		session.app(app, "post", data, response -> {
			if(response.ok()) {
				session.message(Dos.tr("bbs.posted"), false);
				mode = LIST;
				refresh();
			}
			else
				showError(response);
		});
	}

	private void settings() {
		if(status == null)
			return;
		DosInput ssid = new DosInput(String.valueOf(status.getInt("ssid")), 2, Character::isDigit);
		DosInput online = new DosInput(status.getBoolean("online") ? "Y" : "N", 1, c -> "YNyn".indexOf(c) >= 0);
		showDialog(new DosFormDialog(Dos.tr("bbs.setup_title"), Dos.tr("bbs.setup_hint"), List.of(
				new DosFormDialog.Field(Dos.tr("bbs.ssid"), ssid),
				new DosFormDialog.Field(Dos.tr("bbs.online"), online)), values -> {
			int value;
			try {
				value = Integer.parseInt(values.get(0));
			}
			catch(NumberFormatException e) {
				value = -1;
			}
			if(value < PacketBbs.SSID_MIN || value > PacketBbs.SSID_MAX)
				return Dos.tr("bbs.bad_ssid");
			CompoundTag data = new CompoundTag();
			data.putInt("ssid", value);
			data.putBoolean("online", values.get(1).equalsIgnoreCase("Y"));
			session.app(app, "settings", data, response -> {
				if(response.ok())
					refresh();
				else
					showError(response);
			});
			return null;
		}));
	}

	private String termTo() {
		return status == null ? "" : status.getString("termTo");
	}

	private void connect() {
		DosInput call = new DosInput(termTo(), TncPacket.CALLSIGN_MAX, c -> Character.isLetterOrDigit(c) && c < 128 || c == '-' || c == '/');
		showDialog(DosDialog.ask(Dos.tr("bbs.connect_title"), Dos.tr("bbs.connect_hint"), call, value -> {
			if(value.isBlank())
				return;
			CompoundTag data = new CompoundTag();
			data.putString("to", value);
			data.putString("text", "");
			session.app(app, "term", data, response -> {
				if(response.ok())
					refresh();
				else
					showError(response);
			});
		}));
	}

	private void sendLine() {
		String text = line.text();
		if(text.isBlank())
			return;
		if(termTo().isEmpty()) {
			connect();
			return;
		}
		CompoundTag data = new CompoundTag();
		data.putString("to", termTo());
		data.putString("text", text);
		session.app(app, "term", data, response -> {
			if(response.ok()) {
				line.set("");
				termScroll = 0;
				refresh();
			}
			else
				showError(response);
		});
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(page == SYSOP && mode == WRITE) {
			if(Dos.isSaveKey(key)) {
				post();
				return true;
			}
			return body.keyPressed(key);
		}
		if(key == GLFW.GLFW_KEY_TAB) {
			page = page == SYSOP ? TERMINAL : SYSOP;
			mode = LIST;
			refresh();
			return true;
		}
		if(key == GLFW.GLFW_KEY_F5) {
			refresh();
			return true;
		}
		if(page == TERMINAL) {
			switch(key) {
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> sendLine();
				case GLFW.GLFW_KEY_F7 -> connect();
				case GLFW.GLFW_KEY_F8 -> session.app(app, "term_clear", new CompoundTag(), response -> refresh());
				case GLFW.GLFW_KEY_PAGE_UP -> termScroll += 10;
				case GLFW.GLFW_KEY_PAGE_DOWN -> termScroll = Math.max(0, termScroll - 10);
				default -> {
					return line.keyPressed(key);
				}
			}
			return true;
		}
		if(mode == READ) {
			switch(key) {
				case GLFW.GLFW_KEY_UP -> readTop = Math.max(0, readTop - 1);
				case GLFW.GLFW_KEY_DOWN -> readTop = Math.min(Math.max(0, reading.size() - 1), readTop + 1);
				case GLFW.GLFW_KEY_PAGE_UP -> readTop = Math.max(0, readTop - 15);
				case GLFW.GLFW_KEY_PAGE_DOWN -> readTop = Math.min(Math.max(0, reading.size() - 1), readTop + 15);
				default -> {
					return false;
				}
			}
			return true;
		}
		switch(key) {
			case GLFW.GLFW_KEY_DELETE -> delete();
			case GLFW.GLFW_KEY_F7 -> settings();
			default -> {
				return messages.keyPressed(key);
			}
		}
		return true;
	}

	@Override
	protected boolean onChar(char c) {
		if(page == TERMINAL)
			return line.charTyped(c);
		if(mode == WRITE)
			return body.charTyped(c);
		if(mode == LIST) {
			if(c == 'n' || c == 'N') {
				compose(false);
				return true;
			}
			if(c == 'b' || c == 'B') {
				compose(true);
				return true;
			}
		}
		return false;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(page == SYSOP && mode == LIST)
			return messages.mouseClicked(col, row);
		if(page == SYSOP && mode == WRITE)
			return body.mouseClicked(col, row);
		return false;
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		if(page == TERMINAL) {
			termScroll = Math.max(0, termScroll + (int)Math.signum(amount) * 3);
			return true;
		}
		if(mode == READ) {
			readTop = Math.max(0, Math.min(Math.max(0, reading.size() - 1), readTop - (int)Math.signum(amount) * 3));
			return true;
		}
		if(mode == WRITE)
			return body.mouseScrolled(col, row, amount);
		return messages.mouseScrolled(col, row, amount);
	}

	@Override
	protected boolean onEscape() {
		if(page == SYSOP && mode == WRITE) {
			if(body.text().isBlank())
				mode = LIST;
			else
				showDialog(DosDialog.confirm(Dos.tr("bbs.discard_title"), Dos.tr("bbs.discard_ask"), () -> mode = LIST));
			return true;
		}
		if(page == SYSOP && mode == READ) {
			mode = LIST;
			return true;
		}
		return super.onEscape();
	}

	// -------------------- DRAWING --------------------

	private String keys() {
		if(page == TERMINAL)
			return Dos.tr("bbs.keys_terminal");
		return Dos.tr(mode == WRITE ? "bbs.keys_write" : mode == READ ? "bbs.keys_read" : "bbs.keys_sysop");
	}

	@Override
	public void render(TextGrid g, long now) {
		String bbs = status == null ? "" : status.getString("bbs");
		Dos.frame(g, Dos.tr("bbs.title", bbs.isEmpty() ? Dos.tr("station.no_call") : bbs), keys());
		int bg = DosColours.BODY_BG;
		String[] tabs = { Dos.tr("bbs.tab_sysop"), Dos.tr("bbs.tab_terminal") };
		int col = 2;
		for(int i = 0; i < tabs.length; i++) {
			String label = " " + tabs[i] + " ";
			g.put(col, 1, label, i == page ? DosColours.HIGHLIGHT_FG : DosColours.BODY_FG, i == page ? DosColours.HIGHLIGHT_BG : bg);
			col += label.length() + 1;
		}
		StationLine.render(g, 2, status, true);
		if(page == TERMINAL)
			renderTerminal(g);
		else if(mode == READ)
			renderRead(g);
		else if(mode == WRITE)
			renderWrite(g);
		else
			renderSysop(g);
		StationLine.message(g, session);
	}

	private void renderSysop(TextGrid g) {
		int bg = DosColours.BODY_BG;
		if(status != null) {
			boolean online = status.getBoolean("online");
			String state = Dos.tr(online ? "bbs.state_online" : "bbs.state_offline", status.getString("bbs").isEmpty() ? Dos.tr("station.no_call") : status.getString("bbs"),
					messageTags().size(), PacketBbs.MAX_MESSAGES);
			g.put(2, 3, Dos.fit(state, TextGrid.COLS - 4), online ? DosColours.WHITE : DosColours.YELLOW, bg);
		}
		g.put(2, 4, Dos.tr("bbs.columns"), DosColours.YELLOW, bg);
		if(messages.isEmpty())
			g.put(3, 5, Dos.tr("bbs.no_messages"), DosColours.BODY_FG, bg);
		else
			messages.render(g, 1, 5, TextGrid.COLS - 2, 9, DosColours.WHITE, bg, !hasDialog());
		g.hline(1, 14, TextGrid.COLS - 2, TextGrid.H, DosColours.BODY_FG, bg);
		g.put(3, 14, " " + Dos.tr("bbs.activity") + " ", DosColours.YELLOW, bg);
		List<String> log = strings("log");
		int rows = 8;
		for(int i = 0; i < rows; i++) {
			int index = log.size() - rows + i;
			if(index >= 0)
				g.put(2, 15 + i, Dos.fit(log.get(index), TextGrid.COLS - 4), log.get(index).startsWith("  ") ? DosColours.LIGHT_CYAN : DosColours.WHITE, bg);
		}
	}

	private void renderRead(TextGrid g) {
		int bg = DosColours.BODY_BG;
		g.box(1, 3, TextGrid.COLS - 2, 20, false, "", DosColours.BODY_FG, bg);
		for(int i = 0; i < 18; i++) {
			int index = readTop + i;
			if(index < reading.size())
				g.put(3, 4 + i, Dos.fit(reading.get(index), TextGrid.COLS - 6), i == 0 && readTop == 0 ? DosColours.YELLOW : DosColours.WHITE, bg);
		}
	}

	private void renderWrite(TextGrid g) {
		int bg = DosColours.BODY_BG;
		g.put(2, 3, Dos.fit(Dos.tr(writeBulletin ? "bbs.writing_bulletin" : "bbs.writing", writeTo, writeSubject), TextGrid.COLS - 4), DosColours.YELLOW, bg);
		g.put(2, 4, Dos.tr("bbs.length", body.length(), PacketBbs.BODY_MAX), DosColours.BODY_FG, bg);
		body.render(g, 1, 5, TextGrid.COLS - 2, 17, DosColours.WHITE, DosColours.BLACK, !hasDialog());
	}

	private void renderTerminal(TextGrid g) {
		int bg = DosColours.BODY_BG;
		String to = termTo();
		g.put(2, 3, Dos.fit(to.isEmpty() ? Dos.tr("bbs.not_connected") : Dos.tr("bbs.connected", to), TextGrid.COLS - 4), to.isEmpty() ? DosColours.YELLOW : DosColours.WHITE, bg);
		List<String> log = new ArrayList<>();
		for(String each : strings("termLog"))
			log.addAll(PacketBbs.split(each, TextGrid.COLS - 4));
		int rows = 16;
		termScroll = Math.min(termScroll, Math.max(0, log.size() - rows));
		int start = log.size() - rows - termScroll;
		g.fill(1, 4, TextGrid.COLS - 2, rows, ' ', DosColours.LIGHT_GREY, DosColours.BLACK);
		for(int i = 0; i < rows; i++) {
			int index = start + i;
			if(index >= 0 && index < log.size())
				g.put(2, 4 + i, log.get(index), log.get(index).startsWith("> ") ? DosColours.LIGHT_GREEN : DosColours.LIGHT_GREY, DosColours.BLACK);
		}
		g.put(2, 21, ">", DosColours.WHITE, bg);
		line.render(g, 4, 21, TextGrid.COLS - 6, !hasDialog());
	}
}
