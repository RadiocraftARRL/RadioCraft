package com.arrl.radiocraft.common.radio.distress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A distress beacon's state (2026-11-09), the {@code radiocraft:distress_beacon} data component: whether its arming
 * cover is open, whether it is on (it never goes off again), the battery left ({@link BeaconBattery}), the alert's
 * number and whose beacon it is. No game classes beyond the codecs, so it unit tests without Minecraft.
 *
 * @param armed     The arming cover is open: holding right click switches it on.
 * @param active    Transmitting.
 * @param remaining Ticks of battery left.
 * @param alertId   The number every alert from this beacon carries (0 until switched on).
 * @param owner     The name of the player who switched it on.
 */
public record BeaconState(boolean armed, boolean active, int remaining, long alertId, String owner) {

	/** New: shut, off, full. */
	public static final BeaconState NEW = new BeaconState(false, false, BeaconBattery.LIFE_TICKS, 0L, "");

	public BeaconState {
		owner = owner == null ? "" : owner;
		remaining = Math.max(0, Math.min(BeaconBattery.LIFE_TICKS, remaining));
	}

	public static final Codec<BeaconState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("armed", false).forGetter(BeaconState::armed),
			Codec.BOOL.optionalFieldOf("active", false).forGetter(BeaconState::active),
			Codec.INT.optionalFieldOf("remaining", BeaconBattery.LIFE_TICKS).forGetter(BeaconState::remaining),
			Codec.LONG.optionalFieldOf("alert_id", 0L).forGetter(BeaconState::alertId),
			Codec.STRING.optionalFieldOf("owner", "").forGetter(BeaconState::owner)
	).apply(instance, BeaconState::new));

	public static final StreamCodec<ByteBuf, BeaconState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, BeaconState::armed,
			ByteBufCodecs.BOOL, BeaconState::active,
			ByteBufCodecs.VAR_INT, BeaconState::remaining,
			ByteBufCodecs.VAR_LONG, BeaconState::alertId,
			ByteBufCodecs.STRING_UTF8, BeaconState::owner,
			BeaconState::new);

	/** The cover opened or shut (nothing once it is on). */
	public BeaconState withArmed(boolean open) {
		return active ? this : new BeaconState(open, false, remaining, alertId, owner);
	}

	/** Switched on by {@code who}, its alerts numbered {@code id}; only from armed. */
	public BeaconState activated(long id, String who) {
		return active || !armed ? this : new BeaconState(true, true, remaining, id, who);
	}

	/** {@code ticks} more of transmitting. */
	public BeaconState drained(int ticks) {
		return new BeaconState(armed, active, BeaconBattery.drain(remaining, ticks), alertId, owner);
	}

	/** The alert it sends from {@code x, y, z}: active, or spent once the battery is out. */
	public DistressAlert alert(int x, int y, int z, long time) {
		return new DistressAlert(alertId, DistressAlert.Type.BEACON, BeaconBattery.spent(remaining) ? DistressAlert.State.SPENT : DistressAlert.State.ACTIVE,
				owner, DistressNature.UNDESIGNATED, x, y, z, time, "");
	}
}
