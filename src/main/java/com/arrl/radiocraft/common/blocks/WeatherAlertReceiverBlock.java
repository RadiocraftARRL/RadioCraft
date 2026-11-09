package com.arrl.radiocraft.common.blocks;

import com.arrl.radiocraft.common.blockentities.WeatherAlertReceiverBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
 * The Simple Weather Alert Receiver (see {@link WeatherAlertReceiverBlockEntity}): a small table-top weather radio. Its
 * red lamp is {@link #LIT} while it flashes; {@link #POWERED} is its one redstone pulse on a new alert. Shift +
 * right-click with a Small Alkaline (or NiCd) Battery swaps the battery, with an empty hand takes it out; right-click silences
 * an alert or tells its status.
 */
public class WeatherAlertReceiverBlock extends AbstractNetworkBlock {

	public static final MapCodec<WeatherAlertReceiverBlock> CODEC = simpleCodec(WeatherAlertReceiverBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty LIT = BooleanProperty.create("lit");
	public static final BooleanProperty POWERED = BooleanProperty.create("powered");
	private static final VoxelShape SHAPE_NS = Block.box(2.0D, 0.0D, 4.0D, 14.0D, 7.0D, 12.0D);
	private static final VoxelShape SHAPE_EW = Block.box(4.0D, 0.0D, 2.0D, 12.0D, 7.0D, 14.0D);

	@SuppressWarnings("this-escape")
	public WeatherAlertReceiverBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT, POWERED);
	}

	@Override
	public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
	}

	/**
	 * Shift + right-click with a battery: vanilla skips the block when a sneaking player holds something, so
	 * {@code WeatherAlertReceiverEvents} calls this.
	 */
	public static void swapBattery(Level level, BlockPos pos, Player player, ItemStack stack) {
		if(level.isClientSide || !(level.getBlockEntity(pos) instanceof WeatherAlertReceiverBlockEntity receiver))
			return;
		ItemStack old = receiver.swapBattery(stack);
		stack.shrink(1);
		if(!old.isEmpty() && !player.getInventory().add(old))
			player.drop(old, false);
		level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.4F);
	}

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(!level.isClientSide && level.getBlockEntity(pos) instanceof WeatherAlertReceiverBlockEntity receiver) {
			if(player.isSecondaryUseActive()) { // Take the battery out.
				ItemStack old = receiver.swapBattery(ItemStack.EMPTY);
				if(!old.isEmpty()) {
					if(!player.getInventory().add(old))
						player.drop(old, false);
					level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6F, 1.4F);
				}
			}
			else if(receiver.isAlerting(level.getGameTime())) {
				receiver.silence();
				player.displayClientMessage(Component.translatable("message.radiocraft.weather_alert_receiver.silenced"), true);
			}
			else {
				player.sendSystemMessage(Component.translatable("block.radiocraft.weather_alert_receiver").withStyle(ChatFormatting.BOLD));
				for(Component line : receiver.statusLines())
					player.sendSystemMessage(line);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean isMoving) {
		if(!level.isClientSide && newState.getBlock() != this && level.getBlockEntity(pos) instanceof WeatherAlertReceiverBlockEntity receiver && !receiver.getBattery().isEmpty())
			Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, receiver.swapBattery(ItemStack.EMPTY));
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	protected boolean isSignalSource(@NotNull BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction direction) {
		return state.getValue(POWERED) ? 15 : 0;
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new WeatherAlertReceiverBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
		return level.isClientSide || type != RadiocraftBlockEntities.WEATHER_ALERT_RECEIVER.get() ? null : WeatherAlertReceiverBlockEntity::tick;
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.weather_alert_receiver").withStyle(ChatFormatting.GRAY));
	}
}
