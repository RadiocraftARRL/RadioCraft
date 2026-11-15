package com.arrl.radiocraft.common.radio.mesh;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A device on the radio mesh as the server remembers it (2026-11-10), whether its chunk is loaded or not: where it is,
 * what it is, its frequency and address, and whether an antenna was on its coax when it was last loaded. The block
 * entity keeps this up to date while it is loaded; the mesh relays through it and sends for it from here when it is not.
 *
 * @param pos      Where the block is.
 * @param role     Box, controller, RTU or master.
 * @param hz       The frequency it sends and listens on (the license's).
 * @param address  Box number or RTU id (0 for a controller or master).
 * @param external True if an antenna was on its coax (normal range); false with its built-in one (poor range).
 * @param label    What it is called: a box's location ("342 MAIN ST"), from the block's name.
 * @param license  The callsign of the license whose frequency it was set to.
 */
public record MeshNode(GlobalPos pos, MeshRole role, float hz, int address, boolean external, String label, String license) {

	/** Most characters of a label sent over the air. */
	public static final int LABEL_MAX = 32;
	/** How far apart two frequencies may be and still be the same channel, Hz. */
	public static final float SAME_CHANNEL_HZ = 1000.0F;

	public MeshNode {
		label = clean(label == null ? "" : label);
		license = license == null ? "" : license;
	}

	public MeshSystem system() {
		return role.system();
	}

	/** True if it is on {@code otherHz}. */
	public boolean on(float otherHz) {
		return Math.abs(hz - otherHz) < SAME_CHANNEL_HZ;
	}

	public MeshNode withAddress(int newAddress) {
		return new MeshNode(pos, role, hz, newAddress, external, label, license);
	}

	public MeshNode withExternal(boolean newExternal) {
		return new MeshNode(pos, role, hz, address, newExternal, label, license);
	}

	public MeshNode withLabel(String newLabel) {
		return new MeshNode(pos, role, hz, address, external, newLabel, license);
	}

	/** Printable ASCII in upper case, '|' replaced (it separates fields on the air), at most {@link #LABEL_MAX} characters. */
	public static String clean(String text) {
		StringBuilder out = new StringBuilder();
		for(char c : text.toCharArray()) {
			if(c >= 32 && c < 127)
				out.append(c == '|' ? '/' : Character.toUpperCase(c));
			if(out.length() >= LABEL_MAX)
				break;
		}
		return out.toString().strip();
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		savePos(tag, pos);
		tag.putString("role", role.name());
		tag.putFloat("hz", hz);
		tag.putInt("address", address);
		tag.putBoolean("external", external);
		tag.putString("label", label);
		tag.putString("license", license);
		return tag;
	}

	@Nullable
	public static MeshNode load(CompoundTag tag) {
		GlobalPos pos = loadPos(tag);
		if(pos == null)
			return null;
		MeshRole role;
		try {
			role = MeshRole.valueOf(tag.getString("role"));
		}
		catch(IllegalArgumentException e) {
			return null;
		}
		return new MeshNode(pos, role, tag.getFloat("hz"), tag.getInt("address"), tag.getBoolean("external"), tag.getString("label"), tag.getString("license"));
	}

	/** Writes {@code pos} into {@code tag} as "dim" and "pos". */
	public static void savePos(CompoundTag tag, GlobalPos pos) {
		tag.putString("dim", pos.dimension().location().toString());
		tag.putLong("pos", pos.pos().asLong());
	}

	@Nullable
	public static GlobalPos loadPos(CompoundTag tag) {
		ResourceLocation dim = ResourceLocation.tryParse(tag.getString("dim"));
		if(dim == null || !tag.contains("pos"))
			return null;
		return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(tag.getLong("pos")));
	}
}
