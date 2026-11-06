package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.DosPickDialog;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.PropagationForecast;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PREDICT, the Propagation Predictor (computer apps 2, 2026-11-06): pick a target (coordinates, a callsign, or a
 * distance and bearing), Enter, and it shows which HF bands are expected open to it over the next 24 in-game hours, a
 * band by hour grid like VOACAP's, from the live solar weather and its forecast (server side:
 * {@code PropagationHandler}). Tab changes the kind of target, F5 picks a callsign the computer knows, F6 prints the
 * grid, F7 saves it as {@code PREDICT.TXT}. The grid refreshes every 20 seconds while shown.
 */
public final class PropagationApp extends DosApp {

	private static final long REFRESH_MS = 20_000L;
	private static final String[] MODES = { "coords", "call", "bearing" };
	private static final int GRID_LEFT = 8, CELL = 3, HEADER_ROW = 5, FIRST_BAND_ROW = 7;

	private int mode = 2;
	private final DosInput[] fields = {
			new DosInput("", 24, c -> Character.isDigit(c) || c == '-' || c == ' ' || c == ','),
			new DosInput("", 12, c -> Character.isLetterOrDigit(c) && c < 128 || c == '-' || c == '/'),
			new DosInput("", 24, c -> Character.isDigit(c) || c == ' ' || c == ',' || c == '.')
	};
	@Nullable
	private CompoundTag prediction;
	@Nullable
	private CompoundTag asked; // The last request, repeated by the refresh.
	private String problem = "";
	private long lastAsked = 0L;

	public PropagationApp(ComputerSession session, ComputerApp app) {
		super(session, app);
	}

	@Override
	public void tick() {
		if(asked != null && prediction != null && System.currentTimeMillis() - lastAsked > REFRESH_MS && !hasDialog())
			send(asked);
	}

	@Override
	public void resumed() {
		if(asked != null)
			send(asked);
	}

	// -------------------- ASKING --------------------

	/** The request for what is typed, or null (with {@link #problem} set) if it does not read. */
	@Nullable
	private CompoundTag request() {
		CompoundTag data = new CompoundTag();
		data.putString("mode", MODES[mode]);
		String text = fields[mode].text().trim();
		try {
			switch(MODES[mode]) {
				case "coords" -> {
					String[] parts = text.split("[ ,]+");
					if(parts.length != 2)
						throw new NumberFormatException();
					data.putInt("x", Integer.parseInt(parts[0]));
					data.putInt("z", Integer.parseInt(parts[1]));
				}
				case "bearing" -> {
					String[] parts = text.split("[ ,]+");
					if(parts.length != 2)
						throw new NumberFormatException();
					data.putInt("distance", Integer.parseInt(parts[0]));
					data.putDouble("bearing", Double.parseDouble(parts[1]));
				}
				default -> {
					if(text.isEmpty())
						throw new NumberFormatException();
					data.putString("call", text.toUpperCase(Locale.ROOT));
				}
			}
		}
		catch(NumberFormatException e) {
			problem = Dos.tr("prop.bad_" + MODES[mode]);
			return null;
		}
		return data;
	}

	private void predict() {
		CompoundTag data = request();
		if(data != null)
			send(data);
	}

	private void send(CompoundTag data) {
		asked = data;
		lastAsked = System.currentTimeMillis();
		session.app(app, "predict", data, response -> {
			if(!response.ok()) {
				problem = response.errorText();
				prediction = null;
				return;
			}
			problem = "";
			prediction = response.data();
		});
	}

