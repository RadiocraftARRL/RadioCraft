package com.arrl.radiocraft.common.blocks.radios;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.RealWorldInterfaceBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Real-World Interface (1.12): a rig interface box that links the game's radios to a real radio on the server's
 * sound card and serial port. Off unless the server config turns it on and lists the interfaces; see
 * {@link RealWorldInterfaceBlockEntity}. Placeholder model (doc/ModelDesignBounty.md).
 */
public class RealWorldInterfaceBlock extends RadioBlock {

	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static {
		SHAPES.put(Direction.NORTH, Block.box(3.0D, 0.0D, 4.0D, 13.0D, 4.0D, 13.0D));
		SHAPES.put(Direction.SOUTH, Block.box(3.0D, 0.0D, 3.0D, 13.0D, 4.0D, 12.0D));
		SHAPES.put(Direction.WEST, Block.box(4.0D, 0.0D, 3.0D, 13.0D, 4.0D, 13.0D));
		SHAPES.put(Direction.EAST, Block.box(3.0D, 0.0D, 3.0D, 12.0D, 4.0D, 13.0D));
	}

	public RealWorldInterfaceBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return null;
	}

	@Override
	public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return hanging(state, SHAPES.get(state.getValue(RadioBlock.HORIZONTAL_FACING)));
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new RealWorldInterfaceBlockEntity(pos, state);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "real_world_interface")).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "real_world_interface.licence")).withStyle(ChatFormatting.RED));
	}
}
