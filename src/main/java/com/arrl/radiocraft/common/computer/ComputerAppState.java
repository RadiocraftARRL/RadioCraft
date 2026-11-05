package com.arrl.radiocraft.common.computer;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * What one program keeps on one computer between requests on the server (1.12 step 7): the APRS stations heard, a fax
 * going out, a picture coming in. Get it with {@code RadioComputerBlockEntity#state(app, factory)}; it lives as long as
 * the computer's block entity, and what {@link #save} writes goes with the computer's save (not with its item: a
 * computer broken and placed again starts its programs' states afresh, its files stay). Server thread only.
 */
public interface ComputerAppState {

	/** Writes what should survive the world closing into {@code tag}; nothing by default. */
	default void save(CompoundTag tag, HolderLookup.Provider registries) {}

	/** Reads what {@link #save} wrote (called once, when the state is made, if the save had any). */
	default void load(CompoundTag tag, HolderLookup.Provider registries) {}
}
