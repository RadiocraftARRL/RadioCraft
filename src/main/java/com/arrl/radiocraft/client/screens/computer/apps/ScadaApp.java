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
import com.arrl.radiocraft.common.radio.scada.ScadaRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * SCADA (2026-11-10): the HMI for the SCADA master cabled to the computer. Top, its points: the master (unit 0) and
 * each RTU on its frequency, a column per face with the level it reads (white) or the rules put out (cyan), and COMM
 * FAIL in red. Bottom, the rules: Ins types a new one, Del removes the picked one, F3 steps the poll speed (1 to 3 seconds an RTU).
 * F4 shows trends (2026-11-13): one unit's face as a graph of its last readings; Up/Down picks the unit, Left/Right the face.
 */
public final class ScadaApp extends DosApp {

	private static final long STATUS_MS = 500L;
	private static final int RULE_ROWS = 7;

	@Nullable
	private CompoundTag status;
	private long lastStatus;
	private int selected = 0;
	private boolean trends = false;
	private int trendUnit = 0, trendFace = 0;
	@Nullable
	private CompoundTag trend;

	public ScadaApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		setMenuBar(new DosMenuBar(List.of( // 2026-11-12: each item does what its key does.
				new DosMenuBar.Menu(Dos.tr("menu.file"), List.of(
						item("menu.exit", "Esc", GLFW.GLFW_KEY_ESCAPE))),
				new DosMenuBar.Menu(Dos.tr("menu.edit"), List.of(
						item("menu.scada.add_rule", "Ins", GLFW.GLFW_KEY_INSERT),
						item("menu.scada.delete_rule", "Del", GLFW.GLFW_KEY_DELETE).when(() -> !rules().isEmpty()),
						item("menu.previous", "Up", GLFW.GLFW_KEY_UP),
						item("menu.next", "Down", GLFW.GLFW_KEY_DOWN))),
				new DosMenuBar.Menu(Dos.tr("menu.view"), List.of(
						item("menu.scada.trends", "F4", GLFW.GLFW_KEY_F4).when(() -> status != null && status.getBoolean("master")),
						item("menu.scada.previous_face", "Left", GLFW.GLFW_KEY_LEFT).when(() -> trends),
						item("menu.scada.next_face", "Right", GLFW.GLFW_KEY_RIGHT).when(() -> trends))),
				new DosMenuBar.Menu(Dos.tr("menu.radio"), List.of(
						item("menu.scada.poll", "F3", GLFW.GLFW_KEY_F3).when(() -> status != null))))));
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
			if(response.ok())
				status = response.data();
		});
		if(trends) {
			CompoundTag data = new CompoundTag();
			data.putInt("unit", trendUnit);
			session.app(app, "trend", data, response -> {
				if(response.ok())
					trend = response.data();
			});
		}
	}

	/** The units to trend: 0 (the master) and every RTU in the points list. */
	private List<Integer> unitIds() {
		List<Integer> ids = new java.util.ArrayList<>();
		ids.add(0);
		if(status != null) {
			ListTag units = status.getList("units", Tag.TAG_COMPOUND);
			for(int i = 0; i < units.size(); i++)
				ids.add(units.getCompound(i).getInt("id"));
		}
		return ids;
	}

	private void stepUnit(int by) {
		List<Integer> ids = unitIds();
		int at = Math.max(0, ids.indexOf(trendUnit));
		trendUnit = ids.get(Math.floorMod(at + by, ids.size()));
		trend = null;
		refresh();
	}

	private ListTag rules() {
		return status == null ? new ListTag() : status.getList("rules", Tag.TAG_STRING);
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(key == GLFW.GLFW_KEY_F4) {
			trends = !trends;
			trend = null;
			refresh();
			return true;
		}
		if(trends) {
			switch(key) {
				case GLFW.GLFW_KEY_UP -> stepUnit(-1);
				case GLFW.GLFW_KEY_DOWN -> stepUnit(1);
				case GLFW.GLFW_KEY_LEFT -> trendFace = Math.floorMod(trendFace - 1, 6);
				case GLFW.GLFW_KEY_RIGHT -> trendFace = Math.floorMod(trendFace + 1, 6);
				case GLFW.GLFW_KEY_ESCAPE -> trends = false;
				default -> {
					return false;
				}
			}
			return true;
		}
		switch(key) {
			case GLFW.GLFW_KEY_UP -> selected = Math.max(0, selected - 1);
			case GLFW.GLFW_KEY_DOWN -> selected = Math.min(Math.max(0, rules().size() - 1), selected + 1);
			case GLFW.GLFW_KEY_INSERT -> {
				DosInput field = new DosInput("", 40, c -> c >= 32 && c < 127);
				showDialog(DosDialog.ask(Dos.tr("scada.add_title"), Dos.tr("scada.add_hint"), field, text -> {
					CompoundTag data = new CompoundTag();
					data.putString("text", text);
					send("add", data);
				}));
			}
			case GLFW.GLFW_KEY_DELETE -> {
				if(!rules().isEmpty()) {
					CompoundTag data = new CompoundTag();
					data.putInt("index", selected);
					send("delete", data);
				}
			}
			case GLFW.GLFW_KEY_F3 -> {
				if(status != null) {
					CompoundTag data = new CompoundTag();
					data.putInt("n", status.getInt("poll") % ScadaRules.MAX_POLL + 1);
					send("poll", data);
				}
			}
			default -> {
				return false;
			}
		}
		return true;
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
		Dos.frame(g, Dos.tr("scada.title"), Dos.tr("scada.keys"));
		int bg = DosColours.BODY_BG;
		CompoundTag s = status;
		if(s == null) {
			g.put(3, 3, Dos.tr("scada.reading"), DosColours.BODY_FG, bg);
			return;
		}
		if(!s.getBoolean("master")) {
			g.box(1, 2, 78, 6, false, Dos.tr("scada.title"), DosColours.TEXT_FG, bg);
			g.put(3, 4, Dos.tr("scada.no_master"), DosColours.YELLOW, bg);
			g.put(3, 5, Dos.tr("scada.no_master_hint"), DosColours.BODY_FG, bg);
			return;
		}
		String head = s.getString("mhz").isEmpty() ? Dos.tr("scada.no_frequency")
				: Dos.tr("scada.head", s.getString("mhz"), s.getString("license"), s.getInt("poll"));
		boolean dark = s.contains("powered") && !s.getBoolean("powered");
		if(dark)
			head = Dos.tr("scada.no_power");
		g.put(2, 1, Dos.fit(head, 77), dark ? DosColours.LIGHT_RED : DosColours.TEXT_FG, bg);
		if(trends) {
			renderTrend(g, bg);
			return;
		}

		g.box(1, 2, 78, 11, false, Dos.tr("scada.points"), DosColours.TEXT_FG, bg);
		g.put(3, 3, Dos.fit(Dos.tr("scada.points_head"), 74), DosColours.YELLOW, bg);
		int row = 4;
		row = unitRow(g, row, "0 MASTER", s.getString("levels"), s.getString("modes"), false, "", bg);
		ListTag units = s.getList("units", Tag.TAG_COMPOUND);
		for(int i = 0; i < units.size() && row < 12; i++) {
			CompoundTag u = units.getCompound(i);
			long ago = u.getLong("ago");
			String seen = u.getBoolean("fail") ? Dos.tr("scada.comm_fail") : ago < 0 ? Dos.tr("scada.never") : Dos.tr("scada.ago", ago / 20L, u.getInt("replies"), u.getInt("polls"));
			row = unitRow(g, row, String.format(Locale.ROOT, "%d RTU", u.getInt("id")), u.getString("inputs"), null, u.getBoolean("fail"), seen, bg);
			String outputs = u.getString("outputs");
			if(!outputs.equals("------"))
				row = unitRow(g, row, "  " + Dos.tr("scada.wants"), outputs, null, false, "", bg, DosColours.LIGHT_CYAN);
		}
		if(units.isEmpty())
			g.put(3, row, Dos.fit(Dos.tr("scada.no_rtus"), 74), DosColours.BODY_FG, bg);

		g.box(1, 13, 78, 9, false, Dos.tr("scada.rules"), DosColours.TEXT_FG, bg);
		ListTag rules = rules();
		ListTag errors = s.getList("errors", Tag.TAG_STRING);
		selected = Math.max(0, Math.min(selected, Math.max(0, rules.size() - 1)));
		if(rules.isEmpty())
			g.put(3, 14, Dos.fit(Dos.tr("scada.no_rules"), 74), DosColours.BODY_FG, bg);
		int first = Math.max(0, selected - RULE_ROWS + 1);
		for(int i = first; i < rules.size() && i - first < RULE_ROWS; i++) {
			String line = rules.getString(i);
			boolean bad = false;
			for(int e = 0; e < errors.size(); e++)
				if(errors.getString(e).equals(line.trim()))
					bad = true;
			boolean picked = i == selected;
			g.putField(2, 14 + i - first, Dos.fit(String.format(Locale.ROOT, "%2d %s", i + 1, line), 76), 76,
					picked ? DosColours.HIGHLIGHT_FG : bad ? DosColours.LIGHT_RED : DosColours.TEXT_FG, picked ? DosColours.HIGHLIGHT_BG : bg);
		}
	}

	/** Trends: the picked unit's face as a graph, level 0 to 15 up the side, the newest reading on the right. */
	private void renderTrend(TextGrid g, int bg) {
		String unitName = trendUnit == 0 ? "0 MASTER" : String.format(Locale.ROOT, "%d RTU", trendUnit);
		g.box(1, 2, 78, 20, false, Dos.tr("scada.trend_title", unitName, String.valueOf(ScadaRules.FACES.charAt(trendFace))), DosColours.TEXT_FG, bg);
		CompoundTag t = trend;
		if(t == null || t.getInt("unit") != trendUnit) {
			g.put(3, 3, Dos.tr("scada.reading"), DosColours.BODY_FG, bg);
			return;
		}
		ListTag faces = t.getList("faces", Tag.TAG_STRING);
		String face = trendFace < faces.size() ? faces.getString(trendFace) : "";
		int width = 70;
		String shown = face.length() > width ? face.substring(face.length() - width) : face;
		for(int level = 15; level >= 0; level--) {
			int row = 3 + (15 - level);
			if(level % 5 == 0)
				g.put(3, row, String.format(Locale.ROOT, "%2d", level), DosColours.DIM, bg);
			for(int i = 0; i < shown.length(); i++) {
				int v = Character.digit(shown.charAt(i), 16);
				if(v < level)
					continue; // Nothing read here (-1), or below this row.
				g.put(6 + i, row, String.valueOf(v == level ? TextGrid.FULL : TextGrid.SHADE_LIGHT), v == level ? DosColours.LIGHT_CYAN : DosColours.CYAN, bg);
			}
		}
		boolean any = shown.chars().anyMatch(c -> c != '-');
		String foot = !any ? Dos.tr("scada.trend_none") : Dos.tr("scada.trend_span", shown.length(), t.getLong("span"), Math.max(0L, t.getLong("ago")));
		g.put(3, 19, Dos.fit(foot, 74), any ? DosColours.BODY_FG : DosColours.YELLOW, bg);
		for(int f = 0; f < 6; f++) {
			String data = f < faces.size() ? faces.getString(f) : "";
			boolean has = data.chars().anyMatch(c -> c != '-');
			g.put(3 + f * 6, 20, f == trendFace ? "[" + ScadaRules.FACES.charAt(f) + "]" : " " + ScadaRules.FACES.charAt(f) + " ",
					f == trendFace ? DosColours.HIGHLIGHT_FG : has ? DosColours.TEXT_FG : DosColours.DIM, f == trendFace ? DosColours.HIGHLIGHT_BG : bg);
		}
		g.put(40, 20, Dos.fit(Dos.tr("scada.trend_keys"), 37), DosColours.DIM, bg);
	}

	private static int unitRow(TextGrid g, int row, String name, String levels, @Nullable String modes, boolean fail, String note, int bg) {
		return unitRow(g, row, name, levels, modes, fail, note, bg, DosColours.TEXT_FG);
	}

	/** A row of six faces: '-' for none, the level otherwise; {@code modes} ("FNU...") marks the master's OUT faces. */
	private static int unitRow(TextGrid g, int row, String name, String levels, @Nullable String modes, boolean fail, String note, int bg, int colour) {
		g.put(3, row, Dos.fit(name, 12), fail ? DosColours.LIGHT_RED : colour, bg);
		for(int i = 0; i < 6; i++) {
			char c = i < levels.length() ? levels.charAt(i) : '-';
			int value = Character.digit(c, 16);
			boolean out = modes != null && i < modes.length() && modes.charAt(i) == 'U';
			String cell = value < 0 ? "  -" : String.format(Locale.ROOT, "%3d", value);
			g.put(16 + i * 5, row, cell, fail ? DosColours.DIM : out ? DosColours.LIGHT_CYAN : colour, bg);
		}
		g.put(47, row, Dos.fit(note, 31), fail ? DosColours.LIGHT_RED : DosColours.DIM, bg);
		return row + 1;
	}
}
