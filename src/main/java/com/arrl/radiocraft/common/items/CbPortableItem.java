package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.CommonConfig;
import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.radio.cb.CbRadioState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The TRC-217 style CB portable (2026-11-08; see {@link com.arrl.radiocraft.common.radio.voice.handheld.CbPortableRadio}):
 * 40 AM channels, 5 W, no licence needed. Hold right click with it in hand to talk; sneak and use opens its front
 * (channel knob on top, volume with the OFF click, squelch). Turned on, its whip pulls out to about three blocks
 * (the item model's {@code radiocraft:extended} property). Its cells are the {@code radiocraft:cb_energy} component,
 * as big as a small battery's: it draws a receiving handheld's current listening and only a little more on the air
 * (5 W). Sneak and use with a small battery in the other hand swaps their charge.
 */
public class CbPortableItem extends Item {

	/** Ticks a full set of cells lasts: switched on and quiet, receiving a signal, and on the air. */
	static final int IDLE_TICKS = 288_000, RECEIVE_TICKS = 144_000, TRANSMIT_TICKS = 110_000;

	public CbPortableItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	public static CbRadioState state(ItemStack stack) {
		CbRadioState state = stack.get(RadiocraftDataComponent.CB_RADIO.value());
		return state == null ? CbRadioState.DEFAULT : state;
	}

	public static void setState(ItemStack stack, CbRadioState state) {
		stack.set(RadiocraftDataComponent.CB_RADIO.value(), state);
	}

	public static int energy(ItemStack stack) {
		Integer energy = stack.get(RadiocraftDataComponent.CB_ENERGY.value());
		return energy == null ? 0 : energy;
	}

	public static int capacity() {
		return CommonConfig.SMALL_BATTERY_CAPACITY.get();
	}

	/** Switched on (volume off its OFF click) with charge left: the whip is up. */
	public static boolean isOn(ItemStack stack) {
		return stack.getItem() instanceof CbPortableItem && state(stack).volume() > 0 && energy(stack) > 0;
	}

	/**
	 * Takes this tick's current from the cells: a little more on the air than listening. False once they are flat.
	 * Spread over ticks as the VHF handheld's is (most ticks take nothing, so the item is not rewritten every tick).
	 */
	public static boolean drain(ItemStack stack, boolean transmitting, boolean receiving, long gameTime) {
		if(!com.arrl.radiocraft.RadiocraftServerConfig.RADIO_POWER_REQUIRED.get())
			return true;
		int lifetime = transmitting ? TRANSMIT_TICKS : receiving ? RECEIVE_TICKS : IDLE_TICKS;
		int take = VHFHandheldItem.spreadDrain((double)capacity() / lifetime, gameTime);
		int energy = energy(stack);
		if(take > 0) {
			energy = Math.max(0, energy - take);
			stack.set(RadiocraftDataComponent.CB_ENERGY.value(), energy);
		}
		return energy > 0;
	}

	@Override
	public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if(player.isShiftKeyDown()) {
			ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
			if(other.getItem() == RadiocraftItems.SMALL_BATTERY.get()) {
				if(!level.isClientSide)
					swapCharge(stack, other, player);
				return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
			}
			if(hand == InteractionHand.MAIN_HAND && level.isClientSide)
				com.arrl.radiocraft.client.screens.radios.CbPortableScreen.open(player.getInventory().selected); // A separate class: the server never loads it.
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}
		return InteractionResultHolder.pass(stack); // Holding right click talks (PlayerRadio's use-held flag).
	}

	private static void swapCharge(ItemStack radio, ItemStack battery, Player player) {
		IEnergyStorage storage = battery.getCapability(Capabilities.EnergyStorage.ITEM);
		if(storage == null)
			return;
		int theirs = storage.extractEnergy(storage.getEnergyStored(), false);
		storage.receiveEnergy(energy(radio), false);
		radio.set(RadiocraftDataComponent.CB_ENERGY.value(), Math.min(capacity(), theirs));
		player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.displayClientMessage(Component.translatable("message.radiocraft.battery_swapped"), true);
	}

	/** A small battery clicked onto it in an inventory swaps their charge, as on the VHF handheld. */
	@Override
	public boolean overrideStackedOnOther(@NotNull ItemStack radio, net.minecraft.world.inventory.@NotNull Slot slot, net.minecraft.world.inventory.@NotNull ClickAction action, @NotNull Player player) {
		ItemStack slotStack = slot.getItem();
				if(action == net.minecraft.world.inventory.ClickAction.PRIMARY && PortableBatteries.isCell(slotStack)) {
			if(!player.level().isClientSide)
				PortableBatteries.radioOntoCell(radio, slot, player); // NiCd or alkaline; the old cell takes its place.
			return true;
		}
		return false;
	}

	/** One with full cells, for the creative tab. */
	public static ItemStack charged(Item item) {
		ItemStack stack = new ItemStack(item);
		stack.set(RadiocraftDataComponent.CB_ENERGY.value(), capacity());
		return stack;
	}

	@Override
	public void onCraftedBy(@NotNull ItemStack stack, @NotNull Level level, @NotNull Player player) {
		super.onCraftedBy(stack, level, player);
		if(!stack.has(RadiocraftDataComponent.CB_ENERGY.value()) || energy(stack) == 0)
			stack.set(RadiocraftDataComponent.CB_ENERGY.value(), (int)(capacity() * (0.5D + level.random.nextDouble() * 0.2D))); // 50-70%, as a battery.
	}

	@Override
	public boolean shouldCauseReequipAnimation(@NotNull ItemStack oldStack, @NotNull ItemStack newStack, boolean slotChanged) {
		return slotChanged;
	}

	@Override
	public boolean isBarVisible(@NotNull ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(@NotNull ItemStack stack) {
		return Math.round(13.0F * energy(stack) / Math.max(1, capacity()));
	}

	@Override
	public int getBarColor(@NotNull ItemStack stack) {
		return Mth.hsvToRgb(Math.max(0.0F, (float)energy(stack) / Math.max(1, capacity())) / 3.0F, 1.0F, 1.0F);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		CbRadioState state = state(stack);
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "cb.channel"), state.channel(), state.displayStatus()).withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "cb.no_licence")).withStyle(ChatFormatting.GREEN));
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "cb.portable")).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.radiocraft.battery_percentage", String.format(java.util.Locale.ROOT, "%.1f", 100.0D * energy(stack) / Math.max(1, capacity())))
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
