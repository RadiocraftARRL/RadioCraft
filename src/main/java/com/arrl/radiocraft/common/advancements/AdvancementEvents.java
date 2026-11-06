package com.arrl.radiocraft.common.advancements;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.VHFRepeaterBlockEntity;
import com.arrl.radiocraft.common.blockentities.WeatherAlertGeneratorBlockEntity;
import com.arrl.radiocraft.common.items.QslAlbumItem;
import com.arrl.radiocraft.common.qsl.QslCard;
import com.arrl.radiocraft.common.qsl.QslCards;
import com.arrl.radiocraft.common.radio.Callsigns;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.UUID;

/**
 * The game events the advancements listen to (the advancements batch): who placed a repeater or a Weather Alert
 * Generator (Repeater Owner, Storm Spotter), and what QSL cards a player holds (Paper Trail for the signer of a card
 * someone else picks up or holds, Card Collector for an album of {@link RadiocraftAdvancements#ALBUM_CARDS}). Cards
 * are looked at when one is picked up, when a container screen closes (a chest, the fax machine's tray) and at login:
 * never every tick.
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID)
public final class AdvancementEvents {

	private AdvancementEvents() {}

	@SubscribeEvent
	public static void onPlaced(BlockEvent.EntityPlaceEvent event) {
		if(!(event.getEntity() instanceof ServerPlayer player))
			return;
		BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
		if(be instanceof VHFRepeaterBlockEntity repeater)
			repeater.setPlacer(player.getUUID());
		else if(be instanceof WeatherAlertGeneratorBlockEntity generator)
			generator.setPlacer(player.getUUID());
	}

	@SubscribeEvent
	public static void onPickup(ItemEntityPickupEvent.Post event) {
		if(event.getPlayer() instanceof ServerPlayer player)
			holding(player, event.getOriginalStack());
	}

	@SubscribeEvent
	public static void onContainerClosed(PlayerContainerEvent.Close event) {
		if(event.getEntity() instanceof ServerPlayer player)
			checkInventory(player);
	}

	@SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW)
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if(!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null)
			return;
		String call = Callsigns.playerCallsign(player.getServer(), player.getUUID());
		if(call != null && !call.isBlank()) // A callsign from before the advancements.
			RadiocraftAdvancements.grant(player, RadiocraftAdvancements.LICENSED_TO_CHILL);
		checkInventory(player);
	}

	/** Looks at every QSL card and album {@code player} carries. */
	public static void checkInventory(ServerPlayer player) {
		for(ItemStack stack : player.getInventory().items)
			holding(player, stack);
		for(ItemStack stack : player.getInventory().offhand)
			holding(player, stack);
	}

	/** {@code player} has {@code stack}: a QSL card signed by someone else, or an album (with its cards). */
	public static void holding(ServerPlayer player, ItemStack stack) {
		if(stack.isEmpty())
			return;
		if(stack.getItem() instanceof QslAlbumItem) {
			if(QslAlbumItem.count(stack) >= RadiocraftAdvancements.ALBUM_CARDS)
				RadiocraftAdvancements.grant(player, RadiocraftAdvancements.CARD_COLLECTOR);
			for(ItemStack card : QslAlbumItem.cards(stack))
				card(player, card);
		}
		else
			card(player, stack);
	}

	/** Paper Trail: {@code player} holds a card signed by another player, who gets the advancement (now or at login). */
	private static void card(ServerPlayer player, ItemStack stack) {
		QslCard card = QslCards.card(stack);
		if(card == null || !card.signed())
			return;
		String signer = card.fill().signer();
		if(signer.isBlank() || signer.equalsIgnoreCase(player.getGameProfile().getName()))
			return;
		MinecraftServer server = player.getServer();
		UUID id = server == null ? null : RadiocraftAdvancements.playerNamed(server, signer);
		if(id == null || id.equals(player.getUUID()))
			return;
		ServerPlayer online = RadiocraftAdvancements.player(server, id);
		if(online != null ? !RadiocraftAdvancements.has(online, RadiocraftAdvancements.PAPER_TRAIL) : !AdvancementData.of(server).isPending(id, RadiocraftAdvancements.PAPER_TRAIL))
			RadiocraftAdvancements.grant(server, id, RadiocraftAdvancements.PAPER_TRAIL);
	}
}
