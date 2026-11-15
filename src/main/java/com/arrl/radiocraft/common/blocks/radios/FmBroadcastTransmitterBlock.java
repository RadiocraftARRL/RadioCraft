package com.arrl.radiocraft.common.blocks.radios;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.FmBroadcastTransmitterBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The FM broadcast transmitter (2026-11-13): the bottom of a two-block cabinet, the radio itself
 * ({@link FmBroadcastTransmitterBlockEntity}). Placing it puts its top ({@link FmBroadcastTransmitterTopBlock}) above;
 * either goes when the other does. It stands on the floor only. {@link #ON_AIR} lights its lamps (and the top's).
 */
public class FmBroadcastTransmitterBlock extends RadioBlock {

	public static final BooleanProperty ON_AIR = BooleanProperty.create("on_air");
	private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

	public FmBroadcastTransmitterBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(ON_AIR, false));
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return simpleCodec(FmBroadcastTransmitterBlock::new);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(ON_AIR);
	}

	@Override
	public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Level level = context.getLevel();
		if(pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context))
			return null;
		BlockState state = super.getStateForPlacement(context);
		return state == null ? null : state.setValue(CEILING, false); // A floor-standing cabinet.
	}

	@Override
	public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if(!level.isClientSide && placer != null && level.getBlockEntity(pos) instanceof com.arrl.radiocraft.common.blockentities.radio.FmBroadcastTransmitterBlockEntity station)
			station.setOwner(placer.getUUID()); // Who may set its internet stream.
		level.setBlock(pos.above(), RadiocraftBlocks.FM_BROADCAST_TRANSMITTER_TOP.get().defaultBlockState()
				.setValue(FmBroadcastTransmitterTopBlock.FACING, state.getValue(HORIZONTAL_FACING)), Block.UPDATE_ALL);
	}

	/** Goes when its top does. */
	@Override
	protected @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighbour, @NotNull LevelAccessor level,
	                                          @NotNull BlockPos pos, @NotNull BlockPos neighbourPos) {
		if(direction == Direction.UP && !neighbour.is(RadiocraftBlocks.FM_BROADCAST_TRANSMITTER_TOP.get()))
			return Blocks.AIR.defaultBlockState();
		return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
	}

	/** ON AIR (2026-11-14): the cabinet gives a redstone signal while the station's mic is live, for an on-air sign. */
	@Override
	protected boolean isSignalSource(@NotNull BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction direction) {
		return level.getBlockEntity(pos) instanceof com.arrl.radiocraft.common.blockentities.radio.FmBroadcastTransmitterBlockEntity station ? station.onAirSignal() : 0;
	}

	/** Lights the cabinet (bottom and top) while it is on the air; server side, from the block entity. */
	public static void setOnAir(Level level, BlockPos pos, boolean onAir) {
		BlockState state = level.getBlockState(pos);
		if(state.hasProperty(ON_AIR) && state.getValue(ON_AIR) != onAir)
			level.setBlock(pos, state.setValue(ON_AIR, onAir), Block.UPDATE_CLIENTS);
		BlockState top = level.getBlockState(pos.above());
		if(top.hasProperty(FmBroadcastTransmitterTopBlock.ON_AIR) && top.getValue(FmBroadcastTransmitterTopBlock.ON_AIR) != onAir)
			level.setBlock(pos.above(), top.setValue(FmBroadcastTransmitterTopBlock.ON_AIR, onAir), Block.UPDATE_CLIENTS);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new FmBroadcastTransmitterBlockEntity(pos, state);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "fm_broadcast_transmitter")).withStyle(ChatFormatting.GRAY));
	}
}
