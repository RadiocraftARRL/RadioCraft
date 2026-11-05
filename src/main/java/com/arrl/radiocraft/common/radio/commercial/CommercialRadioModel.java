package com.arrl.radiocraft.common.radio.commercial;

import com.arrl.radiocraft.common.radio.Band;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The commercial radio models and what each can be programmed with: the bands it covers, whether it does digital voice
 * and encryption, whether it has a display for the channel name, and how many channels it holds. A programmer refuses a channel the radio cannot
 * do ({@link #check}), as a real radio's programming software does.
 */
public enum CommercialRadioModel {

	/** 1990s brick: VHF high band, analog only, 16 channels, no display (RX/TX LEDs and the knob). */
	BASIC_HANDHELD("basic_handheld", 16, false, false, false, Band.VHF_COMMERCIAL),
	/**
	 * 1990s fire-service handheld: VHF high band and the 2m ham band, digital and encryption, a small display with
	 * the channel name and zone. 40 channels: its 16-position knob plus a zone switch (A = 1-16, B = 17-32,
	 * C = 33-40).
	 */
	UPGRADED_HANDHELD("upgraded_handheld", 40, true, true, true, Band.VHF_COMMERCIAL, "2m"),
	/**
	 * VHF high band mobile, in a dash or on a desk with its hand mic: analog and digital, encryption, a display.
	 * 16 channels.
	 */
	VHF_MOBILE("vhf_mobile", 16, true, true, true, Band.VHF_COMMERCIAL),
	/** Low band mobile, 30-50 MHz: analog only, with a channel number display. 16 channels. */
	LOW_BAND_MOBILE("low_band_mobile", 16, false, false, true, Band.LOW_BAND);

	private final String id;
	private final int channels;
	private final boolean digital;
	private final boolean encryption;
	private final boolean display;
	private final List<String> bands;

	CommercialRadioModel(String id, int channels, boolean digital, boolean encryption, boolean display, String... bands) {
		this.id = id;
		this.channels = channels;
		this.digital = digital;
		this.encryption = encryption;
		this.display = display;
		this.bands = List.of(bands);
	}

	public String id() {
		return id;
	}

	/** How many channels it holds (at most {@link CommercialProgram#MAX_CHANNELS}). */
	public int channelCount() {
		return channels;
	}

	/**
	 * How many zones of {@link CommercialProgram#KNOB_POSITIONS} channels its zone switch has: 1 (no zone switch) for a
	 * 16-channel radio, 3 (A, B, C) for 40.
	 */
	public int zones() {
		return CommercialProgram.zonesFor(channels);
	}

	/** True if it has a zone switch (more channels than its knob has positions). */
	public boolean hasZones() {
		return zones() > 1;
	}

	/** {@code program} as this radio holds it: channels past its capacity dropped, the knob and zone switch on its range. */
	public CommercialProgram fit(CommercialProgram program) {
		return program.fitTo(channels);
	}

	/** True if it can be programmed with digital channels. */
	public boolean digital() {
		return digital;
	}

	/** True if its digital channels can carry an encryption key. */
	public boolean encryption() {
		return encryption;
	}

	/** True if it has a display that shows the channel name. */
	public boolean hasDisplay() {
		return display;
	}

	/** The names of the bands it covers, its home band first. */
	public List<String> bandNames() {
		return bands;
	}

	/** Its home band (what a block radio of this model is built on). */
	public Band homeBand() {
		return band(bands.getFirst());
	}

	/** The bands it covers, from the server config (or the defaults before it loads). */
	public List<Band> bands() {
		List<Band> out = new ArrayList<>();
		for(String name : bands) {
			Band band = band(name);
			if(band != null)
				out.add(band);
		}
		return out;
	}

	/** The band of this radio's that holds {@code hz}, or null if it covers none that does. */
	@Nullable
	public Band bandFor(double hz) {
		for(Band band : bands())
			if(band.contains(hz))
				return band;
		return null;
	}

	/** True if the radio can work on {@code hz} (receive and, if it is a transmit frequency, transmit). */
	public boolean covers(double hz) {
		return bandFor(hz) != null;
	}

	/**
	 * Why {@code channel} cannot be programmed into this radio: a translation key under
	 * {@code message.radiocraft.commercial.}, or null if it can.
	 */
	@Nullable
	public String check(CommercialChannel channel) {
		if(channel.isBlank())
			return null;
		if(!covers(channel.rxHz()))
			return "rx_out_of_range";
		if(!channel.isReceiveOnly() && !covers(channel.txHz()))
			return "tx_out_of_range";
		if(channel.digital() && !digital)
			return "no_digital";
		if(channel.encrypted() && !encryption)
			return "no_encryption";
		return null;
	}

	/**
	 * The radio as it leaves the factory, so it works before anyone programs it: on VHF the five license-free MURS
	 * channels (and 146.520, the 2m calling frequency, on the dual-band handheld); on low band four simplex channels.
	 */
	public CommercialProgram factoryProgram() {
		List<CommercialChannel> channels = new ArrayList<>();
		if(bands.getFirst().equals(Band.LOW_BAND)) {
			channels.add(CommercialChannel.simplex("LB 1", 33_120_000.0F));
			channels.add(CommercialChannel.simplex("LB 2", 33_900_000.0F));
			channels.add(CommercialChannel.simplex("LB 3", 37_980_000.0F));
			channels.add(CommercialChannel.simplex("LB 4", 39_460_000.0F));
		}
		else {
			channels.add(CommercialChannel.simplex("MURS 1", 151_820_000.0F));
			channels.add(CommercialChannel.simplex("MURS 2", 151_880_000.0F));
			channels.add(CommercialChannel.simplex("MURS 3", 151_940_000.0F));
			channels.add(CommercialChannel.simplex("MURS 4", 154_570_000.0F));
			channels.add(CommercialChannel.simplex("MURS 5", 154_600_000.0F));
			if(bands.contains("2m"))
				channels.add(CommercialChannel.simplex("2M CALL", 146_520_000.0F));
		}
		return new CommercialProgram(channels, 0);
	}

	/** {@code id} as a model, or null. */
	@Nullable
	public static CommercialRadioModel byId(String id) {
		for(CommercialRadioModel model : values())
			if(model.id.equals(id))
				return model;
		return null;
	}

	/** The band {@code name} from the server config, or from the defaults when the config is not loaded (unit tests). */
	@Nullable
	static Band band(String name) {
		try {
			Band band = Band.getBand(name);
			if(band != null)
				return band;
		}
		catch(IllegalStateException | NullPointerException | ExceptionInInitializerError | NoClassDefFoundError e) {
			// Config not loaded: the defaults below.
		}
		for(Band band : Band.getAllDefaults())
			if(band.name().equals(name))
				return band;
		return null;
	}
}
