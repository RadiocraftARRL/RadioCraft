package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.RadiocraftClientConfig;
import com.arrl.radiocraft.client.screens.mesh.Crt;
import com.arrl.radiocraft.client.screens.mesh.FireAlarmCrt;
import com.arrl.radiocraft.client.screens.mesh.MeshScreens;
import com.arrl.radiocraft.client.screens.mesh.ScadaCrt;
import com.arrl.radiocraft.common.blockentities.mesh.FireAlarmControllerBlockEntity;
import com.arrl.radiocraft.common.blockentities.mesh.MeshDeviceBlockEntity;
import com.arrl.radiocraft.common.blockentities.mesh.ScadaMasterBlockEntity;
import com.arrl.radiocraft.common.blocks.mesh.MeshDeviceBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * The green screens on the fire alarm controller's and SCADA master's blocks (2026-11-11), as on their terminals: the
 * controller's newest event ({@link FireAlarmCrt}), the master's points ({@link ScadaCrt}) on the CRT half beside it.
 * From what the server sends players near them ({@link MeshScreens}); dark while nothing has come. Same distance and
 * client setting as the radio screens.
 */
public class MeshScreenRenderer<T extends MeshDeviceBlockEntity> implements BlockEntityRenderer<T> {

	private static final int CONTROLLER_INK = 0xFF5CFF7A, MASTER_INK = 0xFF6EE68C;
	/** The controller's CRT glass in model pixels, facing north (tools/fire_alarm_controller_assets.py). */
	private static final float[] CONTROLLER_GLASS = { 1.25F, 6.5F, 2.625F, 7.375F, 1.4F };
	/** The master's CRT glass, on the block beside it (x 16 to 32 is that block, east of the panel facing north). */
	private static final float[] MASTER_GLASS = { 18.0F, 30.0F, 3.5F, 12.5F, 5.0F };
	private static final float INSET = 0.25F;

	private final Font font;
	private final Crt controllerCrt = new Crt(FireAlarmCrt.COLS, FireAlarmCrt.ROWS);
	private final Crt masterCrt = new Crt(ScadaCrt.COLS, ScadaCrt.ROWS);

	public MeshScreenRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.getFont();
	}

	@Override
	public void render(@NotNull T device, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
		if(!RadiocraftClientConfig.SHOW_RADIO_SCREENS.get() || device.getLevel() == null)
			return;
		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		if(camera.distanceToSqr(device.getBlockPos().getCenter()) > RadioDisplayRenderer.VIEW_DISTANCE * RadioDisplayRenderer.VIEW_DISTANCE)
			return;
		BlockState state = device.getBlockState();
		CompoundTag status = MeshScreens.get(device.getBlockPos());
		if(status == null || !state.hasProperty(MeshDeviceBlock.FACING))
			return;
		boolean blink = (System.currentTimeMillis() / 500L) % 2L == 0L;
		float[] glass;
		Crt crt;
		int ink;
		if(device instanceof FireAlarmControllerBlockEntity) {
			FireAlarmCrt.fill(controllerCrt, status, FireAlarmCrt.View.EVENT, 0, 0, blink);
			crt = controllerCrt;
			glass = CONTROLLER_GLASS;
			ink = CONTROLLER_INK;
		}
		else if(device instanceof ScadaMasterBlockEntity) {
			ScadaCrt.fill(masterCrt, status, 0, 0);
			crt = masterCrt;
			glass = MASTER_GLASS;
			ink = MASTER_INK;
		}
		else
			return;
		float x1 = glass[0] + INSET, x2 = glass[1] - INSET, y1 = glass[2] + INSET, y2 = glass[3] - INSET;
		poseStack.pushPose();
		poseStack.translate(0.5D, 0.5D, 0.5D); // Turn as the blockstate turns the model (it faces north as drawn).
		poseStack.mulPose(Axis.YP.rotationDegrees(-((state.getValue(MeshDeviceBlock.FACING).toYRot() + 180.0F) % 360.0F)));
		poseStack.translate(-0.5D, -0.5D, -0.5D);
		// The glass's top left as the viewer sees it: its east edge (they face south), a hair in front of it.
		poseStack.translate(x2 / 16.0D, y2 / 16.0D, glass[4] / 16.0D - 0.003D);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F)); // x now runs to the viewer's right, facing out of the glass.
		crt.drawWorld(poseStack, buffer, font, (x2 - x1) / 16.0F, (y2 - y1) / 16.0F, ink);
		poseStack.popPose();
	}

	/** The master draws on the block beside it, so it must not be culled with its own block. */
	@Override
	public @NotNull AABB getRenderBoundingBox(@NotNull T device) {
		return new AABB(device.getBlockPos()).inflate(1.0D);
	}

	@Override
	public int getViewDistance() {
		return (int)Math.ceil(RadioDisplayRenderer.VIEW_DISTANCE) + 1;
	}
}
