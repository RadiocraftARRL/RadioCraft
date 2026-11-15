package com.arrl.radiocraft.common.blocks.mesh;

import com.arrl.radiocraft.common.blockentities.mesh.FireAlarmControllerBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.radio.firealarm.AlarmEvent;
import com.arrl.radiocraft.common.radio.firealarm.FireAlarms;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.Items;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The fire alarm controller (2026-11-10, see {@link FireAlarmControllerBlockEntity}): an RC-704 receiver. Right-click
 * opens its terminal (2026-11-11: the event screen with NEXT, SILENCE, FAST and E, its box list and setup); shift +
 * right-click is NEXT in the action bar, or clears a head jam; paper loads the printer. {@link #POWERED} is its bell output; {@link #ALARM} is a box in alarm, for the screen's look.
 */
public class FireAlarmControllerBlock extends MeshDeviceBlock {

	public static final MapCodec<FireAlarmControllerBlock> CODEC = simpleCodec(FireAlarmControllerBlock::new);
	public static final BooleanProperty POWERED = BooleanProperty.create("powered");
	public static final BooleanProperty ALARM = BooleanProperty.create("alarm");

	@SuppressWarnings("this-escape")
	public FireAlarmControllerBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(ALARM, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED, ALARM);
	}

	@Override
	protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
	                                                   @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
		if(!stack.is(Items.PAPER))
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		if(!level.isClientSide && level.getBlockEntity(pos) instanceof FireAlarmControllerBlockEntity controller) {
			int left = controller.addPaper(stack.getCount());
			int used = stack.getCount() - left;
			if(used > 0) {
				stack.consume(used, player);
				level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.6F, 1.2F);
			}
			player.displayClientMessage(Component.translatable("message.radiocraft.fire_alarm_controller.paper", controller.getPaper()), true);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(level.isClientSide || !(level.getBlockEntity(pos) instanceof FireAlarmControllerBlockEntity controller))
			return InteractionResult.sidedSuccess(level.isClientSide);
		String base = "message.radiocraft.fire_alarm_controller.";
		if(!player.isSecondaryUseActive() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) { // Its terminal (2026-11-11).
			serverPlayer.openMenu(controller, pos);
			return InteractionResult.SUCCESS;
		}
		if(player.isSecondaryUseActive()) {
			if(controller.clearJam()) {
				level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.4F, 1.6F);
				player.displayClientMessage(Component.translatable(base + "jam_cleared"), true);
			}
			else {
				AlarmEvent event = controller.next();
				player.displayClientMessage(event == null ? Component.translatable(base + "log_empty") : Component.literal(event.logLine() + (event.label().isEmpty() ? "" : "  " + event.label())), true);
			}
		}
		else if(controller.acknowledge()) {
			level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.2F);
			player.displayClientMessage(Component.translatable(base + "silenced").withStyle(ChatFormatting.YELLOW), true);
		}
		else
			status(player, state, controller);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean isMoving) {
		if(level instanceof ServerLevel serverLevel && newState.getBlock() != this && level.getBlockEntity(pos) instanceof FireAlarmControllerBlockEntity controller) {
			if(controller.getPaper() > 0)
				net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, new ItemStack(Items.PAPER, controller.getPaper()));
			FireAlarms.of(serverLevel.getServer()).controllerRemoved(serverLevel.getServer(), controller.globalPos());
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	/** The RC-704's case (tools/fire_alarm_controller_assets.py), by facing. */
	private static final java.util.Map<Direction, net.minecraft.world.phys.shapes.VoxelShape> SHAPES = shapes(0.0D, 0.0D, 0.6D, 16.0D, 9.0D, 15.0D);

	@Override
	protected net.minecraft.world.phys.shapes.@NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos,
	                                                                        net.minecraft.world.phys.shapes.@NotNull CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
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
		return new FireAlarmControllerBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
		return level.isClientSide || type != RadiocraftBlockEntities.FIRE_ALARM_CONTROLLER.get() ? null : FireAlarmControllerBlockEntity::tick;
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.fire_alarm_controller").withStyle(ChatFormatting.GRAY));
	}
}
