package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.radio.adsb.AdsbMath;
import com.arrl.radiocraft.common.radio.adsb.AdsbPicture;
import com.arrl.radiocraft.common.radio.adsb.AdsbScope;
import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.photo.PhotoPalette;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * ADSB, the ADS-B program (1.12 step 9): the computer as a second display of the ADS-B receiver cabled to it. On the
 * left the scope (north up, range rings, trails, each aircraft with its track line and flight identification); on the
 * right the receiver's state and a table of the aircraft, nearest first, and the one picked in full. The range and the
 * tags are the program's own, so the receiver's screen can show something else. F5 switches the receiver on; + and -
 * (or PgUp/PgDn) change the range; Up and Down pick an aircraft; T turns the tags on the scope off and on.
 */
public final class AdsbApp extends DosApp {

	private static final long STATUS_MS = 1_000L;
	private static final int SIZE = 128;
	private static final int[] RANGES = { 250, 500, 1000, 2000, 4000, 8000, 12000, 20000 };
	private static final int LIST_TOP = 7, LIST_ROWS = 10;

	@Nullable
	private CompoundTag status;
	private List<AdsbPicture.Contact> contacts = List.of();
	private long lastStatus = 0L;
	private int rangeIndex = 2;
	private int selected = -1;
	private boolean tags = true;
	private final byte[][] scope = { new byte[SIZE * SIZE], new byte[SIZE * SIZE] };
	private int buffer = 0;
	private boolean dirty = true;
	private final AdsbScope.Palette palette;

