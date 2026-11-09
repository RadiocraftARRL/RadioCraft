package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.RadiocraftClientConfig;
import com.arrl.radiocraft.common.be_networks.BatteryMeterReading;
import com.arrl.radiocraft.common.blockentities.BatteryMeterBlockEntity;
import com.arrl.radiocraft.common.blocks.BatteryMeterBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Writes the battery meter's reading on the dark panel at the front of its model: the voltage on the top line, the
 * charge and an arrow (up while charging, down while discharging) below it. Green, red under 20%, "NO BATT" with no
 * battery on the wire. Same distance and client setting as the radio screens.
 */
public class BatteryMeterRenderer implements BlockEntityRenderer<BatteryMeterBlockEntity> {

	private static final int COLOUR = 0xFF66FF66;
	private static final int LOW_COLOUR = 0xFFFF5533;
	/** The display panel in the model (pixels, facing north as drawn): x, y, and the z of its front face. */
	private static final float X1 = 2.0F, X2 = 14.0F, Y1 = 1.0F, Y2 = 7.0F, FRONT = -0.5F;

	private final Font font;

	public BatteryMeterRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.getFont();
	}

	@Override
	public void render(@NotNull BatteryMeterBlockEntity meter, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
		if(!RadiocraftClientConfig.SHOW_RADIO_SCREENS.get() || meter.getLevel() == null)
			return;
		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		if(camera.distanceToSqr(meter.getBlockPos().getCenter()) > RadioDisplayRenderer.VIEW_DISTANCE * RadioDisplayRenderer.VIEW_DISTANCE)
			return;
		BlockState state = meter.getBlockState();
		if(!state.hasProperty(BatteryMeterBlock.FACING))
			return;

		String top, bottom;
		int colour;
		if(meter.isShownBatteries()) {
			top = String.format("%.1fV", meter.getShownVolts());
			String arrow = meter.getShownTrend() == BatteryMeterReading.Trend.CHARGING ? " ▲" : meter.getShownTrend() == BatteryMeterReading.Trend.DISCHARGING ? " ▼" : "";
			bottom = meter.getShownPercent() + "%" + arrow;
			colour = meter.getShownPercent() < 20 ? LOW_COLOUR : COLOUR;
		}
		else {
			top = "NO";
			bottom = "BATT";
			colour = LOW_COLOUR;
		}

		float width = (X2 - X1) / 16.0F, height = (Y2 - Y1) / 16.0F;
		int widest = Math.max(font.width(top), font.width(bottom));
		float scale = Math.min(width * 0.85F / widest, height * 0.45F / font.lineHeight);

		poseStack.pushPose();
		poseStack.translate(0.5D, 0.5D, 0.5D); // Turn as the blockstate turns the model (it faces north as drawn).
		poseStack.mulPose(Axis.YP.rotationDegrees(-((state.getValue(BatteryMeterBlock.FACING).toYRot() + 180.0F) % 360.0F)));
		poseStack.translate(-0.5D, -0.5D, -0.5D);
		// The panel's top left as seen from the front: its east edge (the viewer faces south), a hair in front of it.
		poseStack.translate(X2 / 16.0D, Y2 / 16.0D, FRONT / 16.0D - 0.002D);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F)); // Text reads along -x and faces north, out of the panel.
		float lineGap = height / 2.0F;
		line(poseStack, buffer, top, colour, scale, width, (lineGap - font.lineHeight * scale) / 2.0F);
		line(poseStack, buffer, bottom, colour, scale, width, lineGap + (lineGap - font.lineHeight * scale) / 2.0F);
		poseStack.popPose();
	}

	/** One line of text centred across the panel, {@code down} below its top edge. */
	private void line(PoseStack poseStack, MultiBufferSource buffer, String text, int colour, float scale, float width, float down) {
		poseStack.pushPose();
		poseStack.translate((width - font.width(text) * scale) / 2.0F, -down, 0.0F);
		poseStack.scale(scale, -scale, scale);
		font.drawInBatch(text, 0.0F, 0.0F, colour, false, poseStack.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
		poseStack.popPose();
	}

	@Override
	public int getViewDistance() {
		return (int)Math.ceil(RadioDisplayRenderer.VIEW_DISTANCE) + 1;
	}
}
