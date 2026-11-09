package com.arrl.radiocraft.common.datacomponents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a broken-out Stable Chemical Battery keeps on its item (2026-11-09): its charge, and whether its seal is broken.
 * An item without it is a new one, sealed and full.
 *
 * @param energy the charge left, in FE
 * @param active true once a redstone signal has broken the seal
 */
public record ChemicalCharge(int energy, boolean active) {

	public static final Codec<ChemicalCharge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("energy").forGetter(ChemicalCharge::energy),
			Codec.BOOL.fieldOf("active").forGetter(ChemicalCharge::active)
	).apply(instance, ChemicalCharge::new));

	public static final StreamCodec<ByteBuf, ChemicalCharge> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ChemicalCharge::energy,
			ByteBufCodecs.BOOL, ChemicalCharge::active,
			ChemicalCharge::new
	);
}
