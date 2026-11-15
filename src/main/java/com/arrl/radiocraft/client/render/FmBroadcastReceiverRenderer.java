package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.RadiocraftClientConfig;
import com.arrl.radiocraft.common.blockentities.radio.FmBroadcastRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.FmBroadcastReceiverBlockEntity;
import com.arrl.radiocraft.common.blocks.radios.RadioBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The FM table radio's dial pointer on the block (2026-11-13): an orange line across the {@code DIAL} glass at the
 * frequency it is tuned to, 88 MHz at the bottom and 108 at the top as the screen's scale. It is a string-driven
 * pointer, so it shows whether the set is on or off: lit while on, dull while off.
 */
public class FmBroadcastReceiverRenderer implements BlockEntityRenderer<FmBroadcastReceiverBlockEntity> {

	/** The scale's ends inside the glass, as a share of its height from the bottom (the texture's ticks). */
	private static final float SCALE_BOTTOM = 1.0F / 20.0F, SCALE_TOP = 19.0F / 20.0F;

	public FmBroadcastReceiverRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	public void render(@NotNull FmBroadcastReceiverBlockEntity radio, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
		BlockState state = radio.getBlockState();
		if(radio.getLevel() == null || !state.hasProperty(RadioBlock.HORIZONTAL_FACING) || !RadiocraftClientConfig.SHOW_RADIO_SCREENS.get())
			return;
		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		if(camera.distanceToSqr(radio.getBlockPos().getCenter()) > RadioDisplayRenderer.VIEW_DISTANCE * RadioDisplayRenderer.VIEW_DISTANCE)
			return;
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		List<RadioDisplays.Display> dials = RadioDisplays.named(path, "DIAL");
		if(dials.isEmpty())
			return;
		RadioDisplays.Display dial = dials.get(0);
		float t = (radio.getFrequency() - FmBroadcastRadioBlockEntity.LOW_HZ) / (FmBroadcastRadioBlockEntity.HIGH_HZ - FmBroadcastRadioBlockEntity.LOW_HZ);
		t = Math.max(0.0F, Math.min(1.0F, t));
		float width = (dial.x2() - dial.x1()) / 16.0F, height = (dial.y2() - dial.y1()) / 16.0F;
		float y = -height + (SCALE_BOTTOM + t * (SCALE_TOP - SCALE_BOTTOM)) * height; // From the glass's top, down negative.
		float thick = height / 40.0F;
		boolean on = radio.wasPowered();

		poseStack.pushPose();
		poseStack.translate(0.5D, 0.5D, 0.5D);
		poseStack.mulPose(Axis.YP.rotationDegrees(-((state.getValue(RadioBlock.HORIZONTAL_FACING).toYRot() + 180.0F) % 360.0F)));
		poseStack.translate(-0.5D, -0.5D, -0.5D);
		if(state.hasProperty(RadioBlock.CEILING) && state.getValue(RadioBlock.CEILING))
			poseStack.translate(0.0D, dial.rise() / 16.0D, 0.0D);
		poseStack.translate(dial.x2() / 16.0D, dial.y2() / 16.0D, dial.front() / 16.0D - 0.002D);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F)); // Now +x runs left to right as seen from the front, +y up.
		Matrix4f m = poseStack.last().pose();
		VertexConsumer v = buffer.getBuffer(RenderType.entityTranslucentEmissive(com.arrl.radiocraft.Radiocraft.id("textures/block/white.png")));
		int light = on ? LightTexture.FULL_BRIGHT : packedLight;
		int r = on ? 255 : 150, g = on ? 140 : 84, b = on ? 40 : 30;
		quad(v, m, width * 0.06F, y - thick, width * 0.94F, y + thick, r, g, b, light);
		poseStack.popPose();
	}

	private static void quad(VertexConsumer v, Matrix4f m, float x0, float y0, float x1, float y1, int r, int g, int b, int light) {
		v.addVertex(m, x0, y0, 0.0F).setColor(r, g, b, 255).setUv(0.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
		v.addVertex(m, x1, y0, 0.0F).setColor(r, g, b, 255).setUv(1.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
		v.addVertex(m, x1, y1, 0.0F).setColor(r, g, b, 255).setUv(1.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
		v.addVertex(m, x0, y1, 0.0F).setColor(r, g, b, 255).setUv(0.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
	}
}
