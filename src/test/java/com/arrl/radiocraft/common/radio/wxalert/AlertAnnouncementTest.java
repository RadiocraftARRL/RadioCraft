package com.arrl.radiocraft.common.radio.wxalert;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.arrl.radiocraft.common.radio.wxalert.WeatherVoiceClip.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the receiver says, spliced from its word clips. */
class AlertAnnouncementTest {

	@Test
	void numbersAreSpokenAsWords() {
		assertEquals(List.of(ZERO), WeatherVoiceClip.number(0));
		assertEquals(List.of(SEVENTEEN), WeatherVoiceClip.number(17));
		assertEquals(List.of(FORTY), WeatherVoiceClip.number(40));
		assertEquals(List.of(THREE, HUNDRED, TWENTY), WeatherVoiceClip.number(320));
		assertEquals(List.of(ONE, HUNDRED, FIVE), WeatherVoiceClip.number(105));
		assertEquals(List.of(TWO, THOUSAND, FOUR, HUNDRED), WeatherVoiceClip.number(2400));
		assertEquals(List.of(TWELVE, THOUSAND, NINETY, NINE), WeatherVoiceClip.number(12099));
	}

	@Test
	void distancesAreRounded() {
		assertEquals(320, AlertAnnouncement.spokenDistance(318.4));
		assertEquals(1300, AlertAnnouncement.spokenDistance(1288));
	}

	@Test
	void compassPoints() {
		assertEquals(NORTH, WeatherVoiceClip.compass(10));
		assertEquals(NORTH, WeatherVoiceClip.compass(350));
		assertEquals(NORTHEAST, WeatherVoiceClip.compass(40));
		assertEquals(WEST, WeatherVoiceClip.compass(270));
		assertEquals(NORTHWEST, WeatherVoiceClip.compass(-45));
	}

	@Test
	void aTornadoWarningGivesDistanceDirectionAndMotion() {
		// The storm is 320 blocks north-west of the receiver at (0, 0), moving east.
		WeatherAlert alert = new WeatherAlert(WeatherAlert.Event.TOR, 1, -226, -226, 400, 60, 1000, 2, 90);
		assertEquals(List.of(INTRO, TORNADO_WARNING, FOR_THE_AREA, THE_STORM_IS, THREE, HUNDRED, TWENTY, BLOCKS, TO_THE, NORTHWEST,
				MOVING, EAST, TAKE_SHELTER_NOW, END_OF_MESSAGE), AlertAnnouncement.clips(alert, 0, 0));
		assertEquals("TORNADO WARNING: STORM 320 BLOCKS NW, MOVING E. TAKE SHELTER NOW.", AlertAnnouncement.text(alert, 0, 0));
	}

	@Test
	void underTheStormItSaysSo() {
		WeatherAlert alert = new WeatherAlert(WeatherAlert.Event.SVR, 1, 10, 10, 200, 80, 1000, -1, -1);
		assertEquals(List.of(INTRO, SEVERE_THUNDERSTORM_WARNING, FOR_THE_AREA, THE_STORM_IS, OVER_YOUR_LOCATION, STAY_INDOORS, END_OF_MESSAGE),
				AlertAnnouncement.clips(alert, 0, 0));
	}

	@Test
	void anAreaAlertHasNoDistance() {
		WeatherAlert watch = new WeatherAlert(WeatherAlert.Event.TOA, 1, 500, 500, 900, 0, 1000, -1, -1);
		assertEquals(List.of(INTRO, TORNADO_WATCH, FOR_THE_AREA, END_OF_MESSAGE), AlertAnnouncement.clips(watch, 0, 0));
		assertEquals("TORNADO WATCH FOR THE AREA.", AlertAnnouncement.text(watch, 0, 0));
		WeatherAlert hurricane = new WeatherAlert(WeatherAlert.Event.HUW, 2, 0, -2000, 600, 300, 1000, 3, -1);
		List<WeatherVoiceClip> clips = AlertAnnouncement.clips(hurricane, 0, 0);
		assertTrue(clips.containsAll(List.of(CATEGORY, THREE, TWO, THOUSAND, NORTH)));
	}

	@Test
	void clipsFollowEachOtherWithoutDrift() {
		List<WeatherVoiceClip> clips = List.of(INTRO, TORNADO_WARNING, FOR_THE_AREA, END_OF_MESSAGE);
		int[] starts = AlertAnnouncement.startTicks(clips, true);
		assertEquals((TONE_MS + PAUSE_MS) / 50, starts[0]);
		assertEquals(Math.round((TONE_MS + PAUSE_MS + INTRO.millis() + PAUSE_MS) / 50.0), starts[1]);
		for(int i = 1; i < starts.length; i++)
			assertTrue(starts[i] > starts[i - 1]);
		assertTrue(AlertAnnouncement.lengthTicks(clips, true) > starts[starts.length - 1]);
		assertEquals(0, AlertAnnouncement.startTicks(clips, false)[0]);
	}
}
