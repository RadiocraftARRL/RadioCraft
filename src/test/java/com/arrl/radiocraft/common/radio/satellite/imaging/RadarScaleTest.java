package com.arrl.radiocraft.common.radio.satellite.imaging;

import com.arrl.radiocraft.common.photo.MapPalettes;
import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The radar colour scale (2026-11-12). */
class RadarScaleTest {

	@Test
	void rainClimbsTheScale() {
		assertEquals(RadarScale.NONE, RadarScale.rain(0.0D, false, false));
		assertEquals(RadarScale.NONE, RadarScale.rain(0.05D, false, false), "drizzle under the first step shows the ground");
		assertEquals(1, RadarScale.rain(0.1D, false, false));
		int last = 0;
		for(int i = 0; i <= 100; i++) {
			int level = RadarScale.rain(i / 100.0D, false, false);
			assertTrue(level >= last, "never lighter as it gets heavier");
			last = level;
		}
		assertEquals(RadarScale.HEAVIEST_RAIN, RadarScale.rain(1.0D, false, false));
		assertEquals(RadarScale.SEVERE, RadarScale.rain(0.1D, true, false), "a severe core is magenta whatever the rain");
		assertEquals(RadarScale.HAIL, RadarScale.rain(0.1D, true, true));
		assertEquals(RadarScale.NONE, RadarScale.snow(0.02D));
		assertEquals(RadarScale.SNOW_LIGHT, RadarScale.snow(0.1D));
		assertEquals(RadarScale.SNOW_HEAVY, RadarScale.snow(0.9D));
	}

	@Test
	void everyLevelHasItsOwnMapColourAndReadsBack() {
		Set<Byte> seen = new HashSet<>();
		assertEquals(0, RadarScale.colour(RadarScale.NONE));
		for(int level = 1; level < RadarScale.LEVELS; level++) {
			byte colour = RadarScale.colour(level);
			assertNotEquals(0, colour, "level " + level);
			assertTrue(seen.add(colour), "level " + level + " shares a colour");
			assertEquals(level, RadarScale.levelOf(colour));
			assertTrue(MapPalettes.vanilla().contains(colour), "map colours, so it prints on a map");
		}
		assertEquals(RadarScale.NONE, RadarScale.levelOf((byte)0));
		assertEquals(RadarScale.NONE, RadarScale.levelOf(MapColor.GRASS.getPackedId(MapColor.Brightness.LOW)), "the ground is not weather");
	}

	@Test
	void theSummarySaysHowMuchAndTheWorst() {
		byte ground = MapColor.GRASS.getPackedId(MapColor.Brightness.LOW);
		byte[] pixels = new byte[100];
		assertEquals("NO DATA", RadarScale.summary(pixels));
		Arrays.fill(pixels, ground);
		assertEquals("NO PRECIPITATION", RadarScale.summary(pixels));
		for(int i = 0; i < 20; i++)
			pixels[i] = RadarScale.colour(2);
		pixels[0] = RadarScale.colour(RadarScale.HAIL);
		pixels[1] = RadarScale.colour(RadarScale.SNOW_HEAVY);
		assertEquals("PRECIP 20%, WORST HAIL", RadarScale.summary(pixels));
		assertTrue(RadarScale.severity(RadarScale.HEAVIEST_RAIN) > RadarScale.severity(RadarScale.SNOW_HEAVY));
	}
}
