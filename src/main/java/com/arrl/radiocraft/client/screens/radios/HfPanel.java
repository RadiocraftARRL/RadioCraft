package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.Radiocraft;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * An HF radio's front panel (2026-11-09): a panel PNG with the moving parts left off, and a layout,
 * {@code assets/radiocraft/gui_layouts/hf_<radio>.json}, that says where each control is and how to draw its moving
 * part (knob pointers, switch handles, the mode lever, the dial's dimple, the meter's needle, lamps). The panels and
 * layouts are drawn by {@code tools/hf_gui_proposals/}; the format is {@code tools/hf_gui_proposals/RUNTIME_SPEC.md}.
 * Angles there are degrees, 0 straight up, clockwise.
 */
public final class HfPanel {

	/** One control: what it is called (what the screen wires it to), its type, its click area, and its drawing data. */
	public record Control(String name, String type, int x, int y, int w, int h, JsonObject json) {

		public boolean contains(double px, double py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}

		public double num(String key, double fallback) {
			JsonElement e = json.get(key);
			return e != null && e.isJsonPrimitive() ? e.getAsDouble() : fallback;
		}

		/** A "#RRGGBB" colour as ARGB, opaque. */
		public int colour(String key, int fallback) {
			JsonElement e = json.get(key);
			return e != null && e.isJsonPrimitive() ? parseColour(e.getAsString(), fallback) : fallback;
		}

		public List<String> strings(String key) {
			List<String> out = new ArrayList<>();
			if(json.get(key) instanceof JsonArray array)
				array.forEach(e -> out.add(e.getAsString()));
			return out;
		}

		public double[] nums(String key) {
			if(!(json.get(key) instanceof JsonArray array))
				return new double[0];
			double[] out = new double[array.size()];
			for(int i = 0; i < out.length; i++)
				out[i] = array.get(i).getAsDouble();
			return out;
		}

		/** The rectangles under {@code key} (a switch's handles), as {x, y, w, h}. */
		public List<int[]> rects(String key) {
			List<int[]> out = new ArrayList<>();
			if(json.get(key) instanceof JsonArray array)
				for(JsonElement e : array) {
					JsonArray r = e.getAsJsonArray();
					out.add(new int[] { r.get(0).getAsInt(), r.get(1).getAsInt(), r.get(2).getAsInt(), r.get(3).getAsInt() });
				}
			return out;
		}

		/** A button's "lit" rectangle, {x, y, w, h, colour}, or null for none. */
		@Nullable
		public int[] lit() {
			if(!(json.get("lit") instanceof JsonArray r) || r.size() < 5)
				return null;
			return new int[] { r.get(0).getAsInt(), r.get(1).getAsInt(), r.get(2).getAsInt(), r.get(3).getAsInt(), parseColour(r.get(4).getAsString(), 0xFFFFC040) };
		}
	}

	private final ResourceLocation texture;
	private final int width, height;
	private final List<Control> controls;

	private HfPanel(ResourceLocation texture, int width, int height, List<Control> controls) {
		this.texture = texture;
		this.width = width;
		this.height = height;
		this.controls = controls;
	}

	public ResourceLocation texture() {
		return texture;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public List<Control> controls() {
		return controls;
	}

	@Nullable
	public Control named(String name) {
		for(Control c : controls)
			if(c.name().equals(name))
				return c;
		return null;
	}

	/** The control under the panel point {@code px, py} that can be worked (not lamps, the display or the meter), or null. */
	@Nullable
	public Control at(double px, double py) {
		for(Control c : controls)
			if(c.contains(px, py) && !c.type().equals("lamp") && !c.type().equals("display"))
				return c;
		return null;
	}

	/** Loads {@code gui_layouts/hf_<radio>.json}, e.g. {@code hf_radio_20m}. */
	public static HfPanel load(String radio) {
		ResourceLocation location = Radiocraft.id("gui_layouts/hf_" + radio + ".json");
		try(Reader reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(location).openAsReader()) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			List<Control> controls = new ArrayList<>();
			for(JsonElement e : root.getAsJsonArray("controls")) {
				JsonObject c = e.getAsJsonObject();
				controls.add(new Control(c.get("name").getAsString(), c.get("type").getAsString(), c.get("x").getAsInt(), c.get("y").getAsInt(),
						c.get("w").getAsInt(), c.get("h").getAsInt(), c));
			}
			return new HfPanel(ResourceLocation.parse(root.get("texture").getAsString()), root.get("width").getAsInt(), root.get("height").getAsInt(), controls);
		}
		catch(Exception e) {
			throw new IllegalStateException("Cannot read the HF panel layout " + location, e);
		}
	}

	static int parseColour(String hex, int fallback) {
		try {
			String digits = hex.startsWith("#") ? hex.substring(1) : hex;
			return 0xFF000000 | Integer.parseInt(digits, 16);
		}
		catch(NumberFormatException e) {
			return fallback;
		}
	}
}
