package com.arrl.radiocraft.common.network.serverbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.mesh.MeshDeviceBlockEntity;
import com.arrl.radiocraft.common.network.PacketChecks;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
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

import java.util.Locale;

/**
 * A fire alarm or SCADA device's setup screen's SET (2026-11-11): the frequency in Hz, the callsign it goes out under
 * and its location name. Checked here ({@link MeshDeviceBlockEntity#setupProblem}); the player hears what happened.
 */
public record SMeshSetupPacket(long pos, float hz, String callsign, String label) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SMeshSetupPacket> TYPE = new CustomPacketPayload.Type<>(Radiocraft.id("s_mesh_setup"));

	public static final StreamCodec<ByteBuf, SMeshSetupPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, SMeshSetupPacket::pos,
			ByteBufCodecs.FLOAT, SMeshSetupPacket::hz,
			ByteBufCodecs.stringUtf8(16), SMeshSetupPacket::callsign,
			ByteBufCodecs.stringUtf8(48), SMeshSetupPacket::label,
			SMeshSetupPacket::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void send(BlockPos pos, float hz, String callsign, String label) {
		PacketDistributor.sendToServer(new SMeshSetupPacket(pos.asLong(), hz, callsign, label));
	}

	public static void handle(SMeshSetupPacket packet, IPayloadContext context) {
		context.enqueueWork(() -> {
			if(!(context.player() instanceof ServerPlayer player))
				return;
			BlockPos pos = BlockPos.of(packet.pos());
			if(!PacketChecks.inReach(player, pos, 2.0D)
					|| !(player.level().getBlockEntity(pos) instanceof MeshDeviceBlockEntity device))
				return;
			String callsign = packet.callsign().trim().toUpperCase(Locale.ROOT);
			String problem = MeshDeviceBlockEntity.setupProblem(packet.hz(), callsign);
			if(problem != null) {
				player.displayClientMessage(Component.translatable("message.radiocraft.mesh_setup." + problem).withStyle(ChatFormatting.RED), false);
				return;
			}
			device.setLabel(player.serverLevel().getServer(), packet.label());
			boolean changed = device.setFrequency(player.serverLevel().getServer(), packet.hz(), callsign);
			player.displayClientMessage(Component.translatable("message.radiocraft.mesh_setup." + (changed ? "done" : "same"), CommercialLicenseRules.mhz(device.getFrequency()),
					device.getLicense(), device.getAddress()).withStyle(ChatFormatting.GREEN), false);
		});
	}
}
