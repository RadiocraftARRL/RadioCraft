package com.arrl.radiocraft.common.radio.voice;

/**
 * What a receiver in one mode makes of a voice signal in another (maintainer, 2026-11-08). Same mode: as
 * {@link Detuning} has it.
 * <ul>
 *     <li><b>SSB hearing AM</b>: an AM signal is a carrier with a sideband each side. Tuned on the carrier the voice
 *     comes through clean; off it, the voice shifts as SSB does and the carrier beats against the receiver as a
 *     whistle at the offset, with the far sideband garbling it a little.</li>
 *     <li><b>SSB hearing FM</b>: heard, but a garbled warble with no words in it ({@link #garble}).</li>
 *     <li><b>AM hearing SSB</b>: no carrier, so nothing opens the receiver: an SSB signal is not heard on its own, only
 *     as garble over an AM station it lands on (QRM).</li>
 *     <li><b>AM hearing FM</b>: the FM carrier opens it, but there are no words, only the warble.</li>
 *     <li><b>FM hearing AM</b>: the limiter strips the AM off: not heard, a little QRM on what is. (FM hearing SSB is as it
 *     always was.)</li>
 * </ul>
 */
public final class ModeMix {

	private ModeMix() {}

	/** How loud the AM carrier's beat note is under the voice, of full scale. */
	static final double CARRIER_LEVEL = 0.18D;
	/** Splatter from the far sideband of an AM signal tuned off its carrier on SSB. */
	static final double FAR_SIDEBAND_SPLATTER = 0.25D;
	/** QRM an SSB signal puts on an AM station it lands on, and an AM signal on an FM one. */
	static final double SSB_ON_AM_QRM = 0.6D, AM_ON_FM_QRM = 0.2D;
	/** Strength left of an FM signal heard as a warble on SSB or AM. */
	static final double FM_WARBLE_STRENGTH = 0.7D;

	/**
	 * @param effect    what the receiver makes of the signal's frequency, from its own {@link Detuning}
	 * @param carrierHz an AM carrier's beat note to add, Hz (0 for none)
	 * @param garble    the voice is replaced by a wordless warble (FM heard on SSB or AM)
	 */
	public record Result(Detuning.Effect effect, double carrierHz, boolean garble) {
		static Result plain(Detuning.Effect effect) {
			return new Result(effect, 0.0D, false);
		}
	}

	/** QRM an SSB signal on the other sideband puts on an SSB receiver: its energy is mostly outside the filter. */
	static final double OPPOSITE_SIDEBAND_QRM = 0.3D;

	/** A receiver in {@code rx} on {@code rxHz}, whose own detuning gave {@code effect}, hearing a {@code tx} signal on {@code txHz}. */
	public static Result of(Modulation rx, Modulation tx, Detuning.Effect effect, double rxHz, double txHz) {
		return of(rx, tx, effect, rxHz, txHz, true);
	}

