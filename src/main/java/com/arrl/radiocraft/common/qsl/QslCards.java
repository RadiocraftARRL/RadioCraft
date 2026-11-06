package com.arrl.radiocraft.common.qsl;

import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * QSL cards as items and files (computer batch 3C): the data component's codecs, making and reading card stacks, and
 * the {@code .QSL} file (compressed NBT {@code {card: ...}}) the QSL Designer keeps templates and received cards in.
 */
public final class QslCards {

	/** The QSL Designer's files: a template (a card with nothing written on it) or a card received or scanned. */
	public static final String EXTENSION = "QSL";

	public static final Codec<QslCard> CODEC = CompoundTag.CODEC.xmap(QslCard::fromTag, QslCard::toTag);
	public static final StreamCodec<ByteBuf, QslCard> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(QslCard::fromTag, QslCard::toTag);

	private QslCards() {}

	public static boolean is(ItemStack stack) {
		return stack.is(RadiocraftItems.QSL_CARD.get());
	}

	/** The card on {@code stack}, or null if it is not a QSL card. A card item with no data is a plain blank. */
	@Nullable
	public static QslCard card(ItemStack stack) {
		if(!is(stack))
			return null;
		QslCard card = stack.get(RadiocraftDataComponent.QSL_CARD.get());
		return card == null ? QslCard.BLANK : card;
	}

	/** {@code count} cards of {@code card}. */
	public static ItemStack stack(QslCard card, int count) {
		ItemStack stack = new ItemStack(RadiocraftItems.QSL_CARD.get(), Math.max(1, Math.min(64, count)));
		stack.set(RadiocraftDataComponent.QSL_CARD.get(), card);
		return stack;
	}

	public static boolean flipped(ItemStack stack) {
		return Boolean.TRUE.equals(stack.get(RadiocraftDataComponent.QSL_FLIPPED.get()));
	}

	/** Turns the card over (the flag is dropped when it shows its front, so turned-back cards stack again). */
	public static void flip(ItemStack stack) {
		if(flipped(stack))
			stack.remove(RadiocraftDataComponent.QSL_FLIPPED.get());
		else
			stack.set(RadiocraftDataComponent.QSL_FLIPPED.get(), true);
	}

	// -------------------- FILES --------------------

	public static byte[] file(QslCard card) {
		CompoundTag tag = new CompoundTag();
		tag.put("card", card.toTag());
		return ComputerFileFormats.nbt(tag);
	}

	/** The card in a {@code .QSL} file, or null if it is not one. */
	@Nullable
	public static QslCard fromFile(byte[] data) {
		if(data == null)
			return null;
		CompoundTag tag = ComputerFileFormats.nbt(data);
		return tag == null || !tag.contains("card") ? null : QslCard.fromTag(tag.getCompound("card"));
	}

	/** The card's front as a 128 x 128 picture (for SSTV tones and a printout on a map). */
	public static byte[] frontPicture(QslCard card) {
		return QslArt.onMap(QslArt.front(card));
	}
}
