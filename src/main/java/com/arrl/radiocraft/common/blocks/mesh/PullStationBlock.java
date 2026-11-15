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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A fire alarm pull station (2026-11-10): the indoor one goes on a wall, the outdoor one is a street pedestal. It joins
 * its fire alarm box by data wire (or by touching it). Right-click pulls it ({@link #PULLED}); it stays down until it
 * is reset with the fire alarm key.
 */
public class PullStationBlock extends BaseEntityBlock implements IWireConnectable, WallWired {

	public static final MapCodec<PullStationBlock> CODEC = simpleCodec(p -> new PullStationBlock(p, false));
	public static final MapCodec<PullStationBlock> OUTDOOR_CODEC = simpleCodec(p -> new PullStationBlock(p, true));
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty PULLED = BooleanProperty.create("pulled");

	private static final java.util.Map<Direction, VoxelShape> WALL = MeshDeviceBlock.shapes(5.5D, 4.0D, 12.0D, 10.5D, 12.0D, 16.0D);
	private static final java.util.Map<Direction, VoxelShape> PLATE = MeshDeviceBlock.shapes(0.0D, 0.0D, 15.0D, 16.0D, 16.0D, 16.0D); // The mounting plate, the whole wall face.
	private static final VoxelShape PEDESTAL = net.minecraft.world.phys.shapes.Shapes.or(Block.box(5.0D, 0.0D, 5.0D, 11.0D, 8.0D, 11.0D), Block.box(4.5D, 8.0D, 4.5D, 11.5D, 16.0D, 11.5D)); // Post and head (tools/fire_alarm_assets.py).

	private final boolean outdoor;

	@SuppressWarnings("this-escape")
	public PullStationBlock(Properties properties, boolean outdoor) {
		super(properties);
		this.outdoor = outdoor;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PULLED, false).setValue(WallWired.WIRE_UP, false).setValue(WallWired.WIRE_DOWN, false).setValue(WallWired.WIRE_CW, WallWired.Side.NONE).setValue(WallWired.WIRE_CCW, WallWired.Side.NONE));
	}

	public boolean isOutdoor() {
		return outdoor;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(FACING, PULLED, WallWired.WIRE_UP, WallWired.WIRE_DOWN, WallWired.WIRE_CW, WallWired.WIRE_CCW);
	}

	@Override
	public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
		if(outdoor)
			return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
		Direction clicked = context.getClickedFace();
		Direction facing = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
		BlockState state = defaultBlockState().setValue(FACING, facing);
		return WallWired.wires(state, context.getLevel(), context.getClickedPos(), facing.getOpposite(), facing, facing.getClockWise());
	}

	/** The indoor one is on the wall behind it; the outdoor one stands on its own. */
	@Override
	public Direction wallBehind(BlockState state) {
		return outdoor ? null : state.getValue(FACING).getOpposite();
	}

	/** Its wire pieces follow the data wire around it (2026-11-11, {@link WallWired}). */
	@Override
	protected @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighbor,
	                                          @NotNull net.minecraft.world.level.LevelAccessor level, @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
		BlockState out = super.updateShape(state, direction, neighbor, level, pos, neighborPos);
		return out.getBlock() == this ? (outdoor ? out : WallWired.wires(out, level, pos, wallBehind(out), out.getValue(FACING), out.getValue(FACING).getClockWise())) : out;
	}

	@Override
	protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		if(outdoor)
			return PEDESTAL;
		return net.minecraft.world.phys.shapes.Shapes.or(WALL.get(state.getValue(FACING)), PLATE.get(state.getValue(FACING)));
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
			if(state.getValue(PULLED)) {
				level.setBlock(pos, state.setValue(PULLED, false), Block.UPDATE_ALL);
				level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.5F, 1.4F);
				player.displayClientMessage(Component.translatable("message.radiocraft.pull_station.reset"), true);
			}
			else
				player.displayClientMessage(Component.translatable("message.radiocraft.pull_station.zone", zone(level, pos)), true);
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
			if(!state.getValue(PULLED)) {
				level.setBlock(pos, state.setValue(PULLED, true), Block.UPDATE_ALL);
				level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 0.7F);
				player.displayClientMessage(Component.translatable("message.radiocraft.pull_station.pulled").withStyle(ChatFormatting.RED), true);
			}
			else
				player.displayClientMessage(Component.translatable("message.radiocraft.pull_station.needs_key"), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** "Station 03: KITCHEN", or that no box has numbered it yet: what shift + right-click shows. */
	public static Component numberMessage(Level level, BlockPos pos) {
		if(!(level.getBlockEntity(pos) instanceof PullStationBlockEntity station) || station.getZone() <= 0)
			return Component.translatable("message.radiocraft.pull_station.no_number");
		String number = String.format(java.util.Locale.ROOT, "%02d", station.getZone());
		String kind = station.getKind().name();
		return station.getName().isEmpty() ? Component.translatable("message.radiocraft.pull_station.number", number, kind)
				: Component.translatable("message.radiocraft.pull_station.number_named", number, station.getName(), kind);
	}

	private static Component zone(Level level, BlockPos pos) {
		int zone = level.getBlockEntity(pos) instanceof PullStationBlockEntity station ? station.getZone() : 0;
		return zone <= 0 ? Component.translatable("message.radiocraft.pull_station.no_zone") : Component.literal(String.format("%02d", zone));
	}

	@Override
	public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return outdoor ? OUTDOOR_CODEC : CODEC;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new PullStationBlockEntity(pos, state);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.pull_station").withStyle(ChatFormatting.GRAY));
	}
}
