package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.CrtMonitorBlockEntity;
import com.arrl.radiocraft.common.blockentities.CrtMonitorGroups;
import com.arrl.radiocraft.common.blocks.CrtMonitorBlock;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Random;
import java.util.function.Function;

/**
 * Draws an External CRT Monitor's picture on its glass (2026-11-12), full bright, a hair in front of it: this block's part of
 * its screen's picture ({@link CrtMonitorPictures}, cut by {@link CrtMonitorGroups#slice} so the bezels hide the
 * strips between tubes), smoothly scaled (linear filtering on the true-colour texture), then a vintage tube's faults,
 * kept cheap: nothing is repainted for them, they are a few quads over the picture with small textures made once.
 * <ul>
 * <li>a faint phosphor glow (the picture again, added, a texel or so off in four directions) and a tiny RGB convergence
 * error growing to the edges (red and blue copies scaled about the centre);</li>
 * <li>horizontal scanlines, and a barrel vignette darkening the corners;</li>
 * <li>a very slight brightness flicker, a little noise, and now and then a slow faint hum bar rolling up the tube;</li>
 * <li>switching on, a bright line that opens into the picture; switching off, the picture collapsing to a line, then a
 * dot that fades.</li>
 * </ul>
 * The model faces north and the north face is seen from -z, so its x runs right to left for the viewer; the pose is
 * turned with the block's facing as the blockstate turns the model.
 */
public class CrtMonitorRenderer implements BlockEntityRenderer<CrtMonitorBlockEntity> {

	private static final float X0 = CrtMonitorGroups.GLASS_X0, X1 = CrtMonitorGroups.GLASS_X1, Y0 = CrtMonitorGroups.GLASS_Y0, Y1 = CrtMonitorGroups.GLASS_Y1;
	/** The glass face, the picture a little in front of it, the faults in front of that (model units). */
	private static final float GLASS_Z = 1.99F, PICTURE_Z = 1.93F, OVERLAY_Z = 1.85F;
	private static final long ON_MS = 520L, OFF_MS = 650L;
	/** Scanlines down one tube. */
	private static final int SCANLINES = 72;
	private static final long HUM_PERIOD_MS = 19_000L, HUM_ROLL_MS = 7_000L;

	private static final Function<ResourceLocation, RenderType> PICTURE = Util.memoize(texture -> RenderType.create("radiocraft_external_crt_monitor_picture",
			DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 1536, false, true,
			RenderType.CompositeState.builder()
					.setShaderState(RenderStateShard.RENDERTYPE_TEXT_SHADER)
					.setTextureState(new RenderStateShard.TextureStateShard(texture, true, false)) // Linear: smooth scaling.
					.setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
					.setLightmapState(RenderStateShard.LIGHTMAP)
					.createCompositeState(false)));
	private static final Function<ResourceLocation, RenderType> GLOW = Util.memoize(texture -> RenderType.create("radiocraft_external_crt_monitor_glow",
			DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 1536, false, false,
			RenderType.CompositeState.builder()
					.setShaderState(RenderStateShard.RENDERTYPE_TEXT_SHADER)
					.setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
					.setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
					.setLightmapState(RenderStateShard.LIGHTMAP)
					.setWriteMaskState(RenderStateShard.COLOR_WRITE)
					.createCompositeState(false)));
	/** Darkening layers (scanlines, vignette): their strength is in their textures' alpha; nothing faint is cut off. */
	private static final Function<ResourceLocation, RenderType> SHADE = Util.memoize(texture -> RenderType.create("radiocraft_external_crt_monitor_shade",
			DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 256, false, false,
			RenderType.CompositeState.builder()
					.setShaderState(RenderStateShard.POSITION_TEX_SHADER)
					.setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
					.setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
					.setWriteMaskState(RenderStateShard.COLOR_WRITE)
					.createCompositeState(false)));

	private static ResourceLocation scanlines, vignette, noise, hum, white;

