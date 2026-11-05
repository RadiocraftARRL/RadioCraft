package com.arrl.radiocraft.common.blocks.adsb;

import com.arrl.radiocraft.common.blockentities.adsb.AdsbBeaconBlockEntity;
import com.arrl.radiocraft.common.blocks.AbstractNetworkBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.radio.adsb.AdsbAirspace;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.Map;

/**
 * The ADS-B beacon (1.12 step 9): a 1090 MHz transponder for anything that flies, built for Sable sub-levels and
 * Create Aeronautics craft (see {@link AdsbBeaconBlockEntity}). It is placed with its front ({@link #FACING}) the way
 * the player looks, so on a craft it points at the nose: standing still, the craft reports that heading. Its lamp
 * ({@link #LIT}) is on while it transmits.
 */
public class AdsbBeaconBlock extends AbstractNetworkBlock {

	public static final MapCodec<AdsbBeaconBlock> CODEC = simpleCodec(AdsbBeaconBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty LIT = BooleanProperty.create("lit");
	private static final Map<Direction, VoxelShape> SHAPES = AdsbShapes.turned(new double[] { 3.0D, 0.0D, 2.0D, 13.0D, 5.0D, 14.0D });

	@SuppressWarnings("this-escape")
	public AdsbBeaconBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT);
	}

	/** Faces the way the player looks: on a craft, the nose. */
	@Override
	public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(!level.isClientSide && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof AdsbBeaconBlockEntity beacon) {
			serverPlayer.openMenu(beacon, pos);
			beacon.sendStatus(serverPlayer); // At once, so the screen does not open blank.
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean isMoving) {
		if(!level.isClientSide && !newState.is(this))
			AdsbAirspace.withdraw(level, pos);
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new AdsbBeaconBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
		return level.isClientSide || type != RadiocraftBlockEntities.ADSB_BEACON.get() ? null : AdsbBeaconBlockEntity::tick;
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.adsb_beacon").withStyle(ChatFormatting.GRAY));
	}
}
