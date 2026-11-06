package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerResponse;
import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosFilePicker;
import com.arrl.radiocraft.client.screens.computer.DosFormDialog;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.DosPickDialog;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.FrequencyList;
import com.arrl.radiocraft.common.computer.Waterfall;
import com.arrl.radiocraft.common.radio.BandScope;
import com.arrl.radiocraft.common.radio.bandplan.BandPlan;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RIGCTL, rig control and a live waterfall (computer batch 3B): the radio on the computer's radio interface, driven from
 * the keyboard as CAT software drives a real rig. The big display shows the frequency, mode, band, power, PTT, S-meter
 * and squelch; under it the waterfall scrolls down across the band (or a quarter, or a sixteenth of it round the
 * dial, Z, and a sixty-fourth), a column per bin, brighter for stronger signals, with the dial marked; click a signal to tune to it, or
 * Ctrl+arrows to jump to the next one. Left/Right tune a step (Shift: ten), F types a frequency, PgUp/PgDn change band
 * (the all-band radio), M the mode, P the power, T the PTT. S scans a range and F3 the memory channels (the Frequency
 * Manager's list, F5 loads another .FRQ, F4 stores the dial), stopping on a signal over the squelch (Q); Space holds
 * the scan, X stops it. The server side is {@code RigControlHandler}; the waterfall rows are its band scope, asked for
 * four times a second while this is on screen.
 */
public final class RigControlApp extends DosApp {

	private static final int DISPLAY_ROW = 2, SCALE_ROW = 6, FALL_TOP = 7, FALL_ROWS = 14, FALL_LEFT = 2, LABEL_ROW = FALL_TOP + FALL_ROWS;
	private static final long SAMPLE_MS = 250L, STATUS_MS = 5_000L;
	/** The waterfall's colours, faint to strong: themed like any of the sixteen. */
	private static final int[] RAMP = { DosColours.BLUE, DosColours.LIGHT_BLUE, DosColours.CYAN, DosColours.LIGHT_CYAN, DosColours.LIGHT_GREEN,
			DosColours.YELLOW, DosColours.LIGHT_RED, DosColours.WHITE };

	private CompoundTag station = new CompoundTag();
	private CompoundTag rig = new CompoundTag();
	private final byte[][] rows = new byte[FALL_ROWS][BandScope.BINS];
	private final long[] rowSeeds = new long[FALL_ROWS];
	private long rowCount;
	private double lo, hi, noise;
	private long lastSample, lastStatus;
	private boolean sampling;
	private final List<String> memoryNames = new ArrayList<>();
	private final List<Double> memoryMhz = new ArrayList<>();
	private int memoryAt = -1;

	public RigControlApp(ComputerSession session, ComputerApp app) {
		super(session, app);
	}

	@Override
	public void opened() {
		status();
	}

	@Override
	public void resumed() {
		status();
	}

	@Override
	public void tick() {
		long now = System.currentTimeMillis();
		if(!sampling && now - lastSample >= SAMPLE_MS && station.getString("problem").isEmpty() && rig.contains("hz"))
			sample();
		if(now - lastStatus > STATUS_MS)
			status();
	}

	@Override
	protected boolean onEscape() {
		session.app(app, "leave", new CompoundTag(), response -> {});
		exit();
		return true;
	}

	private void status() {
		lastStatus = System.currentTimeMillis();
		session.app(app, "status", new CompoundTag(), response -> {
			if(!response.ok())
				return;
			station = response.data();
			rig = station.getCompound("rig");
			memoryNames.clear();
			memoryMhz.clear();
			ListTag list = station.getList("memories", Tag.TAG_COMPOUND);
			for(int i = 0; i < list.size(); i++) {
				FrequencyList.Entry entry = FrequencyList.Entry.fromTag(list.getCompound(i));
				memoryNames.add(entry.name());
				memoryMhz.add(entry.rxMhz());
			}
		});
	}

	private void sample() {
		sampling = true;
		lastSample = System.currentTimeMillis();
		session.app(app, "scope", new CompoundTag(), response -> {
			sampling = false;
			if(response.ok())
				take(response.data());
		});
	}

	@Override
	public void event(String kind, CompoundTag data) {
		if(kind.equals("scope")) // Pushed while the screen is closed and we are near the monitor.
			take(data);
	}

	/** A new waterfall row and the rig's state. */
	private void take(CompoundTag data) {
		rig = data;
		double newLo = data.getDouble("lo"), newHi = data.getDouble("hi");
		if(newLo != lo || newHi != hi) { // Another span: the old rows mean other frequencies.
			for(byte[] row : rows)
				java.util.Arrays.fill(row, (byte)0);
			lo = newLo;
			hi = newHi;
		}
		noise = data.getDouble("noise");
		for(int i = FALL_ROWS - 1; i > 0; i--) {
			System.arraycopy(rows[i - 1], 0, rows[i], 0, BandScope.BINS);
			rowSeeds[i] = rowSeeds[i - 1];
		}
		byte[] row = data.getByteArray("row");
		for(int i = 0; i < BandScope.BINS; i++)
			rows[0][i] = i < row.length ? row[i] : 0;
		rowSeeds[0] = ++rowCount * 0x9E3779B97F4A7C15L;
	}

	// -------------------- ACTIONS --------------------

	private void act(String action, CompoundTag data) {
		session.app(app, action, data, this::answered);
	}

	private void answered(ComputerResponse response) {
		if(!response.ok()) {
			session.message(response.errorText(), true);
			return;
		}
		CompoundTag data = response.data();
		if(data.contains("rig")) {
			station = data;
			rig = data.getCompound("rig");
		}
	}

	private static CompoundTag tag(String key, double value) {
		CompoundTag tag = new CompoundTag();
		tag.putDouble(key, value);
		return tag;
	}

	private static CompoundTag intTag(String key, int value) {
		CompoundTag tag = new CompoundTag();
		tag.putInt(key, value);
		return tag;
	}

	private void tuneTo(double hz) {
		act("tune", tag("hz", hz));
	}

	/** The strongest level seen lately per bin (the last few rows), for jumping to signals. */
	private int[] recentLevels() {
		int[] out = new int[BandScope.BINS];
		for(int r = 0; r < Math.min(4, FALL_ROWS); r++)
			for(int i = 0; i < out.length; i++)
				out[i] = Math.max(out[i], rows[r][i]);
		return out;
	}

	private void jump(int dir) {
		if(!(hi > lo))
			return;
		int from = Waterfall.bin(rig.getDouble("hz"), lo, hi, BandScope.BINS);
		int to = Waterfall.nextSignal(recentLevels(), Math.max(0, from), dir, 2);
		if(to < 0) {
			session.message(Dos.tr("rig.no_signal"), false);
			return;
		}
		tuneTo(Waterfall.centre(to, lo, hi, BandScope.BINS));
	}

	private void memory(int dir) {
		if(memoryMhz.isEmpty()) {
			session.message(Dos.tr("err.rig_no_memories"), true);
			return;
		}
		memoryAt = Math.floorMod(memoryAt + dir, memoryMhz.size());
		tuneTo(memoryMhz.get(memoryAt) * 1_000_000.0D);
		session.message(Dos.tr("rig.memory_tuned", memoryAt + 1, memoryNames.get(memoryAt)), false);
	}

	private void typedFrequency() {
		DosInput field = new DosInput(FrequencyList.mhz(rig.getDouble("hz") / 1_000_000.0D), 12, c -> Character.isDigit(c) || c == '.');
		showDialog(DosDialog.ask(Dos.tr("rig.freq_title"), Dos.tr("rig.freq_prompt"), field, value -> {
			double mhz = FrequencyList.parseMhz(value);
			if(mhz > 0.0D)
				tuneTo(mhz * 1_000_000.0D);
			else
				session.message(Dos.tr("err.rig_out_of_range"), true);
		}));
	}

	private void scanRange() {
		double from = hi > lo ? lo : rig.getDouble("bandLo"), to = hi > lo ? hi : rig.getDouble("bandHi");
		List<DosFormDialog.Field> fields = List.of(
				new DosFormDialog.Field(Dos.tr("rig.scan_from"), new DosInput(FrequencyList.mhz(from / 1_000_000.0D), 12, c -> Character.isDigit(c) || c == '.')),
				new DosFormDialog.Field(Dos.tr("rig.scan_to"), new DosInput(FrequencyList.mhz(to / 1_000_000.0D), 12, c -> Character.isDigit(c) || c == '.')));
		showDialog(new DosFormDialog(Dos.tr("rig.scan_title"), Dos.tr("rig.scan_help"), fields, values -> {
			double low = FrequencyList.parseMhz(values.get(0)), high = FrequencyList.parseMhz(values.get(1));
			if(!(low > 0.0D) || !(high > 0.0D))
				return Dos.tr("err.rig_bad_range");
			CompoundTag data = new CompoundTag();
			data.putString("mode", "range");
			data.putDouble("low", low * 1_000_000.0D);
			data.putDouble("high", high * 1_000_000.0D);
			act("scan", data);
			return null;
		}));
	}

	private void scanMemories() {
		CompoundTag data = new CompoundTag();
		data.putString("mode", "memory");
		act("scan", data);
	}

	private void storeMemory() {
		DosInput field = new DosInput("", FrequencyList.NAME_MAX);
		showDialog(DosDialog.ask(Dos.tr("rig.store_title"), Dos.tr("rig.store_prompt", station.getString("memoryFile")), field, value -> {
			CompoundTag data = new CompoundTag();
			data.putString("name", value);
			session.app(app, "store", data, response -> {
				answered(response);
				if(response.ok()) {
					session.message(Dos.tr("rig.stored", station.getString("memoryFile")), false);
					status();
				}
			});
		}));
	}

	private void loadMemories() {
		showDialog(new DosFilePicker(session, Dos.tr("rig.load_title"), ComputerFileFormats.FREQUENCIES, name -> {
			CompoundTag data = new CompoundTag();
			data.putString("file", name);
			session.app(app, "memories", data, response -> {
				answered(response);
				if(response.ok())
					status();
			});
		}));
	}

	private void pickRadio() {
		ListTag radios = station.getList("radios", Tag.TAG_COMPOUND);
		if(radios.size() < 2) {
			session.message(Dos.tr("rig.one_radio"), false);
			return;
		}
		List<String> lines = new ArrayList<>();
		for(int i = 0; i < radios.size(); i++) {
			CompoundTag radio = radios.getCompound(i);
			lines.add(Dos.fit(I18n.get(radio.getString("name")), 30) + "  " + mhz(radio.getDouble("hz")));
		}
		showDialog(new DosPickDialog(Dos.tr("rig.pick_title"), Dos.tr("rig.pick_prompt"), lines, index -> {
			CompoundTag data = new CompoundTag();
			data.putLong("pos", radios.getCompound(index).getLong("pos"));
			act("select", data);
		}));
	}

	// -------------------- KEYS AND MOUSE --------------------

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		boolean shift = Screen.hasShiftDown();
		switch(key) {
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
				int dir = key == GLFW.GLFW_KEY_LEFT ? -1 : 1;
				if(Dos.ctrl())
					jump(dir);
				else
					act("step", intTag("steps", dir * (shift ? 10 : 1)));
			}
			case GLFW.GLFW_KEY_UP -> memory(1);
			case GLFW.GLFW_KEY_DOWN -> memory(-1);
			case GLFW.GLFW_KEY_PAGE_UP -> act("band", intTag("dir", 1));
			case GLFW.GLFW_KEY_PAGE_DOWN -> act("band", intTag("dir", -1));
			case GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> typedFrequency();
			case GLFW.GLFW_KEY_M -> act("mode", new CompoundTag());
			case GLFW.GLFW_KEY_P -> act("power", new CompoundTag());
			case GLFW.GLFW_KEY_T -> {
				CompoundTag data = new CompoundTag();
				data.putBoolean("on", !rig.getBoolean("ptt"));
				act("ptt", data);
			}
			case GLFW.GLFW_KEY_S -> scanRange();
			case GLFW.GLFW_KEY_F3 -> scanMemories();
			case GLFW.GLFW_KEY_F4 -> storeMemory();
			case GLFW.GLFW_KEY_F5 -> loadMemories();
			case GLFW.GLFW_KEY_SPACE -> act("hold", new CompoundTag());
			case GLFW.GLFW_KEY_X -> {
				CompoundTag data = new CompoundTag();
				data.putString("mode", "off");
				act("scan", data);
			}
			case GLFW.GLFW_KEY_Z -> act("zoom", intTag("zoom", rig.getInt("zoom") + (shift ? -1 : 1)));
			case GLFW.GLFW_KEY_Q -> act("squelch", intTag("level", (rig.getInt("squelch") + (shift ? 9 : 1)) % 10));
			case GLFW.GLFW_KEY_R -> pickRadio();
			default -> {
				return false;
			}
		}
		Dos.click();
		return true;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(row >= SCALE_ROW && row < FALL_TOP + FALL_ROWS && col >= FALL_LEFT && col < FALL_LEFT + BandScope.BINS && hi > lo) {
			tuneTo(Waterfall.centre(col - FALL_LEFT, lo, hi, BandScope.BINS));
			return true;
		}
		return false;
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		act("step", intTag("steps", amount > 0 ? 1 : -1));
		return true;
	}

	// -------------------- DRAWING --------------------

	static String mhz(double hz) {
		return String.format(Locale.ROOT, "%.3f", hz / 1_000_000.0D);
	}

	/** "14.074.00": MHz, kHz and tens of Hz, as a rig's display reads. */
	static String dial(double hz) {
		long tens = Math.round(hz / 10.0D);
		long mhz = tens / 100_000, khz = tens / 100 % 1000, rest = tens % 100;
		return String.format(Locale.ROOT, "%d.%03d.%02d", mhz, khz, rest);
	}

	@Override
	public void render(TextGrid g, long now) {
		String name = rig.contains("name") ? I18n.get(rig.getString("name")) : "";
		Dos.frame(g, Dos.tr("rig.title", name), Dos.tr("rig.keys"));
		int bg = DosColours.BODY_BG;
		String problem = station.getString("problem");
		if(!problem.isEmpty()) {
			g.putField(0, 1, " " + Dos.tr("err." + problem), TextGrid.COLS, DosColours.ERROR_FG, DosColours.ERROR_BG);
			g.put(2, 4, Dos.tr("rig.need_interface"), DosColours.TEXT_FG, bg);
			footer(g, bg);
			return;
		}
		g.putField(0, 1, " " + Dos.tr("rig.station", station.getString("myCall").isEmpty() ? "-" : station.getString("myCall"),
				station.getList("radios", Tag.TAG_COMPOUND).size()), TextGrid.COLS, DosColours.BLACK, DosColours.LIGHT_GREY);
		drawDisplay(g, now);
		drawScale(g);
		drawFall(g);
		footer(g, bg);
	}

	private void drawDisplay(TextGrid g, long now) {
		int fg = DosColours.FIELD_FG, field = DosColours.FIELD_BG;
		g.box(0, DISPLAY_ROW - 0, TextGrid.COLS, 4, true, "", DosColours.BODY_FG, DosColours.BODY_BG);
		g.fill(1, DISPLAY_ROW + 1, TextGrid.COLS - 2, 2, ' ', fg, field);
		boolean power = rig.getBoolean("power");
		int digits = power ? DosColours.ACCENT : DosColours.DIM;
		int col = g.put(2, DISPLAY_ROW + 1, dial(rig.getDouble("hz")), digits, field);
		col = g.put(col + 1, DISPLAY_ROW + 1, "MHz", DosColours.DIM, field);
		col = g.put(col + 3, DISPLAY_ROW + 1, Dos.fit(rig.getString("mode"), 4), DosColours.TEXT_FG, field);
		String band = rig.getString("band");
		col = g.put(col + 3, DISPLAY_ROW + 1, band.equals("low_band") ? Dos.tr("rig.low_band") : band, DosColours.TEXT_FG, field);
		if(rig.getBoolean("channelized"))
			g.put(col + 2, DISPLAY_ROW + 1, Dos.tr("rig.channelized"), DosColours.DIM, field);
		g.put(52, DISPLAY_ROW + 1, power ? Dos.tr("rig.power_on") : Dos.tr("rig.power_off"), DosColours.WHITE, power ? DosColours.GREEN : DosColours.DARK_GREY);
		boolean tx = rig.getBoolean("ptt");
		String txText = !rig.getBoolean("transmitter") ? " RX ONLY " : rig.getBoolean("inhibited") && tx ? " INHIBIT " : tx ? "   TX    " : "   RX    ";
		g.put(62, DISPLAY_ROW + 1, txText, DosColours.WHITE, tx ? DosColours.RED : DosColours.BLUE);
		g.put(72, DISPLAY_ROW + 1, Dos.tr("rig.zoom", 1 << (2 * Math.max(0, Math.min(Waterfall.ZOOMS - 1, rig.getInt("zoom"))))), DosColours.DIM, field);
		// S-meter, squelch, scan.
		int meter = rig.getInt("meter");
		g.put(2, DISPLAY_ROW + 2, "S", DosColours.DIM, field);
		for(int i = 0; i < 15; i++)
			g.set(4 + i, DISPLAY_ROW + 2, i < meter ? TextGrid.FULL : TextGrid.SHADE_LIGHT, i < meter ? (i >= 9 ? DosColours.BAD : DosColours.GOOD) : DosColours.DIM, field);
		g.put(4 + 16, DISPLAY_ROW + 2, meter >= 10 ? "+" + (meter - 9) * 10 : "S" + Math.min(9, (meter * 9 + 14) / 15), DosColours.TEXT_FG, field);
		g.put(28, DISPLAY_ROW + 2, Dos.tr("rig.squelch", rig.getInt("squelch")), DosColours.TEXT_FG, field);
		String scan = rig.getString("scan");
		if(!scan.isEmpty() && !scan.equals("OFF")) {
			boolean blink = (now / 400) % 2 == 0;
			String what = rig.getBoolean("scanHeld") ? Dos.tr("rig.scan_held") : rig.getBoolean("scanStopped") ? Dos.tr("rig.scan_stopped") : Dos.tr("rig.scanning");
			g.put(38, DISPLAY_ROW + 2, (scan.equals("MEMORY") ? Dos.tr("rig.scan_memory") : Dos.tr("rig.scan_range")) + " " + what,
					rig.getBoolean("scanStopped") || blink ? DosColours.ACCENT : DosColours.DIM, field);
		}
		else
			g.put(38, DISPLAY_ROW + 2, Dos.tr("rig.memories", memoryMhz.size(), station.getString("memoryFile")), DosColours.DIM, field);
	}

	private void drawScale(TextGrid g) {
		int bg = DosColours.BODY_BG;
		g.hline(FALL_LEFT, SCALE_ROW, BandScope.BINS, TextGrid.H, DosColours.BODY_FG, bg);
		if(!(hi > lo))
			return;
		for(BandPlan.Marker marker : BandPlan.current().markers()) { // The band plan's spots: FT8, PSK31, SSTV...
			int bin = Waterfall.bin(marker.hz(), lo, hi, BandScope.BINS);
			if(bin >= 0)
				g.set(FALL_LEFT + bin, SCALE_ROW, '+', DosColours.LIGHT_MAGENTA, bg);
		}
		int dial = Waterfall.bin(rig.getDouble("hz"), lo, hi, BandScope.BINS);
		if(dial >= 0)
			g.set(FALL_LEFT + dial, SCALE_ROW, TextGrid.ARROW_DOWN, DosColours.ACCENT, bg);
		g.set(FALL_LEFT - 1, SCALE_ROW, TextGrid.TL, DosColours.BODY_FG, bg);
		g.set(FALL_LEFT + BandScope.BINS, SCALE_ROW, TextGrid.TR, DosColours.BODY_FG, bg);
	}

	private void drawFall(TextGrid g) {
		int bg = DosColours.BODY_BG, field = DosColours.FIELD_BG;
		int dial = hi > lo ? Waterfall.bin(rig.getDouble("hz"), lo, hi, BandScope.BINS) : -1;
		double speckle = Math.max(0.02D, Math.min(0.35D, noise)); // More noise, more snow.
		for(int r = 0; r < FALL_ROWS; r++) {
			int row = FALL_TOP + r;
			g.set(FALL_LEFT - 1, row, TextGrid.V, DosColours.BODY_FG, bg);
			g.set(FALL_LEFT + BandScope.BINS, row, TextGrid.V, DosColours.BODY_FG, bg);
			long seed = rowSeeds[r];
			for(int i = 0; i < BandScope.BINS; i++) {
				int level = rows[r][i];
				if(level > 0) {
					int shade = Math.min(RAMP.length - 1, (level - 1) / 2);
					char c = (level & 1) == 1 && shade > 0 ? TextGrid.SHADE_DARK : TextGrid.FULL;
					g.set(FALL_LEFT + i, row, c, RAMP[shade], shade > 0 ? RAMP[shade - 1] : field);
					continue;
				}
				seed = seed * 6364136223846793005L + 1442695040888963407L;
				boolean snow = seed != 0L && rowSeeds[r] != 0L && ((seed >>> 40) & 0xFFFF) / 65536.0D < speckle * 0.4D;
				g.set(FALL_LEFT + i, row, snow ? TextGrid.SHADE_LIGHT : ' ', DosColours.BLUE, field);
			}
			if(dial >= 0 && rows[r][dial] == 0)
				g.set(FALL_LEFT + dial, row, ':', DosColours.DIM, field); // The dial, down the waterfall.
		}
		g.fill(FALL_LEFT - 1, LABEL_ROW, BandScope.BINS + 2, 1, ' ', DosColours.BODY_FG, bg);
		g.set(FALL_LEFT - 1, LABEL_ROW, TextGrid.BL, DosColours.BODY_FG, bg);
		g.hline(FALL_LEFT, LABEL_ROW, BandScope.BINS, TextGrid.H, DosColours.BODY_FG, bg);
		g.set(FALL_LEFT + BandScope.BINS, LABEL_ROW, TextGrid.BR, DosColours.BODY_FG, bg);
		if(hi > lo) {
			String left = mhz(lo), middle = mhz((lo + hi) / 2.0D), right = mhz(hi);
			g.put(FALL_LEFT, LABEL_ROW, " " + left + " ", DosColours.TEXT_FG, bg);
			g.put(FALL_LEFT + BandScope.BINS / 2 - middle.length() / 2 - 1, LABEL_ROW, " " + middle + " ", DosColours.TEXT_FG, bg);
			g.put(FALL_LEFT + BandScope.BINS - right.length() - 2, LABEL_ROW, " " + right + " ", DosColours.TEXT_FG, bg);
		}
		else
			g.put(FALL_LEFT + 2, FALL_TOP + 2, Dos.tr("rig.waiting"), DosColours.DIM, field);
	}

	private void footer(TextGrid g, int bg) {
		g.putField(0, 22, " " + Dos.tr("rig.hint"), TextGrid.COLS, DosColours.DIM, bg);
		String message = session.message();
		if(!message.isEmpty())
			g.putField(0, 23, " " + message, TextGrid.COLS, session.messageIsError() ? DosColours.RED : DosColours.BLACK, DosColours.LIGHT_GREY);
		else
			g.putField(0, 23, " " + Dos.tr("rig.help"), TextGrid.COLS, DosColours.BODY_FG, bg);
	}
}
