package com.arrl.radiocraft.common.realworld;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResamplerTest {

	private static short[] sine(double hz, int rate, int samples, double amplitude) {
		short[] out = new short[samples];
		for(int i = 0; i < samples; i++)
			out[i] = (short)Math.round(Math.sin(2.0D * Math.PI * hz * i / rate) * amplitude);
		return out;
	}

	private static double rms(short[] pcm, int from) {
		double sum = 0.0D;
		for(int i = from; i < pcm.length; i++)
			sum += (double)pcm[i] * pcm[i];
		return Math.sqrt(sum / (pcm.length - from));
	}

	private static int upCrossings(short[] pcm, int from) {
		int count = 0;
		for(int i = Math.max(1, from); i < pcm.length; i++)
			if(pcm[i - 1] < 0 && pcm[i] >= 0)
				count++;
		return count;
	}

	/** {@code in} through a fresh resampler in blocks of {@code block} samples, joined. */
	private static short[] run(int inRate, int outRate, short[] in, int block) {
		Resampler resampler = new Resampler(inRate, outRate);
		short[] out = new short[0];
		for(int at = 0; at < in.length; at += block) {
			short[] part = resampler.process(in, at, Math.min(block, in.length - at));
			short[] joined = new short[out.length + part.length];
			System.arraycopy(out, 0, joined, 0, out.length);
			System.arraycopy(part, 0, joined, out.length, part.length);
			out = joined;
		}
		return out;
	}

	@Test
	void equalRatesPassThrough() {
		short[] in = sine(440, 48000, 960, 9000);
		assertArrayEquals(in, new Resampler(48000, 48000).process(in));
	}

	@Test
	void outputCountFollowsTheRatio() {
		short[] in = sine(500, 44100, 44100, 8000); // One second.
		assertEquals(48000, run(44100, 48000, in, 441).length, 40); // Less the filter's delay.
		assertEquals(8000, run(48000, 8000, sine(500, 48000, 48000, 8000), 960).length, 10);
		assertEquals(48000, run(8000, 48000, sine(500, 8000, 8000, 8000), 160).length, 100);
	}

	@Test
	void anyBlockSizeGivesTheSameOutput() {
		short[] in = sine(1234, 44100, 9000, 12000);
		short[] whole = run(44100, 48000, in, in.length);
		assertArrayEquals(whole, run(44100, 48000, in, 441));
		assertArrayEquals(whole, run(44100, 48000, in, 7));
		assertArrayEquals(whole, run(44100, 48000, in, 1));
		short[] down = run(48000, 11025, in, in.length);
		assertArrayEquals(down, run(48000, 11025, in, 960));
		assertArrayEquals(down, run(48000, 11025, in, 13));
	}

	@Test
	void aToneKeepsItsPitchAndLevel() {
		short[] in = sine(1000, 8000, 8000, 10000);
		short[] up = run(8000, 48000, in, 160);
		assertEquals(1000.0D * (up.length - 600) / 48000.0D, upCrossings(up, 600), 3.0D); // Still 1 kHz: 48 samples a cycle.
		assertEquals(rms(in, 100), rms(up, 600), rms(in, 100) * 0.03D);
		short[] down = run(48000, 44100, sine(1000, 48000, 48000, 10000), 960);
		assertEquals(1000.0D * (down.length - 600) / 44100.0D, upCrossings(down, 600), 3.0D);
		assertEquals(10000 / Math.sqrt(2.0D), rms(down, 600), 300.0D);
	}

	@Test
	void goingDownFiltersWhatWouldAlias() {
		// 6 kHz cannot exist at 8 kHz (Nyquist 4 kHz): it must be filtered out, not folded down to 2 kHz.
		short[] down = run(48000, 8000, sine(6000, 48000, 48000, 10000), 960);
		assertTrue(rms(down, 200) < 10000 * 0.05D, "6 kHz leaked through: " + rms(down, 200));
		// A voice tone well inside the band goes through.
		short[] voice = run(48000, 8000, sine(1000, 48000, 48000, 10000), 960);
		assertTrue(rms(voice, 200) > 10000 / Math.sqrt(2.0D) * 0.9D);
	}

	@Test
	void fullScaleDoesNotWrap() {
		short[] step = new short[4000];
		for(int i = 0; i < step.length; i++)
			step[i] = i < 2000 ? Short.MAX_VALUE : Short.MIN_VALUE; // Rings past full scale either side of the step.
		short[] out = run(48000, 44100, step, 480);
		for(int i = 200; i < 1600; i++)
			assertTrue(out[i] > 30000, "wrapped at " + i + ": " + out[i]);
		for(int i = 2100; i < 3500; i++)
			assertTrue(out[i] < -30000, "wrapped at " + i + ": " + out[i]);
	}

	@Test
	void ratesMustBePositive() {
		assertThrows(IllegalArgumentException.class, () -> new Resampler(0, 48000));
	}
}
