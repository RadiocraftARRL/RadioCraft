package com.arrl.radiocraft.common.radio.station;

import com.arrl.radiocraft.api.weather.DayOutlook;
import com.arrl.radiocraft.api.weather.WeatherReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The FM station's sentences as word ids (2026-11-14), and that every word they use has a clip. */
class StationSentencesTest {

	private static final String C = StationSentences.COMMA, S = StationSentences.STOP;

	@Test
	void numbersAreSpokenInWords() {
		assertEquals(List.of("zero"), StationSentences.number(0));
		assertEquals(List.of("seventy", "two"), StationSentences.number(72));
		assertEquals(List.of("one", "hundred", "seven"), StationSentences.number(107));
		assertEquals(List.of("one", "thousand", "fifty"), StationSentences.number(1050));
	}

	@Test
	void stationIdSpellsTheCallsignAndTheFrequency() {
		assertEquals(List.of("this_is", "letter_k", "letter_d", "nine", "letter_x", "letter_y", "letter_z", C,
				"ninety", "nine", "point", "nine", "fm", S), StationSentences.stationId("KD9XYZ", 99.9e6F));
		assertEquals(List.of("one", "hundred", "one", "point", "one", "fm", S), StationSentences.stationId("", 101.1e6F), "no callsign: the frequency alone");
	}

	@Test
	void aForecastReadsTheDayTomorrowWindAndNow() {
		WeatherReport now = new WeatherReport(WeatherReport.Precipitation.RAIN, 0.5F, 0.0F, 5.5D, 270.0D, 18.0D, 1004.0D, 85.0D, Set.of());
		List<String> words = StationSentences.forecast("this_afternoon", new DayOutlook(DayOutlook.Type.CHANCE, false),
				new DayOutlook(DayOutlook.Type.RAINY, true), false, now);
		assertEquals(List.of("the_forecast_for", "this_afternoon", C, "chance_of", "rain", S,
				"tomorrow", C, "rain", "likely", S,
				"winds_from_the", "west", "at", "twenty", "kilometers_per_hour", S,
				"currently", "rain", C, "eighteen", "degrees", S), words);
	}

	@Test
	void aColdCalmClearNight() {
		WeatherReport now = new WeatherReport(WeatherReport.Precipitation.NONE, 0.0F, 0.0F, 0.2D, 0.0D, -4.0D, 1020.0D, 40.0D, Set.of());
		List<String> words = StationSentences.forecast("tonight", new DayOutlook(DayOutlook.Type.DRY, false), DayOutlook.UNKNOWN, true, now);
		assertEquals(List.of("the_forecast_for", "tonight", C, "clear", S, "tornado_watch", S, "winds_calm", S,
				"currently", "clear", C, "minus", "four", "degrees", S), words);
	}

	@Test
	void anAlertIsReadInTheLongestClipsWithUnknownWordsLeftOut() {
		List<String> words = StationSentences.alert("Tornado Warning for the area. Take shelter now! Zorblax 5 blocks to the west.", StationVoice.VOCABULARY);
		assertEquals(List.of("tornado_warning", "for_the_area", S, "take_shelter_now", S, "five", "blocks", "to_the", "west", S), words);
	}

	@Test
	void theDayIsCutIntoPeriods() {
		assertEquals("this_morning", StationSentences.period(1000));
		assertEquals("this_afternoon", StationSentences.period(7000));
		assertEquals("this_evening", StationSentences.period(12000));
		assertEquals("tonight", StationSentences.period(18000));
		assertEquals("this_morning", StationSentences.period(24000 * 3 + 500));
	}

	@Test
	void everyWordHasAClip() {
		for(String id : StationVoice.VOCABULARY)
			assertNotNull(getClass().getResource("/assets/radiocraft/sounds/weather_alert/word_" + id + ".ogg"), "no clip for " + id);
		WeatherReport[] skies = {
				new WeatherReport(WeatherReport.Precipitation.SNOW, 0.9F, 0.0F, 12.0D, 45.0D, -10.0D, 990.0D, 90.0D, Set.of()),
				new WeatherReport(WeatherReport.Precipitation.HAIL, 0.1F, 0.6F, 2.0D, 135.0D, 25.0D, 1000.0D, 70.0D, Set.of()),
				new WeatherReport(WeatherReport.Precipitation.DUST, 0.5F, 0.0F, 15.0D, 200.0D, 35.0D, 1010.0D, 10.0D, Set.of()),
				new WeatherReport(WeatherReport.Precipitation.NONE, 0.5F, 0.0F, 11.0D, 315.0D, 5.0D, 1010.0D, 60.0D, Set.of(WeatherReport.Hazard.DENSE_FOG)),
				new WeatherReport(WeatherReport.Precipitation.NONE, 0.0F, 0.0F, 11.0D, 90.0D, 15.0D, 1010.0D, 60.0D, Set.of())};
		for(DayOutlook.Type type : DayOutlook.Type.values())
			for(String period : new String[] {"this_morning", "this_afternoon", "this_evening", "tonight"})
				for(WeatherReport now : skies)
					for(String id : StationSentences.forecast(period, new DayOutlook(type, true), new DayOutlook(type, false), true, now))
						assertTrue(id.equals(C) || id.equals(S) || StationVoice.VOCABULARY.contains(id), "no clip for " + id);
		for(String id : StationSentences.stationId("W1AW", 107.9e6F))
			assertTrue(id.equals(C) || id.equals(S) || StationVoice.VOCABULARY.contains(id), "no clip for " + id);
	}
}
