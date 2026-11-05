package com.arrl.radiocraft.common.network.serverbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.MarineRadioBlockEntity;
import com.arrl.radiocraft.common.items.MarineHandheldItem;
import com.arrl.radiocraft.common.menus.MarineRadioMenu;
import com.arrl.radiocraft.common.radio.MarineChannels;
import com.arrl.radiocraft.common.radio.marine.MarineRadioState;
import com.arrl.radiocraft.common.radio.marine.MarineRadios;
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
 * A key pressed on a marine radio: the marine handheld in inventory slot {@code slot}, or (with {@code slot} -1) the
 * fixed-mount radio at {@code pos} whose screen the player has open. The keys are all a user has: the channel knob
 * ({@link MarineRadioState.Key#TURN}, {@code value} clicks), 16/9, HI/LO and DW. Frequencies always come from the channel.
 */
public record SMarineRadioPacket(int slot, long pos, int key, int value) implements CustomPacketPayload {

	public static final Type<SMarineRadioPacket> TYPE = new Type<>(Radiocraft.id("s_marine_radio"));

	public static final StreamCodec<ByteBuf, SMarineRadioPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, SMarineRadioPacket::slot,
			ByteBufCodecs.VAR_LONG, SMarineRadioPacket::pos,
			ByteBufCodecs.VAR_INT, SMarineRadioPacket::key,
			ByteBufCodecs.INT, SMarineRadioPacket::value,
			SMarineRadioPacket::new);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(IPayloadContext context) {
		context.enqueueWork(() -> {
			MarineChannels.Region region = MarineRadios.region();
			if(slot >= 0) {
				if(slot >= context.player().getInventory().getContainerSize())
					return;
				ItemStack stack = context.player().getInventory().getItem(slot);
				if(MarineHandheldItem.is(stack))
					MarineHandheldItem.setState(stack, MarineHandheldItem.state(stack).press(region, key, value));
			}
			else if(context.player().containerMenu instanceof MarineRadioMenu menu && menu.blockEntity.getBlockPos().asLong() == pos) {
				MarineRadioBlockEntity radio = menu.blockEntity;
				radio.setRadioState(radio.getRadioState().press(region, key, value));
			}
		});
	}

	/** Key {@code key} pressed on the marine handheld in {@code slot}. */
	public static void handheld(int slot, MarineRadioState.Key key, int value) {
		PacketDistributor.sendToServer(new SMarineRadioPacket(slot, 0L, key.ordinal(), value));
	}

	/** Key {@code key} pressed on the marine radio at {@code pos} (its screen open). */
	public static void radio(BlockPos pos, MarineRadioState.Key key, int value) {
		PacketDistributor.sendToServer(new SMarineRadioPacket(-1, pos.asLong(), key.ordinal(), value));
	}
}
