package com.arrl.radiocraft.common.network.clientbound;

import com.arrl.radiocraft.Radiocraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * From the radio computer's server side to the screen of the player using it (1.12 step 5): {@code kind} is "reply"
 * (the answer to one request, matched by its "rid"; see {@link com.arrl.radiocraft.common.computer.ComputerReply}) or
 * "event" (news a program pushes unasked, with its "app"). A file read is at most 256 KB, well under the 1 MB a packet
 * from the server may hold.
 */
public record CComputerPacket(String kind, CompoundTag data) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<CComputerPacket> TYPE = new CustomPacketPayload.Type<>(Radiocraft.id("c_computer"));

	public static final StreamCodec<ByteBuf, CComputerPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, CComputerPacket::kind,
			ByteBufCodecs.COMPOUND_TAG, CComputerPacket::data,
			CComputerPacket::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(final CComputerPacket packet, final IPayloadContext context) {
		context.enqueueWork(() -> com.arrl.radiocraft.client.screens.computer.RadioComputerScreen.receive(packet.kind(), packet.data()));
	}

	public static void send(ServerPlayer player, String kind, CompoundTag data) {
		PacketDistributor.sendToPlayer(player, new CComputerPacket(kind, data));
	}
}
