package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.MarineRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.VHFBaseStationBlockEntity;
import com.arrl.radiocraft.common.datacomponents.CloneImage;
import com.arrl.radiocraft.common.datacomponents.HandheldMemory;
import com.arrl.radiocraft.common.datacomponents.HandheldRadioState;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgramming;
import com.arrl.radiocraft.common.radio.commercial.CommercialRadioModel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Radio Cloner (1.12 playtest fixes 2): a radio shop's cloning box on a cable. Used on a radio while empty it reads
 * the radio's programming; loaded, it writes that programming into every radio it is used on after, as many as wanted;
 * sneak-used in the air it is cleared. A radio is a placed block (right click it: a commercial mobile, a VHF base
 * station) or a handheld in the player's other hand (use the cloner in the air). Sneak-using it on a radio reads that
 * radio whether or not the cloner is loaded.
 * <ul>
 * <li>Commercial radios (handhelds, mobiles placed or as items) clone to commercial radios: channels and contact list,
 * through {@link CommercialProgramming}'s checks, so a model that cannot take a channel (a band it does not cover,
 * digital on the analog brick) refuses the clone with the reason; channels past a smaller radio's capacity are left out
 * and said so. Keys the source marks as not readable are left behind: those channels arrive clear. Unit IDs are never
 * copied (each radio keeps its own).</li>
 * <li>2m ham radios (VHF handhelds, VHF base stations) clone to ham radios: the dial (frequency, tones, offset) and,
 * from a handheld to a handheld, the ten memory channels. Base stations have no memories, so only the dial goes.</li>
 * <li>Marine radios are not cloned: their channels are the fixed marine table.</li>
 * </ul>
 */
public class RadioClonerItem extends Item {

	public RadioClonerItem(Properties properties) {
		super(properties);
	}

	/** What a clone did: worked or not, and the message to show (under {@code message.radiocraft.cloner.}). */
	public record Outcome(boolean ok, MutableComponent message) {}

	/** What the cloner {@code stack} holds, or null if it is empty. */
	@Nullable
	public static CloneImage image(ItemStack stack) {
		return stack.get(RadiocraftDataComponent.CLONE_IMAGE.value());
	}

	// -------------------- USE --------------------

