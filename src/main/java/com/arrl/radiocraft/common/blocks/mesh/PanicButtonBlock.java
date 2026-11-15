package com.arrl.radiocraft.common.blocks.mesh;

import com.arrl.radiocraft.api.benetworks.IWireConnectable;
import com.arrl.radiocraft.common.blockentities.mesh.PullStationBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A vintage panic button (2026-11-11): a red push button on a small wall plate, for under a counter or a desk. It goes
 * on a floor, a wall or the underside of a block like a vanilla button, and works as a pull station: wired to a fire
 * alarm box by data wire (or touching it), pressed it stays in ({@link PullStationBlock#PULLED}) and the box sends the
 * alarm for its zone; the fire alarm key resets it.
 */
public class PanicButtonBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock, IWireConnectable, WallWired {

	public static final MapCodec<PanicButtonBlock> CODEC = simpleCodec(PanicButtonBlock::new);

	private static final VoxelShape FLOOR = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 2.5D, 11.0D);
	private static final VoxelShape CEILING = Block.box(5.0D, 13.5D, 5.0D, 11.0D, 16.0D, 11.0D);
	private static final VoxelShape WALL_NORTH = net.minecraft.world.phys.shapes.Shapes.or(Block.box(5.0D, 5.0D, 13.5D, 11.0D, 11.0D, 16.0D), Block.box(0.0D, 0.0D, 15.5D, 16.0D, 16.0D, 16.0D));
	private static final VoxelShape WALL_SOUTH = net.minecraft.world.phys.shapes.Shapes.or(Block.box(5.0D, 5.0D, 0.0D, 11.0D, 11.0D, 2.5D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 0.5D));
	private static final VoxelShape WALL_WEST = net.minecraft.world.phys.shapes.Shapes.or(Block.box(13.5D, 5.0D, 5.0D, 16.0D, 11.0D, 11.0D), Block.box(15.5D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D));
	private static final VoxelShape WALL_EAST = net.minecraft.world.phys.shapes.Shapes.or(Block.box(0.0D, 5.0D, 5.0D, 2.5D, 11.0D, 11.0D), Block.box(0.0D, 0.0D, 0.0D, 0.5D, 16.0D, 16.0D));

	@SuppressWarnings("this-escape")
	public PanicButtonBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.WALL).setValue(PullStationBlock.PULLED, false).setValue(WIRE_UP, false).setValue(WIRE_DOWN, false).setValue(WIRE_CW, Side.NONE).setValue(WIRE_CCW, Side.NONE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(FACING, FACE, PullStationBlock.PULLED, WIRE_UP, WIRE_DOWN, WIRE_CW, WIRE_CCW);
	}

	/** On a wall, the wall behind it; on a floor or under a desk, none. */
	@Override
	public Direction wallBehind(BlockState state) {
		return state.getValue(FACE) == AttachFace.WALL ? state.getValue(FACING).getOpposite() : null;
	}

	/**
	 * {@code state} with its wire pieces from the data wire around it (2026-11-11): on a wall as the other wall devices;
	 * on a floor or a ceiling, a cable along it from each side wire comes in by (the ceiling model is turned over, so
	 * its +x side is the facing's anticlockwise one).
	 */
	private static BlockState withWires(BlockState state, BlockGetter level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		return switch(state.getValue(FACE)) {
			case WALL -> WallWired.wires(state, level, pos, facing.getOpposite(), facing, facing.getClockWise());
			case FLOOR -> WallWired.wires(state, level, pos, null, facing, facing.getClockWise());
			case CEILING -> WallWired.wires(state, level, pos, null, facing, facing.getCounterClockWise());
		};
	}

	@Override
	public BlockState getStateForPlacement(@NotNull net.minecraft.world.item.context.BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		return state == null ? null : withWires(state, context.getLevel(), context.getClickedPos());
	}

	@Override
	protected @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighbor,
	                                          @NotNull net.minecraft.world.level.LevelAccessor level, @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
		BlockState out = super.updateShape(state, direction, neighbor, level, pos, neighborPos);
		return out.getBlock() == this ? withWires(out, level, pos) : out;
	}

	@Override
	protected @NotNull MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return switch(state.getValue(FACE)) {
			case FLOOR -> FLOOR;
			case CEILING -> CEILING;
			case WALL -> switch(state.getValue(FACING)) {
				case SOUTH -> WALL_SOUTH;
				case WEST -> WALL_WEST;
				case EAST -> WALL_EAST;
				default -> WALL_NORTH;
			};
		};
	}

	@Override
	public boolean acceptsData(@NotNull BlockState state) {
		return true;
	}

	@Override
	protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
	                                                   @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
		if(!stack.is(RadiocraftItems.FIRE_ALARM_KEY.get()))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if(!level.isClientSide) {
			if(state.getValue(PullStationBlock.PULLED)) {
				level.setBlock(pos, state.setValue(PullStationBlock.PULLED, false), Block.UPDATE_ALL);
				level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.5F, 1.2F);
				player.displayClientMessage(Component.translatable("message.radiocraft.panic_button.reset"), true);
			}
			else {
				int zone = level.getBlockEntity(pos) instanceof PullStationBlockEntity station ? station.getZone() : 0;
				player.displayClientMessage(Component.translatable("message.radiocraft.pull_station.zone", zone <= 0
						? Component.translatable("message.radiocraft.pull_station.no_zone") : Component.literal(String.format("%02d", zone))), true);
			}
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(player.isSecondaryUseActive()) { // Its number (and name), without pulling it (2026-11-11).
			if(!level.isClientSide)
				player.displayClientMessage(PullStationBlock.numberMessage(level, pos), true);
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		if(!level.isClientSide) {
			if(!state.getValue(PullStationBlock.PULLED)) {
				level.setBlock(pos, state.setValue(PullStationBlock.PULLED, true), Block.UPDATE_ALL);
				level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, 0.8F);
				player.displayClientMessage(Component.translatable("message.radiocraft.panic_button.pressed").withStyle(ChatFormatting.RED), true);
			}
			else
				player.displayClientMessage(Component.translatable("message.radiocraft.panic_button.needs_key"), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new PullStationBlockEntity(pos, state);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.panic_button").withStyle(ChatFormatting.GRAY));
	}
}
