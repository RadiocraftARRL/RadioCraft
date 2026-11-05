package com.arrl.radiocraft.common.radio.voice;

import org.jetbrains.annotations.Nullable;

/**
 * Digital voice (P25-style) on the commercial radios, simplified: what a digital or encrypted transmission sounds like
 * on each kind of receiver.
 * <ul>
 *     <li>A transmission carries its {@link Mode}: analog FM, or digital with an encryption key id (0 for clear). The
 *     transmitter sets it with {@link #push} around the call that puts the voice on the air; every
 *     {@link com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket} made inside picks it up. Synchronous repeating
 *     (a repeater relays from inside its receive call) keeps the mode of what it relays.</li>
 *     <li>A digital receiver ({@link Decoder}) hears clean audio, no static at all, until the signal is so weak that FM
 *     would be 70% static ({@link #CLIFF}); then it drops out. Just above that it warbles ({@link #EDGE}).</li>
 *     <li>Encryption is virtual: a digital receiver without the key hears garbled digital noise
 *     ({@link #scramble}); an analog receiver hears any digital signal as a harsh buzz ({@link #buzz}). The moderation
 *     computer records the audio at the source, so it has the clear voice.</li>
 * </ul>
 * The maths is pure, so it can be unit tested; the audio functions are cheap per-sample loops with no allocation
 * beyond the returned frame.
 */
public final class DigitalVoice {

	private DigitalVoice() {}

	/**
	 * How a transmission goes out: analog FM, or digital with an encryption key id (0 for none), and the sending
	 * radio's unit ID (0 for none; 1.12 playtest fixes 2). On analog the ID goes out as an MDC-1200 style burst at
	 * key-up ({@link MdcBurst}); on digital it rides in the data, so a receiver without the key still reads it.
	 */
	public record Mode(boolean digital, int keyId, int unitId) {
		public static final Mode ANALOG = new Mode(false, 0);
		public static final Mode DIGITAL_CLEAR = new Mode(true, 0);

		public Mode {
			keyId = digital ? Math.max(0, keyId) : 0;
			unitId = Math.max(0, unitId);
		}

		/** A mode with no unit ID. */
		public Mode(boolean digital, int keyId) {
			this(digital, keyId, 0);
		}

		public boolean encrypted() {
			return digital && keyId != 0;
		}

		/** This mode sent by unit {@code id} (0 for none). */
		public Mode withUnit(int id) {
			return id == unitId ? this : new Mode(digital, keyId, id);
		}
	}

	/**
	 * The unit ID a receiver reads off a transmission it took as {@code reception}, or 0: an ID is read when the radio
	 * decodes the signal's data, which a digital receiver does even without the key (as P25 shows the caller on an
	 * encrypted call), and an analog one does off the MDC burst. An analog receiver hearing digital (a buzz) reads
	 * nothing, and nothing is read below the cliff.
	 */
	public static int unitHeard(Mode mode, Reception reception) {
		if(mode == null || mode.unitId() <= 0)
			return 0;
		return switch(reception) {
			case ANALOG, CLEAR, WARBLE, SCRAMBLED -> mode.unitId();
			case BUZZ, DROPPED -> 0;
		};
	}

	/** A digital receiver's decoder: the key it holds (0 for none, so it only decodes clear digital). */
	public record Decoder(int keyId) {
		public static final Decoder CLEAR_ONLY = new Decoder(0);

		/** True if it can decode a transmission sent with {@code mode}. */
		public boolean hasKeyFor(Mode mode) {
			return !mode.encrypted() || mode.keyId() == keyId;
		}
	}

	/** What a receiver makes of a transmission. */
	public enum Reception {
		/** Analog FM: the receiver's usual processing (hiss as it fades). */
		ANALOG,
		/** Decoded digital voice: clean, no static. */
		CLEAR,
		/** Decoded, but at the edge of the cliff: the vocoder warbles. */
		WARBLE,
		/** Digital without the key: garbled digital noise where the voice was. */
		SCRAMBLED,
		/** Digital heard on an analog receiver: a harsh data buzz. */
		BUZZ,
		/** Too weak for the decoder: silence. */
		DROPPED;

