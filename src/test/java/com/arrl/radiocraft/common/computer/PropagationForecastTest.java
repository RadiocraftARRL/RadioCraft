package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.computer.PropagationForecast.Cell;
import com.arrl.radiocraft.common.computer.PropagationForecast.Grade;
import com.arrl.radiocraft.common.computer.PropagationForecast.Hour;
import com.arrl.radiocraft.common.computer.PropagationForecast.Path;
import com.arrl.radiocraft.common.computer.PropagationForecast.Result;
import com.arrl.radiocraft.common.computer.PropagationForecast.Settings;
import com.arrl.radiocraft.common.radio.Band;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropagationForecastTest {

	// The default 20m and 10m bands (Band.getDefaults).
	private static final Band B20 = new Band("20m", 300, 2000, 6000, 800, 1000, 14_000_000, 14_350_000);
	private static final Band B10 = new Band("10m", 800, 2500, 8000, 2500, 6000, 28_000_000, 29_700_000);
	/** The default thresholds: minimum signal 0.02, SNR 1, line of sight against half the solar noise. */
	private static final Settings SETTINGS = new Settings(0.02D, 1.0D, 0.5D);

	@Test
	void gradesFollowTheThresholds() {
		assertEquals(Grade.GOOD, PropagationForecast.grade(3.0D));
		assertEquals(Grade.FAIR, PropagationForecast.grade(2.99D));
		assertEquals(Grade.FAIR, PropagationForecast.grade(1.5D));
		assertEquals(Grade.POOR, PropagationForecast.grade(1.0D));
		assertEquals(Grade.CLOSED, PropagationForecast.grade(0.99D));
		assertEquals(Grade.CLOSED, PropagationForecast.grade(0.0D));
	}

	@Test
	void pathsAreLineOfSightSkipZoneSkipAndBeyond() {
		assertEquals(Path.LOS, PropagationForecast.path(B20, 100, true));
		assertEquals(Path.SKIP_ZONE, PropagationForecast.path(B20, 1000, true));
		assertEquals(Path.SKIP, PropagationForecast.path(B20, 4000, true));
		assertEquals(Path.BEYOND, PropagationForecast.path(B20, 7000, true));
		// By night 20m skips short: 800-1000.
		assertEquals(Path.SKIP, PropagationForecast.path(B20, 900, false));
		assertEquals(Path.BEYOND, PropagationForecast.path(B20, 4000, false));
	}

	@Test
	void theMiddleOfTheSkipIsGoodAndItsEdgesFade() {
		// Average sky: noise 0.3. Middle of 20m's day skip (4000): strength 1, quality 3.3.
		Cell middle = PropagationForecast.cell(B20, 4000, true, 1.0D, 0.3D, 0.0D, SETTINGS);
		assertEquals(Grade.GOOD, middle.grade());
		assertEquals(Path.SKIP, middle.path());
		assertEquals(1.0D, middle.strength(), 1e-9);
		// 3000: halfway to the edge, strength 0.5, quality 1.67.
		assertEquals(Grade.FAIR, PropagationForecast.cell(B20, 3000, true, 1.0D, 0.3D, 0.0D, SETTINGS).grade());
		// 2500: strength 0.25, under the noise.
		assertEquals(Grade.CLOSED, PropagationForecast.cell(B20, 2500, true, 1.0D, 0.3D, 0.0D, SETTINGS).grade());
	}

	@Test
	void lineOfSightCopiesAgainstHalfTheSolarNoise() {
		// 150 blocks on 20m: strength 0.5; against half of a 0.3 floor (0.15) quality 3.3, against the whole of it 1.67.
		Cell near = PropagationForecast.cell(B20, 150, true, 1.0D, 0.3D, 0.0D, SETTINGS);
		assertEquals(Path.LOS, near.path());
		assertEquals(Grade.GOOD, near.grade());
		// The aurora's static is not halved.
		assertEquals(Grade.POOR, PropagationForecast.cell(B20, 150, true, 1.0D, 0.3D, 0.25D, SETTINGS).grade());
	}

	@Test
	void conditionsLowerTheSkipButNotLineOfSight() {
		assertEquals(Grade.GOOD, PropagationForecast.cell(B20, 4000, true, 1.0D, 0.3D, 0.0D, SETTINGS).grade());
		assertEquals(Grade.FAIR, PropagationForecast.cell(B20, 4000, true, 0.6D, 0.3D, 0.0D, SETTINGS).grade()); // Fair: 2.0
		assertEquals(Grade.POOR, PropagationForecast.cell(B20, 4000, true, 0.3D, 0.3D, 0.0D, SETTINGS).grade()); // Poor: 1.0
		assertEquals(Grade.CLOSED, PropagationForecast.cell(B20, 4000, true, 0.05D, 0.3D, 0.0D, SETTINGS).grade()); // Closed
		assertEquals(Grade.GOOD, PropagationForecast.cell(B20, 150, true, 0.05D, 0.3D, 0.0D, SETTINGS).grade());
	}

	@Test
	void cwReachesFurtherThanVoice() {
		// 6100 blocks is past 20m's day skip for voice; CW counts it as 4067, the middle.
		Cell far = PropagationForecast.cell(B20, 6100, true, 1.0D, 0.3D, 0.0D, SETTINGS);
		assertEquals(Grade.CLOSED, far.grade());
		assertEquals(Grade.GOOD, far.cw());
		// CW is never worse than voice.
		Cell middle = PropagationForecast.cell(B20, 4000, true, 1.0D, 0.3D, 0.0D, SETTINGS);
		assertEquals(Grade.GOOD, middle.cw());
	}

	@Test
	void dayAndNightChangeTheGrid() {
		List<Hour> hours = new ArrayList<>();
		for(int h = 0; h < 24; h++)
			hours.add(new Hour(h, h >= 6 && h < 18, true, 0.3D, 0.0D));
		Result result = PropagationForecast.predict(List.of(B20, B10), 4000, hours, (band, h) -> 1.0D, SETTINGS);
		Cell noon = result.rows().getFirst().cells().get(12);
		Cell midnight = result.rows().getFirst().cells().get(0);
		assertEquals(Grade.GOOD, noon.grade());
		assertEquals(Grade.CLOSED, midnight.grade());
		assertEquals(Path.BEYOND, midnight.path());
		// 10m is open to 4000 by night too (2500-6000).
		assertTrue(result.rows().get(1).cells().get(0).grade() != Grade.CLOSED);
		// 20m's best hour is a day hour.
		int best = result.rows().getFirst().bestHour();
		assertTrue(best >= 6 && best < 18, "best hour " + best);
	}

	@Test
	void conditionsPerBandAndHourAreUsed() {
		List<Hour> hours = List.of(new Hour(12, true, true, 0.3D, 0.0D), new Hour(13, true, false, 0.3D, 0.0D));
		Result result = PropagationForecast.predict(List.of(B20, B10), 4000, hours, (band, h) -> band.equals(B10) ? 0.05D : h == 0 ? 1.0D : 0.3D, SETTINGS);
		assertEquals(Grade.GOOD, result.rows().get(0).cells().get(0).grade());
		assertEquals(Grade.POOR, result.rows().get(0).cells().get(1).grade());
		assertEquals(Grade.CLOSED, result.rows().get(1).cells().get(0).grade());
		assertEquals(-1, result.rows().get(1).bestHour());
		assertEquals(0, result.bestNow());
		assertFalse(result.hours().get(1).known());
	}

	@Test
	void noBandOpenNowIsMinusOne() {
		List<Hour> hours = List.of(new Hour(0, false, true, 1.0D, 0.0D));
		Result result = PropagationForecast.predict(List.of(B20), 4000, hours, (band, h) -> 0.05D, SETTINGS);
		assertEquals(-1, result.bestNow());
	}

	/** Vanilla's sky darkness, written out as Level.updateSkyBrightness does it. */
	private static boolean vanillaIsDay(float timeOfDay, float rain, float thunder) {
		double d0 = 1.0D - (double)(rain * 5.0F) / 16.0D;
		double d1 = 1.0D - (double)(thunder * 5.0F) / 16.0D;
		double d2 = 0.5D + 2.0D * Math.clamp(Math.cos((double)timeOfDay * (Math.PI * 2.0D)), -0.25D, 0.25D);
		int darken = (int)((1.0D - d2 * d0 * d1) * 11.0D);
		return darken < 4;
	}

	@Test
	void isDayMatchesVanilla() {
		for(int i = 0; i <= 1000; i++) {
			float t = i / 1000.0F;
			for(float rain : new float[] { 0.0F, 0.5F, 1.0F })
				assertEquals(vanillaIsDay(t, rain, 0.0F), PropagationForecast.isDay(t, rain, 0.0D), "t=" + t + " rain=" + rain);
			assertEquals(vanillaIsDay(t, 1.0F, 1.0F), PropagationForecast.isDay(t, 1.0D, 1.0D), "t=" + t + " storm");
		}
		assertTrue(PropagationForecast.isDay(0.0D, 0.0D, 0.0D)); // Noon (time of day 0 is the sun at its height).
		assertFalse(PropagationForecast.isDay(0.5D, 0.0D, 0.0D)); // Midnight.
	}

	@Test
	void clockHoursStartAtSix() {
		assertEquals(6, PropagationForecast.hourOfDay(0L));
		assertEquals(12, PropagationForecast.hourOfDay(6000L));
		assertEquals(0, PropagationForecast.hourOfDay(18000L));
		assertEquals(5, PropagationForecast.hourOfDay(23999L));
		assertEquals(6, PropagationForecast.hourOfDay(24000L));
	}

	@Test
	void offsetsGoNorthAndEast() {
		double[] north = PropagationForecast.offset(0, 0, 100, 0);
		assertEquals(0.0D, north[0], 1e-9);
		assertEquals(-100.0D, north[1], 1e-9);
		double[] east = PropagationForecast.offset(0, 0, 100, 90);
		assertEquals(100.0D, east[0], 1e-9);
		assertEquals(0.0D, east[1], 1e-9);
	}
}
