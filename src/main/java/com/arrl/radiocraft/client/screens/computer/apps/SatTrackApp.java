package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.PixelCanvas;
import com.arrl.radiocraft.common.radio.satellite.SatelliteOrbit;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * SATTRACK (2026-11-11, {@code doc/SatellitePlan.md}): every satellite on the left (green amateur, red commercial,
 * with whether it is up now or how long until it rises); the picked one on the right with its frequencies, tone, ID,
 * owner and allowed list, its decay date (yellow in its last week) and its next passes over the computer: how long
 * until it rises, how high it gets, where it rises and how long it stays up. While one of yours passes over, F7 turns it
 * on or off, F3 sets its ID text, F4 its tone, F5 and F6 add and take players off its allowed list.
 * <p>
 * 2026-11-12: its three frequencies, its mode (an amateur one's: F8 steps through repeater, APRS, mailbox and SSTV; F9
 * sets the APRS frequency, F10 uploads an .IMG for SSTV), the Doppler shift while it passes (when the server has it on),
 * the latest telemetry a radio on the computer's station heard, a warning while it is eclipsed on a flat battery or its
 * battery is low after one, and for its owner a warning when its orbit is about to decay. A commercial satellite's F8
 * switches it between repeater and weather imaging (the WXSAT program receives its pictures).
 * <p>
 * Also 2026-11-12: a geostationary satellite is marked GEO and is always up here or never (no passes); DEL lets its
 * owner decommission one while it passes, after a Yes/No question and a final warning whose default is No. A
 * decommissioned one is listed DEAD until its orbit decays.
 * <p>
 * 2026-11-13: three views of the picked one (View menu, Tab to step, or D, I, L): Details (all of the above), Image (a
 * picture of it, the kind picked at random when it was launched: {@link SatelliteArt}) and Location (it going round
 * the square world, the slice in view from here lighter: {@link SatelliteOrbitView}). File: Close (Esc) quits the
 * program; Exit (X) shuts the computer's screen with the tracker left up.
 */
public final class SatTrackApp extends DosApp {

	private static final long STATUS_MS = 1000L;

	@Nullable
	private CompoundTag status;
	private long lastStatus;
	private int selected = 0;
	/** When {@link #status} came, ms: the Location view moves the satellite on from it between answers. */
	private long statusAt;

	private enum View { DETAILS, IMAGE, LOCATION }

	private View view = View.DETAILS;
	/** Left up by Exit (X, 2026-11-13): the computer's screen shut, the tracker kept on it however long. */
	private boolean staysUp = false;

	@Override
	public boolean keepsRunning() {
		return staysUp;
	}

