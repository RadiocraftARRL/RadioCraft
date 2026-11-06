package com.arrl.radiocraft.common.advancements;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the advancements keep between sessions (the overworld's {@code radiocraft_advancements} data): advancements
 * earned by a player who was offline at the time (a QSL card they signed was picked up, another player's voice went
 * through their repeater), given at their next login; and each player's count of verified contacts (Contest Station).
 */
public final class AdvancementData extends SavedData {

	public static final String SAVE_NAME = "radiocraft_advancements";

	private final Map<UUID, Set<String>> pending = new HashMap<>();
	private final Map<UUID, Integer> contacts = new HashMap<>();

	public static SavedData.Factory<AdvancementData> factory() {
		return new SavedData.Factory<>(AdvancementData::new, AdvancementData::load, null);
	}

	public static AdvancementData of(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(factory(), SAVE_NAME);
	}

	/** Keeps event {@code event} for {@code player}, offline now. */
	public void addPending(UUID player, String event) {
		if(pending.computeIfAbsent(player, p -> new LinkedHashSet<>()).add(event))
			setDirty();
	}

	/** True if {@code event} waits for {@code player}'s login. */
	public boolean isPending(UUID player, String event) {
		Set<String> events = pending.get(player);
		return events != null && events.contains(event);
	}

	/** Takes the events waiting for {@code player} (empty if none). */
	public Set<String> takePending(UUID player) {
		Set<String> events = pending.remove(player);
		if(events == null)
			return Set.of();
		setDirty();
		return events;
	}

	/** Counts one more verified contact for {@code player}; returns the new count. */
	public int countContact(UUID player) {
		int count = contacts.merge(player, 1, Integer::sum);
		setDirty();
		return count;
	}

	public int contacts(UUID player) {
		return contacts.getOrDefault(player, 0);
	}

	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		CompoundTag waiting = new CompoundTag();
		for(Map.Entry<UUID, Set<String>> entry : pending.entrySet()) {
			ListTag list = new ListTag();
			for(String event : entry.getValue())
				list.add(StringTag.valueOf(event));
			waiting.put(entry.getKey().toString(), list);
		}
		tag.put("pending", waiting);
		CompoundTag counts = new CompoundTag();
		for(Map.Entry<UUID, Integer> entry : contacts.entrySet())
			counts.putInt(entry.getKey().toString(), entry.getValue());
		tag.put("contacts", counts);
		return tag;
	}

	public static AdvancementData load(CompoundTag tag, HolderLookup.Provider registries) {
		AdvancementData data = new AdvancementData();
		CompoundTag waiting = tag.getCompound("pending");
		for(String key : waiting.getAllKeys()) {
			UUID id = uuid(key);
			if(id == null)
				continue;
			Set<String> events = new LinkedHashSet<>();
			for(Tag element : waiting.getList(key, Tag.TAG_STRING))
				events.add(element.getAsString());
			if(!events.isEmpty())
				data.pending.put(id, events);
		}
		CompoundTag counts = tag.getCompound("contacts");
		for(String key : counts.getAllKeys()) {
			UUID id = uuid(key);
			if(id != null)
				data.contacts.put(id, counts.getInt(key));
		}
		return data;
	}

	private static UUID uuid(String text) {
		try {
			return UUID.fromString(text);
		}
		catch(IllegalArgumentException e) {
			return null;
		}
	}
}
