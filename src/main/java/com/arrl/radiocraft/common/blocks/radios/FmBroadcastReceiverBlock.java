package com.arrl.radiocraft.common.blocks.radios;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.FmBroadcastReceiverBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The FM broadcast receiver (2026-11-13): a walnut table radio. See {@link FmBroadcastReceiverBlockEntity}. Shift +
 * right-click with a Small Alkaline or NiCd Battery puts one in ({@link com.arrl.radiocraft.common.events.VHFReceiverEvents}),
 * with an empty hand takes it out; broken, it drops its cell.
 */
public class FmBroadcastReceiverBlock extends RadioBlock {

	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static { // Drawn facing north: 14 wide, 8 high, 4.5 to 11.5 deep (the knobs stand a little proud of it).
		SHAPES.put(Direction.NORTH, Block.box(1.0D, 0.0D, 4.5D, 15.0D, 8.0D, 11.5D));
		SHAPES.put(Direction.SOUTH, Block.box(1.0D, 0.0D, 4.5D, 15.0D, 8.0D, 11.5D));
		SHAPES.put(Direction.WEST, Block.box(4.5D, 0.0D, 1.0D, 11.5D, 8.0D, 15.0D));
		SHAPES.put(Direction.EAST, Block.box(4.5D, 0.0D, 1.0D, 11.5D, 8.0D, 15.0D));
	}

	public FmBroadcastReceiverBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return simpleCodec(FmBroadcastReceiverBlock::new);
	}

	@Override
	public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return hanging(state, SHAPES.get(state.getValue(HORIZONTAL_FACING)));
	}

	/** Shift + right-click with a cell: puts it in and hands back the one that was there. */
	public static void swapCell(Level level, BlockPos pos, Player player, ItemStack stack) {
		if(level.isClientSide || !(level.getBlockEntity(pos) instanceof FmBroadcastReceiverBlockEntity receiver))
			return;
		ItemStack old = receiver.swapCell(stack);
		stack.shrink(1);
		if(!old.isEmpty() && !player.getInventory().add(old))
			player.drop(old, false);
		level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.4F);
		player.displayClientMessage(Component.translatable("message.radiocraft.vhf_receiver.cell_in"), true);
	}

	/** Shift + right-click with an empty hand takes the cell out; otherwise the screen opens as usual. */
	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
		if(player.isSecondaryUseActive() && level.getBlockEntity(pos) instanceof FmBroadcastReceiverBlockEntity receiver && receiver.hasCell()) {
			if(!level.isClientSide) {
				ItemStack old = receiver.swapCell(ItemStack.EMPTY);
				if(!player.getInventory().add(old))
					player.drop(old, false);
				level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6F, 1.4F);
				player.displayClientMessage(Component.translatable("message.radiocraft.vhf_receiver.cell_out"), true);
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		return super.useWithoutItem(state, level, pos, player, hitResult);
	}

	@Override
	public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean isMoving) {
		if(!level.isClientSide && newState.getBlock() != this && level.getBlockEntity(pos) instanceof FmBroadcastReceiverBlockEntity receiver && receiver.hasCell())
			Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, receiver.swapCell(ItemStack.EMPTY));
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new FmBroadcastReceiverBlockEntity(pos, state);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "fm_broadcast_receiver")).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.radiocraft.fm_broadcast_receiver.cell").withStyle(ChatFormatting.GRAY));
	}
}
