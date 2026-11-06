package com.arrl.radiocraft.common.radio.wxalert;

import com.arrl.radiocraft.common.radio.data.AprsWeather;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The station-spacing rule: three weather beacons, each at least 150 blocks from the others. */
class WeatherNetworkTest {

	private static final AprsWeather CALM = new AprsWeather(0, 0, 0, 60, 0, 50, 1013.0);

	private static WeatherNetwork.Station at(String call, double x, double z) {
		return new WeatherNetwork.Station(call, x, z, CALM);
	}

	@Test
	void fewerThanThreeIsTooFew() {
		WeatherNetwork.Check check = WeatherNetwork.check(List.of(at("A", 0, 0), at("B", 500, 0)));
		assertEquals(WeatherNetwork.Status.TOO_FEW, check.status());
		assertEquals(2, check.heard());
		assertEquals(2, check.spread());
		assertEquals(WeatherNetwork.Status.TOO_FEW, WeatherNetwork.check(List.of()).status());
	}

	@Test
	void threeWellApartAreReady() {
		WeatherNetwork.Check check = WeatherNetwork.check(List.of(at("A", 0, 0), at("B", 300, 0), at("C", 150, 260)));
		assertEquals(WeatherNetwork.Status.READY, check.status());
		assertEquals(3, check.spread());
	}

	@Test
	void everyPairMustBeFarApart() {
		// A and B are 400 apart, but C is only 100 from A: no three are all 150 apart.
		WeatherNetwork.Check check = WeatherNetwork.check(List.of(at("A", 0, 0), at("B", 400, 0), at("C", 0, 100)));
		assertEquals(WeatherNetwork.Status.TOO_CLOSE, check.status());
		assertEquals(2, check.spread());
	}

	@Test
	void allClusteredIsTooClose() {
		WeatherNetwork.Check check = WeatherNetwork.check(List.of(at("A", 0, 0), at("B", 50, 0), at("C", 0, 50), at("D", 60, 60)));
		assertEquals(WeatherNetwork.Status.TOO_CLOSE, check.status());
		assertEquals(4, check.heard());
		assertEquals(1, check.spread());
	}

	@Test
	void exactlyTheSpacingCounts() {
		assertEquals(WeatherNetwork.Status.READY, WeatherNetwork.check(List.of(at("A", 0, 0), at("B", 150, 0), at("C", 75, 150))).status());
		assertEquals(WeatherNetwork.Status.TOO_CLOSE, WeatherNetwork.check(List.of(at("A", 0, 0), at("B", 149.9, 0), at("C", 75, 150))).status());
	}

	@Test
	void aSpreadTripleAmongCloseOnesIsFound() {
		WeatherNetwork.Check check = WeatherNetwork.check(List.of(at("A", 0, 0), at("A2", 10, 10), at("A3", 20, 0),
				at("B", 1000, 0), at("B2", 1010, 5), at("C", 500, 900)));
		assertEquals(WeatherNetwork.Status.READY, check.status());
		assertEquals(3, check.spread());
	}
}
