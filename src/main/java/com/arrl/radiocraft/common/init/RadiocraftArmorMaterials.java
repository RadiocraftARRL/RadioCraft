package com.arrl.radiocraft.common.init;

import com.arrl.radiocraft.Radiocraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

/**
 * RadioCraft's armour materials. NeoForge registers armour materials before items, so the items can take the holder.
 */
public class RadiocraftArmorMaterials {

	public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Radiocraft.MOD_ID);

	/**
	 * The FCC field agent's 1970s hat and jacket (1.12): cloth, so leather's protection, enchantability and sound;
	 * mended with brown wool. Worn, it draws {@code textures/models/armor/fcc_layer_1.png}.
	 */
	public static final DeferredHolder<ArmorMaterial, ArmorMaterial> FCC = ARMOR_MATERIALS.register("fcc", () -> {
		EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
		defense.put(ArmorItem.Type.BOOTS, 1);
		defense.put(ArmorItem.Type.LEGGINGS, 2);
		defense.put(ArmorItem.Type.CHESTPLATE, 3);
		defense.put(ArmorItem.Type.HELMET, 1);
		defense.put(ArmorItem.Type.BODY, 3);
		return new ArmorMaterial(defense, 15, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.BROWN_WOOL),
				List.of(new ArmorMaterial.Layer(Radiocraft.id("fcc"))), 0.0F, 0.0F);
	});

	/** Leather's durability multiplier: the hat lasts 55 hits, the jacket 80. */
	public static final int FCC_DURABILITY = 5;

	private RadiocraftArmorMaterials() {}
}
