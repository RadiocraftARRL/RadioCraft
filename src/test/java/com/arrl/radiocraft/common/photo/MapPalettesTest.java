package com.arrl.radiocraft.common.photo;

import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The game's map colours as a palette (camera, 1.12 step 4). {@link MapColor} is a plain class with no registries, so
 * this runs without bootstrapping the game.
 */
class MapPalettesTest {

	@Test
	void everyMapColourButTransparent() {
		PhotoPalette palette = MapPalettes.vanilla();
		assertEquals(0, palette.size() % 4, "four brightnesses of each colour");
		assertTrue(palette.size() >= 60 * 4, "only " + palette.size() + " colours");
		for(int brightness = 0; brightness < 4; brightness++)
			assertFalse(palette.contains((byte)brightness), "transparent (NONE) is no photo colour");
		for(int i = 0; i < palette.size(); i++) {
			int abgr = MapColor.getColorFromPackedId(palette.id(i));
			int rgb = (abgr & 0xFF) << 16 | (abgr & 0xFF00) | (abgr >> 16) & 0xFF;
			assertEquals(rgb, palette.rgb(i), "colour " + palette.id(i) + " as the map draws it");
		}
	}

	@Test
	void greysRunFromBlackToWhite() {
		PhotoPalette greys = MapPalettes.greys();
		assertTrue(greys.size() >= 16, "only " + greys.size() + " greys");
		int darkest = 255, brightest = 0;
		for(int i = 0; i < greys.size(); i++) {
			int luma = PhotoPalette.luminance(greys.rgb(i));
			darkest = Math.min(darkest, luma);
			brightest = Math.max(brightest, luma);
		}
		assertTrue(darkest < 20 && brightest > 240, "greys only from " + darkest + " to " + brightest);
		assertTrue(greys.contains(MapColor.SNOW.getPackedId(MapColor.Brightness.HIGH)), "white snow is a grey");
		assertTrue(greys.contains(MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.LOWEST)), "black wool is a grey");
	}
}
