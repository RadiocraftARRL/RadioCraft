package com.arrl.radiocraft.common.radio.satellite.imaging;

import net.minecraft.world.level.material.MapColor;

/**
 * The sound of a weather satellite's downlink (2026-11-12), as an FM receiver plays NOAA APT: a 2,400 Hz tone whose
 * loudness follows the picture's brightness, with the famous "tick-tock" twice a second: the sync A burst (1,040 Hz) at
 * the start of each line and sync B (832 Hz) halfway, as the two channels of a real APT line start. The picture it plays
 * is the line scanned last; the real line rate is much higher here, so only the sound keeps APT's two lines a second.
 * Pure (no game classes beyond the map colours), unit tested. One per satellite; not thread safe.
 */
public final class AptAudio {

	public static final int RATE = 48_000, FRAME = 960;
	public static final double CARRIER_HZ = 2400.0D, SYNC_A_HZ = 1040.0D, SYNC_B_HZ = 832.0D;
	/** Samples in one APT line (half a second) and in each sync burst (7 cycles of sync A, about 7 ms). */
	static final int LINE_SAMPLES = RATE / 2, SYNC_SAMPLES = (int)(RATE * 7 / SYNC_A_HZ);
	private static final double LEVEL = 0.35D;

	private final double[] brightness = new double[AptSwath.PIXELS];
	private long sample = 0L;
	private double phase = 0.0D;

	public AptAudio() {
		java.util.Arrays.fill(brightness, 0.4D);
	}

	/** The line the tone plays from now on. */
	public void line(byte[] pixels) {
		for(int i = 0; i < brightness.length && i < pixels.length; i++)
			brightness[i] = luminance(pixels[i]);
	}

	/** The brightness of map colour {@code id}, 0 to 1. */
	static double luminance(byte id) {
		int argb = MapColor.getColorFromPackedId(id & 0xFF); // ABGR
		int r = argb & 0xFF, g = argb >> 8 & 0xFF, b = argb >> 16 & 0xFF;
		return (0.299D * r + 0.587D * g + 0.114D * b) / 255.0D;
	}

	/** The next 20 ms of sound. */
	public short[] frame() {
		short[] out = new short[FRAME];
		for(int i = 0; i < FRAME; i++, sample++) {
			int inLine = (int)(sample % LINE_SAMPLES);
			double v;
			if(inLine < SYNC_SAMPLES)
				v = square(inLine, SYNC_A_HZ);
			else if(inLine >= LINE_SAMPLES / 2 && inLine < LINE_SAMPLES / 2 + SYNC_SAMPLES)
				v = square(inLine - LINE_SAMPLES / 2, SYNC_B_HZ);
			else {
				int half = inLine % (LINE_SAMPLES / 2);
				int pixel = (int)((long)half * brightness.length / (LINE_SAMPLES / 2));
				v = Math.sin(phase) * (0.15D + 0.85D * brightness[Math.min(brightness.length - 1, pixel)]);
			}
			phase += 2.0D * Math.PI * CARRIER_HZ / RATE;
			if(phase > 2.0D * Math.PI)
				phase -= 2.0D * Math.PI;
			out[i] = (short)Math.round(v * LEVEL * Short.MAX_VALUE);
		}
		return out;
	}

	private static double square(int n, double hz) {
		return (n * hz / RATE) % 1.0D < 0.5D ? 1.0D : -1.0D;
	}
}
