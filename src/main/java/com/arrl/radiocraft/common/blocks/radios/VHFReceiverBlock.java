package com.arrl.radiocraft.common.blocks.radios;

import com.arrl.radiocraft.common.blockentities.radio.VHFReceiverBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
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

import java.util.HashMap;
import java.util.List;

/**
 * The VHF receiver (see {@link VHFReceiverBlockEntity}). Shift + right-click with a Small NiCd or Small Alkaline Battery
 * puts the cell in (VHFReceiverEvents, since vanilla skips the block for a sneaking player holding something), with an
 * empty hand takes it out (2026-11-09). Broken, it drops the cell.
 */
public class VHFReceiverBlock extends RadioBlock {

	public static final HashMap<Direction, VoxelShape> SHAPES = new HashMap<>();

	static {
		SHAPES.put(Direction.NORTH, Block.box(4.0D, 0.0D, 3.0D, 12.0D, 3.0D, 16.0D));
		SHAPES.put(Direction.SOUTH, Block.box(4.0D, 0.0D, 0.0D, 12.0D, 3.0D, 13.0D));
		SHAPES.put(Direction.WEST, Block.box(3.0D, 0.0D, 4.0D, 16.0D, 3.0D, 12.0D));
		SHAPES.put(Direction.EAST, Block.box(0.0D, 0.0D, 4.0D, 13.0D, 3.0D, 12.0D));
	}

	public VHFReceiverBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return null;
	}

	@Override
	public @NotNull VoxelShape getShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return hanging(state, SHAPES.get(state.getValue(RadioBlock.HORIZONTAL_FACING)));
	}

	/** Shift + right-click with a cell: it goes in, and the old one comes back. Server side; the caller cancels the click. */
	public static void swapCell(Level level, BlockPos pos, Player player, ItemStack stack) {
		if(level.isClientSide || !(level.getBlockEntity(pos) instanceof VHFReceiverBlockEntity receiver))
			return;
		ItemStack old = receiver.swapCell(stack);
		stack.shrink(1);
		if(!old.isEmpty() && !player.getInventory().add(old))
			player.drop(old, false);
		level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.4F);
		player.displayClientMessage(Component.translatable("message.radiocraft.vhf_receiver.cell_in"), true);
	}

	/** Shift + right-click with an empty hand takes the cell out; without one the screen opens as usual. */
	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
		if(player.isSecondaryUseActive() && level.getBlockEntity(pos) instanceof VHFReceiverBlockEntity receiver && receiver.hasCell()) {
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
		if(!level.isClientSide && newState.getBlock() != this && level.getBlockEntity(pos) instanceof VHFReceiverBlockEntity receiver && receiver.hasCell())
			Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, receiver.swapCell(ItemStack.EMPTY));
		super.onRemove(state, level, pos, newState, isMoving);
	}

    @Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new VHFReceiverBlockEntity(pos, state);
	}

    @Override
    public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable("tooltip.radiocraft.vhf_receiver"));
        tooltipComponents.add(Component.translatable("tooltip.radiocraft.vhf_receiver.cell").withStyle(ChatFormatting.GRAY));
    }
}