		/** True when the audio is digital audio (no FM hiss, no squelch tail): decoded or garbled by the decoder. */
		public boolean digitalAudio() {
			return this == CLEAR || this == WARBLE || this == SCRAMBLED;
		}
	}

	private static final ThreadLocal<Mode> CURRENT = ThreadLocal.withInitial(() -> Mode.ANALOG);

	/** The mode of the transmission being put on the air on this thread: {@link Mode#ANALOG} outside any {@link #push}. */
	public static Mode current() {
		return CURRENT.get();
	}

	/** Sets the mode for what this thread transmits next; give the returned mode back to {@link #pop} in a finally. */
	public static Mode push(Mode mode) {
		Mode was = CURRENT.get();
		CURRENT.set(mode == null ? Mode.ANALOG : mode);
		return was;
	}

	/** Puts back what {@link #push} returned. */
	public static void pop(Mode was) {
		CURRENT.set(was == null ? Mode.ANALOG : was);
	}

	/**
	 * The share of static an FM receiver plays on a signal of strength {@code signal}: the dropouts to static, and the
	 * hiss under what is left ({@link ReceiverMix#fmDropout}, {@link ReceiverMix#fmHiss}).
	 */
	public static double fmStaticShare(double signal) {
		double dropout = ReceiverMix.fmDropout(signal);
		return dropout + (1.0D - dropout) * ReceiverMix.fmHiss(signal);
	}

	/** The share of static on FM where digital gives up. */
	public static final double CLIFF_STATIC = 0.7D;
	/** The weakest signal digital decodes: where FM would be {@link #CLIFF_STATIC} static. */
	public static final double CLIFF = findCliff();
	/** Below this (and above {@link #CLIFF}) decoded digital warbles. */
	public static final double EDGE = CLIFF * 1.3D;

	/** Bisects {@link #fmStaticShare} (falling as the signal rises) for {@link #CLIFF_STATIC}. */
	private static double findCliff() {
		double low = 0.0D, high = ReceiverMix.FM_QUIETING;
		for(int i = 0; i < 60; i++) {
			double mid = (low + high) / 2.0D;
			if(fmStaticShare(mid) > CLIFF_STATIC)
				low = mid;
			else
				high = mid;
		}
		return high;
	}

	/**
	 * What a receiver with {@code decoder} (null for an analog receiver) makes of a transmission sent with {@code mode},
	 * arriving at {@code strength} (0 to 1, after detuning).
	 */
	public static Reception receive(@Nullable Decoder decoder, Mode mode, double strength) {
		if(mode == null || !mode.digital())
			return Reception.ANALOG;
		if(decoder == null)
			return Reception.BUZZ;
		if(strength < CLIFF)
			return Reception.DROPPED;
		if(!decoder.hasKeyFor(mode))
			return Reception.SCRAMBLED;
		return strength < EDGE ? Reception.WARBLE : Reception.CLEAR;
	}

	/**
	 * How strongly an edge signal warbles, 0 at {@link #EDGE} up to 1 at {@link #CLIFF}: the share of each frame the
	 * vocoder gets wrong.
	 */
	public static double warbleDepth(double strength) {
		return Math.max(0.0D, Math.min(1.0D, (EDGE - strength) / (EDGE - CLIFF)));
	}

	/**
	 * The audio a receiver plays for {@code reception}: {@code pcm} itself when it is unchanged (analog, clear), a new
	 * frame otherwise. {@code seed} varies the noise from frame to frame. Not for {@link Reception#DROPPED}.
	 */
	public static short[] render(Reception reception, short[] pcm, double strength, long seed) {
		return switch(reception) {
			case ANALOG, CLEAR, DROPPED -> pcm;
			case WARBLE -> warble(pcm, warbleDepth(strength), seed);
			case SCRAMBLED -> scramble(pcm, seed);
			case BUZZ -> buzz(pcm, seed);
		};
	}

	/** Samples in a vocoder frame's slice: 5 ms at 48 kHz. */
	static final int SLICE = 240;

