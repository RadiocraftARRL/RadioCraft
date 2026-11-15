package com.arrl.radiocraft.common.be_networks.network_objects;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.api.benetworks.PowerMember;
import com.arrl.radiocraft.api.capabilities.IBENetworks;
import com.arrl.radiocraft.common.blockentities.SatelliteTrackerBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.capabilities.BasicEnergyStorage;
import com.arrl.radiocraft.common.radio.antenna.networks.AntennaNetworkManager;
import com.arrl.radiocraft.common.radio.satellite.Moon;
import com.arrl.radiocraft.common.radio.satellite.Satellite;
import com.arrl.radiocraft.common.radio.satellite.SatelliteOrbit;
import com.arrl.radiocraft.common.radio.satellite.SatelliteTracking;
import com.arrl.radiocraft.common.radio.satellite.Satellites;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The satellite tracking antenna's end of the coax (2026-11-12), which also sits on a power wire ({@link PowerMember}):
 * its az/el mount's motors and controller run on {@code satellite_tracker_power_use} a tick while it tracks or slews.
 * Every {@link #PICK_TICKS} it picks a satellite: the one its radio is tuned to if that one is up, else the highest one
 * up. Then it turns and tilts after it ({@link SatelliteTracking}), and notes itself locked on once it is on it.
 * Unpowered or with nothing up, it stows (straight up) and is locked on nothing.
 * <p>
 * SATTRACK can pick its target instead ({@link #setChosen}, 2026-11-12): a satellite by name, or the {@link Moon} for
 * moonbounce. It then follows only that one, stowed while it is down.
 */
public class SatelliteTrackerNetworkObject extends AntennaNetworkObject implements PowerMember {

	public static final ResourceLocation TYPE = Radiocraft.id("satellite_tracker");

	private static final int STORAGE = 200;
	/** Ticks between looks for a satellite to follow. */
	private static final int PICK_TICKS = 10;

	private final BasicEnergyStorage storage = new BasicEnergyStorage(STORAGE, STORAGE, STORAGE);
	private volatile boolean powered = false;
	private String target = "";
	/** What SATTRACK told it to follow (a satellite's name or {@link Moon#NAME}), or empty to choose for itself. */
	private String chosen = "";
	private double targetAz = 0.0D, targetEl = SatelliteTracking.STOW_ELEVATION;

	public SatelliteTrackerNetworkObject(Level level, BlockPos pos) {
		super(level, pos, AntennaNetworkManager.VHF_ID);
	}

	@Override
	public BasicEnergyStorage getStorage() {
		return storage;
	}

	@Override
	public boolean isIndirectConsumer() {
		return true;
	}

	@Override
	public boolean isDirectConsumer() {
		return true;
	}

	/** True if its motors had power this tick. */
	public boolean isPowered() {
		return powered;
	}

	/** What SATTRACK told it to follow, or empty when it chooses for itself. */
	public String getChosen() {
		return chosen;
	}

	/** Follow {@code name} (a satellite or {@link Moon#NAME}) from now on; empty: choose for itself again. */
	public void setChosen(String name) {
		chosen = name == null ? "" : name.toUpperCase(java.util.Locale.ROOT);
		target = "";
	}

	/** The satellite it follows (locked on or still slewing), or empty. */
	public String getTarget() {
		return target;
	}

	@Override
	public void tick(Level level, BlockPos pos) {
		super.tick(level, pos);
		if(level.isClientSide || !(level.getBlockEntity(pos) instanceof SatelliteTrackerBlockEntity mount))
			return;
		long now = level.getGameTime();
		if((now + pos.asLong()) % PICK_TICKS == 0L)
			pick(level, pos, now);

		boolean free = !RadiocraftServerConfig.RADIO_POWER_REQUIRED.get();
		boolean moving = !target.isEmpty() || mount.getElevation() < SatelliteTracking.STOW_ELEVATION - 0.01D;
		powered = free || storage.extractEnergy(RadiocraftServerConfig.SATELLITE_TRACKER_POWER_USE.get(), true) >= RadiocraftServerConfig.SATELLITE_TRACKER_POWER_USE.get();
		if(!powered) { // Dead motors: it stops where it is, on nothing.
			SatelliteTracking.setLocked(pos.asLong(), null);
			mount.setTracking("", false);
			return;
		}
		if(moving && !free)
			storage.extractEnergy(RadiocraftServerConfig.SATELLITE_TRACKER_POWER_USE.get(), false);
		if(target.isEmpty()) {
			targetEl = SatelliteTracking.STOW_ELEVATION;
			targetAz = mount.getAzimuth();
		}
		else if(target.equals(Moon.NAME)) {
			SatelliteOrbit.Look look = Moon.look(level.getTimeOfDay(1.0F));
			targetAz = look.azimuth();
			targetEl = Math.max(0.0D, look.elevation());
		}
		else {
			Satellite satellite = Satellites.byName(target);
			if(satellite != null) { // Follow it between picks.
				SatelliteOrbit.Look look = satellite.look(pos.getX() + 0.5D, pos.getZ() + 0.5D, now);
				targetAz = look.azimuth();
				targetEl = Math.max(0.0D, look.elevation());
			}
		}
		double az = SatelliteTracking.slewAzimuth(mount.getAzimuth(), targetAz, SatelliteTracking.SLEW_DEGREES);
		double el = SatelliteTracking.slewElevation(mount.getElevation(), targetEl, SatelliteTracking.SLEW_DEGREES);
		mount.aim(az, el);
		boolean locked = !target.isEmpty() && SatelliteTracking.onTarget(az, el, targetAz, targetEl);
		SatelliteTracking.setLocked(pos.asLong(), locked ? target : null);
		mount.setTracking(target, locked);
	}

	/** Picks the satellite to follow: the radio's, if it is up; else the highest one up; else none. */
	private void pick(Level level, BlockPos pos, long now) {
		if(chosen.equals(Moon.NAME)) {
			target = Moon.usable(level.getTimeOfDay(1.0F)) ? Moon.NAME : "";
			return;
		}
		if(!chosen.isEmpty()) {
			Satellite satellite = Satellites.byName(chosen);
			target = satellite != null && satellite.look(pos.getX() + 0.5D, pos.getZ() + 0.5D, now).up() ? satellite.name : "";
			return;
		}
		float radioHz = 0.0F;
		for(RadioNetworkObject radio : getRadios())
			if(radio.isPowered && level.isLoaded(radio.getPos()) && level.getBlockEntity(radio.getPos()) instanceof RadioBlockEntity be) {
				radioHz = be.getFrequency();
				break;
			}
		Satellite tuned = null, highest = null;
		double best = 0.0D;
		for(Satellite satellite : Satellites.onOrbit()) {
			SatelliteOrbit.Look look = satellite.look(pos.getX() + 0.5D, pos.getZ() + 0.5D, now);
			if(!look.up())
				continue;
			if(!satellite.decommissioned() && SatelliteTracking.tunedTo(radioHz, satellite.downlinkHz, satellite.uplinkHz))
				tuned = satellite;
			if(look.elevation() > best) {
				best = look.elevation();
				highest = satellite;
			}
		}
		Satellite chosen = tuned != null ? tuned : highest;
		target = chosen == null ? "" : chosen.name;
	}

	@Override
	public void save(CompoundTag nbt) {
		super.save(nbt);
		storage.saveAdditional(nbt);
		nbt.putString("chosen", chosen);
	}

	@Override
	public void load(IBENetworks cap, CompoundTag nbt) {
		super.load(cap, nbt);
		storage.setEnergy(Math.min(STORAGE, nbt.getInt("energy")));
		chosen = nbt.getString("chosen");
	}

	@Override
	public void discard() {
		super.discard();
		SatelliteTracking.setLocked(pos.asLong(), null);
	}

	@Override
	public ResourceLocation getType() {
		return TYPE;
	}
}
