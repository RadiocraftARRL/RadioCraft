package com.arrl.radiocraft.common.photo;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who took which photos, and the per-player cap (camera, 1.12 step 4; no cap by default). */
class PhotoAlbumsTest {

	private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());
	private static final UUID BOB = UUID.nameUUIDFromBytes("bob".getBytes());

	@Test
	void capZeroMeansNoCap() {
		assertFalse(PhotoAlbums.atCap(0, 0));
		assertFalse(PhotoAlbums.atCap(100_000, 0));
		assertFalse(PhotoAlbums.atCap(59, 60));
		assertTrue(PhotoAlbums.atCap(60, 60));
		assertTrue(PhotoAlbums.atCap(61, 60));
	}

	@Test
	void albumsKeepOrderAndOwners() {
		PhotoAlbums albums = new PhotoAlbums();
		albums.add(ALICE, new PhotoAlbums.Photo(5, 1, 1000));
		albums.add(ALICE, new PhotoAlbums.Photo(9, 2, 2000));
		albums.add(BOB, new PhotoAlbums.Photo(7, 2, 1500));
		assertEquals(2, albums.count(ALICE));
		assertEquals(5, albums.photos(ALICE).getFirst().mapId(), "oldest first");
		assertEquals(9, albums.find(ALICE, 9).mapId());
		assertNull(albums.find(ALICE, 7), "Bob's photo is not Alice's");
		assertFalse(albums.remove(ALICE, 7), "Alice cannot delete Bob's photo");
		assertTrue(albums.remove(ALICE, 5));
		assertEquals(1, albums.count(ALICE));
		assertTrue(albums.remove(ALICE, 9));
		assertEquals(0, albums.count(ALICE));
		assertFalse(albums.owners().contains(ALICE), "an empty album is dropped");
		assertEquals(1, albums.count(BOB));
	}
}
