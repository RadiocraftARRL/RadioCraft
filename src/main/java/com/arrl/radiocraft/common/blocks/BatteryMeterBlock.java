package com.arrl.radiocraft.common.blocks;

import com.arrl.radiocraft.api.capabilities.IBENetworks;
import com.arrl.radiocraft.common.be_networks.BatteryMeterReading;
import com.arrl.radiocraft.common.be_networks.network_objects.BatteryMeterNetworkObject;
import com.arrl.radiocraft.common.blockentities.BatteryMeterBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A battery meter: put it on a power wire and its face shows the batteries on that wire, the voltage (as a 12 V bank)
 * and the charge, with an arrow while they charge or discharge. Power passes through it like a wire junction, from
 * any side. A comparator reads the charge (0 empty, 15 full), and using it prints the reading in chat with the time
 * left to empty or to full.
 */
public class BatteryMeterBlock extends AbstractNetworkBlock {

	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	/** A box half a block high, the full width so wire meets its sides. Placeholder until the model bounty is drawn. */
	public static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);

	public BatteryMeterBlock(Properties properties) {
		super(properties); // FACING defaults to north, its first value.
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
		if(level.isClientSide)
			return InteractionResult.SUCCESS;
		if(IBENetworks.getObject(level, pos) instanceof BatteryMeterNetworkObject meter)
			player.displayClientMessage(describe(meter.getReading()), false);
		return InteractionResult.CONSUME;
	}

	/** The reading as one chat line. */
	public static Component describe(BatteryMeterReading reading) {
		if(!reading.hasBatteries())
			return Component.translatable("message.radiocraft.battery_meter.none");
		String volts = String.format("%.1f", reading.volts());
		Component trend = switch(reading.trend()) {
			case CHARGING -> Component.translatable("message.radiocraft.battery_meter.charging", String.format("%.1f", reading.flow()), time(reading.ticksToGo()));
			case DISCHARGING -> Component.translatable("message.radiocraft.battery_meter.discharging", String.format("%.1f", -reading.flow()), time(reading.ticksToGo()));
			case REST -> Component.translatable("message.radiocraft.battery_meter.rest");
		};
		return Component.translatable("message.radiocraft.battery_meter", volts, reading.percent(), reading.batteries(), trend);
	}

	/** Ticks as game time to read: "3 min", "1 h 20 min", "under a minute". */
	private static Component time(long ticks) {
		if(ticks < 0)
			return Component.literal("?");
		long minutes = ticks / 1200;
		if(minutes < 1)
			return Component.translatable("message.radiocraft.battery_meter.under_a_minute");
		if(minutes < 60)
			return Component.translatable("message.radiocraft.battery_meter.minutes", minutes);
		return Component.translatable("message.radiocraft.battery_meter.hours", minutes / 60, minutes % 60);
	}

	@Override
	protected boolean hasAnalogOutputSignal(@NotNull BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(@NotNull BlockState state, Level level, @NotNull BlockPos pos) {
		return level.getBlockEntity(pos) instanceof BatteryMeterBlockEntity be ? be.getComparator() : 0;
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.battery_meter"));
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return null;
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
		return level.isClientSide ? null : createTickerHelper(type, RadiocraftBlockEntities.BATTERY_METER.get(), BatteryMeterBlockEntity::tick);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new BatteryMeterBlockEntity(pos, state);
	}
}
