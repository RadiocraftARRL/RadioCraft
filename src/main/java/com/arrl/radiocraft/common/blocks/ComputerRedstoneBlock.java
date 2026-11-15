package com.arrl.radiocraft.common.blocks;

import com.arrl.radiocraft.api.benetworks.IWireConnectable;
import com.arrl.radiocraft.common.blockentities.ComputerRedstoneBlockEntity;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Computer Redstone block (2026-11-13, doc/Plan-Computer4.md): placed touching a radio computer (or on its data
 * wire), up to {@link ComputerRedstoneBlockEntity#PER_COMPUTER} per computer. The REDSTONE program names it, names its
 * levels ("4 = Door Open") and sets it: on at a level, off, or a pulse; computers elsewhere on the LAN can too, if
 * they or their player are allowed. It gives that level out on every side, and while it is off it reads the redstone
 * coming in, which the program shows by the same names.
 */
public final class ComputerRedstoneBlock extends BaseEntityBlock implements IWireConnectable {

	public static final IntegerProperty POWER = BlockStateProperties.POWER;

	public ComputerRedstoneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(POWER, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWER);
	}

	@Override
	public boolean acceptsData(BlockState state) {
		return true;
	}

	@Override
	protected boolean isSignalSource(@NotNull BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction direction) {
		return state.getValue(POWER);
	}

	@Override
	protected int getDirectSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction direction) {
		return state.getValue(POWER);
	}

	/** Shows its name, level and what it reads. */
	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
		if(!level.isClientSide && level.getBlockEntity(pos) instanceof ComputerRedstoneBlockEntity be)
			player.displayClientMessage(Component.translatable("message.radiocraft.computer_redstone.status",
					be.getName().isEmpty() ? "-" : be.getName(), be.describe(state.getValue(POWER)), be.describe(be.getInput())), true);
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.computer_redstone"));
	}

	@Override
	protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return null;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new ComputerRedstoneBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
		return level.isClientSide ? null : createTickerHelper(type, RadiocraftBlockEntities.COMPUTER_REDSTONE.get(), ComputerRedstoneBlockEntity::tick);
	}
}
