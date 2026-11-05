package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerResponse;
import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosFilePicker;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.DosList;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.photo.PhotoImage;
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
 * FAX (1.12 step 7): the computer as a radio fax machine with no paper, through the radio interface it is cabled to. The
 * LCD at the top says what the line is doing; the inbox lists what came in (kept on the disk as {@code FAX00001.IMG} or
 * {@code .TXT}), the log what went and came. F3 faxes a file (a picture, a text file or a book) to a callsign; F6 scans
 * what is on the scanner's glass and faxes that. Enter opens the one picked (a picture here, text in EDIT), F5 prints
 * it, Del deletes it; F4 hangs up; F7 switches auto answer. With a radio fax machine on the same station, the machine
 * answers the calls and the computer only sends.
 */
public final class FaxApp extends DosApp {

	private static final long REFRESH_MS = 2_000L;

	@Nullable
	private CompoundTag status;
	private long lastAsked = 0L;
	private final DosList inbox = new DosList();
	private final List<String> inboxNames = new ArrayList<>();
	@Nullable
	private String viewing;
	@Nullable
	private byte[] picture;

	public FaxApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		inbox.onActivate(index -> open());
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
		if("received".equals(kind))
			session.message(Dos.tr("fax.received", data.getString("name"), data.getString("from")), false);
		refresh();
	}

	private void refresh() {
		lastAsked = System.currentTimeMillis();
		session.app(app, "status", new CompoundTag(), response -> {
			if(response.ok())
				status = response.data();
		});
	}

	// -------------------- ACTIONS --------------------

	@Nullable
	private String selected() {
		int index = inbox.selected();
		return index >= 0 && index < inboxNames.size() ? inboxNames.get(index) : null;
	}

	private List<String> recent() {
		return status == null ? List.of() : ComputerServer.strings(status.getList("recent", Tag.TAG_STRING));
	}

	/** Pick a file, then a callsign, and fax it. */
	private void send() {
		showDialog(new DosFilePicker(session, Dos.tr("fax.pick_title"), "", name -> {
			String extension = ComputerStorage.extension(name);
			if(!extension.equals(ComputerFileFormats.IMAGE) && !extension.equals(ComputerFileFormats.TEXT) && !extension.equals(ComputerFileFormats.BOOK)) {
				showDialog(DosDialog.message(Dos.tr("error"), Dos.tr("fax.cannot_send_kind", extension)));
				return;
			}
			askCallsign(name);
		}));
	}

	private void askCallsign(String name) {
		List<String> recent = recent();
		DosInput call = new DosInput(recent.isEmpty() ? "" : recent.getFirst(), TncPacket.CALLSIGN_MAX, c -> Character.isLetterOrDigit(c) && c < 128 || c == '-' || c == '/');
		String hint = recent.isEmpty() ? Dos.tr("fax.to_hint", name) : Dos.tr("fax.to_hint_recent", name, String.join(" ", recent));
		showDialog(DosDialog.ask(Dos.tr("fax.to_title"), hint, call, value -> {
			CompoundTag data = new CompoundTag();
			data.putString("name", name);
			data.putString("to", value);
			session.app(app, "send", data, response -> {
				if(response.ok())
					refresh();
				else
					showError(response);
			});
		}));
	}

	/** Scans the glass into a file and faxes it. */
	private void scanAndSend() {
		session.scanner().save("FAXSCAN", response -> {
			if(response.ok())
				askCallsign(response.data().getString("name"));
			else
				showError(response);
		});
	}

	private void hangUp() {
		session.app(app, "cancel", new CompoundTag(), response -> refresh());
	}

	private void toggleAuto() {
		if(status == null)
			return;
		CompoundTag data = new CompoundTag();
		data.putBoolean("on", !status.getBoolean("auto"));
		session.app(app, "auto", data, response -> refresh());
	}

	private void open() {
		String name = selected();
		if(name == null)
			return;
		if(ComputerStorage.extension(name).equals(ComputerFileFormats.IMAGE))
			session.files().read(name, response -> {
				if(!response.ok()) {
					showError(response);
					return;
				}
				byte[] data = response.data().getByteArray("data");
				if(data.length == PhotoImage.PIXELS) {
					viewing = name;
					picture = data;
				}
			});
		else if(session.isInstalled(ComputerApps.EDITOR))
			session.launch(ComputerApps.EDITOR, name);
	}

	private void print() {
		String name = selected();
		if(name == null)
			return;
		java.util.function.Consumer<ComputerResponse> done = response -> {
			if(response.ok())
				session.message(Dos.tr("edit.printed"), false);
			else
				showError(response);
		};
		if(ComputerStorage.extension(name).equals(ComputerFileFormats.IMAGE))
			session.scanner().printImage(name, done);
		else
			session.scanner().printTextFile(name, done);
	}

	private void delete() {
		String name = selected();
		if(name == null)
			return;
		showDialog(DosDialog.confirm(Dos.tr("fax.delete_title"), Dos.tr("fax.delete_ask", name), () -> session.files().delete(name, response -> {
			CompoundTag data = new CompoundTag();
			data.putString("name", name);
			session.app(app, "forget", data, ignored -> refresh());
		})));
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		if(viewing != null && picture != null) {
			Dos.frame(g, Dos.tr("view.title", viewing), Dos.tr("view.keys"));
			g.box((TextGrid.COLS - 36) / 2, 1, 36, 23, false, "", DosColours.TEXT_FG, DosColours.BODY_BG);
			if(!hasDialog())
				g.image((TextGrid.WIDTH - 200) / 2, (TextGrid.HEIGHT - 200) / 2, 200, 200, "fax:" + viewing, picture, 128, 128);
			return;
		}
		Dos.frame(g, Dos.tr("fax.title"), Dos.tr("fax.keys"));
		int bg = DosColours.BODY_BG;
		StationLine.render(g, 1, status, false);

		// The LCD.
		g.box(1, 2, 78, 6, true, Dos.tr("fax.line"), DosColours.TEXT_FG, bg);
		if(status != null) {
			String phase = status.getString("phase");
			boolean busy = !phase.equals("IDLE");
			g.putField(3, 3, " " + status.getString("status") + " ", 50, DosColours.BLACK, busy ? DosColours.LIGHT_GREEN : 0x88AA88);
			if(busy) {
				g.put(3, 4, Dos.fit(Dos.tr(phase.equals("RECEIVING") ? "fax.from" : "fax.to", status.getString("peer"), status.getString("label")), 50), DosColours.TEXT_FG, bg);
				g.bar(3, 5, 50, status.getInt("packets") / (double)Math.max(1, status.getInt("of")), DosColours.LIGHT_CYAN, bg);
				g.put(55, 5, Dos.tr("fax.packets", status.getInt("packets"), status.getInt("of"), status.getInt("retries")), DosColours.BODY_FG, bg);
			}
			g.put(55, 3, Dos.tr(status.getBoolean("auto") ? "fax.auto_on" : "fax.auto_off"), status.getBoolean("auto") ? DosColours.LIGHT_GREEN : DosColours.BODY_FG, bg);
			if(status.getBoolean("machine"))
				g.put(55, 4, Dos.fit(Dos.tr("fax.machine"), 22), DosColours.YELLOW, bg);
		}

		// The inbox and the log.
		ListTag received = status == null ? new ListTag() : status.getList("inbox", Tag.TAG_COMPOUND);
		g.box(1, 8, 50, 15, false, Dos.tr("fax.inbox", received.size()), DosColours.TEXT_FG, bg);
		List<String> lines = new ArrayList<>();
		inboxNames.clear();
		for(int i = 0; i < received.size(); i++) {
			CompoundTag entry = received.getCompound(i);
			inboxNames.add(entry.getString("name"));
			lines.add(String.format(Locale.ROOT, "%-12s %-9s %s %s", entry.getString("name"), entry.getString("from"), StationLine.utc(entry.getLong("utc")),
					entry.getString("label")));
		}
		inbox.setItems(lines);
		if(lines.isEmpty())
			g.put(3, 10, Dos.tr("fax.inbox_empty"), DosColours.BODY_FG, bg);
		else
			inbox.render(g, 2, 9, 48, 13, DosColours.TEXT_FG, bg, true);
		g.box(52, 8, 27, 15, false, Dos.tr("fax.log"), DosColours.TEXT_FG, bg);
		List<String> log = status == null ? List.of() : ComputerServer.strings(status.getList("log", Tag.TAG_STRING));
		for(int i = 0; i < log.size() && i < 13; i++)
			g.put(53, 9 + i, Dos.fit(log.get(i), 25), log.get(i).startsWith("RX") ? DosColours.LIGHT_CYAN : DosColours.TEXT_FG, bg);
		StationLine.message(g, session);
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onEscape() {
		if(viewing != null) {
			viewing = null;
			picture = null;
			return true;
		}
		return super.onEscape();
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(viewing != null) {
			if(key != GLFW.GLFW_KEY_F5)
				return false;
			print(); // The viewer's F5: the picture shown is the one picked.
			return true;
		}
		switch(key) {
			case GLFW.GLFW_KEY_F3 -> send();
			case GLFW.GLFW_KEY_F4 -> hangUp();
			case GLFW.GLFW_KEY_F5 -> print();
			case GLFW.GLFW_KEY_F6 -> scanAndSend();
			case GLFW.GLFW_KEY_F7 -> toggleAuto();
			case GLFW.GLFW_KEY_DELETE -> delete();
			default -> {
				return inbox.keyPressed(key);
			}
		}
		return true;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		return viewing == null && inbox.mouseClicked(col, row);
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		return viewing == null && inbox.mouseScrolled(col, row, amount);
	}
}
