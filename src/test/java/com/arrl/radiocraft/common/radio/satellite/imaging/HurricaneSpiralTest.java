package com.arrl.radiocraft.common.radio.satellite.imaging;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hurricane's spiral bands in the satellite picture (2026-11-18). */
class HurricaneSpiralTest {

	@Test
	void theEyeAndEyewallAreLeftAlone() {
		for(double angle = 0; angle < 6.3; angle += 0.3)
			assertEquals(1.0D, AptPainter.spiral(5, 0.3D, angle, 0), 1e-9);
	}

	@Test
	void armsReachPastTheEdgeAndGapsLieBetween() {
		double most = 0, least = 9;
		for(double angle = 0; angle < 6.3; angle += 0.05) {
			double f = AptPainter.spiral(5, 1.3D, angle, 0);
			most = Math.max(most, f);
			least = Math.min(least, f);
		}
		assertTrue(1.3D / most < 1.0D, "no arm reads rain from inside the storm at 1.3 radii");
		assertTrue(least < 1.0D, "no gap between the arms");
		assertEquals(1.0D, AptPainter.spiral(5, AptPainter.SPIRAL_REACH, 1.0D, 0), 1e-9, "the arms do not end");
	}

	@Test
	void theArmsTurnOverTime() {
		assertTrue(Math.abs(AptPainter.spiral(5, 1.0D, 1.0D, 0) - AptPainter.spiral(5, 1.0D, 1.0D, 6000)) > 1e-3);
	}

	/** Paints a hurricane (the weather mod's round rings of rain) through the spiral to build/hurricane_spiral.png, to look at. */
	@Test
	void paintsAPicture() throws Exception {
		int size = 256;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		for(int py = 0; py < size; py++)
			for(int px = 0; px < size; px++) {
				double dx = (px - size / 2.0) / (size / 5.0), dz = (py - size / 2.0) / (size / 5.0); // 1 radius = 51 px.
				double share = Math.hypot(dx, dz), angle = Math.atan2(dz, dx);
				double u = share / AptPainter.spiral(5, share, angle, 0);
				double rain = u < 0.06 ? 0.05 : u < 0.14 ? (u - 0.06) / 0.08 : u < 0.5 ? 1 : u >= 1 ? 0
						: (1 - (u - 0.5) / 0.5) * (0.75 + 0.25 * Math.cos(u * 30));
				int c = rain <= 0.02 ? 0x203018 : rain < 0.3 ? 0x2E8B2E : rain < 0.6 ? 0xE0D020 : rain < 0.85 ? 0xE07020 : 0xD02020;
				image.setRGB(px, py, c);
			}
		File out = new File("build/hurricane_spiral.png");
		out.getParentFile().mkdirs();
		ImageIO.write(image, "png", out);
		assertTrue(out.exists());
	}
}