	public SatTrackApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		setMenuBar(new com.arrl.radiocraft.client.screens.computer.DosMenuBar(java.util.List.of( // 2026-11-12: each item does what its key does.
				new com.arrl.radiocraft.client.screens.computer.DosMenuBar.Menu(Dos.tr("menu.file"), java.util.List.of(
						item("menu.sattrack.close", "Esc", GLFW.GLFW_KEY_ESCAPE),
						item("menu.sattrack.exit", "X", GLFW.GLFW_KEY_X))),
				new com.arrl.radiocraft.client.screens.computer.DosMenuBar.Menu(Dos.tr("menu.view"), java.util.List.of(
						item("menu.previous", "Up", GLFW.GLFW_KEY_UP),
						item("menu.next", "Down", GLFW.GLFW_KEY_DOWN),
						item("menu.sattrack.details", "D", GLFW.GLFW_KEY_D),
						item("menu.sattrack.image", "I", GLFW.GLFW_KEY_I),
						item("menu.sattrack.location", "L", GLFW.GLFW_KEY_L),
						item("menu.sattrack.next_view", "Tab", GLFW.GLFW_KEY_TAB))),
				new com.arrl.radiocraft.client.screens.computer.DosMenuBar.Menu(Dos.tr("menu.sattrack.satellite"), java.util.List.of(
						item("menu.sattrack.track", "T", GLFW.GLFW_KEY_T).when(() -> picked() != null && status != null && status.getBoolean("trackers")),
						item("menu.sattrack.on_off", "F7", GLFW.GLFW_KEY_F7).when(this::mine),
						item("menu.sattrack.id", "F3", GLFW.GLFW_KEY_F3).when(this::mine),
						item("menu.sattrack.tone", "F4", GLFW.GLFW_KEY_F4).when(this::mine),
						item("menu.sattrack.allow", "F5", GLFW.GLFW_KEY_F5).when(this::mine),
						item("menu.sattrack.disallow", "F6", GLFW.GLFW_KEY_F6).when(this::mine),
						item("menu.sattrack.mode", "F8", GLFW.GLFW_KEY_F8).when(this::mine),
						item("menu.sattrack.aprs", "F9", GLFW.GLFW_KEY_F9).when(() -> mine() && amateur()),
						item("menu.sattrack.picture", "F10", GLFW.GLFW_KEY_F10).when(() -> mine() && amateur()),
						item("menu.sattrack.stealth", "S", GLFW.GLFW_KEY_S).when(() -> mine() && !amateur()),
						item("menu.sattrack.decommission", "Del", GLFW.GLFW_KEY_DELETE).when(() -> {
							CompoundTag s = picked();
							return s != null && s.getBoolean("owner_is_me") && !s.getBoolean("dead");
						}))))).withoutF10()); // F10 sets the SSTV picture here.
	}

	/** True if the picked satellite is one this player may control, still working. */
	private boolean mine() {
		CompoundTag s = picked();
		return s != null && s.getBoolean("mine") && !s.getBoolean("dead");
	}

	private boolean amateur() {
		CompoundTag s = picked();
		return s != null && s.getBoolean("amateur");
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
			if(response.ok()) {
				status = response.data();
				statusAt = System.currentTimeMillis();
			}
		});
	}

	private ListTag satellites() {
		return status == null ? new ListTag() : status.getList("satellites", Tag.TAG_COMPOUND);
	}

	@Nullable
	private CompoundTag picked() {
		ListTag list = satellites();
		return list.isEmpty() ? null : list.getCompound(Math.max(0, Math.min(selected, list.size() - 1)));
	}

	/** The list's rows on the screen: the first satellite shown is drawn on {@link #LIST_TOP}, at most this many. */
	private static final int LIST_TOP = 3, LIST_ROWS = 19, LIST_LEFT = 2, LIST_RIGHT = 27;

	/** The first satellite the list shows, as {@link #render} scrolls it to keep the picked one in view. */
	private int firstShown() {
		return Math.max(0, selected - (LIST_ROWS - 1));
	}

	/** A click on a satellite in the list picks it (2026-11-13). */
	@Override
	protected boolean onClick(int col, int row, int button) {
		if(button != 0 || col < LIST_LEFT || col > LIST_RIGHT || row < LIST_TOP || row >= LIST_TOP + LIST_ROWS)
			return false;
		int index = firstShown() + row - LIST_TOP;
		if(index >= satellites().size())
			return false;
		selected = index;
		return true;
	}

	/** The wheel over the list steps through it. */
	@Override
	protected boolean onScroll(int col, int row, double amount) {
		if(col < LIST_LEFT || col > LIST_RIGHT || row < LIST_TOP || row >= LIST_TOP + LIST_ROWS || amount == 0.0D)
			return false;
		selected = Math.max(0, Math.min(Math.max(0, satellites().size() - 1), selected + (amount > 0.0D ? -1 : 1)));
		return true;
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		CompoundTag s = picked();
		switch(key) {
			case GLFW.GLFW_KEY_UP -> selected = Math.max(0, selected - 1);
			case GLFW.GLFW_KEY_DOWN -> selected = Math.min(Math.max(0, satellites().size() - 1), selected + 1);
			case GLFW.GLFW_KEY_F7, GLFW.GLFW_KEY_F2 -> { // F7: Minecraft keeps F2 for screenshots (2026-11-11).
				if(s != null) {
					CompoundTag data = field(s, "on");
					data.putBoolean("on", !s.getBoolean("on"));
					send(data);
				}
			}
			case GLFW.GLFW_KEY_F3 -> {
				if(s != null)
					showDialog(DosDialog.ask(Dos.tr("sat.id_title"), Dos.tr("sat.id_prompt"), new DosInput(s.getString("id"), 12,
							c -> Character.isLetterOrDigit(c) || c == ' ' || c == '/' || c == '-'), text -> {
						CompoundTag data = field(s, "id");
						data.putString("text", text);
						send(data);
					}));
			}
			case GLFW.GLFW_KEY_F4 -> {
				if(s != null)
					showDialog(DosDialog.ask(Dos.tr("sat.tone_title"), Dos.tr("sat.tone_prompt"), new DosInput(s.getString("tone"), 5,
							c -> Character.isDigit(c) || c == '.'), text -> {
						try {
							CompoundTag data = field(s, "tone");
							data.putFloat("tone", Float.parseFloat(text));
							send(data);
						}
						catch(NumberFormatException e) {
							session.message(Dos.tr("sat.tone_bad"), true);
						}
					}));
			}
			case GLFW.GLFW_KEY_F5, GLFW.GLFW_KEY_F6 -> {
				if(s != null) {
					String field = key == GLFW.GLFW_KEY_F5 ? "allow" : "disallow";
					showDialog(DosDialog.ask(Dos.tr("sat." + field + "_title"), Dos.tr("sat.player_prompt"), new DosInput("", 16,
							c -> Character.isLetterOrDigit(c) || c == '_'), name -> {
						CompoundTag data = field(s, field);
						data.putString("player", name);
						send(data);
					}));
				}
			}
			case GLFW.GLFW_KEY_F8 -> {
				if(s != null) {
					com.arrl.radiocraft.common.radio.satellite.SatelliteMode mode = com.arrl.radiocraft.common.radio.satellite.SatelliteMode.parse(s.getString("mode"));
					CompoundTag data = field(s, "mode");
					data.putString("mode", (mode == null ? com.arrl.radiocraft.common.radio.satellite.SatelliteMode.REPEATER : mode).next(s.getBoolean("amateur")).name());
					send(data);
				}
			}
			case GLFW.GLFW_KEY_F9 -> {
				if(s != null)
					showDialog(DosDialog.ask(Dos.tr("sat.aprs_title"), Dos.tr("sat.aprs_prompt"), new DosInput(s.getString("aprs"), 7,
							c -> Character.isDigit(c) || c == '.'), text -> {
						try {
							CompoundTag data = field(s, "aprs");
							data.putDouble("mhz", text.isBlank() ? 0.0D : Double.parseDouble(text));
							send(data);
						}
						catch(NumberFormatException e) {
							session.message(Dos.tr("sat.aprs_bad"), true);
						}
					}));
			}
			case GLFW.GLFW_KEY_D -> view = View.DETAILS;
			case GLFW.GLFW_KEY_I -> view = View.IMAGE;
			case GLFW.GLFW_KEY_L -> view = View.LOCATION;
			case GLFW.GLFW_KEY_TAB -> view = View.values()[(view.ordinal() + 1) % View.values().length];
			case GLFW.GLFW_KEY_X -> { // Exit: leave the computer with the tracker still up (Esc, Close, quits the program).
				staysUp = true;
				net.minecraft.client.gui.screens.Screen screen = net.minecraft.client.Minecraft.getInstance().screen;
				if(screen != null)
					screen.onClose();
				return true;
			}
			case GLFW.GLFW_KEY_T -> { // Track (2026-11-12): the station's tracking antennas follow it; again for automatic.
				if(s != null && (s.getBoolean("moon") || !s.getBoolean("dead"))) {
					CompoundTag data = new CompoundTag();
					data.putString("name", s.getBoolean("chosen") ? "" : s.getString("name"));
					session.app(app, "track", data, response -> {
						if(response.ok())
							refresh();
						else
							showError(response);
					});
				}
			}
			case GLFW.GLFW_KEY_S -> { // Stealth on/off (2026-11-12): commercial; on only through a tracker locked on it.
				if(s != null && s.getBoolean("mine") && !s.getBoolean("amateur") && !s.getBoolean("dead")) {
					CompoundTag data = field(s, "stealth");
					data.putBoolean("on", !s.getBoolean("stealth"));
					send(data);
				}
			}
			case GLFW.GLFW_KEY_DELETE -> { // Decommission (2026-11-12): its owner alone, asked twice, the second time finally.
				if(s != null && s.getBoolean("owner_is_me") && !s.getBoolean("dead")) {
					String name = s.getString("name");
					showDialog(DosDialog.confirm(Dos.tr("sat.decom_title"), Dos.tr("sat.decom_ask", name),
							() -> showDialog(DosDialog.choice(Dos.tr("sat.decom_final_title"), Dos.tr("sat.decom_final", name, name),
									new String[] { Dos.tr("button.no"), Dos.tr("button.yes") }, index -> { // No first: Enter alone does not end it.
										if(index != 1)
											return;
										CompoundTag data = field(s, "decommission");
										data.putString("confirm", name);
										session.app(app, "set", data, response -> {
											if(!response.ok()) {
												showError(response);
												return;
											}
											session.message(Dos.tr("sat.decom_done", name), false);
											refresh();
										});
									}))));
				}
			}
			case GLFW.GLFW_KEY_F10 -> {
				if(s != null)
					showDialog(DosDialog.ask(Dos.tr("sat.image_title"), Dos.tr("sat.image_prompt"), new DosInput(s.getString("image"), 12,
							c -> Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-'), name -> {
						CompoundTag data = field(s, "image");
						data.putString("file", name);
						send(data);
					}));
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	private static CompoundTag field(CompoundTag satellite, String field) {
		CompoundTag data = new CompoundTag();
		data.putString("name", satellite.getString("name"));
		data.putString("field", field);
		return data;
	}

	private void send(CompoundTag data) {
		session.app(app, "set", data, response -> {
			if(response.ok())
				refresh();
			else
				showError(response);
		});
	}

	/** Ticks as minutes and seconds of real time, {@code 12:05}. */
	private static String clock(long ticks) {
		long seconds = Math.max(0L, ticks) / 20L;
		return seconds >= 3600L ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600L, seconds / 60L % 60L, seconds % 60L)
				: String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("sat.title"), Dos.tr("sat.keys"));
		int bg = DosColours.BODY_BG;
		CompoundTag st = status;
		if(st == null) {
			g.put(3, 3, Dos.tr("sat.reading"), DosColours.BODY_FG, bg);
			return;
		}
		ListTag list = satellites();
		g.box(1, 2, 28, 21, false, Dos.tr("sat.list"), DosColours.TEXT_FG, bg);
		g.box(30, 2, 49, 21, false, Dos.tr(switch(view) {
			case DETAILS -> "sat.detail";
			case IMAGE -> "sat.view_image";
			case LOCATION -> "sat.view_location";
		}), DosColours.TEXT_FG, bg);
		if(!st.getBoolean("overworld"))
			g.put(32, 21, Dos.fit(Dos.tr("sat.not_overworld"), 45), DosColours.YELLOW, bg);
		if(list.isEmpty()) {
			g.put(3, 4, Dos.fit(Dos.tr("sat.none"), 25), DosColours.BODY_FG, bg);
			for(int i = 0; i < 3; i++)
				g.put(32, 4 + i, Dos.fit(Dos.tr("sat.none_hint" + (i + 1)), 45), DosColours.DIM, bg);
			return;
		}
		selected = Math.max(0, Math.min(selected, list.size() - 1));
		int first = firstShown();
		for(int i = first; i < list.size() && i - first < 19; i++) {
			CompoundTag s = list.getCompound(i);
			String when;
			if(s.getBoolean("geo") && !s.getBoolean("passing"))
				when = Dos.tr("sat.geo_never");
			else if(s.getBoolean("passing"))
				when = String.format(Locale.ROOT, "UP %2.0f %s", s.getDouble("el"), com.arrl.radiocraft.common.radio.satellite.SatelliteOrbit.compass(s.getDouble("az")));
			else {
				ListTag passes = s.getList("passes", Tag.TAG_COMPOUND);
				when = passes.isEmpty() ? "" : Dos.tr("sat.in", clock(passes.getCompound(0).getLong("in")));
			}
			if(s.getBoolean("moon") && !s.getBoolean("passing"))
				when = Dos.tr("sat.moon_down");
			String state = s.getBoolean("moon") ? Dos.tr("sat.moon_state") : s.getBoolean("dead") ? Dos.tr("sat.dead") : s.getBoolean("stealth") ? Dos.tr("sat.stealth_short")
					: s.getBoolean("on") ? (s.getBoolean("geo") ? Dos.tr("sat.geo") : "") : "OFF";
			String mark = s.getBoolean("locked") ? "*" : s.getBoolean("chosen") ? ">" : " "; // On it, or turning to it.
			String line = String.format(Locale.ROOT, "%s%-6s %-4s %s", mark, s.getString("name"), state, when);
			boolean picked = i == selected;
			int fg = s.getBoolean("moon") ? DosColours.WHITE : s.getBoolean("amateur") ? DosColours.LIGHT_GREEN : DosColours.LIGHT_RED;
			g.putField(2, 3 + i - first, Dos.fit(line, 26), 26, picked ? DosColours.HIGHLIGHT_FG : fg, picked ? DosColours.HIGHLIGHT_BG : bg);
		}
		CompoundTag s = picked();
		if(s == null)
			return;
		switch(view) {
			case DETAILS -> detail(g, s, bg, now);
			case IMAGE -> image(g, s, bg, now);
			case LOCATION -> location(g, s, bg, st.getBoolean("overworld"));
		}
	}

	/** The pictures' place on the screen: the right-hand box inside its frame, in screen pixels. */
	private static final int PIC_X = 31 * TextGrid.CELL_W, PIC_Y = 3 * TextGrid.CELL_H, PIC_W = 47 * TextGrid.CELL_W;
	private static final int IMAGE_ROWS = 17, LOCATION_ROWS = 15;

	/** The Image view: it tumbling in space, and what kind it is. */
	private void image(TextGrid g, CompoundTag s, int bg, long now) {
		boolean moon = s.getBoolean("moon");
		int kind = moon ? -1 : Math.floorMod(s.getInt("art"), SatelliteModels.KINDS.length);
		int h = IMAGE_ROWS * TextGrid.CELL_H;
		g.fill(31, 3, 47, IMAGE_ROWS, ' ', DosColours.BLACK, DosColours.BLACK);
		long wall = System.currentTimeMillis();
		byte[] frame = SatPictures.frame(s.getString("name"), kind, PIC_W, h, wall);
		if(frame != null && !moon && !s.getBoolean("dead"))
			frame = withLight(frame, SatPictures.light(s.getString("name"), kind, PIC_W, h, wall), s, h);
		if(frame != null)
			g.image(PIC_X, PIC_Y, PIC_W, h, "sattrack/image", frame, PIC_W, h);
		else
			g.put(33, 4, Dos.tr("sat.reading"), DosColours.DIM, DosColours.BLACK);
		String caption = moon ? Dos.tr("sat.art.moon") : s.getString("name") + "  " + Dos.tr("sat.art." + SatelliteModels.KINDS[kind]);
		g.put(32, 20, Dos.fit(caption, 45), moon ? DosColours.WHITE : s.getBoolean("amateur") ? DosColours.LIGHT_GREEN : DosColours.LIGHT_RED, bg);
	}

	/**
	 * {@code frame} with the satellite's light on it at {@code at}, blinking as the one in the sky does (2026-11-14, the
	 * maintainer): the same timing ({@code SatelliteSkyRenderer#flash}, game time) and colour, green for amateur, red for
	 * commercial, a faint grey between flashes. A copy: the turn's frames stay clean.
	 */
	private static byte[] withLight(byte[] frame, @Nullable int[] at, CompoundTag s, int height) {
		if(at == null)
			return frame;
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		float t = mc.level == null ? 0.0F : mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
		float flash = com.arrl.radiocraft.client.render.SatelliteSkyRenderer.flash(s.getString("name"), s.getBoolean("geo"), t);
		int lit = com.arrl.radiocraft.client.render.SatelliteSkyRenderer.flashColour(s.getBoolean("amateur"));
		int core, glow;
		if(flash > 0.02F) {
			core = PixelCanvas.mix(0x606060, lit, Math.min(1.0D, 0.4D + flash));
			glow = PixelCanvas.mix(0x202020, lit, flash * 0.6D);
		}
		else {
			core = 0x6A6A6A;
			glow = -1;
		}
		byte[] out = frame.clone();
		byte coreId = SatPictures.mapId(core), glowId = glow < 0 ? 0 : SatPictures.mapId(glow);
		for(int dy = -2; dy <= 2; dy++)
			for(int dx = -2; dx <= 2; dx++) {
				int x = at[0] + dx, y = at[1] + dy;
				if(x < 0 || y < 0 || x >= PIC_W || y >= height)
					continue;
				boolean inner = Math.abs(dx) <= 1 && Math.abs(dy) <= 1;
				if(inner)
					out[y * PIC_W + x] = coreId;
				else if(glow >= 0 && Math.abs(dx) + Math.abs(dy) <= 3)
					out[y * PIC_W + x] = glowId;
			}
		return out;
	}

	/** The Location picture last drawn, and what it showed (drawn again only when that changes). */
	@Nullable
	private byte[] locationPicture;
	private String locationShown = "";

	private static double wrapTile(double blocks) {
		double tile = SatelliteOrbit.TILE;
		double r = (blocks + tile / 2.0D) % tile;
		return (r < 0.0D ? r + tile : r) - tile / 2.0D;
	}

	/** The Location view: it going round the world, the slice in view from here lighter, and when it is next up. */
	private void location(TextGrid g, CompoundTag s, int bg, boolean overworld) {
		boolean passing = s.getBoolean("passing");
		boolean fixed = s.getBoolean("moon") || s.getBoolean("geo");
		double half = -1.0D, angle = Double.NaN, across = Double.NaN;
		boolean up = passing;
		if(!overworld) {
			// Nothing to show: no sky here.
		}
		else if(fixed) { // The Moon or a geostationary one: the sky above the horizon, it in it or below.
			half = Math.PI / 2.0D;
			double side = s.getDouble("az") < 180.0D ? 1.0D : -1.0D;
			angle = passing ? side * Math.toRadians(90.0D - s.getDouble("el")) : Math.PI;
		}
		else if(s.contains("along")) {
			double ticks = Math.max(0L, System.currentTimeMillis() - statusAt) / 50.0D;
			double along = wrapTile(s.getDouble("along") + s.getDouble("along_speed") * ticks);
			across = wrapTile(s.getDouble("across") + s.getDouble("across_speed") * ticks);
			half = SatelliteOrbitView.halfSlice(across, SatelliteOrbit.COVERAGE, SatelliteOrbit.TILE);
			angle = SatelliteOrbitView.angle(along, SatelliteOrbit.TILE);
		}
		int h = LOCATION_ROWS * TextGrid.CELL_H;
		String shown = String.format(Locale.ROOT, "%.3f/%.3f/%b/%s", half, angle, up, s.getString("name"));
		if(locationPicture == null || !shown.equals(locationShown)) {
			PixelCanvas picture = new PixelCanvas(PIC_W, h);
			picture.starfield(77L);
			SatelliteOrbitView.draw(picture, half, angle, up);
			locationPicture = SatPictures.toMapIds(picture);
			locationShown = shown;
		}
		g.fill(31, 3, 47, LOCATION_ROWS, ' ', DosColours.BLACK, DosColours.BLACK);
		g.image(PIC_X, PIC_Y, PIC_W, h, "sattrack/location", locationPicture, PIC_W, h);
		if(!overworld)
			return;
		String first;
		if(passing)
			first = Dos.tr("sat.now_up", String.format(Locale.ROOT, "%.0f", s.getDouble("el")), SatelliteOrbit.compass(s.getDouble("az")));
		else if(s.getBoolean("moon"))
			first = Dos.tr("sat.moon_down_long");
		else if(s.getBoolean("geo"))
			first = Dos.tr("sat.geo_down");
		else {
			ListTag passes = s.getList("passes", Tag.TAG_COMPOUND);
			first = passes.isEmpty() ? Dos.tr("sat.now_down") : Dos.tr("sat.loc_rises", clock(passes.getCompound(0).getLong("in")));
		}
		g.put(32, 18, Dos.fit(first, 45), passing ? DosColours.LIGHT_CYAN : DosColours.BODY_FG, bg);
		String second = fixed ? Dos.tr(s.getBoolean("moon") ? "sat.loc_moon" : "sat.loc_geo")
				: Double.isNaN(across) ? "" : half > 0.0D ? Dos.tr("sat.loc_track", String.format(Locale.ROOT, "%,d", Math.round(Math.abs(across))))
				: Dos.tr("sat.loc_miss");
		g.put(32, 19, Dos.fit(second, 45), DosColours.DIM, bg);
		g.put(32, 20, Dos.fit(Dos.tr("sat.loc_key"), 45), DosColours.DIM, bg);
	}

	/**
	 * Where it is from here: a geostationary one is always up here (at one elevation and bearing) or never; an orbiting
	 * one passing or below the horizon. Returns the next row.
	 */
	private static int whereNow(TextGrid g, CompoundTag s, int c, int r, int w, int bg, boolean geo) {
		boolean up = s.getBoolean("passing");
		String el = String.format(Locale.ROOT, "%.0f", s.getDouble("el"));
		String to = com.arrl.radiocraft.common.radio.satellite.SatelliteOrbit.compass(s.getDouble("az"));
		if(geo)
			g.put(c, r++, Dos.fit(up ? Dos.tr("sat.geo_up", el, to) : Dos.tr("sat.geo_down"), w), up ? DosColours.LIGHT_CYAN : DosColours.DIM, bg);
		else
			g.put(c, r++, Dos.fit(up ? Dos.tr("sat.now_up", el, to) : Dos.tr("sat.now_down"), w), up ? DosColours.LIGHT_CYAN : DosColours.DIM, bg);
		return r;
	}

	/** Whether the station's tracking antenna follows {@code s}, and the key to make it. */
	private void trackLine(TextGrid g, CompoundTag s, int c, int r, int w, int bg) {
		if(status == null || !status.getBoolean("trackers"))
			return;
		String text = s.getBoolean("locked") ? Dos.tr("sat.track_locked") : s.getBoolean("chosen") ? Dos.tr("sat.track_turning") : Dos.tr("sat.track_key");
		g.put(c, r, Dos.fit(text, w), s.getBoolean("locked") ? DosColours.LIGHT_GREEN : s.getBoolean("chosen") ? DosColours.YELLOW : DosColours.DIM, bg);
	}

	private void detail(TextGrid g, CompoundTag s, int bg, long now) {
		int r = 3, c = 32, w = 45, last = 20;
		boolean amateur = s.getBoolean("amateur");
		String kind = Dos.tr(amateur ? "sat.amateur" : "sat.commercial");
		boolean geo = s.getBoolean("geo"), dead = s.getBoolean("dead");
		if(s.getBoolean("moon")) { // Moonbounce (2026-11-12).
			g.put(c, r++, Dos.fit(Dos.tr("sat.moon_title"), w), DosColours.WHITE, bg);
			g.put(c, r++, Dos.fit(s.getBoolean("passing") ? Dos.tr("sat.now_up", String.format(Locale.ROOT, "%.0f", s.getDouble("el")),
					com.arrl.radiocraft.common.radio.satellite.SatelliteOrbit.compass(s.getDouble("az"))) : Dos.tr("sat.moon_down_long"), w),
					s.getBoolean("passing") ? DosColours.LIGHT_CYAN : DosColours.DIM, bg);
			r++;
			for(int i = 1; i <= 4; i++)
				g.put(c, r++, Dos.fit(Dos.tr("sat.moon_help" + i), w), DosColours.TEXT_FG, bg);
			r++;
			trackLine(g, s, c, r, w, bg);
			return;
		}
		if(s.getBoolean("hidden")) { // Someone else's stealth satellite (2026-11-12): its name and where it is.
			g.put(c, r++, Dos.fit(s.getString("name") + "  " + Dos.tr("sat.stealth"), w), DosColours.LIGHT_MAGENTA, bg);
			g.put(c, r++, Dos.fit(Dos.tr("sat.stealth_hidden"), w), DosColours.DIM, bg);
			r = whereNow(g, s, c, r, w, bg, geo);
			if(!geo) {
				g.put(c, r++, Dos.fit(Dos.tr("sat.passes_head"), w), DosColours.YELLOW, bg);
				ListTag passes = s.getList("passes", Tag.TAG_COMPOUND);
				for(int i = 0; i < passes.size() && r < last; i++) {
					CompoundTag p = passes.getCompound(i);
					long in = p.getLong("in");
					g.put(c, r++, Dos.fit(String.format(Locale.ROOT, "%-9s %5.0f   %-3s %7s", in <= 0 ? Dos.tr("sat.now") : clock(in), p.getDouble("el"),
							p.getString("from"), clock(p.getLong("ticks"))), w), in <= 0 ? DosColours.LIGHT_CYAN : DosColours.TEXT_FG, bg);
				}
			}
			return;
		}
		g.put(c, r++, Dos.fit(s.getString("name") + "  " + kind + (geo ? " " + Dos.tr("sat.geo") : "") + "  "
						+ Dos.tr(dead ? "sat.dead" : s.getBoolean("on") ? "sat.on" : "sat.off"), w),
				amateur ? DosColours.LIGHT_GREEN : DosColours.LIGHT_RED, bg);
		boolean blink = (now / 500L) % 2L == 0L;
		if(dead) { // Decommissioned (2026-11-12): nothing to control, tracked until it comes down.
			g.put(c, r++, Dos.fit(Dos.tr("sat.decommissioned"), w), blink ? DosColours.WHITE : DosColours.LIGHT_RED, bg);
			g.put(c, r++, Dos.fit(Dos.tr("sat.owner_allowed", s.getString("owner"), "-"), w), DosColours.BODY_FG, bg);
			g.put(c, r++, Dos.fit(Dos.tr("sat.decommissioned_decay", s.getLong("days")), w), DosColours.BODY_FG, bg);
			whereNow(g, s, c, r, w, bg, geo);
			return;
		}
		g.put(c, r++, Dos.fit(Dos.tr("sat.freqs3", s.getString("up"), s.getString("down"), s.getString("tlm")), w), DosColours.TEXT_FG, bg);
		g.put(c, r++, Dos.fit(Dos.tr("sat.tone_id", s.getString("tone"), s.getString("id")), w), DosColours.TEXT_FG, bg);
		String mode = s.getString("mode");
		String modeLine = switch(mode) {
			case "APRS" -> Dos.tr("sat.mode_aprs", s.getString("aprs"));
			case "MAILBOX" -> Dos.tr("sat.mode_mailbox", s.getInt("mail"));
			case "SSTV" -> s.getString("image").isEmpty() ? Dos.tr("sat.mode_sstv_none") : Dos.tr("sat.mode_sstv", s.getString("image"));
			case "IMAGING" -> Dos.tr("sat.mode_imaging"); // Commercial weather imaging (2026-11-12).
			default -> Dos.tr(amateur ? "sat.mode_repeater" : "sat.mode_commercial");
		};
		g.put(c, r++, Dos.fit(modeLine, w), DosColours.LIGHT_CYAN, bg);
		if(s.getBoolean("stealth"))
			g.put(c, r++, Dos.fit(Dos.tr("sat.stealth_on"), w), DosColours.LIGHT_MAGENTA, bg);
		if(mode.equals("APRS") && s.getBoolean("aprs_warn"))
			g.put(c, r++, Dos.fit(Dos.tr("sat.aprs_warn"), w), DosColours.YELLOW, bg);
		ListTag allowed = s.getList("allowed", Tag.TAG_STRING);
		StringBuilder names = new StringBuilder();
		for(int i = 0; i < allowed.size(); i++)
			names.append(i == 0 ? "" : ", ").append(allowed.getString(i));
		g.put(c, r++, Dos.fit(Dos.tr("sat.owner_allowed", s.getString("owner"), names.length() == 0 ? "-" : names.toString()), w), DosColours.BODY_FG, bg);
		boolean warn = s.getBoolean("warn");
		g.put(c, r++, Dos.fit(Dos.tr(warn ? "sat.decay_warn" : "sat.decay", s.getString("launched"), s.getLong("days")), w),
				warn ? (blink ? DosColours.WHITE : DosColours.YELLOW) : DosColours.BODY_FG, bg);
		if(geo) {
			r = whereNow(g, s, c, r, w, bg, true);
			if(s.getBoolean("passing") && s.getBoolean("eclipse"))
				g.put(c, r++, Dos.fit(Dos.tr("sat.eclipse"), w), blink ? DosColours.WHITE : DosColours.YELLOW, bg);
		}
		else if(s.getBoolean("passing")) {
			String up = Dos.tr("sat.now_up", String.format(Locale.ROOT, "%.0f", s.getDouble("el")),
					com.arrl.radiocraft.common.radio.satellite.SatelliteOrbit.compass(s.getDouble("az")));
			if(s.contains("shift"))
				up += "  " + Dos.tr("sat.doppler", String.format(Locale.ROOT, "%+.1f", s.getDouble("shift") / 1000.0D));
			g.put(c, r++, Dos.fit(up, w), DosColours.LIGHT_CYAN, bg);
			if(s.getBoolean("eclipse"))
				g.put(c, r++, Dos.fit(Dos.tr("sat.eclipse"), w), blink ? DosColours.WHITE : DosColours.YELLOW, bg);
		}
		else
			g.put(c, r++, Dos.fit(Dos.tr("sat.now_down"), w), DosColours.DIM, bg);
		// Telemetry: what a radio at this station last copied on the telemetry frequency.
		if(s.contains("telemetry")) {
			CompoundTag t = s.getCompound("telemetry");
			g.put(c, r++, Dos.fit(Dos.tr("sat.tlm_head", clock(t.getLong("ago") * 20L)), w), DosColours.YELLOW, bg);
			g.put(c, r++, Dos.fit(Dos.tr("sat.tlm_line", String.format(Locale.ROOT, "%.2f", t.getDouble("volts")), String.format(Locale.ROOT, "%+.2f", t.getDouble("amps")),
					String.format(Locale.ROOT, "%+d", t.getInt("temp")), Dos.tr(t.getBoolean("sun") ? "sat.tlm_sun" : "sat.tlm_shadow"), t.getString("mode"), t.getLong("dk")), w),
					t.getBoolean("low") ? DosColours.LIGHT_RED : DosColours.TEXT_FG, bg);
			if(t.getBoolean("low"))
				g.put(c, r++, Dos.fit(Dos.tr(t.getBoolean("saving") ? "sat.tlm_saving" : "sat.tlm_low"), w), blink ? DosColours.WHITE : DosColours.LIGHT_RED, bg);
			if(s.getBoolean("mine") && t.getLong("dk") <= com.arrl.radiocraft.common.radio.satellite.SatelliteRules.WARN_DAYS)
				g.put(c, r++, Dos.fit(Dos.tr("sat.tlm_decay", t.getLong("dk")), w), blink ? DosColours.WHITE : DosColours.YELLOW, bg);
		}
		else
			g.put(c, r++, Dos.fit(Dos.tr("sat.tlm_none", s.getString("tlm")), w), DosColours.DIM, bg);
		if(geo)
			g.put(c, r++, Dos.fit(Dos.tr("sat.geo_passes"), w), DosColours.DIM, bg);
		else
			g.put(c, r++, Dos.fit(Dos.tr("sat.passes_head"), w), DosColours.YELLOW, bg);
		ListTag passes = s.getList("passes", Tag.TAG_COMPOUND);
		for(int i = 0; i < passes.size() && r < last; i++) {
			CompoundTag p = passes.getCompound(i);
			long in = p.getLong("in");
			String line = String.format(Locale.ROOT, "%-9s %5.0f   %-3s %7s", in <= 0 ? Dos.tr("sat.now") : clock(in), p.getDouble("el"), p.getString("from"),
					clock(p.getLong("ticks")));
			g.put(c, r++, Dos.fit(line, w), in <= 0 ? DosColours.LIGHT_CYAN : DosColours.TEXT_FG, bg);
		}
		trackLine(g, s, c, last - 1, w, bg);
		r = last;
		if(s.getBoolean("mine")) {
			boolean owner = s.getBoolean("owner_is_me");
			String up = owner ? (amateur ? "sat.yours_up_decom" : "sat.yours_up_decom_imaging") : (amateur ? "sat.yours_up_modes" : "sat.yours_up_imaging");
			g.put(c, r, Dos.fit(Dos.tr(s.getBoolean("passing") ? up : "sat.yours_wait"), w),
					s.getBoolean("passing") ? DosColours.LIGHT_GREEN : DosColours.DIM, bg);
			if(!amateur && s.getBoolean("passing"))
				g.put(c, r + 1, Dos.fit(Dos.tr(s.getBoolean("stealth") ? "sat.stealth_key_off" : "sat.stealth_key_on"), w), DosColours.LIGHT_MAGENTA, bg);
		}
		else
			g.put(c, r, Dos.fit(Dos.tr("sat.anyone"), w), DosColours.DIM, bg);
	}
}
