package com.arrl.radiocraft.common.radio.wxalert;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pulse once: a receiver sounds once for each alert however often the generator sends it. */
class AlertMemoryTest {

	private static WeatherAlert alert(WeatherAlert.Event event, long key, long expires) {
		return new WeatherAlert(event, key, 0, 0, 100, 50, expires, -1, -1);
	}

	@Test
	void anAlertSoundsOnce() {
		AlertMemory memory = new AlertMemory();
		WeatherAlert tornado = alert(WeatherAlert.Event.TOR, 7, 6000);
		assertEquals(List.of(tornado), memory.fresh(List.of(tornado), 0));
		assertTrue(memory.fresh(List.of(tornado), 1200).isEmpty());
		assertTrue(memory.fresh(List.of(alert(WeatherAlert.Event.TOR, 7, 7200)), 2400).isEmpty()); // Sent again, renewed.
		assertTrue(memory.fresh(List.of(tornado), 6500).isEmpty()); // Still remembered: the renewal moved its end on.
	}

	@Test
	void anAlertThatRanOutSoundsAgain() {
		AlertMemory memory = new AlertMemory();
		WeatherAlert storm = alert(WeatherAlert.Event.SVR, 3, 1000);
		assertEquals(1, memory.fresh(List.of(storm), 0).size());
		assertEquals(1, memory.size());
		assertTrue(memory.fresh(List.of(), 1000).isEmpty());
		assertEquals(0, memory.size());
		assertEquals(1, memory.fresh(List.of(alert(WeatherAlert.Event.SVR, 3, 3000)), 1500).size());
	}

	@Test
	void aTouchdownIsANewAlert() {
		AlertMemory memory = new AlertMemory();
		memory.fresh(List.of(alert(WeatherAlert.Event.SVR, AlertBuilder.key(WeatherAlert.Event.SVR, 9), 6000)), 0);
		List<WeatherAlert> fresh = memory.fresh(List.of(alert(WeatherAlert.Event.SVR, AlertBuilder.key(WeatherAlert.Event.SVR, 9), 7000),
				alert(WeatherAlert.Event.TOR, AlertBuilder.key(WeatherAlert.Event.TOR, 9), 7000)), 1000);
		assertEquals(1, fresh.size());
		assertEquals(WeatherAlert.Event.TOR, fresh.getFirst().event());
	}

	@Test
	void freshAlertsComeWorstFirst() {
		AlertMemory memory = new AlertMemory();
		List<WeatherAlert> fresh = memory.fresh(List.of(alert(WeatherAlert.Event.FGY, 1, 100), alert(WeatherAlert.Event.TOR, 2, 100),
				alert(WeatherAlert.Event.SVA, 3, 100)), 0);
		assertEquals(List.of(WeatherAlert.Event.TOR, WeatherAlert.Event.SVA, WeatherAlert.Event.FGY), fresh.stream().map(WeatherAlert::event).toList());
		assertTrue(memory.fresh(List.of(alert(WeatherAlert.Event.FGY, 4, 0)), 0).isEmpty()); // Already over.
	}
}
