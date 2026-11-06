package com.arrl.radiocraft.common.computer.license;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every player's License Study progress (computer apps 2), server wide: player, then pool id, then the
 * {@link StudyDeck}. Kept with the world ({@code data/radiocraft_license_study.dat} in the overworld), not on a
 * computer's disk, so a player's cards follow them to any computer and survive a computer being broken, and two
 * players at one computer each study their own. Question ids a pool no longer has are kept and ignored.
 */
public final class LicenseProgress extends SavedData {

	public static final String NAME = "radiocraft_license_study";

	private final Map<UUID, Map<String, StudyDeck>> decks = new HashMap<>();

	public static SavedData.Factory<LicenseProgress> factory() {
		return new SavedData.Factory<>(LicenseProgress::new, LicenseProgress::load, null);
	}

	public static LicenseProgress get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
	}

	/** {@code player}'s deck for {@code pool}, made empty if they have none (call {@link #setDirty} after changing it). */
	public StudyDeck deck(UUID player, String pool) {
		return decks.computeIfAbsent(player, p -> new HashMap<>()).computeIfAbsent(pool, p -> new StudyDeck());
	}

	/** True if {@code player} has answered anything in {@code pool}. */
	public boolean has(UUID player, String pool) {
		Map<String, StudyDeck> mine = decks.get(player);
		return mine != null && mine.containsKey(pool) && !mine.get(pool).isEmpty();
	}

	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		CompoundTag players = new CompoundTag();
		decks.forEach((player, pools) -> {
			CompoundTag mine = new CompoundTag();
			pools.forEach((pool, deck) -> {
				if(!deck.isEmpty())
					mine.put(pool, deck.save());
			});
			if(!mine.isEmpty())
				players.put(player.toString(), mine);
		});
		tag.put("players", players);
		return tag;
	}

	public static LicenseProgress load(CompoundTag tag, HolderLookup.Provider registries) {
		LicenseProgress progress = new LicenseProgress();
		CompoundTag players = tag.getCompound("players");
		for(String key : players.getAllKeys()) {
			UUID player;
			try {
				player = UUID.fromString(key);
			}
			catch(IllegalArgumentException e) {
				continue;
			}
			CompoundTag mine = players.getCompound(key);
			Map<String, StudyDeck> pools = new HashMap<>();
			for(String pool : mine.getAllKeys())
				pools.put(pool, StudyDeck.load(mine.getCompound(pool)));
			progress.decks.put(player, pools);
		}
		return progress;
	}
}
