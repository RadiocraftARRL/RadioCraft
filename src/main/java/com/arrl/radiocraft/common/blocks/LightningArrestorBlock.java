package com.arrl.radiocraft.common.blocks;

import com.arrl.radiocraft.common.blockentities.LightningArrestorBlockEntity;
import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A lightning arrestor: an in-line coax block. The signal goes straight through it from any side to any other
 * ({@link com.arrl.radiocraft.common.be_networks.network_objects.LightningArrestorNetworkObject}). When lightning
 * strikes the antenna, the surge stops here. The coax between the antenna and the arrestor can still burn out, but
 * nothing beyond it is hurt: no radio blown, no coax burnt, no battery fire.
 */
public class LightningArrestorBlock extends AbstractNetworkBlock implements com.arrl.radiocraft.api.benetworks.IWireConnectable {

	/** A low box, the full width so coax meets its sides. Placeholder until the model bounty is drawn. */
	public static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);

	public LightningArrestorBlock(Properties properties) {
		super(properties);
	}

	/**
	 * Its ground lug: ground wire (green) from here to a ground rod grounds the arrestor, and through the coax shield
	 * every radio on its run (2026-11-08). It carries no power: the arrestor joins no power network.
	 */
	@Override
	public boolean acceptsPower(@NotNull BlockState state) {
		return true;
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
		return new LightningArrestorBlockEntity(pos, state);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.lightning_arrestor"));
	}
}
