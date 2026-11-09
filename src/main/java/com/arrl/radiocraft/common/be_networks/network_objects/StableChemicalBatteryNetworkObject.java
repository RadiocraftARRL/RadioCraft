package com.arrl.radiocraft.common.be_networks.network_objects;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.capabilities.IBENetworks;
import com.arrl.radiocraft.common.capabilities.BasicEnergyStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The Stable Chemical Battery on the power wire (2026-11-09): a large battery that is never charged and gives nothing
 * until its seal is broken. Its storage ({@link SealedStorage}) takes no energy from anything (charge controllers,
 * other batteries, Forge Energy), and gives none while sealed, so the power network's sharing
 * ({@link com.arrl.radiocraft.api.benetworks.PowerBENetwork}) sees a full battery with no room and nothing to spare.
 * Once active it discharges like any large battery, until flat.
 */
public class StableChemicalBatteryNetworkObject extends BatteryNetworkObject {

	public static final ResourceLocation TYPE = Radiocraft.id("stable_chemical_battery");

	public StableChemicalBatteryNetworkObject(Level level, BlockPos pos, int capacity, int maxReceive, int maxExtract) {
		super(level, pos, capacity, maxReceive, maxExtract);
		energyStorage = new SealedStorage(capacity, maxExtract, 0);
	}

	/** True once the seal is broken: it gives power. */
	public boolean isActive() {
		return ((SealedStorage)energyStorage).active;
	}

	/** Breaks the seal (or, for a battery being restored, says whether it was broken). */
	public void setActive(boolean active) {
		((SealedStorage)energyStorage).active = active;
	}

	@Override
	public void load(IBENetworks cap, CompoundTag nbt) {
		boolean active = isActive();
		super.load(cap, nbt); // Replaces the storage with a plain one: put the sealed kind back.
		energyStorage = new SealedStorage(energyStorage.getMaxEnergyStored(), energyStorage.getMaxExtract(), energyStorage.getEnergyStored());
		setActive(active);
	}

	@Override
	public ResourceLocation getType() {
		return TYPE;
	}

	/** An energy store that is never charged, and gives energy only once {@link #active}. */
	public static class SealedStorage extends BasicEnergyStorage {

		private boolean active = false;

		public SealedStorage(int capacity, int maxExtract, int energy) {
			super(capacity, 0, maxExtract, energy);
		}

		@Override
		public int receiveEnergy(int toReceive, boolean simulate) {
			return 0;
		}

		@Override
		public int extractEnergy(int toExtract, boolean simulate) {
			return active ? super.extractEnergy(toExtract, simulate) : 0;
		}

		@Override
		public boolean canReceive() {
			return false;
		}

		@Override
		public boolean canExtract() {
			return active && super.canExtract();
		}
	}
}
