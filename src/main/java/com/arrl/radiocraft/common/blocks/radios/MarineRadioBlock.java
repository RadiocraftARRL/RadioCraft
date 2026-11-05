package com.arrl.radiocraft.common.blocks.radios;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.MarineRadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.radio.MarineChannels;
import com.arrl.radiocraft.common.radio.marine.MarineRadioState;
import com.arrl.radiocraft.common.radio.marine.MarineRadios;
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
 * The fixed-mount VHF marine radio: a white box for a boat's console or a harbour office, with its hand mic on a cord
 * (sneak and use). It works on a Sable ship like any radio block. Channel-only, 25 W / 1 W, the 16/9 key and dual
 * watch; the block entity does the radio ({@link MarineRadioBlockEntity}).
 */
public class MarineRadioBlock extends RadioBlock {

	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static {
		SHAPES.put(Direction.NORTH, Block.box(2.0D, 0.0D, 4.0D, 14.0D, 5.0D, 15.0D));
		SHAPES.put(Direction.SOUTH, Block.box(2.0D, 0.0D, 1.0D, 14.0D, 5.0D, 12.0D));
		SHAPES.put(Direction.WEST, Block.box(4.0D, 0.0D, 2.0D, 15.0D, 5.0D, 14.0D));
		SHAPES.put(Direction.EAST, Block.box(1.0D, 0.0D, 2.0D, 12.0D, 5.0D, 14.0D));
	}

	public MarineRadioBlock(Properties properties) {
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
		return new MarineRadioBlockEntity(pos, state);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		MarineRadioState state = stack.get(RadiocraftDataComponent.MARINE_RADIO.value());
		if(state == null)
			state = MarineRadioState.DEFAULT;
		MarineChannels.Region region = MarineRadios.region();
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "marine.channel"), state.displayLine(region), state.channel(region).use())
				.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", state.transmitsHigh(region) ? "marine.high_base" : "marine.low"))
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
