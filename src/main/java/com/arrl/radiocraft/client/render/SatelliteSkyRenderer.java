package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.network.clientbound.CSatelliteBurnoutPacket;
import com.arrl.radiocraft.common.network.clientbound.CSatellitesPacket;
import com.arrl.radiocraft.common.radio.satellite.SatelliteBurnout;
import com.arrl.radiocraft.common.radio.satellite.SatelliteOrbit;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * Satellites passing over at night (2026-11-11, {@code doc/SatellitePlan.md}): each one above the horizon is a dot of a
 * pixel or two at its place in the sky, unseen but for a flash of green (amateur) or red (commercial) about
 * every six seconds, each flash a different brightness. In twilight a grey flash now and then: the satellite tumbling
 * in the sunlight. Nothing by day, under water or in another dimension, and it fades out in rain. Where each one is
 * comes from the world time ({@link SatelliteOrbit}), so this is a few sums a frame per satellite.
 * <p>
 * 2026-11-12: a geostationary satellite hangs still and flashes a third as often ({@link #FLASH_PERIOD_GEO}). A
 * decommissioned one no longer flashes or shows its grey dot: only near dusk and dawn, like a working one's glint, a
 * faint, irregular greyish-white glint now and then as it tumbles dead in the sunlight. A satellite whose orbit decays burns up as a one-pixel streak for three seconds
 * ({@link SatelliteBurnout}), seen only by players it was above the horizon of (the server only tells them).
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID, value = Dist.CLIENT)
public final class SatelliteSkyRenderer {

	private static final float RADIUS = 100.0F; // Same distance as the stars.
	/** Ticks between flashes (about six seconds), and how long one lasts. */
	private static final float FLASH_PERIOD = 120.0F, FLASH_TICKS = 6.0F;
	/** In twilight, the share of flash periods that also have a grey glint. */
	private static final float GLINT_CHANCE = 0.3F;
	/** Ticks between a geostationary satellite's flashes: about 18 seconds, a third as often (2026-11-12). */
	private static final float FLASH_PERIOD_GEO = 360.0F;
	/** A dead (decommissioned) satellite's tumble: a slot of this many ticks may hold one glint ... */
	private static final float TUMBLE_SLOT = 50.0F;
	/** ... with this chance, lasting up to {@link #TUMBLE_TICKS}, at most {@link #TUMBLE_BRIGHT}. */
	private static final float TUMBLE_CHANCE = 0.35F, TUMBLE_TICKS = 8.0F, TUMBLE_BRIGHT = 0.55F;
	/** A burnout streak: how bright its head is at most, and the trail behind it (dots, and ticks between them). */
	private static final float BURNOUT_BRIGHT = 0.6F;
	private static final int BURNOUT_TRAIL = 4;
	private static final float BURNOUT_TRAIL_STEP = 1.5F;

	private SatelliteSkyRenderer() {}

	@SubscribeEvent
	public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
		CSatellitesPacket.forget();
		CSatelliteBurnoutPacket.forget();
	}

	@SubscribeEvent
	public static void onRenderLevelStage(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY)
			return;
		List<CSatellitesPacket.Entry> satellites = CSatellitesPacket.received();
		ClientLevel level = Minecraft.getInstance().level;
		if(level == null || level.dimension() != Level.OVERWORLD)
			return;
		List<CSatelliteBurnoutPacket> burnouts = CSatelliteBurnoutPacket.burning(level.getGameTime());
		if(satellites.isEmpty() && burnouts.isEmpty())
			return;
		Camera camera = event.getCamera();
		float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
		float night = Mth.clamp(level.getStarBrightness(partialTick) * 2.0F, 0.0F, 1.0F);
		float visibility = night * (1.0F - level.getRainLevel(partialTick));
		if(visibility < 0.02F || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL || camera.getFluidInCamera() != FogType.NONE)
			return;
		boolean twilight = night < 0.8F;

		long gameTime = level.getGameTime();
		float t = gameTime + partialTick;
		double x = camera.getPosition().x, z = camera.getPosition().z;
		// One pixel at the sky's distance: the projection's 1 / tan(half the field of view) over half the screen height.
		float pixel = 2.0F * RADIUS / (Math.max(1.0E-3F, event.getProjectionMatrix().m11()) * Math.max(1, Minecraft.getInstance().getWindow().getHeight()));

		PoseStack poseStack = new PoseStack();
		poseStack.mulPose(event.getModelViewMatrix());
		Matrix4f matrix = poseStack.last().pose();
		BufferBuilder buffer = null;
		for(CSatellitesPacket.Entry satellite : satellites) {
			SatelliteOrbit.Look look = satellite.look(x, z, gameTime);
			if(!look.up() || look.elevation() < 0.5D)
				continue;
			int seed = satellite.name().hashCode();
			if(satellite.decommissioned()) { // Dead, tumbling: no light of its own, a faint glint now and then in twilight.
				if(!twilight)
					continue;
				float glint = tumble(seed, t) * (1.0F - night * 0.5F) * (1.0F - level.getRainLevel(partialTick));
				if(glint < 0.01F)
					continue;
				if(buffer == null)
					buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
				dot(buffer, matrix, look, (0.6F + 0.3F * glint) * pixel, 0.8F, 0.8F, 0.8F, Math.min(1.0F, glint));
				continue;
			}
			float period = satellite.geo() != null ? FLASH_PERIOD_GEO : FLASH_PERIOD;
			float phase = Math.floorMod(seed, (int)period);
			float cycleTime = t + phase;
			long cycle = (long)Math.floor(cycleTime / period);
			float into = cycleTime - cycle * period;
			float flash = flash(satellite.name(), satellite.geo() != null, t);
			// The green or red only in the flash; between flashes, and but for the occasional glint, it cannot be seen.
			float r, g, b, a;
			if(flash > 0.02F) { // The whole flash, fading right out.
				int colour = flashColour(satellite.amateur());
				r = (colour >> 16 & 0xFF) / 255.0F;
				g = (colour >> 8 & 0xFF) / 255.0F;
				b = (colour & 0xFF) / 255.0F;
				a = flash * visibility;
			}
			else { // Between flashes nothing at all (2026-11-14, the maintainer: it showed faintly all the way across).
				r = g = b = 0.7F;
				a = 0.0F;
			}
			float size = 0.75F + 0.5F * flash; // One pixel and a half, two in a bright flash.
			// The grey glint, halfway between flashes.
			float glintInto = into - period / 2.0F;
			if(twilight && glintInto >= 0.0F && glintInto < FLASH_TICKS * 1.5F && hash(seed * 31 + 7, cycle) < GLINT_CHANCE) {
				float glint = 1.0F - glintInto / (FLASH_TICKS * 1.5F);
				r = g = b = 0.8F;
				a = Math.max(a, glint * (1.0F - night * 0.5F) * (1.0F - level.getRainLevel(partialTick)));
				size = 0.75F + 0.6F * glint;
			}
			if(a < 0.01F)
				continue;
			if(buffer == null)
				buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
			dot(buffer, matrix, look, size * pixel, r, g, b, Math.min(1.0F, a));
		}
		// Burnouts (2026-11-12): a one-pixel head, white-orange, and a fainter trail behind it.
		for(CSatelliteBurnoutPacket burnout : burnouts)
			for(int i = 0; i <= BURNOUT_TRAIL; i++) {
				double progress = SatelliteBurnout.progress(burnout.start(), t - i * BURNOUT_TRAIL_STEP);
				if(progress < 0.0D)
					continue;
				SatelliteOrbit.Look look = SatelliteBurnout.look(x, z, burnout.subX(), burnout.subZ(), burnout.heading(), progress);
				if(look.elevation() < 0.5D)
					continue;
				float a = (float)SatelliteBurnout.brightness(SatelliteBurnout.progress(burnout.start(), t)) * BURNOUT_BRIGHT * visibility * (1.0F - i / (BURNOUT_TRAIL + 1.0F));
				if(a < 0.01F)
					continue;
				if(buffer == null)
					buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
				dot(buffer, matrix, look, 0.5F * pixel, 1.0F, i == 0 ? 0.85F : 0.6F, i == 0 ? 0.6F : 0.3F, Math.min(1.0F, a));
			}
		if(buffer == null)
			return;
		MeshData mesh = buffer.build();
		if(mesh == null)
			return;
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE); // Additive, as the stars.
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		FogRenderer.setupNoFog();
		BufferUploader.drawWithShader(mesh);
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}

	/**
	 * How bright a dead satellite's tumble glint is at {@code t}, 0 most of the time: each {@link #TUMBLE_SLOT} may hold
	 * one glint at a random moment in it, of a random length and brightness, so they come irregularly.
	 */
	private static float tumble(int seed, float t) {
		long slot = (long)Math.floor(t / TUMBLE_SLOT);
		if(hash(seed * 17 + 3, slot) >= TUMBLE_CHANCE)
			return 0.0F;
		float length = 2.0F + (TUMBLE_TICKS - 2.0F) * hash(seed * 13 + 5, slot);
		float start = (TUMBLE_SLOT - length) * hash(seed * 11 + 9, slot);
		float into = t - slot * TUMBLE_SLOT - start;
		if(into < 0.0F || into >= length)
			return 0.0F;
		return TUMBLE_BRIGHT * (0.3F + 0.7F * hash(seed * 7 + 1, slot)) * (1.0F - into / length);
	}

	/** A number from 0 to 1 for a satellite and a flash, the same every frame. */
	/**
	 * How bright satellite {@code name}'s light flashes at time {@code t} (game ticks, partial included): 0 between
	 * flashes, up to 1 at the start of one. Shared with SAT TRACK's picture of it (2026-11-14), so the light on the model
	 * blinks with the one in the sky.
	 */
	public static float flash(String name, boolean geo, float t) {
		int seed = name.hashCode();
		float period = geo ? FLASH_PERIOD_GEO : FLASH_PERIOD;
		float cycleTime = t + Math.floorMod(seed, (int)period);
		long cycle = (long)Math.floor(cycleTime / period);
		float into = cycleTime - cycle * period;
		return into < FLASH_TICKS ? (0.45F + 0.55F * hash(seed, cycle)) * (1.0F - into / FLASH_TICKS) : 0.0F;
	}

	/** The light's colour in a flash, 0xRRGGBB: green for an amateur satellite, red for a commercial one. */
	public static int flashColour(boolean amateur) {
		return amateur ? 0x59FF59 : 0xFF4D40;
	}

	private static float hash(int seed, long cycle) {
		long h = seed * 0x9E3779B97F4A7C15L + cycle * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (h & 0xFFFFFFL) / (float)0x1000000;
	}

	/** A square {@code half} across (each way from the middle) facing the camera, at the satellite's place in the sky. */
	private static void dot(BufferBuilder buffer, Matrix4f matrix, SatelliteOrbit.Look look, float half, float r, float g, float b, float a) {
		float az = (float)Math.toRadians(look.azimuth()), el = (float)Math.toRadians(look.elevation());
		// North is -Z and east +X: the azimuth turns from -Z towards +X.
		Vector3f dir = new Vector3f(Mth.sin(az) * Mth.cos(el), Mth.sin(el), -Mth.cos(az) * Mth.cos(el));
		Vector3f right = new Vector3f(dir).cross(0.0F, 1.0F, 0.0F);
		if(right.lengthSquared() < 1.0E-4F)
			right.set(1.0F, 0.0F, 0.0F);
		right.normalize().mul(half);
		Vector3f up = new Vector3f(right).cross(dir).normalize().mul(half);
		Vector3f centre = dir.mul(RADIUS);
		vertex(buffer, matrix, centre.x - right.x - up.x, centre.y - right.y - up.y, centre.z - right.z - up.z, r, g, b, a);
		vertex(buffer, matrix, centre.x + right.x - up.x, centre.y + right.y - up.y, centre.z + right.z - up.z, r, g, b, a);
		vertex(buffer, matrix, centre.x + right.x + up.x, centre.y + right.y + up.y, centre.z + right.z + up.z, r, g, b, a);
		vertex(buffer, matrix, centre.x - right.x + up.x, centre.y - right.y + up.y, centre.z - right.z + up.z, r, g, b, a);
	}

	private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float r, float g, float b, float a) {
		buffer.addVertex(matrix, x, y, z).setColor(r, g, b, a);
	}
}
