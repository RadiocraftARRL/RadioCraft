package com.arrl.radiocraft.common.blocks;

import com.arrl.radiocraft.common.blockentities.ComputerMonitorBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.items.FloppyDiskItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The radio computer's monitor and keyboard (1.12 step 5): the moderation computer's CRT and keyboard without its
 * desktop case, in a darker putty colour with a blue DOS screen. It goes on top of a {@link RadioComputerBlock}; using
 * it sits you at the computer below, and the screen you leave shows on its glass ({@code ComputerMonitorRenderer}).
 */
public class ComputerMonitorBlock extends BaseEntityBlock {

	public static final MapCodec<ComputerMonitorBlock> CODEC = simpleCodec(ComputerMonitorBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	private static final Map<Direction, VoxelShape> SHAPES = shapes();

	/** The model faces north: the keyboard in front, the monitor on its stand behind it (see tools/radio_computer_models.py). */
	private static Map<Direction, VoxelShape> shapes() {
		Map<Direction, VoxelShape> keyboard = SpeakerBlock.shapes(new double[] { 2.0D, 0.0D, 0.5D, 14.0D, 1.5D, 5.0D });
		Map<Direction, VoxelShape> monitor = SpeakerBlock.shapes(new double[] { 2.5D, 0.0D, 6.0D, 13.5D, 10.5D, 15.0D });
		Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
		for(Direction direction : keyboard.keySet())
			out.put(direction, Shapes.or(keyboard.get(direction), monitor.get(direction)));
		return out;
	}

	@SuppressWarnings("this-escape")
	public ComputerMonitorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Nullable
	public static RadioComputerBlockEntity computerUnder(Level level, BlockPos pos) {
		return level.getBlockEntity(pos.below()) instanceof RadioComputerBlockEntity computer ? computer : null;
	}

	@Override
	protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
													   @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
		RadioComputerBlockEntity computer = computerUnder(level, pos);
		if(!FloppyDiskItem.isFloppy(stack) || computer == null)
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if(!level.isClientSide)
			RadioComputerBlock.insert(computer, player, stack);
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
			RadioComputerBlockEntity computer = computerUnder(level, pos);
			if(computer == null)
				serverPlayer.displayClientMessage(RadioComputerBlock.message("no_computer").withStyle(ChatFormatting.YELLOW), true);
			else
				computer.open(serverPlayer, pos);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new ComputerMonitorBlockEntity(pos, state);
	}
}
