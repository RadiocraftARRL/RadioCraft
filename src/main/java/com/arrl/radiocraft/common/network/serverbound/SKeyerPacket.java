package com.arrl.radiocraft.common.network.serverbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blocks.IambicKeyerBlock;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** The iambic keyer's front panel (2026-11-12): its MODE, SPEED and SIDETONE, kept in its block state. */
public record SKeyerPacket(BlockPos pos, int mode, int speed, int sidetone) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SKeyerPacket> TYPE = new CustomPacketPayload.Type<>(Radiocraft.id("s_keyer"));

	public static final StreamCodec<ByteBuf, SKeyerPacket> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, SKeyerPacket::pos,
			ByteBufCodecs.VAR_INT, SKeyerPacket::mode,
			ByteBufCodecs.VAR_INT, SKeyerPacket::speed,
			ByteBufCodecs.VAR_INT, SKeyerPacket::sidetone,
			SKeyerPacket::new);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(final SKeyerPacket packet, final IPayloadContext context) {
		context.enqueueWork(() -> {
			if(!com.arrl.radiocraft.common.network.PacketChecks.inReach(context.player(), packet.pos(), 2.0D))
				return;
			BlockState state = context.player().level().getBlockState(packet.pos());
			if(!(state.getBlock() instanceof IambicKeyerBlock))
				return;
			IambicKeyerBlock.KeyerMode[] modes = IambicKeyerBlock.KeyerMode.values();
			BlockState set = state.setValue(IambicKeyerBlock.MODE, modes[Math.floorMod(packet.mode(), modes.length)])
					.setValue(IambicKeyerBlock.SPEED, Math.max(1, Math.min(4, packet.speed())))
					.setValue(IambicKeyerBlock.SIDETONE, Math.max(0, Math.min(3, packet.sidetone())));
			context.player().level().setBlock(packet.pos(), set, Block.UPDATE_ALL);
		});
	}
}
