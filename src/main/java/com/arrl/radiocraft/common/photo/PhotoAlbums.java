package com.arrl.radiocraft.common.photo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who took which photos, oldest first: what {@code camera_photo_cap} counts. A photo stays counted until its owner
 * deletes it. No game classes, so it can be unit tested; {@link PhotoLedger} saves it with the world.
 */
public final class PhotoAlbums {

	/**
	 * One photo.
	 *
	 * @param mapId the photo's map id
	 * @param day the in-game day it was taken
	 * @param takenAt when it was taken, epoch milliseconds
	 */
	public record Photo(int mapId, long day, long takenAt) {}

	private final Map<UUID, List<Photo>> albums = new HashMap<>();

	/** True if a player with {@code count} photos may take no more under {@code cap} (0 = no cap). */
	public static boolean atCap(int count, int cap) {
		return cap > 0 && count >= cap;
	}

	public void add(UUID player, Photo photo) {
		albums.computeIfAbsent(player, ignored -> new ArrayList<>()).add(photo);
	}

	/** {@code player}'s photos, oldest first (a copy). */
	public List<Photo> photos(UUID player) {
		return List.copyOf(albums.getOrDefault(player, List.of()));
	}

	public int count(UUID player) {
		List<Photo> photos = albums.get(player);
		return photos == null ? 0 : photos.size();
	}

	/** {@code player}'s photo on map {@code mapId}, or null if it is not theirs. */
	public Photo find(UUID player, int mapId) {
		for(Photo photo : albums.getOrDefault(player, List.of()))
			if(photo.mapId() == mapId)
				return photo;
		return null;
	}

	/** Takes {@code player}'s photo on map {@code mapId} out of their album. False if it was not theirs. */
	public boolean remove(UUID player, int mapId) {
		List<Photo> photos = albums.get(player);
		if(photos == null || !photos.removeIf(photo -> photo.mapId() == mapId))
			return false;
		if(photos.isEmpty())
			albums.remove(player);
		return true;
	}

	public Set<UUID> owners() {
		return Set.copyOf(albums.keySet());
	}
}
