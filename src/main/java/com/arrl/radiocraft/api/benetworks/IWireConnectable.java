package com.arrl.radiocraft.api.benetworks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A {@link Block} that says in code which of RadioCraft's wires join it, as an alternative to the block tags
 * {@code radiocraft:power_blocks}, {@code radiocraft:coax_blocks} and {@code radiocraft:data_blocks}
 * ({@link com.arrl.radiocraft.api.RadiocraftApi.Tags}). Either is enough: a block in the tag or saying yes here
 * connects. RadioCraft's radio blocks always take coax and data wire.
 *
 * <p>The block also needs a network object for power and coax to do anything: a block entity implementing
 * {@link INetworkObjectProvider}, and the block registered with
 * {@link com.arrl.radiocraft.api.RadiocraftApi#registerNetworkBlocks}.</p>
 *
 * @since 1.12.0
 */
public interface IWireConnectable {

	/**
	 * Whether power wire joins this block (it draws from or feeds a power network).
	 *
	 * @param state the block's state
	 * @return true to connect; false by default
	 */
	default boolean acceptsPower(BlockState state) {
		return false;
	}

	/**
	 * Whether coax joins this block (a radio, an antenna feed point, anything in the RF path).
	 *
	 * @param state the block's state
	 * @return true to connect; false by default
	 */
	default boolean acceptsCoax(BlockState state) {
		return false;
	}

	/**
	 * Whether data wire joins this block (a radio's control and audio link to computers, interfaces and speakers).
	 *
	 * @param state the block's state
	 * @return true to connect; false by default
	 */
	default boolean acceptsData(BlockState state) {
		return false;
	}

	/**
	 * Whether {@code state}'s block connects to the wire kind asked, by this interface. The tags are checked separately.
	 *
	 * @param state any block state
	 * @param power true for power wire, false for coax
	 * @return true if the block implements this interface and accepts that wire
	 */
	static boolean accepts(BlockState state, boolean power) {
		return state.getBlock() instanceof IWireConnectable connectable && (power ? connectable.acceptsPower(state) : connectable.acceptsCoax(state));
	}

	/**
	 * Whether {@code state}'s block takes data wire by this interface.
	 *
	 * @param state any block state
	 * @return true if the block implements this interface and accepts data wire
	 */
	static boolean acceptsDataWire(BlockState state) {
		return state.getBlock() instanceof IWireConnectable connectable && connectable.acceptsData(state);
	}
}
