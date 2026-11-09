package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.Radiocraft;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * A CB radio's front panel as data (2026-11-08): the layout JSON in {@code assets/radiocraft/gui_layouts/} says where
 * every knob, switch, lamp, meter and window is; the background PNG (drawn from the same JSON by
 * {@code tools/cb_gui_assets.py}, or painted by hand) has everything that does not move; and the shared widget sheet
 * {@code textures/gui/cb/cb_widgets.png} has the moving parts. Only the meter needles, the numbers and the portable's
 * pulled-out whip are drawn in code. To move a control, edit the JSON and rerun the script.
 * <p>
 * Widget types: {@code knob} (centre x, y; {@code size} one of {@link #KNOBS}, or {@code none} with a {@code w} x
 * {@code h} hit box; {@code control}, and {@code ring} for a concentric outer ring), {@code toggle} (centre; a
 * {@code key}, {@code invert} if "up" means off), {@code lamp} (centre; {@code lights}, {@code colour}),
 * {@code meter} (box; {@code reads}), {@code digits} (box; {@code shows}, {@code colour}, {@code back},
 * {@code back_lit}, {@code back_off}), {@code light} (the portable's LIGHT button, box), {@code ptt} (a mic jack,
 * centre and {@code r}; or {@code side} with a box: the portable's push-to-talk bar).
 */
public final class CbPanel {

	public static final ResourceLocation SHEET = Radiocraft.id("textures/gui/cb/cb_widgets.png");
	static final int SHEET_W = 512, SHEET_H = 192, FRAMES = 11;
	/** Knob kind: {sheet y, frame size}; the eleven frames (0%, 10%, ... 100%) run along x. As tools/cb_gui_assets.py. */
	static final java.util.Map<String, int[]> KNOBS = java.util.Map.of("small", new int[] {0, 20}, "large", new int[] {20, 30},
			"chrome_small", new int[] {50, 22}, "chrome_large", new int[] {72, 32}, "chrome_xl", new int[] {104, 40});
	static final int TOGGLE_Y = 144, TOGGLE_W = 8, TOGGLE_H = 16, LAMP_Y = 160, LAMP = 6, LIGHT_X = 24, LIGHT_Y = 160, LIGHT = 10;
	static final int PTT_X = 448, PTT_Y = 0, PTT_W = 6, PTT_H = 50, JACK_X = 464, JACK_Y = 0, JACK = 26;
	static final int NEEDLE = 0xFF101010, WHIP = 0xFFD8DCE0;

	public record Widget(String type, int x, int y, int w, int h, int r, String size, String control, String ring, String key, boolean invert,
						 String reads, String shows, String lights, String colour, int textColour, int back, int backLit, int backOff, boolean side, String label) {

		int knobSize() {
			int[] kind = KNOBS.get(size);
			return kind == null ? 0 : kind[1];
		}
	}

	/** What the panel shows, from the radio. */
	public interface Source {
		/** Where a knob is, 0 to 1. */
		double knob(String control);

		/** True if a switch is up (before {@code invert}). */
		boolean toggle(String key);

		/** A meter's reading, 0 to 1. */
		double meter(String reads);

		/** A window's text, empty for dark. */
		String digits(String shows);

		boolean lamp(String lights);

		boolean transmitting();

		boolean on();

		/** The portable's LIGHT button is held. */
		default boolean lightHeld() {
			return false;
		}

		/** The PTT (mic jack or bar) is held on the screen. */
		default boolean pttHeld() {
			return false;
		}
	}

	public final ResourceLocation background;
	public final int width, height;
	public final List<Widget> widgets;
	/** The portable's whip: {x, bottom y, width}, drawn up to the top of the screen while it is on; null for none. */
	@Nullable
	public final int[] whip;

	private CbPanel(ResourceLocation background, int width, int height, List<Widget> widgets, @Nullable int[] whip) {
		this.background = background;
		this.width = width;
		this.height = height;
		this.widgets = widgets;
		this.whip = whip;
	}

	/** Reads {@code gui_layouts/<name>.json}; an empty panel (and a warning) if it cannot. */
	public static CbPanel load(String name) {
		ResourceLocation file = Radiocraft.id("gui_layouts/" + name + ".json");
		try(Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(file)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			List<Widget> widgets = new ArrayList<>();
			for(JsonElement element : json.getAsJsonArray("widgets")) {
				JsonObject o = element.getAsJsonObject();
				widgets.add(new Widget(str(o, "type", ""), num(o, "x", 0), num(o, "y", 0), num(o, "w", 0), num(o, "h", 0), num(o, "r", 8),
						str(o, "size", "small"), str(o, "control", "NONE"), str(o, "ring", ""), str(o, "key", "NONE"),
						o.has("invert") && o.get("invert").getAsBoolean(), str(o, "reads", ""), str(o, "shows", ""), str(o, "lights", "none"),
						str(o, "colour", "red"), colour(o, "colour", 0xFFFF3020), colour(o, "back", 0), colour(o, "back_lit", 0), colour(o, "back_off", 0),
						o.has("side") && o.get("side").getAsBoolean(), str(o, "label", "")));
			}
			int[] whip = null;
			if(json.has("whip")) {
				JsonArray array = json.getAsJsonArray("whip");
				whip = new int[] {array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt()};
			}
			return new CbPanel(ResourceLocation.parse(str(json, "background", "")), num(json, "width", 176), num(json, "height", 100), List.copyOf(widgets), whip);
		}
		catch(Exception e) {
			Radiocraft.LOGGER.warn("Cannot read the CB panel layout {}: {}", file, e.toString());
			return new CbPanel(Radiocraft.id("textures/gui/cb/cb_widgets.png"), 176, 100, List.of(), null);
		}
	}

	private static String str(JsonObject o, String key, String fallback) {
		return o.has(key) ? o.get(key).getAsString() : fallback;
	}

	private static int num(JsonObject o, String key, int fallback) {
		return o.has(key) ? o.get(key).getAsInt() : fallback;
	}

	/** "#RRGGBB" as an opaque ARGB colour; {@code fallback} if absent or a name ("red"). */
	private static int colour(JsonObject o, String key, int fallback) {
		String s = str(o, key, "");
		if(!s.startsWith("#") || s.length() != 7)
			return fallback;
		return 0xFF000000 | Integer.parseInt(s.substring(1), 16);
	}

	// -------------------- DRAWING --------------------

	public void render(GuiGraphics g, Font font, int left, int top, Source source) {
		if(whip != null && source.on()) // The whip pulled out, up off the top of the screen.
			g.fill(left + whip[0], 0, left + whip[0] + whip[2], top + whip[1], WHIP);
		g.blit(background, left, top, 0, 0, width, height, width, height);
		for(Widget w : widgets) {
			int x = left + w.x(), y = top + w.y();
			switch(w.type()) {
				case "knob" -> {
					int[] kind = KNOBS.get(w.size());
					if(kind == null)
						break;
					int frame = (int)Math.round(Math.max(0.0D, Math.min(1.0D, source.knob(w.control()))) * (FRAMES - 1));
					g.blit(SHEET, x - kind[1] / 2, y - kind[1] / 2, frame * kind[1], kind[0], kind[1], kind[1], SHEET_W, SHEET_H);
				}
				case "toggle" -> {
					boolean up = source.toggle(w.key()) != w.invert() || "NONE".equals(w.key());
					g.blit(SHEET, x - TOGGLE_W / 2, y - TOGGLE_H / 2, up ? 0 : TOGGLE_W, TOGGLE_Y, TOGGLE_W, TOGGLE_H, SHEET_W, SHEET_H);
				}
				case "lamp" -> {
					if(!source.lamp(w.lights()))
						break;
					int u = switch(w.colour()) {
						case "green" -> 2 * LAMP;
						case "amber" -> 3 * LAMP;
						case "green_red" -> source.transmitting() ? LAMP : 2 * LAMP;
						default -> LAMP;
					};
					g.blit(SHEET, x - LAMP / 2, y - LAMP / 2, u, LAMP_Y, LAMP, LAMP, SHEET_W, SHEET_H);
				}
				case "meter" -> needle(g, x, y, w.w(), w.h(), source.meter(w.reads()));
				case "digits" -> {
					int back = !source.on() && w.backOff() != 0 ? w.backOff() : source.on() && source.lightHeld() && w.backLit() != 0 ? w.backLit() : w.back();
					if(back != 0)
						g.fill(x, y, x + w.w(), y + w.h(), back);
					String text = source.digits(w.shows());
					if(!text.isEmpty()) {
						float scale = Math.min(w.w() * 0.88F / font.width(text), w.h() * 0.82F / (font.lineHeight - 1));
						g.pose().pushPose();
						g.pose().translate(x + (w.w() - font.width(text) * scale) / 2.0F, y + (w.h() - (font.lineHeight - 2) * scale) / 2.0F, 0.0F);
						g.pose().scale(scale, scale, 1.0F);
						g.drawString(font, text, 0, 0, w.textColour(), false);
						g.pose().popPose();
					}
				}
				case "light" -> g.blit(SHEET, x, y, LIGHT_X + (source.lightHeld() ? LIGHT : 0), LIGHT_Y, LIGHT, LIGHT, SHEET_W, SHEET_H);
				case "ptt" -> {
					if(w.side())
						g.blit(SHEET, x, y, PTT_X + (source.pttHeld() ? 8 : 0), PTT_Y, PTT_W, PTT_H, SHEET_W, SHEET_H);
					else if(source.pttHeld())
						g.blit(SHEET, x - JACK / 2, y - JACK / 2, JACK_X, JACK_Y, JACK, JACK, SHEET_W, SHEET_H);
				}
				default -> {}
			}
		}
	}

	/** The scale's reach, as tools/cb_gui_assets.py draws it. */
	static int meterRadius(int w, int h) {
		return (int)Math.min(h - 14, (w / 2.0D - 4) / 0.866D);
	}

	/** A needle from the middle of the meter's foot, 210 degrees (empty) round to 330 (full). */
	private static void needle(GuiGraphics g, int x, int y, int w, int h, double value) {
		double cx = x + w / 2.0D, cy = y + h - 3;
		double a = Math.toRadians(210.0D + 120.0D * Math.max(0.0D, Math.min(1.0D, value)));
		int r = meterRadius(w, h) + 1;
		for(int i = 2; i <= r; i++) {
			int px = (int)Math.floor(cx + Math.cos(a) * i), py = (int)Math.floor(cy + Math.sin(a) * i);
			g.fill(px, py, px + 1, py + 1, NEEDLE);
		}
	}

	// -------------------- WHAT IS UNDER THE MOUSE --------------------

	/** The widget under the mouse that does something (or shows a tooltip), null for none. */
	@Nullable
	public Widget at(double mx, double my, int left, int top) {
		for(Widget w : widgets) {
			double dx = mx - (left + w.x()), dy = my - (top + w.y());
			boolean hit = switch(w.type()) {
				case "knob" -> w.knobSize() == 0 ? Math.abs(dx) <= w.w() / 2.0D && Math.abs(dy) <= w.h() / 2.0D
						: Math.sqrt(dx * dx + dy * dy) <= w.knobSize() / 2.0D + (w.ring().isEmpty() ? 2 : 6);
				case "toggle" -> Math.abs(dx) <= 6 && Math.abs(dy) <= 10;
				case "ptt" -> w.side() ? dx >= 0 && dx < w.w() && dy >= 0 && dy < w.h() : dx * dx + dy * dy <= w.r() * w.r();
				case "light", "meter", "digits" -> dx >= 0 && dx < w.w() && dy >= 0 && dy < w.h();
				case "lamp" -> Math.abs(dx) <= 4 && Math.abs(dy) <= 4;
				default -> false;
			};
			if(hit)
				return w;
		}
		return null;
	}

	/** The control a knob click works: its {@code ring} if the mouse is on the outer ring, else its {@code control}. */
	public static String knobControl(Widget w, double mx, double my, int left, int top) {
		if(w.ring().isEmpty())
			return w.control();
		double dx = mx - (left + w.x()), dy = my - (top + w.y());
		return Math.sqrt(dx * dx + dy * dy) > w.knobSize() / 2.0D ? w.ring() : w.control();
	}
}
