package com.arrl.radiocraft.common.blocks.mesh;

import com.arrl.radiocraft.common.blockentities.mesh.FireAlarmBoxBlockEntity;
import com.arrl.radiocraft.common.blockentities.mesh.MeshDeviceBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.radio.firealarm.FireAlarms;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * A radio fire alarm box (2026-11-10, see {@link FireAlarmBoxBlockEntity}). Its lamp is {@link #LIT} while a message
 * waits for an ack. A commercial license sets its frequency and box number; its name from an anvil is its location.
 * The fire alarm key opens it (its panel, 2026-11-11: TEST, RESET, SILENCE, DRILL); right-click tells its status. It
 * rings the bells by a redstone signal out of its back, strong into the wall it hangs on.
 */
public class FireAlarmBoxBlock extends MeshDeviceBlock implements WallWired {

	public static final MapCodec<FireAlarmBoxBlock> CODEC = simpleCodec(FireAlarmBoxBlock::new);
	public static final BooleanProperty LIT = BooleanProperty.create("lit");
	/** The wall cabinet, facing north (its back on the south side of the block); turned to its facing. */
	private static final java.util.Map<net.minecraft.core.Direction, VoxelShape> SHAPES = shapes(1.0D, 2.5D, 10.0D, 15.0D, 13.5D, 16.0D);

	@SuppressWarnings("this-escape")
	public FireAlarmBoxBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(LIT, false).setValue(WallWired.WIRE_UP, false).setValue(WallWired.WIRE_DOWN, false).setValue(WallWired.WIRE_CW, WallWired.Side.NONE).setValue(WallWired.WIRE_CCW, WallWired.Side.NONE));
	}

	/** On the wall behind it: its back. */
	@Override
	public net.minecraft.core.Direction wallBehind(BlockState state) {
		return state.getValue(FACING).getOpposite();
	}

	@Override
	public BlockState getStateForPlacement(@NotNull net.minecraft.world.item.context.BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		return state == null ? null : WallWired.wires(state, context.getLevel(), context.getClickedPos(), wallBehind(state), state.getValue(FACING), state.getValue(FACING).getClockWise());
	}

	/** Its wire pieces follow the data wire around it (2026-11-11, {@link WallWired}). */
	@Override
	protected @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull net.minecraft.core.Direction direction, @NotNull BlockState neighbor,
	                                          @NotNull net.minecraft.world.level.LevelAccessor level, @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
		BlockState out = super.updateShape(state, direction, neighbor, level, pos, neighborPos);
		return out.getBlock() == this ? WallWired.wires(out, level, pos, wallBehind(out), out.getValue(FACING), out.getValue(FACING).getClockWise()) : out;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT, WallWired.WIRE_UP, WallWired.WIRE_DOWN, WallWired.WIRE_CW, WallWired.WIRE_CCW);
	}

	@Override
	protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
	                                                   @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
		if(!stack.is(RadiocraftItems.FIRE_ALARM_KEY.get()))
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof FireAlarmBoxBlockEntity box) {
			level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5F, 1.5F);
			serverPlayer.openMenu(box, pos);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof FireAlarmBoxBlockEntity box) {
			if(player.isSecondaryUseActive())
				status(player, state, box);
			else
				box.openSetup(serverPlayer); // Its frequency, callsign and location (2026-11-11).
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected Component addressText(MeshDeviceBlockEntity device) {
		return Component.translatable("message.radiocraft.fire_alarm_box.as_box", String.format(Locale.ROOT, "%04d", device.getAddress()));
	}

	@Override
	public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean isMoving) {
		if(level instanceof ServerLevel serverLevel && newState.getBlock() != this && level.getBlockEntity(pos) instanceof FireAlarmBoxBlockEntity box)
			FireAlarms.of(serverLevel.getServer()).boxRemoved(serverLevel.getServer(), box.globalPos());
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	protected boolean hasAnalogOutputSignal(@NotNull BlockState state) {
		return true;
	}

	/** A comparator reads 15 while the box is in alarm, silenced or not (2026-11-11). */
	@Override
	protected int getAnalogOutputSignal(@NotNull BlockState state, @NotNull net.minecraft.world.level.Level level, @NotNull BlockPos pos) {
		return level.getBlockEntity(pos) instanceof FireAlarmBoxBlockEntity box ? box.comparatorSignal() : 0;
	}

	@Override
	protected boolean isSignalSource(@NotNull BlockState state) {
		return true;
	}

	/** The bells: out of the back only ({@code direction} points from the neighbour asking into this block). */
	@Override
	protected int getSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull net.minecraft.core.Direction direction) {
		return direction == state.getValue(FACING) && level.getBlockEntity(pos) instanceof FireAlarmBoxBlockEntity box && box.bellsRinging() ? 15 : 0;
	}

	@Override
	protected int getDirectSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull net.minecraft.core.Direction direction) {
		return getSignal(state, level, pos, direction);
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new FireAlarmBoxBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
		return level.isClientSide || type != RadiocraftBlockEntities.FIRE_ALARM_BOX.get() ? null : FireAlarmBoxBlockEntity::tick;
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.fire_alarm_box").withStyle(ChatFormatting.GRAY));
	}
}
