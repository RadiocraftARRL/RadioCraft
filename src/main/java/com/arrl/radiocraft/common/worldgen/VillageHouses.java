package com.arrl.radiocraft.common.worldgen;

import com.arrl.radiocraft.Radiocraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Puts the ham shack (1.12 step 8) into villages: on server start it adds {@code radiocraft:village/ham_shack_<type>}
 * to each vanilla village type's house pool ({@code minecraft:village/<type>/houses}) with weight {@link #WEIGHT}, out
 * of 70-90 there, so roughly one village in six or seven has one. The templates have a doorstep jigsaw like the
 * vanilla houses' and their own villager; {@link RotateBlocksProcessor} turns the radio and its wires with the house.
 * <p>
 * Vanilla offers no way to add to a pool, and NeoForge none either, so this adds the element to the pool's list of
 * weighted templates by reflection (the field is {@code templates}; the game runs with Mojang names). Only the pools
 * of this server's registries are touched, every start, so nothing is saved and a datapack's own house pools stay as
 * they are. If the field cannot be found it logs a warning and the shacks are only found as radio sites.
 */
public final class VillageHouses {

	/** The village types and their house pools. */
	public static final List<String> TYPES = List.of("plains", "taiga", "snowy", "savanna", "desert");
	/** Weight of the shack in each house pool (a vanilla small house is 1-4). */
	public static final int WEIGHT = 1;
	public static final ResourceKey<StructureProcessorList> PROCESSORS = ResourceKey.create(Registries.PROCESSOR_LIST, Radiocraft.id("ham_shack_village"));

	private VillageHouses() {}

	/** The template for village type {@code type}. */
	public static ResourceLocation template(String type) {
		return Radiocraft.id("village/ham_shack_" + type);
	}

	public static ResourceKey<StructureTemplatePool> housePool(String type) {
		return ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.withDefaultNamespace("village/" + type + "/houses"));
	}

	public static void onServerAboutToStart(ServerAboutToStartEvent event) {
		add(event.getServer().registryAccess());
	}

	/** Adds the shacks to the house pools in {@code access} (once: a second call finds them there). Returns how many pools took one. */
	public static int add(RegistryAccess access) {
		Registry<StructureTemplatePool> pools = access.registryOrThrow(Registries.TEMPLATE_POOL);
		Holder<StructureProcessorList> processors = access.registryOrThrow(Registries.PROCESSOR_LIST).getHolder(PROCESSORS).orElse(null);
		if(processors == null) {
			Radiocraft.LOGGER.warn("No processor list {}: no ham shacks in villages", PROCESSORS.location());
			return 0;
		}
		Field field = templatesField();
		if(field == null)
			return 0;
		int added = 0;
		for(String type : TYPES) {
			StructureTemplatePool pool = pools.get(housePool(type));
			if(pool == null)
				continue;
			try {
				@SuppressWarnings("unchecked")
				List<StructurePoolElement> templates = (List<StructurePoolElement>)field.get(pool);
				if(contains(templates, template(type)))
					continue;
				StructurePoolElement shack = StructurePoolElement.legacy(template(type).toString(), processors).apply(StructureTemplatePool.Projection.RIGID);
				for(int i = 0; i < WEIGHT; i++)
					templates.add(shack);
				added++;
			}
			catch(IllegalAccessException | ClassCastException | UnsupportedOperationException e) {
				Radiocraft.LOGGER.warn("Could not add the ham shack to {}", housePool(type).location(), e);
			}
		}
		return added;
	}

	/** True if {@code templates} has the shack {@code template} already. */
	public static boolean contains(List<StructurePoolElement> templates, ResourceLocation template) {
		String id = template.toString();
		for(StructurePoolElement element : templates)
			if(element.toString().contains(id))
				return true;
		return false;
	}

	/** The pool's weighted templates (for the GameTests). */
	@SuppressWarnings("unchecked")
	public static List<StructurePoolElement> templates(StructureTemplatePool pool) {
		Field field = templatesField();
		try {
			return field == null ? List.of() : (List<StructurePoolElement>)field.get(pool);
		}
		catch(IllegalAccessException e) {
			return List.of();
		}
	}

	private static Field templatesField() {
		try {
			Field field = StructureTemplatePool.class.getDeclaredField("templates");
			field.setAccessible(true);
			return field;
		}
		catch(NoSuchFieldException | RuntimeException e) {
			Radiocraft.LOGGER.warn("No StructureTemplatePool.templates field: no ham shacks in villages", e);
			return null;
		}
	}
}
