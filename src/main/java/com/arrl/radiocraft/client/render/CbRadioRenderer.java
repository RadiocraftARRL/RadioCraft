package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.RadiocraftClientConfig;
import com.arrl.radiocraft.common.blockentities.radio.CbRadioBlockEntity;
import com.arrl.radiocraft.common.blocks.radios.RadioBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Draws a CB mobile's front panel on the block (2026-11-08), on the elements the model names: the channel number in
 * red on its {@code DISPLAY} window, and on the 5875 the clock on the wider {@code DISPLAY} window (it runs whenever the
 * radio has power to hand, switched on or not) and the needles of its two {@code SIG_TX_METER} meters (the RX signal /
 * TX power meter on the left, the SWR / modulation meter on the right as seen from the front). The ON AIR and RX/TX
 * lamps light as on the other radios ({@link RadioDisplays#lamps}). And the hand mic's cord.
 */
public class CbRadioRenderer implements BlockEntityRenderer<CbRadioBlockEntity> {

	private static final int RED = 0xFFFF3020, NEEDLE = 0xFF101010;
	private final Font font;

	public CbRadioRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.getFont();
	}

	@Override
	public void render(@NotNull CbRadioBlockEntity radio, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
		BlockState state = radio.getBlockState();
		renderMicCord(radio, state, partialTick, poseStack, buffer);
		if(radio.getLevel() == null || !state.hasProperty(RadioBlock.HORIZONTAL_FACING))
			return;
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		boolean on = radio.wasPowered();
		if(on)
			RadioDisplayRenderer.renderLamps(radio, state, RadioDisplays.get(path), RadioDisplays.lamps(path), poseStack, buffer);
		if(!RadiocraftClientConfig.SHOW_RADIO_SCREENS.get())
			return;
		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		if(camera.distanceToSqr(radio.getBlockPos().getCenter()) > RadioDisplayRenderer.VIEW_DISTANCE * RadioDisplayRenderer.VIEW_DISTANCE)
			return;

		List<RadioDisplays.Display> windows = RadioDisplays.named(path, "DISPLAY").stream()
				.sorted(Comparator.comparingDouble(d -> d.x2() - d.x1())).toList(); // Narrowest (the channel) first.
		List<RadioDisplays.Display> meters = RadioDisplays.named(path, "SIG_TX_METER").stream()
				.sorted(Comparator.comparingDouble(d -> -d.x2())).toList(); // As seen from the front, left first (east is left).
		boolean tx = on && radio.isPTTDown() && !radio.isTransmitInhibited();

		if(on && !windows.isEmpty())
			text(radio, state, windows.getFirst(), radio.displayLine(), poseStack, buffer);
		if(radio.isSupplied() && windows.size() > 1) {
			int[] clock = CbRadioBlockEntity.clock(radio.getLevel().getDayTime());
			text(radio, state, windows.getLast(), String.format(Locale.ROOT, "%d:%02d", clock[0], clock[1]), poseStack, buffer);
		}
		if(meters.size() >= 2) {
			double left = !on ? 0.0D : tx ? radio.getPowerMeter() : Math.min(1.0D, radio.getSyncedSignal());
			double right = !tx ? 0.0D : radio.getRadioState().swrMeter() ? radio.getSwrMeter() : radio.getModulationLevel();
			needle(state, meters.get(0), left, poseStack, buffer);
			needle(state, meters.get(1), right, poseStack, buffer);
		}
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

	/** Red LED digits, centred in the window. */
	private void text(CbRadioBlockEntity radio, BlockState state, RadioDisplays.Display window, String text, PoseStack poseStack, MultiBufferSource buffer) {
		float width = (window.x2() - window.x1()) / 16.0F, height = (window.y2() - window.y1()) / 16.0F;
		float scale = Math.min(width * 0.85F / Math.max(1, font.width(text)), height * 0.75F / font.lineHeight);
		poseStack.pushPose();
		toWindow(state, window, poseStack);
		poseStack.translate((width - font.width(text) * scale) / 2.0F, -(height - (font.lineHeight - 1) * scale) / 2.0F, 0.0F);
		poseStack.scale(scale, -scale, scale);
		font.drawInBatch(text, 0.0F, 0.0F, RED, false, poseStack.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
		poseStack.popPose();
	}

	/** A meter's needle from the middle of its bottom edge, swinging from upper left (0) to upper right (1). */
	private static void needle(BlockState state, RadioDisplays.Display meter, double value, PoseStack poseStack, MultiBufferSource buffer) {
		float width = (meter.x2() - meter.x1()) / 16.0F, height = (meter.y2() - meter.y1()) / 16.0F;
		double angle = Math.toRadians(145.0D - 110.0D * Math.max(0.0D, Math.min(1.0D, value)));
		float px = width / 2.0F, py = -height * 0.92F, length = height * 0.8F, half = 0.0035F;
		float tx = px + (float)Math.cos(angle) * length, ty = py + (float)Math.sin(angle) * length;
		float nx = -(float)Math.sin(angle) * half, ny = (float)Math.cos(angle) * half;
		poseStack.pushPose();
		toWindow(state, meter, poseStack);
		Matrix4f pose = poseStack.last().pose();
		VertexConsumer consumer = buffer.getBuffer(RenderType.textBackground());
		float[][] corners = {{px + nx, py + ny}, {tx + nx, ty + ny}, {tx - nx, ty - ny}, {px - nx, py - ny}};
		for(int i = 0; i < 8; i++) { // Wound both ways, so it shows whichever way culling takes it.
			float[] corner = corners[i < 4 ? i : 7 - i];
			consumer.addVertex(pose, corner[0], corner[1], 0.0005F).setColor(NEEDLE).setLight(LightTexture.FULL_BRIGHT);
		}
		poseStack.popPose();
	}

	/**
	 * Where each model's hand mic plugs in, in model pixels as drawn facing north: the 5875's MIC jack bottom right of
	 * its panel, the PC68's bottom left (as seen from the front; east is the viewer's left).
	 */
	private static final java.util.Map<String, Vec3> MIC_JACKS = java.util.Map.of(
			"cb_mobile_5875", new Vec3(1.2D, 1.1D, 0.4D),
			"cb_mobile_pc68", new Vec3(12.0D, 0.7D, 0.5D));

	/** The hand mic's cord, from the radio's mic jack to the hand of the player holding the mic, while it is out. */
	private static void renderMicCord(CbRadioBlockEntity radio, BlockState state, float partialTick, PoseStack poseStack, MultiBufferSource buffer) {
		java.util.UUID holder = radio.getMicHolder();
		if(holder == null || radio.getLevel() == null || !state.hasProperty(RadioBlock.HORIZONTAL_FACING))
			return;
		net.minecraft.world.entity.player.Player player = radio.getLevel().getPlayerByUUID(holder);
		if(player == null)
			return;
		Vec3 jack = MIC_JACKS.getOrDefault(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(), new Vec3(8.0D, 6.0D, 8.0D));
		// Turn the jack as the blockstate turns the model: clockwise from above by the model's y rotation.
		double angle = Math.toRadians((state.getValue(RadioBlock.HORIZONTAL_FACING).toYRot() + 180.0F) % 360.0F);
		double dx = jack.x / 16.0D - 0.5D, dz = jack.z / 16.0D - 0.5D;
		double x = dx * Math.cos(angle) - dz * Math.sin(angle), z = dx * Math.sin(angle) + dz * Math.cos(angle);
		double y = jack.y / 16.0D;
		RadioDisplays.Display display = RadioDisplays.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
		if(display != null && state.hasProperty(RadioBlock.CEILING) && state.getValue(RadioBlock.CEILING))
			y += display.rise() / 16.0D;
		Vec3 from = Vec3.atLowerCornerOf(radio.getBlockPos()).add(0.5D + x, y, 0.5D + z);
		CordRenderer.render(radio.getLevel(), radio.getBlockPos(), from, player.getRopeHoldPosition(partialTick), poseStack, buffer);
	}

	@Override
	public @NotNull AABB getRenderBoundingBox(@NotNull CbRadioBlockEntity radio) {
		AABB box = new AABB(radio.getBlockPos());
		return radio.getMicHolder() == null ? box : box.inflate(com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity.MIC_CORD_LENGTH + 2.0D);
	}
}