	/** In the air: sneaking clears it; else it reads or writes the radio in the other hand. */
	@Override
	public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
		ItemStack cloner = player.getItemInHand(hand);
		if(level.isClientSide)
			return InteractionResultHolder.success(cloner);
		if(player.isShiftKeyDown()) {
			boolean had = image(cloner) != null;
			cloner.remove(RadiocraftDataComponent.CLONE_IMAGE.value());
			tell(player, new Outcome(had, message(had ? "cleared" : "empty")), had ? SoundEvents.UI_BUTTON_CLICK.value() : null);
			return InteractionResultHolder.success(cloner);
		}
		ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		Outcome outcome = other.isEmpty() ? new Outcome(false, message("no_radio")) : useOn(cloner, other, false);
		tell(player, outcome, null);
		return InteractionResultHolder.success(cloner);
	}

	/** On a block, before the block's own use (which would open the radio's screen): a radio block is read or written. */
	@Override
	public @NotNull InteractionResult onItemUseFirst(@NotNull ItemStack stack, @NotNull UseOnContext context) {
		Level level = context.getLevel();
		BlockEntity be = level.getBlockEntity(context.getClickedPos());
		if(!(be instanceof CommercialMobileBlockEntity || be instanceof VHFBaseStationBlockEntity || be instanceof MarineRadioBlockEntity))
			return InteractionResult.PASS;
		Player player = context.getPlayer();
		if(!level.isClientSide && player != null)
			tell(player, useOn(stack, level, context.getClickedPos(), player.isShiftKeyDown()), null);
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	private static void tell(Player player, Outcome outcome, @Nullable net.minecraft.sounds.SoundEvent sound) {
		player.displayClientMessage(outcome.message().withStyle(outcome.ok() ? ChatFormatting.GREEN : ChatFormatting.RED), true);
		net.minecraft.sounds.SoundEvent played = sound != null ? sound : outcome.ok() ? SoundEvents.NOTE_BLOCK_BIT.value() : SoundEvents.NOTE_BLOCK_BASS.value();
		player.level().playSound(null, player.blockPosition(), played, SoundSource.PLAYERS, 0.5F, outcome.ok() ? 1.8F : 0.6F);
	}

	// -------------------- READ OR WRITE (server; public for tests) --------------------

	/** The cloner {@code cloner} used on the radio item {@code radio}: read if empty (or {@code read}), else write. */
	public static Outcome useOn(ItemStack cloner, ItemStack radio, boolean read) {
		CloneImage image = image(cloner);
		Component name = radio.getHoverName();
		if(radio.getItem() instanceof MarineHandheldItem)
			return new Outcome(false, message("marine"));
		CommercialProgramming.Target commercial = CommercialProgramming.of(radio);
		boolean ham = radio.getItem() instanceof VHFHandheldItem && !VHFHandheldItem.isChannelized(radio);
		if(commercial == null && !ham)
			return new Outcome(false, message("unsupported", name));
		if(image == null || read) {
			CloneImage made = commercial != null ? readCommercial(radio.getItem().getDescriptionId(), commercial) : readHandheld(radio);
			cloner.set(RadiocraftDataComponent.CLONE_IMAGE.value(), made);
			return readOutcome(made, name);
		}
		if(commercial != null)
			return writeCommercial(image, commercial, name);
		if(!image.isHam())
			return incompatible(image, name);
		writeHandheld(image, radio);
		return new Outcome(true, message("written_ham", name, mhz(image.dial().frequency()), image.hasMemories() ? image.memory().channels().size() : 0));
	}

	/** The cloner used on the radio block at {@code pos}: read if empty (or {@code read}), else write. */
	public static Outcome useOn(ItemStack cloner, Level level, BlockPos pos, boolean read) {
		CloneImage image = image(cloner);
		BlockEntity be = level.getBlockEntity(pos);
		if(be instanceof MarineRadioBlockEntity)
			return new Outcome(false, message("marine"));
		Component name = be == null ? Component.empty() : Component.translatable(be.getBlockState().getBlock().getDescriptionId());
		CommercialProgramming.Target commercial = CommercialProgramming.of(level, pos);
		VHFBaseStationBlockEntity base = be instanceof VHFBaseStationBlockEntity station ? station : null;
		if(commercial == null && base == null)
			return new Outcome(false, message("unsupported", name));
		if(image == null || read) {
			CloneImage made = commercial != null ? readCommercial(be.getBlockState().getBlock().getDescriptionId(), commercial) : readBase(base);
			cloner.set(RadiocraftDataComponent.CLONE_IMAGE.value(), made);
			return readOutcome(made, name);
		}
		if(commercial != null)
			return writeCommercial(image, commercial, name);
		if(!image.isHam())
			return incompatible(image, name);
		base.setFrequency(image.dial().frequency());
		base.updateFrequency(0); // Onto the radio's tuning steps and range, saved and synced.
		base.setCtcssTone(image.dial().ctcssTone());
		base.setOffsetHz(image.dial().offsetHz());
		return new Outcome(true, message("written_ham", name, mhz(image.dial().frequency()), 0));
	}

	/** A commercial radio's channels and contacts, unreadable keys left behind, no unit ID. */
	public static CloneImage readCommercial(String source, CommercialProgramming.Target target) {
		List<CommercialChannel> channels = CommercialProgramming.readForClone(target);
		CommercialProgram program = CommercialProgram.EMPTY.withChannels(channels).withContacts(target.program().contacts());
		return CloneImage.commercial(source, target.model().id(), program, CommercialProgramming.hiddenKeys(target));
	}

	private static CloneImage readHandheld(ItemStack radio) {
		HandheldRadioState state = radio.getOrDefault(RadiocraftDataComponent.HANDHELD_RADIO_STATE_COMPONENT.value(), HandheldRadioState.getDefault());
		HandheldMemory memory = radio.getOrDefault(RadiocraftDataComponent.HANDHELD_MEMORY.value(), HandheldMemory.EMPTY);
		return CloneImage.ham(radio.getItem().getDescriptionId(), HandheldMemory.Channel.of(0, state), memory.leaveMemory(), true);
	}

	private static CloneImage readBase(VHFBaseStationBlockEntity base) {
		HandheldMemory.Channel dial = new HandheldMemory.Channel(0, base.getFrequency(), base.getCtcssTone(), base.getCtcssTone(), base.getOffsetHz());
		return CloneImage.ham(base.getBlockState().getBlock().getDescriptionId(), dial, HandheldMemory.EMPTY, false);
	}

	private static Outcome readOutcome(CloneImage made, Component name) {
		if(made.isHam())
			return new Outcome(true, message("read_ham", name, mhz(made.dial().frequency()), made.hasMemories() ? made.memory().channels().size() : 0));
		if(made.keysLeft() > 0)
			return new Outcome(true, message("read_keys", name, made.channelCount(), made.keysLeft()));
		return new Outcome(true, message("read", name, made.channelCount()));
	}

	/**
	 * Writes a commercial image into {@code target}: what fits its capacity, all or nothing through
	 * {@link CommercialProgramming#write} (the target keeps its unit ID; the contacts are the image's).
	 */
	public static Outcome writeCommercial(CloneImage image, CommercialProgramming.Target target, Component name) {
		if(!image.isCommercial())
			return incompatible(image, name);
		List<CommercialChannel> all = image.program().channels();
		int capacity = target.model().channelCount();
		int cut = 0;
		for(int i = capacity; i < all.size(); i++)
			if(!all.get(i).isBlank())
				cut++;
		List<CommercialChannel> fit = new ArrayList<>(all.subList(0, Math.min(capacity, all.size())));
		CommercialProgramming.Result result = CommercialProgramming.write(target, fit, 0, image.program().contacts());
		if(!result.ok()) {
			CommercialProgramming.Problem first = result.problems().getFirst();
			return new Outcome(false, message("refused", name, first.position() + 1,
					Component.translatable(Radiocraft.translationKey("message", "commercial." + first.key()))));
		}
		int written = target.program().programmedCount();
		return cut > 0 ? new Outcome(true, message("written_cut", name, written, cut, capacity)) : new Outcome(true, message("written", name, written));
	}

	private static void writeHandheld(CloneImage image, ItemStack radio) {
		HandheldMemory.Channel dial = image.dial();
		radio.update(RadiocraftDataComponent.HANDHELD_RADIO_STATE_COMPONENT.value(), HandheldRadioState.getDefault(),
				old -> new HandheldRadioState(old.power(), old.ptt(), dial.frequency(), old.gain(), old.micGain(), old.receiveIndicatorStrength(),
						dial.ctcssTone(), dial.offsetHz(), dial.rxTone()));
		if(image.hasMemories())
			radio.set(RadiocraftDataComponent.HANDHELD_MEMORY.value(), image.memory().leaveMemory().sanitized());
	}

	private static Outcome incompatible(CloneImage image, Component name) {
		return new Outcome(false, message("incompatible", name, Component.translatable(Radiocraft.translationKey("tooltip", "cloner.family." + image.family()))));
	}

	private static String mhz(float hz) {
		return String.format(Locale.ROOT, "%.4f", hz / 1e6);
	}

	/** {@code message.radiocraft.cloner.<key>}. */
	static MutableComponent message(String key, Object... args) {
		return Component.translatable(Radiocraft.translationKey("message", "cloner." + key), args);
	}

	// -------------------- TOOLTIP --------------------

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		CloneImage image = image(stack);
		if(image == null)
			tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "cloner.empty")).withStyle(ChatFormatting.GRAY));
		else {
			Component family = Component.translatable(Radiocraft.translationKey("tooltip", "cloner.family." + image.family()));
			CommercialRadioModel model = CommercialRadioModel.byId(image.model());
			Component modelName = model == null ? family : Component.translatable(Radiocraft.translationKey("tooltip", "cloner.model." + model.id()));
			tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "cloner.holds"), modelName, Component.translatable(image.source()))
					.withStyle(ChatFormatting.GRAY));
			if(image.isCommercial()) {
				tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "cloner.channels"), image.channelCount(), image.program().contacts().size())
						.withStyle(ChatFormatting.GRAY));
				if(image.keysLeft() > 0)
					tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "cloner.keys_left"), image.keysLeft()).withStyle(ChatFormatting.DARK_GRAY));
			}
			else
				tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "cloner.dial"), mhz(image.dial().frequency()),
						image.hasMemories() ? image.memory().channels().size() : 0).withStyle(ChatFormatting.GRAY));
		}
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "cloner.usage")).withStyle(ChatFormatting.DARK_GRAY));
	}

	@Override
	public boolean isFoil(@NotNull ItemStack stack) {
		return image(stack) != null; // Loaded.
	}
}
