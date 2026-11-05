package com.arrl.radiocraft.common.blocks.adsb;

import com.arrl.radiocraft.common.blockentities.adsb.AdsbAntennaBlockEntity;
import com.arrl.radiocraft.common.blocks.AbstractNetworkBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The ADS-B antenna (1.12 step 9): a short 1090 MHz vertical (a quarter wave is under 7 cm) on a ground plane, for an
 * ADS-B receiver. Coax it to the receiver, or set it straight on top of one. How far the receiver hears depends on how
 * high the aircraft flies ({@link com.arrl.radiocraft.common.radio.adsb.AdsbMath#range}).
 */
public class AdsbAntennaBlock extends AbstractNetworkBlock {

	public static final MapCodec<AdsbAntennaBlock> CODEC = simpleCodec(AdsbAntennaBlock::new);
	private static final VoxelShape SHAPE = Shapes.or(Block.box(4.0D, 0.0D, 4.0D, 12.0D, 2.0D, 12.0D), Block.box(7.0D, 2.0D, 7.0D, 9.0D, 14.0D, 9.0D));

	public AdsbAntennaBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new AdsbAntennaBlockEntity(pos, state);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.adsb_antenna").withStyle(ChatFormatting.GRAY));
	}
}
