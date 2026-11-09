package com.arrl.radiocraft.common.network.serverbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.CbRadioBlockEntity;
import com.arrl.radiocraft.common.items.CbPortableItem;
import com.arrl.radiocraft.common.menus.CbRadioMenu;
import com.arrl.radiocraft.common.radio.cb.CbRadioState;
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
 * A control worked on a CB radio (2026-11-08): the portable in inventory slot {@code slot}, or (with {@code slot} -1)
 * the mobile at {@code pos} whose screen the player has open. See {@link CbRadioState.Key}.
 */
public record SCbRadioPacket(int slot, long pos, int key, int value) implements CustomPacketPayload {

	public static final Type<SCbRadioPacket> TYPE = new Type<>(Radiocraft.id("s_cb_radio"));

	public static final StreamCodec<ByteBuf, SCbRadioPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, SCbRadioPacket::slot,
			ByteBufCodecs.VAR_LONG, SCbRadioPacket::pos,
			ByteBufCodecs.VAR_INT, SCbRadioPacket::key,
			ByteBufCodecs.INT, SCbRadioPacket::value,
			SCbRadioPacket::new);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(IPayloadContext context) {
		context.enqueueWork(() -> {
			if(slot >= 0) {
				if(slot >= context.player().getInventory().getContainerSize())
					return;
				ItemStack stack = context.player().getInventory().getItem(slot);
				if(stack.getItem() instanceof CbPortableItem)
					CbPortableItem.setState(stack, CbPortableItem.state(stack).press(key, value));
			}
			else if(context.player().containerMenu instanceof CbRadioMenu menu && menu.blockEntity.getBlockPos().asLong() == pos) {
				CbRadioBlockEntity radio = menu.blockEntity;
				radio.setRadioState(radio.getRadioState().press(key, value));
			}
		});
	}

	public static void portable(int slot, CbRadioState.Key key, int value) {
		PacketDistributor.sendToServer(new SCbRadioPacket(slot, 0L, key.ordinal(), value));
	}

	public static void radio(BlockPos pos, CbRadioState.Key key, int value) {
		PacketDistributor.sendToServer(new SCbRadioPacket(-1, pos.asLong(), key.ordinal(), value));
	}
}
