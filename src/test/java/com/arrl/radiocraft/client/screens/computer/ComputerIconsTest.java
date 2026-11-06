package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every program has a Program Manager icon ({@code tools/computer_icons.py}): 32 x 32, in the sixteen EGA colours only
 * (the colour schemes recolour icons by matching those exactly).
 */
class ComputerIconsTest {

	private static final String ROOT = "/assets/radiocraft/textures/";

	@Test
	void everyProgramHasASixteenColourIcon() throws IOException {
		for(ComputerApp app : ComputerApps.all()) {
			String file = ROOT + "gui/computer/icons/" + app.id() + ".png"; // DosIcons.forProgram
			BufferedImage image;
			try(InputStream in = ComputerIconsTest.class.getResourceAsStream(file)) {
				assertNotNull(in, "no icon for " + app.id() + ": " + file);
				image = ImageIO.read(in);
			}
			assertNotNull(image, file);
			assertEquals(32, image.getWidth(), file);
			assertEquals(32, image.getHeight(), file);
			for(int y = 0; y < image.getHeight(); y++)
				for(int x = 0; x < image.getWidth(); x++) {
					int argb = image.getRGB(x, y);
					if(argb >>> 24 != 0)
						assertTrue(DosColours.index(argb) >= 0, app.id() + " icon has " + Integer.toHexString(argb) + " at " + x + "," + y);
				}
		}
	}
}
