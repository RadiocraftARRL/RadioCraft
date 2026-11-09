package com.arrl.radiocraft.common.radio.cb;

import com.arrl.radiocraft.common.radio.voice.Detuning;
import com.arrl.radiocraft.common.radio.voice.ReceiverMix;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The CB channel table, a CB radio's state, the speech compressor and the AM receive mode (2026-11-08). */
class CbRadioTest {

	@Test
	void channelsMatchTheFccTable() {
		assertEquals(26_965_000.0D, CbChannels.hz(1));
		assertEquals(27_065_000.0D, CbChannels.hz(9));
		assertEquals(27_185_000.0D, CbChannels.hz(19));
		assertEquals(27_255_000.0D, CbChannels.hz(23)); // Out of order: the old band's top channel.
		assertEquals(27_235_000.0D, CbChannels.hz(24));
		assertEquals(27_245_000.0D, CbChannels.hz(25));
		assertEquals(27_405_000.0D, CbChannels.hz(40));
		assertEquals("27.185", CbChannels.mhz(19));
		assertEquals(23, CbChannels.channelAt(27_255_000.0D));
		assertEquals(0, CbChannels.channelAt(27_000_000.0D));
		for(int ch = 1; ch <= 40; ch++)
			assertEquals(ch, CbChannels.channelAt(CbChannels.hz(ch)));
	}

	@Test
	void channelKnobStopsAtTheEnds() {
		assertEquals(40, CbChannels.turn(39, 5));
		assertEquals(1, CbChannels.turn(2, -5));
		assertEquals(20, CbRadioState.DEFAULT.press(CbRadioState.Key.CHANNEL, 1).channel());
	}

	@Test
	void controls() {
		CbRadioState s = CbRadioState.DEFAULT;
		assertEquals(19, s.channel());
		assertEquals(9, s.press(CbRadioState.Key.CHANNEL_9, 0).channel());
		assertTrue(s.press(CbRadioState.Key.COMPRESSOR, 0).compressor());
		assertTrue(s.press(CbRadioState.Key.METER, 0).swrMeter());
		assertEquals(10, s.press(CbRadioState.Key.SQUELCH, 40).squelch());
		assertEquals(0, s.press(CbRadioState.Key.VOLUME, -40).volume());
		assertEquals(s, s.press(99, 1)); // No such key.
		assertEquals("19", s.displayLine());
		assertEquals("27.185 AM", s.displayStatus());
	}

	@Test
	void compressorLiftsAQuietVoice() {
		SpeechCompressor compressor = new SpeechCompressor();
		short[] quiet = new short[4800];
		for(int i = 0; i < quiet.length; i++)
			quiet[i] = (short)(Math.sin(2 * Math.PI * 440 * i / 48000.0D) * 3000);
		double before = SpeechCompressor.peak(quiet);
		short[] out = null;
		for(int frame = 0; frame < 5; frame++)
			out = compressor.process(quiet.clone());
		assertTrue(SpeechCompressor.peak(out) > before * 2.5D, "A quiet voice comes up");
		assertTrue(compressor.lastPeak() < 1.0D, "But never past full scale");
		short[] loud = quiet.clone();
		for(int i = 0; i < loud.length; i++)
			loud[i] = (short)(loud[i] * 10);
		assertTrue(SpeechCompressor.peak(new SpeechCompressor().process(loud)) <= 1.0D);
	}

	@Test
	void amHearsItsChannelAndBleedsFromTheNext() {
		assertEquals(Detuning.ON_FREQUENCY, Detuning.am(27_185_000.0D, 27_185_000.0D, 1.0D));
		Detuning.Effect near = Detuning.am(27_185_000.0D, 27_186_000.0D, 1.0D);
		assertTrue(near.audible() && near.strength() < 1.0D && near.pitchShiftHz() == 0.0D, "1 kHz off: heard, a little weaker, no pitch shift");
		Detuning.Effect next = Detuning.am(27_185_000.0D, 27_195_000.0D, 1.0D);
		assertFalse(next.audible(), "The next channel is not heard on its own");
		assertTrue(next.qrm() > 0.0D, "but bleeds over");
		assertTrue(Detuning.am(27_185_000.0D, 27_205_000.0D, 1.0D).isNone(), "Two channels away: nothing");
		assertTrue(Detuning.am(27_185_000.0D, 27_205_000.0D, 3.0D).qrm() > 0.0D, "unless it splatters out of band");
	}

