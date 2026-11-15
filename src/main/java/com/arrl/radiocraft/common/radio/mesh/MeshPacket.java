package com.arrl.radiocraft.common.radio.mesh;

import net.minecraft.nbt.CompoundTag;

/**
 * One burst on the radio mesh (2026-11-10). Pure.
 *
 * @param id       Random number, kept by every device that hears it so each relays it once.
 * @param system   Fire alarm or SCADA: the other system's devices ignore it.
 * @param type     What it says ({@code FIRE}, {@code ACK}, {@code POLL}...; each system has its own).
 * @param from     The address of the device that first sent it (a box number, an RTU id; 0 for a controller or master).
 * @param to       The address it is for (0 for the controller or master).
 * @param body     The rest, as the system writes it.
 * @param hops     Relays it has been through.
 * @param hopLimit Most relays it may go through.
 */
public record MeshPacket(long id, MeshSystem system, String type, int from, int to, String body, int hops, int hopLimit) {

	/** Most characters of body a burst carries. */
	public static final int BODY_MAX = 120;

	public MeshPacket {
		body = body == null ? "" : body.length() > BODY_MAX ? body.substring(0, BODY_MAX) : body;
		hops = Math.max(0, hops);
		hopLimit = Math.max(0, hopLimit);
	}

	/** The same packet one relay further along. */
	public MeshPacket relayed() {
		return new MeshPacket(id, system, type, from, to, body, hops + 1, hopLimit);
	}

	/** True if a device that hears it may send it on. */
	public boolean mayRelay() {
		return hops < hopLimit;
	}

	/** Fields of a body, split on '|'. */
	public String[] fields() {
		return body.split("\\|", -1);
	}

	/** Field {@code i} of the body as a number, {@code fallback} if it is missing or not one. */
	public int intField(int i, int fallback) {
		String[] fields = fields();
		if(i >= fields.length)
			return fallback;
		try {
			return Integer.parseInt(fields[i].trim());
		}
		catch(NumberFormatException e) {
			return fallback;
		}
	}

	/** Field {@code i} of the body, empty if missing. */
	public String field(int i) {
		String[] fields = fields();
		return i < fields.length ? fields[i] : "";
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("id", id);
		tag.putString("system", system.name());
		tag.putString("type", type);
		tag.putInt("from", from);
		tag.putInt("to", to);
		tag.putString("body", body);
		tag.putInt("hops", hops);
		tag.putInt("hopLimit", hopLimit);
		return tag;
	}

	public static MeshPacket load(CompoundTag tag) {
		MeshSystem system;
		try {
			system = MeshSystem.valueOf(tag.getString("system"));
		}
		catch(IllegalArgumentException e) {
			system = MeshSystem.FIRE;
		}
		return new MeshPacket(tag.getLong("id"), system, tag.getString("type"), tag.getInt("from"), tag.getInt("to"), tag.getString("body"),
				tag.getInt("hops"), tag.getInt("hopLimit"));
	}
}