	public CrtMonitorRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(CrtMonitorBlockEntity display, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
		effects();
		long now = Util.getMillis();
		// A computer's mosaic, or (on a premium radio, 2026-11-12) the radio's waterfall: the same tube either way.
		boolean radio = display.radioMode();
		CrtMonitorPictures.Picture picture = radio ? null : CrtMonitorPictures.get(display.master());
		CrtMonitorWaterfalls.Fall fall = radio ? CrtMonitorWaterfalls.get(display.getBlockPos()) : null;
		boolean on = radio ? fall != null && fall.on() && display.span() != CrtMonitorBlockEntity.Span.OFF
				: picture != null && picture.hasPicture() && picture.mode != CrtMonitorBlockEntity.Mode.OFF;
		if(display.clientLitChangedAt == Long.MIN_VALUE) { // Seen for the first time: as it is, no animation.
			display.clientLit = on;
			display.clientLitChangedAt = now - 60_000L;
		}
		else if(display.clientLit != on) {
			display.clientLit = on;
			display.clientLitChangedAt = now;
		}
		long since = now - display.clientLitChangedAt;
		if(!on && since >= OFF_MS)
			return;
		int seed = display.getBlockPos().hashCode();

		poseStack.pushPose();
		poseStack.translate(0.5F, 0.0F, 0.5F); // Turned as the blockstate turns the model (north unturned).
		poseStack.mulPose(Axis.YP.rotationDegrees(-(display.getBlockState().getValue(CrtMonitorBlock.FACING).toYRot() + 180.0F)));
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		Matrix4f pose = poseStack.last().pose();
		float cx = (X0 + X1) / 2.0F, cy = (Y0 + Y1) / 2.0F, hw = (X1 - X0) / 2.0F, hh = (Y1 - Y0) / 2.0F;

		if(!on) { // Switching off: the picture's last light collapses to a line, then a dot that fades.
			float p = since / (float)OFF_MS;
			float sx = p < 0.35F ? 1.0F : p < 0.6F ? Math.max(0.012F, 1.0F - (p - 0.35F) / 0.25F) : 0.012F;
			float sy = p < 0.35F ? Math.max(0.01F, 1.0F - p / 0.35F) : 0.01F;
			float fade = p < 0.6F ? 1.0F : 1.0F - (p - 0.6F) / 0.4F;
			int glow = (int)(230 * fade);
			glowQuad(buffers, white, pose, cx + hw * sx, cx - hw * sx, cy - hh * sy, cy + hh * sy, 0.0F, 0.0F, 1.0F, 1.0F, glow, glow, glow);
			if(p >= 0.35F) { // The dot's halo.
				float r = 0.6F * fade;
				glowQuad(buffers, white, pose, cx + r, cx - r, cy - r, cy + r, 0.0F, 0.0F, 1.0F, 1.0F, glow / 5, glow / 5, glow / 5);
			}
			poseStack.popPose();
			return;
		}

		ResourceLocation texture;
		int texWidth, texHeight;
		if(fall != null) {
			net.minecraft.client.multiplayer.ClientLevel clientLevel = Minecraft.getInstance().level;
			texture = fall.texture(clientLevel == null ? 0L : clientLevel.getGameTime());
			texWidth = com.arrl.radiocraft.common.radio.BandScope.BINS;
			texHeight = CrtMonitorWaterfalls.ROWS;
		}
		else {
			texture = picture.texture(picture.frameAt(now));
			texWidth = picture.width;
			texHeight = picture.height;
		}
		if(texture == null) {
			poseStack.popPose();
			return;
		}
		CrtMonitorGroups.Slice slice = CrtMonitorGroups.slice(display.cellA(), display.cellB(), display.groupW(), display.groupH());
		// Switching on: a bright line across, then it opens into the picture.
		float sx = 1.0F, sy = 1.0F, boost = 0.0F;
		if(since < ON_MS) {
			float p = since / (float)ON_MS;
			if(p < 0.3F) {
				sx = Math.max(0.02F, p / 0.3F);
				sy = 0.012F;
				boost = 1.0F;
			}
			else {
				float q = (p - 0.3F) / 0.7F;
				sy = Math.max(0.012F, 1.0F - (1.0F - q) * (1.0F - q));
				boost = 1.0F - q;
			}
		}
		float left = cx + hw * sx, right = cx - hw * sx, bottom = cy - hh * sy, top = cy + hh * sy;
		float flicker = 0.955F + 0.035F * (float)(0.5D + 0.25D * Math.sin(now * 0.0071D + seed) + 0.25D * Math.sin(now * 0.0193D + seed * 0.37D));
		int base = Math.min(255, (int)(255 * flicker * 0.92F));
		VertexConsumer pic = buffers.getBuffer(PICTURE.apply(texture));
		textQuad(pic, pose, left, right, bottom, top, PICTURE_Z, slice.u0(), slice.v0(), slice.u1(), slice.v1(), base, base, base, 255);
		if(boost > 0.0F) {
			int b = (int)(200 * boost);
			glowQuad(buffers, white, pose, left, right, bottom, top, 0.0F, 0.0F, 1.0F, 1.0F, b, b, b);
		}
		// Phosphor glow: the picture added again, a texel or so off each way, faint.
		float du = 1.2F / texWidth, dv = 1.2F / texHeight;
		int g = (int)(16 * flicker);
		for(int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
			glowQuad(buffers, texture, pose, left, right, bottom, top, slice.u0() + d[0] * du, slice.v0() + d[1] * dv, slice.u1() + d[0] * du, slice.v1() + d[1] * dv, g, g, g);
		// Convergence: red a touch larger, blue a touch smaller, about the tube's middle, so they part at the edges.
		convergence(buffers, texture, pose, left, right, bottom, top, slice, 1.006F, 22, 0, 0);
		convergence(buffers, texture, pose, left, right, bottom, top, slice, 0.994F, 0, 0, 22);
		// A slow faint hum bar rolling up now and then.
		long phase = Math.floorMod(now + seed * 977L, HUM_PERIOD_MS);
		if(phase < HUM_ROLL_MS) {
			float travel = phase / (float)HUM_ROLL_MS;
			float band = (Y1 - Y0) * 0.28F;
			float barBottom = Y0 - band + travel * (Y1 - Y0 + band), barTop = barBottom + band;
			float clipBottom = Math.max(bottom, barBottom), clipTop = Math.min(top, barTop);
			if(clipTop > clipBottom)
				glowQuad(buffers, hum, pose, left, right, clipBottom, clipTop, 0.0F, 1.0F - (clipTop - barBottom) / band, 1.0F, 1.0F - (clipBottom - barBottom) / band, 14, 15, 14);
		}
		// A little noise, a new patch of it every frame or two.
		Random random = new Random(now / 40L * 31L + seed);
		float nu = random.nextFloat() * 0.5F, nv = random.nextFloat() * 0.5F;
		glowQuad(buffers, noise, pose, left, right, bottom, top, nu, nv, nu + 0.5F, nv + 0.5F, 18, 18, 18);
		// Scanlines, then the vignette over everything.
		shadeQuad(buffers, scanlines, pose, left, right, bottom, top, 0.0F, 0.0F, 1.0F, sy);
		shadeQuad(buffers, vignette, pose, cx + hw, cx - hw, cy - hh, cy + hh, 0.0F, 0.0F, 1.0F, 1.0F);
		poseStack.popPose();
	}

	private static void convergence(MultiBufferSource buffers, ResourceLocation texture, Matrix4f pose, float left, float right, float bottom, float top,
			CrtMonitorGroups.Slice slice, float scale, int r, int g, int b) {
		float mu = (slice.u0() + slice.u1()) / 2.0F, mv = (slice.v0() + slice.v1()) / 2.0F;
		glowQuad(buffers, texture, pose, left, right, bottom, top, mu + (slice.u0() - mu) / scale, mv + (slice.v0() - mv) / scale,
				mu + (slice.u1() - mu) / scale, mv + (slice.v1() - mv) / scale, r, g, b);
	}

	/** A quad from the viewer's {@code left} to {@code right}, {@code bottom} to {@code top} (model units); v from the top. */
	private static void textQuad(VertexConsumer buffer, Matrix4f pose, float left, float right, float bottom, float top, float z,
			float u0, float v0, float u1, float v1, int r, int g, int b, int a) {
		vertex(buffer, pose, left, bottom, z, u0, v1, r, g, b, a);
		vertex(buffer, pose, right, bottom, z, u1, v1, r, g, b, a);
		vertex(buffer, pose, right, top, z, u1, v0, r, g, b, a);
		vertex(buffer, pose, left, top, z, u0, v0, r, g, b, a);
	}

	private static void glowQuad(MultiBufferSource buffers, ResourceLocation texture, Matrix4f pose, float left, float right, float bottom, float top,
			float u0, float v0, float u1, float v1, int r, int g, int b) {
		textQuad(buffers.getBuffer(GLOW.apply(texture)), pose, left, right, bottom, top, OVERLAY_Z, u0, v0, u1, v1, r, g, b, 255);
	}

	private static void shadeQuad(MultiBufferSource buffers, ResourceLocation texture, Matrix4f pose, float left, float right, float bottom, float top,
			float u0, float v0, float u1, float v1) {
		VertexConsumer buffer = buffers.getBuffer(SHADE.apply(texture));
		float z = OVERLAY_Z / 16.0F;
		buffer.addVertex(pose, left / 16.0F, bottom / 16.0F, z).setUv(u0, v1);
		buffer.addVertex(pose, right / 16.0F, bottom / 16.0F, z).setUv(u1, v1);
		buffer.addVertex(pose, right / 16.0F, top / 16.0F, z).setUv(u1, v0);
		buffer.addVertex(pose, left / 16.0F, top / 16.0F, z).setUv(u0, v0);
	}

	private static void vertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, float u, float v, int r, int g, int b, int a) {
		buffer.addVertex(pose, x / 16.0F, y / 16.0F, z / 16.0F).setColor(r, g, b, a).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
	}

