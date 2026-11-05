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
 * What an ADS-B beacon's or receiver's screen shows (1.12 step 9), sent once a second to the players who have that
 * block's screen open: the beacon's status ({@code AdsbBeaconBlockEntity#statusTag}) or the receiver's picture
 * ({@code AdsbPicture}); and a receiver's compact picture ({@code AdsbPicture#compact}) to the players near it, for
 * the scope drawn on its tube in the world.
 */
public record CAdsbPacket(long pos, CompoundTag data) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<CAdsbPacket> TYPE = new CustomPacketPayload.Type<>(Radiocraft.id("c_adsb"));

	public static final StreamCodec<ByteBuf, CAdsbPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, CAdsbPacket::pos,
			ByteBufCodecs.COMPOUND_TAG, CAdsbPacket::data,
			CAdsbPacket::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(final CAdsbPacket packet, final IPayloadContext context) {
		context.enqueueWork(() -> {
			com.arrl.radiocraft.client.screens.adsb.AdsbScreens.update(BlockPos.of(packet.pos()), packet.data());
			if(packet.data().contains("targets")) // A receiver's picture (full or compact): its tube in the world.
				com.arrl.radiocraft.client.render.AdsbScopes.update(BlockPos.of(packet.pos()), packet.data());
		});
	}

	public static void send(ServerPlayer player, BlockPos pos, CompoundTag data) {
		PacketDistributor.sendToPlayer(player, new CAdsbPacket(pos.asLong(), data));
	}
}
