package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.datacomponents.HandheldRadioState;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import com.arrl.radiocraft.common.radio.commercial.CommercialRadioModel;
import com.arrl.radiocraft.common.radio.crystal.CrystalQuality;
import com.arrl.radiocraft.common.radio.voice.DigitalVoice;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * A commercial (Part 90) handheld: channelized, programmed by a radio shop (the radio computer's programmer, see
 * {@link com.arrl.radiocraft.common.radio.commercial.CommercialProgramming}), the user only turns the 16-position
 * channel knob and the volume. It is a {@link VHFHandheldItem} underneath (battery, charger, Curios belt, hold right
 * click to talk), but its frequency, tones and offset always come from the channel it is on: the handheld's own state
 * ({@link HandheldRadioState}) is kept in step with the channel by {@link #syncState}, and nothing a client sends can
 * retune it. Its case takes dye like leather armour ({@code #minecraft:dyeable}).
 */
public class CommercialHandheldItem extends VHFHandheldItem {

	/** The case colour when it has not been dyed: radio-shop black. */
	public static final int DEFAULT_CASE_COLOUR = 0xFF2B2D30;

	private final CommercialRadioModel model;

	public CommercialHandheldItem(Properties properties, CommercialRadioModel model) {
		super(properties, CrystalQuality.GOOD); // Synthesized: no crystal to choose.
		this.model = model;
	}

	public CommercialRadioModel getModel() {
		return model;
	}

	/** The model of the commercial handheld {@code stack}, or null if it is not one. */
	@Nullable
	public static CommercialRadioModel modelOf(ItemStack stack) {
		return stack.getItem() instanceof CommercialHandheldItem handheld ? handheld.model : null;
	}

	/** True if {@code stack} is a commercial handheld. */
	public static boolean is(ItemStack stack) {
		return stack.getItem() instanceof CommercialHandheldItem;
	}

	/** The handheld's programming (its factory channels if it has none stored). */
	public static CommercialProgram program(ItemStack stack) {
		CommercialProgram program = stack.get(RadiocraftDataComponent.COMMERCIAL_PROGRAM.value());
		CommercialRadioModel model = modelOf(stack);
		if(program != null)
			return model == null ? program : model.fit(program);
		return model == null ? CommercialProgram.EMPTY : model.factoryProgram();
	}

	/** The channel the knob is on, null on an unprogrammed position (or for any other item). */
	@Nullable
	public static CommercialChannel activeChannel(ItemStack stack) {
		return is(stack) ? program(stack).active() : null;
	}

	/** Stores {@code program} and puts the handheld on the knob's channel. */
	public static void setProgram(ItemStack stack, CommercialProgram program) {
		CommercialRadioModel model = modelOf(stack);
		stack.set(RadiocraftDataComponent.COMMERCIAL_PROGRAM.value(), model == null ? program : model.fit(program));
		syncState(stack);
	}

	/** Selects channel position {@code position} (0-based: zone x 16 + knob on a radio with a zone switch). */
	public static void selectChannel(ItemStack stack, int position) {
		setProgram(stack, program(stack).select(position));
	}

	/**
	 * Copies the knob's channel into the handheld's radio state (receive frequency, tones, offset to the transmit
	 * frequency), so the code that reads a handheld's state (charger, analyzer, meters) sees where it really is.
	 */
	public static void syncState(ItemStack stack) {
		CommercialChannel channel = activeChannel(stack);
		if(channel == null)
			return;
		float tone = channel.analogTone();
		int offset = channel.isReceiveOnly() ? 0 : Math.round(channel.txHz() - channel.rxHz());
		stack.update(RadiocraftDataComponent.HANDHELD_RADIO_STATE_COMPONENT.value(), HandheldRadioState.getDefault(),
				old -> new HandheldRadioState(old.power(), old.ptt(), channel.rxHz(), old.gain(), old.micGain(), old.receiveIndicatorStrength(), tone, offset, tone));
	}

	/** True if the handheld may transmit on the channel it is on: a programmed, not receive-only channel inside its bands. */
	public static boolean mayTransmit(ItemStack stack) {
		CommercialChannel channel = activeChannel(stack);
		CommercialRadioModel model = modelOf(stack);
		return channel != null && model != null && !channel.isReceiveOnly() && model.covers(channel.txHz());
	}

	/** The band its transmissions go out on (so they count as in band), null if it cannot transmit. */
	@Nullable
	public static Band transmitBand(ItemStack stack) {
		CommercialChannel channel = activeChannel(stack);
		CommercialRadioModel model = modelOf(stack);
		return channel == null || model == null ? null : model.bandFor(channel.txHz());
	}

	/** How its transmissions go out: the channel's mode with the radio's unit ID, analog without a channel. */
	public static DigitalVoice.Mode transmitMode(ItemStack stack) {
		CommercialChannel channel = activeChannel(stack);
		return channel == null ? DigitalVoice.Mode.ANALOG : channel.mode().withUnit(program(stack).unitId());
	}

	/** Its receiver's digital decoder on the knob's channel, null on analog (or for any other item). */
	@Nullable
	public static DigitalVoice.Decoder decoder(ItemStack stack) {
		CommercialChannel channel = activeChannel(stack);
		return channel == null ? null : channel.decoder();
	}

	/** Sneak and use opens the radio's top panel (knob, volume, LEDs); use without sneaking talks, as on the ham handheld. */
	@Override
	public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
		ItemStack item = player.getItemInHand(hand);
		if(hand == InteractionHand.MAIN_HAND && player.isCrouching()) {
			if(level.isClientSide())
				com.arrl.radiocraft.client.screens.radios.CommercialHandheldScreen.open(player.getInventory().selected); // A separate class: the server never loads it.
			return InteractionResultHolder.success(item);
		}
		return super.use(level, player, hand);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		CommercialProgram program = program(stack);
		CommercialChannel channel = program.active();
		String name = channel == null ? "-" : channel.name().isEmpty() ? String.format(Locale.ROOT, "%.4f MHz", channel.rxHz() / 1e6) : channel.name();
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "commercial.channel"), program.selected() + 1, name).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "commercial.programmed"), program.programmedCount(), model.channelCount())
				.withStyle(ChatFormatting.DARK_GRAY));
		if(program.unitId() > 0)
			tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "commercial.unit_id"), program.unitId()).withStyle(ChatFormatting.DARK_GRAY));
	}
}
