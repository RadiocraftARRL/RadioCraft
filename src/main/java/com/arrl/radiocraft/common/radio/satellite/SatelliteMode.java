package com.arrl.radiocraft.common.radio.satellite;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * What a satellite does with its radio (maintainer, 2026-11-12): exactly one of these at a time, switched from SATTRACK
 * by its owner or the players they allow while it passes. An amateur satellite may be a {@link #REPEATER}, {@link #APRS},
 * {@link #MAILBOX} or {@link #SSTV}; a commercial one a {@link #REPEATER} or, since the weather imaging work of the same
 * day, an {@link #IMAGING} weather satellite. Whatever the mode, every satellite relays distress alerts and sends its
 * telemetry beacon.
 */
public enum SatelliteMode {
	/** An FM repeater: uplink with the tone in, downlink out (the default). */
	REPEATER,
	/** An APRS digipeater, simplex on its APRS frequency (the downlink unless the owner picks another), as the ISS. */
	APRS,
	/** A store-and-forward packet mailbox (PACSAT style): messages up on one pass, down on a later one. */
	MAILBOX,
	/** Sends its owner's picture as SSTV on its downlink every so often while it is over anyone. */
	SSTV,
	/**
	 * Commercial only: a weather imager like the NOAA APT satellites. It scans a swath under its ground track and sends
	 * the picture down line by line on its downlink (see {@code imaging.AptSwath}); the WXSAT program receives it.
	 */
	IMAGING;

	/** The mode named {@code name} (any case), or null. */
	@Nullable
	public static SatelliteMode parse(@Nullable String name) {
		if(name == null)
			return null;
		try {
			return valueOf(name.strip().toUpperCase(Locale.ROOT));
		}
		catch(IllegalArgumentException e) {
			return null;
		}
	}

	/** True if a satellite ({@code amateur} or commercial) may run this mode: a commercial one repeats or images. */
	public boolean allowedOn(boolean amateur) {
		return amateur ? this != IMAGING : this == REPEATER || this == IMAGING;
	}

	/** The next mode after this one that an {@code amateur} (or commercial) satellite may run (SATTRACK's F8 steps through them). */
	public SatelliteMode next(boolean amateur) {
		SatelliteMode mode = this;
		for(int i = 0; i < values().length; i++) {
			mode = values()[(mode.ordinal() + 1) % values().length];
			if(mode.allowedOn(amateur))
				return mode;
		}
		return REPEATER;
	}
}
