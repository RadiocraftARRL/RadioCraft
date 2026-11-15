package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicense;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenses;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import java.util.Random;

/**
 * A blank commercial frequency license (2026-11-10), as a cartographer sells it, for commercial VHF or low band.
 * Right-clicked, it becomes the player's license: a random free frequency in its band, a new WQ callsign, their name
 * and today's date ({@link CommercialLicenses#issue}). Refused when the server's licensing mode is ADMIN or OFF.
 */
public class BlankLicenseItem extends Item {

	private final String band;

	public BlankLicenseItem(String band, Properties properties) {
		super(properties);
		this.band = band;
	}

	public String band() {
		return band;
	}

	@Override
	public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if(!(level instanceof ServerLevel server))
			return InteractionResultHolder.sidedSuccess(stack, true);
		RadiocraftServerConfig.LicensingMode mode = RadiocraftServerConfig.COMMERCIAL_LICENSING.get();
		if(mode != RadiocraftServerConfig.LicensingMode.VILLAGER) {
			player.displayClientMessage(Component.translatable(Radiocraft.translationKey("message",
					mode == RadiocraftServerConfig.LicensingMode.OFF ? "license.off" : "license.admin_only")).withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(stack);
		}
		CommercialLicense license = CommercialLicenses.of(server.getServer()).issue(server.getServer(), band,
				player.getGameProfile().getName(), player.getStringUUID(), 0, new Random(server.random.nextLong()));
		if(license == null) {
			player.displayClientMessage(Component.translatable(Radiocraft.translationKey("message", "license.band_full")).withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(stack);
		}
		ItemStack granted = CommercialLicenseItem.make(server, license);
		level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.sendSystemMessage(Component.translatable(Radiocraft.translationKey("message", "license.granted"),
				license.callsign(), CommercialLicenseRules.mhz(license.outputHz()), license.subscribers()));
		stack.consume(1, player);
		if(stack.isEmpty())
			return InteractionResultHolder.success(granted);
		if(!player.getInventory().add(granted))
			player.drop(granted, false);
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "blank_license")).withStyle(ChatFormatting.GRAY));
	}
}
