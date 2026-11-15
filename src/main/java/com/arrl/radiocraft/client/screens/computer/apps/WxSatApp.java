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
import com.arrl.radiocraft.common.radio.satellite.SatelliteOrbit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * WXSAT (2026-11-12, {@code doc/SatellitePlan.md}, "Weather imaging"): weather satellite pictures from commercial
 * satellites in imaging mode, received through a radio linked to the computer. Three views, each with the station and
 * pass panel on the right:
 * <ul>
 * <li><b>Live</b> (F5): the strip coming in line by line, the newest at the bottom, lost lines black, with the
 * satellite, its elevation and signal, and the next imaging passes.</li>
 * <li><b>Mosaic</b> (F6): the passes stitched onto the map round the station, newest on top; the arrows pan, F8 (or + and
 * -) zooms out and in, Home comes back to the station (marked with a cross).</li>
 * <li><b>Loop</b> (F7): the same view pass by pass, oldest to newest, a second a frame (Space pauses, PgUp and PgDn step),
 * to watch storms move.</li>
 * </ul>
 * F3 saves the view (128 x 128, as {@code WX0001.IMG}...) and F4 prints it through the scanner (plain paper prints the
 * area, the time and the weather as text). The menus (F10) do the same. The computer keeps receiving with the program
 * shut: it is a background service. Esc (File, Close) goes back to the computer's menu; X (File, Exit) leaves the
 * computer with WXSAT up on its screen (2026-11-14, as SATTRACK).
 * <p>
 * The map (2026-11-14, the maintainer): the wheel zooms about the mouse, dragging pans it, and the station is a red ring
 * (an arrow at the edge when it is off the view). The menus are the framework's, so pictures stay out from under them.
 */
public final class WxSatApp extends DosApp {

	private enum View { LIVE, MOSAIC, LOOP }

