package com.arrl.radiocraft.api.antenna;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A {@link Block} that is the feed point of an antenna (where the coax goes in and where {@link IAntennaType#match} is
 * asked), telling RadioCraft which antenna network the antenna built on it goes on. Without it an antenna block of
 * another mod is put on the HF network, which is right below 100 MHz (HF and low band) and deaf to every VHF radio.
 *
 * <p>{@link CompanionAntennaBlock} implements it.</p>
 *
 * @since 1.12.0
 */
public interface IAntennaBlock {

	/**
	 * The network the antenna on this block joins: {@link AntennaNetworks#HF} or {@link AntennaNetworks#VHF}
	 * ({@link AntennaNetworks#forBand} picks one for a band).
	 *
	 * @param state the block's state
	 * @return the network's id
	 */
	ResourceLocation getAntennaNetwork(BlockState state);
}
