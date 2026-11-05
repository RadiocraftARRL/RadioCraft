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
import com.arrl.radiocraft.common.computer.DfSearches;
import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.photo.PhotoPalette;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * DFIND, direction finding (1.12 step 7): the computer as the display of the direction finder cabled to it. On the left
 * the set (frequency, power, modules locked, the meter, its bearing) and the bearings the fix is made from; on the right
 * three pages, Tab between them: MAP, the terrain of the search with each bearing and its spread and the fix in its
 * circle; 3D, the search turning in wireframe with the fix as a hill of likelihood (a spike is a good fix); SEARCHES, the
 * past searches saved on the disk (Enter shows one on the map). The set is worked from here as from its own panel: F5
 * power, Left/Right tune, PgUp/PgDn band, F7 types a frequency, F6 adds a bearing taken elsewhere, F8 removes the last
 * one typed; F3 saves the search, Del deletes a saved one.
 */
public final class DfApp extends DosApp {

	private static final long STATUS_MS = 1_000L, MAP_MS = 10_000L, FRAME_MS = 80L;
	private static final int MAP = 0, SCENE = 1, SEARCHES = 2;
	private static final int SCENE_W = 160, SCENE_H = 100;

	private int page = MAP;
	@Nullable
	private CompoundTag status;
	@Nullable
	private CompoundTag mapData;
	@Nullable
	private byte[] plotted;
	private int mapSearch = -1;
	private String plottedFor = "";
	private long lastStatus = 0L, lastMap = 0L, lastFrame = 0L;
	@Nullable
	private ListTag searches;
	private final DosList searchList = new DosList();
	private final byte[][] scene = { new byte[SCENE_W * SCENE_H], new byte[SCENE_W * SCENE_H] };
	private int sceneBuffer = 0;
	private boolean sceneDrawn = false;

	// Map colours, worked out once.
	private final byte own, typed, linked, fix, edge, grid, mast, peak, hill, black;

