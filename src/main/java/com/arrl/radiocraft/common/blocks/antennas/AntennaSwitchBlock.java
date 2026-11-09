package com.arrl.radiocraft.common.blocks.antennas;

import com.arrl.radiocraft.common.be_networks.WireUtils;
import com.arrl.radiocraft.common.blockentities.AntennaSwitchBlockEntity;
import com.arrl.radiocraft.common.blocks.AbstractNetworkBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The antenna switch (a Workman CX-3 style coax switch): one radio, three antennas, one at a time. The input, the
 * brass connector, faces the radio's coax by itself (see {@link AntennaSwitchBlockEntity#faceTheRadio}); outputs 1, 2
 * and 3 are the other sides, clockwise from it. Right click turns the knob to the next output; a redstone signal
 * turns it as the signal rises, to the next output that has coax on it. Model and texture from
 * {@code tools/antenna_switch_model.py}.
 */
public class AntennaSwitchBlock extends AbstractNetworkBlock {

	/** The input side: where the radio's coax comes in. */
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** The output the knob is turned to: 1, 2 or 3, clockwise from the input. */
	public static final IntegerProperty OUTPUT = IntegerProperty.create("output", 1, 3);
	/** The 10x6x10 box; the connectors reach the block's sides at floor level. */
	public static final VoxelShape SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 6.0D, 13.0D);

	public AntennaSwitchBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OUTPUT, 1));
	}

	/** The side the radio's coax comes in on. */
	public static Direction input(BlockState state) {
		return state.getValue(FACING);
	}

	/** The side of the selected output: {@link #OUTPUT} quarter turns clockwise from the input. */
	public static Direction output(BlockState state) {
		Direction side = state.getValue(FACING);
		for(int i = 0; i < state.getValue(OUTPUT); i++)
			side = side.getClockWise();
		return side;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OUTPUT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction toward = context.getHorizontalDirection().getOpposite(); // Input toward the player, until a radio is found.
		return defaultBlockState().setValue(FACING, AntennaSwitchBlockEntity.radioSide(context.getLevel(), context.getClickedPos(), toward));
	}

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(level instanceof ServerLevel serverLevel) {
			int output = state.getValue(OUTPUT) % 3 + 1;
			turnTo(serverLevel, pos, state, output);
			player.displayClientMessage(Component.translatable("message.radiocraft.antenna_switch", output), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos fromPos, boolean isMoving) {
		super.neighborChanged(state, level, pos, block, fromPos, isMoving);
		if(!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof AntennaSwitchBlockEntity entity))
			return;
		int signal = level.getBestNeighborSignal(pos);
		boolean rising = signal > 0 && entity.lastSignal() == 0;
		entity.setLastSignal(signal);
		if(rising) { // A redstone signal turns it once, to the next output with coax on it (2026-11-09).
			int next = nextWiredOutput(level, pos, state);
			if(next != state.getValue(OUTPUT))
				turnTo(serverLevel, pos, state, next);
		}
		entity.faceTheRadio(serverLevel); // Coax laid up to it: turn to the radio straight away.
	}

	/** Placed beside a signal that is already on: that is not a rising edge, so it does not turn on the next update. */
	@Override
	public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if(!level.isClientSide && level.getBlockEntity(pos) instanceof AntennaSwitchBlockEntity entity)
			entity.setLastSignal(level.getBestNeighborSignal(pos));
	}

	/**
	 * The output after the selected one, going round 1, 2, 3, that has coax linked to it; outputs with no coax are
	 * skipped. The selected output if no other has coax.
	 */
	public static int nextWiredOutput(Level level, BlockPos pos, BlockState state) {
		Set<Direction> wired = new HashSet<>(WireUtils.wireLinksOf(level, pos, Set.of(WireUtils.coaxWires())).values());
		int current = state.getValue(OUTPUT);
		for(int step = 1; step < 3; step++) {
			int candidate = (current - 1 + step) % 3 + 1;
			if(wired.contains(output(state.setValue(OUTPUT, candidate))))
				return candidate;
		}
		return current;
	}

	/** Turns the knob to {@code output}, with a click, and sorts out which antenna the radio now has. */
	public static void turnTo(ServerLevel level, BlockPos pos, BlockState state, int output) {
		level.setBlock(pos, state.setValue(OUTPUT, output), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 1.4F);
		AntennaSwitchBlockEntity.rewire(level, pos);
	}

	@Override
	public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return null;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new AntennaSwitchBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
		return level.isClientSide ? null : createTickerHelper(type, RadiocraftBlockEntities.ANTENNA_SWITCH.get(), AntennaSwitchBlockEntity::tick);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.antenna_switch"));
	}
}
