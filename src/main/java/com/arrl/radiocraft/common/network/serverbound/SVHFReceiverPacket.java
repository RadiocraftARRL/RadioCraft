package com.arrl.radiocraft.common.network.serverbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.menus.VHFReceiverMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * The VHF receiver's own buttons (2026-11-09): a memory button pressed or shift-clicked, and the mute button. Only a
 * player with that receiver's screen open can, and the memory buttons only work while it is switched on.
 */
public record SVHFReceiverPacket(long pos, int action, int value) implements CustomPacketPayload {

	/** A numbered button: select, let go, or store into an empty channel ({@code value} is the channel, 0 to 5). */
	public static final int PRESS_MEMORY = 0;
	/** Shift + click on a numbered button: store the frequency shown in it. */
	public static final int STORE_MEMORY = 1;
	/** The speaker button: {@code value} 1 mutes, 0 unmutes. */
	public static final int SET_MUTED = 2;

	public static final Type<SVHFReceiverPacket> TYPE = new Type<>(Radiocraft.id("s_vhf_receiver"));

	public static final StreamCodec<ByteBuf, SVHFReceiverPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, SVHFReceiverPacket::pos,
			ByteBufCodecs.VAR_INT, SVHFReceiverPacket::action,
			ByteBufCodecs.VAR_INT, SVHFReceiverPacket::value,
			SVHFReceiverPacket::new);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(IPayloadContext context) {
		context.enqueueWork(() -> {
			if(!(context.player().containerMenu instanceof VHFReceiverMenu menu) || !menu.blockEntity.getBlockPos().equals(BlockPos.of(pos)))
				return;
			switch(action) {
				case PRESS_MEMORY -> {
					if(menu.blockEntity.isPowered())
						menu.blockEntity.pressMemory(value);
				}
				case STORE_MEMORY -> {
					if(menu.blockEntity.isPowered())
						menu.blockEntity.storeMemory(value);
				}
				case SET_MUTED -> menu.blockEntity.setMuted(value != 0);
				default -> {}
			}
		});
	}

	public static void send(BlockPos pos, int action, int value) {
		PacketDistributor.sendToServer(new SVHFReceiverPacket(pos.asLong(), action, value));
	}
}
