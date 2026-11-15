package com.arrl.radiocraft.common.network.clientbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.radio.BandScope;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * One waterfall row for an External CRT Monitor on a premium radio (2026-11-12), from the monitor to the players near:
 * whether it shows, its span ({@code CrtMonitorBlockEntity.Span}), the frequencies across it and the dial, the game
 * time of the row, whether the client should start over ({@code fresh}: the first row this player gets) and the row,
 * {@link BandScope#BINS} levels (0 to {@code Waterfall.MAX_LEVEL}; empty when off). Sent only when something changed.
 *
 * @param pos the monitor
 */
public record CCrtWaterfallPacket(BlockPos pos, boolean on, int span, double lowHz, double highHz, double dialHz, long gameTime, boolean fresh,
								  byte[] row) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<CCrtWaterfallPacket> TYPE = new CustomPacketPayload.Type<>(Radiocraft.id("c_crt_waterfall"));

	private record Head(BlockPos pos, boolean on, int span, double lowHz, double highHz, double dialHz) {}

	private static final StreamCodec<ByteBuf, Head> HEAD = StreamCodec.composite(
			BlockPos.STREAM_CODEC, Head::pos,
			ByteBufCodecs.BOOL, Head::on,
			ByteBufCodecs.VAR_INT, Head::span,
			ByteBufCodecs.DOUBLE, Head::lowHz,
			ByteBufCodecs.DOUBLE, Head::highHz,
			ByteBufCodecs.DOUBLE, Head::dialHz,
			Head::new);

	public static final StreamCodec<ByteBuf, CCrtWaterfallPacket> STREAM_CODEC = StreamCodec.composite(
			HEAD, p -> new Head(p.pos, p.on, p.span, p.lowHz, p.highHz, p.dialHz),
			ByteBufCodecs.VAR_LONG, CCrtWaterfallPacket::gameTime,
			ByteBufCodecs.BOOL, CCrtWaterfallPacket::fresh,
			ByteBufCodecs.byteArray(BandScope.BINS), CCrtWaterfallPacket::row,
			(head, gameTime, fresh, row) -> new CCrtWaterfallPacket(head.pos(), head.on(), head.span(), head.lowHz(), head.highHz(), head.dialHz(), gameTime, fresh, row));

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(final CCrtWaterfallPacket packet, final IPayloadContext context) {
		context.enqueueWork(() -> com.arrl.radiocraft.client.render.CrtMonitorWaterfalls.receive(packet));
	}
}
