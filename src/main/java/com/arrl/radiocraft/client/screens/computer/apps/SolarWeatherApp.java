package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerServer;
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
 * SOLAR, solar weather and forecasts (1.12 step 6): two pages, Tab between them. SOLAR: the solar event, the noise floor,
 * each HF band's condition now, by day and by night (in colour), VHF and the aurora, sporadic E, and the next event
 * once it is known (a blackout coming in red). FORECAST: the weather report where the computer is, the days ahead and
 * the storms about, from the weather source (vanilla, or a weather mod). The computer hears them through a wired radio
 * with an antenna or an Ionospheric Processor (see the server side, {@code SolarWeatherHandler}). Both pages refresh
 * every 20 seconds while shown; F5 refreshes now, F6 prints the page as a book on the scanner, F7 saves it as a text
 * file.
 */
public final class SolarWeatherApp extends DosApp {

	private static final long REFRESH_MS = 20_000L;

	private boolean forecastPage = false;
	@Nullable
	private CompoundTag solar, forecast;
	private String problem = "";
	private long lastAsked = 0L;
	private int scroll = 0;

	public SolarWeatherApp(ComputerSession session, ComputerApp app) {
		super(session, app);
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
		if(System.currentTimeMillis() - lastAsked > REFRESH_MS && !hasDialog())
			refresh();
	}

	private void refresh() {
		lastAsked = System.currentTimeMillis();
		boolean page = forecastPage;
		session.app(app, page ? "forecast" : "solar", new CompoundTag(), response -> {
			if(!response.ok()) {
				problem = response.errorText();
				return;
			}
			problem = "";
			if(page)
				forecast = response.data();
			else
				solar = response.data();
		});
	}

	// -------------------- WORDS --------------------

	/** The event's name: the data pack's translation if there is one, else its id with a capital. */
	private static String eventName(String path) {
		if(path.isEmpty())
			return Dos.tr("solar.no_event");
		String key = "solar_event.radiocraft." + path;
		return I18n.exists(key) ? I18n.get(key) : Character.toUpperCase(path.charAt(0)) + path.substring(1);
	}

	private static String condition(String name) {
		return I18n.get("message.radiocraft.solar_weather_station." + name);
	}

	private static int conditionColour(String name) {
		return switch(name) {
			case "good" -> DosColours.LIGHT_GREEN;
			case "fair" -> DosColours.YELLOW;
			case "poor" -> DosColours.LIGHT_RED;
			default -> DosColours.DARK_GREY;
		};
	}

	private static String bandName(String band) {
		return band.equals("low_band") ? Dos.tr("solar.low_band") : band;
	}

	/** Where the reports come from, in words. */
	private static String source(CompoundTag data) {
		return switch(data.getString("source")) {
			case "processor" -> Dos.tr("solar.via_processor", data.getString("via"));
			case "radio" -> Dos.tr("solar.via_radio", I18n.get(data.getString("via")));
			default -> Dos.tr("solar.via_direct");
		};
	}

	/** The page as plain text lines (for printing and saving). */
	private List<String> pageText() {
		List<String> out = new ArrayList<>();
		if(forecastPage) {
			if(forecast != null)
				out.addAll(ComputerServer.strings(forecast.getList("lines", Tag.TAG_STRING)));
			return out;
		}
		if(solar == null)
			return out;
		out.add(Dos.tr("solar.print_header"));
		if(solar.getString("event").isEmpty()) {
			out.add(Dos.tr("solar.no_event"));
			return out;
		}
		out.add(Dos.tr("solar.conditions", eventName(solar.getString("event")), solar.getInt("level")));
		out.add(Dos.tr("solar.noise", solar.getInt("noise")));
		ListTag bands = solar.getList("bands", Tag.TAG_COMPOUND);
		for(int i = 0; i < bands.size(); i++) {
			CompoundTag row = bands.getCompound(i);
			out.add(String.format(Locale.ROOT, "%-8s %s (%s / %s)", bandName(row.getString("band")), condition(row.getString("now")),
					condition(row.getString("day")), condition(row.getString("night"))));
		}
		out.add(Dos.tr("solar.vhf", Dos.tr("solar.vhf." + solar.getString("vhf"))));
		if(solar.contains("next"))
			out.add(Dos.tr("solar.next", eventName(solar.getString("next")), solar.getInt("nextMinutes")));
		return out;
	}

