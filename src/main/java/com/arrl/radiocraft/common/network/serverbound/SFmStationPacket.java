package com.arrl.radiocraft.common.network.serverbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.FmBroadcastTransmitterBlockEntity;
import com.arrl.radiocraft.common.menus.SimpleRadioMenu;
import com.arrl.radiocraft.common.radio.station.FmStation;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * A setting changed on an FM broadcast station's panel (2026-11-14): {@code action} names it, {@code text} or
 * {@code value} carries it. Only a player with that transmitter's screen open: {@code program} (value 0 tape, 1
 * stream), {@code forecast} (minutes), {@code station_id} (value 0/1), {@code duck} (percent), {@code callsign} and
 * {@code url} (text). The stream's address takes the server's leave ({@code fm_internet_streams}) and its owner (who
 * placed it; anyone for one placed before owners were kept; an operator always).
 */
public record SFmStationPacket(long pos, String action, String text, int value) implements CustomPacketPayload {

	public static final Type<SFmStationPacket> TYPE = new Type<>(Radiocraft.id("s_fm_station"));

	public static final StreamCodec<ByteBuf, SFmStationPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, SFmStationPacket::pos,
			ByteBufCodecs.STRING_UTF8, SFmStationPacket::action,
			ByteBufCodecs.STRING_UTF8, SFmStationPacket::text,
			ByteBufCodecs.VAR_INT, SFmStationPacket::value,
			SFmStationPacket::new);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(IPayloadContext context) {
		context.enqueueWork(() -> {
			if(!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof SimpleRadioMenu menu)
					|| !(menu.blockEntity instanceof FmBroadcastTransmitterBlockEntity station) || !station.getBlockPos().equals(BlockPos.of(pos)))
				return;
			apply(player, station, action, text, value);
		});
	}

	/** Carries out a setting (server). */
	public static void apply(ServerPlayer player, FmBroadcastTransmitterBlockEntity station, String action, String text, int value) {
		switch(action) {
			case "program" -> station.setProgram(value == 1 ? FmStation.Program.STREAM : FmStation.Program.TAPE);
			case "forecast" -> station.setForecastMinutes(value);
			case "station_id" -> station.setStationIdOn(value != 0);
			case "duck" -> station.setDuckLevel(value / 100.0F);
			case "callsign" -> station.setCallsign(text);
			case "url" -> {
				if(!FmBroadcastTransmitterBlockEntity.streamsAllowed()) {
					player.displayClientMessage(Component.translatable("radiocraft.fm_station.streams_off"), true);
					return;
				}
				if(station.getOwner() != null && !station.getOwner().equals(player.getUUID()) && !player.hasPermissions(2)) {
					player.displayClientMessage(Component.translatable("radiocraft.fm_station.not_owner"), true);
					return;
				}
				station.setStreamUrl(text);
			}
			default -> {}
		}
	}

	public static void send(BlockPos pos, String action, String text, int value) {
		PacketDistributor.sendToServer(new SFmStationPacket(pos.asLong(), action, text == null ? "" : text, value));
	}
}
