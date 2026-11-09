package com.arrl.radiocraft.client.photo;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.items.CameraItem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import com.arrl.radiocraft.common.network.serverbound.SCameraShutterPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The camera on the client: while the player holds right click with a camera (aiming), a viewfinder marks the square the
 * photo will be (the middle of the screen, as {@link PhotoCapture} takes it), the hand is hidden, and the scroll wheel
 * zooms from 1x to 3x in half steps by narrowing the field of view, so the photo is exactly what the viewfinder shows.
 * A left click (the attack key, whatever it is bound to) takes the photo instead of swinging; letting go of right click
 * lowers the camera. After the shutter the zoom is held even if the camera is lowered at once until the frame is grabbed (the server answers within a moment), then
 * drops back. The zoom is remembered for the next photo.
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID, value = Dist.CLIENT)
public final class CameraClient {

	public static final double MIN_ZOOM = 1.0D, MAX_ZOOM = 3.0D, ZOOM_STEP = 0.5D;
	/** How long the zoom is held after the shutter for the frame grab, at most. */
	private static final long HOLD_MILLIS = 2000L;

	private static double zoom = MIN_ZOOM;
	private static long holdUntil = 0L;

	private CameraClient() {}

	public static double zoom() {
		return zoom;
	}

	/** True while the local player holds a camera up. */
	public static boolean aiming() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.isUsingItem() && player.getUseItem().getItem() instanceof CameraItem;
	}

	/**
	 * While aiming, the attack key takes the photo. Read before the game handles its keys: while an item is in use
	 * the game throws attack clicks away, so they never reach an interaction event.
	 */
	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Pre event) {
		Minecraft minecraft = Minecraft.getInstance();
		if(minecraft.screen != null || !aiming())
			return;
		boolean clicked = false;
		while(minecraft.options.keyAttack.consumeClick())
			clicked = true;
		if(clicked && minecraft.player != null && !minecraft.player.getCooldowns().isOnCooldown(minecraft.player.getUseItem().getItem())) {
			holdUntil = Util.getMillis() + HOLD_MILLIS; // Keep the zoom until the photo is grabbed.
			SCameraShutterPacket.send();
		}
	}

	/** The frame for the photo was grabbed: the zoom may drop back. */
	public static void captured() {
		holdUntil = 0L;
	}

	private static boolean zoomed() {
		return zoom > MIN_ZOOM && (aiming() || Util.getMillis() < holdUntil);
	}

	/** The field of view, in degrees, that magnifies {@code fov} by {@code zoom} (narrowing the view, not scaling the angle). */
	public static double zoomedFov(double fov, double zoom) {
		double half = Math.toRadians(fov) / 2.0D;
		return Math.toDegrees(2.0D * Math.atan(Math.tan(half) / zoom));
	}

	@SubscribeEvent
	public static void onComputeFov(ViewportEvent.ComputeFov event) {
		if(event.usedConfiguredFov() && zoomed()) // The level's view, not the hand's.
			event.setFOV(zoomedFov(event.getFOV(), zoom));
	}

	@SubscribeEvent
	public static void onScroll(InputEvent.MouseScrollingEvent event) {
		if(!aiming() || event.getScrollDeltaY() == 0.0D)
			return;
		zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + Math.signum(event.getScrollDeltaY()) * ZOOM_STEP));
		event.setCanceled(true); // Not the hotbar.
	}

	@SubscribeEvent
	public static void onRenderHand(RenderHandEvent event) {
		if(aiming())
			event.setCanceled(true);
	}

	/** A photo whose owner deleted it (its map blanked, see {@code PhotoLedger}) says so. */
	@SubscribeEvent
	public static void onTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
		net.minecraft.world.item.ItemStack stack = event.getItemStack();
		if(!com.arrl.radiocraft.common.photo.PhotoPrints.isPhoto(stack) || Minecraft.getInstance().level == null)
			return;
		net.minecraft.world.level.saveddata.maps.MapItemSavedData data = net.minecraft.world.item.MapItem.getSavedData(stack, Minecraft.getInstance().level);
		if(data != null && com.arrl.radiocraft.common.photo.PhotoLedger.isBlank(data))
			event.getToolTip().add(Component.translatable(Radiocraft.translationKey("tooltip", "photo.deleted")).withStyle(net.minecraft.ChatFormatting.RED));
	}

	@SubscribeEvent
	public static void onRenderGui(RenderGuiEvent.Post event) {
		if(!aiming())
			return;
		Minecraft minecraft = Minecraft.getInstance();
		GuiGraphics graphics = event.getGuiGraphics();
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		int side = Math.min(width, height);
		int left = (width - side) / 2, top = (height - side) / 2, right = left + side, bottom = top + side;
		int shade = 0x99000000, white = 0xFFFFFFFF;
		// Outside the photo, darkened.
		graphics.fill(0, 0, left, height, shade);
		graphics.fill(right, 0, width, height, shade);
		graphics.fill(left, 0, right, top, shade);
		graphics.fill(left, bottom, right, height, shade);
		// Corner marks, a little inside the frame.
		int inset = 6, arm = Math.max(8, side / 12);
		int l = left + inset, t = top + inset, r = right - inset, b = bottom - inset;
		graphics.fill(l, t, l + arm, t + 1, white);
		graphics.fill(l, t, l + 1, t + arm, white);
		graphics.fill(r - arm, t, r, t + 1, white);
		graphics.fill(r - 1, t, r, t + arm, white);
		graphics.fill(l, b - 1, l + arm, b, white);
		graphics.fill(l, b - arm, l + 1, b, white);
		graphics.fill(r - arm, b - 1, r, b, white);
		graphics.fill(r - 1, b - arm, r, b, white);
		// A small centre cross.
		int cx = width / 2, cy = height / 2;
		graphics.fill(cx - 4, cy, cx + 5, cy + 1, 0xC0FFFFFF);
		graphics.fill(cx, cy - 4, cx + 1, cy + 5, 0xC0FFFFFF);
		// Zoom, paper and the hint, along the bottom of the frame.
		Boolean colour = minecraft.player == null ? null : CameraItem.paperFor(minecraft.player, minecraft.player.getUsedItemHand());
		Component paper = Component.translatable(Radiocraft.translationKey("screen", colour == null ? "camera.no_paper" : colour ? "camera.color" : "camera.bw"));
		Component zoomText = Component.translatable(Radiocraft.translationKey("screen", "camera.zoom"), String.format(java.util.Locale.ROOT, "%.1f", zoom));
		int textY = b - 12;
		graphics.drawString(minecraft.font, zoomText, l + 4, textY, white, true);
		graphics.drawString(minecraft.font, paper, r - 4 - minecraft.font.width(paper), textY, colour == null ? 0xFFFF5555 : white, true);
		Component hint = Component.translatable(Radiocraft.translationKey("screen", "camera.hint"));
		graphics.drawString(minecraft.font, hint, cx - minecraft.font.width(hint) / 2, t + 4, 0xC0FFFFFF, true);
	}
}
