package com.arrl.radiocraft.common.radio.voice;

import com.arrl.radiocraft.common.radio.microwave.MicrowaveMath;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a link does to RIGCTL's remote audio (2026-11-09): wire is clean, microwave delays, roughens and drops with its grade. */
class LinkAudioTest {

	private static short[] tone() {
		short[] pcm = new short[960];
		for(int i = 0; i < pcm.length; i++)
			pcm[i] = (short)Math.round(Math.sin(i * 0.1D) * 12_345.0D);
		return pcm;
	}

	@Test
	void wireIsClean() {
		LinkAudio.Impairment wire = LinkAudio.of(null);
		assertTrue(wire.clean());
		short[] pcm = tone();
		assertSame(pcm, LinkAudio.roughen(pcm, wire, new Random(1)), "clean audio is passed as it is");
		LinkAudio.Line line = new LinkAudio.Line(wire, new Random(1));
		line.offer(pcm, 1_000L);
		assertEquals(1, line.due(1_000L).size(), "wire has no delay");
	}

	@Test
	void gradesGetWorse() {
		LinkAudio.Impairment good = LinkAudio.of(MicrowaveMath.Grade.GOOD), fair = LinkAudio.of(MicrowaveMath.Grade.FAIR),
				poor = LinkAudio.of(MicrowaveMath.Grade.POOR);
		assertFalse(good.clean(), "microwave is never quite clean: it delays");
		assertEquals(0.0D, good.drop());
		assertTrue(good.delayNanos() < fair.delayNanos() && fair.delayNanos() < poor.delayNanos());
		assertTrue(fair.drop() < poor.drop() && fair.bits() > poor.bits());
		assertEquals(poor, LinkAudio.of(MicrowaveMath.Grade.WEAK), "too weak breaks up like poor");
	}

	@Test
	void framesWaitTheirDelay() {
		LinkAudio.Impairment good = LinkAudio.of(MicrowaveMath.Grade.GOOD);
		LinkAudio.Line line = new LinkAudio.Line(good, new Random(2));
		line.offer(tone(), 0L);
		line.offer(tone(), 20_000_000L);
		assertTrue(line.due(good.delayNanos() - 1L).isEmpty(), "out before its delay");
		assertEquals(1, line.due(good.delayNanos()).size());
		assertEquals(1, line.due(good.delayNanos() + 20_000_000L).size());
		assertEquals(0, line.waiting());
	}

	@Test
	void poorLinksDropAndRoughen() {
		LinkAudio.Impairment poor = LinkAudio.of(MicrowaveMath.Grade.POOR);
		LinkAudio.Line line = new LinkAudio.Line(poor, new Random(3));
		int kept = 0;
		for(int i = 0; i < 1000; i++) { // Drained as it goes: a line holds at most MAX_HELD frames.
			line.offer(tone(), i);
			kept += line.due(Long.MAX_VALUE).size();
		}
		assertTrue(kept < 1000 && kept > 800, "about one frame in eight is lost: " + kept + " kept");
		short[] rough = LinkAudio.roughen(tone(), new LinkAudio.Impairment(0L, 0.0D, 7, 0.0D), new Random(4));
		for(short sample : rough)
			assertEquals(0, sample & ((1 << 9) - 1), "the low nine bits are gone at 7 bits");
	}

	@Test
	void aLineNobodyDrainsStaysSmall() {
		LinkAudio.Line line = new LinkAudio.Line(LinkAudio.of(MicrowaveMath.Grade.GOOD), new Random(5));
		for(int i = 0; i < LinkAudio.MAX_HELD * 3; i++)
			line.offer(tone(), 0L);
		assertEquals(LinkAudio.MAX_HELD, line.waiting());
	}
}
