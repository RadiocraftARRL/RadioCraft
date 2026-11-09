package com.arrl.radiocraft.common.blocks.radios;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.CbRadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.radio.cb.CbMobileModel;
import com.arrl.radiocraft.common.radio.cb.CbRadioState;
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
 * A CB mobile (2026-11-08): the 5875 or the PC68 ({@link CbMobileModel}). Its hand mic comes off on a cord (sneak and
 * use), as on the marine radio. The block entity does the radio ({@link CbRadioBlockEntity}).
 */
public class CbRadioBlock extends RadioBlock {

	private final CbMobileModel model;
	private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

	public CbRadioBlock(Properties properties, CbMobileModel model) {
		super(properties);
		this.model = model;
		// Drawn facing north: front at low z. Box from x1..x2, height h, z1..z2 (see tools/cb_radio_assets.py).
		double x1 = model.deluxe() ? 1.0D : 2.5D, x2 = 16.0D - x1, h = model.deluxe() ? 6.0D : 4.0D, z1 = model.deluxe() ? 3.0D : 4.0D, z2 = 13.0D;
		shapes.put(Direction.NORTH, Block.box(x1, 0.0D, z1, x2, h, z2));
		shapes.put(Direction.SOUTH, Block.box(x1, 0.0D, 16.0D - z2, x2, h, 16.0D - z1));
		shapes.put(Direction.WEST, Block.box(z1, 0.0D, x1, z2, h, x2));
		shapes.put(Direction.EAST, Block.box(16.0D - z2, 0.0D, x1, 16.0D - z1, h, x2));
	}

	public CbMobileModel getModel() {
		return model;
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return null;
	}

	@Override
	public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return hanging(state, shapes.get(state.getValue(RadioBlock.HORIZONTAL_FACING)));
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new CbRadioBlockEntity(pos, state);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		CbRadioState state = stack.get(RadiocraftDataComponent.CB_RADIO.value());
		if(state == null)
			state = CbRadioState.DEFAULT;
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "cb.channel"), state.channel(), state.displayStatus()).withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "cb.no_licence")).withStyle(ChatFormatting.GREEN));
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", model.deluxe() ? "cb.5875" : "cb.pc68")).withStyle(ChatFormatting.GRAY));
	}
}
