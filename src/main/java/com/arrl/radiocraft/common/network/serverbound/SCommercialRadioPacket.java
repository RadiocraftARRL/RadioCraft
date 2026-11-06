package com.arrl.radiocraft.common.network.serverbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity;
import com.arrl.radiocraft.common.items.CommercialHandheldItem;
import com.arrl.radiocraft.common.menus.CommercialMobileMenu;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * The user worked a commercial radio's controls: the handheld in inventory slot {@code slot}, or (with {@code slot} -1)
 * the mobile at {@code pos} whose screen they have open. {@code action} {@link #CHANNEL}: the channel knob to
 * {@code value} (a 0-based position); {@link #VOLUME} (handhelds): the power/volume knob to {@code value} (0 = off,
 * see {@link CommercialHandheldItem#setVolumeKnob}); {@link #ZONE} (the upgraded handheld's A-D keys): zone
 * {@code value} (0 = A). Turning knobs is all a user can do to a commercial radio's channels; programming
 * goes through {@link com.arrl.radiocraft.common.radio.commercial.CommercialProgramming}.
 */
public record SCommercialRadioPacket(int slot, long pos, int action, int value) implements CustomPacketPayload {

	public static final int CHANNEL = 0, VOLUME = 1, ZONE = 2;

	public static final Type<SCommercialRadioPacket> TYPE = new Type<>(Radiocraft.id("s_commercial_radio"));

	public static final StreamCodec<ByteBuf, SCommercialRadioPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, SCommercialRadioPacket::slot,
			ByteBufCodecs.VAR_LONG, SCommercialRadioPacket::pos,
			ByteBufCodecs.VAR_INT, SCommercialRadioPacket::action,
			ByteBufCodecs.VAR_INT, SCommercialRadioPacket::value,
			SCommercialRadioPacket::new);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(IPayloadContext context) {
		context.enqueueWork(() -> apply(context.player()));
	}

	/** What the packet does for {@code player} (on the server thread; GameTests call it with a mock player). */
	public void apply(Player player) {
		if(slot >= 0) {
			if(slot >= player.getInventory().getContainerSize())
				return;
			ItemStack stack = player.getInventory().getItem(slot);
			if(!CommercialHandheldItem.is(stack))
				return;
			switch(action) {
				case CHANNEL -> {
					if(value >= 0 && value < CommercialProgram.MAX_POSITIONS) // The radio fits it to its own knob and zones.
						CommercialHandheldItem.selectChannel(stack, value);
				}
				case VOLUME -> CommercialHandheldItem.setVolumeKnob(stack, value);
				case ZONE -> CommercialHandheldItem.selectZone(stack, value);
				default -> {}
			}
		}
		else if(action == CHANNEL && value >= 0 && value < CommercialProgram.MAX_POSITIONS
				&& player.containerMenu instanceof CommercialMobileMenu menu && menu.blockEntity.getBlockPos().asLong() == pos) {
			CommercialMobileBlockEntity radio = menu.blockEntity;
			radio.selectChannel(value);
		}
	}

	/** The handheld in {@code slot} on knob position {@code position}. */
	public static void handheld(int slot, int position) {
		PacketDistributor.sendToServer(new SCommercialRadioPacket(slot, 0L, CHANNEL, position));
	}

	/** The handheld in {@code slot}'s power/volume knob to {@code position} (0 = off). */
	public static void volume(int slot, int position) {
		PacketDistributor.sendToServer(new SCommercialRadioPacket(slot, 0L, VOLUME, position));
	}

	/** The handheld in {@code slot} on zone {@code zone} (0 = A: its A to D keys). */
	public static void zone(int slot, int zone) {
		PacketDistributor.sendToServer(new SCommercialRadioPacket(slot, 0L, ZONE, zone));
	}

	/** The mobile at {@code pos} (its screen open) on knob position {@code position}. */
	public static void mobile(BlockPos pos, int position) {
		PacketDistributor.sendToServer(new SCommercialRadioPacket(-1, pos.asLong(), CHANNEL, position));
	}
}
