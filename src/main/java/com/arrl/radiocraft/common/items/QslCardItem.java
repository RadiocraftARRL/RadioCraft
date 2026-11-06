package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.common.qsl.QslCard;
import com.arrl.radiocraft.common.qsl.QslCards;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A QSL card (computer batch 3C), printed by the radio computer's scanner from a QSL Designer template, never crafted.
 * Right click a card nobody has signed to fill it in (a form like a book and quill's: to, band, frequency, mode,
 * report, notes; the date and time go in by themselves) and sign it, which locks it. Right click a signed card (or
 * sneak and right click any card) to turn it over. It shows in the hand like a map and in an item frame by its front
 * (client: {@code client.qsl.QslCardRenderer}). Identical blank cards stack.
 */
public class QslCardItem extends Item {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

	public QslCardItem(Properties properties) {
		super(properties);
	}

	@Override
	public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		QslCard card = QslCards.card(stack);
		if(card == null)
			return InteractionResultHolder.pass(stack);
		if(card.signed() || player.isShiftKeyDown()) {
			if(!level.isClientSide)
				QslCards.flip(stack);
			player.playSound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 0.6F, 1.3F);
		}
		else if(level.isClientSide)
			com.arrl.radiocraft.client.screens.QslFillScreen.open(hand); // A separate class, so the server never loads it.
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		QslCard card = QslCards.card(stack);
		if(card != null)
			tooltip.addAll(lines(card));
	}

	/** The tooltip: from, to, date and band (and mode), or what to do with a blank one. */
	public static List<Component> lines(QslCard card) {
		List<Component> out = new ArrayList<>();
		String key = "tooltip.radiocraft.qsl_card.";
		if(!card.from().isEmpty())
			out.add(Component.translatable(key + "from", card.from()).withStyle(ChatFormatting.GRAY));
		QslCard.Fill fill = card.fill();
		if(card.blank()) {
			out.add(Component.translatable(key + "blank").withStyle(ChatFormatting.GRAY));
			return out;
		}
		if(!fill.to().isEmpty())
			out.add(Component.translatable(key + "to", fill.to()).withStyle(ChatFormatting.GRAY));
		if(fill.utc() != 0L)
			out.add(Component.translatable(key + "date", DATE.format(Instant.ofEpochMilli(fill.utc()))).withStyle(ChatFormatting.GRAY));
		if(!fill.band().isEmpty() || !fill.mode().isEmpty())
			out.add(Component.translatable(key + "band", fill.band(), fill.mode()).withStyle(ChatFormatting.GRAY));
		out.add(fill.signed() ? Component.translatable(key + "signed", fill.signer()).withStyle(ChatFormatting.DARK_AQUA)
				: Component.translatable(key + "unsigned").withStyle(ChatFormatting.YELLOW));
		if(card.copy())
			out.add(Component.translatable(key + "copy").withStyle(ChatFormatting.RED));
		return out;
	}
}
