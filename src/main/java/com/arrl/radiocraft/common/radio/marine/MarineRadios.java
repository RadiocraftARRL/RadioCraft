package com.arrl.radiocraft.common.radio.marine;

import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.MarineChannels;

/**
 * What the marine radios share that needs the game: the channel plan from the server config (synced to clients, so
 * screens read it too) and the band their signals go out on.
 */
public final class MarineRadios {

	private MarineRadios() {}

	/** The channel plan in force ({@code marine_channel_plan}); the US plan before the config loads. */
	public static MarineChannels.Region region() {
		try {
			MarineChannels.Region region = RadiocraftServerConfig.MARINE_CHANNEL_PLAN.get();
			return region == null ? MarineChannels.Region.US : region;
		}
		catch(IllegalStateException | NullPointerException e) {
			return MarineChannels.Region.US; // Config not loaded yet.
		}
	}

	/**
	 * The band marine transmissions go out on: commercial VHF, which holds the whole marine band, so they are in band
	 * and never splatter like an out-of-band (MARS) signal.
	 */
	public static Band band() {
		Band band = Band.getBand(Band.VHF_COMMERCIAL);
		return band != null ? band : Band.getBand("2m");
	}
}
