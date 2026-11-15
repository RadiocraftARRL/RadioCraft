package com.arrl.radiocraft.common.radio.antenna.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.UnknownNullability;

/** Where a yagi points: a compass bearing in degrees (0 north, 90 east), in 22.5 degree steps (2026-11-12). */
public class YagiAntennaData extends AntennaData {

	private double bearing;

	public YagiAntennaData(double bearing) {
		this.bearing = bearing;
	}

	public double getBearing() {
		return bearing;
	}

	public void setBearing(double bearing) {
		this.bearing = bearing;
	}

	@Override
	public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag nbt = new CompoundTag();
		nbt.putDouble("bearing", bearing);
		return nbt;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
		// Nothing: the bearing comes from the block (YagiAntennaType#match), which is what was placed. (Before
		// 2026-11-12 a "facing" was saved here that was never set, so every yagi beamed north.)
	}
}
