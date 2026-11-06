package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The MUSIC visualizer's bands (1.12 music player fixes): spread in pitch, so every bar moves with ordinary music. */
class MusicSpectrumTest {

	private static final double RATE = 44100.0D;

	@Test
	void bandsCoverTheRangeInOrderAndAreNeverEmpty() {
		int[] edges = MusicSpectrum.bandEdges(24, RATE, MusicSpectrum.FFT, 55.0D, 5500.0D);
		assertEquals(25, edges.length);
		for(int b = 0; b < 24; b++)
			assertTrue(edges[b + 1] > edges[b], "band " + b + " is empty");
		double binHz = RATE / MusicSpectrum.FFT;
		assertEquals(55.0D, edges[0] * binHz, binHz);
		assertEquals(5500.0D, edges[24] * binHz, binHz);
		assertTrue(edges[24] <= MusicSpectrum.FFT / 2);
	}

	@Test
	void bandsWidenWithPitch() {
		int[] edges = MusicSpectrum.bandEdges(24, RATE, MusicSpectrum.FFT, 55.0D, 5500.0D);
		// Spaced in pitch, not in Hz: the top band holds many more bins than one in the middle, which holds more than the bottom one.
		int bottom = edges[1] - edges[0], middle = edges[13] - edges[12], top = edges[24] - edges[23];
		assertTrue(top > 3 * middle, "top " + top + " middle " + middle);
		assertTrue(middle >= bottom);
		// Each octave of the range gets about the same number of bars (6.6 octaves, 24 bars: 3 to 4 each).
		MusicSpectrum spectrum = new MusicSpectrum(24, RATE, 55.0D, 5500.0D);
		int a2 = spectrum.bandOf(bin(110.0D)), a3 = spectrum.bandOf(bin(220.0D)), a4 = spectrum.bandOf(bin(440.0D)), a5 = spectrum.bandOf(bin(880.0D));
		assertTrue(a3 - a2 >= 2 && a4 - a3 >= 3 && a5 - a4 >= 3, a2 + " " + a3 + " " + a4 + " " + a5);
	}

	@Test
	void forcedUpBandsStayInsideTheSpectrum() {
		// More bars than bins: every band one bin wide, the top still at Nyquist or below.
		int[] edges = MusicSpectrum.bandEdges(40, 8000.0D, 64, 50.0D, 4000.0D);
		for(int b = 0; b < 40; b++)
			assertTrue(edges[b + 1] > edges[b] || edges[b + 1] == edges[b] && edges[b] <= 0, "band " + b);
		assertTrue(edges[40] <= 32);
	}

	@Test
	void aToneLightsItsOwnBar() {
		for(double hz : new double[] {82.4D, 196.0D, 440.0D, 1046.5D, 3136.0D}) {
			MusicSpectrum spectrum = new MusicSpectrum(24, RATE, 55.0D, 5500.0D);
			feed(spectrum, RATE * 0.5D, t -> 0.3D * Math.sin(2.0D * Math.PI * hz * t));
			int expected = spectrum.bandOf(bin(hz));
			int loudest = 0;
			for(int b = 1; b < 24; b++)
				if(spectrum.level(b) > spectrum.level(loudest))
					loudest = b;
			assertEquals(expected, loudest, "a " + hz + " Hz tone");
			assertTrue(spectrum.level(expected) > 0.9F);
			// Far from the tone the bars stay dark.
			for(int b = 0; b < 24; b++)
				if(Math.abs(b - expected) > 3)
					assertTrue(spectrum.level(b) < 0.5F, hz + " Hz lit bar " + b + " to " + spectrum.level(b));
		}
	}

	@Test
	void musicSpreadOverTheOctavesMovesEveryBar() {
		// A bass line, chords and a melody: notes from E1 to E8, louder low down as real tunes are.
		MusicSpectrum spectrum = new MusicSpectrum(24, RATE, 55.0D, 5500.0D);
		float[] peak = new float[24];
		for(int note = 28; note <= 112; note++) {
			double hz = 440.0D * Math.pow(2.0D, (note - 69) / 12.0D), amp = 0.4D * Math.pow(0.97D, note - 28);
			feed(spectrum, RATE * 0.06D, t -> amp * Math.sin(2.0D * Math.PI * hz * t));
			for(int b = 0; b < 24; b++)
				peak[b] = Math.max(peak[b], spectrum.level(b));
		}
		for(int b = 0; b < 24; b++)
			assertTrue(peak[b] > 0.6F, "bar " + b + " only reached " + peak[b]);
	}

	@Test
	void barsFallWhenTheSoundStops() {
		MusicSpectrum spectrum = new MusicSpectrum(24, RATE, 55.0D, 5500.0D);
		feed(spectrum, RATE * 0.3D, t -> 0.3D * Math.sin(2.0D * Math.PI * 440.0D * t));
		int bar = spectrum.bandOf(bin(440.0D));
		float lit = spectrum.level(bar);
		feed(spectrum, RATE * 0.3D, t -> 0.0D);
		assertTrue(spectrum.level(bar) < lit - 0.3F, "still " + spectrum.level(bar));
		for(int i = 0; i < 40; i++)
			spectrum.fall();
		assertEquals(0.0F, spectrum.level(bar));
	}

	@Test
	void barsShowWhatHasBeenHeardNotWhatWasFed() {
		MusicSpectrum spectrum = new MusicSpectrum(24, RATE, 55.0D, 5500.0D);
		int bar = spectrum.bandOf(bin(440.0D));
		spectrum.played(0L); // Nothing heard yet: the bars wait for the sound card.
		feed(spectrum, RATE * 0.3D, t -> 0.3D * Math.sin(2.0D * Math.PI * 440.0D * t));
		assertEquals(0.0F, spectrum.level(bar), "ran ahead of the sound");
		spectrum.played((long)(RATE * 0.3D)); // The tone has reached the ears.
		float lit = spectrum.level(bar);
		assertTrue(lit > 0.9F, "only " + lit);
		feed(spectrum, RATE * 0.3D, t -> 0.0D); // Silence handed over but not heard yet: the tone still shows.
		assertEquals(lit, spectrum.level(bar));
		spectrum.played((long)(RATE * 0.6D));
		assertTrue(spectrum.level(bar) < lit - 0.3F, "still " + spectrum.level(bar));
	}

	private static int bin(double hz) {
		return (int)Math.round(hz * MusicSpectrum.FFT / RATE);
	}

	private static void feed(MusicSpectrum spectrum, double samples, java.util.function.DoubleUnaryOperator wave) {
		float[] chunk = new float[512];
		int n = (int)samples;
		for(int done = 0; done < n; ) {
			int count = Math.min(chunk.length, n - done);
			for(int i = 0; i < count; i++)
				chunk[i] = (float)wave.applyAsDouble((done + i) / RATE);
			spectrum.feed(chunk, count);
			done += count;
		}
	}
}