	@Test
	void groundPlaneIsMatchedOnCbAndUsableOnTenMetres() {
		double ch19 = com.arrl.radiocraft.common.radio.antenna.types.CbGroundPlaneAntennaType.swr(27_185_000.0D);
		double edge = com.arrl.radiocraft.common.radio.antenna.types.CbGroundPlaneAntennaType.swr(26_965_000.0D);
		double tenBottom = com.arrl.radiocraft.common.radio.antenna.types.CbGroundPlaneAntennaType.swr(28_000_000.0D);
		assertTrue(ch19 < 1.15D && edge < 1.35D, "Matched across CB: " + ch19 + ", " + edge);
		assertEquals(1.8D, tenBottom, 0.05D, "About 1.8:1 at the bottom of 10m");
		assertTrue(com.arrl.radiocraft.common.radio.antenna.types.CbGroundPlaneAntennaType.swr(29_600_000.0D) > 2.5D, "Worse at the top of 10m");
		assertEquals(10.0D, com.arrl.radiocraft.common.radio.antenna.types.CbGroundPlaneAntennaType.swr(14_200_000.0D), "Nothing on 20m");
	}

	@Test
	void amHissesAsItWeakens() {
		assertEquals(0.0D, ReceiverMix.amHiss(0.8D, 0.1D));
		double weak = ReceiverMix.amHiss(0.1D, 0.05D), weaker = ReceiverMix.amHiss(0.03D, 0.05D);
		assertTrue(weak > 0.0D && weaker > weak, "Weaker signals hiss more");
		assertTrue(ReceiverMix.amHiss(0.4D, 0.4D) > 0.0D, "A noisy band hisses under a signal a quiet band would copy clean");
		assertTrue(ReceiverMix.amLevel(0.1D, 0.1D) > ReceiverMix.voiceLevel(0.1D, 0.1D), "The AGC holds AM up better than SSB");
	}

	@Test
	void functionKnobClarifierAndRfGain() {
		CbRadioState s = CbRadioState.DEFAULT;
		assertEquals(CbRadioState.Mode.USB, s.press(CbRadioState.Key.FUNCTION, 1).mode());
		assertEquals(CbRadioState.Mode.LSB, s.press(CbRadioState.Key.FUNCTION, 5).mode());
		assertEquals(CbRadioState.Mode.AM, s.press(CbRadioState.Key.FUNCTION, 2).press(CbRadioState.Key.FUNCTION, -9).mode());
		assertEquals(CbRadioState.Mode.FM, s.press(CbRadioState.Key.AM_FM, 0).mode());
		assertEquals(CbRadioState.Mode.AM, s.press(CbRadioState.Key.AM_FM, 0).press(CbRadioState.Key.AM_FM, 0).mode());
		assertEquals(1000, s.press(CbRadioState.Key.CLARIFIER, 40).clarifierHz());
		assertEquals(1.0D, s.rfGainFactor(), 1e-9);
		assertTrue(s.press(CbRadioState.Key.RF_GAIN, -10).rfGainFactor() < 0.1D);
		assertEquals(1.0D, s.impulseNoiseFactor());
		assertTrue(s.press(CbRadioState.Key.NB, 0).impulseNoiseFactor() < s.press(CbRadioState.Key.ANL, 0).impulseNoiseFactor(), "The blanker beats the limiter");
		CbRadioState both = s.press(CbRadioState.Key.NB_ANL, 0);
		assertTrue(both.nb() && both.anl());
		CbRadioState plain = s.press(CbRadioState.Key.FUNCTION, 1).press(CbRadioState.Key.CLARIFIER, 3).withoutDeluxeControls();
		assertEquals(CbRadioState.Mode.AM, plain.mode());
		assertEquals(0, plain.clarifier());
		assertEquals("27.185 USB", s.press(CbRadioState.Key.FUNCTION, 1).displayStatus());
	}
}

