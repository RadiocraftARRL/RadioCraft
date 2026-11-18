package com.arrl.radiocraft.common.blocks;

import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * A Band Plan sheet hung on a wall (2026-11-18): the {@link com.arrl.radiocraft.common.items.BandPlanItem} used on the
 * side of a block puts it up there. Use it to read it; break it (or the wall behind it) and the sheet drops.
 * {@link #FACING} is the way the sheet faces, out from the wall. There is no block item: it is the sheet.
 */
public class BandPlanWallBlock extends HorizontalDirectionalBlock {

	public static final MapCodec<BandPlanWallBlock> CODEC = simpleCodec(BandPlanWallBlock::new);
	private static final VoxelShape NORTH = Block.box(2.0D, 1.0D, 15.0D, 14.0D, 15.0D, 16.0D); // On the south wall, facing north.
	private static final VoxelShape SOUTH = Block.box(2.0D, 1.0D, 0.0D, 14.0D, 15.0D, 1.0D);
	private static final VoxelShape EAST = Block.box(0.0D, 1.0D, 2.0D, 1.0D, 15.0D, 14.0D);
	private static final VoxelShape WEST = Block.box(15.0D, 1.0D, 2.0D, 16.0D, 15.0D, 14.0D);

	public BandPlanWallBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected @NotNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return switch(state.getValue(FACING)) {
			case SOUTH -> SOUTH;
			case EAST -> EAST;
			case WEST -> WEST;
			default -> NORTH;
		};
	}

	@Override
	protected @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return Shapes.empty(); // Paper: walk through it, like a painting.
	}

	/** Whether a sheet facing {@code facing} at {@code pos} has a wall behind it to hang on. */
	public static boolean canHang(LevelReader level, BlockPos pos, Direction facing) {
		BlockPos wall = pos.relative(facing.getOpposite());
		return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
	}

	@Override
	protected boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
		return canHang(level, pos, state.getValue(FACING));
	}

	@Override
	protected @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighborState,
											  @NotNull LevelAccessor level, @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
		if(direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos))
			return Blocks.AIR.defaultBlockState(); // The wall is gone: the sheet falls (and drops, from its loot table).
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(level.isClientSide)
			com.arrl.radiocraft.client.screens.BandPlanScreen.open(); // A separate class, so the server never loads it.
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public @NotNull ItemStack getCloneItemStack(@NotNull BlockState state, @NotNull HitResult target, @NotNull LevelReader level, @NotNull BlockPos pos, @NotNull Player player) {
		return new ItemStack(RadiocraftItems.BAND_PLAN.get());
	}
}
