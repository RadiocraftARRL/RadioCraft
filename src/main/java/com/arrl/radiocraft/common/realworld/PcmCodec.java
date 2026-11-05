package com.arrl.radiocraft.common.realworld;

/**
 * 16-bit signed PCM as a sound card line carries it (little or big endian, one or more interleaved channels) to and
 * from the mono samples the radios work in. Several channels are averaged on the way in, and the mono sample is
 * copied to every channel on the way out. Pure.
 */
public final class PcmCodec {

	private PcmCodec() {}

	/** Bytes one sample frame takes: two per channel. */
	public static int frameBytes(int channels) {
		return 2 * channels;
	}

	/**
	 * Reads {@code length} bytes of {@code bytes} into mono samples in {@code out}; returns how many. A partial frame
	 * at the end is left out.
	 */
	public static int decode(byte[] bytes, int length, int channels, boolean bigEndian, short[] out) {
		int frames = Math.min(length / frameBytes(channels), out.length);
		int at = 0;
		for(int f = 0; f < frames; f++) {
			int sum = 0;
			for(int c = 0; c < channels; c++) {
				int lo = bytes[at + (bigEndian ? 1 : 0)] & 0xFF;
				int hi = bytes[at + (bigEndian ? 0 : 1)]; // Signed: carries the sign.
				sum += (hi << 8) | lo;
				at += 2;
			}
			out[f] = (short)(channels == 1 ? sum : Math.round((double)sum / channels));
		}
		return frames;
	}

	/** Writes {@code count} mono samples of {@code pcm} to {@code out}, on every channel; returns the bytes written. */
	public static int encode(short[] pcm, int count, int channels, boolean bigEndian, byte[] out) {
		int at = 0;
		for(int i = 0; i < count; i++) {
			byte hi = (byte)(pcm[i] >> 8);
			byte lo = (byte)pcm[i];
			for(int c = 0; c < channels; c++) {
				out[at++] = bigEndian ? hi : lo;
				out[at++] = bigEndian ? lo : hi;
			}
		}
		return at;
	}
}
