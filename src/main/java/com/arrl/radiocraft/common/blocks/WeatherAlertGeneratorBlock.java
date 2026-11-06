package com.arrl.radiocraft.common.blocks;

import com.arrl.radiocraft.common.blockentities.WeatherAlertGeneratorBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Simple Weather Alert Generator (see {@link WeatherAlertGeneratorBlockEntity}): a cabinet that touches an APRS
 * station (or is cabled to one by data wire), takes power and coax. Its green lamp ({@link #READY}) is lit while it is
 * on the air, its red one ({@link #TRANSMITTING}) while it sends. Right-click it for its status.
 */
public class WeatherAlertGeneratorBlock extends AbstractNetworkBlock {

	public static final MapCodec<WeatherAlertGeneratorBlock> CODEC = simpleCodec(WeatherAlertGeneratorBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty READY = BooleanProperty.create("ready");
	public static final BooleanProperty TRANSMITTING = BooleanProperty.create("transmitting");

	@SuppressWarnings("this-escape")
	public WeatherAlertGeneratorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(READY, false).setValue(TRANSMITTING, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(FACING, READY, TRANSMITTING);
	}

	@Override
	public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof WeatherAlertGeneratorBlockEntity generator) {
			generator.refreshNow(serverLevel);
			player.sendSystemMessage(Component.translatable("block.radiocraft.weather_alert_generator").withStyle(ChatFormatting.BOLD));
			for(Component line : generator.statusLines())
				player.sendSystemMessage(line);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new WeatherAlertGeneratorBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
		return level.isClientSide || type != RadiocraftBlockEntities.WEATHER_ALERT_GENERATOR.get() ? null : WeatherAlertGeneratorBlockEntity::tick;
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.weather_alert_generator").withStyle(ChatFormatting.GRAY));
	}
}
