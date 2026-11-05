package com.arrl.radiocraft.api.items;

import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.BandEdges;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Lets another mod's handheld radio say which band it transmits on and where it may transmit. Implement it on your
 * handheld's {@link Item}, which must extend RadioCraft's {@code VHFHandheldItem} (that is what makes RadioCraft carry
 * it as a radio: the player's radio, the screen, batteries, the desk charger, Curios), and register its capabilities
 * with {@link com.arrl.radiocraft.api.RadiocraftApi#registerHandhelds}.
 *
 * <p><b>Limits in 1.12</b>: every handheld is on the VHF antenna network and its dial tunes 143-174 MHz (2m and
 * commercial VHF, as the stock ham handheld). A handheld for HF or low band is not supported; RadioCraft's HF portable
 * is the manpack.</p>
 *
 * <p>Both methods are called on the server thread every tick for every handheld a player carries, and on the voice
 * thread: keep them quick and side-effect free.</p>
 *
 * @since 1.12.0
 */
public interface IHandheldRadio {

	/**
	 * The band this handheld's transmissions are sent on (they splatter outside it). 2m by default.
	 *
	 * @param stack the handheld
	 * @return the band, or null to use 2m
	 */
	default Band transmitBand(ItemStack stack) {
		return Band.getBand("2m");
	}

	/**
	 * Whether the handheld may transmit on {@code txHz}. By default, inside {@link #transmitBand}, or anywhere it tunes
	 * with a MARS mod ({@code hasMarsMod}). Return false and the radio shows OUT OF BAND and refuses the PTT.
	 *
	 * @param stack the handheld
	 * @param txHz where it would transmit, in Hz
	 * @param hasMarsMod true if a MARS mod is fitted to the stack
	 * @return true if it may transmit
	 */
	default boolean mayTransmit(ItemStack stack, float txHz, boolean hasMarsMod) {
		Band band = transmitBand(stack);
		if(band == null)
			band = Band.getBand("2m");
		return band == null || BandEdges.canTransmit(band, txHz, hasMarsMod);
	}
}
