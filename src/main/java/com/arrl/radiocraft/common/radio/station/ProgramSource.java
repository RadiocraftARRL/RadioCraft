package com.arrl.radiocraft.common.radio.station;

import org.jetbrains.annotations.Nullable;

/**
 * Something an FM broadcast station plays as its program (2026-11-14, the maintainer's FM station): the tape player on
 * the transmitter's data wire, or an internet stream. The station's mixer ({@code FmStation}) pulls one frame at a time
 * on the server thread, 50 a second, and mixes the live mic and the station's voice over it.
 */
public interface ProgramSource {

	/** Samples in a frame: 20 ms of mono 16-bit PCM at 48 kHz (as {@code RepeaterAudio.FRAME}). */
	int FRAME = 960;

	/**
	 * The next 20 ms of program, {@link #FRAME} samples, or null for none just now (between tapes, buffering, stopped).
	 * Server thread. A source that is pulled advances; one that is not stays where it is.
	 */
	@Nullable
	short[] nextFrame();

	/** True while it has program to give (a tape playing or queued, a stream connected and buffered). */
	boolean live();

	/** What is playing, for the station's screen and the logs: a tape's name, a stream's title; empty if nothing. */
	default String nowPlaying() {
		return "";
	}

	/**
	 * True when the source is at a break between items (a tape just ended, before the next starts), where the station
	 * may slot in a forecast or its station ID without cutting anything off. A stream has none: it never asks.
	 */
	default boolean atBreak() {
		return false;
	}
}
