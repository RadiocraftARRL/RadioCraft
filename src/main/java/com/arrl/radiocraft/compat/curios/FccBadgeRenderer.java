package com.arrl.radiocraft.compat.curios;

import com.arrl.radiocraft.client.render.FccJacketModel;
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
 * The FCC badge worn with Curios (2026-11-09): pinned on the left breast in the body slot, or clipped to the front of the
 * belt, left of the buckle, in the belt slot. Drawn flat from its item model, over whatever is worn on the chest, and
 * following the body as it turns and crouches. Client only; only loaded with Curios.
 */
public class FccBadgeRenderer implements ICurioRenderer {

	/** The item model is a block (16 pixels) across; the shield in it is 10 of those, so about 3 pixels on the chest. */
	private static final float SCALE = 0.3F;
	/**
	 * Where its middle sits (pixels from the neck, model space: +x the wearer's left, +y down, -z their front). On the
	 * chest: halfway out to the left side, a breast pocket's height. On the belt: just left of the middle, on the belt line.
	 */
	private static final float CHEST_X = 2.0F, CHEST_Y = 4.0F, BELT_X = 1.5F, BELT_Y = 10.5F;
	/**
	 * The body's front is 2 pixels from its middle; the skin's jacket layer stands 0.25 over that, leggings' waist 0.5.
	 * The badge is 0.3 thick at this size, so its middle stands 0.2 clear of what it is pinned to.
	 */
	private static final float BODY_FRONT = 2.0F, SKIN_LAYER = 0.25F, LEGGINGS = 0.5F, CLEARANCE = 0.2F;

	public static void register() {
		CuriosRendererRegistry.register(RadiocraftItems.FCC_BADGE.get(), FccBadgeRenderer::new);
	}

	@Override
	public <T extends LivingEntity, M extends EntityModel<T>> void render(@NotNull ItemStack stack, @NotNull SlotContext slotContext, @NotNull PoseStack poseStack,
			@NotNull RenderLayerParent<T, M> parent, @NotNull MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount, float partialTicks,
			float ageInTicks, float netHeadYaw, float headPitch) {
		LivingEntity entity = slotContext.entity();
		poseStack.pushPose();
		if(parent.getModel() instanceof HumanoidModel<?> humanoid)
			humanoid.body.translateAndRotate(poseStack); // Leans and turns with the body (crouching included).
		else {
			ICurioRenderer.translateIfSneaking(poseStack, entity);
			ICurioRenderer.rotateIfSneaking(poseStack, entity);
		}
		boolean belt = "belt".equals(slotContext.identifier());
		// Over the chest armour if there is any (the FCC jacket fits close, other chestplates stand a pixel off), else over the skin.
		float under = Math.max(SKIN_LAYER, FccJacketModel.chestInflation(entity));
		if(belt && entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).getItem() instanceof net.minecraft.world.item.ArmorItem)
			under = Math.max(under, LEGGINGS);
		float front = BODY_FRONT + under + CLEARANCE;
		poseStack.translate((belt ? BELT_X : CHEST_X) / 16.0F, (belt ? BELT_Y : CHEST_Y) / 16.0F, -front / 16.0F);
		// Entity models are upside down (+y down), and the item's face is on its +z side: half a turn about x puts it
		// upright with its face to the front, not mirrored.
		poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
		poseStack.scale(SCALE, SCALE, SCALE);
		Minecraft.getInstance().getItemRenderer().renderStatic(entity, stack, ItemDisplayContext.NONE, false, poseStack, buffers, entity.level(), light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
