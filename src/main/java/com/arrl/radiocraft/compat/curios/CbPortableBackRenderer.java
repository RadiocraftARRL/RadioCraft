package com.arrl.radiocraft.compat.curios;

import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * The CB portable worn in the Curios back slot (2026-11-08) or on the belt (2026-11-09, on the right hip): strapped upright between the shoulder blades, its face
 * to the back, following the body. The item model picks itself (the {@code radiocraft:extended} property), so the
 * whip stands up over the wearer's head while the radio is on and is down when it is off. Client only; only loaded
 * with Curios.
 */
public class CbPortableBackRenderer implements ICurioRenderer {

	/** Full size: the radio is about 8 pixels tall, the whip about 2.5 blocks over it when pulled out. */
	private static final float SCALE = 1.0F;
	/**
	 * Where its body's middle sits on the wearer (pixels from the neck, model space: +y down, +z their back): high on
	 * the back, against it (the back is at 2, the radio 2 deep).
	 */
	private static final float Y = 5.0F, Z = 3.0F;
	/** On the belt: past the right hip (the body is 4 pixels either side), hanging from the waist. */
	private static final float BELT_X = -5.5F, BELT_Y = 13.0F;
	/** The model's body sits this far below the model's middle (room for the whip), pixels. */
	private static final float BODY_BELOW_MIDDLE = 20.0F;

	public static void register() {
		CuriosRendererRegistry.register(RadiocraftItems.CB_PORTABLE.get(), CbPortableBackRenderer::new);
	}

	@Override
	public <T extends LivingEntity, M extends EntityModel<T>> void render(@NotNull ItemStack stack, @NotNull SlotContext slotContext, @NotNull PoseStack poseStack,
			@NotNull RenderLayerParent<T, M> parent, @NotNull MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount, float partialTicks,
			float ageInTicks, float netHeadYaw, float headPitch) {
		LivingEntity entity = slotContext.entity();
		poseStack.pushPose();
		if(parent.getModel() instanceof HumanoidModel<?> humanoid)
			humanoid.body.translateAndRotate(poseStack);
		else {
			ICurioRenderer.translateIfSneaking(poseStack, entity);
			ICurioRenderer.rotateIfSneaking(poseStack, entity);
		}
		boolean belt = "belt".equals(slotContext.identifier());
		if(belt) // Clipped to the right hip, its face out to the side (2026-11-09).
			poseStack.translate(BELT_X / 16.0F, (BELT_Y - BODY_BELOW_MIDDLE * SCALE) / 16.0F, 0.0F);
		else
			poseStack.translate(0.0F, (Y - BODY_BELOW_MIDDLE * SCALE) / 16.0F, Z / 16.0F);
		poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F)); // Entity models are upside down.
		poseStack.mulPose(Axis.YP.rotationDegrees(belt ? 90.0F : 180.0F)); // Its face out to the side, or to the back.
		poseStack.scale(SCALE, SCALE, SCALE);
		Minecraft.getInstance().getItemRenderer().renderStatic(entity, stack, ItemDisplayContext.NONE, false, poseStack, buffers, entity.level(), light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
