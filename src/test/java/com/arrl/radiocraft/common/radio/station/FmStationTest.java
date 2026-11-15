package com.arrl.radiocraft.common.radio.station;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The FM station's mixer (2026-11-14): the carrier, the mic's duck, waiting warnings, forecasts at breaks, the fallback. */
class FmStationTest {

	/** A steady tone of {@code level}; at a break every {@code itemFrames} frames when that is above 0. */
	static final class Tone implements ProgramSource {
		final short level;
		final int itemFrames;
		boolean live = true;
		int pulled;

		Tone(int level, int itemFrames) {
			this.level = (short)level;
			this.itemFrames = itemFrames;
		}

		@Override
		public short[] nextFrame() {
			if(!live)
				return null;
			pulled++;
			short[] f = new short[FRAME];
			for(int i = 0; i < FRAME; i++)
				f[i] = (i & 1) == 0 ? level : (short)-level;
			return f;
		}

		@Override
		public boolean live() {
			return live;
		}

		@Override
		public boolean atBreak() {
			return itemFrames > 0 && pulled % itemFrames == 0;
		}
	}

	/** Says a short burst for anything asked. */
	static final class Speaker implements FmStation.Voice {
		int alerts, forecasts, ids;

		private static short[] burst() {
			short[] s = new short[FmStation.FRAME * 3];
			java.util.Arrays.fill(s, (short)4000);
			return s;
		}

		@Override
		public short[] alert(String text) {
			alerts++;
			return burst();
		}

		@Override
		public short[] forecast() {
			forecasts++;
			return burst();
		}

		@Override
		public short[] stationId() {
			ids++;
			return burst();
		}
	}

	private static int peak(short[] f) {
		int p = 0;
		for(short s : f)
			p = Math.max(p, Math.abs((int)s));
		return p;
	}

	@Test
	void aBareCarrierIsQuietFrames() {
		FmStation station = new FmStation();
		short[] f = station.frame(null, null, new Speaker());
		assertEquals(FmStation.FRAME, f.length);
		assertEquals(0, peak(f), "nothing to send: a quiet carrier");
	}

	@Test
	void theMicDucksTheProgramAndItComesBack() {
		FmStation station = new FmStation();
		Tone tape = new Tone(10000, 0);
		Speaker voice = new Speaker();
		int open = 0;
		for(int i = 0; i < 20; i++)
			open = peak(station.frame(tape, tape, voice));
		station.setMicKeyed(true);
		for(int i = 0; i < 20; i++)
			station.frame(tape, tape, voice);
		assertEquals(station.duckLevel(), station.duckGain(), 0.02F, "ducked under the mic");
		int ducked = peak(station.frame(tape, tape, voice));
		assertTrue(ducked < open * 0.3, "program under the mic: " + ducked + " of " + open);
		station.setMicKeyed(false);
		for(int i = 0; i < FmStation.RELEASE_FRAMES + 20; i++)
			station.frame(tape, tape, voice);
		assertEquals(1.0F, station.duckGain(), 1e-6F, "back up after the release");
	}

	@Test
	void aWarningWaitsForTheMicThenInterrupts() {
		FmStation station = new FmStation();
		Tone tape = new Tone(8000, 0);
		Speaker voice = new Speaker();
		station.setMicKeyed(true);
		station.queueAlert("Tornado warning");
		for(int i = 0; i < 30; i++)
			station.frame(tape, tape, voice);
		assertEquals(0, voice.alerts, "the warning went out over the live mic");
		assertEquals(1, station.alertsWaiting());
		station.setMicKeyed(false);
		for(int i = 0; i < 20; i++)
			station.frame(tape, tape, voice);
		assertEquals(1, voice.alerts, "the warning did not go out once the mic was free");
		int before = tape.pulled;
		station.queueAlert("Second");
		station.frame(tape, tape, voice);
		assertEquals(FmStation.Speaking.ALERT, station.speaking());
		station.frame(tape, tape, voice);
		assertEquals(before, tape.pulled, "a tape keeps its place under a warning");
	}

	@Test
	void forecastsWaitForABreak() {
		FmStation station = new FmStation();
		Tone tape = new Tone(6000, 50); // A break every 50 frames.
		Speaker voice = new Speaker();
		station.frame(tape, tape, voice); // Now 1 frame into an item.
		station.forecastDue();
		for(int i = 0; i < 30; i++)
			station.frame(tape, tape, voice);
		assertEquals(0, voice.forecasts, "the forecast cut a tape off");
		for(int i = 0; i < 40; i++)
			station.frame(tape, tape, voice);
		assertEquals(1, voice.forecasts, "the forecast did not go out at the break");
	}

	@Test
	void theTapeStandsInForADeadStream() {
		FmStation station = new FmStation();
		station.setProgram(FmStation.Program.STREAM);
		Tone stream = new Tone(5000, 0), tape = new Tone(3000, 0);
		Speaker voice = new Speaker();
		stream.live = false;
		for(int i = 0; i < FmStation.DEAD_AIR_FRAMES + 5; i++)
			station.frame(stream, tape, voice);
		assertTrue(station.onFallback(), "no fallback to the tape");
		assertTrue(tape.pulled > 0);
		stream.live = true;
		for(int i = 0; i < FmStation.BACK_FRAMES + 5; i++)
			station.frame(stream, tape, voice);
		assertFalse(station.onFallback(), "did not go back to the stream");
	}

	@Test
	void limiterBringsLoudDown() {
		FmStation station = new FmStation();
		Tone loud = new Tone(32000, 0);
		Speaker voice = new Speaker();
		int last = 0;
		for(int i = 0; i < 50; i++)
			last = peak(station.frame(loud, loud, voice));
		assertTrue(last < 32000 * 0.8, "loud program not limited: " + last);
	}
}
