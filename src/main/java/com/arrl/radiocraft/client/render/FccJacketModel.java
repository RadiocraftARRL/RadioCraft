package com.arrl.radiocraft.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * The FCC field agent's jacket cut close to the body (2026-11-09). Vanilla draws chest armour on the outer armour model,
 * blown up by a whole pixel, so the jacket stood off the wearer like a box. This one sits just over the skin's own
 * jacket and sleeve layer (0.25), and on slim (Alex) skins its sleeves are 3 pixels wide like the arms under them. Same
 * 64x32 armour texture. Client only.
 */
public final class FccJacketModel {

	/** Sleeves and body: just past the skin's outer layer (0.25), so the two do not flicker through each other. */
	public static final float INFLATION = 0.3F;
	/**
	 * The body when leggings are worn as well: leggings draw their waist on the inner armour model at 0.5, which would
	 * poke through a body at 0.3, so the jacket's body goes just over them.
	 */
	public static final float INFLATION_OVER_LEGGINGS = 0.55F;
	/** Other chest armour: vanilla's outer armour model. */
	public static final float VANILLA_CHEST_INFLATION = 1.0F;

	/** [slim][over leggings], built the first time each is needed. */
	private static final HumanoidModel<?>[][] MODELS = new HumanoidModel<?>[2][2];

	private FccJacketModel() {}

	/** The client extension that swaps the jacket's armour model; register it for the jacket. */
	public static IClientItemExtensions extensions() {
		return new IClientItemExtensions() {
			@Override
			public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
				return slot == EquipmentSlot.CHEST ? model(isSlim(entity), overLeggings(entity)) : original;
			}
		};
	}

	/** A player whose skin has the slim (Alex) arms, 3 pixels wide. */
	public static boolean isSlim(LivingEntity entity) {
		return entity instanceof AbstractClientPlayer player && player.getSkin().model() == PlayerSkin.Model.SLIM;
	}

	private static boolean overLeggings(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof ArmorItem;
	}

	/** How far the front of the chest armour this entity wears stands off its body, pixels (0 with none). */
	public static float chestInflation(LivingEntity entity) {
		ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
		if(chest.isEmpty() || !(chest.getItem() instanceof ArmorItem))
			return 0.0F;
		if(chest.is(com.arrl.radiocraft.common.init.RadiocraftItems.FCC_JACKET.get()))
			return overLeggings(entity) ? INFLATION_OVER_LEGGINGS : INFLATION;
		return VANILLA_CHEST_INFLATION;
	}

	private static HumanoidModel<?> model(boolean slim, boolean overLeggings) {
		int s = slim ? 1 : 0, l = overLeggings ? 1 : 0;
		if(MODELS[s][l] == null) {
			ModelPart root = LayerDefinition.create(mesh(slim, overLeggings), 64, 32).bakeRoot();
			MODELS[s][l] = new HumanoidModel<LivingEntity>(root);
		}
		return MODELS[s][l];
	}

	private static MeshDefinition mesh(boolean slim, boolean overLeggings) {
		CubeDeformation fit = new CubeDeformation(INFLATION);
		MeshDefinition mesh = HumanoidModel.createMesh(fit, 0.0F);
		PartDefinition root = mesh.getRoot();
		if(overLeggings)
			root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F,
					new CubeDeformation(INFLATION_OVER_LEGGINGS)), PartPose.ZERO);
		if(slim) { // As PlayerModel builds slim arms. The pivots are copied from the wearer's model each frame anyway.
			root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, fit),
					PartPose.offset(-5.0F, 2.5F, 0.0F));
			root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, fit),
					PartPose.offset(5.0F, 2.5F, 0.0F));
		}
		return mesh;
	}
}
