package com.arrl.radiocraft.common.radio.satellite.imaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A scan line on the air: where it lies, how well it was copied, and the noise a weak copy gets (2026-11-12). */
class AptLineTest {

	@Test
	void geometryGoesThroughThePacketText() {
		AptLine.Geometry g = new AptLine.Geometry(42, -1234, 5678, 0.6D, -0.8D);
		String text = g.encode();
		assertTrue(text.length() < 100, "fits a packet's text");
		assertEquals(g, AptLine.Geometry.decode(text));
		String heard = AptLine.withQuality(text, 1.234D);
		assertEquals(g, AptLine.Geometry.decode(heard), "the radio's quality does not spoil it");
		assertEquals(1.23D, AptLine.quality(heard), 1e-9);
		assertEquals(-1.0D, AptLine.quality(text), "no quality before a radio decoded it");
		assertNull(AptLine.Geometry.decode("rubbish"));
		assertNull(AptLine.Geometry.decode("1|2|3|0.5|0.5"), "not a unit vector");
	}

	@Test
	void aStrongSignalIsCleanAWeakOneSnowyAndBelowTheFloorLost() {
		byte[] clean = new byte[AptSwath.PIXELS];
		for(int i = 0; i < clean.length; i++)
			clean[i] = (byte)(4 + i % 40);
		assertArrayEquals(clean, AptLine.degrade(clean, AptLine.CLEAN, 7L), "clean at full strength");
		assertNull(AptLine.degrade(clean, AptLine.FLOOR - 0.01D, 7L), "lost under the floor");
		assertEquals(0.0D, AptLine.lossChance(1.0D));
		assertTrue(AptLine.lossChance(0.5D) > 0.0D && AptLine.lossChance(0.5D) < 1.0D);
		assertTrue(AptLine.noiseFraction(0.6D) > AptLine.noiseFraction(1.2D), "noisier the weaker");
		assertTrue(AptLine.noiseFraction(AptLine.FLOOR) <= AptLine.MAX_NOISE);
		// A weak copy that came through: some pixels are noise, the rest are the line's.
		byte[] weak = null;
		for(long seed = 0L; weak == null; seed++)
			weak = AptLine.degrade(clean, 0.7D, seed);
		int changed = 0;
		for(int i = 0; i < clean.length; i++)
			if(weak[i] != clean[i]) {
				changed++;
				assertTrue(AptLine.isNoise(weak[i]), "what changed is noise");
			}
		assertTrue(changed > 10 && changed < clean.length, "snowy, not blank: " + changed);
		assertArrayEquals(AptLine.degrade(clean, 0.9D, 99L), AptLine.degrade(clean, 0.9D, 99L), "the same seed, the same copy");
	}
}
