package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.common.blockentities.AntennaBlockEntity;
import com.arrl.radiocraft.common.blocks.antennas.DoubleVHFAntennaBlock;
import com.arrl.radiocraft.common.blocks.antennas.YagiAntennaBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;

/**
 * A yagi aimed between the four directions (2026-11-12, {@link YagiAntennaBlock#TURN}): its blockstate shows nothing,
 * and this draws the ordinary model of the nearest direction, both halves, turned about the mast by the 22.5 or 45
 * degrees left over. Drawn from the lower half's block entity (antennas share {@link AntennaBlockEntity}; anything
 * else is skipped).
 */
public class YagiAntennaRenderer implements BlockEntityRenderer<AntennaBlockEntity> {

	/** The mast in the model as drawn (beam towards -X): 5 units east of the block's centre. */
	private static final double MAST_OFFSET = 5.0D / 16.0D;

	public YagiAntennaRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(@NotNull AntennaBlockEntity antenna, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int light, int overlay) {
		BlockState state = antenna.getBlockState();
		if(!(state.getBlock() instanceof YagiAntennaBlock) || state.getValue(DoubleVHFAntennaBlock.HALF) != DoubleBlockHalf.LOWER)
			return;
		int turn = state.getValue(YagiAntennaBlock.TURN);
		if(turn == YagiAntennaBlock.STRAIGHT || antenna.getLevel() == null)
			return;
		Direction facing = state.getValue(DoubleVHFAntennaBlock.FACING);
		BlockState straight = state.setValue(YagiAntennaBlock.TURN, YagiAntennaBlock.STRAIGHT);
		BlockState upper = straight.setValue(DoubleVHFAntennaBlock.HALF, DoubleBlockHalf.UPPER);
		// The mast sits opposite the beam: turn about it, so the mast stays where the block's shape is.
		Direction back = facing.getOpposite();
		double px = 0.5D + back.getStepX() * MAST_OFFSET, pz = 0.5D + back.getStepZ() * MAST_OFFSET;
		float degrees = (turn - YagiAntennaBlock.STRAIGHT) * 22.5F; // Clockwise from above, as the compass turns.
		poseStack.pushPose();
		poseStack.rotateAround(Axis.YP.rotationDegrees(-degrees), (float)px, 0.0F, (float)pz);
		BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
		draw(dispatcher, poseStack, buffer, straight, light, overlay);
		poseStack.translate(0.0D, 1.0D, 0.0D);
		BlockPos above = antenna.getBlockPos().above();
		draw(dispatcher, poseStack, buffer, upper, LevelRenderer.getLightColor(antenna.getLevel(), above), overlay);
		poseStack.popPose();
	}

	private static void draw(BlockRenderDispatcher dispatcher, PoseStack poseStack, MultiBufferSource buffer, BlockState state, int light, int overlay) {
		BakedModel model = dispatcher.getBlockModel(state);
		for(RenderType type : model.getRenderTypes(state, net.minecraft.util.RandomSource.create(42L), ModelData.EMPTY))
			dispatcher.getModelRenderer().renderModel(poseStack.last(), buffer.getBuffer(type == RenderType.cutout() ? RenderType.cutout() : type), state, model,
					1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, type);
	}

	@Override
	public @NotNull AABB getRenderBoundingBox(@NotNull AntennaBlockEntity antenna) {
		return new AABB(antenna.getBlockPos()).inflate(2.0D).expandTowards(0.0D, 2.0D, 0.0D);
	}
}