	/**
	 * {@link #of(Modulation, Modulation, Detuning.Effect, double, double)}, where {@code sameSideband} is false for an SSB
	 * signal on the other sideband from an SSB receiver's (2026-11-09: USB against LSB on CB): not heard, a little QRM.
	 */
	public static Result of(Modulation rx, Modulation tx, Detuning.Effect effect, double rxHz, double txHz, boolean sameSideband) {
		if(rx == Modulation.SSB && tx == Modulation.SSB && !sameSideband && !effect.isNone())
			return Result.plain(new Detuning.Effect(false, 0.0D, 0.0D, 0.0D, effect.audible() ? OPPOSITE_SIDEBAND_QRM : effect.qrm()));
		if(rx == tx || effect.isNone())
			return Result.plain(effect);
		double off = Math.abs(rxHz - txHz);
		switch(rx) {
			case SSB -> {
				if(!effect.audible())
					return Result.plain(effect);
				if(tx == Modulation.AM) {
					if(off < Detuning.ON_FREQUENCY_HZ)
						return Result.plain(effect); // Zero beat on the carrier: clean.
					return new Result(new Detuning.Effect(true, effect.strength(), effect.pitchShiftHz(), Math.max(effect.splatter(), FAR_SIDEBAND_SPLATTER), 0.0D), off, false);
				}
				return new Result(new Detuning.Effect(true, effect.strength() * FM_WARBLE_STRENGTH, 0.0D, effect.splatter(), 0.0D), 0.0D, true); // FM.
			}
			case AM -> {
				if(tx == Modulation.SSB) // No carrier: only garble over an AM station it lands on.
					return Result.plain(new Detuning.Effect(false, 0.0D, 0.0D, 0.0D, effect.audible() ? SSB_ON_AM_QRM : effect.qrm()));
				if(!effect.audible())
					return Result.plain(effect);
				return new Result(new Detuning.Effect(true, effect.strength() * FM_WARBLE_STRENGTH, 0.0D, effect.splatter(), 0.0D), 0.0D, true); // FM: a carrier, no words.
			}
			case FM -> {
				if(tx == Modulation.AM) // The limiter strips AM: not heard, a little QRM.
					return Result.plain(new Detuning.Effect(false, 0.0D, 0.0D, 0.0D, effect.audible() ? AM_ON_FM_QRM : effect.qrm()));
				return Result.plain(effect); // FM hearing SSB: as it always was.
			}
		}
		return Result.plain(effect);
	}

	/** The sound state a receiver keeps between frames, so the carrier and the warble do not click from frame to frame. */
	public static final class Tones {
		private double carrierPhase = 0.0D, warblePhase = 0.0D, envelope = 0.0D;
	}

	static final int SAMPLE_RATE = 48_000;

	/** {@code pcm} with an AM carrier's beat note at {@code hz} mixed under it (a new array). */
	public static short[] withCarrier(short[] pcm, double hz, Tones tones) {
		short[] out = new short[pcm.length];
		double step = 2.0D * Math.PI * hz / SAMPLE_RATE;
		for(int i = 0; i < pcm.length; i++) {
			tones.carrierPhase += step;
			if(tones.carrierPhase > 2.0D * Math.PI)
				tones.carrierPhase -= 2.0D * Math.PI;
			out[i] = clamp(pcm[i] + Math.sin(tones.carrierPhase) * CARRIER_LEVEL * 32767.0D);
		}
		return out;
	}

	/**
	 * An FM signal as an SSB or AM receiver hears it (a new array): no words, a warbling tone whose pitch swings with the
	 * voice (the frequency deviation, heard as the edge of a filter), rough with it.
	 */
	public static short[] garble(short[] pcm, Tones tones) {
		short[] out = new short[pcm.length];
		double attack = Math.exp(-1.0D / (0.004D * SAMPLE_RATE)), release = Math.exp(-1.0D / (0.05D * SAMPLE_RATE));
		for(int i = 0; i < pcm.length; i++) {
			double x = pcm[i] / 32768.0D, magnitude = Math.abs(x);
			tones.envelope = magnitude > tones.envelope ? attack * tones.envelope + (1.0D - attack) * magnitude : release * tones.envelope + (1.0D - release) * magnitude;
			double hz = 500.0D + 2200.0D * Math.min(1.0D, tones.envelope * 4.0D) + 600.0D * x; // Swings with the voice.
			tones.warblePhase += 2.0D * Math.PI * hz / SAMPLE_RATE;
			if(tones.warblePhase > 2.0D * Math.PI)
				tones.warblePhase -= 2.0D * Math.PI;
			double tone = Math.signum(Math.sin(tones.warblePhase)) * 0.55D + Math.sin(tones.warblePhase * 2.7D) * 0.2D; // Harsh, buzzy.
			out[i] = clamp(tone * (0.15D + Math.min(1.0D, tones.envelope * 3.0D)) * 0.5D * 32767.0D);
		}
		return out;
	}

	private static short clamp(double sample) {
		return (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sample)));
	}
}
