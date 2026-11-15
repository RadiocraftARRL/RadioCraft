package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.api.antenna.AntennaTypes;
import com.arrl.radiocraft.api.antenna.IAntenna;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.SatelliteTrackerNetworkObject;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.radio.antenna.StaticAntenna;
import com.arrl.radiocraft.common.radio.antenna.networks.AntennaNetworkManager;
import com.arrl.radiocraft.common.radio.satellite.SatelliteTracking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The satellite tracking antenna (2026-11-12): an antenna on the coax, and an az/el mount whose motors run on a power
 * wire ({@link SatelliteTrackerNetworkObject} turns it). Keeps where the dish points, which satellite it follows and
 * whether it is locked on; clients are told when the aim moves a degree or more, and the renderer slews smoothly
 * between ({@link #renderAzimuth}, {@link #renderElevation}).
 */
public final class SatelliteTrackerBlockEntity extends AntennaBlockEntity implements GeoBlockEntity {

	/** Clients hear of a move once it adds up to this many degrees. */
	private static final double SYNC_DEGREES = 1.0D;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private float azimuth = 0.0F;
	private float elevation = (float)SatelliteTracking.STOW_ELEVATION;
	private String tracking = "";
	private boolean locked = false;
	private float sentAzimuth = Float.NaN, sentElevation = Float.NaN;

	/** Client: where the model is drawn, slewing toward the last aim heard. */
	public float renderAzimuth = Float.NaN, renderElevation = Float.NaN;
	/** Client: the game time (with the partial tick) the render aim was last moved. */
	public double renderAt = -1.0D;

	public SatelliteTrackerBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.SATELLITE_TRACKER.get(), pos, state, AntennaNetworkManager.VHF_ID);
	}

	public float getAzimuth() {
		return azimuth;
	}

	public float getElevation() {
		return elevation;
	}

	/** The satellite it follows, or empty. */
	public String getTracking() {
		return tracking;
	}

	/** True while it is on its satellite. */
	public boolean isLocked() {
		return locked;
	}

	/** Points the dish (server, by the mount's motors). */
	public void aim(double az, double el) {
		azimuth = (float)az;
		elevation = (float)el;
		setChanged();
		if(Float.isNaN(sentAzimuth) || Math.abs(SatelliteTracking.azimuthDiff(sentAzimuth, azimuth)) >= SYNC_DEGREES || Math.abs(sentElevation - elevation) >= SYNC_DEGREES)
			sync();
	}

	/** Which satellite it follows and whether it is on it (server). */
	public void setTracking(String name, boolean locked) {
		if(name.equals(tracking) && locked == this.locked)
			return;
		tracking = name;
		this.locked = locked;
		setChanged();
		sync();
	}

	private void sync() {
		if(level == null || level.isClientSide)
			return;
		sentAzimuth = azimuth;
		sentElevation = elevation;
		level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	@Override
	public BENetworkObject createNetworkObject() {
		SatelliteTrackerNetworkObject object = new SatelliteTrackerNetworkObject(level, worldPosition);
		if(level != null) {
			IAntenna antenna = AntennaTypes.match(level, worldPosition);
			if(antenna instanceof StaticAntenna<?> staticAntenna)
				object.setAntenna(staticAntenna);
		}
		return object;
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if(level != null && !level.isClientSide)
			SatelliteTracking.setLocked(worldPosition.asLong(), null);
	}

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putFloat("azimuth", azimuth);
		tag.putFloat("elevation", elevation);
		tag.putString("tracking", tracking);
		tag.putBoolean("locked", locked);
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.loadAdditional(tag, registries);
		if(tag.contains("azimuth"))
			azimuth = tag.getFloat("azimuth");
		if(tag.contains("elevation"))
			elevation = tag.getFloat("elevation");
		tracking = tag.getString("tracking");
		locked = tag.getBoolean("locked");
	}

	@Override
	public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// Driven by the aim in the renderer, not by animations.
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
