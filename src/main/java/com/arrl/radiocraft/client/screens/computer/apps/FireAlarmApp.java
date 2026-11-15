package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosMenuBar;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * FIRE ALARM (2026-11-10): the fire alarm controller cabled to the computer. Left, its boxes by number with how each
 * stands (an arrow picks one); right, its log, newest first. F2 acknowledges (STOP), F5 places the picked box out of
 * service or back in, Del takes it off the list. The header flashes red while an alarm waits to be acknowledged.
 */
public final class FireAlarmApp extends DosApp {

	private static final long STATUS_MS = 500L;

	@Nullable
	private CompoundTag status;
	private long lastStatus;
	private int selected = 0;
	private int logScroll = 0;
	private boolean wasAttention;
	/** The box map in place of the log (F4, 2026-11-13). */
	private boolean showMap = false;
	private static final long MAP_MS = 5000L, FLASH_MS = 400L;
	private static final int SIZE = com.arrl.radiocraft.common.radio.data.AprsMapPainter.SIZE;
	private long lastMap = 0L;
	@Nullable
	private byte[] terrain;
	@Nullable
	private com.arrl.radiocraft.common.radio.data.AprsMapPainter.Fit fit;
	private ListTag places = new ListTag();
	/** The two painted frames (alarms lit, dark) and what they were painted from: repainted into new arrays when that changes. */
	private final byte[][] frames = new byte[2][];
	private final Object[] painted = new Object[2];

