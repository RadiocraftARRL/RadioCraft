package com.arrl.radiocraft.common.network.serverbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.RealWorldInterfaceBlockEntity;
import com.arrl.radiocraft.common.menus.RealWorldInterfaceMenu;
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

import java.util.Locale;

/**
 * A change on a Real-World Interface's screen (its menu open): pick an interface by name (empty for none), tune to a
 * frequency typed in MHz, FM or SSB, Everywhere on or off. The block checks {@code real_world_interface_op_only}; a
 * refusal comes back to the player over the hotbar.
 */
public record SRealWorldInterfacePacket(long pos, int action, String text) implements CustomPacketPayload {

	public enum Action { SELECT, FREQUENCY, MODE, EVERYWHERE }

	public static final Type<SRealWorldInterfacePacket> TYPE = new Type<>(Radiocraft.id("s_real_world_interface"));

	public static final StreamCodec<ByteBuf, SRealWorldInterfacePacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, SRealWorldInterfacePacket::pos,
			ByteBufCodecs.VAR_INT, SRealWorldInterfacePacket::action,
			ByteBufCodecs.stringUtf8(64), SRealWorldInterfacePacket::text,
			SRealWorldInterfacePacket::new);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(IPayloadContext context) {
		context.enqueueWork(() -> {
			if(!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof RealWorldInterfaceMenu menu)
					|| menu.blockEntity.getBlockPos().asLong() != pos || action < 0 || action >= Action.values().length)
				return;
			RealWorldInterfaceBlockEntity block = menu.blockEntity;
			Component refused = null;
			Component noRight = Component.translatable(Radiocraft.translationKey("message", "real_world_interface.op_only"));
			switch(Action.values()[action]) {
				case SELECT -> refused = block.select(text.trim(), player);
				case FREQUENCY -> {
					double mhz;
					try {
						mhz = Double.parseDouble(text.trim().replace(',', '.'));
					}
					catch(NumberFormatException e) {
						mhz = -1.0D;
					}
					if(!RealWorldInterfaceBlockEntity.mayEdit(player))
						refused = noRight;
					else if(!(mhz > 0.0D) || !block.setTunedFrequency(mhz * 1_000_000.0D, player))
						refused = Component.translatable(Radiocraft.translationKey("message", "real_world_interface.bad_frequency"), text.trim());
				}
				case MODE -> {
					if(!block.setFm(text.trim().toUpperCase(Locale.ROOT).equals("FM"), player))
						refused = noRight;
				}
				case EVERYWHERE -> {
					if(!block.setEverywhere(text.trim().equals("1"), player))
						refused = noRight;
				}
			}
			if(refused != null)
				player.displayClientMessage(refused, true);
			block.sendStatus(player);
		});
	}

	public static void send(BlockPos pos, Action action, String text) {
		PacketDistributor.sendToServer(new SRealWorldInterfacePacket(pos.asLong(), action.ordinal(), text));
	}
}
