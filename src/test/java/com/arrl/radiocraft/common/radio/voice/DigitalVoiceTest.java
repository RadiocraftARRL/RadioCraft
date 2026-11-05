package com.arrl.radiocraft.common.radio.voice;

import com.arrl.radiocraft.common.radio.voice.DigitalVoice.Decoder;
import com.arrl.radiocraft.common.radio.voice.DigitalVoice.Mode;
import com.arrl.radiocraft.common.radio.voice.DigitalVoice.Reception;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Digital voice: the cliff where FM would be 70% static, who decodes what, and the noise it makes otherwise. */
class DigitalVoiceTest {

	private static short[] tone() {
		short[] pcm = new short[960];
		for(int i = 0; i < pcm.length; i++)
			pcm[i] = (short)(Math.sin(2 * Math.PI * 440 * i / 48000.0D) * 8000);
		return pcm;
	}

	@Test
	void theCliffIsWhereFmWouldBeSeventyPercentStatic() {
		assertEquals(0.7D, DigitalVoice.fmStaticShare(DigitalVoice.CLIFF), 1e-6D);
		assertTrue(DigitalVoice.CLIFF > ReceiverMix.FM_STATIC_ONLY && DigitalVoice.CLIFF < ReceiverMix.FM_QUIETING, "cliff " + DigitalVoice.CLIFF);
		assertTrue(DigitalVoice.fmStaticShare(DigitalVoice.CLIFF * 0.9D) > 0.7D, "weaker is noisier on FM");
		assertTrue(DigitalVoice.EDGE > DigitalVoice.CLIFF);
	}

	@Test
	void analogIsAlwaysAnalog() {
		assertEquals(Reception.ANALOG, DigitalVoice.receive(null, Mode.ANALOG, 0.01D));
		assertEquals(Reception.ANALOG, DigitalVoice.receive(new Decoder(5), Mode.ANALOG, 1.0D));
	}

	@Test
	void digitalIsCleanUntilTheCliffThenGone() {
		Decoder decoder = Decoder.CLEAR_ONLY;
		assertEquals(Reception.CLEAR, DigitalVoice.receive(decoder, Mode.DIGITAL_CLEAR, 0.5D));
		assertEquals(Reception.WARBLE, DigitalVoice.receive(decoder, Mode.DIGITAL_CLEAR, (DigitalVoice.CLIFF + DigitalVoice.EDGE) / 2.0D));
		assertEquals(Reception.DROPPED, DigitalVoice.receive(decoder, Mode.DIGITAL_CLEAR, DigitalVoice.CLIFF * 0.99D));
		assertEquals(1.0D, DigitalVoice.warbleDepth(DigitalVoice.CLIFF), 1e-9D);
		assertEquals(0.0D, DigitalVoice.warbleDepth(DigitalVoice.EDGE), 1e-9D);
	}

	@Test
	void encryptionNeedsTheKey() {
		Mode encrypted = new Mode(true, 1234);
		assertTrue(encrypted.encrypted());
		assertEquals(Reception.CLEAR, DigitalVoice.receive(new Decoder(1234), encrypted, 0.8D));
		assertEquals(Reception.SCRAMBLED, DigitalVoice.receive(new Decoder(99), encrypted, 0.8D));
		assertEquals(Reception.SCRAMBLED, DigitalVoice.receive(Decoder.CLEAR_ONLY, encrypted, 0.8D));
		assertEquals(Reception.CLEAR, DigitalVoice.receive(new Decoder(1234), Mode.DIGITAL_CLEAR, 0.8D), "a keyed radio still decodes clear digital");
		assertEquals(Reception.BUZZ, DigitalVoice.receive(null, encrypted, 0.8D), "an analog radio hears a buzz");
		assertEquals(Reception.BUZZ, DigitalVoice.receive(null, Mode.DIGITAL_CLEAR, 0.01D), "an analog radio buzzes even weak (and fades as FM)");
		assertFalse(new Mode(false, 77).encrypted(), "analog carries no key");
	}

	@Test
	void theAudioIsWhatTheReceiverMakesOfIt() {
		short[] pcm = tone();
		assertSame(pcm, DigitalVoice.render(Reception.CLEAR, pcm, 1.0D, 1L));
		assertSame(pcm, DigitalVoice.render(Reception.ANALOG, pcm, 1.0D, 1L));
		short[] scrambled = DigitalVoice.render(Reception.SCRAMBLED, pcm, 1.0D, 42L);
		assertEquals(pcm.length, scrambled.length);
		assertFalse(java.util.Arrays.equals(pcm, scrambled), "scrambled is not the voice");
		short[] buzz = DigitalVoice.buzz(pcm, 7L);
		assertEquals(pcm.length, buzz.length);
		assertTrue(DigitalVoice.rms(buzz) > 1000.0D, "the buzz is loud: " + DigitalVoice.rms(buzz));
		assertArrayEquals(pcm, DigitalVoice.warble(pcm, 0.0D, 3L), "no warble at depth 0");
		assertArrayEquals(scrambled, DigitalVoice.scramble(pcm, 42L), "the same seed scrambles the same way");
		assertArrayEquals(tone(), pcm, "the input is left alone");
	}

	@Test
	void pushAndPopNest() {
		assertEquals(Mode.ANALOG, DigitalVoice.current());
		Mode outer = DigitalVoice.push(new Mode(true, 5));
		Mode inner = DigitalVoice.push(Mode.DIGITAL_CLEAR);
		assertEquals(Mode.DIGITAL_CLEAR, DigitalVoice.current());
		DigitalVoice.pop(inner);
		assertEquals(new Mode(true, 5), DigitalVoice.current());
		DigitalVoice.pop(outer);
		assertEquals(Mode.ANALOG, DigitalVoice.current());
	}
}
