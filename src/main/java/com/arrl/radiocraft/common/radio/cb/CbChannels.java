package com.arrl.radiocraft.common.radio.cb;

/**
 * The 40 US Citizens Band channels (FCC Part 95, 26.965-27.405 MHz, AM voice). They sit 10 kHz apart with gaps where
 * the radio-control frequencies are (channels 3/4, 7/8, 11/12, 15/16, 19/20), and channel 23 is out of order: it was
 * the last channel of the old 23-channel band, so 24 and 25 were fitted in below it when the band grew to 40 in 1977.
 * Channel 9 is for emergencies; 19 is the highway (trucker) channel. No game classes, so it unit tests on its own.
 */
public final class CbChannels {

	private CbChannels() {}

	public static final int FIRST = 1, LAST = 40, COUNT = 40;
	/** Emergencies and travellers' help. */
	public static final int EMERGENCY = 9;
	/** The highway channel. */
	public static final int HIGHWAY = 19;
	/** Channels are this far apart (where there is no gap). */
	public static final int SPACING_HZ = 10_000;

	private static final int[] KHZ = {
			26_965, 26_975, 26_985, 27_005, 27_015, 27_025, 27_035, 27_055, 27_065, 27_075,
			27_085, 27_105, 27_115, 27_125, 27_135, 27_155, 27_165, 27_175, 27_185, 27_205,
			27_215, 27_225, 27_255, 27_235, 27_245, 27_265, 27_275, 27_285, 27_295, 27_305,
			27_315, 27_325, 27_335, 27_345, 27_355, 27_365, 27_375, 27_385, 27_395, 27_405};

	/** Channel {@code channel}'s frequency in Hz (clamped to 1-40). */
	public static double hz(int channel) {
		return KHZ[clamp(channel) - 1] * 1000.0D;
	}

	/** {@code channel} kept inside 1-40. */
	public static int clamp(int channel) {
		return Math.max(FIRST, Math.min(LAST, channel));
	}

	/** The channel on {@code hz} (within 1 kHz), 0 if none. */
	public static int channelAt(double hz) {
		for(int i = 0; i < KHZ.length; i++)
			if(Math.abs(KHZ[i] * 1000.0D - hz) < 1000.0D)
				return i + 1;
		return 0;
	}

	/** The channel knob turned {@code steps} from {@code channel}: a rotary switch, it stops at 1 and 40. */
	public static int turn(int channel, int steps) {
		return clamp(channel + steps);
	}

	/** "27.185" for channel 19. */
	public static String mhz(int channel) {
		int khz = KHZ[clamp(channel) - 1];
		return (khz / 1000) + "." + String.format(java.util.Locale.ROOT, "%03d", khz % 1000);
	}
}
