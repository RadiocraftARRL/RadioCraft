package com.arrl.radiocraft.common.radio.wxalert;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The alerts a receiver has already sounded for, by {@link WeatherAlert#key}, until they run out: a generator sends its
 * alerts again every minute, and the receiver flashes, beeps, speaks and pulses only for one it has not had yet. Pure
 * apart from saving.
 */
public final class AlertMemory {

	private final Map<Long, Long> known = new HashMap<>(); // Key -> the tick it runs out.

	/**
	 * Of {@code heard} (all affecting this receiver), those it has not sounded for yet, worst first; all of them are
	 * remembered (or have their end moved on) until they expire.
	 */
	public List<WeatherAlert> fresh(List<WeatherAlert> heard, long now) {
		prune(now);
		List<WeatherAlert> out = new ArrayList<>();
		for(WeatherAlert alert : heard) {
			if(alert.expired(now))
				continue;
			Long before = known.put(alert.key(), Math.max(alert.expires(), known.getOrDefault(alert.key(), 0L)));
			if(before == null)
				out.add(alert);
		}
		out.sort(java.util.Comparator.comparingInt(alert -> alert.event().ordinal()));
		return out;
	}

	/** Forgets alerts that have run out. */
	public void prune(long now) {
		known.values().removeIf(expires -> expires <= now);
	}

	public int size() {
		return known.size();
	}

	public void clear() {
		known.clear();
	}

	public ListTag save() {
		ListTag list = new ListTag();
		for(Map.Entry<Long, Long> entry : known.entrySet()) {
			CompoundTag tag = new CompoundTag();
			tag.putLong("key", entry.getKey());
			tag.putLong("expires", entry.getValue());
			list.add(tag);
		}
		return list;
	}

	public void load(ListTag list) {
		known.clear();
		for(Tag entry : list)
			if(entry instanceof CompoundTag tag)
				known.put(tag.getLong("key"), tag.getLong("expires"));
	}
}
