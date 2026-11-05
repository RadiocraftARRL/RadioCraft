package com.arrl.radiocraft.common.radio.adsb;

/**
 * One ADS-B report, as a beacon sends it about once a second (an airborne position, velocity and identification
 * squitter rolled into one): its 24-bit ICAO address, flight identification and squawk, where it really is in the
 * world (blocks; {@code height} is above sea level), its track, ground speed and vertical rate (blocks a second),
 * the game time it was sent, and the beacon block it came from ({@code beacon}, a packed {@code BlockPos}, so a
 * receiver can leave out its own craft). Pure, immutable.
 */
public record AdsbTarget(int icao, String callsign, int squawk, double x, double y, double z, double height,
		double heading, double groundSpeed, double verticalRate, long time, long beacon) {

	/** The name shown for it: its flight identification, or its ICAO address with none set. */
	public String label() {
		return callsign.isEmpty() ? AdsbMath.hex(icao) : callsign;
	}

	public int feet() {
		return AdsbMath.feet(height);
	}

	public int knots() {
		return AdsbMath.knots(groundSpeed);
	}

	public int feetPerMinute() {
		return AdsbMath.feetPerMinute(verticalRate);
	}
}