	private void print() {
		List<String> lines = pageText();
		if(lines.isEmpty())
			return;
		String title = Dos.tr(forecastPage ? "solar.print_forecast" : "solar.print_solar");
		session.scanner().printText(title, String.join("\n", lines), response -> {
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
		String name = forecastPage ? "FORECAST.TXT" : "SOLAR.TXT";
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
		Dos.frame(g, Dos.tr(forecastPage ? "solar.title_forecast" : "solar.title_solar"), Dos.tr("solar.keys"));
		int bg = DosColours.BODY_BG;
		// The tabs.
		g.put(2, 1, " " + Dos.tr("solar.tab_solar") + " ", forecastPage ? DosColours.BODY_FG : DosColours.HIGHLIGHT_FG, forecastPage ? bg : DosColours.HIGHLIGHT_BG);
		g.put(4 + Dos.tr("solar.tab_solar").length(), 1, " " + Dos.tr("solar.tab_forecast") + " ", forecastPage ? DosColours.HIGHLIGHT_FG : DosColours.BODY_FG,
				forecastPage ? DosColours.HIGHLIGHT_BG : bg);
		CompoundTag data = forecastPage ? forecast : solar;
		if(!problem.isEmpty()) {
			g.box(10, 8, 60, 7, true, Dos.tr("solar.no_receiver_title"), DosColours.DIALOG_FG, DosColours.DIALOG_BG);
			List<String> lines = new ArrayList<>();
			for(String paragraph : (problem + "\n" + Dos.tr("solar.no_receiver_hint")).split("\n"))
				lines.add(paragraph);
			for(int i = 0; i < lines.size() && i < 4; i++)
				g.put(12, 10 + i, Dos.fit(lines.get(i), 56), i == 0 ? DosColours.RED : DosColours.DIALOG_FG, DosColours.DIALOG_BG);
			return;
		}
		if(data == null) {
			g.put(2, 3, Dos.tr("solar.reading"), DosColours.TEXT_FG, bg);
			return;
		}
		g.put(2, 2, Dos.fit(source(data), TextGrid.COLS - 4), DosColours.DARK_GREY, bg);
		if(forecastPage)
			renderForecast(g, data);
		else
			renderSolar(g, data);
		String message = session.message();
		if(!message.isEmpty())
			g.putField(0, 23, " " + message, TextGrid.COLS, session.messageIsError() ? DosColours.RED : DosColours.BLACK, DosColours.LIGHT_GREY);
	}

	private void renderSolar(TextGrid g, CompoundTag data) {
		int fg = DosColours.BODY_FG, bg = DosColours.BODY_BG, head = DosColours.YELLOW;
		if(data.getString("event").isEmpty()) {
			g.put(2, 4, Dos.tr("solar.no_event"), DosColours.TEXT_FG, bg);
			return;
		}
		g.box(1, 3, 38, 9, false, Dos.tr("solar.sun"), DosColours.TEXT_FG, bg);
		g.put(3, 5, Dos.tr("solar.conditions", eventName(data.getString("event")), data.getInt("level")), DosColours.TEXT_FG, bg);
		g.put(3, 6, Dos.tr("solar.noise", data.getInt("noise")), fg, bg);
		g.bar(3, 7, 34, data.getInt("noise") / 100.0D, data.getInt("noise") >= 70 ? DosColours.LIGHT_RED : DosColours.LIGHT_CYAN, bg);
		g.put(3, 8, Dos.tr(data.getBoolean("day") ? "solar.daytime" : "solar.nighttime"), fg, bg);
		g.put(3, 9, Dos.tr("solar.left", data.getInt("minutesLeft")), fg, bg);

		g.box(41, 3, 38, 9, false, Dos.tr("solar.bands"), DosColours.TEXT_FG, bg);
		g.put(43, 4, String.format(Locale.ROOT, "%-9s%-8s%-8s%s", Dos.tr("solar.col.band"), Dos.tr("solar.col.now"), Dos.tr("solar.col.day"), Dos.tr("solar.col.night")), head, bg);
		ListTag bands = data.getList("bands", Tag.TAG_COMPOUND);
		for(int i = 0; i < bands.size() && i < 6; i++) {
			CompoundTag row = bands.getCompound(i);
			int r = 5 + i;
			g.put(43, r, bandName(row.getString("band")), DosColours.TEXT_FG, bg);
			g.put(52, r, condition(row.getString("now")), conditionColour(row.getString("now")), bg);
			g.put(60, r, condition(row.getString("day")), conditionColour(row.getString("day")), bg);
			g.put(68, r, condition(row.getString("night")), conditionColour(row.getString("night")), bg);
		}

		g.box(1, 12, 78, 10, false, Dos.tr("solar.sky"), DosColours.TEXT_FG, bg);
		int r = 14;
		g.put(3, r++, Dos.tr("solar.vhf", Dos.tr("solar.vhf." + data.getString("vhf"))), fg, bg);
		String aurora = data.getString("aurora");
		int latitude = data.getInt("latitude");
		g.put(3, r++, aurora.equals("active") ? Dos.tr("solar.aurora_active", latitude) : aurora.equals("expected") ? Dos.tr("solar.aurora_expected", latitude) : Dos.tr("solar.aurora_none"),
				aurora.equals("none") ? fg : DosColours.LIGHT_GREEN, bg);
		g.put(3, r++, Dos.tr(data.getBoolean("sporadicE") ? "solar.es_open" : "solar.es_closed"), data.getBoolean("sporadicE") ? DosColours.LIGHT_CYAN : fg, bg);
		r++;
		if(data.contains("next")) {
			boolean blackout = data.getBoolean("blackoutNext");
			g.put(3, r++, Dos.tr("solar.next", eventName(data.getString("next")), data.getInt("nextMinutes")), blackout ? DosColours.LIGHT_RED : DosColours.TEXT_FG, bg);
			if(blackout)
				g.put(3, r, Dos.tr("solar.blackout_warning"), DosColours.LIGHT_RED, bg);
		}
		else
			g.put(3, r, Dos.tr("solar.next_unknown"), fg, bg);
	}

	private void renderForecast(TextGrid g, CompoundTag data) {
		int fg = DosColours.BODY_FG, bg = DosColours.BODY_BG;
		CompoundTag here = data.getCompound("now");
		g.box(1, 3, 78, 5, false, Dos.tr("solar.here"), DosColours.TEXT_FG, bg);
		g.put(3, 4, Dos.fit(here.getString("summary"), 74), DosColours.TEXT_FG, bg);
		g.put(3, 5, Dos.fit(Dos.tr("solar.here_values", Math.round(here.getDouble("temp")), Math.round(here.getDouble("temp") * 9.0D / 5.0D + 32.0D),
				Math.round(here.getDouble("pressure")), Math.round(here.getDouble("humidity")), Math.round(here.getDouble("wind")), Math.round(here.getDouble("windFrom")) % 360), 74), fg, bg);
		String hazards = here.getString("hazards");
		if(!hazards.isEmpty())
			g.put(3, 6, Dos.fit(Dos.tr("solar.warning", hazards), 74), DosColours.LIGHT_RED, bg);

		List<String> lines = ComputerServer.strings(data.getList("lines", Tag.TAG_STRING));
		int rows = 13;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - rows)));
		g.box(1, 8, 78, rows + 2, false, Dos.tr(data.getBoolean("local") ? "solar.report_local" : "solar.report"), DosColours.TEXT_FG, bg);
		for(int i = 0; i < rows && scroll + i < lines.size(); i++) {
			String line = lines.get(scroll + i);
			int colour = line.startsWith("WARNING") || line.contains("TORNADO") ? DosColours.LIGHT_RED
					: line.equals("OUTLOOK") || line.startsWith("STORMS WITHIN") ? DosColours.YELLOW : DosColours.TEXT_FG;
			g.put(3, 9 + i, Dos.fit(line, 74), colour, bg);
		}
		if(lines.size() > rows)
			g.put(66, 8 + rows + 1, Dos.tr("solar.more", scroll + 1, lines.size()), DosColours.DARK_GREY, bg);
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
				forecastPage = !forecastPage;
				scroll = 0;
				refresh();
			}
			case GLFW.GLFW_KEY_F5 -> refresh();
			case GLFW.GLFW_KEY_F6 -> print();
			case GLFW.GLFW_KEY_F7 -> saveText();
			case GLFW.GLFW_KEY_UP -> scroll = Math.max(0, scroll - 1);
			case GLFW.GLFW_KEY_DOWN -> scroll++;
			case GLFW.GLFW_KEY_PAGE_UP -> scroll = Math.max(0, scroll - 10);
			case GLFW.GLFW_KEY_PAGE_DOWN -> scroll += 10;
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(row != 1)
			return false;
		boolean wantForecast = col > 3 + Dos.tr("solar.tab_solar").length();
		if(wantForecast != forecastPage) {
			forecastPage = wantForecast;
			scroll = 0;
			refresh();
		}
		return true;
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		scroll = Math.max(0, scroll - (int)Math.signum(amount) * 2);
		return true;
	}
}
