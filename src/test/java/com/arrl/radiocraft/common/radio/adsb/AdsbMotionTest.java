package com.arrl.radiocraft.common.radio.adsb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An ADS-B beacon's ground speed, track and climb, worked out from its reports a second apart (1.12 step 9). */
class AdsbMotionTest {

	/** Reports once a second (20 ticks) for {@code seconds}, moving {@code vx, vy, vz} blocks a second. */
	private static AdsbMotion fly(double vx, double vy, double vz, int seconds, double nose) {
		AdsbMotion motion = new AdsbMotion();
		for(int s = 0; s <= seconds; s++)
			motion.update(100.0D + vx * s, 120.0D + vy * s, -50.0D + vz * s, 1000L + s * 20L, nose);
		return motion;
	}

	@Test
	void aSteadyFlightGivesItsSpeedTrackAndClimb() {
		AdsbMotion motion = fly(30.0D, 2.0D, -40.0D, 12, Double.NaN); // 50 blocks a second, north-east-ish.
		assertEquals(50.0D, motion.groundSpeed(), 0.05D);
		assertEquals(AdsbMath.bearing(30.0D, -40.0D), motion.heading(), 0.1D);
		assertEquals(2.0D, motion.verticalRate(), 0.01D);
		assertTrue(motion.moving());
	}

	@Test
	void dueEastIsNinetyAndDueSouthIsOneEighty() {
		assertEquals(90.0D, fly(20.0D, 0.0D, 0.0D, 10, Double.NaN).heading(), 0.1D);
		assertEquals(180.0D, fly(0.0D, 0.0D, 20.0D, 10, Double.NaN).heading(), 0.1D);
	}

	@Test
	void theFirstReportKnowsNoSpeed() {
		AdsbMotion motion = new AdsbMotion();
		motion.update(0.0D, 70.0D, 0.0D, 100L, 270.0D);
		assertEquals(0.0D, motion.groundSpeed(), 1.0E-9D);
		assertFalse(motion.moving());
		assertEquals(270.0D, motion.heading(), 1.0E-9D, "standing still, the nose heading is reported");
	}

	@Test
	void standingStillReportsTheNoseNotAStaleTrack() {
		AdsbMotion motion = fly(0.0D, 0.0D, 0.0D, 5, 135.0D);
		assertEquals(0.0D, motion.groundSpeed(), 1.0E-9D);
		assertEquals(135.0D, motion.heading(), 1.0E-9D);
		assertEquals(225.0D, fly(0.0D, 0.0D, 0.0D, 3, -135.0D).heading(), 1.0E-9D, "wrapped into 0-360");
	}

	@Test
	void slowingDownToAStopKeepsTheLastTrackWithoutANose() {
		AdsbMotion motion = fly(0.0D, 0.0D, -20.0D, 6, Double.NaN); // North.
		for(int s = 1; s <= 30; s++) // Then parked where it stopped.
			motion.update(100.0D, 120.0D, -50.0D - 20.0D * 6, 1000L + 6 * 20L + s * 20L, Double.NaN);
		assertFalse(motion.moving());
		assertEquals(0.0D, motion.heading(), 0.1D);
	}

	@Test
	void theSmoothingFollowsATurnWithinAFewSeconds() {
		AdsbMotion motion = fly(20.0D, 0.0D, 0.0D, 10, Double.NaN); // East...
		for(int s = 1; s <= 6; s++) // ...then south.
			motion.update(100.0D + 200.0D, 120.0D, -50.0D + 20.0D * s, 1000L + 200L + s * 20L, Double.NaN);
		assertEquals(180.0D, motion.heading(), 2.0D);
		assertEquals(20.0D, motion.groundSpeed(), 1.0D);
	}

	@Test
	void aTeleportOrALongGapStartsAfresh() {
		AdsbMotion motion = fly(20.0D, 0.0D, 0.0D, 5, Double.NaN);
		motion.update(50_000.0D, 120.0D, 0.0D, 1000L + 6 * 20L, Double.NaN); // 50 km in a second.
		assertEquals(0.0D, motion.groundSpeed(), 1.0E-9D);
		motion = fly(20.0D, 0.0D, 0.0D, 5, Double.NaN);
		motion.update(300.0D, 120.0D, -50.0D, 1000L + 5 * 20L + 20L * 60L, Double.NaN); // A minute later.
		assertEquals(0.0D, motion.groundSpeed(), 1.0E-9D);
	}

	@Test
	void resetForgetsThePast() {
		AdsbMotion motion = fly(20.0D, 0.0D, 0.0D, 5, Double.NaN);
		motion.reset();
		motion.update(10_000.0D, 120.0D, 0.0D, 5000L, Double.NaN);
		assertEquals(0.0D, motion.groundSpeed(), 1.0E-9D);
	}
}
