package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.RadiocraftClientConfig;
import com.arrl.radiocraft.common.blockentities.BatteryMeterBlockEntity;
import com.arrl.radiocraft.common.blocks.BatteryMeterBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The battery meter's needle (2026-11-11: the meter is an analog DC voltmeter, its dial painted by
 * {@code tools/battery_meter_model.py}). It swings from the pivot over the 8 to 16 V scale to the voltage the meter
 * reads, easing there as a real movement does, and rests on 8 with no battery on the wire. Lit by the block's light,
 * like the dial behind it. Same distance and client setting as the radio screens.
 */
public class BatteryMeterRenderer implements BlockEntityRenderer<BatteryMeterBlockEntity> {

	/** The needle's pivot and length, model pixels as drawn facing north, and the dial's face (z). */
	private static final float PIVOT_X = 8.0F, PIVOT_Y = 3.25F, LENGTH = 7.25F, WIDTH = 0.2F, FRONT = 4.0F;
	/** The scale: 8 to 16 V over 45 degrees either side of straight up. */
	private static final double LOW = 8.0D, HIGH = 16.0D, SWEEP = 45.0D;
	private static final int NEEDLE = 0xFF1A1816;

	/** Where each meter's needle is now, volts (client only), so it eases to a new reading. */
	private static final Map<BatteryMeterBlockEntity, Double> SHOWN = new WeakHashMap<>();
	private static final Map<BatteryMeterBlockEntity, Long> LAST_NANOS = new WeakHashMap<>();

	public BatteryMeterRenderer(BlockEntityRendererProvider.Context context) {}

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

		double target = meter.isShownBatteries() ? meter.getShownVolts() : LOW;
		long now = System.nanoTime();
		double seconds = Math.min(0.25D, (now - LAST_NANOS.getOrDefault(meter, now)) / 1.0E9D);
		LAST_NANOS.put(meter, now);
		double shown = SHOWN.getOrDefault(meter, target);
		shown += (target - shown) * (1.0D - Math.exp(-seconds * 4.0D)); // About a quarter second to settle.
		SHOWN.put(meter, shown);
		double deg = -SWEEP + (Math.max(LOW, Math.min(HIGH, shown)) - LOW) / (HIGH - LOW) * 2.0D * SWEEP;

		poseStack.pushPose();
		poseStack.translate(0.5D, 0.5D, 0.5D); // Turn as the blockstate turns the model (it faces north as drawn).
		poseStack.mulPose(Axis.YP.rotationDegrees(-((state.getValue(BatteryMeterBlock.FACING).toYRot() + 180.0F) % 360.0F)));
		poseStack.translate(-0.5D, -0.5D, -0.5D);
		// Clockwise as the viewer sees it (they look south, so their right is west, -x).
		float sin = (float)Math.sin(Math.toRadians(deg)), cos = (float)Math.cos(Math.toRadians(deg));
		float tipX = PIVOT_X - sin * LENGTH, tipY = PIVOT_Y + cos * LENGTH;
		float nx = cos * WIDTH / 2.0F, ny = sin * WIDTH / 2.0F; // Half the needle's width, across it.
		float[][] corners = { { PIVOT_X - nx, PIVOT_Y - ny }, { tipX - nx * 0.4F, tipY - ny * 0.4F }, { tipX + nx * 0.4F, tipY + ny * 0.4F }, { PIVOT_X + nx, PIVOT_Y + ny } };
		VertexConsumer consumer = buffer.getBuffer(RenderType.textBackground());
		Matrix4f pose = poseStack.last().pose();
		float z = (FRONT - 0.02F) / 16.0F;
		for(int i = 0; i < 8; i++) { // Wound both ways, so it shows whichever way culling takes it.
			float[] corner = corners[i < 4 ? i : 7 - i];
			consumer.addVertex(pose, corner[0] / 16.0F, corner[1] / 16.0F, z).setColor(NEEDLE).setLight(packedLight);
		}
		poseStack.popPose();
	}

	@Override
	public int getViewDistance() {
		return (int)Math.ceil(RadioDisplayRenderer.VIEW_DISTANCE) + 1;
	}
}
