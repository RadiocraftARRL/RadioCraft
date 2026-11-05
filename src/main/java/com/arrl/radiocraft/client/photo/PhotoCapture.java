package com.arrl.radiocraft.client.photo;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.network.serverbound.SPhotoChunkPacket;
import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.photo.PhotoImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Grabs what the player sees for the camera: the next frame once the level is drawn (after
 * {@link RenderLevelStageEvent.Stage#AFTER_LEVEL}, so without the hand in view or the HUD), its middle square scaled to
 * {@link PhotoImage#SIZE} (see {@link PhotoImage#downscale}). One GPU read per photo, on the render thread.
 * <p>
 * {@link #requestFrame} is the general hook (the radio computer's scanner or SSTV can take a picture of the screen with
 * it); {@link #takePhoto} is the camera's: quantise to the map colours and send the photo up.
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID, value = Dist.CLIENT)
public final class PhotoCapture {

	/** Most frame requests waiting at once; more are dropped (nothing should ask that often). */
	private static final int MAX_WAITING = 4;
	private static final List<Consumer<int[]>> WAITING = new ArrayList<>();

	private PhotoCapture() {}

	/**
	 * Asks for the next frame: {@code whenGrabbed} gets its middle square as {@link PhotoImage#SIZE} by
	 * {@link PhotoImage#SIZE} {@code 0xRRGGBB} pixels, on the render thread. Call on the client thread.
	 */
	public static void requestFrame(Consumer<int[]> whenGrabbed) {
		if(WAITING.size() < MAX_WAITING)
			WAITING.add(whenGrabbed);
	}

	/** The camera's shutter: grab the next frame, make it a colour or black and white photo, send it as {@code nonce}. */
	public static void takePhoto(int nonce, boolean colour) {
		requestFrame(rgb -> {
			CameraClient.captured(); // The viewfinder's zoom was held for this frame.
			SPhotoChunkPacket.sendPhoto(nonce, PhotoImage.photo(rgb, MapPalettes.vanilla(), colour));
		});
	}

	@SubscribeEvent
	public static void onRenderLevelStage(RenderLevelStageEvent event) {
		if(WAITING.isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL)
			return;
		List<Consumer<int[]>> grabbed = new ArrayList<>(WAITING);
		WAITING.clear();
		int[] rgb;
		try {
			rgb = grab(Minecraft.getInstance().getMainRenderTarget(), PhotoImage.SIZE);
		}
		catch(RuntimeException e) {
			Radiocraft.LOGGER.warn("Could not read the frame for a photo", e);
			return;
		}
		for(Consumer<int[]> consumer : grabbed)
			consumer.accept(rgb.clone());
	}

	/** The middle square of {@code target}'s colour buffer, {@code size} by {@code size}, as {@code 0xRRGGBB}. */
	public static int[] grab(RenderTarget target, int size) {
		int width = target.width, height = target.height;
		try(NativeImage image = new NativeImage(width, height, false)) {
			RenderSystem.bindTexture(target.getColorTextureId());
			image.downloadTexture(0, false);
			// GL rows run bottom up; NativeImage pixels are 0xAABBGGRR.
			return PhotoImage.downscale((x, y) -> {
				int abgr = image.getPixelRGBA(x, height - 1 - y);
				return (abgr & 0xFF) << 16 | (abgr & 0xFF00) | (abgr >> 16) & 0xFF;
			}, width, height, size);
		}
	}
}
