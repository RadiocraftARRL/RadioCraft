package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.RadiocraftClientConfig;
import com.arrl.radiocraft.common.blockentities.radio.AirbandRadioBlockEntity;
import com.arrl.radiocraft.common.blocks.radios.RadioBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

/**
 * Writes the airband radio's two frequencies in amber on its {@code DISPLAY} windows (2026-11-13): USE on the left
 * window as seen from the front, STANDBY on the right, while it is on.
 */
public class AirbandRadioRenderer implements BlockEntityRenderer<AirbandRadioBlockEntity> {

	private static final int AMBER = 0xFFFFA020;
	private final Font font;

	public AirbandRadioRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.getFont();
	}

	@Override
	public void render(@NotNull AirbandRadioBlockEntity radio, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
		BlockState state = radio.getBlockState();
		if(radio.getLevel() == null || !radio.wasPowered() || !state.hasProperty(RadioBlock.HORIZONTAL_FACING) || !RadiocraftClientConfig.SHOW_RADIO_SCREENS.get())
			return;
		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		if(camera.distanceToSqr(radio.getBlockPos().getCenter()) > RadioDisplayRenderer.VIEW_DISTANCE * RadioDisplayRenderer.VIEW_DISTANCE)
			return;
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		List<RadioDisplays.Display> windows = RadioDisplays.named(path, "DISPLAY").stream()
				.sorted(Comparator.comparingDouble(d -> -d.x2())).toList(); // As seen from the front, left first (east is left).
		if(windows.size() < 2)
			return;
		text(state, windows.get(0), AirbandRadioBlockEntity.format(radio.getFrequency()), poseStack, buffer);
		text(state, windows.get(1), AirbandRadioBlockEntity.format(radio.getStandbyFrequency()), poseStack, buffer);
	}

	/** Turns the pose to the window's top left as seen from the front, a hair in front of it; +x right, +y up, in blocks. */
	private static void toWindow(BlockState state, RadioDisplays.Display window, PoseStack poseStack) {
		poseStack.translate(0.5D, 0.5D, 0.5D);
		poseStack.mulPose(Axis.YP.rotationDegrees(-((state.getValue(RadioBlock.HORIZONTAL_FACING).toYRot() + 180.0F) % 360.0F)));
		poseStack.translate(-0.5D, -0.5D, -0.5D);
		if(state.hasProperty(RadioBlock.CEILING) && state.getValue(RadioBlock.CEILING))
			poseStack.translate(0.0D, window.rise() / 16.0D, 0.0D);
		poseStack.translate(window.x2() / 16.0D, window.y2() / 16.0D, window.front() / 16.0D - 0.002D);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
	}

	private void text(BlockState state, RadioDisplays.Display window, String text, PoseStack poseStack, MultiBufferSource buffer) {
		float width = (window.x2() - window.x1()) / 16.0F, height = (window.y2() - window.y1()) / 16.0F;
		float scale = Math.min(width * 0.9F / Math.max(1, font.width(text)), height * 0.75F / font.lineHeight);
		poseStack.pushPose();
		toWindow(state, window, poseStack);
		poseStack.translate((width - font.width(text) * scale) / 2.0F, -(height - (font.lineHeight - 1) * scale) / 2.0F, 0.0F);
		poseStack.scale(scale, -scale, scale);
		font.drawInBatch(text, 0.0F, 0.0F, AMBER, false, poseStack.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
		poseStack.popPose();
	}
}