	private void pickCallsign() {
		session.app(app, "targets", new CompoundTag(), response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			ListTag targets = response.data().getList("targets", Tag.TAG_COMPOUND);
			if(targets.isEmpty()) {
				session.message(Dos.tr("prop.no_targets"), true);
				return;
			}
			List<String> lines = new ArrayList<>();
			for(int i = 0; i < targets.size(); i++) {
				CompoundTag t = targets.getCompound(i);
				lines.add(String.format(Locale.ROOT, "%-12s %-7s %7d %4d", t.getString("call"), Dos.tr("prop.kind_" + t.getString("kind")), t.getInt("distance"), t.getInt("bearing")));
			}
			showDialog(new DosPickDialog(Dos.tr("prop.pick_title"), Dos.tr("prop.pick_message"), lines, index -> {
				mode = 1;
				fields[1].set(targets.getCompound(index).getString("call"));
				predict();
			}));
		});
	}

	// -------------------- WORDS --------------------

	private static String bandName(String band) {
		return band.equals("low_band") ? Dos.tr("prop.low_band") : band;
	}

	private static String eventName(String path) {
		if(path.isEmpty())
			return Dos.tr("prop.no_event");
		String key = "solar_event.radiocraft." + path;
		return I18n.exists(key) ? I18n.get(key) : Character.toUpperCase(path.charAt(0)) + path.substring(1);
	}

	private static String source(CompoundTag data) {
		return switch(data.getString("source")) {
			case "processor" -> Dos.tr("solar.via_processor", data.getString("via"));
			case "radio" -> Dos.tr("solar.via_radio", I18n.get(data.getString("via")));
			default -> Dos.tr("solar.via_direct");
		};
	}

	/** The cell's three characters: L for line of sight, the grade letter (lower case for a guess), c for CW only. */
	private static String cellText(char grade, char cw, char path, boolean known) {
		char shown = grade;
		if(grade == PropagationForecast.Grade.CLOSED.letter && cw != grade)
			shown = 'c';
		else if(!known && grade != PropagationForecast.Grade.CLOSED.letter)
			shown = Character.toLowerCase(grade);
		return (path == PropagationForecast.Path.LOS.letter ? "L" : " ") + shown + " ";
	}

	private static int gradeColour(char grade, char cw, boolean known) {
		PropagationForecast.Grade g = PropagationForecast.Grade.of(grade);
		if(g == PropagationForecast.Grade.CLOSED && cw != grade)
			return known ? DosColours.LIGHT_CYAN : DosColours.CYAN;
		return switch(g) {
			case GOOD -> known ? DosColours.LIGHT_GREEN : DosColours.GREEN;
			case FAIR -> known ? DosColours.YELLOW : DosColours.BROWN;
			case POOR -> known ? DosColours.LIGHT_RED : DosColours.RED;
			case CLOSED -> DosColours.DARK_GREY;
		};
	}

	/** The prediction as plain text lines (for printing and saving). */
	private List<String> pageText() {
		List<String> out = new ArrayList<>();
		if(prediction == null)
			return out;
		CompoundTag p = prediction;
		out.add(Dos.tr("prop.print_header"));
		out.add(Dos.tr("prop.to", p.getString("target"), p.getInt("distance"), p.getInt("bearing")));
		out.add(Dos.tr("prop.sky", eventName(p.getString("event")), p.getInt("noise")));
		ListTag hours = p.getList("hours", Tag.TAG_COMPOUND);
		StringBuilder header = new StringBuilder(String.format(Locale.ROOT, "%-8s", Dos.tr("prop.col_band")));
		for(int h = 0; h < hours.size(); h++)
			header.append(String.format(Locale.ROOT, "%02d ", hours.getCompound(h).getInt("hour")));
		out.add(header.toString().stripTrailing());
		ListTag bands = p.getList("bands", Tag.TAG_COMPOUND);
		for(int i = 0; i < bands.size(); i++) {
			CompoundTag row = bands.getCompound(i);
			StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%-8s", Dos.fit(bandName(row.getString("band")), 7)));
			String grades = row.getString("grades"), cw = row.getString("cw"), paths = row.getString("paths");
			for(int h = 0; h < grades.length() && h < hours.size(); h++)
				line.append(cellText(grades.charAt(h), cw.charAt(h), paths.charAt(h), hours.getCompound(h).getBoolean("known")));
			out.add(line.toString().stripTrailing());
		}
		out.add(Dos.tr("prop.legend"));
		out.add(Dos.tr("prop.legend2"));
		return out;
	}

	private void print() {
		List<String> lines = pageText();
		if(lines.isEmpty())
			return;
		session.scanner().printText(Dos.tr("prop.print_title"), String.join("\n", lines), response -> {
			if(response.ok())
				session.message(Dos.tr("edit.printed"), false);
			else
				showError(response);
		});
	}

	private void saveText() {
		List<String> lines = pageText();
		if(lines.isEmpty())
			return;
		String name = "PREDICT.TXT";
		session.files().writeText(name, String.join("\n", lines), response -> {
			if(response.ok())
				session.message(Dos.tr("edit.saved", name), false);
			else
				showError(response);
		});
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("prop.title"), Dos.tr("prop.keys"));
		int bg = DosColours.BODY_BG, fg = DosColours.BODY_FG;
		// The kind of target, as tabs, and its field.
		int col = g.put(1, 1, Dos.tr("prop.target") + " ", fg, bg);
		for(int i = 0; i < MODES.length; i++) {
			String label = " " + Dos.tr("prop.mode_" + MODES[i]) + " ";
			col = g.put(col, 1, label, i == mode ? DosColours.HIGHLIGHT_FG : fg, i == mode ? DosColours.HIGHLIGHT_BG : bg) + 1;
		}
		g.put(1, 2, Dos.tr("prop.field_" + MODES[mode]), DosColours.TEXT_FG, bg);
		fields[mode].render(g, 26, 2, 26, !hasDialog());
		g.put(54, 2, Dos.tr("prop.enter"), DosColours.DARK_GREY, bg);

		if(!problem.isEmpty()) {
			g.put(1, 3, Dos.fit(problem, 78), DosColours.LIGHT_RED, bg);
			if(prediction == null) {
				g.put(1, 5, Dos.fit(Dos.tr("prop.hint"), 78), fg, bg);
				g.put(1, 6, Dos.fit(Dos.tr("prop.hint2"), 78), fg, bg);
			}
		}
		if(prediction == null) {
			if(problem.isEmpty()) {
				g.put(1, 4, Dos.fit(Dos.tr("prop.hint"), 78), fg, bg);
				g.put(1, 5, Dos.fit(Dos.tr("prop.hint2"), 78), fg, bg);
			}
			renderMessage(g);
			return;
		}
		CompoundTag p = prediction;
		if(problem.isEmpty())
			g.put(1, 3, Dos.fit(Dos.tr("prop.to", p.getString("target"), p.getInt("distance"), p.getInt("bearing")), 78), DosColours.TEXT_FG, bg);
		g.put(1, 4, Dos.fit(Dos.tr("prop.sky", eventName(p.getString("event")), p.getInt("noise")) + "  " + source(p), 78), DosColours.DARK_GREY, bg);

		// Hour header: the clock hour, the sun on a day hour, dimmed where the solar weather is a guess.
		ListTag hours = p.getList("hours", Tag.TAG_COMPOUND);
		g.put(1, HEADER_ROW, Dos.tr("prop.col_band"), DosColours.YELLOW, bg);
		for(int h = 0; h < hours.size() && h < PropagationForecast.HOURS; h++) {
			CompoundTag hour = hours.getCompound(h);
			int x = GRID_LEFT + h * CELL;
			boolean day = hour.getBoolean("day"), known = hour.getBoolean("known");
			g.put(x, HEADER_ROW, String.format(Locale.ROOT, "%02d", hour.getInt("hour")), known ? DosColours.TEXT_FG : DosColours.LIGHT_GREY, bg);
			g.put(x, HEADER_ROW + 1, day ? "**" : "..", day ? DosColours.YELLOW : DosColours.LIGHT_BLUE, bg);
		}
		ListTag bands = p.getList("bands", Tag.TAG_COMPOUND);
		int rows = Math.min(bands.size(), 11);
		for(int i = 0; i < rows; i++) {
			CompoundTag row = bands.getCompound(i);
			int r = FIRST_BAND_ROW + i;
			g.put(1, r, Dos.fit(bandName(row.getString("band")), 7), DosColours.TEXT_FG, bg);
			String grades = row.getString("grades"), cw = row.getString("cw"), paths = row.getString("paths");
			for(int h = 0; h < grades.length() && h < hours.size() && h < PropagationForecast.HOURS; h++) {
				boolean known = hours.getCompound(h).getBoolean("known");
				g.put(GRID_LEFT + h * CELL, r, cellText(grades.charAt(h), cw.charAt(h), paths.charAt(h), known), gradeColour(grades.charAt(h), cw.charAt(h), known),
						known ? DosColours.BLACK : DosColours.BODY_BG);
			}
		}
		int r = FIRST_BAND_ROW + rows + 1;
		String best = p.getString("bestNow");
		g.put(1, r++, best.isEmpty() ? Dos.tr("prop.best_none") : Dos.tr("prop.best_now", bandName(best)), best.isEmpty() ? DosColours.LIGHT_RED : DosColours.LIGHT_GREEN, bg);
		for(int i = 0; i < rows && r < 22; i++) {
			CompoundTag row = bands.getCompound(i);
			int bestHour = row.getInt("best");
			if(bestHour >= 0 && bestHour < hours.size()) {
				col = 1 + (i % 2) * 39;
				g.put(col, r, Dos.fit(Dos.tr("prop.best_hour", bandName(row.getString("band")), String.format(Locale.ROOT, "%02d", hours.getCompound(bestHour).getInt("hour"))), 38), fg, bg);
				if(i % 2 == 1)
					r++;
			}
		}
		g.put(1, 22, Dos.fit(Dos.tr("prop.legend"), 78), DosColours.DARK_GREY, bg);
		renderMessage(g);
	}

	private void renderMessage(TextGrid g) {
		String message = session.message();
		if(!message.isEmpty())
			g.putField(0, 23, " " + message, TextGrid.COLS, session.messageIsError() ? DosColours.RED : DosColours.BLACK, DosColours.LIGHT_GREY);
		else
			g.put(1, 23, Dos.fit(Dos.tr("prop.legend2"), 78), DosColours.DARK_GREY, DosColours.BODY_BG);
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_TAB -> {
				mode = (mode + 1) % MODES.length;
				problem = "";
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> predict();
			case GLFW.GLFW_KEY_F5 -> pickCallsign();
			case GLFW.GLFW_KEY_F6 -> print();
			case GLFW.GLFW_KEY_F7 -> saveText();
			default -> {
				return fields[mode].keyPressed(key);
			}
		}
		return true;
	}

	@Override
	protected boolean onChar(char c) {
		return fields[mode].charTyped(c);
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(row != 1)
			return false;
		int x = 1 + Dos.tr("prop.target").length() + 1;
		for(int i = 0; i < MODES.length; i++) {
			int width = Dos.tr("prop.mode_" + MODES[i]).length() + 2;
			if(col >= x && col < x + width) {
				mode = i;
				problem = "";
				return true;
			}
			x += width + 1;
		}
		return false;
	}
}
