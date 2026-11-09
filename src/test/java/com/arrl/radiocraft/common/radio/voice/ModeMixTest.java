package com.arrl.radiocraft.common.radio.voice;

import com.arrl.radiocraft.common.radio.Band;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a receiver in one mode makes of a signal in another (2026-11-08). */
class ModeMixTest {

	private static final double CB19 = 27_185_000.0D;

	@Test
	void ssbHearsAmCleanOnTheCarrierAndWhistlesOffIt() {
		ModeMix.Result on = ModeMix.of(Modulation.SSB, Modulation.AM, Detuning.ssb(CB19, CB19, false), CB19, CB19);
		assertTrue(on.effect().audible() && on.carrierHz() == 0.0D && !on.garble(), "Zero beat: clean");
		ModeMix.Result off = ModeMix.of(Modulation.SSB, Modulation.AM, Detuning.ssb(CB19 + 1000.0D, CB19, false), CB19 + 1000.0D, CB19);
		assertTrue(off.effect().audible(), "Off the carrier: still heard");
		assertEquals(1000.0D, off.carrierHz(), 1e-6, "The carrier beats at the offset");
		assertTrue(off.effect().splatter() > 0.0D, "The far sideband garbles it a little");
	}

	@Test
	void ssbHearsFmAsAWarble() {
		ModeMix.Result fm = ModeMix.of(Modulation.SSB, Modulation.FM, Detuning.ssb(29_600_000.0D, 29_600_000.0D, false), 29_600_000.0D, 29_600_000.0D);
		assertTrue(fm.effect().audible() && fm.garble(), "Heard, as a garbled warble");
	}

	@Test
	void amHearsSsbOnlyAsQrmAndFmAsAWarble() {
		ModeMix.Result ssb = ModeMix.of(Modulation.AM, Modulation.SSB, Detuning.am(CB19, CB19, 1.0D), CB19, CB19);
		assertFalse(ssb.effect().audible(), "No carrier: SSB does not open an AM receiver");
		assertTrue(ssb.effect().qrm() > 0.0D, "but garbles an AM station it lands on");
		ModeMix.Result fm = ModeMix.of(Modulation.AM, Modulation.FM, Detuning.am(CB19, CB19, 1.0D), CB19, CB19);
		assertTrue(fm.effect().audible() && fm.garble(), "FM's carrier opens it: a warble, no words");
	}

	@Test
	void fmDoesNotHearAm() {
		ModeMix.Result am = ModeMix.of(Modulation.FM, Modulation.AM, Detuning.fm(CB19, CB19, 0.0D), CB19, CB19);
		assertFalse(am.effect().audible());
		assertTrue(am.effect().qrm() > 0.0D);
	}

	@Test
	void sameModeIsUntouched() {
		Detuning.Effect effect = Detuning.ssb(14_200_000.0D, 14_201_000.0D, false);
		assertEquals(effect, ModeMix.of(Modulation.SSB, Modulation.SSB, effect, 14_200_000.0D, 14_201_000.0D).effect());
	}

	@Test
	void usualModulationByBand() {
		assertEquals(Modulation.FM, Modulation.usual(new Band("2m", 500, 0, 0, 0, 0, 144_000_000, 148_000_000)));
		assertEquals(Modulation.SSB, Modulation.usual(new Band("20m", 300, 0, 0, 0, 0, 14_000_000, 14_350_000)));
		assertEquals(Modulation.AM, Modulation.usual(new Band(Band.CB, 600, 0, 0, 0, 0, 26_960_000, 27_410_000)));
	}

	@Test
	void garbleAndCarrierStayInRange() {
		short[] voice = new short[960];
		for(int i = 0; i < voice.length; i++)
			voice[i] = (short)(Math.sin(i * 0.05D) * 20000);
		ModeMix.Tones tones = new ModeMix.Tones();
		short[] warble = ModeMix.garble(voice, tones);
		short[] whistled = ModeMix.withCarrier(voice, 1000.0D, tones);
		assertEquals(voice.length, warble.length);
		assertEquals(voice.length, whistled.length);
	}

	@Test
	void ssbOnTheOtherSidebandIsOnlyQrm() {
		Detuning.Effect on = Detuning.ssb(27_255_000.0D, 27_255_000.0D, true);
		ModeMix.Result other = ModeMix.of(Modulation.SSB, Modulation.SSB, on, 27_255_000.0D, 27_255_000.0D, false);
		org.junit.jupiter.api.Assertions.assertFalse(other.effect().audible(), "USB is not heard on LSB");
		org.junit.jupiter.api.Assertions.assertTrue(other.effect().qrm() > 0.0D, "but it is there as QRM");
		org.junit.jupiter.api.Assertions.assertEquals(on, ModeMix.of(Modulation.SSB, Modulation.SSB, on, 27_255_000.0D, 27_255_000.0D, true).effect());
	}
}

