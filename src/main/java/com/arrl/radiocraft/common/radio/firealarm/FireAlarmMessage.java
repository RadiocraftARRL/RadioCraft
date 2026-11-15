package com.arrl.radiocraft.common.radio.firealarm;

import com.arrl.radiocraft.common.radio.mesh.MeshNode;
import com.arrl.radiocraft.common.radio.mesh.MeshPacket;
import com.arrl.radiocraft.common.radio.mesh.MeshSystem;

/**
 * What fire alarm boxes and controllers say on the mesh (2026-11-10). Pure. A box's message is "zone|seq|label" (the
 * label so a controller that never heard the box still shows where it is); a controller's ack is "seq", to the box.
 */
public final class FireAlarmMessage {

	/** A pull station was pulled (or the box's redstone input came on). */
	public static final String FIRE = "FIRE";
	/** A pulled station was reset with the key (or the redstone input went off). */
	public static final String RESTORE = "RESTORE";
	/** The key turned in the box: a test, logged and printed but not an alarm. */
	public static final String TEST = "TEST";
	/** The box was broken off its mounting: its last burst. */
	public static final String TAMPER = "TAMPER";
	/** The box's daily check-in. */
	public static final String CHECKIN = "CHECKIN";
	/** The controller's ack of a box's message. */
	public static final String ACK = "ACK";

	private FireAlarmMessage() {}

	/** A box's message. */
	public static MeshPacket box(long id, String type, int box, int zone, int seq, String label, int hopLimit) {
		return box(id, type, box, zone, seq, label, "", AlarmKind.FIRE, hopLimit);
	}

	public static MeshPacket box(long id, String type, int box, int zone, int seq, String label, String zoneName, int hopLimit) {
		return box(id, type, box, zone, seq, label, zoneName, AlarmKind.FIRE, hopLimit);
	}

	/** A box's message about a named location and its kind of alarm: "zone|seq|label|location name|KIND" (2026-11-11). */
	public static MeshPacket box(long id, String type, int box, int zone, int seq, String label, String zoneName, AlarmKind kind, int hopLimit) {
		String body = zone + "|" + seq + "|" + MeshNode.clean(label);
		String name = MeshNode.clean(zoneName);
		if(kind != AlarmKind.FIRE)
			body += "|" + name + "|" + kind.name();
		else if(!name.isEmpty())
			body += "|" + name;
		return new MeshPacket(id, MeshSystem.FIRE, type, box, 0, body, 0, hopLimit);
	}

	/** The kind of alarm in a box's message: FIRE unless it says otherwise. */
	public static AlarmKind kind(MeshPacket packet) {
		return AlarmKind.of(packet.field(4));
	}

	/** The station's name in a box's message, empty if it has none (or the box predates names). */
	public static String zoneName(MeshPacket packet) {
		return MeshNode.clean(packet.field(3));
	}

	/** A controller's ack of message {@code seq} from {@code box}. */
	public static MeshPacket ack(long id, int box, int seq, int hopLimit) {
		return new MeshPacket(id, MeshSystem.FIRE, ACK, 0, box, Integer.toString(seq), 0, hopLimit);
	}

	public static int zone(MeshPacket packet) {
		return packet.intField(0, 0);
	}

	public static int seq(MeshPacket packet) {
		return packet.type().equals(ACK) ? packet.intField(0, -1) : packet.intField(1, -1);
	}

	public static String label(MeshPacket packet) {
		return packet.field(2);
	}

	/** True if a box tries a message again until acked: all but the check-in, which gives up and waits for tomorrow. */
	public static boolean retriesForever(String type) {
		return !type.equals(CHECKIN);
	}

	/** Ticks to wait before try {@code tries} + 1: a few seconds at first, then longer, at most a minute. */
	public static long backoff(int tries) {
		return Math.min(1200L, 60L << Math.min(5, Math.max(0, tries - 1)));
	}
}
