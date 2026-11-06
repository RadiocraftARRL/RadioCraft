package com.arrl.radiocraft.common.network.serverbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.qsl.QslCard;
import com.arrl.radiocraft.common.qsl.QslCards;
import com.arrl.radiocraft.common.radio.weather.WeatherReports;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * What a player wrote on a QSL card in their hand (computer batch 3C), and whether they signed it. The server puts in
 * the date and time itself and the signer's name, refuses a signed card ({@link QslCard#problem}), and with a stack of
 * blank cards in the hand takes one card off the stack for the writing.
 */
public record SQslCardPacket(boolean offHand, CompoundTag written, boolean sign) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SQslCardPacket> TYPE = new CustomPacketPayload.Type<>(Radiocraft.id("s_qsl_card"));

	public static final StreamCodec<ByteBuf, SQslCardPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, SQslCardPacket::offHand,
			ByteBufCodecs.COMPOUND_TAG, SQslCardPacket::written,
			ByteBufCodecs.BOOL, SQslCardPacket::sign,
			SQslCardPacket::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(IPayloadContext context) {
		context.enqueueWork(() -> {
			if(context.player() instanceof ServerPlayer player)
				apply(player, offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, QslCard.Fill.fromTag(written), sign);
		});
	}

	/** Writes {@code written} on the card in {@code hand}; null if done, else the error key (a chat line is sent). */
	public static String apply(ServerPlayer player, InteractionHand hand, QslCard.Fill written, boolean sign) {
		ItemStack stack = player.getItemInHand(hand);
		QslCard card = QslCards.card(stack);
		if(card == null)
			return "no_card";
		String problem = card.problem(written, sign);
		QslCard made = problem != null ? null
				: card.filled(written, System.currentTimeMillis(), WeatherReports.timestamp(player.level().getDayTime()), player.getGameProfile().getName(), sign);
		if(made == null) {
			String key = problem == null ? "signed" : problem;
			player.displayClientMessage(Component.translatable("message.radiocraft.qsl." + key), true);
			return key;
		}
		if(stack.getCount() == 1) {
			stack.set(RadiocraftDataComponent.QSL_CARD.get(), made);
			stack.remove(RadiocraftDataComponent.QSL_FLIPPED.get());
		}
		else { // One card off the stack of blanks.
			stack.shrink(1);
			ItemStack one = QslCards.stack(made, 1);
			if(!player.getInventory().add(one))
				player.drop(one, false);
		}
		return null;
	}

	public static void send(InteractionHand hand, QslCard.Fill written, boolean sign) {
		PacketDistributor.sendToServer(new SQslCardPacket(hand == InteractionHand.OFF_HAND, written.toTag(), sign));
	}
}
