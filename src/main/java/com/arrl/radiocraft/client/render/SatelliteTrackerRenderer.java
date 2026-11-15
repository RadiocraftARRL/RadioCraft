package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.SatelliteTrackerBlockEntity;
import com.arrl.radiocraft.common.radio.satellite.SatelliteTracking;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Draws the satellite tracking antenna (2026-11-12, geo/block/satellite_tracker.geo.json): the plinth stays put, the
 * {@code turntable} turns to the azimuth and the dish's {@code lnb_arm} tilts to the elevation, as on the microwave dish
 * ({@link SatelliteDishRenderer#REST_ELEVATION}). Between the server's updates it slews smoothly toward the last aim
 * heard at the mount's own speed, so the turning reads as motors, not jumps.
 */
public class SatelliteTrackerRenderer extends GeoBlockRenderer<SatelliteTrackerBlockEntity> {

	public SatelliteTrackerRenderer(BlockEntityRendererProvider.Context context) {
		super(new Model());
	}

	@Override
	protected void rotateBlock(Direction facing, PoseStack poseStack) {
		// The plinth is square and never turns; the turntable does the turning.
	}

	@Override
	public AABB getRenderBoundingBox(SatelliteTrackerBlockEntity tracker) {
		return new AABB(tracker.getBlockPos()).inflate(1.0D);
	}

	/** Moves the drawn aim toward the block entity's, at the mount's slew speed. */
	static void slew(SatelliteTrackerBlockEntity tracker, double now) {
		if(Float.isNaN(tracker.renderAzimuth) || tracker.renderAt < 0.0D) {
			tracker.renderAzimuth = tracker.getAzimuth();
			tracker.renderElevation = tracker.getElevation();
			tracker.renderAt = now;
			return;
		}
		double step = Math.max(0.0D, Math.min(20.0D, now - tracker.renderAt)) * SatelliteTracking.SLEW_DEGREES;
		tracker.renderAt = now;
		tracker.renderAzimuth = (float)SatelliteTracking.slewAzimuth(tracker.renderAzimuth, tracker.getAzimuth(), step);
		tracker.renderElevation = (float)SatelliteTracking.slewElevation(tracker.renderElevation, tracker.getElevation(), step);
	}

	public static class Model extends DefaultedBlockGeoModel<SatelliteTrackerBlockEntity> {
		public Model() {
			super(Radiocraft.id("satellite_tracker"));
		}

		@Override
		public void setCustomAnimations(SatelliteTrackerBlockEntity tracker, long instanceId, AnimationState<SatelliteTrackerBlockEntity> state) {
			super.setCustomAnimations(tracker, instanceId, state);
			if(tracker.getLevel() != null)
				slew(tracker, tracker.getLevel().getGameTime() + state.getPartialTick());
			float az = Float.isNaN(tracker.renderAzimuth) ? tracker.getAzimuth() : tracker.renderAzimuth;
			float el = Float.isNaN(tracker.renderElevation) ? tracker.getElevation() : tracker.renderElevation;
			GeoBone turntable = getAnimationProcessor().getBone("turntable");
			if(turntable != null) // As the microwave dish turns: minus the bearing about Y, the model facing north unturned.
				turntable.setRotY((float)Math.toRadians(-az));
			GeoBone arm = getAnimationProcessor().getBone("lnb_arm");
			if(arm != null)
				arm.setRotZ(arm.getInitialSnapshot().getRotZ() + (float)Math.toRadians(el - SatelliteDishRenderer.REST_ELEVATION));
		}
	}
}
