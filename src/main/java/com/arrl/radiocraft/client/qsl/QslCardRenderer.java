package com.arrl.radiocraft.client.qsl;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.qsl.QslArt;
import com.arrl.radiocraft.common.qsl.QslCard;
import com.arrl.radiocraft.common.qsl.QslCards;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderItemInFrameEvent;
import org.joml.Matrix4f;

/**
 * Draws QSL cards like maps (computer batch 3C): held in one hand beside the arm, or in both hands in front of the view
 * when held in the main hand with the other hand empty (the vanilla map transforms, copied from
 * {@code ItemInHandRenderer}, whose methods are private), showing the side it is turned to; and an item frame shows
 * the card's front. The picture is the card's own texture ({@link QslTextures}).
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID, value = Dist.CLIENT)
public final class QslCardRenderer {

	/** The card is {@link QslArt#WIDTH} wide on a map's 128, centred top to bottom. */
	private static final float TOP = (128 - QslArt.HEIGHT) / 2.0F, BOTTOM = TOP + QslArt.HEIGHT;

	private QslCardRenderer() {}

	@SubscribeEvent
	public static void onRenderHand(RenderHandEvent event) {
		ItemStack stack = event.getItemStack();
		QslCard card = QslCards.card(stack);
		Minecraft mc = Minecraft.getInstance();
		if(card == null || mc.player == null || mc.player.isScoping())
			return;
		event.setCanceled(true);
		AbstractClientPlayer player = mc.player;
		boolean main = event.getHand() == InteractionHand.MAIN_HAND;
		HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
		PoseStack pose = event.getPoseStack();
		pose.pushPose();
		if(main && player.getOffhandItem().isEmpty())
			twoHanded(pose, event.getMultiBufferSource(), event.getPackedLight(), event.getInterpolatedPitch(), event.getEquipProgress(), event.getSwingProgress(),
					card, QslCards.flipped(stack));
		else
			oneHanded(pose, event.getMultiBufferSource(), event.getPackedLight(), event.getEquipProgress(), arm, event.getSwingProgress(), card, QslCards.flipped(stack));
		pose.popPose();
	}

	@SubscribeEvent
	public static void onRenderInFrame(RenderItemInFrameEvent event) {
		QslCard card = QslCards.card(event.getItemStack());
		if(card == null)
			return;
		event.setCanceled(true);
		PoseStack pose = event.getPoseStack();
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
		pose.scale(0.0078125F, 0.0078125F, 0.0078125F);
		pose.translate(-64.0F, -64.0F, 0.0F);
		pose.translate(0.0F, 0.0F, -1.0F);
		int light = event.getItemFrameEntity().getType() == EntityType.GLOW_ITEM_FRAME ? 0xF000D2 : event.getPackedLight();
		quad(pose, event.getMultiBufferSource(), light, card, false);
		pose.popPose();
	}

	// -------------------- AS VANILLA HOLDS A MAP --------------------

	private static void oneHanded(PoseStack pose, MultiBufferSource buffer, int light, float equip, HumanoidArm hand, float swing, QslCard card, boolean back) {
		float f = hand == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		pose.translate(f * 0.125F, -0.125F, 0.0F);
		if(!Minecraft.getInstance().player.isInvisible()) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(f * 10.0F));
			playerArm(pose, buffer, light, equip, swing, hand);
			pose.popPose();
		}
		pose.pushPose();
		pose.translate(f * 0.51F, -0.08F + equip * -1.2F, -0.75F);
		float f1 = Mth.sqrt(swing);
		float f2 = Mth.sin(f1 * (float)Math.PI);
		pose.translate(f * -0.5F * f2, 0.4F * Mth.sin(f1 * (float)(Math.PI * 2)) - 0.3F * f2, -0.3F * Mth.sin(swing * (float)Math.PI));
		pose.mulPose(Axis.XP.rotationDegrees(f2 * -45.0F));
		pose.mulPose(Axis.YP.rotationDegrees(f * f2 * -30.0F));
		card(pose, buffer, light, card, back);
		pose.popPose();
	}

	private static void twoHanded(PoseStack pose, MultiBufferSource buffer, int light, float pitch, float equip, float swing, QslCard card, boolean back) {
		float f = Mth.sqrt(swing);
		pose.translate(0.0F, 0.2F * Mth.sin(swing * (float)Math.PI) / 2.0F, -0.4F * Mth.sin(f * (float)Math.PI));
		float tilt = mapTilt(pitch);
		pose.translate(0.0F, 0.04F + equip * -1.2F + tilt * -0.5F, -0.72F);
		pose.mulPose(Axis.XP.rotationDegrees(tilt * -85.0F));
		if(!Minecraft.getInstance().player.isInvisible()) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(90.0F));
			mapHand(pose, buffer, light, HumanoidArm.RIGHT);
			mapHand(pose, buffer, light, HumanoidArm.LEFT);
			pose.popPose();
		}
		pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(f * (float)Math.PI) * 20.0F));
		pose.scale(2.0F, 2.0F, 2.0F);
		card(pose, buffer, light, card, back);
	}

	private static float mapTilt(float pitch) {
		float f = Mth.clamp(1.0F - pitch / 45.0F + 0.1F, 0.0F, 1.0F);
		return -Mth.cos(f * (float)Math.PI) * 0.5F + 0.5F;
	}

	private static void card(PoseStack pose, MultiBufferSource buffer, int light, QslCard card, boolean back) {
		pose.mulPose(Axis.YP.rotationDegrees(180.0F));
		pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
		pose.scale(0.38F, 0.38F, 0.38F);
		pose.translate(-0.5F, -0.5F, 0.0F);
		pose.scale(0.0078125F, 0.0078125F, 0.0078125F);
		quad(pose, buffer, light, card, back);
	}

	/** The card's side as a quad over the middle of a map's 128 x 128. */
	private static void quad(PoseStack pose, MultiBufferSource buffer, int light, QslCard card, boolean back) {
		VertexConsumer vc = buffer.getBuffer(RenderType.text(QslTextures.get(card, back)));
		Matrix4f m = pose.last().pose();
		vc.addVertex(m, 0.0F, BOTTOM, 0.0F).setColor(-1).setUv(0.0F, 1.0F).setLight(light);
		vc.addVertex(m, 128.0F, BOTTOM, 0.0F).setColor(-1).setUv(1.0F, 1.0F).setLight(light);
		vc.addVertex(m, 128.0F, TOP, 0.0F).setColor(-1).setUv(1.0F, 0.0F).setLight(light);
		vc.addVertex(m, 0.0F, TOP, 0.0F).setColor(-1).setUv(0.0F, 0.0F).setLight(light);
	}

	private static PlayerRenderer playerRenderer() {
		Minecraft mc = Minecraft.getInstance();
		return (PlayerRenderer)mc.getEntityRenderDispatcher().<AbstractClientPlayer>getRenderer(mc.player);
	}

	private static void mapHand(PoseStack pose, MultiBufferSource buffer, int light, HumanoidArm side) {
		pose.pushPose();
		float f = side == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		pose.mulPose(Axis.YP.rotationDegrees(92.0F));
		pose.mulPose(Axis.XP.rotationDegrees(45.0F));
		pose.mulPose(Axis.ZP.rotationDegrees(f * -41.0F));
		pose.translate(f * 0.3F, -1.1F, 0.45F);
		if(side == HumanoidArm.RIGHT)
			playerRenderer().renderRightHand(pose, buffer, light, Minecraft.getInstance().player);
		else
			playerRenderer().renderLeftHand(pose, buffer, light, Minecraft.getInstance().player);
		pose.popPose();
	}

	private static void playerArm(PoseStack pose, MultiBufferSource buffer, int light, float equip, float swing, HumanoidArm side) {
		boolean right = side != HumanoidArm.LEFT;
		float f = right ? 1.0F : -1.0F;
		float f1 = Mth.sqrt(swing);
		float f2 = -0.3F * Mth.sin(f1 * (float)Math.PI);
		float f3 = 0.4F * Mth.sin(f1 * (float)(Math.PI * 2));
		float f4 = -0.4F * Mth.sin(swing * (float)Math.PI);
		pose.translate(f * (f2 + 0.64000005F), f3 + -0.6F + equip * -0.6F, f4 + -0.71999997F);
		pose.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
		float f5 = Mth.sin(swing * swing * (float)Math.PI);
		float f6 = Mth.sin(f1 * (float)Math.PI);
		pose.mulPose(Axis.YP.rotationDegrees(f * f6 * 70.0F));
		pose.mulPose(Axis.ZP.rotationDegrees(f * f5 * -20.0F));
		pose.translate(f * -1.0F, 3.6F, 3.5F);
		pose.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
		pose.mulPose(Axis.XP.rotationDegrees(200.0F));
		pose.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
		pose.translate(f * 5.6F, 0.0F, 0.0F);
		if(right)
			playerRenderer().renderRightHand(pose, buffer, light, Minecraft.getInstance().player);
		else
			playerRenderer().renderLeftHand(pose, buffer, light, Minecraft.getInstance().player);
	}
}
