package com.arrl.radiocraft.common.realworld;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The players who clicked "I hold a licence" on a Real-World Interface's screen (1.12 open-question fixes), kept in the
 * overworld's {@code radiocraft_real_world_licences} data. Once is enough, for every interface on the server.
 * <p>
 * <b>Who is talking.</b> The interface's sound card gets its receiver's mixed audio, which no longer says who spoke. So
 * the check is made earlier, on each frame as it reaches the interface's receiver: every voice frame on the air carries
 * the player who said it ({@code AntennaVoicePacket#getSourcePlayer}; a repeater, a parrot or a tape passing a player's
 * voice on keeps that player), and the interface drops a frame from a player who has not clicked, before it is mixed.
 * Frames from no player (the world's own stations, blocks with voice ids of their own) are not held back.
 * {@link #mayBridge} is that check; it runs on the voice chat thread, so the set is a concurrent one.
 */
public final class RealWorldLicences extends SavedData {

	public static final String SAVE_NAME = "radiocraft_real_world_licences";

	private final Set<UUID> acknowledged = ConcurrentHashMap.newKeySet();

	// The loaded data for the running server, so the voice thread never touches the data storage.
	@Nullable
	private static volatile RealWorldLicences current;
	@Nullable
	private static volatile MinecraftServer currentServer;
	/** Whether a voice id is a player's (online, or known to the server's profile cache); replaced by GameTests. */
	private static volatile Predicate<UUID> isPlayer = RealWorldLicences::knownPlayer;

	public static SavedData.Factory<RealWorldLicences> factory() {
		return new SavedData.Factory<>(RealWorldLicences::new, RealWorldLicences::load, null);
	}

	/** The data of {@code server}, loaded (and kept for the voice thread) on first use. Server thread. */
	public static RealWorldLicences of(MinecraftServer server) {
		RealWorldLicences data = current;
		if(data == null || currentServer != server) {
			data = server.overworld().getDataStorage().computeIfAbsent(factory(), SAVE_NAME);
			current = data;
			currentServer = server;
		}
		return data;
	}

	/** {@code player} clicked "I hold a licence". Server thread. */
	public static void acknowledge(MinecraftServer server, UUID player) {
		RealWorldLicences data = of(server);
		if(data.acknowledged.add(player))
			data.setDirty();
	}

	/** True if {@code player} has clicked "I hold a licence". Server thread. */
	public static boolean hasAcknowledged(MinecraftServer server, UUID player) {
		return of(server).acknowledged.contains(player);
	}

	/**
	 * True if a voice frame from {@code source} may go out through an interface: it is from no player, or from a player
	 * who has clicked "I hold a licence". Any thread; false for a player while the data is not loaded yet.
	 */
	public static boolean mayBridge(@Nullable UUID source) {
		if(source == null || !isPlayer.test(source))
			return true;
		RealWorldLicences data = current;
		return data != null && data.acknowledged.contains(source);
	}

	private static boolean knownPlayer(UUID id) {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if(server == null)
			return false;
		if(server.getPlayerList().getPlayer(id) != null)
			return true;
		return server.getProfileCache() != null && server.getProfileCache().get(id).isPresent();
	}

	/** GameTests: which voice ids count as players (null puts the real check back). */
	public static void setPlayerCheckForTesting(@Nullable Predicate<UUID> check) {
		isPlayer = check == null ? RealWorldLicences::knownPlayer : check;
	}

	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		ListTag list = new ListTag();
		for(UUID id : acknowledged)
			list.add(NbtUtils.createUUID(id));
		tag.put("acknowledged", list);
		return tag;
	}

	public static RealWorldLicences load(CompoundTag tag, HolderLookup.Provider registries) {
		RealWorldLicences data = new RealWorldLicences();
		for(Tag element : tag.getList("acknowledged", Tag.TAG_INT_ARRAY))
			data.acknowledged.add(NbtUtils.loadUUID(element));
		return data;
	}
}
