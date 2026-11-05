package com.arrl.radiocraft.api.antenna;

import com.arrl.radiocraft.api.benetworks.IWireConnectable;
import com.arrl.radiocraft.common.blockentities.AntennaBlockEntity;
import com.arrl.radiocraft.common.blocks.AbstractNetworkBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A block that is the feed point of another mod's antenna: coax joins it (no tag needed), it carries a
 * {@link CompanionAntennaBlockEntity}, and the antenna built on it goes on the network given here. Pair it with an
 * {@link IAntennaType} whose {@code match} recognises it ({@link SimpleAntennaType.Builder#block} does that for a
 * one-block antenna). Breaking it takes the antenna off the air.
 *
 * <p>Also register the block for RadioCraft's network capability:
 * {@link com.arrl.radiocraft.api.RadiocraftApi#registerNetworkBlocks}.</p>
 *
 * @since 1.12.0
 */
public class CompanionAntennaBlock extends AbstractNetworkBlock implements IAntennaBlock, IWireConnectable {

	private final ResourceLocation network;
	private final BlockEntityType.BlockEntitySupplier<? extends CompanionAntennaBlockEntity> factory;

	/**
	 * Makes the block.
	 *
	 * @param properties the block properties
	 * @param network the antenna network: {@link AntennaNetworks#HF} below 100 MHz, {@link AntennaNetworks#VHF} above
	 * @param factory makes the block entity, usually {@code (pos, state) -> new CompanionAntennaBlockEntity(MY_TYPE.get(), pos, state)}
	 */
	public CompanionAntennaBlock(Properties properties, ResourceLocation network, BlockEntityType.BlockEntitySupplier<? extends CompanionAntennaBlockEntity> factory) {
		super(properties);
		this.network = network;
		this.factory = factory;
	}

	/**
	 * Not used: RadioCraft's network blocks are not data driven.
	 *
	 * @return null
	 */
	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return null;
	}

	/**
	 * The network given to the constructor.
	 *
	 * @param state the block's state
	 * @return the network id
	 */
	@Override
	public ResourceLocation getAntennaNetwork(BlockState state) {
		return network;
	}

	/**
	 * Coax runs into the feed point.
	 *
	 * @param state the block's state
	 * @return true
	 */
	@Override
	public boolean acceptsCoax(BlockState state) {
		return true;
	}

	/**
	 * Makes the block entity with the factory given to the constructor.
	 *
	 * @param pos where it is
	 * @param state the block
	 * @return the block entity
	 */
	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return factory.create(pos, state);
	}

	/**
	 * Ticks the antenna re-check on the server.
	 *
	 * @param level the level
	 * @param state the block
	 * @param type the block entity type
	 * @param <T> the block entity class
	 * @return the server ticker, null on the client
	 */
	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
		return level.isClientSide ? null : AntennaBlockEntity::tick;
	}

	/**
	 * A block next to the feed point changed (an element added or taken away): the antenna is looked at again after
	 * the antenna update delay.
	 */
	@Override
	protected void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos fromPos, boolean isMoving) {
		super.neighborChanged(state, level, pos, block, fromPos, isMoving);
		if(!level.isClientSide && level.getBlockEntity(pos) instanceof AntennaBlockEntity antenna)
			antenna.markAntennaChanged();
	}
}
