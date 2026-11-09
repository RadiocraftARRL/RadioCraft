package com.arrl.radiocraft.common.capabilities;

import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.common.MutableDataComponentHolder;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;

/**
 * A Small Alkaline Battery's charge (2026-11-08): full until it is first used (no component yet means a fresh cell),
 * and it can only be drawn from. Nothing charges it: alkaline cells are not rechargeable.
 */
public class AlkalineEnergyStorage extends ComponentEnergyStorage {

	private final MutableDataComponentHolder holder;
	private final DataComponentType<Integer> component;

	public AlkalineEnergyStorage(MutableDataComponentHolder holder, DataComponentType<Integer> component, int capacity) {
		super(holder, component, capacity, 0, capacity);
		this.holder = holder;
		this.component = component;
	}

	@Override
	public int getEnergyStored() {
		return holder.has(component) ? super.getEnergyStored() : getMaxEnergyStored();
	}

	@Override
	public int receiveEnergy(int toReceive, boolean simulate) {
		return 0;
	}

	@Override
	public boolean canReceive() {
		return false;
	}
}
