package com.arrl.radiocraft.common.blocks.mesh;

import com.arrl.radiocraft.common.blockentities.mesh.ScadaMasterBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The SCADA master (2026-11-11): a console two blocks wide, after a 1970s mainframe's. The control {@link Part#PANEL}
 * is where the player puts it and is the master itself (its block entity, its redstone faces, its coax and data
 * wire); its CRT {@link Part#SCREEN} stands on the player's left as they face the front, and only shows. Right-click
 * either half for the console's screen; break either and both go.
 */
public class ScadaMasterBlock extends ScadaBlock {

	public static final MapCodec<ScadaMasterBlock> CODEC = simpleCodec(ScadaMasterBlock::new);
	public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

	public enum Part implements StringRepresentable {
		PANEL, SCREEN;

		@Override
		public @NotNull String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	@SuppressWarnings("this-escape")
	public ScadaMasterBlock(Properties properties) {
		super(properties, true);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.PANEL));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(FACING, PART);
	}

	/** Where the other half is from {@code state}'s half: the CRT is on the viewer's left, the facing's clockwise side. */
	public static Direction toOther(BlockState state) {
		Direction facing = state.getValue(FACING);
		return state.getValue(PART) == Part.PANEL ? facing.getClockWise() : facing.getCounterClockWise();
	}

	/** The panel half (the master) of the console {@code state} at {@code pos} is part of. */
	public static BlockPos panelPos(BlockState state, BlockPos pos) {
		return state.getValue(PART) == Part.PANEL ? pos : pos.relative(toOther(state));
	}

	@Override
	public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
		Direction facing = context.getHorizontalDirection().getOpposite();
		BlockPos screen = context.getClickedPos().relative(facing.getClockWise());
		Level level = context.getLevel();
		if(!level.getWorldBorder().isWithinBounds(screen) || !level.getBlockState(screen).canBeReplaced(context))
			return null; // No room for the CRT.
		return defaultBlockState().setValue(FACING, facing).setValue(PART, Part.PANEL);
	}

	@Override
	public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if(!level.isClientSide && state.getValue(PART) == Part.PANEL)
			level.setBlock(pos.relative(toOther(state)), state.setValue(PART, Part.SCREEN), Block.UPDATE_ALL);
	}

	/** A half whose other half is gone goes too. */
	@Override
	protected @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighbour, @NotNull LevelAccessor level,
	                                          @NotNull BlockPos pos, @NotNull BlockPos neighbourPos) {
		if(direction == toOther(state) && !(neighbour.is(this) && neighbour.getValue(PART) != state.getValue(PART) && neighbour.getValue(FACING) == state.getValue(FACING)))
			return Blocks.AIR.defaultBlockState();
		return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
	}

	/** Breaking the CRT in creative must not drop the console: its drop comes from the panel only (see the loot table). */
	@Override
	public @NotNull BlockState playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
		if(!level.isClientSide && player.isCreative() && state.getValue(PART) == Part.SCREEN) {
			BlockPos panel = pos.relative(toOther(state));
			BlockState other = level.getBlockState(panel);
			if(other.is(this) && other.getValue(PART) == Part.PANEL)
				level.setBlock(panel, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	public boolean acceptsCoax(@NotNull BlockState state) {
		return state.getValue(PART) == Part.PANEL;
	}

	@Override
	public boolean acceptsData(@NotNull BlockState state) {
		return state.getValue(PART) == Part.PANEL;
	}

	@Override
	protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
	                                                   @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
		if(state.getValue(PART) == Part.SCREEN) { // The CRT passes a license or a rule book on to the panel.
			BlockPos panel = panelPos(state, pos);
			BlockState panelState = level.getBlockState(panel);
			return panelState.is(this) ? useItemOn(stack, panelState, level, panel, player, hand, hit.withPosition(panel)) : ItemInteractionResult.FAIL;
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	/** Right-click: the console's screen. Shift + right-click on the panel still sets a face, as on an RTU. */
	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(player.isSecondaryUseActive() && state.getValue(PART) == Part.PANEL)
			return super.useWithoutItem(state, level, pos, player, hit);
		BlockPos panel = panelPos(state, pos);
		if(player instanceof ServerPlayer serverPlayer && level.getBlockEntity(panel) instanceof ScadaMasterBlockEntity master)
			serverPlayer.openMenu(master, panel);
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected int getSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction direction) {
		return state.getValue(PART) == Part.PANEL ? super.getSignal(state, level, pos, direction) : 0;
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return state.getValue(PART) == Part.PANEL ? new ScadaMasterBlockEntity(pos, state) : null;
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
		return state.getValue(PART) == Part.PANEL ? super.getTicker(level, state, type) : null;
	}
}
