package com.arrl.radiocraft.common.datacomponents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.UUID;

/**
 * A radio computer broken out of the world (1.12 step 5): its id, which names its disk in the world save
 * ({@link com.arrl.radiocraft.common.computer.ComputerFileStore}), and the programs installed on it. Placed again, it
 * has its files and programs back. Two items with one id (a creative copy) share a disk.
 */
public record ComputerData(UUID id, List<String> installed) {

	/** Most programs one computer can have installed (there are far fewer). */
	public static final int MAX_INSTALLED = 64;

	public static final Codec<ComputerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(ComputerData::id),
			Codec.STRING.listOf().optionalFieldOf("installed", List.of()).forGetter(ComputerData::installed)
	).apply(instance, ComputerData::new));

	public static final StreamCodec<ByteBuf, ComputerData> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, ComputerData::id,
			ByteBufCodecs.stringUtf8(32).apply(ByteBufCodecs.list(MAX_INSTALLED)), ComputerData::installed,
			ComputerData::new
	);

	public ComputerData {
		installed = installed == null ? List.of() : List.copyOf(installed.size() > MAX_INSTALLED ? installed.subList(0, MAX_INSTALLED) : installed);
	}
}
