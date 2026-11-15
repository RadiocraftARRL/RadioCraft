package com.arrl.radiocraft.common.blocks.radios;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.AirbandRadioBlockEntity;
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

/** The airband radio (2026-11-13): a vintage panel-mount aviation COM. See {@link AirbandRadioBlockEntity}. */
public class AirbandRadioBlock extends RadioBlock {

	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static { // Drawn facing north (tools/airband_fm_models.py): 15 wide, 5 high, 2.5 to 15 deep.
		SHAPES.put(Direction.NORTH, Block.box(0.5D, 0.0D, 2.0D, 15.5D, 5.0D, 15.0D));
		SHAPES.put(Direction.SOUTH, Block.box(0.5D, 0.0D, 1.0D, 15.5D, 5.0D, 14.0D));
		SHAPES.put(Direction.WEST, Block.box(2.0D, 0.0D, 0.5D, 15.0D, 5.0D, 15.5D));
		SHAPES.put(Direction.EAST, Block.box(1.0D, 0.0D, 0.5D, 14.0D, 5.0D, 15.5D));
	}

	public AirbandRadioBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return simpleCodec(AirbandRadioBlock::new);
	}

	@Override
	public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return hanging(state, SHAPES.get(state.getValue(HORIZONTAL_FACING)));
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new AirbandRadioBlockEntity(pos, state);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "airband_radio")).withStyle(ChatFormatting.GRAY));
	}
}
