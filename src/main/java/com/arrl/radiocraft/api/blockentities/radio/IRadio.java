package com.arrl.radiocraft.api.blockentities.radio;

import com.arrl.radiocraft.common.radio.Band;

/**
 * A radio block: what another mod can read from, and set on, any RadioCraft radio block entity (every stock radio, and
 * {@link com.arrl.radiocraft.api.radio.CompanionRadioBlockEntity}), without depending on the internal radio classes.
 *
 * <p>Get one with {@code level.getBlockEntity(pos) instanceof IRadio radio}. Call it on the server thread unless a
 * method says otherwise; the getters are also safe on the client (they read synced state).</p>
 *
 * @since 1.12.0
 */
public interface IRadio {

	/**
	 * The band the radio is on. Its transmissions carry this band, so a transmission outside it is out of band (it
	 * splatters, see {@link com.arrl.radiocraft.api.radio.RadioBands#splatterReach}).
	 *
	 * @return the band, or null for a radio with no band
	 */
	Band getBand();

	/**
	 * The dial frequency: what the receiver listens on.
	 *
	 * @return the frequency in Hz
	 */
	float getFrequency();

	/**
	 * Sets the dial frequency as it is, without snapping it to the step grid or clamping it to the band. The caller
	 * keeps it sensible (see {@link com.arrl.radiocraft.api.radio.RadioBands#canTune}).
	 *
	 * @param frequency the frequency in Hz
	 */
	void setFrequency(float frequency);

	/**
	 * Where the radio transmits: the dial frequency, or the dial plus a repeater offset, or a channel's transmit
	 * frequency on a channelized radio.
	 *
	 * @return the transmit frequency in Hz
	 */
	float getTransmitFrequency();

	/**
	 * Whether the radio is switched on and has power (from its power network, or always when the server does not
	 * require power).
	 *
	 * @return true while it is on
	 */
	boolean isPowered();

	/** Switches the radio on or off, as its power button does. */
	void toggle();

	/**
	 * Whether the push-to-talk is held (from the screen, the hand mic or the desk mic).
	 *
	 * @return true while keyed
	 */
	boolean isPTTDown();

	/**
	 * Keys or unkeys the transmitter, as the PTT button does.
	 *
	 * @param value true to key
	 */
	void setPTTDown(boolean value);

	/**
	 * Whether voice from a player near the radio would go out now: on, keyed, in voice mode and allowed to transmit
	 * where it is tuned.
	 *
	 * @return true if it would transmit
	 */
	boolean canTransmitVoice();

	/**
	 * True while the transmitter refuses to key where it is tuned (out of band without a MARS mod, a receive-only
	 * channel). The display shows OUT OF BAND.
	 *
	 * @return true while inhibited
	 */
	boolean isTransmitInhibited();

	/**
	 * Whether this radio may transmit on {@code hz}. By default: inside its band, or anywhere it tunes with a MARS mod
	 * ({@link com.arrl.radiocraft.api.radio.RadioBands#canTransmit}). A radio that covers more (or less) overrides
	 * {@code RadioBlockEntity#mayTransmitOn}.
	 *
	 * @param hz the frequency in Hz
	 * @return true if the transmitter would key there
	 */
	boolean mayTransmitOn(double hz);

	/**
	 * Whether the radio transmits outside its band now.
	 *
	 * @return true if out of band
	 */
	boolean isOutOfBand();

	/**
	 * Whether a MARS mod is fitted (the radio may transmit anywhere it tunes).
	 *
	 * @return true if fitted
	 */
	boolean hasMarsMod();

	/**
	 * Whether the dial goes past the band edges (general coverage receive). False for channelized radios and repeaters.
	 *
	 * @return true if it tunes out of band
	 */
	boolean tunesOutOfBand();

	/**
	 * Whether the transmitter is limited to the band (unless a MARS mod is fitted). False for receivers and repeaters.
	 *
	 * @return true if limited
	 */
	boolean limitsTransmitToBand();

	/**
	 * Whether the radio works in FM (VHF and commercial radios) rather than SSB (HF).
	 *
	 * @return true for FM
	 */
	boolean isFM();

	/**
	 * The strength of the last signal the radio heard (voice, CW or data), 0 to 1.
	 *
	 * @return the strength
	 */
	double getLastSignalStrength();

	/**
	 * The game time the radio last heard a signal, or -1 if it never has. A test can compare it with the level's game
	 * time to know whether a transmission arrived this tick.
	 *
	 * @return the game time
	 */
	long getLastSignalGameTime();

	/**
	 * The SWR the radio sees on its coax at its frequency: 0 with no power or no antenna, 1 for a perfect match,
	 * higher for worse; 10 with more than one antenna on the coax.
	 *
	 * @return the SWR
	 */
	double getAntennaSWR();
}
