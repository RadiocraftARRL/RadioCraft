package com.arrl.radiocraft.common.events;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

/** Radio things villagers sell. */
@EventBusSubscriber(modid = Radiocraft.MOD_ID)
public class VillagerTradeEvents {

	/**
	 * Journeyman cartographers sell the world map module (twelve emeralds and a compass) and blank commercial licenses;
	 * masters the satellite contracts.
	 */
	@SubscribeEvent
	public static void onVillagerTrades(VillagerTradesEvent event) {
		if(event.getType() != VillagerProfession.CARTOGRAPHER)
			return;
		event.getTrades().get(3).add((trader, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 12),
				java.util.Optional.of(new ItemCost(Items.COMPASS, 1)), new ItemStack(RadiocraftItems.WORLD_MAP_MODULE.get()), 4, 15, 0.2F));
		// Commercial frequency licenses (2026-11-10): frequency coordination is the map maker's trade. Journeymen sell
		// blank VHF and low band licenses, experts the upgrade.
		event.getTrades().get(3).add((trader, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 16),
				java.util.Optional.of(new ItemCost(Items.PAPER, 1)), new ItemStack(RadiocraftItems.BLANK_VHF_LICENSE.get()), 4, 15, 0.2F));
		event.getTrades().get(3).add((trader, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 16),
				java.util.Optional.of(new ItemCost(Items.PAPER, 1)), new ItemStack(RadiocraftItems.BLANK_LOW_BAND_LICENSE.get()), 4, 15, 0.2F));
		event.getTrades().get(4).add((trader, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 32),
				java.util.Optional.of(new ItemCost(Items.COMPASS, 1)), new ItemStack(RadiocraftItems.COMMERCIAL_LICENSE_UPGRADE.get()), 3, 20, 0.2F));
		// Satellite contracts (2026-11-11, doc/SatellitePlan.md): masters sell one of each now and then, 64 emeralds and a
		// diamond, a single use per villager until it restocks.
		event.getTrades().get(5).add((trader, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 64),
				java.util.Optional.of(new ItemCost(Items.DIAMOND, 1)), new ItemStack(RadiocraftItems.AMATEUR_SATELLITE_CONTRACT.get()), 1, 30, 0.05F));
		event.getTrades().get(5).add((trader, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 64),
				java.util.Optional.of(new ItemCost(Items.DIAMOND, 1)), new ItemStack(RadiocraftItems.COMMERCIAL_SATELLITE_CONTRACT.get()), 1, 30, 0.05F));
		// Geostationary contracts (2026-11-12): clearly dearer, 64 emeralds and a block of diamonds (nine), same seller,
		// same single use per restock.
		event.getTrades().get(5).add((trader, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 64),
				java.util.Optional.of(new ItemCost(Items.DIAMOND_BLOCK, 1)), new ItemStack(RadiocraftItems.AMATEUR_GEOSTATIONARY_CONTRACT.get()), 1, 30, 0.05F));
		event.getTrades().get(5).add((trader, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 64),
				java.util.Optional.of(new ItemCost(Items.DIAMOND_BLOCK, 1)), new ItemStack(RadiocraftItems.COMMERCIAL_GEOSTATIONARY_CONTRACT.get()), 1, 30, 0.05F));
	}
}
