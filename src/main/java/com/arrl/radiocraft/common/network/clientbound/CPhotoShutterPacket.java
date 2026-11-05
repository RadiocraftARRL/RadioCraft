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
 * The camera's shutter went (see {@link com.arrl.radiocraft.common.items.CameraItem}): the client grabs the next frame,
 * makes the photo ({@code PhotoCapture}) and sends it up with {@code nonce} as
 * {@link com.arrl.radiocraft.common.network.serverbound.SPhotoChunkPacket}s.
 *
 * @param nonce what each chunk must carry back
 * @param colour colour photo paper (else black and white: the client makes the greys)
 */
public record CPhotoShutterPacket(int nonce, boolean colour) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<CPhotoShutterPacket> TYPE = new CustomPacketPayload.Type<>(Radiocraft.id("c_photo_shutter"));

	public static final StreamCodec<ByteBuf, CPhotoShutterPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, CPhotoShutterPacket::nonce,
			ByteBufCodecs.BOOL, CPhotoShutterPacket::colour,
			CPhotoShutterPacket::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(final CPhotoShutterPacket packet, final IPayloadContext context) {
		context.enqueueWork(() -> com.arrl.radiocraft.client.photo.PhotoCapture.takePhoto(packet.nonce(), packet.colour()));
	}

	public static void send(ServerPlayer player, int nonce, boolean colour) {
		PacketDistributor.sendToPlayer(player, new CPhotoShutterPacket(nonce, colour));
	}
}
