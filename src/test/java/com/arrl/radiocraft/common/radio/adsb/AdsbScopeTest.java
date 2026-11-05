package com.arrl.radiocraft.common.radio.adsb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The radio computer's ADS-B scope picture (1.12 step 9). */
class AdsbScopeTest {

	private static final int SIZE = 128;
	private static final AdsbScope.Palette PALETTE = new AdsbScope.Palette((byte)1, (byte)2, (byte)3, (byte)4, (byte)5, (byte)6, (byte)7, (byte)8);

	private static AdsbPicture.Contact contact(int icao, float dx, float dz, int squawk, int[] trail) {
		return new AdsbPicture.Contact(icao, "AB", squawk, dx, dz, 3000, 120, 90, 0, 0, trail);
	}

	private static int count(byte[] pixels, byte colour) {
		int n = 0;
		for(byte b : pixels)
			if(b == colour)
				n++;
		return n;
	}

	@Test
	void anEmptyScopeIsARoundFaceWithRings() {
		byte[] pixels = new byte[SIZE * SIZE];
		AdsbScope.paint(pixels, SIZE, List.of(), 1000, -1, true, PALETTE);
		assertEquals(PALETTE.outside(), pixels[0], "the corner is off the scope");
		assertEquals(PALETTE.face(), pixels[64 * SIZE + 64 + 5 * SIZE + 7], "inside");
		assertTrue(count(pixels, PALETTE.ring()) > 300, "four rings and the cross");
		assertEquals(0, count(pixels, PALETTE.target()));
	}

	@Test
	void aTargetLandsWhereItIs() {
		// 500 blocks east on a 1000 block scope: half way to the right edge.
		int[] at = AdsbScope.place(SIZE, 1000, 500.0D, 0.0D);
		assertArrayEquals(new int[] { 95, 64 }, at);
		assertNull(AdsbScope.place(SIZE, 1000, 1200.0D, 0.0D), "off the scope");
		byte[] pixels = new byte[SIZE * SIZE];
		AdsbScope.paint(pixels, SIZE, List.of(contact(9, 500.0F, 0.0F, 1200, new int[] { 400, 0, 450, 0 })), 1000, -1, false, PALETTE);
		assertEquals(PALETTE.target(), pixels[at[1] * SIZE + at[0]]);
		assertEquals(PALETTE.trail(), pixels[64 * SIZE + 89], "the trail behind it, 450 blocks east");
		assertEquals(0, count(pixels, PALETTE.tag()), "tags off");
	}

	@Test
	void theCompactPictureKeepsWhatTheTubeDraws() {
		net.minecraft.nbt.CompoundTag full = new net.minecraft.nbt.CompoundTag();
		full.putBoolean("on", true);
		full.putBoolean("powered", true);
		full.putBoolean("antenna", true);
		full.putInt("range", 1000);
		full.putInt("reach", 18000);
		net.minecraft.nbt.ListTag targets = new net.minecraft.nbt.ListTag();
		for(int i = 0; i < 30; i++) {
			net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
			t.putInt("icao", i);
			t.putString("cs", "T" + i);
			t.putInt("sq", 1200);
			t.putFloat("dx", i * 50.0F); // Targets 21 and on are off a 1000 block scope.
			t.putFloat("dz", 0.0F);
			t.putInt("alt", 3000);
			t.putInt("trk", 90);
			int[] trail = new int[60]; // 30 points.
			for(int p = 0; p < 30; p++)
				trail[p * 2] = p;
			t.putIntArray("trail", trail);
			targets.add(t);
		}
		full.put("targets", targets);
		net.minecraft.nbt.CompoundTag compact = AdsbPicture.compact(full, AdsbPicture.COMPACT_TARGETS);
		List<AdsbPicture.Contact> contacts = AdsbPicture.contacts(compact);
		assertEquals(AdsbPicture.COMPACT_TARGETS, contacts.size(), "at most 16 targets");
		assertEquals("T0", contacts.getFirst().label());
		assertEquals(1000, compact.getInt("range"));
		assertTrue(compact.getBoolean("powered"));
		int[] trail = contacts.getFirst().trail();
		assertEquals(AdsbPicture.COMPACT_TRAIL * 2, trail.length, "the trail thinned to 12 points");
		assertEquals(29, trail[trail.length - 2], "it ends at the newest point");
		assertEquals(7, trail[0], "every other point, oldest first");

		full.putInt("range", 250);
		assertEquals(6, AdsbPicture.contacts(AdsbPicture.compact(full, 16)).size(), "targets off the scope are left out");
	}

	@Test
	void tagsEmergenciesAndThePickShow() {
		byte[] pixels = new byte[SIZE * SIZE];
		AdsbScope.paint(pixels, SIZE, List.of(contact(9, 0.0F, -300.0F, 7700, new int[0])), 1000, 9, true, PALETTE);
		assertTrue(count(pixels, PALETTE.emergency()) > 9, "the target and its tag in red");
		assertTrue(count(pixels, PALETTE.selected()) > 0, "the box round the picked target");
		assertEquals(0, count(pixels, PALETTE.target()));
	}

	@Test
	void farTargetsAreLeftOff() {
		byte[] pixels = new byte[SIZE * SIZE];
		AdsbScope.paint(pixels, SIZE, List.of(contact(9, 5000.0F, 0.0F, 1200, new int[] { 4000, 0 })), 1000, -1, true, PALETTE);
		assertEquals(0, count(pixels, PALETTE.target()));
		assertEquals(0, count(pixels, PALETTE.trail()));
	}

	@Test
	void theSquareDisplayFillsTheFaceEdgeToEdge() {
		byte[] pixels = new byte[SIZE * SIZE];
		AdsbScope.paint(pixels, SIZE, List.of(), 1000, -1, true, PALETTE, true);
		assertEquals(0, count(pixels, PALETTE.outside()), "no corner is off the display");
		assertTrue(count(pixels, PALETTE.ring()) > 300, "the rings, clipped at the edges");
		// A target out in a corner (past the range on the round scope) shows on the square one.
		List<AdsbPicture.Contact> corner = List.of(contact(9, 900.0F, 900.0F, 1200, new int[0]));
		byte[] round = new byte[SIZE * SIZE];
		AdsbScope.paint(round, SIZE, corner, 1000, -1, false, PALETTE);
		assertEquals(0, count(round, PALETTE.target()), "off the round scope");
		AdsbScope.paint(pixels, SIZE, corner, 1000, -1, false, PALETTE, true);
		assertTrue(count(pixels, PALETTE.target()) > 0, "on the square display");
	}
}
