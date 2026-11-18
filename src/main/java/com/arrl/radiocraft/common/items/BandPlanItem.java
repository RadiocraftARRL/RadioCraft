package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.common.blocks.BandPlanWallBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.NotNull;

/**
 * The Band Plan sheet: right click to read it. Every band (the ham bands and the commercial ones) is a bar coloured by
 * what each part is for, with markers for frequencies worth knowing and a legend. What it shows comes from the server
 * config ({@link com.arrl.radiocraft.common.radio.bandplan.BandPlan}), so the sheet is the same for everyone on a server.
 * Its tooltip line comes from {@code tooltip.radiocraft.band_plan} (ItemTooltipEvents). Used on the side of a block it
 * hangs there ({@link BandPlanWallBlock}, 2026-11-18), to read from the wall.
 */
public class BandPlanItem extends Item {

	public BandPlanItem(Properties properties) {
		super(properties);
	}

	@Override
	public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if(level.isClientSide)
			com.arrl.radiocraft.client.screens.BandPlanScreen.open(); // A separate class, so the server never loads it.
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
		Direction face = context.getClickedFace();
		if(!face.getAxis().isHorizontal())
			return InteractionResult.PASS; // On a floor or ceiling: read it instead (use()).
		BlockPlaceContext place = new BlockPlaceContext(context);
		BlockPos pos = place.getClickedPos();
		Level level = context.getLevel();
		BlockState state = RadiocraftBlocks.BAND_PLAN_WALL.get().defaultBlockState().setValue(BandPlanWallBlock.FACING, face);
		if(!place.canPlace() || !BandPlanWallBlock.canHang(level, pos, face) || !level.isUnobstructed(state, pos, net.minecraft.world.phys.shapes.CollisionContext.empty()))
			return InteractionResult.PASS;
		if(!level.isClientSide) {
			level.setBlock(pos, state, 3);
			level.playSound(null, pos, SoundEvents.PAINTING_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
			level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(context.getPlayer(), state));
			if(context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild)
				context.getItemInHand().shrink(1);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}
}
