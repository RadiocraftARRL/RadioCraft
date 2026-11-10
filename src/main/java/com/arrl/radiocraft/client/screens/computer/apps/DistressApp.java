package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosPickDialog;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.init.RadiocraftSoundEvents;
import com.arrl.radiocraft.common.radio.DirectionFinding;
import com.arrl.radiocraft.common.radio.data.AprsMapPainter;
import com.arrl.radiocraft.common.radio.distress.DistressAlert;
import com.arrl.radiocraft.common.radio.distress.DistressMap;
import com.arrl.radiocraft.common.radio.distress.DistressNature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * DISTRESS (2026-11-09): the distress alerts heard by every radio cabled to the computer that can hear them (marine
 * radios' DSC receivers: DSC calls on channel 70; commercial VHF mobiles on 161.975 MHz: distress beacons), merged into
 * one list; the top right says what it can hear. On the left a map round the station and the alerts (the station's
 * box; DSC calls as red diamonds, beacons as orange rings, flashing while active, grey once over); on the right a list
 * (who: MMSI or the beacon's owner, nature, age, bearing and distance from the station, state), and the one picked in
 * full. A new alert sounds the alarm. Up and Down (or a click) pick; F5 acknowledges a DSC call through a marine radio
 * on channel 70; F6 prints a map of the area round the alert on the scanner. F7 sends an SOS (MARINE: a DSC call
 * through a marine radio; PERSONAL: a beacon alert on 161.975 through a commercial VHF radio, every 50 s), the nature
 * picked from a list and confirmed; F8 cancels it. The bottom row shows the outgoing SOS, or what F7 can send.
 */
public final class DistressApp extends DosApp {

	private static final long STATUS_MS = 1_000L, MAP_MS = 10_000L, FLASH_MS = 500L;
	private static final int SIZE = 128;
	private static final int LIST_TOP = 6, LIST_ROWS = 10;

	/** One alert from the status, with its ages in seconds. */
	private record Row(DistressAlert alert, long age, long heard) {}

	@Nullable
	private CompoundTag status;
	private List<Row> rows = List.of();
	private long lastStatus = 0L, lastMap = 0L;
	private long selected = 0L;
	/** Alerts already known, and in what state: a new one (or one gone active again) sounds the alarm. */
	private final Set<String> known = new HashSet<>();
	private boolean first = true;
	@Nullable
	private byte[] terrain;
	@Nullable
	private AprsMapPainter.Fit fit;
	private final byte[][] frames = { new byte[SIZE * SIZE], new byte[SIZE * SIZE] };
	private boolean dirty = true;

	public DistressApp(ComputerSession session, ComputerApp app) {
		super(session, app);
	}

	@Override
	public void opened() {
		refresh();
		refreshMap();
	}

	@Override
	public void resumed() {
		refresh();
	}

	@Override
	public void tick() {
		if(hasDialog())
			return;
		long now = System.currentTimeMillis();
		if(now - lastStatus > STATUS_MS)
			refresh();
		if(now - lastMap > MAP_MS)
			refreshMap();
	}

	private void refresh() {
		lastStatus = System.currentTimeMillis();
		session.app(app, "status", new CompoundTag(), response -> {
			if(!response.ok())
				return;
			status = response.data();
			List<Row> next = new ArrayList<>();
			for(Tag t : status.getList("alerts", Tag.TAG_COMPOUND)) {
				CompoundTag a = (CompoundTag)t;
				DistressAlert alert = DistressAlert.decode(a.getString("a"));
				if(alert != null)
					next.add(new Row(alert, a.getLong("age"), a.getLong("heard")));
			}
			boolean alarm = false;
			for(Row row : next) {
				String key = row.alert().id() + ":" + row.alert().state();
				if(known.add(key) && row.alert().state() == DistressAlert.State.ACTIVE && !first)
					alarm = true;
			}
			if(alarm)
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(RadiocraftSoundEvents.DSC_ALARM.get(), 1.0F, 0.6F));
			if(next.size() != rows.size() || alarm)
				refreshMap(); // New places to fit on the map.
			first = false;
			rows = next;
			if(selected == 0L && !rows.isEmpty())
				selected = rows.getFirst().alert().id();
			dirty = true;
		});
	}

	private void refreshMap() {
		lastMap = System.currentTimeMillis();
		session.app(app, "map", new CompoundTag(), response -> {
			if(!response.ok())
				return;
			CompoundTag d = response.data();
			byte[] pixels = d.getByteArray("pixels");
			if(pixels.length != SIZE * SIZE)
				return;
			terrain = pixels;
			fit = new AprsMapPainter.Fit(d.getInt("cx"), d.getInt("cz"), Math.max(1, d.getInt("bpp")), SIZE, SIZE);
			dirty = true;
		});
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		CompoundTag data = status;
		Dos.frame(g, data == null ? Dos.tr("distress.title") : Dos.tr("distress.title_mmsi", data.getString("mmsi")), Dos.tr("distress.keys"));
		int bg = DosColours.BODY_BG;
		g.box(1, 2, 33, 21, false, Dos.tr("distress.map"), DosColours.TEXT_FG, bg);
		g.box(34, 2, 45, 21, false, Dos.tr("distress.alerts"), DosColours.TEXT_FG, bg);
		if(data == null) {
			g.put(36, 4, Dos.tr("distress.reading"), DosColours.BODY_FG, bg);
			return;
		}
		renderSos(g, data, bg, now);
		if(!data.getBoolean("radio")) {
			int row = 3; // Wrapped, not cut short (2026-11-09).
			for(String line : AboutApp.wrap(Dos.tr("distress.no_radio"), 41))
				g.put(36, row++, line, DosColours.YELLOW, bg);
			for(String line : AboutApp.wrap(Dos.tr("distress.no_radio_hint"), 41))
				if(row < LIST_TOP + 8)
					g.put(36, row++, line, DosColours.BODY_FG, bg);
			return;
		}
		renderHearing(g, data, bg);
		renderList(g, bg, now);
		renderDetails(g, bg);
		if(terrain != null && fit != null && !hasDialog()) {
			if(dirty) {
				paint(frames[0], true);
				paint(frames[1], false);
				dirty = false;
			}
			byte[] frame = frames[(int)((now / FLASH_MS) % 2L)];
			g.image(2 * TextGrid.CELL_W + 1, 3 * TextGrid.CELL_H + 3, 184, 184, "distress:" + session.pos().asLong(), frame, SIZE, SIZE);
		}
	}

	/**
	 * What the program can hear (2026-11-09), two lines: "DSC: 1 marine radio" and "BEACONS: 1 radio on 161.975", or
	 * what is missing, in yellow.
	 */
	private static void renderHearing(TextGrid g, CompoundTag data, int bg) {
		int marines = data.getInt("marines"), marinesOn = data.getInt("marinesOn");
		String dsc = marinesOn > 0 ? Dos.tr("distress.hear.dsc", marinesOn) : marines > 0 ? Dos.tr("distress.hear.dsc_off") : Dos.tr("distress.hear.dsc_none");
		int commercials = data.getInt("commercials"), commercialsOn = data.getInt("commercialsOn"), beacon = data.getInt("beaconRadios");
		String beacons = beacon > 0 ? Dos.tr("distress.hear.beacons", beacon) : commercialsOn > 0 ? Dos.tr("distress.hear.beacons_tune")
				: commercials > 0 ? Dos.tr("distress.hear.beacons_off") : Dos.tr("distress.hear.beacons_none");
		g.put(36, 3, Dos.fit(dsc, 41), marinesOn > 0 ? DosColours.LIGHT_GREEN : DosColours.YELLOW, bg);
		g.put(36, 4, Dos.fit(beacons, 41), beacon > 0 ? DosColours.LIGHT_GREEN : DosColours.YELLOW, bg);
	}

	/**
	 * The bottom row (2026-11-09): an outgoing SOS, flashing red while it is on the air, with F8 to cancel it; else what
	 * F7 can send, each kind green when ready or grey with why not.
	 */
	private static void renderSos(TextGrid g, CompoundTag data, int bg, long now) {
		int row = TextGrid.ROWS - 2;
		boolean flash = (now / FLASH_MS) % 2L == 0L;
		List<String> out = new ArrayList<>();
		boolean on = false;
		if(data.contains("outDsc")) {
			CompoundTag dsc = data.getCompound("outDsc");
			String state = dsc.getString("state");
			on |= state.equals("ACTIVE");
			String nature = DistressNature.byName(dsc.getString("nature")).label();
			out.add(state.equals("ACKNOWLEDGED") ? Dos.tr("distress.out.dsc_acked", nature, dsc.getString("ackBy"))
					: Dos.tr("distress.out.dsc", nature, age(dsc.getLong("next"))));
		}
		if(data.contains("outPersonal")) {
			CompoundTag p = data.getCompound("outPersonal");
			String state = p.getString("state");
			String nature = DistressNature.byName(p.getString("nature")).label();
			if(state.equals("ACTIVE")) {
				on = true;
				out.add(Dos.tr("distress.out.personal", nature, age(p.getLong("next"))));
			}
			else if(state.equals("STOPPED"))
				out.add(Dos.tr("distress.out.personal_stopped." + p.getString("reason")));
		}
		if(!out.isEmpty()) {
			String line = String.join("  ", out) + (on || data.contains("outDsc") ? "  " + Dos.tr("distress.out.cancel") : "");
			g.putField(1, row, Dos.fit(line, TextGrid.COLS - 2), TextGrid.COLS - 2, on && flash ? DosColours.WHITE : DosColours.YELLOW,
					on ? DosColours.RED : bg);
			return;
		}
		int col = 1;
		String head = Dos.tr("distress.sos.head");
		g.put(col, row, head, DosColours.TEXT_FG, bg);
		col += head.length() + 1;
		for(String kind : new String[] { "marine", "personal" }) {
			String why = data.getString(kind + "Why");
			String text = Dos.tr("distress.sos." + kind) + ": " + (why.isEmpty() ? Dos.tr("distress.sos.ready") : Dos.tr("err." + why));
			text = Dos.fit(text, Math.max(0, TextGrid.COLS - 1 - col));
			g.put(col, row, text, why.isEmpty() ? DosColours.LIGHT_GREEN : DosColours.DARK_GREY, bg);
			col += text.length() + 2;
			if(col >= TextGrid.COLS - 1)
				break;
		}
	}

	/** The map with every alert's marker, lit or (for the flash's other half) dark. */
	private void paint(byte[] into, boolean lit) {
		System.arraycopy(terrain, 0, into, 0, into.length);
		for(int i = rows.size() - 1; i >= 0; i--) { // Oldest first, so the newest is on top.
			DistressAlert a = rows.get(i).alert();
			DistressMap.marker(into, fit, a, lit, a.id() == selected);
		}
	}

	private void renderList(TextGrid g, int bg, long now) {
		g.put(36, LIST_TOP - 1, Dos.tr("distress.head"), DosColours.YELLOW, bg);
		if(rows.isEmpty()) {
			g.put(36, LIST_TOP, Dos.fit(Dos.tr("distress.none"), 41), DosColours.BODY_FG, bg);
			return;
		}
		boolean flash = (now / FLASH_MS) % 2L == 0L;
		for(int i = 0; i < Math.min(LIST_ROWS, rows.size()); i++) {
			Row row = rows.get(i);
			DistressAlert a = row.alert();
			String line = String.format(Locale.ROOT, "%-9s %-7s %4s %03d %4s %s", clip(a.owner(), 9), a.isDsc() ? a.nature().shortLabel() : "BEACON",
					age(row.age()), Math.round(bearing(a)) % 360, distance(a), Dos.tr("distress.state_short." + a.state().name().toLowerCase(Locale.ROOT)));
			boolean picked = a.id() == selected;
			boolean active = a.state() == DistressAlert.State.ACTIVE;
			int fg = picked ? DosColours.HIGHLIGHT_FG : active ? (flash ? DosColours.LIGHT_RED : DosColours.WHITE) : DosColours.LIGHT_GREY;
			g.putField(36, LIST_TOP + i, line, 41, fg, picked ? DosColours.HIGHLIGHT_BG : bg);
		}
		if(rows.size() > LIST_ROWS)
			g.put(36, LIST_TOP + LIST_ROWS, Dos.tr("distress.more", rows.size() - LIST_ROWS), DosColours.BODY_FG, bg);
	}

	private void renderDetails(TextGrid g, int bg) {
		Row row = selectedRow();
		if(row == null) {
			g.put(36, 19, Dos.fit(Dos.tr("distress.pick"), 41), DosColours.DARK_GREY, bg);
			return;
		}
		DistressAlert a = row.alert();
		g.put(36, 17, Dos.fit(Dos.tr(a.isDsc() ? "distress.detail_dsc" : "distress.detail_beacon", a.owner()), 41), a.state() == DistressAlert.State.ACTIVE ? DosColours.LIGHT_RED : DosColours.YELLOW, bg);
		g.put(36, 18, Dos.fit(Dos.tr("distress.detail_nature", a.nature().label()), 41), DosColours.TEXT_FG, bg);
		g.put(36, 19, Dos.fit(a.coordinates(), 41), DosColours.TEXT_FG, bg);
		g.put(36, 20, Dos.fit(Dos.tr("distress.detail_where", String.format(Locale.ROOT, "%03d", Math.round(bearing(a)) % 360), distance(a), row.heard()), 41), DosColours.TEXT_FG, bg);
		String state = Dos.tr("distress.state." + a.state().name().toLowerCase(Locale.ROOT)) + (a.ackBy().isEmpty() ? "" : " " + Dos.tr("distress.ack_by", a.ackBy()));
		g.put(36, 21, Dos.fit(state, 41), DosColours.TEXT_FG, bg);
	}

	@Nullable
	private Row selectedRow() {
		for(Row row : rows)
			if(row.alert().id() == selected)
				return row;
		return null;
	}

	private double bearing(DistressAlert a) {
		CompoundTag data = status;
		return data == null ? 0.0D : DirectionFinding.bearing(data.getInt("x") + 0.5D, data.getInt("z") + 0.5D, a.x() + 0.5D, a.z() + 0.5D);
	}

	/** Blocks away in four characters at most: "850", "1.2k", "34k". */
	private String distance(DistressAlert a) {
		CompoundTag data = status;
		if(data == null)
			return "";
		double d = Math.hypot(a.x() - data.getInt("x"), a.z() - data.getInt("z"));
		if(d < 999.5D)
			return Long.toString(Math.round(d));
		return d < 9_950.0D ? String.format(Locale.ROOT, "%.1fk", d / 1000.0D) : Math.min(999L, Math.round(d / 1000.0D)) + "k";
	}

	/** "45s", "12m", "3h". */
	private static String age(long seconds) {
		if(seconds < 60L)
			return seconds + "s";
		if(seconds < 3600L)
			return seconds / 60L + "m";
		return seconds / 3600L + "h";
	}

	private static String clip(String text, int width) {
		return text.length() > width ? text.substring(0, width) : text;
	}

	// -------------------- INPUT --------------------

	private void pick(int step) {
		if(rows.isEmpty())
			return;
		int index = -1;
		for(int i = 0; i < rows.size(); i++)
			if(rows.get(i).alert().id() == selected)
				index = i;
		index = index < 0 ? 0 : Math.floorMod(index + step, Math.min(LIST_ROWS, rows.size()));
		selected = rows.get(index).alert().id();
		dirty = true;
	}

	private void act(String action) {
		Row row = selectedRow();
		if(row == null)
			return;
		CompoundTag data = new CompoundTag();
		data.putLong("id", row.alert().id());
		session.app(app, action, data, response -> {
			if(response.ok())
				refresh();
			else
				showError(response);
		});
	}

	// -------------------- SENDING AN SOS (2026-11-09) --------------------

	private static final String[] KINDS = { "marine", "personal" };

	/**
	 * F7: which SOS (MARINE: a DSC call through a marine radio; PERSONAL: a beacon alert on 161.975 through a commercial
	 * VHF radio; one that cannot go now says why), then the nature of distress from a list (UNDESIGNATED first), then
	 * a Yes/No before it goes.
	 */
	private void sendSos() {
		CompoundTag data = status;
		if(data == null)
			return;
		List<String> lines = new ArrayList<>();
		for(String kind : KINDS) {
			String why = data.getString(kind + "Why");
			lines.add(Dos.fit(Dos.tr("distress.sos." + kind + ".line") + (why.isEmpty() ? "" : "  (" + Dos.tr("err." + why) + ")"), 70));
		}
		showDialog(new DosPickDialog(Dos.tr("distress.sos.title"), Dos.tr("distress.sos.pick_kind"), lines, index -> {
			String kind = KINDS[index];
			String why = data.getString(kind + "Why");
			if(!why.isEmpty()) {
				showDialog(DosDialog.message(Dos.tr("distress.sos.title"), Dos.tr("err." + why)));
				return;
			}
			pickNature(kind);
		}));
	}

	private void pickNature(String kind) {
		List<String> lines = new ArrayList<>();
		for(DistressNature nature : DistressNature.values())
			lines.add(nature.label());
		showDialog(new DosPickDialog(Dos.tr("distress.sos.title"), Dos.tr("distress.sos.pick_nature"), lines, index -> {
			DistressNature nature = DistressNature.byIndex(index);
			showDialog(DosDialog.confirm(Dos.tr("distress.sos.confirm_title." + kind), Dos.tr("distress.sos.confirm." + kind, nature.label()), () -> {
				CompoundTag request = new CompoundTag();
				request.putString("kind", kind);
				request.putString("nature", nature.name());
				session.app(app, "send", request, response -> {
					if(response.ok()) {
						session.message(Dos.tr("distress.sos.sent." + kind), false);
						refresh();
					}
					else
						showError(response);
				});
			}));
		}));
	}

	/** F8: cancels the outgoing SOS (asks which if both are on), after a Yes/No; the cancel burst goes out. */
	private void cancelSos() {
		CompoundTag data = status;
		if(data == null)
			return;
		List<String> kinds = new ArrayList<>();
		if(data.contains("outDsc"))
			kinds.add("marine");
		if(data.contains("outPersonal") && data.getCompound("outPersonal").getString("state").equals("ACTIVE"))
			kinds.add("personal");
		if(kinds.isEmpty()) {
			session.message(Dos.tr("err.distress_nothing_to_cancel"), true);
			return;
		}
		if(kinds.size() == 1) {
			confirmCancel(kinds.getFirst());
			return;
		}
		List<String> lines = new ArrayList<>();
		for(String kind : kinds)
			lines.add(Dos.tr("distress.sos." + kind + ".line"));
		showDialog(new DosPickDialog(Dos.tr("distress.sos.cancel_title"), Dos.tr("distress.sos.pick_cancel"), lines, index -> confirmCancel(kinds.get(index))));
	}

	private void confirmCancel(String kind) {
		showDialog(DosDialog.confirm(Dos.tr("distress.sos.cancel_title"), Dos.tr("distress.sos.cancel_confirm." + kind), () -> {
			CompoundTag request = new CompoundTag();
			request.putString("kind", kind);
			session.app(app, "cancel", request, response -> {
				if(response.ok()) {
					session.message(Dos.tr("distress.sos.cancelled." + kind), false);
					refresh();
				}
				else
					showError(response);
			});
		}));
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_UP -> pick(-1);
			case GLFW.GLFW_KEY_DOWN -> pick(1);
			case GLFW.GLFW_KEY_F5 -> act("ack");
			case GLFW.GLFW_KEY_F6 -> act("print");
			case GLFW.GLFW_KEY_F7 -> sendSos();
			case GLFW.GLFW_KEY_F8 -> cancelSos();
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(col >= 36 && col < 77 && row >= LIST_TOP && row < LIST_TOP + Math.min(LIST_ROWS, rows.size())) {
			selected = rows.get(row - LIST_TOP).alert().id();
			dirty = true;
			return true;
		}
		return false;
	}
}
