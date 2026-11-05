package com.arrl.radiocraft.common.radio.adsb;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every ADS-B beacon on the air, by level: the latest report of each, put here by the beacon about once a second and
 * taken away when it goes off, is broken or unloads. Receivers read it (only those switched on, once a second), so
 * nothing ever scans the world for aircraft. Server thread only.
 */
public final class AdsbAirspace {

	/** A report older than this (ticks) is off the air: the beacon stopped without saying so. */
	public static final long STALE_TICKS = 60L;

	private static final Map<ResourceKey<Level>, Map<Long, AdsbTarget>> LEVELS = new HashMap<>();

	private AdsbAirspace() {}

	/** The beacon at {@code beacon} sent {@code report}. */
	public static void publish(Level level, BlockPos beacon, AdsbTarget report) {
		LEVELS.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(beacon.asLong(), report);
	}

	/** The beacon at {@code beacon} is off the air. */
	public static void withdraw(Level level, BlockPos beacon) {
		Map<Long, AdsbTarget> map = LEVELS.get(level.dimension());
		if(map != null)
			map.remove(beacon.asLong());
	}

	/** The latest reports in {@code level}, read only; some may be stale (see {@link #isFresh}). */
	public static Collection<AdsbTarget> reports(Level level) {
		Map<Long, AdsbTarget> map = LEVELS.get(level.dimension());
		return map == null ? List.of() : java.util.Collections.unmodifiableCollection(map.values());
	}

	/** True if {@code report} was sent within {@link #STALE_TICKS} of {@code now}. */
	public static boolean isFresh(AdsbTarget report, long now) {
		return report.time() <= now && now - report.time() <= STALE_TICKS;
	}

	/** Beacons on the air in {@code level} (fresh reports). */
	public static int count(Level level) {
		long now = level.getGameTime();
		int count = 0;
		for(AdsbTarget report : reports(level))
			if(isFresh(report, now))
				count++;
		return count;
	}

	/** Forgets everything: the server stopped (a single player world closed). */
	public static void clear() {
		LEVELS.clear();
	}
}
