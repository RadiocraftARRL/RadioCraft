package com.arrl.radiocraft.common.datacomponents;

import com.arrl.radiocraft.common.fcc.FccBadges;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * Who an FCC badge was issued to (1.12): the agent's name and UUID as an operator gave it with
 * {@code /radiocraft fcc badge}, and its number ({@link FccBadges#numberFor}). A badge without it is a blank.
 */
public record FccBadge(String agent, UUID agentId, int number) {

	public static final Codec<FccBadge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("agent").forGetter(FccBadge::agent),
			UUIDUtil.CODEC.fieldOf("agent_id").forGetter(FccBadge::agentId),
			Codec.INT.fieldOf("number").forGetter(FccBadge::number)
	).apply(instance, FccBadge::new));

	public static final StreamCodec<ByteBuf, FccBadge> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(FccBadges.NAME_MAX), FccBadge::agent,
			UUIDUtil.STREAM_CODEC, FccBadge::agentId,
			ByteBufCodecs.VAR_INT, FccBadge::number,
			FccBadge::new
	);

	public FccBadge {
		agent = FccBadges.cleanName(agent);
	}

	/** The badge issued to {@code agent} called {@code name}. */
	public static FccBadge issuedTo(String name, UUID agent) {
		return new FccBadge(name, agent, FccBadges.numberFor(agent));
	}
}
