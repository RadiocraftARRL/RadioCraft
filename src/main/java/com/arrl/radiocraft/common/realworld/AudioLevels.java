package com.arrl.radiocraft.common.realworld;

/** Levels and gain for the Real-World Interface's audio: dBFS meters and clipped gain. Pure. */
public final class AudioLevels {

	/** What silence reads, dBFS. */
	public static final double FLOOR_DB = -100.0D;

	private AudioLevels() {}

	/** The RMS level of {@code pcm}, dBFS (a full-scale square wave is 0, a full-scale sine about -3). */
	public static double rmsDb(short[] pcm) {
		if(pcm.length == 0)
			return FLOOR_DB;
		double sum = 0.0D;
		for(short s : pcm)
			sum += (double)s * s;
		double rms = Math.sqrt(sum / pcm.length) / 32768.0D;
		return rms <= 0.0D ? FLOOR_DB : Math.max(FLOOR_DB, 20.0D * Math.log10(rms));
	}

	/** {@code count} samples of {@code pcm} times {@code gain}, clipped to 16 bits, in place. Gain 1 leaves them be. */
	public static void gain(short[] pcm, int count, double gain) {
		if(gain == 1.0D)
			return;
		for(int i = 0; i < count; i++)
			pcm[i] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(pcm[i] * gain)));
	}

	/** A meter reading that rises at once and falls back by {@code fallDb} each update, like a peak meter. */
	public static double meter(double shown, double now, double fallDb) {
		return now >= shown ? now : Math.max(now, Math.max(FLOOR_DB, shown - fallDb));
	}
}
