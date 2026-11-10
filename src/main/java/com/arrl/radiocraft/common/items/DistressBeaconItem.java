package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.init.RadiocraftSoundEvents;
import com.arrl.radiocraft.common.radio.distress.BeaconBattery;
import com.arrl.radiocraft.common.radio.distress.BeaconState;
import com.arrl.radiocraft.common.radio.distress.DistressBeacons;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The distress beacon (2026-11-09), after a vintage EPIRB: a battery, a transmitter and a strobe in a buoyant case.
 * Right click opens the arming cover (sneak and right click shuts it again); holding right click with the cover open
 * switches it on, and then nothing switches it off. While on it sends, from wherever it is (a player's inventory, or
 * floating as a dropped item, which then does not despawn): an alert with its position and its owner's name on
 * 161.975 MHz when switched on and every 50 seconds ({@link BeaconBattery#BURST_TICKS}), and a homing tone direction
 * finders can follow ({@link DistressBeacons}). The battery lasts one Minecraft day of that; then it sends a last
 * "spent" alert and becomes a {@code spent_distress_beacon}. One use only. The model's strobe lights while it is on
 * (item property {@code radiocraft:active}, registered client side).
 */
public class DistressBeaconItem extends Item {

	/** Ticks right click is held to switch it on. */
	public static final int ACTIVATE_TICKS = 40;

	public DistressBeaconItem(Properties properties) {
		super(properties);
	}

	public static BeaconState state(ItemStack stack) {
		BeaconState state = stack.get(RadiocraftDataComponent.DISTRESS_BEACON.value());
		return state == null ? BeaconState.NEW : state;
	}

	/** True while it transmits (the model's strobe is lit). */
	public static boolean isActive(ItemStack stack) {
		return stack.getItem() instanceof DistressBeaconItem && state(stack).active();
	}

	// -------------------- USE: ARM, THEN HOLD --------------------

	@Override
	public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BeaconState state = state(stack);
		if(state.active())
			return InteractionResultHolder.pass(stack); // On for good: there is no off switch.
		if(!state.armed() || player.isShiftKeyDown()) { // The cover: opened, or shut again with sneak.
			boolean open = !state.armed();
			if(!level.isClientSide) {
				stack.set(RadiocraftDataComponent.DISTRESS_BEACON.value(), state.withArmed(open));
				level.playSound(null, player.blockPosition(), open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.5F, 1.6F);
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}
		player.startUsingItem(hand); // Armed: hold to switch on.
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
		return ACTIVATE_TICKS;
	}

	@Override
	public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
		return UseAnim.BOW;
	}

	@Override
	public void onUseTick(@NotNull Level level, @NotNull LivingEntity entity, @NotNull ItemStack stack, int remainingUseDuration) {
		if(!level.isClientSide && remainingUseDuration % 20 == 0 && remainingUseDuration > 0)
			level.playSound(null, entity.blockPosition(), RadiocraftSoundEvents.DSC_BEEP.get(), SoundSource.PLAYERS, 0.5F, 1.2F);
	}

	@Override
	public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity) {
		BeaconState state = state(stack);
		if(level instanceof ServerLevel serverLevel && state.armed() && !state.active()) {
			long id = serverLevel.getRandom().nextLong();
			String owner = entity instanceof Player player ? player.getGameProfile().getName() : entity.getName().getString();
			BeaconState on = state.activated(id == 0L ? 1L : id, owner);
			stack.set(RadiocraftDataComponent.DISTRESS_BEACON.value(), on);
			serverLevel.playSound(null, entity.blockPosition(), RadiocraftSoundEvents.DSC_BEEP.get(), SoundSource.PLAYERS, 0.8F, 1.6F);
			DistressBeacons.alert(serverLevel, entity.blockPosition(), entity instanceof Player player ? player : null,
					on.alert(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ(), serverLevel.getGameTime()));
		}
		return stack;
	}

	// -------------------- TRANSMITTING --------------------

	@Override
	public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean selected) {
		if(!(level instanceof ServerLevel serverLevel) || !state(stack).active())
			return;
		ItemStack spent = tickActive(stack, serverLevel, entity, entity instanceof Player player ? player : null);
		if(spent != null && entity instanceof Player player) { // Swap it in wherever the beacon was.
			for(int i = 0; i < player.getInventory().getContainerSize(); i++)
				if(player.getInventory().getItem(i) == stack) {
					player.getInventory().setItem(i, spent);
					break;
				}
		}
	}

	/** Floating, it keeps sending, and does not despawn while it does. */
	@Override
	public boolean onEntityItemUpdate(@NotNull ItemStack stack, @NotNull ItemEntity entity) {
		if(entity.level() instanceof ServerLevel serverLevel && state(stack).active()) {
			entity.setExtendedLifetime(); // Its despawn clock held back as long as it transmits.
			ItemStack spent = tickActive(stack, serverLevel, entity, null);
			if(spent != null)
				entity.setItem(spent);
		}
		return false;
	}

	/**
	 * One tick of an active beacon at {@code at}: the homing tone, the battery counted down once a second and an alert
	 * when one is due. Returns the spent beacon to put in its place once the battery is out, else null.
	 */
	@Nullable
	private static ItemStack tickActive(ItemStack stack, ServerLevel level, Entity at, @Nullable Player holder) {
		long now = level.getGameTime();
		BeaconState state = state(stack);
		if(now % DistressBeacons.HOMING_TICKS == 0L)
			DistressBeacons.homing(level, at.blockPosition(), holder, "distress_beacon:" + state.alertId());
		if(now % BeaconBattery.STEP_TICKS != 0L)
			return null;
		BeaconState after = state.drained(BeaconBattery.STEP_TICKS);
		if(BeaconBattery.spent(after.remaining())) {
			DistressBeacons.alert(level, at.blockPosition(), holder, after.alert(at.getBlockX(), at.getBlockY(), at.getBlockZ(), now)); // The last word: SPENT.
			return new ItemStack(RadiocraftItems.SPENT_DISTRESS_BEACON.get());
		}
		stack.set(RadiocraftDataComponent.DISTRESS_BEACON.value(), after);
		if(BeaconBattery.burstDue(state.remaining(), after.remaining()))
			DistressBeacons.alert(level, at.blockPosition(), holder, after.alert(at.getBlockX(), at.getBlockY(), at.getBlockZ(), now));
		return null;
	}

	/** The battery's countdown changes the item each second: no re-equip bob in the hand for that. */
	@Override
	public boolean shouldCauseReequipAnimation(@NotNull ItemStack oldStack, @NotNull ItemStack newStack, boolean slotChanged) {
		return slotChanged || oldStack.getItem() != newStack.getItem();
	}

	// -------------------- LOOKS --------------------

	@Override
	public boolean isBarVisible(@NotNull ItemStack stack) {
		return state(stack).active();
	}

	@Override
	public int getBarWidth(@NotNull ItemStack stack) {
		return Math.round(13.0F * BeaconBattery.share(state(stack).remaining()));
	}

	@Override
	public int getBarColor(@NotNull ItemStack stack) {
		return Mth.hsvToRgb(BeaconBattery.share(state(stack).remaining()) / 3.0F, 1.0F, 1.0F);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		BeaconState state = state(stack);
		if(state.active()) {
			tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "distress_beacon.active"), BeaconBattery.remainingText(state.remaining())).withStyle(ChatFormatting.RED));
			tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "distress_beacon.owner"), state.owner()).withStyle(ChatFormatting.GRAY));
		}
		else {
			tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "distress_beacon.one_use")).withStyle(ChatFormatting.GOLD));
			tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", state.armed() ? "distress_beacon.armed" : "distress_beacon.safe")).withStyle(ChatFormatting.GRAY));
		}
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "distress_beacon")).withStyle(ChatFormatting.GRAY));
	}
}
