package com.arrl.radiocraft.common.blocks.mesh;

import com.arrl.radiocraft.common.init.RadiocraftSoundEvents;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * A fire alarm bell (2026-11-11): a red gong on a wall, rung by redstone. It rings for as long as it is powered, from
 * any side: a fire alarm box powers the wall behind it while in alarm, so bells on that wall (or wired to it by
 * redstone) ring with it, and SILENCE quiets them. Nothing to do with the radio: any redstone rings it.
 */
public class FireBellBlock extends HorizontalDirectionalBlock {

	public static final MapCodec<FireBellBlock> CODEC = simpleCodec(FireBellBlock::new);
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	/** Ticks between rings of its sound, which is that long. */
	public static final int RING_TICKS = 10;
	/** The gong and its plate (tools/fire_alarm_assets.py), facing north on a south wall. */
	private static final Map<Direction, VoxelShape> SHAPES = MeshDeviceBlock.shapes(3.0D, 3.0D, 10.0D, 13.0D, 13.0D, 16.0D);
	/** Its mounting plate: the whole wall face. */
	private static final Map<Direction, VoxelShape> PLATE = MeshDeviceBlock.shapes(0.0D, 0.0D, 15.5D, 16.0D, 16.0D, 16.0D);

	@SuppressWarnings("this-escape")
	public FireBellBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
	}

	@Override
	protected @NotNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED);
	}

	/** On the wall clicked, facing out of it; on a floor or ceiling, facing the player. */
	@Override
	public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
		Direction clicked = context.getClickedFace();
		Direction facing = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
		return defaultBlockState().setValue(FACING, facing).setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
	}

	@Override
	protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return net.minecraft.world.phys.shapes.Shapes.or(SHAPES.get(state.getValue(FACING)), PLATE.get(state.getValue(FACING)));
	}

	@Override
	protected void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moved) {
		if(state.getValue(POWERED) && !level.isClientSide)
			level.scheduleTick(pos, this, 1);
	}

	@Override
	protected void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos from, boolean moved) {
		if(level.isClientSide)
			return;
		boolean powered = level.hasNeighborSignal(pos);
		if(powered == state.getValue(POWERED))
			return;
		level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
		if(powered)
			level.scheduleTick(pos, this, 1);
	}

	/** While powered: a ring, and the next one booked. */
	@Override
	protected void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
		if(!state.getValue(POWERED))
			return;
		level.playSound(null, pos, RadiocraftSoundEvents.FIRE_BELL.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
		level.scheduleTick(pos, this, RING_TICKS);
	}
}
