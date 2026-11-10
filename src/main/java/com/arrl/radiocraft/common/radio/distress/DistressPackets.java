package com.arrl.radiocraft.common.radio.distress;

import com.arrl.radiocraft.common.radio.data.AprsIcon;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Distress alerts on the air (2026-11-09): a {@link DistressAlert} goes out as a {@link TncPacket} of kind
 * {@link TncPacket.Kind#DISTRESS}, the same data packets APRS and the BBS use, so it travels as far as any VHF signal
 * from that antenna and height would. DSC bursts are on channel 70, beacon alerts on 161.975 MHz (AIS 1, where real
 * AIS-SART and man-overboard beacons send).
 */
public final class DistressPackets {

	private DistressPackets() {}

	/** Channel 70, DSC: 156.525 MHz. */
	public static final float DSC_HZ = 156_525_000.0F;
	/** AIS channel 1, where the distress beacon sends its alerts and homing tone: 161.975 MHz. */
	public static final float BEACON_HZ = 161_975_000.0F;
	/** How far off a frequency a burst still decodes: inside one 25 kHz marine channel. */
	public static final float TOLERANCE_HZ = 12_500.0F;

	/** True if {@code hz} is channel 70. */
	public static boolean isDsc(float hz) {
		return Math.abs(hz - DSC_HZ) < TOLERANCE_HZ;
	}

	/** True if {@code hz} is the beacon frequency. */
	public static boolean isBeacon(float hz) {
		return Math.abs(hz - BEACON_HZ) < TOLERANCE_HZ;
	}

	/** Where {@code alert} goes out: channel 70 for a DSC call, 161.975 for a beacon. */
	public static float frequency(DistressAlert alert) {
		return alert.isDsc() ? DSC_HZ : BEACON_HZ;
	}

	/** {@code alert} as a packet (packet {@code packetId}, random per burst so digipeaters treat each burst on its own). */
	public static TncPacket packet(long packetId, DistressAlert alert) {
		return new TncPacket(packetId, TncPacket.Kind.DISTRESS, TncPacket.sanitizeCallsign(alert.owner()), "", alert.encode(),
				new BlockPos(alert.x(), alert.y(), alert.z()), AprsIcon.HOUSE.name(), 0, ItemStack.EMPTY);
	}

	/** The alert a packet carries, or null if it carries none (another kind, or garbled). */
	@Nullable
	public static DistressAlert alert(TncPacket packet) {
		return packet.kind() == TncPacket.Kind.DISTRESS ? DistressAlert.decode(packet.text()) : null;
	}

	/** A beacon's homing tone: a packet with nothing in it, for direction finders. */
	public static TncPacket homing(long packetId) {
		return new TncPacket(packetId, TncPacket.Kind.HOMING, "", "", "", null, AprsIcon.HOUSE.name(), 0, ItemStack.EMPTY);
	}
}
