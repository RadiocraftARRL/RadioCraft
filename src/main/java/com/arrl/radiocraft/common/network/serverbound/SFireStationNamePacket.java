package com.arrl.radiocraft.common.network.serverbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.mesh.FireAlarmBoxBlockEntity;
import com.arrl.radiocraft.common.network.PacketChecks;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
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
 * A fire alarm box's programming panel sets one of its locations (2026-11-11): the box, the location's number, its name
 * (blank clears it) and its kind of alarm (FIRE, MEDICAL...). The location must be on the box's data wire.
 */
public record SFireStationNamePacket(long pos, int zone, String name, String kind) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SFireStationNamePacket> TYPE = new CustomPacketPayload.Type<>(Radiocraft.id("s_fire_station_name"));

	public static final StreamCodec<ByteBuf, SFireStationNamePacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, SFireStationNamePacket::pos,
			ByteBufCodecs.VAR_INT, SFireStationNamePacket::zone,
			ByteBufCodecs.stringUtf8(64), SFireStationNamePacket::name,
			ByteBufCodecs.stringUtf8(16), SFireStationNamePacket::kind,
			SFireStationNamePacket::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void send(BlockPos pos, int zone, String name, com.arrl.radiocraft.common.radio.firealarm.AlarmKind kind) {
		PacketDistributor.sendToServer(new SFireStationNamePacket(pos.asLong(), zone, name, kind.name()));
	}

	public static void handle(SFireStationNamePacket packet, IPayloadContext context) {
		context.enqueueWork(() -> {
			if(!(context.player() instanceof ServerPlayer player))
				return;
			BlockPos pos = BlockPos.of(packet.pos());
			if(!PacketChecks.inReach(player, pos, 2.0D)
					|| !(player.level().getBlockEntity(pos) instanceof FireAlarmBoxBlockEntity box))
				return;
			if(!box.setStation(player.serverLevel(), packet.zone(), packet.name(), com.arrl.radiocraft.common.radio.firealarm.AlarmKind.of(packet.kind())))
				player.displayClientMessage(Component.translatable("message.radiocraft.fire_alarm_box.no_station", String.format(java.util.Locale.ROOT, "%02d", packet.zone()))
						.withStyle(ChatFormatting.RED), true);
		});
	}
}
