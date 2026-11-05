package com.arrl.radiocraft.common.init;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.datacomponents.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class RadiocraftDataComponent {

    // The specialized DeferredRegister.DataComponents simplifies data component registration and avoids some generic inference issues with the `DataComponentType.Builder` within a `Supplier`
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Radiocraft.MOD_ID);


    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EnergyRecord>> ENERGY_DATA_COMPONENT = DATA_COMPONENTS.registerComponentType(
            "energy",
            builder -> builder
                    .persistent(EnergyRecord.CODEC)
                    .networkSynchronized(EnergyRecord.STREAM_CODEC)
    );

    // Integer data component for storing energy values (used by batteries)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BATTERY_ENERGY = DATA_COMPONENTS.registerComponentType(
            "battery_energy",
            builder -> builder.persistent(net.minecraft.util.ExtraCodecs.NON_NEGATIVE_INT)
    );

    // Integer data component for storing energy in VHF Handheld radios
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RADIO_ENERGY = DATA_COMPONENTS.registerComponentType(
            "radio_energy",
            builder -> builder.persistent(net.minecraft.util.ExtraCodecs.NON_NEGATIVE_INT)
    );

    /** A radio's hand mic on its cord: which radio, and which lending (see {@link com.arrl.radiocraft.common.items.MicCord}). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.arrl.radiocraft.common.items.MicCord>> MIC_CORD = DATA_COMPONENTS.registerComponentType(
            "mic_cord",
            builder -> builder
                    .persistent(com.arrl.radiocraft.common.items.MicCord.CODEC)
                    .networkSynchronized(com.arrl.radiocraft.common.items.MicCord.STREAM_CODEC)
    );

    /** The frequency a handheld direction finder listens on, in Hz. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> DF_FREQUENCY = DATA_COMPONENTS.registerComponentType(
            "df_frequency",
            builder -> builder
                    .persistent(com.mojang.serialization.Codec.FLOAT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.FLOAT)
    );

    /** What is on a tape reel (blank, a server tape, or a recording) and its colourway. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.arrl.radiocraft.common.datacomponents.TapeContents>> TAPE_CONTENTS = DATA_COMPONENTS.registerComponentType(
            "tape_contents",
            builder -> builder
                    .persistent(com.arrl.radiocraft.common.datacomponents.TapeContents.CODEC)
                    .networkSynchronized(com.arrl.radiocraft.common.datacomponents.TapeContents.STREAM_CODEC)
    );

    /** The lines on a roll torn off a teletype. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.arrl.radiocraft.common.datacomponents.TeletypeRollContents>> TELETYPE_ROLL_CONTENTS = DATA_COMPONENTS.registerComponentType(
            "teletype_roll_contents",
            builder -> builder
                    .persistent(com.arrl.radiocraft.common.datacomponents.TeletypeRollContents.CODEC)
                    .networkSynchronized(com.arrl.radiocraft.common.datacomponents.TeletypeRollContents.STREAM_CODEC)
    );

    /** The HF manpack's settings and battery. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.arrl.radiocraft.common.datacomponents.ManpackState>> MANPACK_STATE = DATA_COMPONENTS.registerComponentType(
            "manpack_state",
            builder -> builder
                    .persistent(com.arrl.radiocraft.common.datacomponents.ManpackState.CODEC)
                    .networkSynchronized(com.arrl.radiocraft.common.datacomponents.ManpackState.STREAM_CODEC)
    );

    /** A handheld's memory channels and whether it runs on one (see {@link HandheldMemory}). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<HandheldMemory>> HANDHELD_MEMORY = DATA_COMPONENTS.registerComponentType(
            "handheld_memory",
            builder -> builder
                    .persistent(HandheldMemory.CODEC)
                    .networkSynchronized(HandheldMemory.STREAM_CODEC)
    );

    /** The APRS module in a handheld, with what it has heard (see {@link HandheldAprs}); absent without the module. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<HandheldAprs>> HANDHELD_APRS = DATA_COMPONENTS.registerComponentType(
            "handheld_aprs",
            builder -> builder
                    .persistent(HandheldAprs.CODEC)
                    .networkSynchronized(HandheldAprs.STREAM_CODEC)
    );

    /**
     * A MARS mod is fitted (see {@link com.arrl.radiocraft.common.items.MarsModItem}): the radio may transmit anywhere it
     * tunes, not just inside its band. On HF radio, VHF base station, all-band and QRP items, the VHF handheld and the
     * HF manpack; a placed radio keeps it in its block entity.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.util.Unit>> MARS_MOD = DATA_COMPONENTS.registerComponentType(
            "mars_mod",
            builder -> builder
                    .persistent(net.minecraft.util.Unit.CODEC)
                    .networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(net.minecraft.util.Unit.INSTANCE))
    );

    /**
     * A commercial radio's channel list and knob position (see
     * {@link com.arrl.radiocraft.common.radio.commercial.CommercialProgram}): on the commercial handhelds, and on a
     * commercial mobile's item (its block entity takes it when placed and gives it back when broken).
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.arrl.radiocraft.common.radio.commercial.CommercialProgram>> COMMERCIAL_PROGRAM = DATA_COMPONENTS.registerComponentType(
            "commercial_program",
            builder -> builder
                    .persistent(com.arrl.radiocraft.common.radio.commercial.CommercialProgram.CODEC)
                    .networkSynchronized(com.arrl.radiocraft.common.radio.commercial.CommercialProgram.STREAM_CODEC)
    );

    /**
     * A marine radio's channel, HI/LO switch and dual watch (see
     * {@link com.arrl.radiocraft.common.radio.marine.MarineRadioState}): on the marine handheld, and on the marine
     * radio's item (its block entity takes it when placed and gives it back when broken).
     */
    /** What a Radio Cloner holds: one radio's programming to write into others (see {@link CloneImage}). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CloneImage>> CLONE_IMAGE = DATA_COMPONENTS.registerComponentType(
            "clone_image",
            builder -> builder
                    .persistent(CloneImage.CODEC)
                    .networkSynchronized(CloneImage.STREAM_CODEC)
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.arrl.radiocraft.common.radio.marine.MarineRadioState>> MARINE_RADIO = DATA_COMPONENTS.registerComponentType(
            "marine_radio",
            builder -> builder
                    .persistent(com.arrl.radiocraft.common.radio.marine.MarineRadioState.CODEC)
                    .networkSynchronized(com.arrl.radiocraft.common.radio.marine.MarineRadioState.STREAM_CODEC)
    );

    /**
     * A filled map that is a photo (see {@link PhotoInfo} and {@link com.arrl.radiocraft.common.photo.PhotoPrints}):
     * who took it and on which photo paper. The picture itself is the map's.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PhotoInfo>> PHOTO = DATA_COMPONENTS.registerComponentType(
            "photo",
            builder -> builder
                    .persistent(PhotoInfo.CODEC)
                    .networkSynchronized(PhotoInfo.STREAM_CODEC)
    );

    /**
     * A radio computer's item: its id (which names its disk in the world save) and installed programs (see
     * {@link ComputerData}). The block entity takes it when placed and gives it back when broken.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ComputerData>> COMPUTER = DATA_COMPONENTS.registerComponentType(
            "computer",
            builder -> builder
                    .persistent(ComputerData.CODEC)
                    .networkSynchronized(ComputerData.STREAM_CODEC)
    );

    /** An FCC badge's agent and number (1.12, see {@link FccBadge}); set by {@code /radiocraft fcc badge}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FccBadge>> FCC_BADGE = DATA_COMPONENTS.registerComponentType(
            "fcc_badge",
            builder -> builder
                    .persistent(FccBadge.CODEC)
                    .networkSynchronized(FccBadge.STREAM_CODEC)
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<HandheldRadioState>> HANDHELD_RADIO_STATE_COMPONENT = DATA_COMPONENTS.registerComponentType(
            "handheld_radio_state",
            builder -> builder
                    .persistent(HandheldRadioState.CODEC)
                    .networkSynchronized(HandheldRadioState.STREAM_CODEC)
    );

}
