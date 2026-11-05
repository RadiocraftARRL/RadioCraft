package com.arrl.radiocraft.common.fcc;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.datacomponents.FccBadge;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;

/**
 * The FCC field gear (1.12): the FCC seal banner pattern (data-driven, {@code data/radiocraft/banner_pattern/fcc.json},
 * put on a banner at a loom with the FCC Banner Pattern item), the ready-made FCC banner, and the FCC badge, which only
 * an operator can hand out ({@code /radiocraft fcc badge}). The hat and jacket are plain armour
 * ({@link com.arrl.radiocraft.common.init.RadiocraftArmorMaterials#FCC}).
 */
public final class FccGear {

	/** The FCC seal banner pattern. */
	public static final ResourceKey<BannerPattern> PATTERN = ResourceKey.create(Registries.BANNER_PATTERN, Radiocraft.id("fcc"));
	/** The patterns the FCC Banner Pattern item puts on at a loom (just {@link #PATTERN}). */
	public static final TagKey<BannerPattern> PATTERN_ITEM = TagKey.create(Registries.BANNER_PATTERN, Radiocraft.id("pattern_item/fcc"));

	private FccGear() {}

	/**
	 * The ready-made FCC banner: a blue banner with the white seal and a yellow border, named "FCC Banner". Empty when
	 * the pattern is not in {@code registries} (a datapack took it out).
	 */
	public static ItemStack banner(HolderLookup.Provider registries) {
		HolderLookup.RegistryLookup<BannerPattern> patterns = registries.lookupOrThrow(Registries.BANNER_PATTERN);
		var seal = patterns.get(PATTERN);
		if(seal.isEmpty())
			return ItemStack.EMPTY;
		BannerPatternLayers.Builder layers = new BannerPatternLayers.Builder();
		patterns.get(BannerPatterns.BORDER).ifPresent(border -> layers.add(border, DyeColor.YELLOW));
		layers.add(seal.get(), DyeColor.WHITE);
		ItemStack banner = new ItemStack(Items.BLUE_BANNER);
		banner.set(DataComponents.BANNER_PATTERNS, layers.build());
		banner.set(DataComponents.ITEM_NAME, Component.translatable(Radiocraft.translationKey("item", "fcc_banner")));
		return banner;
	}

	/** A badge issued to {@code agent}: their name and UUID on it, and their badge number. */
	public static ItemStack badgeFor(Player agent) {
		ItemStack badge = new ItemStack(RadiocraftItems.FCC_BADGE.get());
		badge.set(RadiocraftDataComponent.FCC_BADGE.value(), FccBadge.issuedTo(agent.getGameProfile().getName(), agent.getUUID()));
		return badge;
	}

	/** Gives {@code agent} a badge, into their inventory or dropped at their feet when it is full. */
	public static void issueBadge(Player agent) {
		ItemStack badge = badgeFor(agent);
		if(!agent.getInventory().add(badge))
			agent.drop(badge, false);
	}
}
