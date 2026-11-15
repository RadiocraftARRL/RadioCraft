package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.DosMenuBar;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * LAN RELAY (2026-11-13): the chat room of a relay server on the LAN. Enter types a line, F6 sets your username on
 * this computer, F3 steps through the LAN's rooms when there is more than one, F5 makes this computer a relay server
 * (it needs a SETUP name and background services on). On the right, who is in the room.
 */
public final class RelayApp extends DosApp {

	private static final long STATUS_MS = 1000L;
	private static final int TEXT_W = 56, ROWS = 17;

	@Nullable
	private CompoundTag status;
	private long lastStatus;
	/** The room picked, "" to take the only one. */
	private String server = "";
	private int scroll = 0;

	public RelayApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		setMenuBar(new DosMenuBar(List.of(
				new DosMenuBar.Menu(Dos.tr("menu.file"), List.of(
						item("menu.exit", "Esc", GLFW.GLFW_KEY_ESCAPE))),
				new DosMenuBar.Menu(Dos.tr("menu.relay.room"), List.of(
						item("menu.relay.say", "Enter", GLFW.GLFW_KEY_ENTER).when(() -> status != null && !status.getString("room").isEmpty()),
						item("menu.relay.name", "F6", GLFW.GLFW_KEY_F6),
						item("menu.relay.next_room", "F3", GLFW.GLFW_KEY_F3).when(() -> servers().size() > 1))),
				new DosMenuBar.Menu(Dos.tr("menu.relay.server"), List.of(
						item("menu.relay.serve", "F5", GLFW.GLFW_KEY_F5),
						item("menu.relay.clear", "F8", GLFW.GLFW_KEY_F8).when(() -> status != null && status.getBoolean("serving")))),
				new DosMenuBar.Menu(Dos.tr("menu.view"), List.of(
						item("menu.relay.older", "PgUp", GLFW.GLFW_KEY_PAGE_UP),
						item("menu.relay.newer", "PgDn", GLFW.GLFW_KEY_PAGE_DOWN))))));
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
		if(!hasDialog() && System.currentTimeMillis() - lastStatus > STATUS_MS)
			refresh();
	}

	private void refresh() {
		lastStatus = System.currentTimeMillis();
		CompoundTag data = new CompoundTag();
		data.putString("server", server);
		session.app(app, "status", data, response -> {
			if(response.ok())
				status = response.data();
		});
	}

	private List<String> servers() {
		List<String> out = new ArrayList<>();
		if(status != null) {
			ListTag list = status.getList("servers", Tag.TAG_STRING);
			for(int i = 0; i < list.size(); i++)
				out.add(list.getString(i));
		}
		return out;
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				if(status != null && status.getString("me").isEmpty())
					askName();
				else if(status != null && !status.getString("room").isEmpty()) {
					DosInput field = new DosInput("", 200, c -> c >= 32 && c != 127);
					showDialog(DosDialog.ask(Dos.tr("relay.say_title", status.getString("room")), Dos.tr("relay.say_hint"), field, text -> {
						CompoundTag data = new CompoundTag();
						data.putString("server", status.getString("room"));
						data.putString("text", text);
						send("say", data);
						scroll = 0;
					}));
				}
			}
			case GLFW.GLFW_KEY_F6 -> askName(); // Not F2: Minecraft takes that for screenshots.
			case GLFW.GLFW_KEY_F3 -> {
				List<String> servers = servers();
				if(!servers.isEmpty()) {
					String now = status == null ? "" : status.getString("room");
					int at = servers.indexOf(now);
					server = servers.get((at + 1) % servers.size());
					scroll = 0;
					refresh();
				}
			}
			case GLFW.GLFW_KEY_F5 -> {
				CompoundTag data = new CompoundTag();
				data.putBoolean("on", status == null || !status.getBoolean("serving"));
				send("serve", data);
			}
			case GLFW.GLFW_KEY_F8 -> {
				if(status != null && status.getBoolean("serving"))
					showDialog(DosDialog.confirm(Dos.tr("relay.clear_title"), Dos.tr("relay.clear"), () -> send("clear", new CompoundTag())));
			}
			case GLFW.GLFW_KEY_PAGE_UP -> scroll += ROWS / 2;
			case GLFW.GLFW_KEY_PAGE_DOWN -> scroll = Math.max(0, scroll - ROWS / 2);
			default -> {
				return false;
			}
		}
		return true;
	}

	private void askName() {
		DosInput field = new DosInput(status == null ? "" : status.getString("me"), 16, c -> Character.isLetterOrDigit(c) && c < 128 || c == '-' || c == '_');
		showDialog(DosDialog.ask(Dos.tr("relay.name_title"), Dos.tr("relay.name_hint"), field, text -> {
			CompoundTag data = new CompoundTag();
			data.putString("name", text);
			send("name", data);
		}));
	}

	private void send(String action, CompoundTag data) {
		session.app(app, action, data, response -> {
			if(response.ok())
				refresh();
			else
				showError(response);
		});
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("relay.title"), Dos.tr("relay.keys"));
		int bg = DosColours.BODY_BG;
		CompoundTag s = status;
		if(s == null) {
			g.put(3, 3, Dos.tr("relay.reading"), DosColours.BODY_FG, bg);
			return;
		}
		String me = s.getString("me");
		String room = s.getString("room");
		List<String> servers = servers();
		StringBuilder rooms = new StringBuilder();
		for(String name : servers)
			rooms.append(name.equalsIgnoreCase(room) ? "[" + name + "] " : name + " ");
		String head = Dos.tr("relay.head", me.isEmpty() ? Dos.tr("relay.no_name") : me, servers.isEmpty() ? Dos.tr("relay.no_rooms") : rooms.toString().trim());
		g.put(2, 1, Dos.fit(head, 77), DosColours.TEXT_FG, bg);

		g.box(1, 2, 60, 20, false, room.isEmpty() ? Dos.tr("relay.no_room") : Dos.tr("relay.room", room), DosColours.TEXT_FG, bg);
		if(room.isEmpty()) {
			int row = 4;
			String why = servers.isEmpty() ? "relay.none_hint" : "relay.pick_hint";
			for(String line : AboutApp.wrap(Dos.tr(why), TEXT_W))
				g.put(3, row++, line, DosColours.YELLOW, bg);
		}
		else {
			List<String> wrapped = new ArrayList<>();
			List<Integer> colours = new ArrayList<>();
			ListTag lines = s.getList("lines", Tag.TAG_COMPOUND);
			for(int i = 0; i < lines.size(); i++) {
				CompoundTag l = lines.getCompound(i);
				String text = l.getString("clock") + " <" + l.getString("user") + "@" + l.getString("computer") + "> " + l.getString("text");
				boolean mine = l.getString("user").equals(me);
				for(String part : AboutApp.wrap(text, TEXT_W)) {
					wrapped.add(part);
					colours.add(mine ? DosColours.LIGHT_CYAN : DosColours.TEXT_FG);
				}
			}
			scroll = Math.max(0, Math.min(scroll, Math.max(0, wrapped.size() - ROWS)));
			int end = wrapped.size() - scroll, start = Math.max(0, end - ROWS);
			for(int i = start; i < end; i++)
				g.put(3, 3 + i - start, wrapped.get(i), colours.get(i), bg);
			if(wrapped.isEmpty())
				g.put(3, 3, Dos.tr("relay.empty"), DosColours.DIM, bg);
		}
		if(me.isEmpty())
			g.put(3, 20, Dos.fit(Dos.tr("relay.set_name"), 56), DosColours.YELLOW, bg);

		g.box(62, 2, 17, 20, false, Dos.tr("relay.online"), DosColours.TEXT_FG, bg);
		ListTag online = s.getList("online", Tag.TAG_STRING);
		for(int i = 0; i < online.size() && i < 18; i++)
			g.put(63, 3 + i, Dos.fit(online.getString(i), 15), DosColours.LIGHT_GREEN, bg);

		String serving = !s.getBoolean("serving") ? Dos.tr("relay.not_serving")
				: !s.getBoolean("named") ? Dos.tr("relay.serving_no_name")
				: !s.getBoolean("services") ? Dos.tr("relay.serving_no_services") : Dos.tr("relay.serving");
		boolean trouble = s.getBoolean("serving") && (!s.getBoolean("named") || !s.getBoolean("services"));
		g.put(2, 22, Dos.fit(serving, 77), trouble ? DosColours.YELLOW : DosColours.DIM, bg);
	}
}
