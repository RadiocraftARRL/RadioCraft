package com.arrl.radiocraft.common.blocks;

import com.arrl.radiocraft.common.blockentities.WeatherDisplayBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.RenderShape;
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

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Local Weather Station Display (2026-11-18, see {@link WeatherDisplayBlockEntity}): a desk display in a wood case
 * whose red LCD steps through the nearest Local Weather Station's readings. Right click for all of them at once.
 */
public final class WeatherDisplayBlock extends BaseEntityBlock {

	public static final MapCodec<WeatherDisplayBlock> CODEC = simpleCodec(WeatherDisplayBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static {
		SHAPES.put(Direction.NORTH, Block.box(2.0D, 0.0D, 5.5D, 14.0D, 9.0D, 11.0D));
		SHAPES.put(Direction.SOUTH, Block.box(2.0D, 0.0D, 5.0D, 14.0D, 9.0D, 10.5D));
		SHAPES.put(Direction.EAST, Block.box(5.0D, 0.0D, 2.0D, 10.5D, 9.0D, 14.0D));
		SHAPES.put(Direction.WEST, Block.box(5.5D, 0.0D, 2.0D, 11.0D, 9.0D, 14.0D));
	}

	public WeatherDisplayBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
		if(!level.isClientSide && level.getBlockEntity(pos) instanceof WeatherDisplayBlockEntity display) {
			int[] r = display.getReadings();
			if(r == null)
				player.displayClientMessage(Component.translatable("message.radiocraft.weather_display.no_station"), true);
			else
				player.displayClientMessage(Component.translatable("message.radiocraft.weather_display.readings", r[WeatherDisplayBlockEntity.TEMP],
						r[WeatherDisplayBlockEntity.HUMIDITY], String.format(Locale.ROOT, "%.2f", r[WeatherDisplayBlockEntity.PRESSURE] / 100.0D),
						compass(r[WeatherDisplayBlockEntity.WIND_FROM]), r[WeatherDisplayBlockEntity.WIND], r[WeatherDisplayBlockEntity.GUST],
						String.format(Locale.ROOT, "%.2f", r[WeatherDisplayBlockEntity.RAIN] / 100.0D)), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** N, NNE, NE... for a bearing in degrees. */
	public static String compass(int degrees) {
		String[] points = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
		return points[(int)Math.round(Math.floorMod(degrees, 360) / 22.5D) % 16];
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.weather_display"));
	}

	@Override
	public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new WeatherDisplayBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
		return level.isClientSide ? null : createTickerHelper(type, RadiocraftBlockEntities.WEATHER_DISPLAY.get(), WeatherDisplayBlockEntity::tick);
	}
}