	public FireAlarmApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		setMenuBar(new DosMenuBar(List.of( // 2026-11-12: each item does what its key does.
				new DosMenuBar.Menu(Dos.tr("menu.file"), List.of(
						item("menu.exit", "Esc", GLFW.GLFW_KEY_ESCAPE))),
				new DosMenuBar.Menu(Dos.tr("menu.fire.panel"), List.of(
						item("menu.fire.acknowledge", "F2", GLFW.GLFW_KEY_F2),
						item("menu.fire.service", "F5", GLFW.GLFW_KEY_F5).when(() -> selectedBox() != null),
						item("menu.fire.forget", "Del", GLFW.GLFW_KEY_DELETE).when(() -> selectedBox() != null))),
				new DosMenuBar.Menu(Dos.tr("menu.view"), List.of(
						item("menu.fire.map", "F4", GLFW.GLFW_KEY_F4),
						item("menu.fire.previous_box", "Up", GLFW.GLFW_KEY_UP),
						item("menu.fire.next_box", "Down", GLFW.GLFW_KEY_DOWN),
						item("menu.fire.log_up", "PgUp", GLFW.GLFW_KEY_PAGE_UP),
						item("menu.fire.log_down", "PgDn", GLFW.GLFW_KEY_PAGE_DOWN))))));
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
		if(showMap && !hasDialog() && System.currentTimeMillis() - lastMap > MAP_MS)
			refreshMap();
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
			fit = new com.arrl.radiocraft.common.radio.data.AprsMapPainter.Fit(d.getInt("cx"), d.getInt("cz"), Math.max(1, d.getInt("bpp")), SIZE, SIZE);
			places = d.getList("boxes", Tag.TAG_COMPOUND);
		});
	}

	private void refresh() {
		lastStatus = System.currentTimeMillis();
		session.app(app, "status", new CompoundTag(), response -> {
			if(!response.ok())
				return;
			status = response.data();
			boolean attention = status.getBoolean("attention");
			if(attention && !wasAttention)
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), 1.2F, 0.6F));
			wasAttention = attention;
		});
	}

	private ListTag boxes() {
		return status == null ? new ListTag() : status.getList("boxes", Tag.TAG_COMPOUND);
	}

	@Nullable
	private CompoundTag selectedBox() {
		ListTag boxes = boxes();
		return boxes.isEmpty() ? null : boxes.getCompound(Math.max(0, Math.min(selected, boxes.size() - 1)));
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_UP -> selected = Math.max(0, selected - 1);
			case GLFW.GLFW_KEY_DOWN -> selected = Math.min(Math.max(0, boxes().size() - 1), selected + 1);
			case GLFW.GLFW_KEY_PAGE_UP -> logScroll = Math.max(0, logScroll - 10);
			case GLFW.GLFW_KEY_PAGE_DOWN -> logScroll += 10;
			case GLFW.GLFW_KEY_F2 -> send("silence", new CompoundTag());
			case GLFW.GLFW_KEY_F4 -> {
				showMap = !showMap;
				if(showMap)
					refreshMap();
			}
			case GLFW.GLFW_KEY_F5 -> {
				CompoundTag box = selectedBox();
				if(box != null) {
					CompoundTag data = new CompoundTag();
					data.putInt("box", box.getInt("box"));
					data.putBoolean("out", !box.getBoolean("out"));
					send("service", data);
				}
			}
			case GLFW.GLFW_KEY_DELETE -> {
				CompoundTag box = selectedBox();
				if(box != null)
					showDialog(DosDialog.confirm(Dos.tr("fire.forget_title"), Dos.tr("fire.forget", String.format(Locale.ROOT, "%04d", box.getInt("box"))), () -> {
						CompoundTag data = new CompoundTag();
						data.putInt("box", box.getInt("box"));
						send("forget", data);
					}));
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
		Dos.frame(g, Dos.tr("fire.title"), Dos.tr("fire.keys"));
		int bg = DosColours.BODY_BG;
		CompoundTag s = status;
		if(s == null) {
			g.put(3, 3, Dos.tr("fire.reading"), DosColours.BODY_FG, bg);
			return;
		}
		if(!s.getBoolean("controller")) {
			g.box(1, 2, 78, 6, false, Dos.tr("fire.title"), DosColours.TEXT_FG, bg);
			g.put(3, 4, Dos.tr("fire.no_controller"), DosColours.YELLOW, bg);
			g.put(3, 5, Dos.tr("fire.no_controller_hint"), DosColours.BODY_FG, bg);
			return;
		}
		boolean attention = s.getBoolean("attention");
		boolean flash = attention && (now / 400L) % 2L == 0L;
		String head = s.getString("mhz").isEmpty() ? Dos.tr("fire.no_frequency")
				: Dos.tr("fire.head", s.getString("mhz"), s.getString("license"));
		String state = attention ? Dos.tr(s.getBoolean("alarm") ? "fire.state_alarm" : "fire.state_trouble") : Dos.tr(s.getBoolean("alarm") ? "fire.state_silenced" : "fire.state_normal");
		g.putField(1, 1, Dos.fit(" " + head + "   " + state, 78), 78, flash ? DosColours.WHITE : attention ? DosColours.LIGHT_RED : DosColours.TEXT_FG, flash ? DosColours.RED : bg);
		String printer = s.getBoolean("jammed") ? Dos.tr("fire.head_jam") : s.getInt("paper") <= 0 ? Dos.tr("fire.paper_out") : Dos.tr("fire.paper", s.getInt("paper"));
		if(s.getInt("slips") > 0)
			printer += "  " + Dos.tr("fire.slips", s.getInt("slips"));
		g.put(3, 22, Dos.fit(printer, 74), s.getBoolean("jammed") || s.getInt("paper") <= 0 ? DosColours.YELLOW : DosColours.DIM, bg);

		g.box(1, 2, 38, 20, false, Dos.tr("fire.boxes"), DosColours.TEXT_FG, bg);
		ListTag boxes = boxes();
		selected = Math.max(0, Math.min(selected, Math.max(0, boxes.size() - 1)));
		if(boxes.isEmpty())
			g.put(3, 4, Dos.fit(Dos.tr("fire.no_boxes"), 34), DosColours.BODY_FG, bg);
		int first = Math.max(0, selected - 15);
		for(int i = first; i < boxes.size() && i - first < 17; i++) {
			CompoundTag box = boxes.getCompound(i);
			String status = box.getBoolean("out") ? "OUT SVC" : box.getString("status");
			int[] zones = box.getIntArray("zones");
			String zoneText = zones.length == 0 ? "" : " Z" + Arrays.toString(zones).replace(" ", "").replace("[", "").replace("]", "");
			String line = String.format(Locale.ROOT, "%04d %-8s %s", box.getInt("box"), status, box.getString("label")) + zoneText;
			int fg = switch(box.getString("status")) {
				case "ALARM" -> DosColours.LIGHT_RED;
				case "TROUBLE", "MISSING" -> DosColours.YELLOW;
				default -> DosColours.TEXT_FG;
			};
			if(box.getBoolean("out"))
				fg = DosColours.DIM;
			boolean picked = i == selected;
			g.putField(2, 3 + i - first, Dos.fit(line, 36), 36, picked ? DosColours.HIGHLIGHT_FG : fg, picked ? DosColours.HIGHLIGHT_BG : bg);
		}
		CompoundTag picked = selectedBox();
		if(picked != null) {
			long minutes = picked.getLong("ago") / 1200L;
			g.put(3, 20, Dos.fit(Dos.tr("fire.heard", minutes), 34), DosColours.DIM, bg);
		}

		if(showMap) {
			renderMap(g, bg, now);
			return;
		}
		g.box(40, 2, 39, 20, false, Dos.tr("fire.log"), DosColours.TEXT_FG, bg);
		ListTag log = s.getList("log", Tag.TAG_COMPOUND);
		logScroll = Math.max(0, Math.min(logScroll, Math.max(0, log.size() - 17)));
		if(log.isEmpty())
			g.put(42, 4, Dos.fit(Dos.tr("fire.log_empty"), 35), DosColours.BODY_FG, bg);
		for(int i = logScroll; i < log.size() && i - logScroll < 18; i++) {
			CompoundTag e = log.getCompound(i);
			String line = e.getString("line");
			g.put(41, 3 + i - logScroll, Dos.fit(line, 37), e.getBoolean("attention") ? DosColours.LIGHT_RED : DosColours.TEXT_FG, bg);
		}
	}

	/** The box map: terrain round the controller, each box in its status colour (alarms flash), the picked one ringed. */
	private void renderMap(TextGrid g, int bg, long now) {
		g.box(40, 2, 39, 20, false, Dos.tr("fire.map"), DosColours.TEXT_FG, bg);
		byte[] base = terrain;
		com.arrl.radiocraft.common.radio.data.AprsMapPainter.Fit f = fit;
		if(base == null || f == null) {
			g.put(42, 4, Dos.tr("fire.reading"), DosColours.BODY_FG, bg);
			return;
		}
		java.util.Map<Integer, CompoundTag> byNumber = new java.util.HashMap<>();
		for(int i = 0; i < boxes().size(); i++)
			byNumber.put(boxes().getCompound(i).getInt("box"), boxes().getCompound(i));
		CompoundTag pickedBox = selectedBox();
		int picked = pickedBox == null ? -1 : pickedBox.getInt("box");
		boolean lit = (now / FLASH_MS) % 2L == 0L;
		int which = lit ? 0 : 1;
		java.util.List<Object> from = java.util.List.of(base, places, boxes(), picked);
		if(frames[which] != null && from.equals(painted[which])) {
			if(!hasDialog() && !menuOpen())
				g.image(41 * TextGrid.CELL_W + 23, 3 * TextGrid.CELL_H + 1, 168, 168, "firemap:" + session.pos().asLong() + ":" + which, frames[which], SIZE, SIZE);
			drawUnplaced(g, bg);
			return;
		}
		byte[] frame = base.clone();
		frames[which] = frame;
		painted[which] = from;
		java.util.Set<Integer> placed = new java.util.HashSet<>();
		for(int i = 0; i < places.size(); i++) {
			CompoundTag p = places.getCompound(i);
			CompoundTag box = byNumber.get(p.getInt("box"));
			if(box == null)
				continue;
			placed.add(p.getInt("box"));
			boolean alarm = box.getString("status").equals("ALARM") && !box.getBoolean("out");
			com.arrl.radiocraft.common.radio.firealarm.FireAlarmMap.box(frame, f, p.getInt("x") + 0.5D, p.getInt("z") + 0.5D, p.getInt("box"),
					com.arrl.radiocraft.common.radio.firealarm.FireAlarmMap.colour(box.getString("status"), box.getBoolean("out")), !alarm || lit, p.getInt("box") == picked);
		}
		unplaced = 0;
		for(int i = 0; i < boxes().size(); i++)
			if(!placed.contains(boxes().getCompound(i).getInt("box")))
				unplaced++;
		if(!hasDialog() && !menuOpen()) // Pictures are drawn over the text: none under an open menu.
			g.image(41 * TextGrid.CELL_W + 23, 3 * TextGrid.CELL_H + 1, 168, 168, "firemap:" + session.pos().asLong() + ":" + which, frame, SIZE, SIZE);
		drawUnplaced(g, bg);
	}

	private int unplaced = 0;

	private void drawUnplaced(TextGrid g, int bg) {
		if(unplaced > 0)
			g.put(42, 20, Dos.fit(Dos.tr("fire.map_unplaced", unplaced), 35), DosColours.DIM, bg);
	}
}
