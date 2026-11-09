package com.arrl.radiocraft.common.radio.voice;

import com.arrl.radiocraft.common.radio.Band;
import org.jetbrains.annotations.Nullable;

/**
 * How a voice transmission is modulated (2026-11-08): single sideband (the HF ham rigs), AM (CB) or FM (VHF, the
 * commercial and low band radios, the all-band radio's FM). Each voice packet carries its transmitter's
 * ({@link com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket#getModulation}); a transmitter that does not say
 * ({@link #push}) gets its band's usual one ({@link #usual}). What a receiver in one mode makes of a signal in another
 * is {@link ModeMix}.
 */
public enum Modulation {
	SSB, AM, FM;

	private static final ThreadLocal<Modulation> CURRENT = new ThreadLocal<>();

	/** The modulation pushed on this thread, null if none was. */
	@Nullable
	public static Modulation current() {
		return CURRENT.get();
	}

	/** A transmitter says how it modulates what it sends next on this thread; give the result to {@link #pop}. */
	@Nullable
	public static Modulation push(Modulation modulation) {
		Modulation was = CURRENT.get();
		CURRENT.set(modulation);
		return was;
	}

	public static void pop(@Nullable Modulation was) {
		if(was == null)
			CURRENT.remove();
		else
			CURRENT.set(was);
	}

	private static final ThreadLocal<Boolean> LOWER_SIDEBAND = new ThreadLocal<>();

	/** The sideband pushed on this thread (true lower), null if none was. */
	@Nullable
	public static Boolean currentLowerSideband() {
		return LOWER_SIDEBAND.get();
	}

	/** A transmitter says which sideband it sends SSB on (2026-11-09); give the result to {@link #popLowerSideband}. */
	@Nullable
	public static Boolean pushLowerSideband(boolean lower) {
		Boolean was = LOWER_SIDEBAND.get();
		LOWER_SIDEBAND.set(lower);
		return was;
	}

	public static void popLowerSideband(@Nullable Boolean was) {
		if(was == null)
			LOWER_SIDEBAND.remove();
		else
			LOWER_SIDEBAND.set(was);
	}

	/** What a transmitter on {@code band} that says nothing uses: FM on VHF, SSB below, AM on CB. */
	public static Modulation usual(@Nullable Band band) {
		if(band == null)
			return SSB;
		if(band.isVhf())
			return FM;
		return Band.CB.equals(band.name()) ? AM : SSB;
	}
}
