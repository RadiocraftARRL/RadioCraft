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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * The user turned a commercial radio's channel knob to {@code position} (0-based): the handheld in inventory slot
 * {@code slot}, or (with {@code slot} -1) the mobile at {@code pos} whose screen they have open. Turning the knob is
 * all a user can do to a commercial radio's channels; programming goes through
 * {@link com.arrl.radiocraft.common.radio.commercial.CommercialProgramming}.
 */
public record SCommercialRadioPacket(int slot, long pos, int position) implements CustomPacketPayload {

	public static final Type<SCommercialRadioPacket> TYPE = new Type<>(Radiocraft.id("s_commercial_radio"));

	public static final StreamCodec<ByteBuf, SCommercialRadioPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, SCommercialRadioPacket::slot,
			ByteBufCodecs.VAR_LONG, SCommercialRadioPacket::pos,
			ByteBufCodecs.VAR_INT, SCommercialRadioPacket::position,
			SCommercialRadioPacket::new);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(IPayloadContext context) {
		context.enqueueWork(() -> {
			if(position < 0 || position >= CommercialProgram.MAX_POSITIONS) // The radio fits it to its own knob and zones.
				return;
			if(slot >= 0) {
				if(slot >= context.player().getInventory().getContainerSize())
					return;
				ItemStack stack = context.player().getInventory().getItem(slot);
				if(CommercialHandheldItem.is(stack))
					CommercialHandheldItem.selectChannel(stack, position);
			}
			else if(context.player().containerMenu instanceof CommercialMobileMenu menu && menu.blockEntity.getBlockPos().asLong() == pos) {
				CommercialMobileBlockEntity radio = menu.blockEntity;
				radio.selectChannel(position);
			}
		});
	}

	/** The handheld in {@code slot} on knob position {@code position}. */
	public static void handheld(int slot, int position) {
		PacketDistributor.sendToServer(new SCommercialRadioPacket(slot, 0L, position));
	}

	/** The mobile at {@code pos} (its screen open) on knob position {@code position}. */
	public static void mobile(BlockPos pos, int position) {
		PacketDistributor.sendToServer(new SCommercialRadioPacket(-1, pos.asLong(), position));
	}
}
