package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosFormDialog;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.DosList;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.radio.data.AprsIcon;
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
 * APRS (1.12 step 7): the computer as an APRS station, through the radio interface and VHF radio it is cabled to. Three
 * pages, Tab between them: STATIONS (every station heard, its distance and bearing from here, when, its comment), the
 * MESSAGES to and from this station (with delivery: sending, retrying, sent once acked, failed), and a MAP of them in the
 * H19's style. F5 beacons now; N writes a message (R replies to the one picked, or messages the station picked); F6 sets
 * the beacon (comment, symbol, automatic beaconing); F7 sets the station's callsign. The server side
 * ({@code AprsHandler}) keeps the station going while nobody is at the computer.
 */
public final class AprsApp extends DosApp {

	private static final long REFRESH_MS = 5_000L, MAP_REFRESH_MS = 15_000L;
	private static final int STATIONS = 0, MESSAGES = 1, MAP = 2;

	private int page = STATIONS;
	@Nullable
	private CompoundTag status;
	@Nullable
	private byte[] map;
	private long lastAsked = 0L, lastMap = 0L;
	private final DosList stations = new DosList();
	private final DosList messages = new DosList();

	public AprsApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		stations.onActivate(index -> compose(callOf(index), ""));
		messages.onActivate(index -> reply());
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
		long now = System.currentTimeMillis();
		if(now - lastAsked > REFRESH_MS && !hasDialog())
			refresh();
		if(page == MAP && now - lastMap > MAP_REFRESH_MS)
			refreshMap();
	}

	@Override
	public void event(String kind, CompoundTag data) {
		if("update".equals(kind)) {
			refresh();
			if("message".equals(data.getString("what")))
				session.message(Dos.tr("aprs.message_in", data.getString("from")), false);
			else if("station".equals(data.getString("what")))
				session.message(Dos.tr("aprs.station_in", data.getString("from")), false);
		}
	}

	private void refresh() {
		lastAsked = System.currentTimeMillis();
		session.app(app, "status", new CompoundTag(), response -> {
			if(response.ok())
				status = response.data();
		});
		if(page == MESSAGES)
			session.app(app, "read", new CompoundTag(), response -> {});
	}

	private void refreshMap() {
		lastMap = System.currentTimeMillis();
		session.app(app, "map", new CompoundTag(), response -> {
			if(response.ok() && response.data().getByteArray("pixels").length == 128 * 128) {
				map = response.data().getByteArray("pixels");
			}
		});
	}

	// -------------------- ACTIONS --------------------

	private ListTag stationTags() {
		return status == null ? new ListTag() : status.getList("stations", Tag.TAG_COMPOUND);
	}

	private ListTag messageTags() {
		return status == null ? new ListTag() : status.getList("messages", Tag.TAG_COMPOUND);
	}

	private String callOf(int index) {
		ListTag list = stationTags();
		return index >= 0 && index < list.size() ? list.getCompound(index).getString("call") : "";
	}

	private void beacon() {
		session.app(app, "beacon", new CompoundTag(), response -> {
			if(response.ok()) {
				session.message(Dos.tr("aprs.beaconed"), false);
				refresh();
			}
			else
				showError(response);
		});
	}

	/** A message to {@code to} (both fields editable). */
	private void compose(String to, String text) {
		DosInput toField = new DosInput(to, TncPacket.CALLSIGN_MAX, c -> Character.isLetterOrDigit(c) && c < 128 || c == '-' || c == '/');
		DosInput textField = new DosInput(text, TncPacket.MESSAGE_TEXT_MAX, c -> c >= 32 && c < 127);
		showDialog(new DosFormDialog(Dos.tr("aprs.compose_title"), Dos.tr("aprs.compose_hint"), List.of(
				new DosFormDialog.Field(Dos.tr("aprs.to"), toField),
				new DosFormDialog.Field(Dos.tr("aprs.text"), textField)), values -> {
			if(values.get(0).isBlank())
				return Dos.tr("aprs.need_to");
			if(values.get(1).isBlank())
				return Dos.tr("aprs.need_text");
			CompoundTag data = new CompoundTag();
			data.putString("to", values.get(0));
			data.putString("text", values.get(1));
			session.app(app, "send", data, response -> {
				if(response.ok()) {
					session.message(Dos.tr("aprs.sent", values.get(0).toUpperCase(Locale.ROOT)), false);
					refresh();
				}
				else
					showError(response);
			});
			return null;
		}).focus(to.isEmpty() ? 0 : 1));
	}

	/** Reply to the message picked (to whoever it was from, or to whom it went). */
	private void reply() {
		ListTag list = messageTags();
		int index = messages.selected();
		if(index < 0 || index >= list.size()) {
			compose("", "");
			return;
		}
		CompoundTag message = list.getCompound(index);
		compose(message.getBoolean("out") ? message.getString("to") : message.getString("from"), "");
	}

	private void settings() {
		if(status == null)
			return;
		DosInput comment = new DosInput(status.getString("comment"), TncPacket.BEACON_TEXT_MAX, c -> c >= 32 && c < 127);
		DosInput icon = new DosInput(status.getString("icon"), 8, c -> Character.isLetter(c) && c < 128);
		DosInput auto = new DosInput(status.getBoolean("auto") ? "Y" : "N", 1, c -> "YNyn".indexOf(c) >= 0);
		StringBuilder icons = new StringBuilder();
		for(AprsIcon each : AprsIcon.values())
			if(each != AprsIcon.WX)
				icons.append(icons.isEmpty() ? "" : " ").append(each.name());
		showDialog(new DosFormDialog(Dos.tr("aprs.setup_title"), Dos.tr("aprs.setup_hint", icons.toString(), status.getInt("interval") / 60), List.of(
				new DosFormDialog.Field(Dos.tr("aprs.comment"), comment),
				new DosFormDialog.Field(Dos.tr("aprs.icon"), icon),
				new DosFormDialog.Field(Dos.tr("aprs.auto"), auto)), values -> {
			CompoundTag data = new CompoundTag();
			data.putString("comment", values.get(0));
			data.putString("icon", values.get(1).isBlank() ? "HOUSE" : values.get(1));
			data.putBoolean("auto", values.get(2).equalsIgnoreCase("Y"));
			session.app(app, "settings", data, response -> {
				if(response.ok())
					refresh();
				else
					showError(response);
			});
			return null;
		}));
	}

	private void callsign() {
		DosInput call = new DosInput(status == null ? "" : status.getString("callsign"), TncPacket.CALLSIGN_MAX, c -> Character.isLetterOrDigit(c) && c < 128 || c == '-' || c == '/');
		showDialog(DosDialog.ask(Dos.tr("aprs.call_title"), Dos.tr("aprs.call_hint"), call, value -> {
			CompoundTag data = new CompoundTag();
			data.putString("callsign", value);
			session.app(app, "callsign", data, response -> {
				if(response.ok())
					refresh();
				else
					showError(response);
			});
		}));
	}

	private void clear() {
		showDialog(DosDialog.confirm(Dos.tr("aprs.clear_title"), Dos.tr("aprs.clear_ask"), () -> session.app(app, "clear", new CompoundTag(), response -> refresh())));
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		String call = status == null ? "" : status.getString("callsign");
		Dos.frame(g, Dos.tr("aprs.title", call.isEmpty() ? Dos.tr("station.no_call") : call), Dos.tr("aprs.keys"));
		int bg = DosColours.BODY_BG;
		String[] tabs = { Dos.tr("aprs.tab_stations"), Dos.tr("aprs.tab_messages"), Dos.tr("aprs.tab_map") };
		int col = 2;
		for(int i = 0; i < tabs.length; i++) {
			String label = " " + tabs[i] + (i == MESSAGES && unread() > 0 ? " (" + unread() + ")" : "") + " ";
			g.put(col, 1, label, i == page ? DosColours.HIGHLIGHT_FG : DosColours.BODY_FG, i == page ? DosColours.HIGHLIGHT_BG : bg);
			col += label.length() + 1;
		}
		StationLine.render(g, 2, status, true);
		if(status != null) {
			long last = status.getLong("lastBeacon");
			String beacon = Dos.tr(status.getBoolean("auto") ? "aprs.beacon_auto" : "aprs.beacon_manual", status.getInt("interval") / 60,
					last < 0 ? Dos.tr("aprs.never") : Dos.tr("aprs.ago", StationLine.ago(last)), status.getString("icon"), status.getString("comment"));
			g.put(2, 3, Dos.fit(beacon, TextGrid.COLS - 4), DosColours.BODY_FG, bg);
		}
		switch(page) {
			case STATIONS -> renderStations(g);
			case MESSAGES -> renderMessages(g);
			default -> renderMap(g);
		}
		StationLine.message(g, session);
	}

	private int unread() {
		int count = 0;
		ListTag list = messageTags();
		for(int i = 0; i < list.size(); i++)
			if(list.getCompound(i).getBoolean("unread"))
				count++;
		return count;
	}

	private static String stationLine(CompoundTag station) {
		String where = station.getBoolean("position") ? String.format(Locale.ROOT, "%6d %03d", station.getInt("distance"), station.getInt("bearing")) : "     -   -";
		return String.format(Locale.ROOT, "%-10s %-8s %s %5s %3d  %s", station.getString("call"), station.getString("icon"), where,
				StationLine.ago(station.getLong("ago")), station.getInt("hops"), station.getString("comment"));
	}

	private void renderStations(TextGrid g) {
		int bg = DosColours.BODY_BG;
		g.box(1, 4, 78, 15, false, Dos.tr("aprs.stations", stationTags().size()), DosColours.TEXT_FG, bg);
		g.put(2, 5, Dos.tr("aprs.stations_head"), DosColours.YELLOW, bg);
		List<String> lines = new ArrayList<>();
		ListTag list = stationTags();
		for(int i = 0; i < list.size(); i++)
			lines.add(stationLine(list.getCompound(i)));
		stations.setItems(lines);
		if(lines.isEmpty())
			g.put(3, 7, Dos.tr("aprs.no_stations"), DosColours.BODY_FG, bg);
		else
			stations.render(g, 2, 6, 76, 12, DosColours.TEXT_FG, bg, true);
		int index = stations.selected();
		if(index >= 0 && index < list.size()) {
			CompoundTag station = list.getCompound(index);
			g.box(1, 19, 78, 4, false, station.getString("call"), DosColours.TEXT_FG, bg);
			String at = station.getBoolean("position") ? Dos.tr("aprs.at", station.getInt("x"), station.getInt("z"), station.getInt("distance"), station.getInt("bearing"))
					: Dos.tr("aprs.no_position");
			g.put(3, 20, Dos.fit(at, 74), DosColours.TEXT_FG, bg);
			g.put(3, 21, Dos.fit(Dos.tr("aprs.first_heard", StationLine.ago(station.getLong("first")), station.getString("comment")), 74), DosColours.BODY_FG, bg);
		}
	}

	private static int deliveryColour(String delivery) {
		return switch(delivery) {
			case "SENT" -> DosColours.LIGHT_GREEN;
			case "FAILED" -> DosColours.LIGHT_RED;
			default -> DosColours.YELLOW;
		};
	}

	private void renderMessages(TextGrid g) {
		int bg = DosColours.BODY_BG;
		ListTag list = messageTags();
		g.box(1, 4, 78, 18, false, Dos.tr("aprs.messages", list.size()), DosColours.TEXT_FG, bg);
		List<String> lines = new ArrayList<>();
		for(int i = 0; i < list.size(); i++) {
			CompoundTag message = list.getCompound(i);
			boolean out = message.getBoolean("out");
			String who = out ? Dos.tr("aprs.to_short", message.getString("to")) : Dos.tr("aprs.from_short", message.getString("from"));
			String state = out ? Dos.tr("aprs.delivery." + message.getString("delivery").toLowerCase(Locale.ROOT), message.getInt("retries")) : message.getBoolean("unread") ? Dos.tr("aprs.new") : "";
			lines.add(String.format(Locale.ROOT, "%4s %-14s %-9s %s", StationLine.ago(message.getLong("ago")), who, state, message.getString("text")));
		}
		messages.setItems(lines);
		if(lines.isEmpty())
			g.put(3, 6, Dos.tr("aprs.no_messages"), DosColours.BODY_FG, bg);
		else
			messages.render(g, 2, 5, 76, 16, DosColours.TEXT_FG, bg, true);
		// Colour the delivery column of the lines in view.
		for(int r = 0; r < 16; r++) {
			int index = messages.top() + r;
			if(index < 0 || index >= list.size() || index == messages.selected())
				continue;
			CompoundTag message = list.getCompound(index);
			int colour = message.getBoolean("out") ? deliveryColour(message.getString("delivery")) : message.getBoolean("unread") ? DosColours.LIGHT_CYAN : DosColours.TEXT_FG;
			for(int c = 22; c < 31; c++)
				g.colour(c, 5 + r, colour, bg);
		}
	}

	private void renderMap(TextGrid g) {
		int bg = DosColours.BODY_BG;
		g.box(1, 4, 34, 19, false, Dos.tr("aprs.tab_map"), DosColours.TEXT_FG, bg);
		if(map == null)
			g.put(3, 6, Dos.tr("aprs.map_reading"), DosColours.BODY_FG, bg);
		else if(!hasDialog())
			g.image(12, 50, 170, 170, "aprs:" + session.pos().asLong(), map, 128, 128);
		g.box(36, 4, 43, 19, false, Dos.tr("aprs.stations", stationTags().size()), DosColours.TEXT_FG, bg);
		ListTag list = stationTags();
		for(int i = 0; i < list.size() && i < 16; i++) {
			CompoundTag station = list.getCompound(i);
			String where = station.getBoolean("position") ? String.format(Locale.ROOT, "%5d blk %03d", station.getInt("distance"), station.getInt("bearing")) : "";
			g.put(38, 6 + i, Dos.fit(String.format(Locale.ROOT, "%-10s %s  %s", station.getString("call"), where, StationLine.ago(station.getLong("ago"))), 39), DosColours.TEXT_FG, bg);
		}
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_TAB -> {
				page = (page + ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 2 : 1)) % 3;
				if(page == MAP)
					refreshMap();
				if(page == MESSAGES)
					refresh();
			}
			case GLFW.GLFW_KEY_F5 -> beacon();
			case GLFW.GLFW_KEY_F6 -> settings();
			case GLFW.GLFW_KEY_F7 -> callsign();
			case GLFW.GLFW_KEY_F8 -> clear();
			default -> {
				if(page == STATIONS)
					return stations.keyPressed(key);
				if(page == MESSAGES)
					return messages.keyPressed(key);
				return false;
			}
		}
		return true;
	}

	@Override
	protected boolean onChar(char c) {
		switch(Character.toUpperCase(c)) {
			case 'N' -> compose(page == STATIONS ? callOf(stations.selected()) : "", "");
			case 'R' -> {
				if(page == MESSAGES)
					reply();
				else
					compose(callOf(stations.selected()), "");
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(row == 1) {
			int c = 2;
			for(int i = 0; i < 3; i++) {
				String[] tabs = { Dos.tr("aprs.tab_stations"), Dos.tr("aprs.tab_messages"), Dos.tr("aprs.tab_map") };
				int width = tabs[i].length() + 2 + (i == MESSAGES && unread() > 0 ? (" (" + unread() + ")").length() : 0);
				if(col >= c && col < c + width) {
					page = i;
					if(page == MAP)
						refreshMap();
					return true;
				}
				c += width + 1;
			}
			return false;
		}
		if(page == STATIONS)
			return stations.mouseClicked(col, row);
		if(page == MESSAGES)
			return messages.mouseClicked(col, row);
		return false;
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		if(page == STATIONS)
			return stations.mouseScrolled(col, row, amount);
		if(page == MESSAGES)
			return messages.mouseScrolled(col, row, amount);
		return false;
	}
}