	/**
	 * Digital voice without the key: the frame cut into 5 ms slices played in a scrambled order, each ring modulated
	 * by a tone that jumps from slice to slice, and crushed to a few bits. It follows the talker's syllables, so it is
	 * clearly someone talking, but nothing can be made out of it.
	 */
	public static short[] scramble(short[] pcm, long seed) {
		short[] out = new short[pcm.length];
		long state = seed | 1L;
		int slices = Math.max(1, (pcm.length + SLICE - 1) / SLICE);
		int shift = 1 + Math.floorMod(seed, Math.max(1, slices - 1));
		double rms = rms(pcm);
		double floor = 0.06D * 32767.0D; // The data stream never quite goes silent while it is keyed.
		for(int s = 0; s < slices; s++) {
			int from = ((s * shift + 1) % slices) * SLICE; // Out of order (some slices twice, some not at all).
			state = xorshift(state);
			double carrier = 2.0D * Math.PI * (300.0D + (state >>> 40) % 1100L) / RadioVoiceProcessor.SAMPLE_RATE;
			for(int i = 0; i < SLICE && s * SLICE + i < out.length; i++) {
				int at = Math.min(pcm.length - 1, from + i);
				state = xorshift(state);
				double noise = ((state >>> 11) * 0x1.0p-53 * 2.0D - 1.0D) * Math.max(floor, rms * 0.6D);
				double x = pcm[at] * Math.sin(carrier * i) + noise;
				out[s * SLICE + i] = crush(x, 5);
			}
		}
		return out;
	}

	/**
	 * A digital signal heard on an analog FM receiver: a raspy buzz at the data's frame rate over hiss, at a steady
	 * level whether or not anyone is talking (the data runs all the time it is keyed).
	 */
	public static short[] buzz(short[] pcm, long seed) {
		short[] out = new short[pcm.length];
		long state = seed | 1L;
		double level = 0.32D * 32767.0D;
		for(int i = 0; i < out.length; i++) {
			state = xorshift(state);
			double white = (state >>> 11) * 0x1.0p-53 * 2.0D - 1.0D;
			double frame = (i / 48) % 2 == 0 ? 0.55D : -0.55D; // 500 Hz square: the symbol clock.
			double gate = (i / 960) % 2 == 0 ? 1.0D : 0.85D;
			out[i] = crush((white * 0.6D + frame * 0.4D) * level * gate, 6);
		}
		return out;
	}

	/**
	 * Decoded digital voice at the edge of the cliff: some slices come out robotic (crushed and chopped at 25 Hz), more
	 * of them the deeper {@code depth} (0 to 1) goes.
	 */
	public static short[] warble(short[] pcm, double depth, long seed) {
		short[] out = pcm.clone();
		if(depth <= 0.0D)
			return out;
		long state = seed | 1L;
		for(int from = 0; from < out.length; from += SLICE) {
			state = xorshift(state);
			if((state >>> 11) * 0x1.0p-53 >= depth * 0.8D)
				continue;
			for(int i = from; i < Math.min(out.length, from + SLICE); i++) {
				double chop = ((i / 960.0D * 2.0D) % 1.0D) < 0.5D ? 1.0D : 0.35D; // 25 Hz chop over the 20 ms frame.
				out[i] = crush(out[i] * chop, 4);
			}
		}
		return out;
	}

	/** Root mean square of a frame, in sample units. */
	static double rms(short[] pcm) {
		if(pcm.length == 0)
			return 0.0D;
		double sum = 0.0D;
		for(short s : pcm)
			sum += (double)s * s;
		return Math.sqrt(sum / pcm.length);
	}

	/** {@code x} (sample units) quantised to {@code bits} bits and clamped to a sample. */
	private static short crush(double x, int bits) {
		double step = 65536.0D / (1 << bits);
		double q = Math.round(x / step) * step;
		return (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, q));
	}

	private static long xorshift(long x) {
		x ^= x << 13;
		x ^= x >>> 7;
		x ^= x << 17;
		return x;
	}
}
