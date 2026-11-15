package com.arrl.radiocraft.common.network.clientbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.radio.satellite.SatelliteBurnout;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A satellite burning up as its orbit decays (2026-11-12, {@link SatelliteBurnout}), sent only to a player it is above
 * the horizon of: the point under it as they see it, which way it was going and the game time it began. The sky
 * renderer plays the streak for {@link SatelliteBurnout#TICKS}.
 *
 * @param subX    The point under it, x.
 * @param subZ    And z.
 * @param heading Which way it was going, radians from +X towards +Z.
 * @param start   The game time it began burning.
 */
public record CSatelliteBurnoutPacket(float subX, float subZ, float heading, long start) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<CSatelliteBurnoutPacket> TYPE = new CustomPacketPayload.Type<>(Radiocraft.id("c_satellite_burnout"));

	public static final StreamCodec<ByteBuf, CSatelliteBurnoutPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, CSatelliteBurnoutPacket::subX,
			ByteBufCodecs.FLOAT, CSatelliteBurnoutPacket::subZ,
			ByteBufCodecs.FLOAT, CSatelliteBurnoutPacket::heading,
			ByteBufCodecs.VAR_LONG, CSatelliteBurnoutPacket::start,
			CSatelliteBurnoutPacket::new
	);

	/** Most streaks kept at once on a client. */
	private static final int MAX = 16;

	/** On a client: the streaks in the sky now (read by {@code SatelliteSkyRenderer}, on the render thread). */
	private static volatile List<CSatelliteBurnoutPacket> burning = List.of();

	/** On a client: the streaks still burning at {@code time} (older ones are dropped). */
	public static List<CSatelliteBurnoutPacket> burning(double time) {
		List<CSatelliteBurnoutPacket> now = burning;
		if(now.isEmpty() || now.stream().allMatch(b -> SatelliteBurnout.burning(b.start, time) || b.start > time))
			return now;
		List<CSatelliteBurnoutPacket> kept = now.stream().filter(b -> SatelliteBurnout.burning(b.start, time) || b.start > time).toList();
		burning = kept;
		return kept;
	}

	/** On a client leaving a world: none. */
	public static void forget() {
		burning = List.of();
	}

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(final CSatelliteBurnoutPacket packet, final IPayloadContext context) {
		context.enqueueWork(() -> {
			List<CSatelliteBurnoutPacket> next = new ArrayList<>(burning);
			if(next.size() >= MAX)
				next.removeFirst();
			next.add(packet);
			burning = List.copyOf(next);
		});
	}

	/** Tells {@code player} of a burnout over {@code subX, subZ} that began at {@code start}. */
	public static void sendTo(ServerPlayer player, double subX, double subZ, double heading, long start) {
		PacketDistributor.sendToPlayer(player, new CSatelliteBurnoutPacket((float)subX, (float)subZ, (float)heading, start));
	}
}
