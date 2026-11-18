package com.arrl.radiocraft.common.radio.voice;

import java.util.List;

/**
 * The sound of a Weather Observer's report (2026-11-18), meant to be told from APRS by ear: two short 1800 Hz pips,
 * then a slow 300 baud FSK burst low in the passband (mark 1070 Hz, space 1270 Hz, as Bell 103 sends), a warble where
 * APRS is a 1200 baud screech.
 */
public final class WeatherObserverAudio {

	public static final double BAUD = 300.0D;
	public static final double MARK_HZ = 1070.0D;
	public static final double SPACE_HZ = 1270.0D;
	public static final double PIP_HZ = 1800.0D;
	/** Each pip and the gap after it, seconds. */
	public static final double PIP_SECONDS = 0.08D;
	public static final int PREAMBLE_FLAGS = 6;
	private static final double LEVEL = 0.35D;

	private WeatherObserverAudio() {}

	/** The frames of a report of {@code bytes} bytes, its bits seeded from {@code seed}. */
	public static List<short[]> frames(int bytes, long seed) {
		double rate = RadioVoiceProcessor.SAMPLE_RATE;
		int pip = (int)Math.round(PIP_SECONDS * rate);
		double[] burst = Afsk.samples(bytes, seed, BAUD, MARK_HZ, SPACE_HZ, PREAMBLE_FLAGS);
		double[] out = new double[pip * 4 + burst.length];
		for(int p = 0; p < 2; p++) // Pip, gap, pip, gap.
			for(int i = 0; i < pip; i++) {
				double fade = Math.min(1.0D, Math.min(i, pip - i) / (rate * 0.005D)); // 5 ms edges, no clicks.
				out[p * 2 * pip + i] = Math.sin(2.0D * Math.PI * PIP_HZ * i / rate) * LEVEL * fade;
			}
		System.arraycopy(burst, 0, out, pip * 4, burst.length);
		return RepeaterAudio.frames(out);
	}
}
