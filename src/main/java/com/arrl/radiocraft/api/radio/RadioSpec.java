package com.arrl.radiocraft.api.radio;

import java.util.Objects;

/**
 * What a {@link CompanionRadioBlockEntity} is: its band, mode, dial step, power draw and whether it tunes past its band.
 *
 * <pre>{@code
 * RadioSpec lowBandMobile = RadioSpec.fm(RadioBands.LOW_BAND, 12_500).withPower(13, 2);
 * RadioSpec hfRig = RadioSpec.ssb(RadioBands.M20);
 * }</pre>
 *
 * @param band the band's name ({@link RadioBands}); looked up when the block entity is made
 * @param fm true for FM (squelched, no background static, VHF and commercial practice), false for SSB (HF)
 * @param stepHz how far one click of the dial moves, in Hz
 * @param transmitUse FE per tick drawn while transmitting
 * @param receiveUse FE per tick drawn while on
 * @param tunesOutOfBand true if the dial goes past the band edges (an amateur rig's general coverage receive); false
 *                       keeps it inside the band (commercial radios)
 * @since 1.12.0
 */
public record RadioSpec(String band, boolean fm, int stepHz, int transmitUse, int receiveUse, boolean tunesOutOfBand) {

	/**
	 * Checks the values.
	 *
	 * @param band the band's name
	 * @param fm true for FM
	 * @param stepHz the dial step, at least 1 Hz
	 * @param transmitUse FE per tick transmitting, at least 0
	 * @param receiveUse FE per tick on, at least 0
	 * @param tunesOutOfBand true to tune past the band
	 */
	public RadioSpec {
		Objects.requireNonNull(band, "band");
		if(stepHz < 1)
			throw new IllegalArgumentException("stepHz must be at least 1");
		transmitUse = Math.max(0, transmitUse);
		receiveUse = Math.max(0, receiveUse);
	}

	/**
	 * An FM radio that stays in its band, with a VHF base station's power draw (13 FE/t transmitting, 2 receiving).
	 *
	 * @param band the band's name
	 * @param stepHz the channel step (12 500 for commercial, 20 000 for 2m, 25 000 for marine)
	 * @return the spec
	 */
	public static RadioSpec fm(String band, int stepHz) {
		return new RadioSpec(band, true, stepHz, 13, 2, false);
	}

	/**
	 * An SSB radio with 1 kHz steps that tunes past its band, with a 10m HF radio's power draw (26 FE/t transmitting,
	 * 8 receiving).
	 *
	 * @param band the band's name
	 * @return the spec
	 */
	public static RadioSpec ssb(String band) {
		return new RadioSpec(band, false, 1_000, 26, 8, true);
	}

	/**
	 * This spec with another power draw.
	 *
	 * @param transmitUse FE per tick transmitting
	 * @param receiveUse FE per tick on
	 * @return a new spec
	 */
	public RadioSpec withPower(int transmitUse, int receiveUse) {
		return new RadioSpec(band, fm, stepHz, transmitUse, receiveUse, tunesOutOfBand);
	}

	/**
	 * This spec tuning past its band or not.
	 *
	 * @param value true to tune past the band edges
	 * @return a new spec
	 */
	public RadioSpec withTunesOutOfBand(boolean value) {
		return new RadioSpec(band, fm, stepHz, transmitUse, receiveUse, value);
	}
}
