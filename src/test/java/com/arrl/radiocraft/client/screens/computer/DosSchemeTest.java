package com.arrl.radiocraft.client.screens.computer;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The radio computer's colour schemes (SETUP): the sixteen colours remapped, monochrome brightness order, raw colours. */
class DosSchemeTest {

	@Test
	void classicShowsEveryColourAsItIs() {
		for(int rgb : DosColours.PALETTE)
			assertEquals(rgb, DosScheme.CLASSIC.display(rgb));
		assertEquals(0x123456, DosScheme.CLASSIC.display(0x123456), "another colour is kept");
	}

	@Test
	void schemesHaveUniqueIdsAndUnknownIsClassic() {
		Set<String> ids = new HashSet<>();
		for(DosScheme scheme : DosScheme.ALL)
			assertTrue(ids.add(scheme.id()), scheme.id());
		assertTrue(ids.containsAll(Set.of("classic", "green", "amber", "white", "high_contrast", "teal", "plasma")));
		assertSame(DosScheme.CLASSIC, DosScheme.byId("no_such_scheme"));
		assertSame(DosScheme.AMBER, DosScheme.byId("amber"));
		for(DosScheme scheme : DosScheme.ALL)
			assertTrue(scheme.id().matches("[a-z0-9_]{1,24}"), "ComputerSettings would not keep " + scheme.id());
	}

	@Test
	void aColourSchemeRemapsTheSixteen() {
		assertEquals(0x000000, DosScheme.HIGH_CONTRAST.display(DosColours.BODY_BG), "high contrast has a black body");
		assertEquals(0xFFFFFF, DosScheme.HIGH_CONTRAST.display(DosColours.BODY_FG));
		assertEquals(0x005A5A, DosScheme.TEAL.display(DosColours.BLUE));
		assertEquals(0x7F7F7F, DosScheme.TEAL.display(0x7F7F7F), "a colour scheme keeps other colours");
	}

	@Test
	void monochromeKeepsTheBrightnessOrderOfTheRoles() {
		for(DosScheme scheme : DosScheme.ALL) {
			if(!scheme.monochrome())
				continue;
			// Text over its background: light text on the body, dark text on the bars.
			assertTrue(lum(scheme, DosColours.TEXT_FG) > lum(scheme, DosColours.BODY_BG) + 0.5D, scheme.id() + " text");
			assertTrue(lum(scheme, DosColours.BODY_FG) > lum(scheme, DosColours.DIM), scheme.id() + " dim is dimmer");
			assertTrue(lum(scheme, DosColours.DIM) > lum(scheme, DosColours.BODY_BG) + 0.1D, scheme.id() + " dim still shows");
			assertTrue(lum(scheme, DosColours.HIGHLIGHT_BG) > lum(scheme, DosColours.HIGHLIGHT_FG) + 0.3D, scheme.id() + " highlight");
			assertTrue(lum(scheme, DosColours.TITLE_BG) > lum(scheme, DosColours.TITLE_FG) + 0.3D, scheme.id() + " title");
			assertTrue(lum(scheme, DosColours.ERROR_FG) > lum(scheme, DosColours.ERROR_BG) + 0.3D, scheme.id() + " error");
			assertTrue(lum(scheme, DosColours.KEYS_BG) > lum(scheme, DosColours.HOTKEY_FG) + 0.15D, scheme.id() + " hot keys");
			assertTrue(lum(scheme, DosColours.FIELD_BG) < lum(scheme, DosColours.BODY_BG), scheme.id() + " fields stand out");
			assertTrue(sameHue(scheme.display(DosColours.LIGHT_GREY), scheme.phosphor()), scheme.id() + " body text is not the phosphor's colour");
		}
	}

	@Test
	void monochromeTurnsOtherColoursIntoBrightness() {
		int dark = DosScheme.GREEN.display(0x202020), light = DosScheme.GREEN.display(0xE0E0E0);
		assertTrue(DosScheme.luminance(light) > DosScheme.luminance(dark));
		assertEquals(0, DosScheme.GREEN.display(DosColours.BLACK), "black stays black");
	}

	@Test
	void rawColoursAreNeverChanged() {
		assertEquals(0x0000AA, DosScheme.AMBER.display(DosColours.RAW | 0x0000AA));
		assertNotEquals(0x0000AA, DosScheme.AMBER.display(0x0000AA));
	}

	@Test
	void indexFindsTheSixteen() {
		assertEquals(1, DosColours.index(DosColours.BLUE));
		assertEquals(15, DosColours.index(0xFF000000 | DosColours.WHITE));
		assertEquals(-1, DosColours.index(0x123456));
	}

	private static double lum(DosScheme scheme, int colour) {
		return DosScheme.luminance(scheme.display(colour));
	}

	/** True if {@code rgb}'s channels are in the same order of size as {@code phosphor}'s (the same hue, roughly). */
	private static boolean sameHue(int rgb, int phosphor) {
		int[] a = { rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF }, b = { phosphor >> 16 & 0xFF, phosphor >> 8 & 0xFF, phosphor & 0xFF };
		for(int i = 0; i < 3; i++)
			for(int j = 0; j < 3; j++)
				if(b[i] > b[j] + 20 && a[i] < a[j])
					return false;
		return true;
	}
}
