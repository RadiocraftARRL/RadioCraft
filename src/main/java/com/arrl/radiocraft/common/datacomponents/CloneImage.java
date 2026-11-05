package com.arrl.radiocraft.common.datacomponents;

import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a Radio Cloner holds (1.12 playtest fixes 2): one radio's programming, read off it to be written into others.
 * Two families that never mix:
 * <ul>
 * <li>{@link #COMMERCIAL}: a commercial radio's channels and contact list ({@link #program}), with every key the source
 * would not let a programmer read already taken off (those channels are clear); never its unit ID.</li>
 * <li>{@link #HAM_VHF}: a 2m ham radio's dial (frequency, tones, offset in {@link #dial}) and, from a handheld, its
 * memory channels ({@link #memory}, {@link #hasMemories}).</li>
 * </ul>
 *
 * @param family      {@link #COMMERCIAL} or {@link #HAM_VHF}.
 * @param source      The translation key of the radio it was read from (for the tooltip).
 * @param model       The commercial model id it was read from ({@code CommercialRadioModel#id}), or "".
 * @param program     The commercial channels and contacts (empty for ham).
 * @param memory      The ham handheld's memory channels (empty otherwise).
 * @param dial        The ham radio's dial: frequency, tones and offset (slot 0; unused for commercial).
 * @param hasMemories True if the ham image came from a handheld, so writing it replaces the target's memories.
 * @param keysLeft    How many keys the source would not give up (left behind: those channels went clear).
 */
public record CloneImage(String family, String source, String model, CommercialProgram program, HandheldMemory memory,
                         HandheldMemory.Channel dial, boolean hasMemories, int keysLeft) {

	public static final String COMMERCIAL = "commercial";
	public static final String HAM_VHF = "ham_vhf";

	public static final Codec<CloneImage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("family").forGetter(CloneImage::family),
			Codec.STRING.optionalFieldOf("source", "").forGetter(CloneImage::source),
			Codec.STRING.optionalFieldOf("model", "").forGetter(CloneImage::model),
			CommercialProgram.CODEC.optionalFieldOf("program", CommercialProgram.EMPTY).forGetter(CloneImage::program),
			HandheldMemory.CODEC.optionalFieldOf("memory", HandheldMemory.EMPTY).forGetter(CloneImage::memory),
			HandheldMemory.Channel.CODEC.optionalFieldOf("dial", new HandheldMemory.Channel(0, 0.0F, 0.0F, 0.0F, 0)).forGetter(CloneImage::dial),
			Codec.BOOL.optionalFieldOf("hasMemories", false).forGetter(CloneImage::hasMemories),
			Codec.INT.optionalFieldOf("keysLeft", 0).forGetter(CloneImage::keysLeft)
	).apply(instance, CloneImage::new));

	public static final StreamCodec<ByteBuf, CloneImage> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	/** A commercial radio's programming. */
	public static CloneImage commercial(String source, String model, CommercialProgram program, int keysLeft) {
		return new CloneImage(COMMERCIAL, source, model, program, HandheldMemory.EMPTY, new HandheldMemory.Channel(0, 0.0F, 0.0F, 0.0F, 0), false, keysLeft);
	}

	/** A ham VHF radio's dial and (from a handheld) memories. */
	public static CloneImage ham(String source, HandheldMemory.Channel dial, HandheldMemory memory, boolean hasMemories) {
		return new CloneImage(HAM_VHF, source, "", CommercialProgram.EMPTY, memory, dial, hasMemories, 0);
	}

	public boolean isCommercial() {
		return COMMERCIAL.equals(family);
	}

	public boolean isHam() {
		return HAM_VHF.equals(family);
	}

	/** How many channels it holds: commercial channels programmed, or ham memories (from a handheld). */
	public int channelCount() {
		return isCommercial() ? program.programmedCount() : memory.channels().size();
	}
}
