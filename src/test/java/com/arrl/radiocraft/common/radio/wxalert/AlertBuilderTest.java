package com.arrl.radiocraft.common.radio.wxalert;

import com.arrl.radiocraft.api.weather.StormReport;
import com.arrl.radiocraft.api.weather.WeatherReport;
import com.arrl.radiocraft.common.radio.data.AprsWeather;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which storm gives which alert, and the warnings a station's own report gives. */
class AlertBuilderTest {

	@Test
	void stormsGiveWarnings() {
		assertEquals(WeatherAlert.Event.TOR, AlertBuilder.warningFor(StormReport.Kind.TORNADO, true, Set.of(), 2));
		assertEquals(WeatherAlert.Event.SVR, AlertBuilder.warningFor(StormReport.Kind.TORNADO, false, Set.of(), 2)); // Not down yet.
		assertEquals(WeatherAlert.Event.SVR, AlertBuilder.warningFor(StormReport.Kind.SEVERE_THUNDERSTORM, false, Set.of(), -1));
		assertEquals(WeatherAlert.Event.BZW, AlertBuilder.warningFor(StormReport.Kind.SUPERCELL, false, Set.of(WeatherReport.Hazard.BLIZZARD), -1));
		assertEquals(WeatherAlert.Event.DSW, AlertBuilder.warningFor(StormReport.Kind.SUPERCELL, false, Set.of(WeatherReport.Hazard.SANDSTORM), -1));
		assertEquals(WeatherAlert.Event.HUW, AlertBuilder.warningFor(StormReport.Kind.HURRICANE, false, Set.of(), 3));
		assertEquals(WeatherAlert.Event.TRW, AlertBuilder.warningFor(StormReport.Kind.HURRICANE, false, Set.of(), 0));
		assertEquals(WeatherAlert.Event.HWW, AlertBuilder.warningFor(StormReport.Kind.HIGH_WIND, false, Set.of(), -1));
		assertNull(AlertBuilder.warningFor(StormReport.Kind.THUNDERSTORM, false, Set.of(), -1));
		assertNull(AlertBuilder.warningFor(StormReport.Kind.SHOWER, false, Set.of(), -1));
	}

	@Test
	void forecastStormsGiveWatches() {
		assertEquals(WeatherAlert.Event.TOA, AlertBuilder.watchFor(StormReport.Kind.TORNADO, 1));
		assertEquals(WeatherAlert.Event.SVA, AlertBuilder.watchFor(StormReport.Kind.SUPERCELL, -1));
		assertNull(AlertBuilder.watchFor(StormReport.Kind.STRONG_THUNDERSTORM, -1));
	}

	@Test
	void aStationsOwnReportCanWarn() {
		WeatherNetwork.Station wet = new WeatherNetwork.Station("W1", 100, 200, new AprsWeather(270, 10, 15, 60, 120, 90, 1000.0));
		List<WeatherAlert> alerts = AlertBuilder.forStation(wet, 0);
		assertEquals(1, alerts.size());
		assertEquals(WeatherAlert.Event.FFW, alerts.getFirst().event());
		assertEquals(100, alerts.getFirst().x());
		assertTrue(alerts.getFirst().affects(100, 200 + AlertBuilder.STATION_ALERT_RADIUS));
		WeatherNetwork.Station windy = new WeatherNetwork.Station("W2", 0, 0, new AprsWeather(270, 30, 60, 60, 0, 50, 1000.0));
		assertEquals(List.of(WeatherAlert.Event.HWW), AlertBuilder.forStation(windy, 0).stream().map(WeatherAlert::event).toList());
		WeatherNetwork.Station calm = new WeatherNetwork.Station("W3", 0, 0, new AprsWeather(null, null, null, null, null, null, null));
		assertTrue(AlertBuilder.forStation(calm, 0).isEmpty());
	}

	@Test
	void theSameHazardKeepsItsKey() {
		assertEquals(AlertBuilder.key(WeatherAlert.Event.TOR, 5), AlertBuilder.key(WeatherAlert.Event.TOR, 5));
		assertNotEquals(AlertBuilder.key(WeatherAlert.Event.TOR, 5), AlertBuilder.key(WeatherAlert.Event.SVR, 5));
	}

	/**
	 * Tropical advisories (2026-11-15): a disturbance gives a tropical weather statement with its chance, a storm a day or
	 * two off a watch (hurricane or tropical storm), a near one the warning, unless a storm's warning is already out.
	 */
	@Test
	void tropicalAdvisoriesGiveStatementsWatchesAndWarnings() {
		AlertArea area = AlertArea.around(List.of(new double[] {0, 0}, new double[] {100, 0}), 50);
		List<com.arrl.radiocraft.api.weather.TropicalReport.TrackPoint> track = List.of(new com.arrl.radiocraft.api.weather.TropicalReport.TrackPoint(0, 3000, 0, 800));
		var disturbance = new com.arrl.radiocraft.api.weather.TropicalReport(9, com.arrl.radiocraft.api.weather.TropicalReport.Stage.DISTURBANCE, false, -1, 65, 500, 30000, 3200, track);
		WeatherAlert statement = AlertBuilder.forTropical(disturbance, area, 0, List.of());
		assertEquals(WeatherAlert.Event.HLS, statement.event());
		assertEquals(65, statement.category());
		assertTrue(statement.affects(50, 0));
		var watch = new com.arrl.radiocraft.api.weather.TropicalReport(9, com.arrl.radiocraft.api.weather.TropicalReport.Stage.WATCH, true, 3, 100, 0, 15000, 3200, track);
		assertEquals(WeatherAlert.Event.HUA, AlertBuilder.forTropical(watch, area, 0, List.of()).event());
		var stormWatch = new com.arrl.radiocraft.api.weather.TropicalReport(9, com.arrl.radiocraft.api.weather.TropicalReport.Stage.OUTLOOK, false, 0, 0, 0, 30000, 3200, track);
		assertEquals(WeatherAlert.Event.TRA, AlertBuilder.forTropical(stormWatch, area, 0, List.of()).event());
		var warning = new com.arrl.radiocraft.api.weather.TropicalReport(9, com.arrl.radiocraft.api.weather.TropicalReport.Stage.WARNING, true, 3, 100, 0, 5000, 3200, track);
		assertEquals(WeatherAlert.Event.HUW, AlertBuilder.forTropical(warning, area, 0, List.of()).event());
		WeatherAlert already = new WeatherAlert(WeatherAlert.Event.HUW, 1, 3000, 0, 900, 800, 6000, 3, 90);
		assertNull(AlertBuilder.forTropical(warning, area, 0, List.of(already)), "two hurricane warnings for one storm");
	}
}
