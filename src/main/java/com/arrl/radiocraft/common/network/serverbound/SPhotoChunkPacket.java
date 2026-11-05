package com.arrl.radiocraft.common.network.serverbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.photo.PhotoUpload;
import com.arrl.radiocraft.common.photo.PhotoUploads;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * A quarter of a photo (32 rows of map colour ids) going up after {@link com.arrl.radiocraft.common.network.clientbound.CPhotoShutterPacket}.
 * The codec refuses more than {@link PhotoUpload#CHUNK_BYTES}; {@link PhotoUploads} drops chunks for no open upload,
 * repeats and late ones.
 *
 * @param nonce the shutter's nonce
 * @param index which chunk, 0 to {@link PhotoUpload#CHUNKS} - 1
 * @param data the chunk's {@link PhotoUpload#CHUNK_BYTES} map colour ids
 */
public record SPhotoChunkPacket(int nonce, int index, byte[] data) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SPhotoChunkPacket> TYPE = new CustomPacketPayload.Type<>(Radiocraft.id("s_photo_chunk"));

	public static final StreamCodec<ByteBuf, SPhotoChunkPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, SPhotoChunkPacket::nonce,
			ByteBufCodecs.VAR_INT, SPhotoChunkPacket::index,
			ByteBufCodecs.byteArray(PhotoUpload.CHUNK_BYTES), SPhotoChunkPacket::data,
			SPhotoChunkPacket::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(final SPhotoChunkPacket packet, final IPayloadContext context) {
		context.enqueueWork(() -> {
			if(context.player() instanceof ServerPlayer player)
				PhotoUploads.accept(player, packet.nonce(), packet.index(), packet.data());
		});
	}

	/** Sends {@code pixels} (a whole photo) up as its chunks. */
	public static void sendPhoto(int nonce, byte[] pixels) {
		for(int index = 0; index < PhotoUpload.CHUNKS; index++)
			PacketDistributor.sendToServer(new SPhotoChunkPacket(nonce, index, PhotoUpload.chunk(pixels, index)));
	}
}
