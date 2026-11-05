package com.arrl.radiocraft.api.radio;

import com.arrl.radiocraft.api.benetworks.IWireConnectable;
import com.arrl.radiocraft.common.blocks.radios.RadioBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The block of another mod's radio: a RadioCraft radio block (it faces the player, hangs from a ceiling, can blow
 * its transmitter, takes coax and data wire without tags, ticks its {@link CompanionRadioBlockEntity}, gives a
 * comparator its S-meter, lends its hand mic on sneak-right-click).
 *
 * <p>Power wire, coax and data wire all join it without tags ({@link IWireConnectable}; override to refuse one).
 * Also register the block with {@link com.arrl.radiocraft.api.RadiocraftApi#registerNetworkBlocks}, and give it a
 * loot table that copies {@code radiocraft:mars_mod} if a fitted MARS mod should survive breaking it.</p>
 *
 * @since 1.12.0
 */
public class CompanionRadioBlock extends RadioBlock implements IWireConnectable {

	private final BlockEntityType.BlockEntitySupplier<? extends CompanionRadioBlockEntity> factory;

	/**
	 * Makes the block.
	 *
	 * @param properties the block properties (RadioCraft's radios use stone-like, no occlusion)
	 * @param factory makes the block entity, usually {@code (pos, state) -> new CompanionRadioBlockEntity(MY_TYPE.get(), pos, state, SPEC)}
	 */
	public CompanionRadioBlock(Properties properties, BlockEntityType.BlockEntitySupplier<? extends CompanionRadioBlockEntity> factory) {
		super(properties);
		this.factory = factory;
	}

	/**
	 * Not used: RadioCraft's radio blocks are not data driven.
	 *
	 * @return null
	 */
	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return null;
	}

	/**
	 * Power wire joins the radio.
	 *
	 * @param state the block's state
	 * @return true
	 */
	@Override
	public boolean acceptsPower(BlockState state) {
		return true;
	}

	/**
	 * Coax joins the radio (every radio block takes it anyway).
	 *
	 * @param state the block's state
	 * @return true
	 */
	@Override
	public boolean acceptsCoax(BlockState state) {
		return true;
	}

	/**
	 * Data wire joins the radio (every radio block takes it anyway).
	 *
	 * @param state the block's state
	 * @return true
	 */
	@Override
	public boolean acceptsData(BlockState state) {
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
}
