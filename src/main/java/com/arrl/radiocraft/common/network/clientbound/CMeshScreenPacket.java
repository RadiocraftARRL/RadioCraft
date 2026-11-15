package com.arrl.radiocraft.common.network.clientbound;

import com.arrl.radiocraft.Radiocraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * What a fire alarm controller's or SCADA master's green screen shows (2026-11-11): its status as the FIRE ALARM and
 * SCADA programs have it ({@code FireAlarmHandler#status}, {@code ScadaHandler#status}), sent every second to players
 * near it (for the screen drawn on the block) and twice a second to those with its screen open.
 */
public record CMeshScreenPacket(long pos, CompoundTag data) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<CMeshScreenPacket> TYPE = new CustomPacketPayload.Type<>(Radiocraft.id("c_mesh_screen"));

	public static final StreamCodec<ByteBuf, CMeshScreenPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, CMeshScreenPacket::pos,
			ByteBufCodecs.COMPOUND_TAG, CMeshScreenPacket::data,
			CMeshScreenPacket::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(final CMeshScreenPacket packet, final IPayloadContext context) {
		context.enqueueWork(() -> com.arrl.radiocraft.client.screens.mesh.MeshScreens.update(BlockPos.of(packet.pos()), packet.data()));
	}

	public static void send(ServerPlayer player, BlockPos pos, CompoundTag data) {
		PacketDistributor.sendToPlayer(player, new CMeshScreenPacket(pos.asLong(), data));
	}
}
