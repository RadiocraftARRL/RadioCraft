package com.arrl.radiocraft.common.radio.antenna.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.UnknownNullability;

/** A vertical on a Vertical Antenna Base (2026-11-18): the poles standing on it and the band its coil is tapped for. */
public class VerticalAntennaData extends AntennaData {

	private int poles;
	private int tapMetres;

	public VerticalAntennaData(int poles, int tapMetres) {
		this.poles = poles;
		this.tapMetres = tapMetres;
	}

	public int getPoles() {
		return poles;
	}

	/** The band the coil is tapped for, in metres (10, 20, 40, 80). */
	public int getTapMetres() {
		return tapMetres;
	}

	@Override
	public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag nbt = new CompoundTag();
		nbt.putInt("poles", poles);
		nbt.putInt("tap", tapMetres);
		return nbt;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
		poles = nbt.getInt("poles");
		tapMetres = nbt.getInt("tap");
	}
}
