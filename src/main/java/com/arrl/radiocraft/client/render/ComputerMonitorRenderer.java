package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.MonitorMirror;
import com.arrl.radiocraft.common.blockentities.ComputerMonitorBlockEntity;
import com.arrl.radiocraft.common.blocks.ComputerMonitorBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The radio computer's monitor showing its screen (1.12 step 5): the text screen of this player's programs (see
 * {@link ComputerSession#monitorPicture}; kept live while they are near, so clocks and MUSIC move), lit by itself, stretched over the CRT's glass as a real monitor fills its
 * tube. Without one (another player's computer, or one left long ago) the glass shows the model's own DOS screen.
 */
public class ComputerMonitorRenderer implements BlockEntityRenderer<ComputerMonitorBlockEntity> {

	// The glass on the model's north face, in model units (see tools/radio_computer_models.py).
	private static final float GLASS_X0 = 3.75F, GLASS_X1 = 12.25F, GLASS_Y0 = 3.25F, GLASS_Y1 = 9.5F, GLASS_Z = 6.0F;
	/** A little in front of the glass, so it never fights the model's face. */
	private static final float LIFT = 0.03F;
	private static final float MARGIN = 0.15F;
	/** Plain white, tinted to the dark glass of a monitor without power. */
	private static final ResourceLocation DARK = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

	public ComputerMonitorRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(ComputerMonitorBlockEntity monitor, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
		if(monitor.getLevel() != null && monitor.getLevel().getBlockEntity(monitor.getBlockPos().below()) instanceof com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity computer
				&& !computer.hasPower()) { // No power: dark glass over the model's own screen (2026-11-15).
			glass(monitor, poseStack, buffers.getBuffer(RenderType.text(DARK)), 0.0F, 18, 20, 18);
			return;
		}
		// Near enough, the picture is kept live while the screen is closed (MonitorMirror redraws it every so often).
		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		boolean near = camera.distanceToSqr(monitor.getBlockPos().getCenter()) <= MonitorMirror.RANGE * MonitorMirror.RANGE;
		ResourceLocation picture = ComputerSession.monitorPicture(monitor.getBlockPos(), near);
		if(picture == null)
			return;
		glass(monitor, poseStack, buffers.getBuffer(RenderType.text(picture)), MARGIN, 255, 255, 255);
	}

	/** Fills the glass, {@code margin} in from its edges, with {@code buffer}'s texture tinted {@code r, g, b}. */
	private static void glass(ComputerMonitorBlockEntity monitor, PoseStack poseStack, VertexConsumer buffer, float margin, int r, int g, int b) {
		// Facing north, the viewer's left is east (+x).
		float left = (GLASS_X1 - margin) / 16, right = (GLASS_X0 + margin) / 16, bottom = (GLASS_Y0 + margin) / 16, top = (GLASS_Y1 - margin) / 16;
		float z = (GLASS_Z - LIFT) / 16;
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.0F, 0.5F); // Turned as the blockstate turns the model (north unturned).
		poseStack.mulPose(Axis.YP.rotationDegrees(-(monitor.getBlockState().getValue(ComputerMonitorBlock.FACING).toYRot() + 180.0F)));
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		Matrix4f pose = poseStack.last().pose();
		// Counter-clockwise from the front; the picture's rows run bottom up, as GL keeps them.
		vertex(buffer, pose, left, bottom, z, 0.0F, 0.0F, r, g, b);
		vertex(buffer, pose, right, bottom, z, 1.0F, 0.0F, r, g, b);
		vertex(buffer, pose, right, top, z, 1.0F, 1.0F, r, g, b);
		vertex(buffer, pose, left, top, z, 0.0F, 1.0F, r, g, b);
		poseStack.popPose();
	}

	private static void vertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, float u, float v, int r, int g, int b) {
		buffer.addVertex(pose, x, y, z).setColor(r, g, b, 255).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
	}
}
