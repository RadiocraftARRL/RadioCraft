package com.arrl.radiocraft.api.weather;

import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.solar.BandCondition;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Where a band's sky-wave (skip) conditions come from, for a mod that runs its own space weather. RadioCraft's own solar
 * weather (data-driven solar events, picked at random) is used for every level and band no registered source answers
 * for. Register with {@link SolarSources#register}.
 *
 * <p>The condition scales skip only: {@code GOOD} 1, {@code FAIR} 0.6, {@code POOR} 0.3, {@code CLOSED} 0.05 of the
 * skip strength. Line of sight is never affected, and an aurora over either station still makes HF a step worse on top
 * of what a source says.</p>
 *
 * <p><b>Threading</b>: {@link #condition} is called on the voice chat thread for every signal, and on the server thread.
 * It must be quick, must not allocate much and must not touch the world: keep the conditions in a field your server
 * tick updates and read that.</p>
 *
 * <p>To add solar events to RadioCraft's own weather instead, a data pack can add event JSON files (see the wiki's
 * solar weather page); no code is needed.</p>
 *
 * @since 1.12.0
 */
public interface SolarSource {

	/**
	 * A short name for logs and debug output.
	 *
	 * @return the name
	 */
	String name();

	/**
	 * How {@code band} carries skip in {@code level} now.
	 *
	 * @param level the level (a server level on the server, the client level on the client)
	 * @param band the band, never null
	 * @param day true in the daytime
	 * @return the condition, or null to leave this band (or level) to the next source and finally RadioCraft's own
	 */
	@Nullable
	BandCondition condition(Level level, Band band, boolean day);
}
