package com.arrl.radiocraft.common.radio.wxalert;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Alert area matching: the stations' hull plus a margin, and an alert's circle round a receiver. */
class AlertAreaTest {

	private static AlertArea square() {
		return AlertArea.around(List.of(new double[] { 0, 0 }, new double[] { 400, 0 }, new double[] { 400, 400 },
				new double[] { 0, 400 }, new double[] { 200, 200 }), 100);
	}

	@Test
	void hullDropsInnerPoints() {
		assertEquals(4, square().corners().size());
	}

	@Test
	void insideTheHullIsInTheArea() {
		AlertArea area = square();
		assertEquals(0.0, area.distance(200, 100), 1e-9);
		assertTrue(area.contains(10, 390));
	}

	@Test
	void theMarginReachesPastTheStations() {
		AlertArea area = square();
		assertTrue(area.contains(-99, 200));
		assertFalse(area.contains(-101, 200));
		assertEquals(50.0, area.distance(450, 200), 1e-9);
		assertTrue(area.contains(470, 470)); // 99 blocks from the corner.
		assertFalse(area.contains(480, 480)); // 113 from it.
		assertTrue(area.touches(-150, 200, 60)); // A storm 60 blocks round reaching in.
	}

	@Test
	void theCircleHoldsTheArea() {
		AlertArea area = square();
		assertEquals(200, area.centreX(), 1e-9);
		assertEquals(200, area.centreZ(), 1e-9);
		assertEquals(Math.hypot(200, 200) + 100, area.radius(), 1e-9);
	}

	@Test
	void stationsInALineMakeASegment() {
		AlertArea line = AlertArea.around(List.of(new double[] { 0, 0 }, new double[] { 200, 0 }, new double[] { 400, 0 }), 50);
		assertEquals(2, line.corners().size());
		assertTrue(line.contains(200, 49));
		assertFalse(line.contains(200, 51));
	}

	@Test
	void anAlertAffectsReceiversInItsCircle() {
		WeatherAlert alert = new WeatherAlert(WeatherAlert.Event.TOR, 1, 100, 100, 80, 40, 1000, 2, 90);
		assertTrue(alert.affects(100, 100));
		assertTrue(alert.affects(100 + 80 + WeatherAlert.MARGIN - 1, 100));
		assertFalse(alert.affects(100 + 80 + WeatherAlert.MARGIN + 1, 100));
		assertTrue(alert.hasStorm());
		assertFalse(alert.expired(999));
		assertTrue(alert.expired(1000));
	}

	@Test
	void alertsGoOnTheAirAndBack() {
		WeatherAlert alert = new WeatherAlert(WeatherAlert.Event.SVR, -42L, -1250, 340, 260, 120, 18000, -1, 315);
		WeatherAlert back = WeatherAlert.decode(alert.encode());
		assertNotNull(back);
		assertEquals(alert, back);
		assertNull(WeatherAlert.decode("XYZ 1 2 3 4 5 6 7 8"));
		assertNull(WeatherAlert.decode("TOR 1 2"));
		assertNull(WeatherAlert.decode("TOR zz 1 2 3 4 5 6 7"));
	}

	@Test
	void bearingsAreCompassDegrees() {
		assertEquals(0.0, WeatherAlert.bearing(0, 0, 0, -10), 1e-9); // North is -z.
		assertEquals(90.0, WeatherAlert.bearing(0, 0, 10, 0), 1e-9);
		assertEquals(180.0, WeatherAlert.bearing(0, 0, 0, 10), 1e-9);
		assertEquals(315.0, WeatherAlert.bearing(0, 0, -10, -10), 1e-9);
	}
}
