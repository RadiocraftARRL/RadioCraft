package com.arrl.radiocraft.common.photo;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.commands.PhotoCommands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * The world's record of camera photos ({@link PhotoAlbums}), saved with the overworld: what {@code camera_photo_cap}
 * counts (0, the default, is no cap). Players in creative mode and operators (permission level 2) are not counted
 * against the cap, though their photos are still recorded. A player at the cap is offered their old photos to delete
 * ({@link PhotoCommands}); deleting blanks the photo's map on the server, so every copy of it (frames, chests, faxed
 * copies sharing the map) shows an empty sheet and says "Photo deleted". Nothing is ever deleted without the owner
 * confirming it.
 */
public final class PhotoLedger extends SavedData {

	private static final String DATA_NAME = Radiocraft.MOD_ID + "_photos";

	private final PhotoAlbums albums = new PhotoAlbums();

	public static PhotoLedger get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(PhotoLedger::new, PhotoLedger::load), DATA_NAME);
	}

	public PhotoAlbums albums() {
		return albums;
	}

	/** True if {@code player} is not held to the cap: creative mode or an operator. */
	public static boolean exempt(ServerPlayer player) {
		return player.getAbilities().instabuild || player.hasPermissions(2);
	}

	/** True (and the delete offer sent) if {@code player} is at the cap and may take no more photos. */
	public static boolean refuseOverCap(ServerPlayer player) {
		int cap = RadiocraftServerConfig.CAMERA_PHOTO_CAP.get();
		if(cap <= 0 || exempt(player) || player.getServer() == null)
			return false;
		int count = get(player.getServer()).albums.count(player.getUUID());
		if(!PhotoAlbums.atCap(count, cap))
			return false;
		PhotoCommands.offerDeletion(player, count, cap);
		return true;
	}

	/** Records {@code player}'s new photo on {@code mapId}. */
	public static void record(ServerPlayer player, MapId mapId) {
		if(player.getServer() == null)
			return;
		PhotoLedger ledger = get(player.getServer());
		long day = player.getServer().overworld().getDayTime() / 24000L;
		ledger.albums.add(player.getUUID(), new PhotoAlbums.Photo(mapId.id(), day, System.currentTimeMillis()));
		ledger.setDirty();
	}

	/**
	 * Deletes {@code owner}'s photo on map {@code mapId}: out of their album, and its picture blanked (every copy shows
	 * an empty sheet). False if it is not theirs.
	 */
	public boolean delete(ServerLevel level, UUID owner, int mapId) {
		if(!albums.remove(owner, mapId))
			return false;
		setDirty();
		MapItemSavedData data = level.getMapData(new MapId(mapId));
		if(data != null) {
			for(int z = 0; z < PhotoImage.SIZE; z++)
				for(int x = 0; x < PhotoImage.SIZE; x++)
					data.updateColor(x, z, (byte)0); // Through updateColor, so players holding it see it go.
			data.setDirty();
		}
		return true;
	}

	/** True if {@code data}'s picture is all transparent: a deleted photo. */
	public static boolean isBlank(MapItemSavedData data) {
		for(byte colour : data.colors)
			if(colour != 0)
				return false;
		return true;
	}

	private static PhotoLedger load(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		PhotoLedger ledger = new PhotoLedger();
		CompoundTag players = tag.getCompound("players");
		for(String key : players.getAllKeys()) {
			UUID player;
			try {
				player = UUID.fromString(key);
			}
			catch(IllegalArgumentException e) {
				continue;
			}
			for(Tag entry : players.getList(key, Tag.TAG_COMPOUND)) {
				CompoundTag photo = (CompoundTag)entry;
				ledger.albums.add(player, new PhotoAlbums.Photo(photo.getInt("map"), photo.getLong("day"), photo.getLong("at")));
			}
		}
		return ledger;
	}

	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		CompoundTag players = new CompoundTag();
		for(UUID player : albums.owners()) {
			ListTag list = new ListTag();
			for(PhotoAlbums.Photo photo : albums.photos(player)) {
				CompoundTag entry = new CompoundTag();
				entry.putInt("map", photo.mapId());
				entry.putLong("day", photo.day());
				entry.putLong("at", photo.takenAt());
				list.add(entry);
			}
			players.put(player.toString(), list);
		}
		tag.put("players", players);
		return tag;
	}
}
