package com.arrl.radiocraft.common.blocks.power;

import com.arrl.radiocraft.api.benetworks.IWireConnectable;
import com.arrl.radiocraft.common.blockentities.PowerSwitchBlockEntity;
import com.arrl.radiocraft.common.blocks.AbstractNetworkBlock;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A power switch on the power wire (maintainer, 2026-11-09): a lever on a box that connects some of its terminals and
 * not others, so a player or redstone can switch circuits. Right click throws the lever; so does a redstone signal,
 * once, as it rises. The terminals it connects are {@link #passes}; the network walk passes through the switch between
 * those only ({@link com.arrl.radiocraft.common.be_networks.network_objects.PowerSwitchNetworkObject}). The two kinds
 * are {@link OnOffPowerSwitchBlock} and {@link LeftRightPowerSwitchBlock}. Placeholder models (ModelDesignBounty).
 */
public abstract class PowerSwitchBlock extends AbstractNetworkBlock implements IWireConnectable {

	/** The terminal the switch is built round: one of the On/Off pair, the Left/Right's input. Toward the placer. */
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** A box half a block high, the full width, so wire meets its sides. */
	public static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);

	protected PowerSwitchBlock(Properties properties) {
		super(properties);
	}

	/** True if the lever, as it stands in {@code state}, connects the terminal on {@code side} to the others. */
	public abstract boolean passes(BlockState state, Direction side);

	/** {@code state} with the lever thrown the other way. */
	protected abstract BlockState thrown(BlockState state);

	/** True if the lever in {@code state} is in its second position (on, or right): the click sounds higher. */
	protected abstract boolean isThrown(BlockState state);

	/** The message shown to the player who throws it: where the lever is now. */
	protected abstract Component positionMessage(BlockState state);

	/** The tooltip's translation key. */
	protected abstract String tooltipKey();

	/** Throws the lever of the switch at {@code pos}, with a click, and sorts out which circuits are joined now. */
	public static void toggle(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if(!(state.getBlock() instanceof PowerSwitchBlock block))
			return;
		BlockState thrown = block.thrown(state);
		level.setBlock(pos, thrown, Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, block.isThrown(thrown) ? 0.6F : 0.5F);
		PowerSwitchBlockEntity.rewire(level, pos);
	}

	@Override
	public boolean acceptsPower(BlockState state) {
		return true; // In code as well as the power_blocks tag, so it connects whatever the tags say.
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(level instanceof ServerLevel serverLevel) {
			toggle(serverLevel, pos);
			player.displayClientMessage(positionMessage(level.getBlockState(pos)), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** A redstone signal throws the lever once, as it rises; it stays where it is while the signal stays on. */
	@Override
	protected void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos fromPos, boolean isMoving) {
		super.neighborChanged(state, level, pos, block, fromPos, isMoving);
		if(!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof PowerSwitchBlockEntity entity))
			return;
		int signal = level.getBestNeighborSignal(pos);
		boolean rising = signal > 0 && entity.lastSignal() == 0;
		entity.setLastSignal(signal);
		if(rising)
			toggle(serverLevel, pos);
	}

	/** Placed beside a signal that is already on: that is not a rising edge, so it does not throw on the next update. */
	@Override
	public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if(!level.isClientSide && level.getBlockEntity(pos) instanceof PowerSwitchBlockEntity entity)
			entity.setLastSignal(level.getBestNeighborSignal(pos));
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
		return new PowerSwitchBlockEntity(pos, state);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable(tooltipKey()));
	}
}
