package com.arrl.radiocraft.common.radio.commercial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Locale;

/**
 * One entry of a commercial radio's contact list (1.12 playtest fixes 2): a unit ID and the alias a radio with a
 * display shows for it while that unit talks ("ENG 5" for unit 1205), as an MDC-1200 or P25 radio's alias list does.
 *
 * @param id    The unit ID, 1 to {@link CommercialProgram#UNIT_MAX}.
 * @param alias What the display shows, upper case, at most {@link #ALIAS_MAX} characters.
 */
public record CommercialContact(int id, String alias) {

	/** Characters in an alias (the radios' displays fit eight). */
	public static final int ALIAS_MAX = 8;

	public CommercialContact {
		id = Math.max(0, Math.min(CommercialProgram.UNIT_MAX, id));
		alias = sanitizeAlias(alias);
	}

	public static final Codec<CommercialContact> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("id").forGetter(CommercialContact::id),
			Codec.STRING.optionalFieldOf("alias", "").forGetter(CommercialContact::alias)
	).apply(instance, CommercialContact::new));

	public static final StreamCodec<ByteBuf, CommercialContact> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CommercialContact::id,
			ByteBufCodecs.stringUtf8(ALIAS_MAX * 4), CommercialContact::alias,
			CommercialContact::new);

	/** True if it names a unit (an ID of 1 or more and an alias). */
	public boolean valid() {
		return id > 0 && !alias.isEmpty();
	}

	/** Upper case printable ASCII, at most {@link #ALIAS_MAX} characters. */
	public static String sanitizeAlias(String alias) {
		if(alias == null)
			return "";
		StringBuilder out = new StringBuilder();
		for(char c : alias.toUpperCase(Locale.ROOT).strip().toCharArray()) {
			if(c >= 32 && c < 127)
				out.append(c);
			if(out.length() >= ALIAS_MAX)
				break;
		}
		return out.toString().strip();
	}
}
