package com.arrl.radiocraft.common.radio.voice;

/**
 * The analog unit ID burst (1.12 playtest fixes 2), in the style of Motorola's MDC-1200: at each key-up a commercial
 * radio on an analog channel with a unit ID sends about 150 ms of 1200 baud FSK (1200 Hz for a one, 1800 Hz for a
 * zero) in place of the start of the voice: the "brrrp" heard on fire and business channels. A preamble of alternating
 * bits, a sync word, then the unit ID and a check, scrambled so different IDs sound different. Receivers do not decode
 * the sound itself: the ID rides on the transmission's {@link DigitalVoice.Mode} and radios with a display show it
 * ({@link DigitalVoice#unitHeard}). Pure, so it is unit tested.
 */
public final class MdcBurst {

	/** Bits a second. */
	public static final int BAUD = 1200;
	/** Samples per bit at the voice rate (48 kHz). */
	public static final int SAMPLES_PER_BIT = 48_000 / BAUD;
	/** The tone of a one, Hz. */
	public static final double MARK_HZ = 1200.0D;
	/** The tone of a zero, Hz. */
	public static final double SPACE_HZ = 1800.0D;
	/** Preamble bits (alternating 1 0). */
	static final int PREAMBLE_BITS = 24;
	/** The sync word (40 bits, MDC-1200's). */
	static final long SYNC = 0x07092A446FL;
	static final int SYNC_BITS = 40;
	/** Data bits after the sync: 14 bytes. */
	static final int DATA_BITS = 112;
	/** Every bit of the burst. */
	public static final int BITS = PREAMBLE_BITS + SYNC_BITS + DATA_BITS;
	/** The burst's length in samples (about 147 ms). */
	public static final int LENGTH = BITS * SAMPLES_PER_BIT;
	/** Its level against full scale. */
	static final double LEVEL = 0.38D;
	/** A gap longer than this between voice frames starts a new transmission (a new key-up), ns. */
	public static final long KEY_UP_GAP_NANOS = 300_000_000L;

	private MdcBurst() {}

	/** The burst's bits for unit {@code unitId}: preamble, sync, then the ID and its check spread over 14 bytes. */
	static boolean[] bits(int unitId) {
		boolean[] out = new boolean[BITS];
		int at = 0;
		for(int i = 0; i < PREAMBLE_BITS; i++)
			out[at++] = i % 2 == 0;
		for(int i = SYNC_BITS - 1; i >= 0; i--)
			out[at++] = (SYNC >>> i & 1L) != 0L;
		// Op 0x01 (PTT ID), arg 0x80 (pre-ID), the unit ID, a CRC-ish check, then the bytes again (MDC sends its block
		// with forward error correction, so twice over is a fair stand-in), scrambled by a small LFSR.
		int id = unitId & 0xFFFF;
		int[] bytes = new int[14];
		bytes[0] = 0x01;
		bytes[1] = 0x80;
		bytes[2] = id >>> 8;
		bytes[3] = id & 0xFF;
		int check = 0xFFFF;
		for(int i = 0; i < 4; i++)
			check = (check << 5 ^ check >>> 3 ^ bytes[i] * 0x1021) & 0xFFFF;
		bytes[4] = check >>> 8;
		bytes[5] = check & 0xFF;
		for(int i = 6; i < 12; i++)
			bytes[i] = bytes[i - 6] ^ 0x5A;
		bytes[12] = 0x00;
		bytes[13] = 0x00;
		int lfsr = 0x1FF;
		for(int b : bytes)
			for(int i = 7; i >= 0; i--) {
				int bit = (b >>> i & 1) ^ (lfsr & 1);
				lfsr = lfsr >>> 1 | ((lfsr ^ lfsr >>> 4) & 1) << 8;
				out[at++] = bit != 0;
			}
		return out;
	}

	/** The whole burst for unit {@code unitId} as 48 kHz PCM, phase continuous, faded in and out over 2 ms. */
	public static short[] build(int unitId) {
		boolean[] bits = bits(unitId);
		short[] out = new short[LENGTH];
		double phase = 0.0D;
		int fade = 48_000 / 500;
		for(int n = 0; n < LENGTH; n++) {
			double hz = bits[n / SAMPLES_PER_BIT] ? MARK_HZ : SPACE_HZ;
			phase += 2.0D * Math.PI * hz / RadioVoiceProcessor.SAMPLE_RATE;
			double gain = Math.min(1.0D, Math.min(n, LENGTH - 1 - n) / (double)fade);
			out[n] = (short)Math.round(Math.sin(phase) * LEVEL * gain * Short.MAX_VALUE);
		}
		return out;
	}

	/**
	 * One transmitter's burst: watches the voice frames going out, and at each key-up (a gap of more than
	 * {@link #KEY_UP_GAP_NANOS}) puts the burst over the first frames. One per radio, used from its voice thread.
	 */
	public static final class Sender {
		private short[] burst = new short[0];
		private int burstId = -1;
		private int sent = LENGTH;
		private long lastFrame = Long.MIN_VALUE / 2;

		/**
		 * The frame to send for {@code pcm} from unit {@code unitId} at {@code nowNanos}: {@code pcm} itself, or a new
		 * frame carrying the burst (voice under it is replaced, as on a real radio, which mutes the mic for the ID).
		 * Unit 0 never bursts.
		 */
		public short[] frame(short[] pcm, int unitId, long nowNanos) {
			boolean keyUp = nowNanos - lastFrame > KEY_UP_GAP_NANOS;
			lastFrame = nowNanos;
			if(unitId <= 0)
				return pcm;
			if(keyUp) {
				if(burstId != unitId) {
					burst = build(unitId);
					burstId = unitId;
				}
				sent = 0;
			}
			if(sent >= LENGTH)
				return pcm;
			short[] out = pcm.clone();
			for(int i = 0; i < out.length && sent < LENGTH; i++)
				out[i] = burst[sent++];
			return out;
		}

		/** True while a burst is going out (for tests). */
		public boolean sending() {
			return sent < LENGTH;
		}
	}
}
