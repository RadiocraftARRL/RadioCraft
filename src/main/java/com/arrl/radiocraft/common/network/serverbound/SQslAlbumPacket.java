package com.arrl.radiocraft.common.network.serverbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.items.QslAlbumItem;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** Takes one card out of the QSL album in the player's hand (computer batch 3C), from the album's screen. */
public record SQslAlbumPacket(boolean offHand, int index) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SQslAlbumPacket> TYPE = new CustomPacketPayload.Type<>(Radiocraft.id("s_qsl_album"));

	public static final StreamCodec<ByteBuf, SQslAlbumPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, SQslAlbumPacket::offHand,
			ByteBufCodecs.VAR_INT, SQslAlbumPacket::index,
			SQslAlbumPacket::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(IPayloadContext context) {
		context.enqueueWork(() -> {
			ItemStack album = context.player().getItemInHand(offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
			if(!album.is(RadiocraftItems.QSL_ALBUM.get()) || index < 0)
				return;
			ItemStack card = QslAlbumItem.take(album, index);
			if(!card.isEmpty() && !context.player().getInventory().add(card))
				context.player().drop(card, false);
		});
	}

	public static void send(InteractionHand hand, int index) {
		PacketDistributor.sendToServer(new SQslAlbumPacket(hand == InteractionHand.OFF_HAND, index));
	}
}
