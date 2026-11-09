package com.arrl.radiocraft.common.blocks;

import com.arrl.radiocraft.common.blockentities.GroundRodBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * A ground rod (maintainer, 2026-11-08): copper-clad steel, driven into the ground. Stack rods down a hole and fill the
 * soil back round them to go deeper; bond the top one to a radio or a lightning arrestor with ground wire (green
 * wire). How good a ground it makes depends on the soil, the biome and the wet ({@link com.arrl.radiocraft.common.radio.ground.GroundQuality});
 * use it with an empty hand to have it measured. Never required: a good ground lowers an HF station's noise, and lets
 * a lightning arrestor do its job.
 */
public class GroundRodBlock extends AbstractNetworkBlock implements com.arrl.radiocraft.api.benetworks.IWireConnectable {

	public static final VoxelShape SHAPE = Block.box(7.0D, 0.0D, 7.0D, 9.0D, 16.0D, 9.0D);

	public GroundRodBlock(Properties properties) {
		super(properties);
	}

	/** Ground wire is power wire, dyed green: it joins here. */
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
		return new GroundRodBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
		return level.isClientSide || type != RadiocraftBlockEntities.GROUND_ROD.get() ? null
				: (l, pos, s, be) -> GroundRodBlockEntity.serverTick(l, pos, (GroundRodBlockEntity)be);
	}

	/** Use it with an empty hand: how good a ground this rod (and the rods stacked with it) makes. */
	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(!level.isClientSide) {
			double quality = com.arrl.radiocraft.common.radio.ground.Grounding.quality(level, Set.of(pos));
			player.displayClientMessage(Component.translatable("message.radiocraft.ground_rod",
					Component.translatable("message.radiocraft.ground." + com.arrl.radiocraft.common.radio.ground.GroundQuality.grade(quality)),
					Math.round(quality * 100.0D)), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.ground_rod").withStyle(ChatFormatting.GRAY));
	}
}
