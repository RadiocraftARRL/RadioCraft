package com.arrl.radiocraft.common.blocks.radios;

import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The top of the FM broadcast transmitter's cabinet (2026-11-13): its control panel, meters and exciter. No radio of
 * its own: right click opens the transmitter below, breaking it breaks that, and it goes when that does (it drops
 * nothing itself).
 */
public class FmBroadcastTransmitterTopBlock extends HorizontalDirectionalBlock {

	public static final BooleanProperty ON_AIR = FmBroadcastTransmitterBlock.ON_AIR;

	public FmBroadcastTransmitterTopBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON_AIR, false));
	}

	@Override
	protected @NotNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return simpleCodec(FmBroadcastTransmitterTopBlock::new);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(FACING, ON_AIR);
	}

	@Override
	protected boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
		return level.getBlockState(pos.below()).is(RadiocraftBlocks.FM_BROADCAST_TRANSMITTER.get());
	}

	@Override
	protected @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighbour, @NotNull LevelAccessor level,
	                                          @NotNull BlockPos pos, @NotNull BlockPos neighbourPos) {
		if(direction == Direction.DOWN && !neighbour.is(RadiocraftBlocks.FM_BROADCAST_TRANSMITTER.get()))
			return Blocks.AIR.defaultBlockState();
		return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
	}

	/** Breaking the top breaks the transmitter below, which drops the item (unless in creative). */
	@Override
	public @NotNull BlockState playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
		BlockPos below = pos.below();
		if(!level.isClientSide && level.getBlockState(below).is(RadiocraftBlocks.FM_BROADCAST_TRANSMITTER.get()))
			level.destroyBlock(below, !player.isCreative(), player);
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected @NotNull List<ItemStack> getDrops(@NotNull BlockState state, LootParams.@NotNull Builder params) {
		return List.of();
	}

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		BlockPos below = pos.below();
		BlockState transmitter = level.getBlockState(below);
		if(!transmitter.is(RadiocraftBlocks.FM_BROADCAST_TRANSMITTER.get()))
			return InteractionResult.PASS;
		return transmitter.useWithoutItem(level, player, hit.withPosition(below));
	}

	@Override
	public @NotNull ItemStack getCloneItemStack(@NotNull LevelReader level, @NotNull BlockPos pos, @NotNull BlockState state) {
		return new ItemStack(RadiocraftBlocks.FM_BROADCAST_TRANSMITTER.get());
	}
}
