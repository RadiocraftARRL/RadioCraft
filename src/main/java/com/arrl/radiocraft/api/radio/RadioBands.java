package com.arrl.radiocraft.api.radio;

import com.arrl.radiocraft.api.antenna.AntennaNetworks;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.BandEdges;
import com.arrl.radiocraft.common.radio.MarineChannels;
import com.arrl.radiocraft.common.radio.marine.MarineRadios;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The bands, and the rules for where a radio may tune and transmit, for other mods.
 *
 * <p><b>Bands.</b> RadioCraft's bands come from the server config (each has a section there: line of sight range,
 * skip distances, edges), so read them here when you need them rather than keeping a {@link Band}: an operator may
 * have moved an edge. The stock bands are the amateur 80m, 40m, 20m, 10m and 2m, commercial {@link #LOW_BAND}
 * (30-50 MHz, on the HF antenna network) and {@link #VHF_COMMERCIAL} (150-174 MHz, which also holds the marine
 * channels). Another mod can add a band of its own with {@link #register}; it has no config entry.</p>
 *
 * <p><b>Tuning and transmitting</b> ({@link BandEdges}): an amateur radio tunes and receives past its band (a quarter
 * of the band's width each side on HF; 143-174 MHz on 2m) and transmits only inside it, or anywhere it tunes with a
 * MARS mod. A commercial band tunes exactly its band. A transmission outside the band it is sent on splatters further
 * into neighbouring frequencies ({@link #splatterReach}). A radio passes its own band with every transmission, so a
 * commercial radio passing a commercial band is in band.</p>
 *
 * <p>Everything here is safe on both sides once the server config has loaded (in a world). Before that,
 * {@link #get} returns null for config bands.</p>
 *
 * @since 1.12.0
 */
public final class RadioBands {

	/** Amateur 80m, 3.5-4.0 MHz. */
	public static final String M80 = "80m";
	/** Amateur 40m, 7.0-7.3 MHz. */
	public static final String M40 = "40m";
	/** Amateur 20m, 14.0-14.35 MHz. */
	public static final String M20 = "20m";
	/** Amateur 10m, 28.0-29.7 MHz. */
	public static final String M10 = "10m";
	/** Amateur 2m, 144-148 MHz (VHF). */
	public static final String M2 = "2m";
	/** Commercial low band, 30-50 MHz: between 10m and VHF in how it travels, on the HF antenna network. */
	public static final String LOW_BAND = Band.LOW_BAND;
	/** Citizens Band, 26.965-27.405 MHz: 40 AM channels, no licence. */
	public static final String CB = Band.CB;
	/** Commercial VHF (high band), 150-174 MHz, like 2m; the marine channels are inside it. */
	public static final String VHF_COMMERCIAL = Band.VHF_COMMERCIAL;

	private RadioBands() {}

	/**
	 * A band by name: a stock band from the server config, or one another mod registered.
	 *
	 * @param name the band's name ("20m", {@link #LOW_BAND}, ...)
	 * @return the band, or null if there is none by that name
	 */
	public static Band get(String name) {
		return Band.getBand(name);
	}

	/**
	 * The band a frequency is in: the narrowest band that contains it, else the amateur band whose tuning range does
	 * (13.95 MHz gives 20m).
	 *
	 * @param hz the frequency in Hz
	 * @return the band, or null if none
	 */
	public static Band at(double hz) {
		return Band.getBand((float)hz);
	}

	/**
	 * Every band: the stock ones (amateur first) as the server config has them, then the registered ones.
	 *
	 * @return a new list
	 */
	public static List<Band> all() {
		List<Band> out = new ArrayList<>();
		for(Band band : Band.getAllDefaults()) {
			Band configured = Band.getBand(band.name());
			out.add(configured != null ? configured : band);
		}
		out.addAll(Band.getRegistered());
		return out;
	}

	/**
	 * Adds a band of your own mod, for your radios and antennas (6m, 70cm, a band of a fictional service). It is not in
	 * the server config, so its numbers are what you give. Call once, at mod construction or common setup, on both
	 * sides. RadioCraft's own radios never tune to it.
	 *
	 * <p>The band behaves like the stock ones: below 100 MHz it is on the HF antenna network and skips as its
	 * {@code minSkipDay..maxSkipNight} say, from 100 MHz up it is on the VHF network (handhelds hear it). Solar weather
	 * for it is read under its own name if a solar event lists it, else under {@code solarLike}'s.</p>
	 *
	 * <p>Not covered: the band plan sheet, the band calculator, the solar weather station's band list and the antenna
	 * analyzer's band list show only the stock bands.</p>
	 *
	 * @param band the band (its name must be new)
	 * @param amateur true for an amateur band: radios tune past its edges and transmit there with a MARS mod
	 * @param solarLike the stock band whose solar weather it follows ("10m" for a 6m band), or null for its own name only
	 * @param displayName the short name for screens ("6m")
	 * @return the band
	 * @throws IllegalArgumentException if a stock or registered band already has the name
	 */
	public static Band register(Band band, boolean amateur, String solarLike, String displayName) {
		Objects.requireNonNull(band, "band");
		Band.register(new Band.Registered(band, amateur, solarLike == null ? band.name() : solarLike,
				displayName == null ? band.name() : displayName));
		return band;
	}

	/**
	 * The bands other mods registered.
	 *
	 * @return the registered bands
	 */
	public static Collection<Band> registered() {
		return Band.getRegistered();
	}

	/**
	 * Whether a band is an amateur band (MARS extension, out-of-band rules) rather than commercial.
	 *
	 * @param band the band
	 * @return true for amateur
	 */
	public static boolean isAmateur(Band band) {
		return band != null && band.isAmateur();
	}

	/**
	 * The antenna network a band's signals travel on: HF below 100 MHz, VHF from 100 MHz.
	 *
	 * @param band the band
	 * @return {@link AntennaNetworks#HF} or {@link AntennaNetworks#VHF}
	 */
	public static ResourceLocation antennaNetwork(Band band) {
		return AntennaNetworks.forBand(band);
	}

	/**
	 * The lowest frequency a radio on {@code band} tunes to.
	 *
	 * @param band the band
	 * @return the frequency in Hz
	 */
	public static float tuneMin(Band band) {
		return BandEdges.tuneMin(band);
	}

	/**
	 * The highest frequency a radio on {@code band} tunes to.
	 *
	 * @param band the band
	 * @return the frequency in Hz
	 */
	public static float tuneMax(Band band) {
		return BandEdges.tuneMax(band);
	}

	/**
	 * Whether a radio on {@code band} tunes to {@code hz}.
	 *
	 * @param band the band
	 * @param hz the frequency in Hz
	 * @return true if it tunes there
	 */
	public static boolean canTune(Band band, double hz) {
		return BandEdges.canTune(band, hz);
	}

	/**
	 * Whether a radio on {@code band} may transmit on {@code hz}: inside the band, or anywhere it tunes with a MARS mod.
	 * True for a null band (nothing to check against).
	 *
	 * @param band the band
	 * @param hz the frequency in Hz
	 * @param marsMod true if a MARS mod is fitted
	 * @return true if it may transmit
	 */
	public static boolean canTransmit(Band band, double hz, boolean marsMod) {
		return BandEdges.canTransmit(band, hz, marsMod);
	}

	/**
	 * Whether {@code hz} is outside {@code band} (a transmission there splatters).
	 *
	 * @param band the band
	 * @param hz the frequency in Hz
	 * @return true if out of band
	 */
	public static boolean isOutOfBand(Band band, double hz) {
		return BandEdges.isOutOfBand(band, hz);
	}

	/**
	 * How far a transmission on {@code hz} sent as {@code band} splatters, against an in-band one's (1): the server's
	 * {@code mars_mod_splatter} out of band.
	 *
	 * @param band the band the transmission is sent on
	 * @param hz the frequency in Hz
	 * @return the reach multiplier
	 */
	public static double splatterReach(Band band, double hz) {
		return BandEdges.splatterReach(band, hz);
	}

	/**
	 * A marine channel in the server's marine channel plan (US or international), for a radio of your own that talks
	 * to the stock marine radios. Marine is not a band of its own: the channels are frequencies in
	 * {@link #VHF_COMMERCIAL}.
	 *
	 * @param name the channel as a radio shows it: "16", "9", "22A", "WX1"
	 * @return the channel, or null if the plan has none by that name
	 */
	public static MarineChannel marineChannel(String name) {
		MarineChannels.Channel channel = MarineChannels.byName(MarineRadios.region(), name);
		return channel == null ? null : new MarineChannel(channel.name(), channel.transmitHz(), channel.receiveHz(), channel.voice());
	}

	/**
	 * A marine channel, as a ship's radio uses it.
	 *
	 * @param name the channel's name ("16")
	 * @param transmitHz where a ship transmits, 0 for a receive-only (weather) channel
	 * @param receiveHz where a ship listens (the coast frequency on a duplex channel)
	 * @param voice false for the DSC channel 70
	 */
	public record MarineChannel(String name, double transmitHz, double receiveHz, boolean voice) {

		/**
		 * Whether a ship only listens here.
		 *
		 * @return true for the weather channels
		 */
		public boolean isReceiveOnly() {
			return transmitHz <= 0.0D;
		}
	}
}
