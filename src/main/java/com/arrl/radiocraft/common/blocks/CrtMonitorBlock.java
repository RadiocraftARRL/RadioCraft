package com.arrl.radiocraft.common.blocks;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.CrtMonitorBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The External CRT Monitor (2026-11-12): a vintage studio CRT monitor (tools/crt_monitor_model.py) that shows the
 * weather satellite mosaic of a radio computer running WXSAT, centred on where it stands. It links to such a computer
 * placed beside it (or on its data wire), or through another display that is linked; same-facing displays edge to edge
 * make one screen ({@link com.arrl.radiocraft.common.blockentities.CrtMonitorGroups}). Right-click cycles LIVE
 * MOSAIC, LOOP and OFF for the whole screen. It takes no power of its own: it shows while the computer is on.
 * <p>
 * On its own (not part of a bigger screen), with no WXSAT computer linked, touching a premium radio (2026-11-12) it
 * shows the radio's waterfall round the dial instead; right-click then cycles NARROW, WIDE and OFF. It shows while
 * the radio is on. See {@link com.arrl.radiocraft.common.blockentities.CrtMonitorBlockEntity}.
 */
public class CrtMonitorBlock extends BaseEntityBlock {

	public static final MapCodec<CrtMonitorBlock> CODEC = simpleCodec(CrtMonitorBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	@SuppressWarnings("this-escape")
	public CrtMonitorBlock(Properties properties) {
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

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(!level.isClientSide && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof CrtMonitorBlockEntity display) {
			String key = display.cycle(); // On a radio: NARROW, WIDE, OFF; on a computer: LIVE MOSAIC, LOOP, OFF.
			serverPlayer.displayClientMessage(Component.translatable(Radiocraft.translationKey("message", "external_crt_monitor.mode"),
					Component.translatable(Radiocraft.translationKey("message", "external_crt_monitor.mode." + key))).withStyle(ChatFormatting.GREEN), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block neighbour, @NotNull BlockPos from, boolean moving) {
		super.neighborChanged(state, level, pos, neighbour, from, moving);
		if(level.getBlockEntity(pos) instanceof CrtMonitorBlockEntity display)
			display.neighboursChanged(); // A display placed or broken beside it: the screen re-forms.
	}

	/** Both uses: a WXSAT computer's mosaic, and a premium radio's waterfall. */
	@Override
	public void appendHoverText(@NotNull net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.@NotNull TooltipContext context,
								@NotNull java.util.List<Component> tooltip, @NotNull net.minecraft.world.item.TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.external_crt_monitor").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.radiocraft.external_crt_monitor.radio").withStyle(ChatFormatting.GRAY));
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
		return new CrtMonitorBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
		return level.isClientSide ? null : createTickerHelper(type, RadiocraftBlockEntities.EXTERNAL_CRT_MONITOR.get(), CrtMonitorBlockEntity::serverTick);
	}
}
