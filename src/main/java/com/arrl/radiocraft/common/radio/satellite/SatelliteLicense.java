package com.arrl.radiocraft.common.radio.satellite;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A satellite license (2026-11-11), on the item a contract hands out with the satellite: its name, its frequencies
 * (uplink, downlink and, since 2026-11-12, telemetry), the tone it was launched with and the day it went up. A keepsake once the satellite has come down.
 *
 * @param name       The satellite ({@code RO-3}).
 * @param amateur    Amateur or commercial.
 * @param uplinkHz   Its uplink, Hz.
 * @param downlinkHz Its downlink, Hz.
 * @param telemetryHz Its telemetry beacon, Hz (0 on a license from before 2026-11-12).
 * @param tone       The CTCSS tone it was launched with, Hz (its owner may change it later).
 * @param holder     Whose it is.
 * @param launchDay  The day it was launched, {@code 2026-11-11}.
 */
public record SatelliteLicense(String name, boolean amateur, float uplinkHz, float downlinkHz, float telemetryHz, float tone, String holder, String launchDay) {

	public SatelliteLicense {
		name = name == null ? "" : name;
		holder = holder == null ? "" : holder;
		launchDay = launchDay == null ? "" : launchDay;
	}

	public static SatelliteLicense of(Satellite satellite) {
		return new SatelliteLicense(satellite.name, satellite.amateur, satellite.uplinkHz, satellite.downlinkHz, satellite.telemetryHz(), satellite.tone(), satellite.ownerName,
				satellite.launchDay);
	}

	public static final Codec<SatelliteLicense> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("name").forGetter(SatelliteLicense::name),
			Codec.BOOL.optionalFieldOf("amateur", true).forGetter(SatelliteLicense::amateur),
			Codec.FLOAT.optionalFieldOf("uplink", 0.0F).forGetter(SatelliteLicense::uplinkHz),
			Codec.FLOAT.optionalFieldOf("downlink", 0.0F).forGetter(SatelliteLicense::downlinkHz),
			Codec.FLOAT.optionalFieldOf("telemetry", 0.0F).forGetter(SatelliteLicense::telemetryHz),
			Codec.FLOAT.optionalFieldOf("tone", 0.0F).forGetter(SatelliteLicense::tone),
			Codec.STRING.optionalFieldOf("holder", "").forGetter(SatelliteLicense::holder),
			Codec.STRING.optionalFieldOf("launched", "").forGetter(SatelliteLicense::launchDay)
	).apply(instance, SatelliteLicense::new));

	public static final StreamCodec<ByteBuf, SatelliteLicense> STREAM_CODEC = StreamCodec.of(
			(buf, l) -> {
				ByteBufCodecs.stringUtf8(32).encode(buf, l.name);
				buf.writeBoolean(l.amateur);
				buf.writeFloat(l.uplinkHz);
				buf.writeFloat(l.downlinkHz);
				buf.writeFloat(l.telemetryHz);
				buf.writeFloat(l.tone);
				ByteBufCodecs.stringUtf8(64).encode(buf, l.holder);
				ByteBufCodecs.stringUtf8(16).encode(buf, l.launchDay);
			},
			buf -> new SatelliteLicense(ByteBufCodecs.stringUtf8(32).decode(buf), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
					ByteBufCodecs.stringUtf8(64).decode(buf), ByteBufCodecs.stringUtf8(16).decode(buf)));
}