	@Override
	public boolean shouldRenderOffScreen(CrtMonitorBlockEntity display) {
		return false;
	}

	// -------------------- THE FAULTS' TEXTURES, MADE ONCE --------------------

	private static void effects() {
		if(white != null)
			return;
		// Scanlines: every other line of the tube a little darker.
		scanlines = texture("scanlines", 4, SCANLINES * 2, (x, y) -> y % 2 == 1 ? 0x48000000 : 0x00000000);
		// Barrel vignette: a superellipse, as a tube's face, darker towards the corners.
		vignette = texture("vignette", 64, 64, (x, y) -> {
			double nx = (x + 0.5D) / 32.0D - 1.0D, ny = (y + 0.5D) / 32.0D - 1.0D;
			double d = Math.pow(Math.pow(Math.abs(nx), 4.0D) + Math.pow(Math.abs(ny), 4.0D), 0.25D);
			double radial = Math.sqrt(nx * nx + ny * ny) / Math.sqrt(2.0D);
			double a = Math.max(0.0D, Math.min(1.0D, (d - 0.5D) / 0.5D));
			int alpha = (int)Math.round(255.0D * Math.min(0.85D, 0.62D * Math.pow(a, 2.2D) + 0.18D * radial * radial));
			return alpha << 24;
		});
		Random random = new Random(1980L);
		noise = texture("noise", 128, 128, (x, y) -> {
			int v = random.nextInt(100) < 7 ? 255 : random.nextInt(60);
			return 0xFF000000 | v << 16 | v << 8 | v;
		});
		// The hum bar: a soft band, brightest in its middle.
		hum = texture("hum", 4, 64, (x, y) -> {
			int v = (int)Math.round(255.0D * Math.sin(Math.PI * (y + 0.5D) / 64.0D));
			return 0xFF000000 | v << 16 | v << 8 | v;
		});
		white = texture("white", 4, 4, (x, y) -> 0xFFFFFFFF);
	}

	private interface Pixels {
		/** The colour at {@code x, y}, ARGB. */
		int argb(int x, int y);
	}

	private static ResourceLocation texture(String name, int width, int height, Pixels pixels) {
		NativeImage image = new NativeImage(width, height, false);
		for(int y = 0; y < height; y++)
			for(int x = 0; x < width; x++) {
				int c = pixels.argb(x, y);
				image.setPixelRGBA(x, y, c & 0xFF00FF00 | (c & 0xFF) << 16 | c >> 16 & 0xFF); // ARGB to NativeImage's ABGR.
			}
		ResourceLocation id = Radiocraft.id("crt_monitor_fx/" + name);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
		return id;
	}
}
