package com.arrl.radiocraft.common.network.serverbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.items.CameraItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * The player left clicked while holding a camera up: take the photo. The server only fires the shutter if the player
 * really is aiming a camera ({@link CameraItem#fireShutter} checks the rest).
 */
public record SCameraShutterPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SCameraShutterPacket> TYPE = new CustomPacketPayload.Type<>(Radiocraft.id("s_camera_shutter"));
    public static final SCameraShutterPacket INSTANCE = new SCameraShutterPacket();
    public static final StreamCodec<ByteBuf, SCameraShutterPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if(context.player() instanceof ServerPlayer player && player.isUsingItem() && player.getUseItem().getItem() instanceof CameraItem)
                CameraItem.fireShutter(player, player.getUsedItemHand());
        });
    }

    public static void send() {
        PacketDistributor.sendToServer(INSTANCE);
    }
}