	public DfApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		PhotoPalette palette = MapPalettes.vanilla();
		own = id(palette, 0xFFFF40);
		typed = id(palette, 0x40E0FF);
		linked = id(palette, 0x60FF60);
		fix = id(palette, 0xFF3030);
		edge = id(palette, 0x000000);
		grid = id(palette, 0x205020);
		mast = id(palette, 0xE0E0E0);
		peak = id(palette, 0xFF4040);
		hill = id(palette, 0xFFA040);
		black = id(palette, 0x000000);
		searchList.onActivate(index -> {
			mapSearch = index;
			page = MAP;
			refreshMap();
		});
	}

	private static byte id(PhotoPalette palette, int rgb) {
		return palette.id(palette.nearest(rgb));
	}

	@Override
	public void opened() {
		refresh();
		refreshMap();
		refreshSearches();
	}

	@Override
	public void resumed() {
		opened();
	}

	@Override
	public void tick() {
		long now = System.currentTimeMillis();
		if(hasDialog())
			return;
		if(now - lastStatus > STATUS_MS)
			refresh();
		if(page == MAP && mapSearch < 0 && now - lastMap > MAP_MS)
			refreshMap();
	}

	private void refresh() {
		lastStatus = System.currentTimeMillis();
		session.app(app, "status", new CompoundTag(), response -> {
			if(!response.ok())
				return;
			String before = status == null ? "" : bearingsKey(status);
			status = response.data();
			if(mapSearch < 0 && !bearingsKey(status).equals(before))
				refreshMap(); // New bearings: a new map round them.
		});
	}

	private void refreshMap() {
		lastMap = System.currentTimeMillis();
		CompoundTag data = new CompoundTag();
		data.putInt("search", mapSearch);
		session.app(app, "map", data, response -> {
			if(response.ok()) {
				mapData = response.data();
				plotted = null;
			}
		});
	}

	private void refreshSearches() {
		session.app(app, "searches", new CompoundTag(), response -> {
			if(response.ok())
				searches = response.data().getList("searches", Tag.TAG_COMPOUND);
		});
	}

	/** A short key of a status's bearings, to tell when they change. */
	private static String bearingsKey(CompoundTag data) {
		StringBuilder key = new StringBuilder();
		ListTag list = data.getList("bearings", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size(); i++) {
			CompoundTag b = list.getCompound(i);
			key.append(Math.round(b.getDouble("x"))).append(',').append(Math.round(b.getDouble("z"))).append(',').append(Math.round(b.getDouble("bearing"))).append(';');
		}
		return key.toString();
	}

	// -------------------- WORKING THE SET --------------------

	private void control(String action, CompoundTag data) {
		session.app(app, action, data, response -> {
			if(response.ok())
				refresh();
			else
				showError(response);
		});
	}

	private void tune(int steps) {
		CompoundTag data = new CompoundTag();
		data.putInt("steps", steps);
		control("tune", data);
	}

	private void band(int direction) {
		CompoundTag data = new CompoundTag();
		data.putInt("direction", direction);
		control("band", data);
	}

	private void typeFrequency() {
		DosInput mhz = new DosInput(status == null ? "" : String.format(Locale.ROOT, "%.3f", status.getFloat("frequency") / 1_000_000.0F), 10,
				c -> Character.isDigit(c) || c == '.');
		showDialog(DosDialog.ask(Dos.tr("df.freq_title"), Dos.tr("df.freq_hint"), mhz, value -> {
			try {
				CompoundTag data = new CompoundTag();
				data.putFloat("mhz", Float.parseFloat(value));
				control("frequency", data);
			}
			catch(NumberFormatException e) {
				session.message(Dos.tr("df.not_a_number", value), true);
			}
		}));
	}

	private void addBearing() {
		java.util.function.Predicate<Character> number = c -> Character.isDigit(c) || c == '-' || c == '.';
		DosInput x = new DosInput("", 8, number), z = new DosInput("", 8, number), bearing = new DosInput("", 6, number), sigma = new DosInput("5", 5, number);
		showDialog(new DosFormDialog(Dos.tr("df.add_title"), Dos.tr("df.add_hint"), List.of(
				new DosFormDialog.Field(Dos.tr("df.add_x"), x),
				new DosFormDialog.Field(Dos.tr("df.add_z"), z),
				new DosFormDialog.Field(Dos.tr("df.add_bearing"), bearing),
				new DosFormDialog.Field(Dos.tr("df.add_sigma"), sigma)), values -> {
			try {
				CompoundTag data = new CompoundTag();
				data.putInt("x", (int)Math.round(Double.parseDouble(values.get(0))));
				data.putInt("z", (int)Math.round(Double.parseDouble(values.get(1))));
				data.putDouble("bearing", Double.parseDouble(values.get(2)));
				data.putDouble("sigma", Math.max(0.5D, Double.parseDouble(values.get(3))));
				control("add", data);
				return null;
			}
			catch(NumberFormatException e) {
				return Dos.tr("df.add_bad");
			}
		}));
	}

	private void removeTyped() {
		int count = status == null ? 0 : status.getList("typed", Tag.TAG_STRING).size();
		if(count == 0)
			return;
		CompoundTag data = new CompoundTag();
		data.putInt("index", count - 1);
		control("remove", data);
	}

	private void save() {
		DosInput note = new DosInput("", DfSearches.NOTE_MAX);
		showDialog(DosDialog.ask(Dos.tr("df.save_title"), Dos.tr("df.save_hint"), note, value -> {
			CompoundTag data = new CompoundTag();
			data.putString("note", value);
			session.app(app, "save", data, response -> {
				if(response.ok()) {
					session.message(Dos.tr("df.saved"), false);
					refreshSearches();
				}
				else
					showError(response);
			});
		}));
	}

	private void deleteSearch() {
		int index = searchList.selected();
		if(searches == null || index < 0 || index >= searches.size())
			return;
		showDialog(DosDialog.confirm(Dos.tr("df.delete_title"), Dos.tr("df.delete_ask"), () -> {
			CompoundTag data = new CompoundTag();
			data.putInt("index", index);
			session.app(app, "delete", data, response -> {
				if(mapSearch == index)
					mapSearch = -1;
				refreshSearches();
			});
		}));
	}

	// -------------------- DRAWING --------------------

	private byte colour(String source) {
		return switch(source) {
			case "OWN" -> own;
			case "LINKED" -> linked;
			default -> typed;
		};
	}

	private List<DfDrawing.Bearing> bearings(@Nullable CompoundTag data) {
		List<DfDrawing.Bearing> out = new ArrayList<>();
		if(data == null)
			return out;
		ListTag list = data.getList("bearings", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size(); i++) {
			CompoundTag b = list.getCompound(i);
			out.add(new DfDrawing.Bearing(b.getDouble("x"), b.getDouble("z"), b.getDouble("bearing"), b.getDouble("sigma"), colour(b.getString("source"))));
		}
		return out;
	}

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("df.title"), Dos.tr("df.keys"));
		int bg = DosColours.BODY_BG;
		String[] tabs = { Dos.tr("df.tab_map"), Dos.tr("df.tab_3d"), Dos.tr("df.tab_searches") };
		int col = 32;
		for(int i = 0; i < tabs.length; i++) {
			String label = " " + tabs[i] + " ";
			g.put(col, 1, label, i == page ? DosColours.HIGHLIGHT_FG : DosColours.BODY_FG, i == page ? DosColours.HIGHLIGHT_BG : bg);
			col += label.length() + 1;
		}
		renderSet(g, bg);
		switch(page) {
			case MAP -> renderMap(g, bg);
			case SCENE -> renderScene(g, bg, now);
			default -> renderSearches(g, bg);
		}
		StationLine.message(g, session);
	}

	private void renderSet(TextGrid g, int bg) {
		g.box(1, 2, 30, 21, false, Dos.tr("df.set"), DosColours.TEXT_FG, bg);
		if(status == null) {
			g.put(3, 4, Dos.tr("df.reading"), DosColours.BODY_FG, bg);
			return;
		}
		if(!status.getBoolean("finder")) {
			g.put(3, 4, Dos.tr("df.no_finder"), DosColours.YELLOW, bg);
			g.put(3, 5, Dos.tr("df.no_finder_hint"), DosColours.BODY_FG, bg);
			return;
		}
		int r = 3;
		g.put(3, r++, String.format(Locale.ROOT, "%.3f MHz  %s", status.getFloat("frequency") / 1_000_000.0F, status.getString("band")), DosColours.TEXT_FG, bg);
		String power = !status.getBoolean("switchedOn") ? Dos.tr("df.off") : !status.getBoolean("powered") ? Dos.tr("df.no_power") : Dos.tr("df.on");
		g.put(3, r++, power, status.getBoolean("on") ? DosColours.LIGHT_GREEN : DosColours.YELLOW, bg);
		g.put(3, r++, Dos.tr("df.modules", status.getInt("locked"), status.getInt("usable"), status.getInt("modules")), DosColours.BODY_FG, bg);
		g.put(3, r, Dos.tr("df.meter"), DosColours.BODY_FG, bg);
		g.bar(9, r++, 20, status.getDouble("meter"), status.getBoolean("hearing") ? DosColours.LIGHT_GREEN : DosColours.DARK_GREY, bg);
		if(status.getBoolean("hasBearing"))
			g.put(3, r++, Dos.tr("df.bearing", Math.round(status.getDouble("bearing")) % 360, String.format(Locale.ROOT, "%.1f", status.getDouble("sigma"))), DosColours.YELLOW, bg);
		else
			g.put(3, r++, Dos.tr("df.no_bearing"), DosColours.BODY_FG, bg);
		g.put(3, r++, Dos.tr("df.here", status.getInt("x"), status.getInt("z")), DosColours.BODY_FG, bg);
		r++;
		CompoundTag fixTag = status.getCompound("fix");
		if(fixTag.getBoolean("found")) {
			g.put(3, r++, Dos.tr("df.fix", Math.round(fixTag.getDouble("x")), Math.round(fixTag.getDouble("z"))), DosColours.LIGHT_RED, bg);
			g.put(3, r++, Dos.tr("df.fix_radius", Math.round(fixTag.getDouble("radius"))), DosColours.LIGHT_RED, bg);
		}
		else
			g.put(3, r++, Dos.fit(Dos.tr("df.problem." + fixTag.getString("problem").toLowerCase(Locale.ROOT)), 26), DosColours.BODY_FG, bg);
		r++;
		g.put(3, r++, Dos.tr("df.bearings"), DosColours.YELLOW, bg);
		ListTag list = status.getList("bearings", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size() && r < 22; i++) {
			CompoundTag b = list.getCompound(i);
			String source = b.getString("source");
			int colour = source.equals("OWN") ? DosColours.YELLOW : source.equals("LINKED") ? DosColours.LIGHT_GREEN : DosColours.LIGHT_CYAN;
			g.put(3, r++, Dos.fit(String.format(Locale.ROOT, "%-4s %6d %6d %03d+%.0f", Dos.tr("df.source." + source.toLowerCase(Locale.ROOT)),
					Math.round(b.getDouble("x")), Math.round(b.getDouble("z")), Math.round(b.getDouble("bearing")) % 360, b.getDouble("sigma")), 27), colour, bg);
		}
	}

	private void renderMap(TextGrid g, int bg) {
		String title = mapSearch < 0 ? Dos.tr("df.map_now") : Dos.tr("df.map_search", mapSearch + 1);
		g.box(31, 2, 48, 21, false, title, DosColours.TEXT_FG, bg);
		CompoundTag data = mapData;
		if(data == null || data.getByteArray("pixels").length != 128 * 128) {
			g.put(33, 4, Dos.tr(status != null && !status.getBoolean("finder") && mapSearch < 0 ? "df.no_finder" : "df.map_reading"), DosColours.BODY_FG, bg);
			return;
		}
		String key = data.getInt("cx") + "," + data.getInt("cz") + "," + data.getInt("bpp") + ":" + bearingsKey(data);
		if(plotted == null || !key.equals(plottedFor)) {
			CompoundTag f = data.getCompound("fix");
			plotted = DfDrawing.plot(data.getByteArray("pixels"), 128, 128, data.getInt("cx"), data.getInt("cz"), data.getInt("bpp"), bearings(data),
					f.getBoolean("found"), f.getDouble("x"), f.getDouble("z"), f.getDouble("radius"), fix, edge);
			plottedFor = key;
		}
		if(!hasDialog())
			g.image(32 * TextGrid.CELL_W + 4, 3 * TextGrid.CELL_H + 4, 184, 184, "df:map:" + session.pos().asLong(), plotted, 128, 128);
		int bpp = data.getInt("bpp");
		g.put(66, 4, Dos.tr("df.scale"), DosColours.YELLOW, bg);
		g.put(66, 5, Dos.tr("df.scale_value", bpp * 128), DosColours.BODY_FG, bg);
		g.put(66, 7, Dos.tr("df.legend"), DosColours.YELLOW, bg);
		g.put(66, 8, Dos.tr("df.legend_own"), DosColours.YELLOW, bg);
		g.put(66, 9, Dos.tr("df.legend_typed"), DosColours.LIGHT_CYAN, bg);
		g.put(66, 10, Dos.tr("df.legend_linked"), DosColours.LIGHT_GREEN, bg);
		g.put(66, 11, Dos.tr("df.legend_fix"), DosColours.LIGHT_RED, bg);
		g.put(66, 12, Dos.tr("df.legend_spread"), DosColours.BODY_FG, bg);
		if(mapSearch >= 0)
			g.put(66, 20, Dos.tr("df.map_back"), DosColours.BODY_FG, bg);
	}

	private void renderScene(TextGrid g, int bg, long now) {
		g.box(31, 2, 48, 21, false, Dos.tr("df.tab_3d"), DosColours.TEXT_FG, bg);
		CompoundTag data = status;
		if(data == null || !data.getBoolean("finder") || data.getList("bearings", Tag.TAG_COMPOUND).isEmpty()) {
			g.put(33, 4, Dos.tr("df.scene_empty"), DosColours.BODY_FG, bg);
			return;
		}
		if(!sceneDrawn || now - lastFrame >= FRAME_MS) { // A new frame into the other buffer, so the texture sees a new picture.
			lastFrame = now;
			sceneBuffer ^= 1;
			CompoundTag f = data.getCompound("fix");
			DfScene.render(scene[sceneBuffer], SCENE_W, SCENE_H, (now % 24_000L) / 24_000.0D * 2.0D * Math.PI, bearings(data), f.getBoolean("found"),
					f.getDouble("x"), f.getDouble("z"), f.getDouble("radius"), new DfScene.Palette(black, grid, mast, peak, hill, mast));
			sceneDrawn = true;
		}
		if(!hasDialog())
			g.image(32 * TextGrid.CELL_W, 3 * TextGrid.CELL_H + 6, 46 * TextGrid.CELL_W, 46 * TextGrid.CELL_W * SCENE_H / SCENE_W, "df:3d:" + session.pos().asLong(),
					scene[sceneBuffer], SCENE_W, SCENE_H);
		CompoundTag f = data.getCompound("fix");
		String line = f.getBoolean("found") ? Dos.tr("df.scene_fix", Math.round(f.getDouble("radius"))) : Dos.tr("df.scene_no_fix");
		g.put(33, 21, Dos.fit(line, 44), f.getBoolean("found") ? DosColours.LIGHT_RED : DosColours.BODY_FG, bg);
	}

	private void renderSearches(TextGrid g, int bg) {
		g.box(31, 2, 48, 21, false, Dos.tr("df.tab_searches"), DosColours.TEXT_FG, bg);
		List<String> lines = new ArrayList<>();
		ListTag list = searches == null ? new ListTag() : searches;
		for(int i = 0; i < list.size(); i++) {
			CompoundTag s = list.getCompound(i);
			CompoundTag f = s.getCompound("fix");
			String where = f.getBoolean("found") ? String.format(Locale.ROOT, "%d %d +%d", Math.round(f.getDouble("x")), Math.round(f.getDouble("z")), Math.round(f.getDouble("radius")))
					: Dos.tr("df.no_fix_short");
			lines.add(String.format(Locale.ROOT, "%s %8.3f %d %-16s %s", StationLine.utc(s.getLong("utc")), s.getFloat("frequency") / 1_000_000.0F, s.getInt("count"),
					where, s.getString("note")));
		}
		searchList.setItems(lines);
		g.put(33, 3, Dos.tr("df.searches_head"), DosColours.YELLOW, bg);
		if(lines.isEmpty())
			g.put(33, 5, Dos.tr("df.no_searches"), DosColours.BODY_FG, bg);
		else
			searchList.render(g, 32, 4, 46, 17, DosColours.TEXT_FG, bg, true);
		g.put(33, 21, Dos.tr("df.searches_keys"), DosColours.BODY_FG, bg);
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onEscape() {
		if(page == MAP && mapSearch >= 0) {
			mapSearch = -1;
			refreshMap();
			return true;
		}
		return super.onEscape();
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_TAB -> {
				page = (page + ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 2 : 1)) % 3;
				if(page == SEARCHES)
					refreshSearches();
			}
			case GLFW.GLFW_KEY_F3 -> save();
			case GLFW.GLFW_KEY_F5 -> control("power", new CompoundTag());
			case GLFW.GLFW_KEY_F6 -> addBearing();
			case GLFW.GLFW_KEY_F7 -> typeFrequency();
			case GLFW.GLFW_KEY_F8 -> removeTyped();
			case GLFW.GLFW_KEY_LEFT -> tune((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -10 : -1);
			case GLFW.GLFW_KEY_RIGHT -> tune((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1);
			case GLFW.GLFW_KEY_PAGE_UP -> {
				if(page == SEARCHES)
					return searchList.keyPressed(key);
				band(1);
			}
			case GLFW.GLFW_KEY_PAGE_DOWN -> {
				if(page == SEARCHES)
					return searchList.keyPressed(key);
				band(-1);
			}
			case GLFW.GLFW_KEY_DELETE -> {
				if(page == SEARCHES)
					deleteSearch();
			}
			default -> {
				return page == SEARCHES && searchList.keyPressed(key);
			}
		}
		return true;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(row == 1) {
			int c = 32;
			String[] tabs = { Dos.tr("df.tab_map"), Dos.tr("df.tab_3d"), Dos.tr("df.tab_searches") };
			for(int i = 0; i < tabs.length; i++) {
				if(col >= c && col < c + tabs[i].length() + 2) {
					page = i;
					if(page == SEARCHES)
						refreshSearches();
					return true;
				}
				c += tabs[i].length() + 3;
			}
			return false;
		}
		return page == SEARCHES && searchList.mouseClicked(col, row);
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		return page == SEARCHES && searchList.mouseScrolled(col, row, amount);
	}
}
