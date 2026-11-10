package com.arrl.radiocraft.common.network.serverbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.VHFReceiverBlockEntity;
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
 * The VHF receiver's own controls (2026-11-09, the scanner): a channel switch, the SET and MAN/AUTO keys, and the TUNE
 * knob. Only a player with that receiver's screen open can. The switches and keys move with the power off, as switches
 * do; TUNE only works while it runs with its SET lamp lit. VOLUME (with OFF) and SQUELCH go as every radio's do
 * ({@link SBlockRadioControlPacket}).
 */
public record SVHFReceiverPacket(long pos, int action, int value) implements CustomPacketPayload {

	/** A channel switch: {@code value} is the channel (0 to 7) times two, plus 1 for up (on). */
	public static final int SET_CHANNEL = 0;
	/** The SET key: {@code value} 1 in, 0 out. */
	public static final int SET_KEY = 1;
	/** The MAN/AUTO key: {@code value} 1 MAN (hold the scan), 0 AUTO. */
	public static final int HOLD_KEY = 2;
	/** TUNE turned: {@code value} is how far, in Hz (at most a full turn of the range either way). */
	public static final int TUNE = 3;

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
			VHFReceiverBlockEntity receiver = menu.blockEntity;
			switch(action) {
				case SET_CHANNEL -> receiver.setChannelOn(value >> 1, (value & 1) != 0);
				case SET_KEY -> receiver.setSetMode(value != 0);
				case HOLD_KEY -> receiver.setScanHold(value != 0);
				case TUNE -> {
					int range = (int)(VHFReceiverBlockEntity.MAX_HZ - VHFReceiverBlockEntity.MIN_HZ);
					if(receiver.isPowered())
						receiver.tuneSetChannel(Math.max(-range, Math.min(range, value)));
				}
				default -> {}
			}
		});
	}

	public static void send(BlockPos pos, int action, int value) {
		PacketDistributor.sendToServer(new SVHFReceiverPacket(pos.asLong(), action, value));
	}
}
