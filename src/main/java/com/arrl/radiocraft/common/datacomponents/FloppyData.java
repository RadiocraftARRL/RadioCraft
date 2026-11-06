package com.arrl.radiocraft.common.datacomponents;

import com.arrl.radiocraft.common.computer.FloppyDisk;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * A data floppy (computer batch 3A): a blank floppy disk that files were copied onto in a radio computer's drive. Its
 * id names its files in the world save ({@link com.arrl.radiocraft.common.computer.FloppyFileStore}), as a radio
 * computer's item names its disk ({@link ComputerData}); the rest is what the tooltip shows, kept up to date by the
 * computer whenever the files change. Two items with one id (a creative copy) share their files.
 *
 * @param id    names the files on the server
 * @param label the volume label ({@link FloppyDisk#cleanLabel}), "" for none
 * @param files how many files are on it
 * @param used  bytes they take
 */
public record FloppyData(UUID id, String label, int files, long used) {

	public static final Codec<FloppyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(FloppyData::id),
			Codec.STRING.optionalFieldOf("label", "").forGetter(FloppyData::label),
			Codec.INT.optionalFieldOf("files", 0).forGetter(FloppyData::files),
			Codec.LONG.optionalFieldOf("used", 0L).forGetter(FloppyData::used)
	).apply(instance, FloppyData::new));

	public static final StreamCodec<ByteBuf, FloppyData> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, FloppyData::id,
			ByteBufCodecs.stringUtf8(FloppyDisk.LABEL_MAX * 2), FloppyData::label,
			ByteBufCodecs.VAR_INT, FloppyData::files,
			ByteBufCodecs.VAR_LONG, FloppyData::used,
			FloppyData::new
	);

	public FloppyData {
		label = FloppyDisk.cleanLabel(label);
		files = Math.max(0, files);
		used = Math.max(0L, used);
	}

	/** A floppy with no files and no label: it can go back to being a plain blank disk. */
	public boolean isEmpty() {
		return files == 0 && label.isEmpty();
	}
}
