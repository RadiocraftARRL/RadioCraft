package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.PixelCanvas;
import com.arrl.radiocraft.common.radio.satellite.SatelliteOrbit;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** SATTRACK's Image and Location views (2026-11-13). */
class SatelliteViewsTest {

	/** How many pixels are lit beyond the faintest stars: the craft. */
	private static int lit(PixelCanvas c) {
		int n = 0;
		for(int p : c.pixels())
			if(((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF) > 150)
				n++;
		return n;
	}

	@Test
	void everyKindDrawsAndTurns() {
		for(int kind = -1; kind < SatelliteModels.KINDS.length; kind++) {
			PixelCanvas a = new PixelCanvas(120, 80), b = new PixelCanvas(120, 80);
			a.fill(0);
			b.fill(0);
			SatelliteModels.render(a, SatelliteModels.model(kind), kind, 0);
			SatelliteModels.render(b, SatelliteModels.model(kind), kind, SatelliteModels.FRAMES / 4);
			assertTrue(lit(a) > 150, "kind " + kind + " draws a craft: " + lit(a));
			if(kind >= 0)
				assertFalse(Arrays.equals(a.pixels(), b.pixels()), "kind " + kind + " turns");
		}
	}

	@Test
	void sliceIsWidestOverheadAndGoneOutOfRange() {
		double tile = SatelliteOrbit.TILE, cover = SatelliteOrbit.COVERAGE;
		double over = SatelliteOrbitView.halfSlice(0.0D, cover, tile), off = SatelliteOrbitView.halfSlice(0.8D * cover, cover, tile);
		assertTrue(over > off && off > 0.0D);
		assertTrue(SatelliteOrbitView.halfSlice(cover + 1.0D, cover, tile) < 0.0D);
		assertEquals(0.0D, SatelliteOrbitView.angle(0.0D, tile), 1e-9, "over the station is straight up");
	}

	@Test
	void inTheSliceExactlyWhileInCoverage() {
		SatelliteOrbit orbit = new SatelliteOrbit(0.7D, 1_000.0D, 5_000.0D, 0.03D);
		for(int t = 0; t < 24_000; t += 37) {
			double[] track = orbit.track(100.0D, -250.0D, t);
			double half = SatelliteOrbitView.halfSlice(track[1], SatelliteOrbit.COVERAGE, SatelliteOrbit.TILE);
			boolean inSlice = half > 0.0D && Math.abs(SatelliteOrbitView.angle(track[0], SatelliteOrbit.TILE)) <= half;
			double distance = Math.hypot(track[0], track[1]);
			if(Math.abs(distance - SatelliteOrbit.COVERAGE) > 1.0D)
				assertEquals(orbit.look(100.0D, -250.0D, t).up(), inSlice, "tick " + t);
		}
	}

	@Test
	void locationViewDrawsTheGlobe() {
		PixelCanvas c = new PixelCanvas(282, 150);
		c.starfield(1L);
		SatelliteOrbitView.draw(c, 1.2D, 0.5D, true);
		int blue = 0, green = 0;
		for(int p : c.pixels()) {
			int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
			if(b > r + 40 && b > g)
				blue++;
			if(g > r + 20 && g > b + 10)
				green++;
		}
		assertTrue(blue > 300 && green > 100, "sea " + blue + ", land " + green);
	}

	/**
	 * Each satellite's light (2026-11-14) is on the picture for at least part of the turn, and the body hides it at times
	 * (on some kinds the top never turns away from us); the Moon has none.
	 */
	@Test
	void theLightShowsAndHidesAsItTurns() {
		int hidden = 0;
		for(int kind = 0; kind < SatelliteModels.KINDS.length; kind++) {
			var shapes = SatelliteModels.model(kind);
			int shown = 0;
			for(int f = 0; f < SatelliteModels.FRAMES; f++) {
				int[] at = SatelliteModels.lightAt(shapes, kind, f, 160, 120);
				if(at != null) {
					shown++;
					assertTrue(at[0] >= 0 && at[0] < 160 && at[1] >= 0 && at[1] < 120, "kind " + kind + " frame " + f + " off the picture");
				}
			}
			assertTrue(shown > 0, "kind " + kind + "'s light never shows");
			hidden += SatelliteModels.FRAMES - shown;
		}
		assertTrue(hidden > 0, "no satellite's body ever hides its light");
		assertNull(SatelliteModels.lightAt(SatelliteModels.model(-1), -1, 0, 160, 120), "the Moon has no light");
	}
}
