package com.arrl.radiocraft.common.be_networks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatteryMeterReadingTest {

	@Test
	void restingVoltageFollowsCharge() {
		assertEquals(11.8D, new BatteryMeterReading(1, 0, 100, 0).volts(), 1e-9);
		assertEquals(12.7D, new BatteryMeterReading(1, 100, 100, 0).volts(), 1e-9);
		assertEquals(12.25D, new BatteryMeterReading(2, 100, 200, 0).volts(), 1e-9);
	}

	@Test
	void chargingLiftsAndLoadSags() {
		BatteryMeterReading rest = new BatteryMeterReading(1, 50, 100, 0);
		BatteryMeterReading charging = new BatteryMeterReading(1, 50, 100, 10);
		BatteryMeterReading hardCharge = new BatteryMeterReading(1, 50, 100, 1000);
		BatteryMeterReading load = new BatteryMeterReading(1, 50, 100, -13);
		BatteryMeterReading heavyLoad = new BatteryMeterReading(1, 50, 100, -1000);
		assertTrue(charging.volts() > rest.volts());
		assertEquals(BatteryMeterReading.CHARGE_VOLTS, hardCharge.volts(), 1e-9);
		assertEquals(rest.volts() - 0.25D, load.volts(), 1e-9);
		assertEquals(rest.volts() - BatteryMeterReading.MAX_SAG, heavyLoad.volts(), 1e-9);
	}

	@Test
	void sameDrawSagsABiggerBankLess() {
		assertTrue(new BatteryMeterReading(4, 200, 400, -26).volts() > new BatteryMeterReading(1, 50, 100, -26).volts());
	}

	@Test
	void comparatorCountsLikeAContainer() {
		assertEquals(0, new BatteryMeterReading(1, 0, 100, 0).comparator());
		assertEquals(1, new BatteryMeterReading(1, 1, 1000, 0).comparator());
		assertEquals(8, new BatteryMeterReading(1, 50, 100, 0).comparator());
		assertEquals(15, new BatteryMeterReading(1, 100, 100, 0).comparator());
		assertEquals(0, BatteryMeterReading.NONE.comparator());
	}

	@Test
	void trendAndTimeToGo() {
		assertEquals(BatteryMeterReading.Trend.REST, new BatteryMeterReading(1, 50, 100, 0.01).trend());
		BatteryMeterReading draining = new BatteryMeterReading(1, 100, 200, -2);
		assertEquals(BatteryMeterReading.Trend.DISCHARGING, draining.trend());
		assertEquals(50, draining.ticksToGo());
		BatteryMeterReading filling = new BatteryMeterReading(1, 100, 200, 4);
		assertEquals(BatteryMeterReading.Trend.CHARGING, filling.trend());
		assertEquals(25, filling.ticksToGo());
		assertEquals(-1, new BatteryMeterReading(1, 100, 200, 0).ticksToGo());
	}

	@Test
	void noBatteriesReadsNothing() {
		assertFalse(BatteryMeterReading.NONE.hasBatteries());
		assertEquals(0.0D, BatteryMeterReading.NONE.volts());
		assertEquals(0, BatteryMeterReading.NONE.percent());
	}

	@Test
	void smoothingSettlesOnASteadyFlow() {
		double flow = 0;
		for(int i = 0; i < 200; i++)
			flow = BatteryMeterReading.smooth(flow, -10);
		assertEquals(-10.0D, flow, 1e-6);
	}
}