	public AdsbApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		PhotoPalette map = MapPalettes.vanilla();
		palette = new AdsbScope.Palette(id(map, 0x000000), id(map, 0x041C0A), id(map, 0x145A20), id(map, 0x1E7A2C), id(map, 0x8CFF96),
				id(map, 0xFF4030), id(map, 0x60E070), id(map, 0xFFFF60));
	}

	private static byte id(PhotoPalette palette, int rgb) {
		return palette.id(palette.nearest(rgb));
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
		session.app(app, "status", new CompoundTag(), response -> {
			if(!response.ok())
				return;
			status = response.data();
			contacts = AdsbPicture.contacts(status);
			dirty = true;
		});
	}

	private int range() {
		return RANGES[rangeIndex];
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("adsb.title"), Dos.tr("adsb.keys"));
		int bg = DosColours.BODY_BG;
		g.box(1, 2, 33, 21, false, Dos.tr("adsb.scope", rangeText(range())), DosColours.TEXT_FG, bg);
		g.box(34, 2, 45, 21, false, Dos.tr("adsb.traffic"), DosColours.TEXT_FG, bg);
		CompoundTag data = status;
		if(data == null) {
			g.put(36, 4, Dos.tr("adsb.reading"), DosColours.BODY_FG, bg);
			return;
		}
		if(!data.getBoolean("receiver")) {
			g.put(36, 4, Dos.tr("adsb.no_receiver"), DosColours.YELLOW, bg);
			g.put(36, 5, Dos.fit(Dos.tr("adsb.no_receiver_hint"), 41), DosColours.BODY_FG, bg);
			return;
		}
		boolean on = data.getBoolean("on"), powered = data.getBoolean("powered"), antenna = data.getBoolean("antenna");
		String state = !on ? "off" : !powered ? "no_power" : !antenna ? "no_antenna" : "receiving";
		g.put(36, 3, Dos.fit(Dos.tr("adsb.state." + state), 41), powered && antenna ? DosColours.LIGHT_GREEN : DosColours.YELLOW, bg);
		if(powered && antenna)
			g.put(36, 4, Dos.fit(Dos.tr("adsb.reach", rangeText(data.getInt("reach")), rangeText(data.getInt("reachLow"))), 41), DosColours.BODY_FG, bg);
		renderTable(g, bg);
		renderDetails(g, bg);
		if(powered && antenna && !hasDialog()) {
			if(dirty) {
				buffer ^= 1; // A new array, so the texture repaints.
				AdsbScope.paint(scope[buffer], SIZE, contacts, range(), selected, tags, palette);
				dirty = false;
			}
			g.image(2 * TextGrid.CELL_W + 1, 3 * TextGrid.CELL_H + 3, 184, 184, "adsb:" + session.pos().asLong(), scope[buffer], SIZE, SIZE);
		}
		else
			g.put(3, 12, Dos.fit(Dos.tr("adsb.scope_dark"), 29), DosColours.DARK_GREY, bg);
	}

	private void renderTable(TextGrid g, int bg) {
		g.put(36, LIST_TOP - 1, Dos.tr("adsb.head"), DosColours.YELLOW, bg);
		if(contacts.isEmpty()) {
			g.put(36, LIST_TOP, Dos.tr("adsb.none"), DosColours.BODY_FG, bg);
			return;
		}
		for(int i = 0; i < Math.min(LIST_ROWS, contacts.size()); i++) {
			AdsbPicture.Contact c = contacts.get(i);
			String line = String.format(Locale.ROOT, "%-8s %s %4d %03d %5.2f %s", c.label(), AdsbMath.flightLevel(c.feet()), c.knots(), c.track(),
					c.distance() / 1000.0D, AdsbMath.squawk(c.squawk()));
			boolean picked = c.icao() == selected;
			int fg = c.emergency() ? DosColours.LIGHT_RED : picked ? DosColours.HIGHLIGHT_FG : DosColours.TEXT_FG;
			g.putField(36, LIST_TOP + i, line, 41, fg, picked ? DosColours.HIGHLIGHT_BG : bg);
		}
		if(contacts.size() > LIST_ROWS)
			g.put(36, LIST_TOP + LIST_ROWS, Dos.tr("adsb.more", contacts.size() - LIST_ROWS), DosColours.BODY_FG, bg);
	}

	private void renderDetails(TextGrid g, int bg) {
		AdsbPicture.Contact c = selectedContact();
		if(c == null) {
			g.put(36, 19, Dos.fit(Dos.tr("adsb.pick"), 41), DosColours.DARK_GREY, bg);
			return;
		}
		int fg = c.emergency() ? DosColours.LIGHT_RED : DosColours.YELLOW;
		String emergency = AdsbMath.emergencyTag(c.squawk());
		g.put(36, 18, Dos.fit(c.label() + "  ICAO " + AdsbMath.hex(c.icao()) + "  SQUAWK " + AdsbMath.squawk(c.squawk()) + (emergency.isEmpty() ? "" : "  " + emergency), 41), fg, bg);
		g.put(36, 19, Dos.fit(Dos.tr("adsb.detail_alt", c.feet(), c.feetPerMinute()), 41), DosColours.TEXT_FG, bg);
		g.put(36, 20, Dos.fit(Dos.tr("adsb.detail_speed", c.knots(), String.format(Locale.ROOT, "%03d", c.track())), 41), DosColours.TEXT_FG, bg);
		g.put(36, 21, Dos.fit(Dos.tr("adsb.detail_where", String.format(Locale.ROOT, "%03d", Math.round(c.bearing()) % 360),
				String.format(Locale.ROOT, "%.2f", c.distance() / 1000.0D), c.age()), 41), DosColours.TEXT_FG, bg);
	}

	@Nullable
	private AdsbPicture.Contact selectedContact() {
		for(AdsbPicture.Contact c : contacts)
			if(c.icao() == selected)
				return c;
		return null;
	}

	/** "250 m" or "2 km". */
	private static String rangeText(int blocks) {
		if(blocks < 1000)
			return blocks + " m";
		return blocks % 1000 == 0 ? blocks / 1000 + " km" : String.format(Locale.ROOT, "%.1f km", blocks / 1000.0D);
	}

	// -------------------- INPUT --------------------

	private void zoom(int step) {
		rangeIndex = Math.max(0, Math.min(RANGES.length - 1, rangeIndex + step));
		dirty = true;
	}

	private void pick(int step) {
		if(contacts.isEmpty())
			return;
		int index = -1;
		for(int i = 0; i < contacts.size(); i++)
			if(contacts.get(i).icao() == selected)
				index = i;
		index = index < 0 ? 0 : Math.floorMod(index + step, Math.min(LIST_ROWS, contacts.size()));
		selected = contacts.get(index).icao();
		dirty = true;
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_F5 -> session.app(app, "power", new CompoundTag(), response -> {
				if(response.ok())
					refresh();
				else
					showError(response);
			});
			case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD, GLFW.GLFW_KEY_PAGE_UP -> zoom(1);
			case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT, GLFW.GLFW_KEY_PAGE_DOWN -> zoom(-1);
			case GLFW.GLFW_KEY_UP -> pick(-1);
			case GLFW.GLFW_KEY_DOWN -> pick(1);
			case GLFW.GLFW_KEY_T -> {
				tags = !tags;
				dirty = true;
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(col >= 36 && col < 77 && row >= LIST_TOP && row < LIST_TOP + Math.min(LIST_ROWS, contacts.size())) {
			selected = contacts.get(row - LIST_TOP).icao();
			dirty = true;
			return true;
		}
		return false;
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		if(col < 34) {
			zoom(amount > 0 ? -1 : 1);
			return true;
		}
		return false;
	}
}
