package com.arrl.radiocraft.api.weather;

import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.solar.BandCondition;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The registered {@link SolarSource}s. The first that answers for a band in a level decides its condition; with none,
 * RadioCraft's own solar weather does.
 *
 * @since 1.12.0
 */
public final class SolarSources {

	private static final List<SolarSource> SOURCES = new CopyOnWriteArrayList<>();

	private SolarSources() {}

	/**
	 * Adds a source, asked after the ones registered before it. Call at common setup.
	 *
	 * @param source the source
	 */
	public static void register(SolarSource source) {
		SOURCES.add(source);
	}

	/**
	 * Takes a source away again (a mod that can be switched off in its config).
	 *
	 * @param source the source
	 */
	public static void unregister(SolarSource source) {
		SOURCES.remove(source);
	}

	/**
	 * What the registered sources say about {@code band} in {@code level}: the first answer, or null if none answers
	 * (RadioCraft's own solar weather then decides). Called by RadioCraft for every skip calculation.
	 *
	 * @param level the level
	 * @param band the band
	 * @param day true in the daytime
	 * @return the condition, or null
	 */
	@Nullable
	public static BandCondition condition(Level level, Band band, boolean day) {
		if(SOURCES.isEmpty() || band == null)
			return null;
		for(SolarSource source : SOURCES) {
			BandCondition condition = source.condition(level, band, day);
			if(condition != null)
				return condition;
		}
		return null;
	}
}
