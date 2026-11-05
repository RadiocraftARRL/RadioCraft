package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.common.blockentities.adsb.AdsbReceiverBlockEntity;
import com.arrl.radiocraft.common.blocks.adsb.AdsbReceiverBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * The ADS-B receiver's live scope on its display in the world (1.12 playtest), the way the radio computer's monitor
 * shows its screen: a square green display filling the block's front edge to edge (playtest fixes 2), with range
 * rings clipped at its edges, trails, targets and their tags, from the
 * pictures the receiver sends players within {@link AdsbReceiverBlockEntity#SCOPE_VIEW_RANGE} blocks
 * ({@link AdsbScopes}). Drawn only while the receiver is on and powered (its {@code LIT} state) and a picture has come
 * lately; otherwise the model's own tube shows (dark when off).
 */
public class AdsbScopeRenderer implements BlockEntityRenderer<AdsbReceiverBlockEntity> {

	// The display on the model's north side, model units: the "scope" element of adsb_receiver_screen.json
	// (tools/adsb_models.py), the whole face, edge to edge (1.12 playtest fixes 2), at z -0.02.
	public static final float SCREEN_X0 = 0.0F, SCREEN_X1 = 16.0F, SCREEN_Y0 = 0.0F, SCREEN_Y1 = 16.0F, SCREEN_Z = -0.02F;
	/** A little in front of the face, so it never fights the model. */
	private static final float LIFT = 0.03F;

	public AdsbScopeRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(AdsbReceiverBlockEntity receiver, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
		BlockState state = receiver.getBlockState();
		if(!(state.getBlock() instanceof AdsbReceiverBlock) || !state.getValue(AdsbReceiverBlock.LIT))
			return; // Off or without power: the dark tube of the model.
		ResourceLocation texture = AdsbScopes.texture(receiver.getBlockPos());
		if(texture == null)
			return;
		// Facing north, the viewer's left is east (+x): u runs from x1 to x0.
		float left = SCREEN_X1 / 16, right = SCREEN_X0 / 16, bottom = SCREEN_Y0 / 16, top = SCREEN_Y1 / 16;
		float z = (SCREEN_Z - LIFT) / 16;
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.0F, 0.5F); // Turned as the blockstate turns the model (north unturned).
		poseStack.mulPose(Axis.YP.rotationDegrees(-(state.getValue(AdsbReceiverBlock.FACING).toYRot() + 180.0F)));
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		Matrix4f pose = poseStack.last().pose();
		VertexConsumer buffer = buffers.getBuffer(RenderType.text(texture));
		// Counter-clockwise from the front; the texture's rows run top down.
		vertex(buffer, pose, left, bottom, z, 0.0F, 1.0F);
		vertex(buffer, pose, right, bottom, z, 1.0F, 1.0F);
		vertex(buffer, pose, right, top, z, 1.0F, 0.0F);
		vertex(buffer, pose, left, top, z, 0.0F, 0.0F);
		poseStack.popPose();
	}

	private static void vertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, float u, float v) {
		buffer.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
	}

	@Override
	public int getViewDistance() {
		return AdsbReceiverBlockEntity.SCOPE_VIEW_RANGE;
	}
}
