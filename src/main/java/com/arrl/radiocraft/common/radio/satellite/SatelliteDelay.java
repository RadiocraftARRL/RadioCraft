package com.arrl.radiocraft.common.radio.satellite;

/**
 * How long a satellite holds what it hears before it goes back out (2026-11-12, maintainer): the transit delay. Pure
 * numbers, no game classes, so it is unit tested.
 * <p>
 * Voice is held a little (you hear yourself come back, as through a real bird), never so long a conversation breaks
 * down; the frames keep their order and spacing, only later ({@link SatelliteRadio} buffers the stream). A digipeat is
 * held longer, a mailbox answer longest (the board on board has to work). A distress relay is never held: it goes out
 * at once. A geostationary satellite takes half of each (the maintainer's call, 2026-11-12: "since they're lower"),
 * but no data reply comes back sooner than {@link #MIN_REPLY_TICKS}: the sender's radio is deaf until it has finished
 * sending, so an earlier answer would be lost.
 */
public final class SatelliteDelay {

	/** What is passing through. */
	public enum Kind { VOICE, DIGIPEAT, MAILBOX, DISTRESS }

	/** Voice through an orbiting satellite, ms: 300, a noticeable echo you can still talk through. */
	public static final int VOICE_MS = 300;
	/** An APRS digipeat, ticks: 2 s. */
	public static final int DIGIPEAT_TICKS = 40;
	/** A mailbox (PACSAT) answer, ticks: 4 s, the board on board reads and answers. */
	public static final int MAILBOX_TICKS = 80;
	/** A distress relay, ticks: none, a safety-of-life relay goes out at once. */
	public static final int DISTRESS_TICKS = 0;
	/** The shortest a data reply is held, ticks: the sender's radio is deaf until it finishes sending (1 s). */
	public static final int MIN_REPLY_TICKS = 20;
	/** How much shorter a geostationary satellite's delays are: half. */
	public static final int GEO_DIVISOR = 2;
	/** One voice frame, ms. */
	public static final int FRAME_MS = 20;

	private SatelliteDelay() {}

	/** How long voice is held, ms: {@link #VOICE_MS}, half that for a geostationary satellite. */
	public static int voiceMillis(boolean geostationary) {
		return geostationary ? VOICE_MS / GEO_DIVISOR : VOICE_MS;
	}

	/** The same in voice frames (rounded up). */
	public static int voiceFrames(boolean geostationary) {
		return (voiceMillis(geostationary) + FRAME_MS - 1) / FRAME_MS;
	}

	/** When a voice frame heard at {@code heardMillis} goes out again, ms (the same clock). */
	public static long voiceDue(long heardMillis, boolean geostationary) {
		return heardMillis + voiceMillis(geostationary);
	}

	/**
	 * How long {@code kind} is held, ticks: voice as {@link #voiceMillis} (rounded up to a tick), data as above, halved
	 * for a geostationary satellite but never under {@link #MIN_REPLY_TICKS}; a distress relay never.
	 */
	public static int ticks(Kind kind, boolean geostationary) {
		return switch(kind) {
			case VOICE -> (voiceMillis(geostationary) + 49) / 50;
			case DISTRESS -> DISTRESS_TICKS;
			case DIGIPEAT -> data(DIGIPEAT_TICKS, geostationary);
			case MAILBOX -> data(MAILBOX_TICKS, geostationary);
		};
	}

	private static int data(int ticks, boolean geostationary) {
		return Math.max(MIN_REPLY_TICKS, geostationary ? ticks / GEO_DIVISOR : ticks);
	}
}
