package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.Radiocraft;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

/**
 * Keeps the radio computer's monitor live in the world (1.12, 2026-11-06): while this player's computer screen is
 * closed and its monitor was drawn in the last frame within {@link #RANGE} blocks of the camera, the programs are drawn
 * again offscreen into the monitor's picture every {@link #INTERVAL_MS} ms ({@link #MUSIC_INTERVAL_MS} while MUSIC
 * plays there, so its clock, bar and visualizer move). It reuses the screen's own drawing: the program in front draws
 * into a {@link TextGrid} kept with the session and {@link DosRenderer} draws that into the picture's render target, so
 * nothing but the programs' own text is made per redraw.
 * <p>
 * Only this player's own sessions are drawn (other players' computers keep the model's DOS face; nothing is sent over
 * the network). It runs at the start of a frame ({@link RenderFrameEvent.Pre}), outside the world and GUI rendering,
 * with the GUI's projection swapped for one the size of the picture and put back after.
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID, value = Dist.CLIENT)
public final class MonitorMirror {

	/** Blocks from the camera within which a monitor's picture is kept live. */
	public static final double RANGE = 16.0D;
	public static final long INTERVAL_MS = 500L, MUSIC_INTERVAL_MS = 200L;
	/** How often the average redraw time is logged (debug), ms. */
	private static final long LOG_EVERY_MS = 60_000L;

	private static int frame = 0;
	private static final Matrix4f PROJECTION = new Matrix4f();
	@Nullable
	private static GuiGraphics graphics;
	private static long timedNanos = 0L, timedCount = 0L, lastLog = 0L;

	private MonitorMirror() {}

	/** The frame count, bumped at the start of every frame. */
	static int frame() {
		return frame;
	}

	@SubscribeEvent
	public static void onFrameStart(RenderFrameEvent.Pre event) {
		int last = frame++;
		Minecraft minecraft = Minecraft.getInstance();
		if(minecraft.level == null)
			return;
		long now = System.currentTimeMillis();
		for(ComputerSession session : ComputerSession.all()) {
			if(session.seenFrame != last || session.isOpen() || !session.hasMirror())
				continue; // Not drawn last frame (far, or out of sight), or the open screen copies itself there already.
			boolean music = ComputerMusic.isPlayingAt(session.pos()) && !ComputerMusic.paused();
			if(now - session.lastRedraw < (music ? MUSIC_INTERVAL_MS : INTERVAL_MS))
				continue;
			session.lastRedraw = now;
			long start = System.nanoTime();
			redraw(minecraft, session, now);
			timedNanos += System.nanoTime() - start;
			timedCount++;
		}
		if(timedCount > 0L && now - lastLog >= LOG_EVERY_MS) {
			Radiocraft.LOGGER.debug("Radio computer monitor: {} live redraws in the last minute, {} ms each on average (CPU side)", timedCount,
					String.format(Locale.ROOT, "%.3f", timedNanos / 1.0E6D / timedCount));
			timedNanos = 0L;
			timedCount = 0L;
			lastLog = now;
		}
	}

	/** Draws {@code session}'s programs into its monitor picture. Render thread, between frames. */
	private static void redraw(Minecraft minecraft, ComputerSession session, long now) {
		TextGrid grid = session.monitorGrid;
		if(grid == null)
			session.monitorGrid = grid = new TextGrid();
		session.drawPrograms(grid, now);
		if(graphics == null)
			graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
		TextureTarget target = session.mirrorTarget();

		float far = net.neoforged.neoforge.client.ClientHooks.getGuiFarPlane();
		RenderSystem.backupProjectionMatrix();
		RenderSystem.setProjectionMatrix(PROJECTION.setOrtho(0.0F, TextGrid.WIDTH, TextGrid.HEIGHT, 0.0F, 1000.0F, far), VertexSorting.ORTHOGRAPHIC_Z);
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.translation(0.0F, 0.0F, 10000.0F - far);
		RenderSystem.applyModelViewMatrix();
		float fogStart = RenderSystem.getShaderFogStart();
		RenderSystem.setShaderFogStart(Float.MAX_VALUE); // No fog on the text (the GUI has none either).
		try {
			target.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);
			target.clear(Minecraft.ON_OSX);
			target.bindWrite(true);
			DosRenderer.render(graphics, minecraft.font, grid, 0, 0, now / 400L % 2L == 0L, session.scheme());
			graphics.flush();
			// The picture is opaque whatever the blending left in its alpha (as ComputerSession.capture does).
			RenderSystem.colorMask(false, false, false, true);
			RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
			RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
			RenderSystem.colorMask(true, true, true, true);
		}
		finally {
			RenderSystem.setShaderFogStart(fogStart);
			modelView.popMatrix();
			RenderSystem.applyModelViewMatrix();
			RenderSystem.restoreProjectionMatrix();
			minecraft.getMainRenderTarget().bindWrite(true);
		}
	}
}
