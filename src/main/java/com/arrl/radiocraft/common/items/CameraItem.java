package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.network.clientbound.CPhotoShutterPacket;
import com.arrl.radiocraft.common.photo.PhotoLedger;
import com.arrl.radiocraft.common.photo.PhotoUploads;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The camera: hold right click to raise it to your eye and aim (a viewfinder shows the square that will be taken; the
 * scroll wheel zooms 1x to 3x, see {@code client.photo.CameraClient}); let go to take the photo. The photo is the
 * middle square of the screen without your hand or the HUD, printed on a sheet of photo paper from your inventory as a
 * locked map ({@link com.arrl.radiocraft.common.photo.PhotoPrints}). Photo paper in the other hand is used first, else
 * the first found in the inventory; colour photo paper gives a colour photo, black and white paper a greyscale one.
 * <p>
 * The server decides: on release it fires the shutter ({@link #fireShutter}: {@link PhotoUploads#open}, the click
 * everyone nearby hears), asks the player's game for the picture ({@link CPhotoShutterPacket}), and prints it when the
 * picture arrives. The camera's cooldown ({@code camera_cooldown_seconds}) is the film advance; {@code camera_photo_cap}
 * may limit how many photos a player keeps ({@link PhotoLedger}).
 */
public class CameraItem extends Item {

	/** Long enough that nobody holds it up this long (an hour). */
	private static final int AIM_TICKS = 72000;

	public CameraItem(Properties properties) {
		super(properties);
	}

	@Override
	public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if(player.getCooldowns().isOnCooldown(this))
			return InteractionResultHolder.fail(stack);
		ServerPlayer serverPlayer = player instanceof ServerPlayer p ? p : null;
		if(!RadiocraftServerConfig.CAMERA_ENABLED.get()) { // The server config reaches clients too.
			if(serverPlayer != null)
				serverPlayer.displayClientMessage(Component.translatable(Radiocraft.translationKey("message", "camera.disabled")), true);
			return InteractionResultHolder.fail(stack);
		}
		if(paperFor(player, hand) == null) {
			if(serverPlayer != null)
				serverPlayer.displayClientMessage(Component.translatable(Radiocraft.translationKey("message", "camera.no_paper")), true);
			return InteractionResultHolder.fail(stack);
		}
		if(serverPlayer != null && PhotoLedger.refuseOverCap(serverPlayer))
			return InteractionResultHolder.fail(stack);
		player.startUsingItem(hand); // Aiming: the viewfinder is up until the button is let go.
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
		return AIM_TICKS;
	}

	@Override
	public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
		return UseAnim.SPYGLASS; // Held up to the eye.
	}

	@Override
	public void releaseUsing(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity, int timeLeft) {
		if(level.isClientSide)
			com.arrl.radiocraft.client.photo.CameraClient.shutterReleased(entity); // A separate class, so the server never loads it.
		else if(entity instanceof ServerPlayer player)
			fireShutter(player, player.getUsedItemHand());
	}

	/**
	 * Fires the shutter of the camera in {@code player}'s {@code hand}: checks again (on, film advanced, paper, cap),
	 * opens the upload, asks the client for the picture, clicks and starts the cooldown. True if it fired.
	 */
	public static boolean fireShutter(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if(!(stack.getItem() instanceof CameraItem camera) || player.getCooldowns().isOnCooldown(camera) || !RadiocraftServerConfig.CAMERA_ENABLED.get())
			return false;
		Boolean colour = paperFor(player, hand);
		if(colour == null) {
			player.displayClientMessage(Component.translatable(Radiocraft.translationKey("message", "camera.no_paper")), true);
			return false;
		}
		if(PhotoLedger.refuseOverCap(player))
			return false;
		int nonce = PhotoUploads.open(player, colour);
		CPhotoShutterPacket.send(player, nonce, colour);
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.7F, 1.9F);
		player.getCooldowns().addCooldown(camera, cooldownTicks());
		return true;
	}

	/** Ticks between photos, from {@code camera_cooldown_seconds}. */
	public static int cooldownTicks() {
		return Math.max(20, (int)Math.round(RadiocraftServerConfig.CAMERA_COOLDOWN_SECONDS.get() * 20.0D));
	}

	/** True if {@code stack} is photo paper: colour photo paper if {@code colour}, else black and white. */
	public static boolean isPaper(ItemStack stack, boolean colour) {
		return stack.is(colour ? RadiocraftItems.COLOR_PHOTO_PAPER.get() : RadiocraftItems.PHOTO_PAPER.get());
	}

	/** True if {@code stack} is photo paper of either kind. */
	public static boolean isPaper(ItemStack stack) {
		return isPaper(stack, true) || isPaper(stack, false);
	}

	/**
	 * Which paper a camera in {@code cameraHand} prints on: true for colour, false for black and white, null for none.
	 * The other hand first, then the inventory in slot order. A creative player with no paper prints in colour.
	 */
	@Nullable
	public static Boolean paperFor(Player player, InteractionHand cameraHand) {
		ItemStack other = player.getItemInHand(cameraHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		if(isPaper(other))
			return isPaper(other, true);
		Inventory inventory = player.getInventory();
		for(int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if(isPaper(stack))
				return isPaper(stack, true);
		}
		return player.getAbilities().instabuild ? Boolean.TRUE : null;
	}

	/**
	 * Uses up one sheet of {@code player}'s photo paper of the kind given (from a hand first). True if there was one;
	 * creative players always have one and lose nothing.
	 */
	public static boolean takePaper(Player player, boolean colour) {
		if(player.getAbilities().instabuild)
			return true;
		for(InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if(isPaper(stack, colour)) {
				stack.shrink(1);
				return true;
			}
		}
		Inventory inventory = player.getInventory();
		for(int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if(isPaper(stack, colour)) {
				stack.shrink(1);
				inventory.setChanged();
				return true;
			}
		}
		return false;
	}
}
