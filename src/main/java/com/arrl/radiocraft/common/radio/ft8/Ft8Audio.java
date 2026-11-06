package com.arrl.radiocraft.common.radio.ft8;

import com.arrl.radiocraft.common.radio.voice.RadioVoiceProcessor;
import com.arrl.radiocraft.common.radio.voice.RepeaterAudio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an FT8 transmission sounds like on a receiver (computer batch 3B): 8-tone FSK, {@link Ft8#TONE_SPACING_HZ}
 * apart, a symbol every {@link Ft8#SYMBOL_SECONDS}, 79 symbols with the Costas sync tones at the start, middle and
 * end, at the station's audio offset: the slow, even warble of a real FT8 signal. The 58 data symbols come from the
 * message by a fixed shuffle (not real LDPC-coded FT8: nobody decodes the audio, the packet carries the message), so
 * the same message always sounds the same.
 * <p>
 * Made without a {@code Math.sin} per sample (a phasor turned by a multiply-add, as {@code RepeaterAudio} does since the
 * performance fixes), and the last few transmissions are kept: a station calling CQ sends the same thing every other
 * slot. 12.64 s of 48 kHz is 632 frames, about 1.2 MB, so only {@link #CACHE} are kept. The frames are shared;
 * listeners clone what they change.
 */
public final class Ft8Audio {

	private Ft8Audio() {}

	/** Transmissions kept. */
	public static final int CACHE = 4;
	private static final double LEVEL = 0.3D;
	private static final double RAMP_SECONDS = 0.01D;

	private static final Map<String, List<short[]>> CACHED = new LinkedHashMap<>(8, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, List<short[]>> eldest) {
			return size() > CACHE;
		}
	};

	/** The 79 tones (0-7) of {@code message}: the Costas arrays at 0, 36 and 72, the data from the message's bits. */
	public static int[] symbols(String message) {
		int[] out = new int[Ft8.SYMBOLS];
		long state = 0x9E3779B97F4A7C15L;
		for(int i = 0; i < message.length(); i++)
			state = (state ^ message.charAt(i)) * 0x100000001B3L;
		for(int i = 0; i < Ft8.SYMBOLS; i++) {
			if(i < 7 || (i >= 36 && i < 43) || i >= 72) {
				out[i] = Ft8.COSTAS[i < 7 ? i : i < 43 ? i - 36 : i - 72];
				continue;
			}
			state ^= state << 13;
			state ^= state >>> 7;
			state ^= state << 17;
			out[i] = (int)((state >>> 32) & 7L);
		}
		return out;
	}

	/** The transmission's 20 ms frames (shared: do not change them). */
	public static synchronized List<short[]> frames(String message, int offsetHz) {
		String key = offsetHz + "|" + message;
		List<short[]> frames = CACHED.get(key);
		if(frames == null) {
			frames = Collections.unmodifiableList(make(symbols(message), Ft8.clampOffset(offsetHz)));
			CACHED.put(key, frames);
		}
		return frames;
	}

	/** Frames in a transmission: 632. */
	public static int frameCount() {
		int samples = (int)Math.round(Ft8.SYMBOLS * Ft8.SYMBOL_SECONDS * RadioVoiceProcessor.SAMPLE_RATE);
		return (samples + RepeaterAudio.FRAME - 1) / RepeaterAudio.FRAME;
	}

	private static List<short[]> make(int[] symbols, int offsetHz) {
		double rate = RadioVoiceProcessor.SAMPLE_RATE;
		int perSymbol = (int)Math.round(Ft8.SYMBOL_SECONDS * rate);
		int total = perSymbol * symbols.length;
		int ramp = (int)(RAMP_SECONDS * rate);
		double[] cos = new double[8], sin = new double[8];
		for(int tone = 0; tone < 8; tone++) {
			double step = 2.0D * Math.PI * (offsetHz + tone * Ft8.TONE_SPACING_HZ) / rate;
			cos[tone] = Math.cos(step);
			sin[tone] = Math.sin(step);
		}
		List<short[]> frames = new ArrayList<>(frameCount());
		short[] frame = new short[RepeaterAudio.FRAME];
		int inFrame = 0;
		double re = 1.0D, im = 0.0D; // The phase carries on from tone to tone (continuous-phase FSK, no clicks).
		for(int n = 0; n < total; n++) {
			int tone = symbols[n / perSymbol];
			double envelope = Math.min(1.0D, Math.min(n, total - 1 - n) / (double)ramp);
			frame[inFrame++] = (short)Math.round(im * LEVEL * envelope * 32767.0D);
			double nextRe = re * cos[tone] - im * sin[tone];
			im = re * sin[tone] + im * cos[tone];
			re = nextRe;
			if((n & 4095) == 4095) {
				double norm = 1.0D / Math.sqrt(re * re + im * im);
				re *= norm;
				im *= norm;
			}
			if(inFrame == RepeaterAudio.FRAME) {
				frames.add(frame);
				frame = new short[RepeaterAudio.FRAME];
				inFrame = 0;
			}
		}
		if(inFrame > 0)
			frames.add(frame); // The rest is silence.
		return frames;
	}

	/** Forgets the kept transmissions (tests). */
	public static synchronized void clear() {
		CACHED.clear();
	}
}
