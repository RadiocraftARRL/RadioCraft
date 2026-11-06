package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.qsl.QslCards;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A QSL album (computer batch 3C): holds up to {@link #MAX_CARDS} QSL cards. In an inventory it works like a bundle:
 * right click it with cards on the cursor to put them in, with an empty cursor to take the last one out (or right click
 * a slot of cards while holding the album on the cursor). Right click it in the hand to page through the cards (client:
 * {@code client.screens.QslAlbumScreen}), turn them over, and take one out.
 * <p>
 * Size: a card is at most {@code QslCard.MAX_BYTES} (2.5 KB with its picture), so a full album is at most about
 * 120 KB on the network, a long written book's worth.
 */
public class QslAlbumItem extends Item {

	public static final int MAX_CARDS = 48;

	public QslAlbumItem(Properties properties) {
		super(properties);
	}

	// -------------------- THE CARDS --------------------

	/** The stacks of cards in {@code album}, in order (copies). */
	public static List<ItemStack> cards(ItemStack album) {
		ItemContainerContents contents = album.get(RadiocraftDataComponent.QSL_ALBUM.get());
		List<ItemStack> out = new ArrayList<>();
		if(contents != null)
			contents.nonEmptyItemsCopy().forEach(out::add);
		return out;
	}

	public static int count(ItemStack album) {
		int n = 0;
		for(ItemStack stack : cards(album))
			n += stack.getCount();
		return n;
	}

	private static void store(ItemStack album, List<ItemStack> cards) {
		if(cards.isEmpty())
			album.remove(RadiocraftDataComponent.QSL_ALBUM.get());
		else
			album.set(RadiocraftDataComponent.QSL_ALBUM.get(), ItemContainerContents.fromItems(cards));
	}

	/** Puts as many of {@code cards} (QSL cards only) in as fit, shrinking {@code cards}; answers how many. */
	public static int add(ItemStack album, ItemStack cards) {
		if(cards.isEmpty() || !QslCards.is(cards))
			return 0;
		int moving = Math.min(MAX_CARDS - count(album), cards.getCount());
		if(moving <= 0)
			return 0;
		List<ItemStack> held = cards(album);
		ItemStack moved = cards.copyWithCount(moving);
		moved.remove(RadiocraftDataComponent.QSL_FLIPPED.get()); // Every card goes in front up.
		boolean merged = false;
		for(ItemStack stack : held)
			if(ItemStack.isSameItemSameComponents(stack, moved) && stack.getCount() + moving <= stack.getMaxStackSize()) {
				stack.grow(moving);
				merged = true;
				break;
			}
		if(!merged)
			held.add(moved);
		if(held.size() > 256) // ItemContainerContents' limit; cannot happen under MAX_CARDS.
			return 0;
		store(album, held);
		cards.shrink(moving);
		return moving;
	}

	/** Takes one card out of stack {@code index} (or the last stack for -1); empty if there is none. */
	public static ItemStack take(ItemStack album, int index) {
		List<ItemStack> held = cards(album);
		if(held.isEmpty())
			return ItemStack.EMPTY;
		int at = index < 0 || index >= held.size() ? held.size() - 1 : index;
		ItemStack out = held.get(at).split(1);
		if(held.get(at).isEmpty())
			held.remove(at);
		store(album, held);
		return out;
	}

	// -------------------- IN AN INVENTORY --------------------

	@Override
	public boolean overrideStackedOnOther(@NotNull ItemStack album, @NotNull Slot slot, @NotNull ClickAction action, @NotNull Player player) {
		if(album.getCount() != 1 || action != ClickAction.SECONDARY)
			return false;
		ItemStack inSlot = slot.getItem();
		if(inSlot.isEmpty()) {
			ItemStack out = take(album, -1);
			if(!out.isEmpty()) {
				ItemStack left = slot.safeInsert(out);
				if(!left.isEmpty())
					add(album, left);
				player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.0F);
			}
			return true;
		}
		if(!QslCards.is(inSlot))
			return false;
		int room = MAX_CARDS - count(album);
		if(room > 0) {
			ItemStack taken = slot.safeTake(inSlot.getCount(), room, player);
			add(album, taken);
			if(!taken.isEmpty())
				slot.safeInsert(taken);
			player.playSound(SoundEvents.BOOK_PUT, 0.8F, 1.0F);
		}
		return true;
	}

	@Override
	public boolean overrideOtherStackedOnMe(@NotNull ItemStack album, @NotNull ItemStack other, @NotNull Slot slot, @NotNull ClickAction action,
			@NotNull Player player, @NotNull SlotAccess access) {
		if(album.getCount() != 1 || action != ClickAction.SECONDARY || !slot.allowModification(player))
			return false;
		if(other.isEmpty()) {
			ItemStack out = take(album, -1);
			if(!out.isEmpty()) {
				access.set(out);
				player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.0F);
			}
			return true;
		}
		if(!QslCards.is(other))
			return false;
		if(add(album, other) > 0)
			player.playSound(SoundEvents.BOOK_PUT, 0.8F, 1.0F);
		return true;
	}

	// -------------------- IN THE HAND --------------------

	@Override
	public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if(level.isClientSide)
			com.arrl.radiocraft.client.screens.QslAlbumScreen.open(hand); // A separate class, so the server never loads it.
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.qsl_album", count(stack), MAX_CARDS).withStyle(ChatFormatting.GRAY));
	}
}
