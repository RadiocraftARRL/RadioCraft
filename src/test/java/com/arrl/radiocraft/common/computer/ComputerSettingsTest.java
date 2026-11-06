package com.arrl.radiocraft.common.computer;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SETUP's settings: saved and read back, cleaned, and the clock. */
class ComputerSettingsTest {

	@Test
	void settingsSurviveTheirTag() {
		ComputerSettings settings = ComputerSettings.DEFAULT.withView(ComputerSettings.View.COMMAND).withScheme("amber")
				.withName("Shack PC").withClock(ComputerSettings.Clock.REAL).withHour24(false);
		assertEquals(settings, ComputerSettings.fromTag(settings.toTag()));
	}

	@Test
	void missingOrBadValuesAreTheDefaults() {
		assertSame(ComputerSettings.DEFAULT, ComputerSettings.fromTag(new CompoundTag()));
		CompoundTag tag = new CompoundTag();
		tag.putString("view", "sideways");
		tag.putString("scheme", "Bad Scheme!");
		tag.putString("clock", "sundial");
		ComputerSettings read = ComputerSettings.fromTag(tag);
		assertEquals(ComputerSettings.View.LIST, read.view());
		assertEquals("classic", read.scheme());
		assertEquals(ComputerSettings.Clock.GAME, read.clock());
		assertTrue(read.hour24());
	}

	@Test
	void theScreensaverWaitIsOneOfTheSteps() {
		assertEquals(5, ComputerSettings.DEFAULT.saverMinutes());
		assertEquals("starfield", ComputerSettings.DEFAULT.saverStyle());
		assertEquals(6000L, ComputerSettings.DEFAULT.saverTicks());
		assertEquals(-1L, ComputerSettings.DEFAULT.withSaverMinutes(0).saverTicks());
		assertEquals(3, ComputerSettings.DEFAULT.withSaverMinutes(4).saverMinutes());
		assertEquals(0, ComputerSettings.DEFAULT.withSaverMinutes(-7).saverMinutes());
		assertEquals(10, ComputerSettings.DEFAULT.withSaverMinutes(99).saverMinutes());
		assertEquals(10, ComputerSettings.DEFAULT.stepSaver(1).saverMinutes());
		assertEquals(0, ComputerSettings.DEFAULT.stepSaver(1).stepSaver(1).saverMinutes(), "after 10, never");
		assertEquals(3, ComputerSettings.DEFAULT.stepSaver(-1).saverMinutes());
		ComputerSettings set = ComputerSettings.DEFAULT.withSaverMinutes(1).withSaverStyle("waterfall");
		assertEquals(set, ComputerSettings.fromTag(set.toTag()));
		assertEquals("starfield", ComputerSettings.DEFAULT.withSaverStyle("Not A Style!").saverStyle());
		// Settings kept before the screensaver existed read as the default wait.
		CompoundTag old = new CompoundTag();
		old.putString("view", "icons");
		assertEquals(5, ComputerSettings.fromTag(old).saverMinutes());
	}

	@Test
	void namesArePrintableAndShort() {
		assertEquals("Shack", ComputerSettings.cleanName("  Sh§ack\n "));
		assertEquals(ComputerSettings.MAX_NAME, ComputerSettings.cleanName("x".repeat(40)).length());
		assertEquals("", ComputerSettings.cleanName(null));
	}

	@Test
	void theWorldClockStartsAtSixInTheMorning() {
		assertEquals("06:00", time(0L));
		assertEquals("12:00", time(6000L));
		assertEquals("18:00", time(12000L));
		assertEquals("00:00", time(18000L));
		assertEquals("06:30", time(500L));
		assertEquals("06:00", time(24000L * 5L), "any day");
	}

	@Test
	void twelveAndTwentyFourHourTime() {
		assertEquals("13:05", ComputerSettings.time(13, 5, true));
		assertEquals("1:05p", ComputerSettings.time(13, 5, false));
		assertEquals("12:00a", ComputerSettings.time(0, 0, false));
		assertEquals("12:30p", ComputerSettings.time(12, 30, false));
	}

	private static String time(long dayTime) {
		int[] t = ComputerSettings.gameTime(dayTime);
		return ComputerSettings.time(t[0], t[1], true);
	}
}
