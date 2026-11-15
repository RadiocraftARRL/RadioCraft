package com.arrl.radiocraft.common.datacomponents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * @param ctcssTone CTCSS tone sent under the voice (the TX tone), 0 for none.
 * @param offsetHz  Repeater offset: the radio transmits this far from its frequency (0 for simplex).
 * @param rxTone    CTCSS tone needed to open the squelch (the RX tone, tone squelch), 0 for none: hears everything.
 * @param tuneStep  The dial's STEP in Hz (2026-11-14, {@link com.arrl.radiocraft.common.radio.TuningStep}), 0 for the server's.
 */
public record HandheldRadioState(boolean power, boolean ptt, float freq, float gain, float micGain, float receiveIndicatorStrength, float ctcssTone, int offsetHz, float rxTone, int tuneStep) {

    public HandheldRadioState(boolean power, boolean ptt, float freq, float gain, float micGain, float receiveIndicatorStrength, float ctcssTone, int offsetHz, float rxTone) {
        this(power, ptt, freq, gain, micGain, receiveIndicatorStrength, ctcssTone, offsetHz, rxTone, 0);
    }

    public HandheldRadioState(boolean power, boolean ptt, float freq, float gain, float micGain, float receiveIndicatorStrength) {
        this(power, ptt, freq, gain, micGain, receiveIndicatorStrength, 0.0f, 0, 0.0f);
    }

    public static final Codec<HandheldRadioState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.fieldOf("power").forGetter(HandheldRadioState::power),
                    Codec.BOOL.fieldOf("ptt").forGetter(HandheldRadioState::ptt),
                    Codec.FLOAT.fieldOf("frequency").forGetter(HandheldRadioState::freq),
                    Codec.FLOAT.fieldOf("gain").forGetter(HandheldRadioState::gain),
                    Codec.FLOAT.fieldOf("micGain").forGetter(HandheldRadioState::micGain),
                    Codec.FLOAT.optionalFieldOf("ctcssTone", 0.0f).forGetter(HandheldRadioState::ctcssTone),
                    Codec.INT.optionalFieldOf("offsetHz", 0).forGetter(HandheldRadioState::offsetHz),
                    Codec.FLOAT.optionalFieldOf("rxTone", 0.0f).forGetter(HandheldRadioState::rxTone),
                    Codec.INT.optionalFieldOf("tuneStep", 0).forGetter(HandheldRadioState::tuneStep)
            ).apply(instance, (power, ptt, freq, gain, micGain, tone, offset, rxTone, step)->new HandheldRadioState(power, ptt, freq, gain, micGain, 0.0f, tone, offset, rxTone,
                    com.arrl.radiocraft.common.radio.TuningStep.sanitize(step))));

    public static final StreamCodec<ByteBuf, HandheldRadioState> STREAM_CODEC = StreamCodec.of(
            (buf, state) -> {
                buf.writeBoolean(state.power());
                buf.writeBoolean(state.ptt());
                buf.writeFloat(state.freq());
                buf.writeFloat(state.gain());
                buf.writeFloat(state.micGain());
                buf.writeFloat(state.receiveIndicatorStrength());
                buf.writeFloat(state.ctcssTone());
                buf.writeInt(state.offsetHz());
                buf.writeFloat(state.rxTone());
                buf.writeInt(state.tuneStep());
            },
            buf -> new HandheldRadioState(buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readFloat(), buf.readInt())
    );

    public static HandheldRadioState getDefault() {
        return new HandheldRadioState(false, false, 146_520_000.0f, 1.0f, 1.0f, 0.0f);
    }

    /** This state with its TX tone and offset replaced. */
    public HandheldRadioState withToneAndOffset(float tone, int offset) {
        return new HandheldRadioState(power, ptt, freq, gain, micGain, receiveIndicatorStrength, tone, offset, rxTone, tuneStep);
    }

    /** This state with its RX tone replaced. */
    public HandheldRadioState withRxTone(float tone) {
        return new HandheldRadioState(power, ptt, freq, gain, micGain, receiveIndicatorStrength, ctcssTone, offsetHz, tone, tuneStep);
    }

    /** This state with its STEP replaced (0 for the server's). */
    public HandheldRadioState withTuneStep(int step) {
        return new HandheldRadioState(power, ptt, freq, gain, micGain, receiveIndicatorStrength, ctcssTone, offsetHz, rxTone,
                com.arrl.radiocraft.common.radio.TuningStep.sanitize(step));
    }

    /** This state with the tones, offset and STEP of {@code from}. */
    public HandheldRadioState withTonesOf(HandheldRadioState from) {
        return new HandheldRadioState(power, ptt, freq, gain, micGain, receiveIndicatorStrength, from.ctcssTone, from.offsetHz, from.rxTone, from.tuneStep);
    }
}
