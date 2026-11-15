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
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * FCC (2026-11-10): spectrum monitoring with a direction finder cabled to the computer. The left panel shows what the
 * finder is tuned to and hears, with the bearing, and who is licensed there; UNLICENSED in red when no license grants
 * a commercial frequency someone is transmitting on. The right panel logs every signal heard while the program is open.
 * F3 types a frequency (not F2: Minecraft takes that for screenshots), the arrows retune a channel at a time, F8 clears the log.
 */
public final class FccApp extends DosApp {

	private static final long STATUS_MS = 500L;

	@Nullable
	private CompoundTag status;
	private long lastStatus;
	private boolean wasUnlicensed;

	public FccApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		setMenuBar(new DosMenuBar(List.of( // 2026-11-12: each item does what its key does.
				new DosMenuBar.Menu(Dos.tr("menu.file"), List.of(
						item("menu.fcc.clear_log", "F8", GLFW.GLFW_KEY_F8),
						item("menu.exit", "Esc", GLFW.GLFW_KEY_ESCAPE))),
				new DosMenuBar.Menu(Dos.tr("menu.radio"), List.of(
						item("menu.fcc.frequency", "F3", GLFW.GLFW_KEY_F3),
						item("menu.fcc.up", "Up", GLFW.GLFW_KEY_UP),
						item("menu.fcc.down", "Down", GLFW.GLFW_KEY_DOWN),
						item("menu.fcc.up10", "PgUp", GLFW.GLFW_KEY_PAGE_UP),
						item("menu.fcc.down10", "PgDn", GLFW.GLFW_KEY_PAGE_DOWN))))));
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
			boolean unlicensed = status.getBoolean("hearing") && illegal(status.getString("verdict"));
			if(unlicensed && !wasUnlicensed)
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), 1.6F, 0.5F));
			wasUnlicensed = unlicensed;
		});
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_F3 -> typeFrequency();
			case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_RIGHT -> tune(1);
			case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_LEFT -> tune(-1);
			case GLFW.GLFW_KEY_PAGE_UP -> tune(10);
			case GLFW.GLFW_KEY_PAGE_DOWN -> tune(-10);
			case GLFW.GLFW_KEY_F8 -> session.app(app, "clear", new CompoundTag(), response -> refresh());
			default -> {
				return false;
			}
		}
		return true;
	}

	private void tune(int steps) {
		CompoundTag data = new CompoundTag();
		data.putInt("steps", steps);
		session.app(app, "tune", data, response -> {
			if(response.ok())
				refresh();
			else
				showError(response);
		});
	}

	private void typeFrequency() {
		DosInput mhz = new DosInput(status == null ? "" : String.format(Locale.ROOT, "%.4f", status.getFloat("frequency") / 1_000_000.0F), 10,
				c -> Character.isDigit(c) || c == '.');
		showDialog(DosDialog.ask(Dos.tr("fcc.freq_title"), Dos.tr("fcc.freq_hint"), mhz, value -> {
			try {
				CompoundTag data = new CompoundTag();
				data.putFloat("mhz", Float.parseFloat(value));
				session.app(app, "frequency", data, response -> {
					if(response.ok())
						refresh();
					else
						showError(response);
				});
			}
			catch(NumberFormatException e) {
				session.message(Dos.tr("fcc.freq_bad"), true);
			}
		}));
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("fcc.title"), Dos.tr("fcc.keys"));
		int bg = DosColours.BODY_BG;
		g.box(1, 2, 36, 21, false, Dos.tr("fcc.monitor"), DosColours.TEXT_FG, bg);
		g.box(38, 2, 41, 21, false, Dos.tr("fcc.log"), DosColours.TEXT_FG, bg);
		CompoundTag s = status;
		if(s == null) {
			g.put(3, 4, Dos.tr("fcc.reading"), DosColours.BODY_FG, bg);
			return;
		}
		renderLog(g, s, bg);
		if(!s.getBoolean("finder")) {
			g.put(3, 4, Dos.fit(Dos.tr("fcc.no_finder"), 33), DosColours.YELLOW, bg);
			g.put(3, 5, Dos.fit(Dos.tr("fcc.no_finder_hint"), 33), DosColours.BODY_FG, bg);
			g.put(3, 6, Dos.fit(Dos.tr("fcc.no_finder_hint2"), 33), DosColours.BODY_FG, bg);
			return;
		}
		int r = 4;
		g.put(3, r++, String.format(Locale.ROOT, "%.4f MHz  %s", s.getFloat("frequency") / 1_000_000.0F, s.getString("band")), DosColours.TEXT_FG, bg);
		if(!s.getBoolean("on")) {
			g.put(3, ++r, Dos.fit(Dos.tr("fcc.finder_off"), 33), DosColours.YELLOW, bg);
			return;
		}
		r++;
		boolean hearing = s.getBoolean("hearing");
		int bars = hearing ? (int)Math.round(Math.max(0.0D, Math.min(1.0D, s.getDouble("meter"))) * 20) : 0;
		g.put(3, r, Dos.tr("fcc.signal"), DosColours.BODY_FG, bg);
		g.put(11, r++, String.valueOf(TextGrid.FULL).repeat(bars) + String.valueOf(TextGrid.SHADE_LIGHT).repeat(20 - bars), hearing ? DosColours.LIGHT_GREEN : DosColours.DIM, bg);
		g.put(3, r++, s.contains("bearing") ? Dos.tr("fcc.bearing", String.format(Locale.ROOT, "%03d", Math.round(s.getDouble("bearing")) % 360),
				String.format(Locale.ROOT, "%.0f", s.getDouble("sigma"))) : Dos.tr("fcc.no_bearing"), DosColours.TEXT_FG, bg);
		r++;

		String verdict = s.getString("verdict");
		g.put(3, r++, Dos.tr("fcc.status"), DosColours.BODY_FG, bg);
		int colour = switch(verdict) {
			case "UNLICENSED", "CB_AMPLIFIED" -> DosColours.LIGHT_RED;
			case "LICENSED" -> DosColours.LIGHT_GREEN;
			default -> DosColours.LIGHT_CYAN;
		};
		boolean flash = illegal(verdict) && hearing && (now / 400L) % 2L == 0L;
		g.putField(3, r++, Dos.fit(" " + Dos.tr("fcc.verdict." + verdict.toLowerCase(Locale.ROOT)), 33), 33,
				flash ? DosColours.WHITE : colour, flash ? DosColours.RED : bg);
		if(verdict.equals("LICENSED")) {
			g.put(3, r++, Dos.fit(Dos.tr("fcc.licensee", s.getString("callsign"), s.getString("holder")), 33), DosColours.TEXT_FG, bg);
		}
		else if(verdict.equals("UNLICENSED")) {
			g.put(3, r++, Dos.fit(Dos.tr(hearing ? "fcc.unlicensed_on_air" : "fcc.unlicensed_quiet"), 33), hearing ? DosColours.YELLOW : DosColours.BODY_FG, bg);
		}
		else if(verdict.equals("CB_AMPLIFIED")) {
			g.put(3, r++, Dos.fit(Dos.tr("fcc.cb_amplified_on_air"), 33), DosColours.YELLOW, bg);
		}
		r++;
		for(String line : Dos.tr("fcc.help").split("\n"))
			if(r < 21)
				g.put(3, r++, Dos.fit(line, 33), DosColours.DIM, bg);
	}

	private static void renderLog(TextGrid g, CompoundTag s, int bg) {
		ListTag log = s.getList("log", Tag.TAG_COMPOUND);
		g.put(40, 3, Dos.fit(Dos.tr("fcc.log_head"), 37), DosColours.YELLOW, bg);
		if(log.isEmpty()) {
			g.put(40, 4, Dos.fit(Dos.tr("fcc.log_empty"), 37), DosColours.BODY_FG, bg);
			return;
		}
		for(int i = 0; i < Math.min(log.size(), 17); i++) {
			CompoundTag e = log.getCompound(i);
			long minutes = e.getLong("time") / 1200L;
			String verdict = e.getString("verdict");
			String who = verdict.equals("LICENSED") ? e.getString("callsign") : Dos.tr("fcc.short." + verdict.toLowerCase(Locale.ROOT));
			String bearing = e.contains("bearing") ? String.format(Locale.ROOT, "%03d", Math.round(e.getDouble("bearing")) % 360) : "---";
			String line = String.format(Locale.ROOT, "%3dm %-9s %-10s %s", minutes, e.getString("mhz"), who, bearing);
			g.put(40, 4 + i, Dos.fit(line, 37), illegal(verdict) ? DosColours.LIGHT_RED : DosColours.TEXT_FG, bg);
		}
	}

	/** UNLICENSED, or CB through an amplifier: the two the monitor sounds for. */
	private static boolean illegal(String verdict) {
		return verdict.equals("UNLICENSED") || verdict.equals("CB_AMPLIFIED");
	}
}
