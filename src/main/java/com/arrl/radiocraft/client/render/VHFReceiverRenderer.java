package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.common.blockentities.radio.VHFReceiverBlockEntity;
import com.arrl.radiocraft.common.blocks.radios.RadioBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * The VHF receiver's moving parts in the world (2026-11-09): the eight slide switch handles, up or down; the numeral of
 * the channel the scan is on, lit red and full bright over the dim one painted in the window; and the SET lamp. It has
 * no display. Everything is in front texture pixels of {@code models/block/vhf_receiver.json} as
 * {@code tools/vhf_receiver_assets.py} draws it (its B_* constants): 8 px a model unit, the front the case's north face,
 * its top left at model x 14, y 4, z 4.
 */
public class VHFReceiverRenderer implements BlockEntityRenderer<VHFReceiverBlockEntity> {

	/** The 3 x 5 glyphs of the numerals 1 to 8, rows top down (tools/marine_radio_assets.py GLYPHS). */
	public static final String[] DIGITS = { "010110010010111", "110001010100111", "110001010001110", "101101111001001",
			"111100110001110", "011100111101111", "111001010010010", "111101111101111" };

	private static final float FRONT_X = 14.0F, FRONT_Y = 4.0F, FRONT_Z = 4.0F;
	/** How high the model reaches (the bail's bar), model units: the ceiling model is raised by the rest of the block. */
	private static final float MODEL_TOP = 5.0F;
	private static final int NUMERAL_X0 = 45, NUMERAL_PITCH = 6, NUMERAL_Y = 6;
	private static final int HANDLE_ON_Y = 19, HANDLE_OFF_Y = 25, HANDLE_H = 3;
	private static final int[] SET_LAMP = { 32, 8, 2, 2 };
	private static final int NUMERAL_LIT = 0xFFFF4A30, LAMP_LIT = 0xFFFF3020, HANDLE = 0xFFD6D8DC, HANDLE_SHADOW = 0xFF46484C;

	public VHFReceiverRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	public void render(@NotNull VHFReceiverBlockEntity receiver, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer,
	                   int packedLight, int packedOverlay) {
		BlockState state = receiver.getBlockState();
		if(!state.hasProperty(RadioBlock.HORIZONTAL_FACING))
			return;
		poseStack.pushPose();
		poseStack.translate(0.5D, 0.5D, 0.5D); // As the blockstate turns the model (it faces north as drawn).
		poseStack.mulPose(Axis.YP.rotationDegrees(-((state.getValue(RadioBlock.HORIZONTAL_FACING).toYRot() + 180.0F) % 360.0F)));
		poseStack.translate(-0.5D, -0.5D, -0.5D);
		if(state.hasProperty(RadioBlock.CEILING) && state.getValue(RadioBlock.CEILING))
			poseStack.translate(0.0D, (16.0F - MODEL_TOP) / 16.0D, 0.0D);
		VertexConsumer consumer = buffer.getBuffer(RenderType.textBackground());
		Matrix4f pose = poseStack.last().pose();
		for(int i = 0; i < VHFReceiverBlockEntity.CHANNELS; i++) {
			int x = NUMERAL_X0 + NUMERAL_PITCH * i, y = receiver.isChannelOn(i) ? HANDLE_ON_Y : HANDLE_OFF_Y;
			quad(consumer, pose, x, y, 3, HANDLE_H - 1, HANDLE, packedLight);
			quad(consumer, pose, x, y + HANDLE_H - 1, 3, 1, HANDLE_SHADOW, packedLight);
		}
		if(receiver.wasPowered()) {
			int lit = receiver.getScanChannel();
			if(lit >= 0 && lit < DIGITS.length && receiver.isChannelOn(lit)) {
				String glyph = DIGITS[lit];
				for(int gy = 0; gy < 5; gy++)
					for(int gx = 0; gx < 3; gx++)
						if(glyph.charAt(gy * 3 + gx) == '1')
							quad(consumer, pose, NUMERAL_X0 + NUMERAL_PITCH * lit + gx, NUMERAL_Y + gy, 1, 1, NUMERAL_LIT, LightTexture.FULL_BRIGHT);
			}
			if(receiver.isSetLit())
				quad(consumer, pose, SET_LAMP[0], SET_LAMP[1], SET_LAMP[2], SET_LAMP[3], LAMP_LIT, LightTexture.FULL_BRIGHT);
		}
		poseStack.popPose();
	}

	/** A flat rectangle on the front, {@code x, y, w, h} in front texture pixels, a hair in front of it. */
	private static void quad(VertexConsumer consumer, Matrix4f pose, int x, int y, int w, int h, int colour, int light) {
		float x1 = (FRONT_X - (x + w) / 8.0F) / 16.0F, x2 = (FRONT_X - x / 8.0F) / 16.0F;
		float y1 = (FRONT_Y - (y + h) / 8.0F) / 16.0F, y2 = (FRONT_Y - y / 8.0F) / 16.0F;
		float z = FRONT_Z / 16.0F - 0.002F;
		float[][] corners = { { x1, y1 }, { x1, y2 }, { x2, y2 }, { x2, y1 } };
		for(int i = 0; i < 8; i++) { // Wound both ways, so it shows whichever way culling takes it.
			float[] corner = corners[i < 4 ? i : 7 - i];
			consumer.addVertex(pose, corner[0], corner[1], z).setColor(colour).setLight(light);
		}
	}
}
