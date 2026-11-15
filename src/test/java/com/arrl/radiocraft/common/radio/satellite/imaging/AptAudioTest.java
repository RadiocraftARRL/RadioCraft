package com.arrl.radiocraft.common.radio.satellite.imaging;

import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** The downlink's APT sound: a tone that follows the picture, and the tick-tock of the syncs (2026-11-12). */
class AptAudioTest {

	@Test
	void framesAreTwentyMillisecondsAndALineStartsWithSyncA() {
		AptAudio audio = new AptAudio();
		short[] first = audio.frame();
		assertEquals(AptAudio.FRAME, first.length);
		int full = 0;
		for(int i = 0; i < AptAudio.SYNC_SAMPLES; i++)
			if(Math.abs(first[i]) > Short.MAX_VALUE * 0.3D)
				full++;
		assertEquals(AptAudio.SYNC_SAMPLES, full, "sync A is a square wave at full level");
	}

	@Test
	void theToneIsLouderForABrighterPicture() {
		AptAudio dark = new AptAudio(), bright = new AptAudio();
		byte[] black = new byte[AptSwath.PIXELS], white = new byte[AptSwath.PIXELS];
		Arrays.fill(black, MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.LOWEST));
		Arrays.fill(white, MapColor.SNOW.getPackedId(MapColor.Brightness.HIGH));
		dark.line(black);
		bright.line(white);
		assertTrue(peak(bright.frame()) > peak(dark.frame()) * 2, "the picture modulates the 2,400 Hz tone");
	}

	private static int peak(short[] frame) {
		int peak = 0;
		for(int i = AptAudio.SYNC_SAMPLES + 10; i < frame.length; i++)
			peak = Math.max(peak, Math.abs(frame[i]));
		return peak;
	}
}
