package com.arrl.radiocraft.common.datacomponents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Marks a filled map as a photo (see {@link com.arrl.radiocraft.common.photo.PhotoPrints}): who took it, whether it is
 * on colour or black and white photo paper, and the source ({@link #CAMERA}; the scanner and SSTV may add their own).
 * Copies (a cartography table, a radio fax) keep it, so a copy is still a photo.
 */
public record PhotoInfo(String photographer, boolean colour, String source) {

    public static final String CAMERA = "camera";
    /** Longest photographer name kept. */
    public static final int NAME_MAX = 64;

    public static final Codec<PhotoInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("by", "").forGetter(PhotoInfo::photographer),
            Codec.BOOL.optionalFieldOf("colour", true).forGetter(PhotoInfo::colour),
            Codec.STRING.optionalFieldOf("source", CAMERA).forGetter(PhotoInfo::source)
    ).apply(instance, PhotoInfo::new));

    public static final StreamCodec<ByteBuf, PhotoInfo> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(NAME_MAX), PhotoInfo::photographer,
            ByteBufCodecs.BOOL, PhotoInfo::colour,
            ByteBufCodecs.stringUtf8(32), PhotoInfo::source,
            PhotoInfo::new
    );

    public PhotoInfo {
        photographer = photographer == null ? "" : photographer.length() > NAME_MAX ? photographer.substring(0, NAME_MAX) : photographer;
        source = source == null || source.isEmpty() ? CAMERA : source.length() > 32 ? source.substring(0, 32) : source;
    }
}
