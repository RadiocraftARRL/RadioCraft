package com.arrl.radiocraft.api.antenna;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.radio.antenna.StaticAntenna;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The {@link IAntennaType} registry. When an antenna feed point loads or its build changes, RadioCraft asks the types
 * in turn whether the blocks there make one of them ({@link #match}): first the priority types, then the ordinary ones
 * in the order they were registered, last the fallbacks. The first that says yes is the antenna.
 *
 * <p>Register at common setup ({@code FMLCommonSetupEvent}, inside {@code enqueueWork}) on both sides. RadioCraft's
 * own types are registered before any other mod's common setup runs. A type whose builds could also look like a stock
 * antenna (a dipole made of your own wire, say) should use {@link #registerPriorityType} so it is asked first.</p>
 */
public class AntennaTypes {

    /** Not needed (everything here is static); kept so the public API does not change. */
    public AntennaTypes() {}

	private static final Map<ResourceLocation, IAntennaType<?>> REGISTRY = new LinkedHashMap<>(); // Matched in registration order.
	private static final List<IAntennaType<?>> FALLBACKS = new ArrayList<>();
	private static final List<IAntennaType<?>> PRIORITY = new CopyOnWriteArrayList<>();

	/**
	 * Register an {@link IAntennaType}. A duplicate id is logged and ignored.
     * @param <C> The type of {@link IAntennaType} being registered.
	 * @param type The {@link IAntennaType} to be registered
	 * @return The {@link IAntennaType} after registration.
	 */
	public static <C extends IAntennaType<?>> C registerType(C type) {
		ResourceLocation id = type.getId();
		if(REGISTRY.containsKey(id))
            Radiocraft.LOGGER.warn("Attempted to register a duplicate Antenna Type of ID {}", id.toString());
		else
			REGISTRY.put(id, type);

		return type;
	}

	/**
	 * Register an {@link IAntennaType} that is asked before every ordinary type, so a build that would also match a
	 * stock type (an improvised wire antenna, a dipole) is this type instead.
	 *
	 * @param <C> The type of {@link IAntennaType} being registered.
	 * @param type The {@link IAntennaType} to be registered
	 * @return The {@link IAntennaType} after registration.
	 * @since 1.12.0
	 */
	public static <C extends IAntennaType<?>> C registerPriorityType(C type) {
		if(!REGISTRY.containsKey(type.getId())) {
			registerType(type);
			PRIORITY.add(type);
		}
		else
			Radiocraft.LOGGER.warn("Attempted to register a duplicate Antenna Type of ID {}", type.getId());
		return type;
	}

	/**
	 * Register an {@link IAntennaType} that is only tried when no ordinary type matches: a catch-all for builds that are
	 * not any particular antenna.
	 * @param <C> The type of {@link IAntennaType} being registered.
	 * @param type The {@link IAntennaType} to be registered
	 * @return The {@link IAntennaType} after registration.
	 */
	public static <C extends IAntennaType<?>> C registerFallbackType(C type) {
		registerType(type);
		FALLBACKS.add(type);
		return type;
	}

	/**
	 * Grab an instance of {@link IAntennaType} by it's ID.
	 * @param id The {@link ResourceLocation} representing the desired {@link IAntennaType}, it should match the location
	 *           returned by {@link IAntennaType#getId()}
	 * @return The {@link IAntennaType} registered under id, otherwise null if none were found.
	 */
	public static IAntennaType<?> getType(ResourceLocation id) {
		return REGISTRY.get(id);
	}

	/**
	 * Every registered type, in registration order.
	 *
	 * @return a read-only view
	 * @since 1.12.0
	 */
	public static Collection<IAntennaType<?>> getTypes() {
		return Collections.unmodifiableCollection(REGISTRY.values());
	}

	/**
	 * A readable name for a type, from its id: "radiocraft:inverted_v" becomes "Inverted V".
	 *
	 * @param type the type
	 * @return the name, as the antenna analyzer and Jade show it
	 */
	public static String displayName(IAntennaType<?> type) {
		StringBuilder name = new StringBuilder();
		for(String word : type.getId().getPath().split("_")) {
			if(word.isEmpty())
				continue;
			if(!name.isEmpty())
				name.append(' ');
			name.append(word.length() <= 2 ? word.toUpperCase(Locale.ROOT) : Character.toUpperCase(word.charAt(0)) + word.substring(1));
		}
		return name.toString();
	}

	/**
	 * Attempt to check for a valid {@link StaticAntenna} at the provided location: the priority types first, then the
	 * ordinary ones in registration order, then the fallbacks.
	 * @param level The {@link Level} to check.
	 * @param pos The {@link BlockPos} the antenna is located at.
	 * @return An {@link StaticAntenna} of the first matching type, otherwise null if there was no valid antenna.
	 */
	public static StaticAntenna<?> match(Level level, BlockPos pos) {
		for(IAntennaType<?> type : PRIORITY) {
			StaticAntenna<?> antenna = type.match(level, pos);
			if(antenna != null)
				return antenna;
		}
		for(IAntennaType<?> type : REGISTRY.values()) {
			if(FALLBACKS.contains(type) || PRIORITY.contains(type))
				continue;
			StaticAntenna<?> antenna = type.match(level, pos);
			if(antenna != null)
				return antenna;
		}
		for(IAntennaType<?> type : FALLBACKS) {
			StaticAntenna<?> antenna = type.match(level, pos);
			if(antenna != null)
				return antenna;
		}
		return null;
	}

}
