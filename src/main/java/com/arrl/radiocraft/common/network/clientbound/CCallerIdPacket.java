package com.arrl.radiocraft.common.network.clientbound;

import com.arrl.radiocraft.Radiocraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * A commercial handheld of this player's heard unit {@code unitId} talking on {@code rxHz} (1.12 playtest fixes 2):
 * its display shows the caller's ID, or the alias its contact list gives it, while the unit talks and for a few
 * seconds after. Sent when a new unit is heard and again every second or so while it keeps talking.
 */
public record CCallerIdPacket(float rxHz, int unitId) implements CustomPacketPayload {

	public static final Type<CCallerIdPacket> TYPE = new Type<>(Radiocraft.id("c_caller_id"));

	public static final StreamCodec<ByteBuf, CCallerIdPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, CCallerIdPacket::rxHz,
			ByteBufCodecs.VAR_INT, CCallerIdPacket::unitId,
			CCallerIdPacket::new);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(final CCallerIdPacket packet, final IPayloadContext context) {
		context.enqueueWork(() -> com.arrl.radiocraft.client.CallerIds.heard(packet.rxHz(), packet.unitId()));
	}

	public static void send(ServerPlayer player, float rxHz, int unitId) {
		PacketDistributor.sendToPlayer(player, new CCallerIdPacket(rxHz, unitId));
	}
}
