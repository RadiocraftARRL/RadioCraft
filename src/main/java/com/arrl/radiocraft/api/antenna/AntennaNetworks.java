package com.arrl.radiocraft.api.antenna;

import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.networks.AntennaNetworkManager;
import net.minecraft.resources.ResourceLocation;

/**
 * The antenna networks. Every antenna on the air, every radio's bare connector and every handheld is on one of two
 * networks, and a signal only reaches antennas on the network it was sent on:
 * <ul>
 *     <li>{@link #HF}: everything below 100 MHz (80m to 10m and commercial low band, 30-50 MHz),</li>
 *     <li>{@link #VHF}: 100 MHz and up (2m, commercial VHF 150-174 MHz and the marine channels). Handhelds are always
 *     here.</li>
 * </ul>
 * Which band a signal is on is carried in the packet; the network only decides who is asked.
 *
 * @since 1.12.0
 */
public final class AntennaNetworks {

	/** The HF network id, {@code radiocraft:hf}. */
	public static final ResourceLocation HF = AntennaNetworkManager.HF_ID;
	/** The VHF network id, {@code radiocraft:vhf}. */
	public static final ResourceLocation VHF = AntennaNetworkManager.VHF_ID;

	private AntennaNetworks() {}

	/**
	 * The network a band's antennas belong on: {@link #VHF} for bands from 100 MHz up ({@link Band#isVhf()}), else
	 * {@link #HF}.
	 *
	 * @param band the band, or null
	 * @return the network id ({@link #HF} for null)
	 */
	public static ResourceLocation forBand(Band band) {
		return band != null && band.isVhf() ? VHF : HF;
	}

	/**
	 * A network by id, to add or remove an {@link IAntenna} of your own (a vehicle antenna that moves, say). Not from
	 * the voice thread.
	 *
	 * @param id {@link #HF} or {@link #VHF}
	 * @return the network, or null for an unknown id
	 */
	public static IAntennaNetwork get(ResourceLocation id) {
		return AntennaNetworkManager.getNetwork(id);
	}
}
