package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.Radiocraft;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where each radio's screen is, read from its block model: the element the maintainer names {@code DISPLAY} (in
 * Blockbench), on the model as drawn facing north. Premium and Cheap radios share their plain radio's model shape, so
 * they look up the plain one. Read on first use and again after every resource reload, so a re-export lines up.
 */
public final class RadioDisplays {

	/**
	 * A radio's screen in model pixels (0 to 16): its left and right as seen from the front ({@code x1} west, {@code x2}
	 * east, and the front faces north, so east is on the viewer's left), bottom and top, and the depth of its front
	 * face. {@code rise} lifts the whole radio when it hangs from a ceiling, as the blockstate's ceiling model does.
	 */
	public record Display(float x1, float x2, float y1, float y2, float front, float rise) {}

	private static final Map<String, Optional<Display>> CACHE = new ConcurrentHashMap<>();

	/**
	 * A front panel lamp painted on a radio's texture, in model pixels as {@link Display} (left and right as seen from
	 * the front, bottom and top, the depth of the face it is on): lit in {@code colour} while the radio transmits
	 * ({@code tx}) or while it hears a signal.
	 */
	public record Lamp(float x1, float x2, float y1, float y2, float front, int colour, boolean tx) {}

	/** The lamps of each radio model that has them; the texture holds them dark, the renderer lights them. */
	private static final Map<String, java.util.List<Lamp>> LAMPS = Map.of(
			"vhf_base_station", java.util.List.of(
					new Lamp(4.5F, 5.5F, 2.75F, 3.75F, 1.75F, 0xFFFF3B30, true), // TX, red, right of the display's left edge.
					new Lamp(6.5F, 7.5F, 2.75F, 3.75F, 1.75F, 0xFF3BFF6A, false)), // RX, green.
			"commercial_mobile", commercialLamps(),
			"cb_mobile_5875", java.util.List.of( // ON AIR: the TX_LED cube's face (maintainer's model, 2026-11-08).
					new Lamp(3.5F, 4.0F, 4.0F, 4.5F, 0.6F, 0xFFFF3B30, true),
					new Lamp(6.75F, 7.25F, 4.0F, 4.5F, 0.6F, 0xFFFFB020, false)), // AWI, amber: lit while a signal comes in.
			"cb_mobile_pc68", java.util.List.of( // The red and green LEDs painted beside the channel window.
					new Lamp(5.5F, 6.0F, 1.5F, 2.0F, 0.75F, 0xFFFF3B30, true),
					new Lamp(5.5F, 6.0F, 2.5F, 3.0F, 0.75F, 0xFF3BFF6A, false)),
			"low_band_mobile", commercialLamps(),
			"marine_radio", java.util.List.of( // TX and RX lamps right of the display (placeholder model, see ModelDesignBounty).
					new Lamp(2.75F, 3.75F, 3.0F, 3.75F, 3.75F, 0xFFFF3B30, true),
					new Lamp(2.75F, 3.75F, 1.5F, 2.25F, 3.75F, 0xFF3BFF6A, false)));

	/** The commercial mobiles' TX and RX lamps, right of the display (placeholder model, see ModelDesignBounty). */
	private static java.util.List<Lamp> commercialLamps() {
		return java.util.List.of(
				new Lamp(3.0F, 4.0F, 2.25F, 3.0F, 2.75F, 0xFFFF3B30, true),
				new Lamp(3.0F, 4.0F, 1.0F, 1.75F, 2.75F, 0xFF3BFF6A, false));
	}

	/** The lamps of the radio block {@code blockPath}, none if its model has none. */
	public static java.util.List<Lamp> lamps(String blockPath) {
		return LAMPS.getOrDefault(blockPath.replaceAll("_(pure|poor)$", ""), java.util.List.of());
	}

	private RadioDisplays() {}

	/** Forgets what was read, after a resource reload. */
	public static void clear() {
		CACHE.clear();
		NAMED.clear();
	}

	private static final Map<String, java.util.List<Display>> NAMED = new ConcurrentHashMap<>();

	/**
	 * Every element named {@code name} (any case) in the block model {@code blockPath}, in model pixels as
	 * {@link Display}, in the order the model has them; empty if none. The CB 5875 has two DISPLAY windows (the clock
	 * and the channel) and two SIG_TX_METER meters.
	 */
	public static java.util.List<Display> named(String blockPath, String name) {
		String model = blockPath.replaceAll("_(pure|poor)$", "");
		return NAMED.computeIfAbsent(model + "#" + name, key -> readNamed(model, name));
	}

	private static java.util.List<Display> readNamed(String model, String name) {
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(Radiocraft.id("models/block/" + model + ".json"));
		if(resource.isEmpty())
			return java.util.List.of();
		try(Reader reader = resource.get().openAsReader()) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			java.util.List<Display> out = new java.util.ArrayList<>();
			float top = 0.0F;
			for(JsonElement element : json.getAsJsonArray("elements"))
				top = Math.max(top, element.getAsJsonObject().getAsJsonArray("to").get(1).getAsFloat());
			for(JsonElement element : json.getAsJsonArray("elements")) {
				JsonObject object = element.getAsJsonObject();
				if(!object.has("name") || !object.get("name").getAsString().equalsIgnoreCase(name))
					continue;
				var from = object.getAsJsonArray("from");
				var to = object.getAsJsonArray("to");
				out.add(new Display(from.get(0).getAsFloat(), to.get(0).getAsFloat(), from.get(1).getAsFloat(), to.get(1).getAsFloat(),
						Math.min(from.get(2).getAsFloat(), to.get(2).getAsFloat()), 16.0F - Math.min(16.0F, top)));
			}
			return java.util.List.copyOf(out);
		}
		catch(Exception e) {
			Radiocraft.LOGGER.warn("Cannot read the {} elements of radio model {}: {}", name, model, e.toString());
			return java.util.List.of();
		}
	}

	/** The screen of the radio block {@code blockPath} ("hf_radio_20m_pure"), or null if its model has none. */
	@Nullable
	public static Display get(String blockPath) {
		String model = blockPath.replaceAll("_(pure|poor)$", "");
		return CACHE.computeIfAbsent(model, RadioDisplays::read).orElse(null);
	}

	private static Optional<Display> read(String model) {
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(Radiocraft.id("models/block/" + model + ".json"));
		if(resource.isEmpty())
			return Optional.empty();
		try(Reader reader = resource.get().openAsReader()) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			if(!json.has("elements"))
				return Optional.empty();
			float top = 0.0F;
			JsonObject display = null;
			for(JsonElement element : json.getAsJsonArray("elements")) {
				JsonObject object = element.getAsJsonObject();
				top = Math.max(top, object.getAsJsonArray("to").get(1).getAsFloat());
				if(object.has("name") && object.get("name").getAsString().equalsIgnoreCase("DISPLAY"))
					display = object;
			}
			if(display == null)
				return Optional.empty();
			var from = display.getAsJsonArray("from");
			var to = display.getAsJsonArray("to");
			float rise = 16.0F - Math.min(16.0F, top); // The ceiling model is raised so its top meets the block above.
			return Optional.of(new Display(from.get(0).getAsFloat(), to.get(0).getAsFloat(), from.get(1).getAsFloat(), to.get(1).getAsFloat(),
					Math.min(from.get(2).getAsFloat(), to.get(2).getAsFloat()), rise));
		}
		catch(Exception e) {
			Radiocraft.LOGGER.warn("Cannot read the display of radio model {}: {}", model, e.toString());
			return Optional.empty();
		}
	}
}
