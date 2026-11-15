package com.arrl.radiocraft.common.blockentities.radio;

import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.voice.Ctcss;
import com.arrl.radiocraft.client.blockentity.RadioBlockEntityClientHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A 2m FM radio. Besides the frequency it has the two settings a repeater user needs: a CTCSS tone (sent under the
 * voice, and the receiver only opens for it) and a repeater offset (it listens on its frequency and transmits 600 kHz
 * below or above it).
 */
public abstract class VHFRadioBlockEntity extends RadioBlockEntity {

    protected float ctcssTone = Ctcss.NONE;
    protected int offsetHz = 0;
    /** The dial's STEP (2026-11-14), one of {@link com.arrl.radiocraft.common.radio.TuningStep#STEPS}, or 0 for the server's. */
    protected int tuneStepHz = 0;

    @Override
    public boolean isFM() {
        return true;
    }

    /** VHF radios step by the channel step, like the handhelds, or by the STEP chosen on the radio. */
    @Override
    public int getFrequencyStep() {
        return com.arrl.radiocraft.common.radio.TuningStep.effective(hasTuneStep() ? tuneStepHz : 0, RadiocraftServerConfig.VHF_FREQUENCY_STEP.get());
    }

    /** True if this radio has a STEP setting (a ham VHF radio; not a repeater, nor the scanner with its own steps). */
    public boolean hasTuneStep() {
        return true;
    }

    /** The STEP chosen, or 0 for the server's channel step. */
    public int getTuneStepHz() {
        return tuneStepHz;
    }

    /** Sets the STEP (one of the standard steps, or 0 for the server's). The frequency stays where it is. */
    public void setTuneStepHz(int hz) {
        int sane = com.arrl.radiocraft.common.radio.TuningStep.sanitize(hz);
        if(sane != tuneStepHz) {
            tuneStepHz = sane;
            setChanged();
            updateBlock();
        }
    }

    /**
     * With a STEP, the dial lands on that step's grid the way it is turned (164.480 up one 12.5 kHz click is 164.4875),
     * even when the frequency was left off it by another step.
     */
    @Override
    protected float snapToTuningRange(float frequency, int stepCount) {
        if(!hasTuneStep() || band == null)
            return super.snapToTuningRange(frequency, stepCount);
        boolean wide = tunesOutOfBand();
        return com.arrl.radiocraft.common.radio.TuningStep.tune(band.minFrequency(),
                wide ? com.arrl.radiocraft.common.radio.BandEdges.tuneMin(band) : band.minFrequency(),
                wide ? com.arrl.radiocraft.common.radio.BandEdges.tuneMax(band) : band.maxFrequency(),
                frequency, getFrequencyStep(), stepCount);
    }

    public VHFRadioBlockEntity(BlockEntityType<? extends RadioBlockEntity> type, BlockPos pos, BlockState state, Band band) {
        super(type, pos, state, band);
        ssbEnabled = true;
    }

    public float getCtcssTone() {
        return ctcssTone;
    }

    /** Sets the tone (snapped to a standard one, or none). */
    public void setCtcssTone(float tone) {
        float sane = Ctcss.sanitize(tone);
        if(sane != ctcssTone) {
            ctcssTone = sane;
            setChanged();
            updateBlock();
        }
    }

    public int getOffsetHz() {
        return offsetHz;
    }

    /** Sets the repeater offset: simplex (0) or 600 kHz either way. */
    public void setOffsetHz(int offset) {
        int sane = Ctcss.sanitizeOffset(offset);
        if(sane != offsetHz) {
            offsetHz = sane;
            setChanged();
            updateBlock();
        }
    }

    @Override
    public float getTransmitFrequency() {
        if(offsetHz == 0 || band == null)
            return frequency;
        if(band.contains(frequency) || !tunesOutOfBand()) // In band the offset stays in band, as it always has.
            return Math.max(band.minFrequency(), Math.min(band.maxFrequency(), frequency + offsetHz));
        return Math.max(com.arrl.radiocraft.common.radio.BandEdges.tuneMin(band), Math.min(com.arrl.radiocraft.common.radio.BandEdges.tuneMax(band), frequency + offsetHz));
    }

    @Override
    public float getTransmitTone() {
        return ctcssTone;
    }

    @Override
    public float getToneSquelch() {
        return ctcssTone;
    }

    /**
     * FM is squelched: no background static (a weak signal's hiss is in its audio, see RadioVoiceProcessor#fmFade; the
     * static instance plays only a blown transmitter's roar). With a squelch knob turned to OPEN the receiver hisses,
     * as a real FM set does, until a signal quiets it (2026-11-11).
     */
    @Override
    public boolean shouldPlayStatic() {
        return hasSquelchKnob() && getSquelchLevel() <= 0.0F;
    }

    @Override
    protected void setupSoundInstances() {
        super.setupSoundInstances();
        RadioBlockEntityClientHandler.startStaticSoundInstance(this);
    }

    @Override
    protected void setupSaveTag(CompoundTag nbt) {
        super.setupSaveTag(nbt);
        nbt.putFloat("ctcssTone", ctcssTone);
        nbt.putInt("offsetHz", offsetHz);
        nbt.putInt("tuneStep", tuneStepHz);
    }

    @Override
    protected void readSaveTag(CompoundTag nbt) {
        // The step first: loading puts the frequency back on the step's grid, and on the server's 20 kHz grid 164.4875
        // came back as 164.480, then 164.48125 once the 6.25 kHz step was on (2026-11-14, the maintainer's radio).
        tuneStepHz = com.arrl.radiocraft.common.radio.TuningStep.sanitize(nbt.getInt("tuneStep"));
        super.readSaveTag(nbt);
        ctcssTone = Ctcss.sanitize(nbt.getFloat("ctcssTone"));
        offsetHz = Ctcss.sanitizeOffset(nbt.getInt("offsetHz"));
    }

}
