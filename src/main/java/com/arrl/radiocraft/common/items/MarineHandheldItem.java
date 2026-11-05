package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.datacomponents.HandheldRadioState;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.radio.MarineChannels;
import com.arrl.radiocraft.common.radio.crystal.CrystalQuality;
import com.arrl.radiocraft.common.radio.marine.MarineRadioState;
import com.arrl.radiocraft.common.radio.marine.MarineRadios;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A VHF marine handheld: channel-only (the marine channel table, {@link MarineChannels}), 5 W or 1 W, a 16/9 key and
 * dual watch. It is a {@link VHFHandheldItem} underneath (battery, charger, Curios belt, hold right click to talk), but
 * its frequencies always come from the channel it is on: the handheld's own state ({@link HandheldRadioState}) is
 * kept in step with the channel by {@link #syncState}, and nothing a client sends can retune it. Its signals go out on
 * commercial VHF, which holds the marine band, so they are in band (no MARS splatter).
 */
public class MarineHandheldItem extends VHFHandheldItem {

	public MarineHandheldItem(Properties properties) {
		super(properties, CrystalQuality.GOOD); // Synthesized: no crystal to choose.
	}

	/** True if {@code stack} is a marine handheld. */
	public static boolean is(ItemStack stack) {
		return stack.getItem() instanceof MarineHandheldItem;
	}

	/** The handheld's settings (a fresh radio's if it has none stored). */
	public static MarineRadioState state(ItemStack stack) {
		MarineRadioState state = stack.get(RadiocraftDataComponent.MARINE_RADIO.value());
		return state == null ? MarineRadioState.DEFAULT : state;
	}

	/** Stores {@code state} and puts the handheld on its channel. */
	public static void setState(ItemStack stack, MarineRadioState state) {
		stack.set(RadiocraftDataComponent.MARINE_RADIO.value(), state);
		syncState(stack);
	}

	/** The channel it is on, in the plan in force. */
	public static MarineChannels.Channel channel(ItemStack stack) {
		return state(stack).channel(MarineRadios.region());
	}

	/**
	 * Copies the channel into the handheld's radio state (receive frequency, no tones, the duplex offset to the ship
	 * frequency), so the code that reads a handheld's state (charger, analyzer, meters) sees where it really is.
	 */
	public static void syncState(ItemStack stack) {
		if(!is(stack))
			return;
		MarineRadioState state = state(stack);
		MarineChannels.Region region = MarineRadios.region();
		float rx = (float)state.receiveHz(region);
		int offset = state.mayTransmit(region) ? (int)Math.round(state.transmitHz(region) - state.receiveHz(region)) : 0;
		stack.update(RadiocraftDataComponent.HANDHELD_RADIO_STATE_COMPONENT.value(), HandheldRadioState.getDefault(),
				old -> new HandheldRadioState(old.power(), old.ptt(), rx, old.gain(), old.micGain(), old.receiveIndicatorStrength(), 0.0F, offset, 0.0F));
	}

	/** True if the handheld keys up on the channel it is on (not a weather channel, not DSC channel 70). */
	public static boolean mayTransmit(ItemStack stack) {
		return is(stack) && state(stack).mayTransmit(MarineRadios.region());
	}

	/** Its transmit power against a standard handheld's 1: 5 W, or 1 W on LO (and on the 1 W channels). */
	public static float transmitPower(ItemStack stack) {
		return state(stack).handheldPower(MarineRadios.region());
	}

	/** Sneak and use opens the radio's front panel (channel, 16/9, HI/LO, DW, volume); use without sneaking talks. */
	@Override
	public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
		ItemStack item = player.getItemInHand(hand);
		if(hand == InteractionHand.MAIN_HAND && player.isCrouching()) {
			if(level.isClientSide())
				com.arrl.radiocraft.client.screens.radios.MarineHandheldScreen.open(player.getInventory().selected); // A separate class: the server never loads it.
			return InteractionResultHolder.success(item);
		}
		return super.use(level, player, hand);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		MarineRadioState state = state(stack);
		MarineChannels.Region region = MarineRadios.region();
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "marine.channel"), state.displayLine(region), state.channel(region).use())
				.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", state.transmitsHigh(region) ? "marine.high_handheld" : "marine.low"))
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
