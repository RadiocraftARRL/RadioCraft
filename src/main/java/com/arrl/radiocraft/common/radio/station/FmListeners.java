package com.arrl.radiocraft.common.radio.station;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The radios switched on and tuned to the FM broadcast band, and where (2026-11-15, the maintainer: an FM station's
 * internet stream should only hold one of the server's few stream slots while someone may hear it, yet be ready as a
 * listener tunes towards it). Each such radio reports itself every few ticks ({@code RadioBlockEntity}); a station
 * asks whether any reported lately is in range and tuned near it. Server thread.
 */
public final class FmListeners {

	/** The FM broadcast band with a little either side, Hz. */
	public static final float LOW = 87_000_000.0F, HIGH = 109_000_000.0F;
	/** How long a report counts, ticks. */
	private static final long FRESH_TICKS = 100L;

	private record Heard(float hz, long tick) {}

	private static final Map<ResourceKey<Level>, Map<BlockPos, Heard>> HEARD = new ConcurrentHashMap<>();

	private FmListeners() {}

	/** True if {@code hz} is on (or just off) the FM broadcast band. */
	public static boolean onBand(float hz) {
		return hz >= LOW && hz <= HIGH;
	}

	/** The radio at {@code pos} is on and tuned to {@code hz} (in the band) at game time {@code now}. */
	public static void heard(Level level, BlockPos pos, float hz, long now) {
		HEARD.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>()).put(pos.immutable(), new Heard(hz, now));
	}

	/** The radio at {@code pos} went away or off. */
	public static void gone(Level level, BlockPos pos) {
		Map<BlockPos, Heard> map = HEARD.get(level.dimension());
		if(map != null)
			map.remove(pos);
	}

	/**
	 * True if a radio reported lately is within {@code reach} blocks of {@code at} and tuned within {@code window} Hz of
	 * {@code hz}. Old reports are dropped as it looks.
	 */
	public static boolean anyNear(Level level, BlockPos at, float hz, float window, double reach, long now) {
		Map<BlockPos, Heard> map = HEARD.get(level.dimension());
		if(map == null)
			return false;
		boolean found = false;
		for(Iterator<Map.Entry<BlockPos, Heard>> it = map.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<BlockPos, Heard> e = it.next();
			Heard heard = e.getValue();
			if(now - heard.tick() > FRESH_TICKS || now < heard.tick()) {
				it.remove();
				continue;
			}
			if(!found && Math.abs(heard.hz() - hz) <= window && e.getKey().distSqr(at) <= reach * reach)
				found = true;
		}
		return found;
	}

	/** Forgets everything (server stop, tests). */
	public static void clear() {
		HEARD.clear();
	}
}
