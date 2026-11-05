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
 * What a Real-World Interface's screen shows about its link (1.12 step 11): the interfaces to pick from, the devices,
 * PTT, VOX/COS and levels ({@code RealWorldInterfaceBlockEntity#statusTag}). Sent four times a second, only to the
 * players with that block's screen open, and once as it opens.
 */
public record CRealWorldInterfacePacket(long pos, CompoundTag data) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<CRealWorldInterfacePacket> TYPE = new CustomPacketPayload.Type<>(Radiocraft.id("c_real_world_interface"));

	public static final StreamCodec<ByteBuf, CRealWorldInterfacePacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, CRealWorldInterfacePacket::pos,
			ByteBufCodecs.COMPOUND_TAG, CRealWorldInterfacePacket::data,
			CRealWorldInterfacePacket::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(final CRealWorldInterfacePacket packet, final IPayloadContext context) {
		context.enqueueWork(() -> com.arrl.radiocraft.client.screens.RealWorldInterfaceScreen.update(BlockPos.of(packet.pos()), packet.data()));
	}

	public static void send(ServerPlayer player, BlockPos pos, CompoundTag data) {
		PacketDistributor.sendToPlayer(player, new CRealWorldInterfacePacket(pos.asLong(), data));
	}
}
