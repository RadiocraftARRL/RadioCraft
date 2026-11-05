package com.arrl.radiocraft.common.photo;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.datacomponents.PhotoInfo;
import com.arrl.radiocraft.common.items.CameraItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Photos on their way up from players' games, at most one per player. The camera opens one when the server fires the
 * shutter ({@link #open}); the client then sends the picture in {@link PhotoUpload#CHUNKS} chunks
 * ({@code SPhotoChunkPacket}), and the last one prints the photo on a sheet of the player's photo paper. Chunks for no
 * open upload, repeats, and chunks after {@link PhotoUpload#TIMEOUT_TICKS} are thrown away. With the camera's cooldown
 * this keeps a client to one 16 KB picture per {@code camera_cooldown_seconds}, and only when it was asked for one.
 * Server thread only.
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID)
public final class PhotoUploads {

	private static final Map<UUID, PhotoUpload> OPEN = new HashMap<>();

	private PhotoUploads() {}

	/** Opens an upload for {@code player}'s next photo (replacing any still open) and returns its nonce. */
	public static int open(ServerPlayer player, boolean colour) {
		int nonce = player.getRandom().nextInt() & 0x7FFFFFFF | 1; // Never 0, so a zeroed packet never matches.
		OPEN.put(player.getUUID(), new PhotoUpload(nonce, colour, tick(player)));
		return nonce;
	}

	/** The nonce of {@code player}'s open upload, or 0 if none (tests and debugging). */
	public static int openNonce(ServerPlayer player) {
		PhotoUpload upload = OPEN.get(player.getUUID());
		return upload == null ? 0 : upload.nonce();
	}

	/**
	 * Takes chunk {@code index} of {@code player}'s photo {@code nonce}. On the last chunk the photo is printed and
	 * given to the player, and returned; null otherwise.
	 */
	@Nullable
	public static ItemStack accept(ServerPlayer player, int nonce, int index, byte[] data) {
		PhotoUpload upload = OPEN.get(player.getUUID());
		if(upload == null)
			return null;
		PhotoUpload.Result result = upload.accept(nonce, index, data, tick(player));
		if(result == PhotoUpload.Result.EXPIRED)
			OPEN.remove(player.getUUID());
		if(result != PhotoUpload.Result.COMPLETE)
			return null;
		OPEN.remove(player.getUUID());
		return develop(player, upload.pixels(), upload.colour());
	}

	/**
	 * Prints {@code pixels} on a sheet of {@code player}'s photo paper (colour or black and white, as when the shutter
	 * went), records it against {@code camera_photo_cap} ({@link PhotoLedger}) and gives it to them. Null, with a
	 * message, if the paper ran out or the cap was reached meanwhile.
	 */
	@Nullable
	public static ItemStack develop(ServerPlayer player, byte[] pixels, boolean colour) {
		if(PhotoLedger.refuseOverCap(player)) // Two photos on their way when the cap was one short.
			return null;
		if(!CameraItem.takePaper(player, colour)) {
			player.displayClientMessage(Component.translatable(Radiocraft.translationKey("message", "camera.no_paper")), true);
			return null;
		}
		String name = player.getGameProfile().getName();
		ItemStack photo = PhotoPrints.photo(player.serverLevel(), pixels, new PhotoInfo(name, colour, PhotoInfo.CAMERA), player.blockPosition());
		net.minecraft.world.level.saveddata.maps.MapId mapId = photo.get(net.minecraft.core.component.DataComponents.MAP_ID);
		if(mapId != null)
			PhotoLedger.record(player, mapId);
		ItemStack given = photo.copy();
		if(!player.getInventory().add(given))
			player.drop(given, false);
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.6F, 1.2F);
		return photo;
	}

	/** Forgets {@code player}'s open upload. */
	public static void close(ServerPlayer player) {
		OPEN.remove(player.getUUID());
	}

	@SubscribeEvent
	public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		OPEN.remove(event.getEntity().getUUID());
	}

	private static long tick(ServerPlayer player) {
		return player.getServer() == null ? 0L : player.getServer().getTickCount();
	}
}
