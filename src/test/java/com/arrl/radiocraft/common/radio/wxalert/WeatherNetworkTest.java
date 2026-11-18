package com.arrl.radiocraft.common.radio.wxalert;

import com.arrl.radiocraft.common.radio.data.AprsWeather;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The tiers (2026-11-18): how many weather stations each at least 150 blocks from the others, up to three. */
class WeatherNetworkTest {

	private static final AprsWeather CALM = new AprsWeather(0, 0, 0, 60, 0, 50, 1013.0);

	private static WeatherNetwork.Station at(String call, double x, double z) {
		return new WeatherNetwork.Station(call, x, z, CALM);
	}

	@Test
	void noneIsTooFew() {
		WeatherNetwork.Check check = WeatherNetwork.check(List.of());
		assertEquals(WeatherNetwork.Status.TOO_FEW, check.status());
		assertEquals(0, check.tier());
	}

	@Test
	void oneStationIsTierOne() {
		WeatherNetwork.Check check = WeatherNetwork.check(List.of(at("A", 0, 0)));
		assertEquals(WeatherNetwork.Status.READY, check.status());
		assertEquals(1, check.tier());
	}

	@Test
	void twoApartAreTierTwo() {
		WeatherNetwork.Check check = WeatherNetwork.check(List.of(at("A", 0, 0), at("B", 500, 0)));
		assertEquals(WeatherNetwork.Status.READY, check.status());
		assertEquals(2, check.heard());
		assertEquals(2, check.tier());
	}

	@Test
	void threeWellApartAreTierThree() {
		WeatherNetwork.Check check = WeatherNetwork.check(List.of(at("A", 0, 0), at("B", 300, 0), at("C", 150, 260)));
		assertEquals(3, check.spread());
		assertEquals(3, check.tier());
	}

	@Test
	void moreThanThreeStaysTierThree() {
		WeatherNetwork.Check check = WeatherNetwork.check(List.of(at("A", 0, 0), at("B", 1000, 0), at("C", 0, 1000), at("D", 1000, 1000)));
		assertEquals(4, check.spread());
		assertEquals(3, check.tier());
	}

	@Test
	void everyPairMustBeFarApart() {
		// A and B are 400 apart, but C is only 100 from A: no three are all 150 apart.
		WeatherNetwork.Check check = WeatherNetwork.check(List.of(at("A", 0, 0), at("B", 400, 0), at("C", 0, 100)));
		assertEquals(2, check.tier());
		List<WeatherNetwork.Station> group = WeatherNetwork.spreadGroup(List.of(at("A", 0, 0), at("B", 400, 0), at("C", 0, 100)), WeatherNetwork.MIN_SPACING);
		assertEquals(List.of("A", "B"), group.stream().map(WeatherNetwork.Station::call).toList());
	}

	@Test
	void allClusteredIsTierOne() {
		WeatherNetwork.Check check = WeatherNetwork.check(List.of(at("A", 0, 0), at("B", 50, 0), at("C", 0, 50), at("D", 60, 60)));
		assertEquals(4, check.heard());
		assertEquals(1, check.spread());
		assertEquals(1, check.tier());
	}

	@Test
	void exactlyTheSpacingCounts() {
		assertEquals(3, WeatherNetwork.check(List.of(at("A", 0, 0), at("B", 150, 0), at("C", 75, 150))).tier());
		assertEquals(2, WeatherNetwork.check(List.of(at("A", 0, 0), at("B", 149.9, 0), at("C", 75, 150))).tier());
	}

	@Test
	void aSpreadTripleAmongCloseOnesIsFound() {
		WeatherNetwork.Check check = WeatherNetwork.check(List.of(at("A", 0, 0), at("A2", 10, 10), at("A3", 20, 0),
				at("B", 1000, 0), at("B2", 1010, 5), at("C", 500, 900)));
		assertEquals(3, check.tier());
	}
}