	private static final long STATUS_MS = 1000L, LIVE_MS = 400L, LOOP_FRAME_MS = 1000L;
	/** Where the picture goes on the screen, pixels, and the mosaic's size. */
	private static final int PIC_X = 13, PIC_Y = 22, VIEW_W = 256, VIEW_H = 184;
	private static final int[] ZOOMS = com.arrl.radiocraft.common.computer.apps.WxSatHandler.ZOOMS;
	private static final int LIVE_W = com.arrl.radiocraft.common.computer.apps.WxSatHandler.LIVE_W;
	private static final byte RING = MapColor.COLOR_RED.getPackedId(MapColor.Brightness.HIGH), DOT = MapColor.SNOW.getPackedId(MapColor.Brightness.HIGH),
			EDGE = MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.LOWEST);

	private View view = View.LIVE;
	/** Left up by Exit (X): the computer's screen shut with WXSAT on it, kept however long. */
	private boolean staysUp = false;
	/** A drag on the map: where it started (grid pixels) and the centre then; NaN when none. */
	private double dragX = Double.NaN, dragY;
	private int dragCentreX, dragCentreZ;
	@Nullable
	private CompoundTag status;
	private long lastStatus, lastLive, lastView, loopStepAt;
	private long liveVersion = 0L;
	private byte[] live = new byte[0];
	private int liveRows = 0;
	// The mosaic: where it looks and the picture of each frame, kept until the mosaic or the view changes.
	private boolean centred = false;
	private int centreX, centreZ, zoom = 0;
	/** Each frame's picture and the mosaic version it was painted from (kept on screen until a newer one comes). */
	private final Map<Integer, byte[]> frames = new HashMap<>();
	private final Map<Integer, Long> frameVersions = new HashMap<>();
	private ListTag frameList = new ListTag();
	private int loopFrame = 0;
	private boolean playing = true, viewPending = false;
	private int saved = 1;

	public WxSatApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		setMenuBar(menuBar());
	}

	/** The menu bar; rebuilt when "Block LAN sync" changes, so its tick shows (2026-11-15). */
	private DosMenuBar menuBar() {
		boolean blocked = status != null && status.getBoolean("lan_block");
		return new DosMenuBar(List.of(
				new DosMenuBar.Menu(Dos.tr("wx.menu.file"), List.of(
						new DosMenuBar.Item(Dos.tr("wx.menu.save"), "F3", this::save),
						new DosMenuBar.Item(Dos.tr("wx.menu.print"), "F4", this::print),
						new DosMenuBar.Item(Dos.tr("wx.menu.clear"), "", this::clear),
						item("menu.wxsat.close", "Esc", GLFW.GLFW_KEY_ESCAPE),
						item("menu.wxsat.exit", "X", GLFW.GLFW_KEY_X))),
				new DosMenuBar.Menu(Dos.tr("wx.menu.view"), List.of(
						new DosMenuBar.Item(Dos.tr("wx.menu.live"), "F5", () -> show(View.LIVE)),
						new DosMenuBar.Item(Dos.tr("wx.menu.mosaic"), "F6", () -> show(View.MOSAIC)),
						new DosMenuBar.Item(Dos.tr("wx.menu.loop"), "F7", () -> show(View.LOOP)),
						new DosMenuBar.Item(Dos.tr("wx.menu.zoom_out"), "F8", () -> zoom(1)),
						new DosMenuBar.Item(Dos.tr("wx.menu.zoom_in"), "+", () -> zoom(-1)),
						new DosMenuBar.Item(Dos.tr("wx.menu.home"), "Home", this::home))),
				new DosMenuBar.Menu(Dos.tr("wx.menu.options"), List.of(
						new DosMenuBar.Item((blocked ? "[x] " : "[ ] ") + Dos.tr("wx.menu.lan_block"), "L", this::toggleLanBlock)))));
	}

	/** "Block LAN sync": this computer stops sharing its pictures with WXSAT computers on its LAN and taking theirs. */
	private void toggleLanBlock() {
		CompoundTag data = new CompoundTag();
		data.putBoolean("on", status == null || !status.getBoolean("lan_block"));
		session.app(app, "lan_block", data, response -> {
			if(response.ok())
				session.message(Dos.tr(data.getBoolean("on") ? "wx.lan_blocked" : "wx.lan_unblocked"), false);
			refresh();
		});
	}

	@Override
	public boolean keepsRunning() {
		return staysUp;
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
		if(hasDialog())
			return;
		if(now - lastStatus > STATUS_MS)
			refresh();
		if(view == View.LIVE && now - lastLive > LIVE_MS)
			fetchLive();
		if(view != View.LIVE && stale(shownFrame()) && !viewPending && now - lastView > 300L)
			fetchView(shownFrame());
		if(view == View.LOOP && playing && now - loopStepAt > LOOP_FRAME_MS && frameList.size() > 0) {
			loopStepAt = now;
			loopFrame = (loopFrame + 1) % frameList.size();
		}
	}

	/** True if frame {@code which} has no picture yet, or one older than the mosaic. */
	private boolean stale(int which) {
		Long painted = frameVersions.get(which);
		return painted == null || status != null && status.getLong("v") != painted;
	}

	/** The frame the view shows: the latest in the mosaic view, the loop's in the loop. */
	private int shownFrame() {
		return view == View.LOOP && frameList.size() > 0 ? Math.min(loopFrame, frameList.size() - 1) : -1;
	}

	private void refresh() {
		lastStatus = System.currentTimeMillis();
		session.app(app, "status", new CompoundTag(), response -> {
			if(!response.ok())
				return;
			boolean wasBlocked = status != null && status.getBoolean("lan_block");
			status = response.data();
			if(wasBlocked != status.getBoolean("lan_block"))
				setMenuBar(menuBar());
			if(!centred) {
				centreX = status.getInt("sx");
				centreZ = status.getInt("sz");
				centred = true;
			}
		});
	}

	private void fetchLive() {
		lastLive = System.currentTimeMillis();
		CompoundTag data = new CompoundTag();
		data.putLong("have", liveVersion);
		session.app(app, "live", data, response -> {
			if(!response.ok() || response.data().getBoolean("same"))
				return;
			liveVersion = response.data().getLong("lv");
			live = response.data().getByteArray("pixels");
			liveRows = response.data().getInt("rows");
		});
	}

	private CompoundTag viewData(int which) {
		CompoundTag data = new CompoundTag();
		data.putInt("x", centreX);
		data.putInt("z", centreZ);
		data.putInt("zoom", ZOOMS[zoom]);
		data.putInt("frame", which);
		data.putInt("w", VIEW_W);
		data.putInt("h", VIEW_H);
		return data;
	}

	private void fetchView(int which) {
		lastView = System.currentTimeMillis();
		viewPending = true;
		int askedX = centreX, askedZ = centreZ, askedZoom = zoom;
		session.app(app, "view", viewData(which), response -> {
			viewPending = false;
			if(!response.ok() || askedX != centreX || askedZ != centreZ || askedZoom != zoom)
				return; // Moved meanwhile: the next tick asks again.
			CompoundTag d = response.data();
			frameList = d.getList("frames", Tag.TAG_COMPOUND);
			frames.put(which, marked(d.getByteArray("pixels")));
			frameVersions.put(which, d.getLong("v"));
		});
	}

	/**
	 * The picture with the station on it: a red ring round a white dot, edged in black so it shows on any ground and on the
	 * black where nothing was seen; off the view, a red arrow at the edge pointing to it.
	 */
	private byte[] marked(byte[] pixels) {
		if(status == null || pixels.length != VIEW_W * VIEW_H)
			return pixels;
		int bpp = ZOOMS[zoom];
		double fu = (status.getInt("sx") - centreX) / (double)bpp + VIEW_W / 2.0D, fv = (status.getInt("sz") - centreZ) / (double)bpp + VIEW_H / 2.0D;
		int u = (int)Math.floor(fu), v = (int)Math.floor(fv);
		if(u >= 0 && u < VIEW_W && v >= 0 && v < VIEW_H) {
			for(int dv = -7; dv <= 7; dv++)
				for(int du = -7; du <= 7; du++) {
					double d = Math.sqrt(du * du + dv * dv);
					if(d >= 4.0D && d < 6.0D)
						put(pixels, u + du, v + dv, RING);
					else if((d >= 3.0D && d < 4.0D) || (d >= 6.0D && d < 7.0D))
						put(pixels, u + du, v + dv, EDGE);
				}
			for(int dv = -1; dv <= 1; dv++)
				for(int du = -1; du <= 1; du++)
					put(pixels, u + du, v + dv, du == 0 && dv == 0 ? DOT : EDGE);
			return pixels;
		}
		// Off the view: an arrow just inside the edge, on the line from the middle to the station.
		double cx = VIEW_W / 2.0D, cy = VIEW_H / 2.0D, dx = fu - cx, dy = fv - cy;
		double scale = Math.min((cx - 8.0D) / Math.max(1.0E-6D, Math.abs(dx)), (cy - 8.0D) / Math.max(1.0E-6D, Math.abs(dy)));
		double ax = cx + dx * scale, ay = cy + dy * scale, len = Math.max(1.0E-6D, Math.hypot(dx, dy));
		double ux = dx / len, uy = dy / len;
		for(int t = -6; t <= 6; t++)
			for(int w = -4; w <= 4; w++) {
				double half = (6 - t) * 4 / 12.0D; // A triangle pointing along (ux, uy), narrowing to its tip.
				if(Math.abs(w) > half + 0.5D)
					continue;
				int px = (int)Math.round(ax + ux * t - uy * w), py = (int)Math.round(ay + uy * t + ux * w);
				put(pixels, px, py, Math.abs(w) > half - 1.0D ? EDGE : RING);
			}
		return pixels;
	}

	private static void put(byte[] pixels, int u, int v, byte colour) {
		if(u >= 0 && u < VIEW_W && v >= 0 && v < VIEW_H)
			pixels[u + v * VIEW_W] = colour;
	}

	/** The view moved or zoomed: every frame is painted again. */
	private void moved() {
		frames.clear();
		frameVersions.clear();
	}

	private void show(View next) {
		view = next;
		if(next == View.LOOP) {
			loopFrame = 0;
			playing = true;
			loopStepAt = System.currentTimeMillis();
		}
	}

	private void zoom(int step) {
		zoom = Math.max(0, Math.min(ZOOMS.length - 1, zoom + step));
		moved();
		if(view == View.LIVE)
			view = View.MOSAIC;
	}

	/** Zooms by {@code step} keeping the ground under picture pixel {@code u, v} where it is (the wheel). */
	private void zoomAbout(int step, double u, double v) {
		int before = ZOOMS[zoom], next = Math.max(0, Math.min(ZOOMS.length - 1, zoom + step));
		if(next == zoom)
			return;
		double bx = centreX + (u - VIEW_W / 2.0D) * before, bz = centreZ + (v - VIEW_H / 2.0D) * before;
		zoom = next;
		centreX = (int)Math.round(bx - (u - VIEW_W / 2.0D) * ZOOMS[zoom]);
		centreZ = (int)Math.round(bz - (v - VIEW_H / 2.0D) * ZOOMS[zoom]);
		moved();
	}

	private void home() {
		if(status != null) {
			centreX = status.getInt("sx");
			centreZ = status.getInt("sz");
			moved();
		}
	}

	private void pan(int dx, int dz) {
		if(view == View.LIVE)
			return;
		int step = ZOOMS[zoom] * VIEW_W / 4;
		centreX += dx * step;
		centreZ += dz * step;
		moved();
	}

	private void save() {
		showDialog(DosDialog.ask(Dos.tr("wx.save_title"), Dos.tr("wx.save_prompt"), new DosInput(String.format(Locale.ROOT, "WX%04d.IMG", saved), 12,
				c -> Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-'), name -> {
			CompoundTag data = viewData(shownFrame());
			data.putString("name", name.toUpperCase(Locale.ROOT).endsWith(".IMG") ? name : name + ".IMG");
			session.app(app, "save", data, response -> {
				if(response.ok()) {
					saved++;
					session.message(Dos.tr("wx.saved", data.getString("name")), false);
				}
				else
					showError(response);
			});
		}));
	}

	private void print() {
		session.app(app, "print", viewData(shownFrame()), response -> {
			if(response.ok())
				session.message(Dos.tr("wx.printed"), false);
			else
				showError(response);
		});
	}

	private void clear() {
		showDialog(DosDialog.confirm(Dos.tr("wx.clear_title"), Dos.tr("wx.clear_prompt"), () -> session.app(app, "clear", new CompoundTag(), response -> {
			moved();
			refresh();
		})));
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_F5 -> show(View.LIVE);
			case GLFW.GLFW_KEY_F6 -> show(View.MOSAIC);
			case GLFW.GLFW_KEY_F7 -> show(View.LOOP);
			case GLFW.GLFW_KEY_L -> toggleLanBlock();
			case GLFW.GLFW_KEY_F8 -> zoom(zoom == ZOOMS.length - 1 ? -ZOOMS.length : 1);
			case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> zoom(-1);
			case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> zoom(1);
			case GLFW.GLFW_KEY_F3 -> save();
			case GLFW.GLFW_KEY_F4 -> print();
			case GLFW.GLFW_KEY_HOME -> home();
			case GLFW.GLFW_KEY_LEFT -> pan(-1, 0);
			case GLFW.GLFW_KEY_RIGHT -> pan(1, 0);
			case GLFW.GLFW_KEY_UP -> pan(0, -1);
			case GLFW.GLFW_KEY_DOWN -> pan(0, 1);
			case GLFW.GLFW_KEY_SPACE -> playing = !playing;
			case GLFW.GLFW_KEY_X -> { // Exit: leave the computer with WXSAT still up (Esc, Close, quits the program).
				staysUp = true;
				net.minecraft.client.gui.screens.Screen screen = net.minecraft.client.Minecraft.getInstance().screen;
				if(screen != null)
					screen.onClose();
			}
			case GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN -> {
				if(view == View.LOOP && frameList.size() > 0) {
					playing = false;
					loopFrame = Math.floorMod(loopFrame + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1), frameList.size());
				}
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** True if grid pixel {@code x, y} is on the map. */
	private boolean onMap(double x, double y) {
		return view != View.LIVE && x >= PIC_X && x < PIC_X + VIEW_W && y >= PIC_Y && y < PIC_Y + VIEW_H;
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		double x = (col + 0.5D) * TextGrid.CELL_W, y = (row + 0.5D) * TextGrid.CELL_H;
		if(view == View.LIVE) {
			zoom(amount > 0 ? -1 : 1); // From the live strip, the wheel opens the map.
			return true;
		}
		if(!onMap(x, y))
			return false;
		zoomAbout(amount > 0 ? -1 : 1, x - PIC_X, y - PIC_Y); // Up zooms in.
		return true;
	}

	@Override
	protected boolean onDrag(double x, double y, int button) {
		if(button != GLFW.GLFW_MOUSE_BUTTON_LEFT)
			return false;
		if(Double.isNaN(dragX)) {
			if(!onMap(x, y))
				return false;
			dragX = x;
			dragY = y;
			dragCentreX = centreX;
			dragCentreZ = centreZ;
			return true;
		}
		int bpp = ZOOMS[zoom];
		int nx = dragCentreX - (int)Math.round((x - dragX) * bpp), nz = dragCentreZ - (int)Math.round((y - dragY) * bpp);
		if(nx != centreX || nz != centreZ) {
			centreX = nx;
			centreZ = nz;
			moved();
		}
		return true;
	}

	@Override
	protected boolean onRelease(double x, double y, int button) {
		boolean was = !Double.isNaN(dragX);
		dragX = Double.NaN;
		return was;
	}

	/** Ticks as minutes and seconds, {@code 3:05}. */
	private static String clock(long ticks) {
		long seconds = Math.max(0L, ticks) / 20L;
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, "", Dos.tr("wx.keys"));
		g.put(titleCol(), 0, Dos.tr("wx.title"), DosColours.TITLE_FG, DosColours.TITLE_BG);
		int bg = DosColours.BODY_BG;
		String title = switch(view) {
			case LIVE -> Dos.tr("wx.live");
			case MOSAIC -> Dos.tr("wx.mosaic", ZOOMS[zoom]);
			case LOOP -> Dos.tr("wx.loop", ZOOMS[zoom]);
		};
		g.box(1, 1, 45, 22, false, title, DosColours.TEXT_FG, bg);
		g.fill(2, 2, 43, 20, ' ', DosColours.BODY_FG, DosColours.BLACK);
		CompoundTag st = status;
		if(st == null) {
			g.put(3, 3, Dos.tr("wx.reading"), DosColours.BODY_FG, DosColours.BLACK);
			return;
		}
		switch(view) {
			case LIVE -> renderLive(g);
			case MOSAIC, LOOP -> renderMosaic(g, now);
		}
		panel(g, st, bg, now);
	}

	private void renderLive(TextGrid g) {
		if(liveRows <= 0 || live.length < liveRows * LIVE_W) {
			g.put(4, 6, Dos.fit(Dos.tr("wx.live_none1"), 40), DosColours.DIM, DosColours.BLACK);
			g.put(4, 7, Dos.fit(Dos.tr("wx.live_none2"), 40), DosColours.DIM, DosColours.BLACK);
			return;
		}
		g.image(PIC_X, PIC_Y + (176 - liveRows), LIVE_W, liveRows, "wxsat_live", live, LIVE_W, liveRows);
	}

	private void renderMosaic(TextGrid g, long now) {
		byte[] pixels = frames.get(shownFrame());
		if(pixels == null) {
			pixels = frames.get(-1); // While a loop frame loads, the latest.
			if(pixels == null) {
				g.put(4, 6, Dos.fit(Dos.tr("wx.loading"), 40), DosColours.DIM, DosColours.BLACK);
				return;
			}
		}
		if(frameList.isEmpty()) {
			g.put(4, 6, Dos.fit(Dos.tr("wx.mosaic_none1"), 40), DosColours.DIM, DosColours.BLACK);
			g.put(4, 7, Dos.fit(Dos.tr("wx.mosaic_none2"), 40), DosColours.DIM, DosColours.BLACK);
			return;
		}
		g.image(PIC_X, PIC_Y, VIEW_W, VIEW_H, "wxsat_view_" + shownFrame(), pixels, VIEW_W, VIEW_H);
		int which = view == View.LOOP ? shownFrame() : frameList.size() - 1;
		if(which >= 0 && which < frameList.size()) {
			CompoundTag f = frameList.getCompound(which);
			String label = Dos.tr("wx.frame", which + 1, frameList.size(), f.getString("sat"), f.getString("time"));
			if(view == View.LOOP && !playing)
				label += "  " + Dos.tr("wx.paused");
			g.putField(2, 21, Dos.fit(label, 43), 43, DosColours.YELLOW, DosColours.BLACK);
		}
		g.put(2, 2, Dos.fit(String.format(Locale.ROOT, "X%d Z%d", centreX, centreZ), 20), DosColours.LIGHT_CYAN, DosColours.BLACK);
		g.put(23, 2, Dos.fit(Dos.tr("wx.you_are"), 22), DosColours.LIGHT_RED, DosColours.BLACK); // The ring's legend.
	}

	private void panel(TextGrid g, CompoundTag st, int bg, long now) {
		int c = 48, w = 30, r = 2;
		g.box(47, 1, 32, 22, false, Dos.tr("wx.station"), DosColours.TEXT_FG, bg);
		boolean blink = (now / 500L) % 2L == 0L;
		if(!st.getBoolean("overworld"))
			g.put(c, r++, Dos.fit(Dos.tr("wx.not_overworld"), w), DosColours.YELLOW, bg);
		g.put(c, r++, Dos.fit(st.getBoolean("radio") ? Dos.tr("wx.radio", st.getString("radio_mhz")) : Dos.tr("wx.no_radio"), w),
				st.getBoolean("radio") ? DosColours.TEXT_FG : DosColours.LIGHT_RED, bg);
		r++;
		if(st.getBoolean("receiving")) {
			g.put(c, r++, Dos.fit(Dos.tr("wx.receiving", st.getString("sat")), w), blink ? DosColours.LIGHT_GREEN : DosColours.GREEN, bg);
			double q = st.getDouble("quality");
			g.put(c, r, Dos.tr("wx.signal"), DosColours.BODY_FG, bg);
			g.bar(c + 8, r++, w - 8, Math.min(1.0D, q / 2.0D), q >= 1.5D ? DosColours.LIGHT_GREEN : q >= 1.0D ? DosColours.YELLOW : DosColours.LIGHT_RED, DosColours.BLACK);
		}
		else if(!st.getString("sat").isEmpty())
			g.put(c, r++, Dos.fit(Dos.tr("wx.last_pass", st.getString("sat"), clock(st.getLong("ago"))), w), DosColours.BODY_FG, bg);
		else
			g.put(c, r++, Dos.fit(Dos.tr("wx.waiting"), w), DosColours.DIM, bg);
		String why = st.getString("why");
		if(!why.isEmpty() && !why.equals("no_pass")) // Up now, but nothing coming in: say why.
		{
			g.put(c, r++, Dos.fit(Dos.tr("wx.why." + why, st.getString("why_sat"), st.getString("why_down"), st.getString("radio_mhz")), w), DosColours.LIGHT_RED, bg);
			if(why.equals("wrong_freq") || why.equals("unheard"))
				g.put(c, r++, Dos.fit(Dos.tr("wx.why." + why + "2", st.getString("why_sat"), st.getString("why_down"), st.getString("radio_mhz")), w), DosColours.LIGHT_RED, bg);
		}
		if(st.contains("frame")) // A geostationary imager's frame (2026-11-14).
			g.put(c, r++, Dos.fit(st.getBoolean("frame_done") ? Dos.tr("wx.frame_done", st.getInt("frame"))
					: Dos.tr("wx.frame_row", st.getInt("frame"), st.getInt("row"), st.getInt("rows")), w), DosColours.LIGHT_CYAN, bg);
		else if(st.getInt("lines") > 0)
			g.put(c, r++, Dos.fit(Dos.tr("wx.lines", st.getInt("lines"), st.getInt("lost")), w), DosColours.TEXT_FG, bg);
		g.put(c, r++, Dos.fit(Dos.tr("wx.stored", st.getInt("passes"), st.getInt("tiles")), w), DosColours.TEXT_FG, bg);
		if(st.getBoolean("lan_block"))
			g.put(c, r++, Dos.fit(Dos.tr("wx.lan_off"), w), DosColours.DIM, bg);
		else if(st.getInt("lan") > 0)
			g.put(c, r++, Dos.fit(Dos.tr("wx.lan_on", st.getInt("lan")), w), DosColours.LIGHT_CYAN, bg);
		r++;
		g.put(c, r++, Dos.fit(Dos.tr("wx.imagers"), w), DosColours.YELLOW, bg);
		ListTag imagers = st.getList("imagers", Tag.TAG_COMPOUND);
		if(imagers.isEmpty())
			g.put(c, r++, Dos.fit(Dos.tr("wx.no_imagers"), w), DosColours.DIM, bg);
		for(int i = 0; i < imagers.size() && r < 19; i++) {
			CompoundTag s = imagers.getCompound(i);
			g.put(c, r++, Dos.fit(s.getString("name") + "  " + s.getString("down") + " MHz" + (s.getBoolean("on") ? "" : "  OFF"), w), DosColours.LIGHT_RED, bg);
			String when;
			if(s.getBoolean("passing")) {
				when = Dos.tr("wx.up", String.format(Locale.ROOT, "%.0f", s.getDouble("el")), SatelliteOrbit.compass(s.getDouble("az")));
				if(s.contains("shift"))
					when += " " + String.format(Locale.ROOT, "%+.1fkHz", s.getDouble("shift") / 1000.0D);
			}
			else
				when = s.contains("in") ? Dos.tr("wx.next", clock(s.getLong("in")), String.format(Locale.ROOT, "%.0f", s.getDouble("max"))) : "";
			g.put(c + 1, r++, Dos.fit(when, w - 1), s.getBoolean("passing") ? DosColours.LIGHT_CYAN : DosColours.BODY_FG, bg);
		}
		g.put(c, 20, Dos.fit(Dos.tr("wx.monitor_hint"), w), DosColours.DIM, bg); // Where else the mosaic shows (2026-11-14).
		g.put(c, 21, Dos.fit(view == View.LIVE ? Dos.tr("wx.hint_live") : view == View.LOOP ? Dos.tr("wx.hint_loop") : Dos.tr("wx.hint_mosaic"), w), DosColours.DIM, bg);
	}
}
