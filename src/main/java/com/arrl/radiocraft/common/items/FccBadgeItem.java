package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.datacomponents.FccBadge;
import com.arrl.radiocraft.common.fcc.FccBadges;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The FCC field agent's badge (1.12). Not craftable: an operator issues it with {@code /radiocraft fcc badge <player>},
 * which stamps the agent's name and badge number on it ({@link FccBadge}). Right click shows it to the players within
 * {@link FccBadges#SHOW_RANGE} blocks; a badge shown by someone it was not issued to says whose it is. With Curios it is
 * worn in the body (chest) slot ({@code data/curios/tags/item/body.json}); without, it works from the hand or offhand.
 */
public class FccBadgeItem extends Item {

	public FccBadgeItem(Properties properties) {
		super(properties);
	}

	@Override
	public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if(!level.isClientSide) {
			Component shown = shownMessage(player, stack.get(RadiocraftDataComponent.FCC_BADGE.value()));
			double range = FccBadges.SHOW_RANGE * FccBadges.SHOW_RANGE;
			for(Player other : level.players())
				if(other.distanceToSqr(player) <= range)
					other.sendSystemMessage(shown);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
		}
		player.getCooldowns().addCooldown(this, FccBadges.SHOW_COOLDOWN);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	/** What the players nearby read when {@code holder} shows a badge carrying {@code badge} (null: a blank). */
	public static Component shownMessage(Player holder, FccBadge badge) {
		Component who = holder.getDisplayName();
		if(badge == null)
			return Component.translatable(Radiocraft.translationKey("message", "fcc_badge.shown_blank"), who).withStyle(ChatFormatting.GRAY);
		if(FccBadges.isOwnBadge(badge.agentId(), holder.getUUID()))
			return Component.translatable(Radiocraft.translationKey("message", "fcc_badge.shown"), who, badge.number()).withStyle(ChatFormatting.GOLD);
		return Component.translatable(Radiocraft.translationKey("message", "fcc_badge.shown_other"), who, badge.agent(), badge.number()).withStyle(ChatFormatting.RED);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		FccBadge badge = stack.get(RadiocraftDataComponent.FCC_BADGE.value());
		if(badge == null) {
			tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "fcc_badge.blank")).withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "fcc_badge.agent"), badge.agent()).withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "fcc_badge.number"), badge.number()).withStyle(ChatFormatting.GOLD));
	}
}
