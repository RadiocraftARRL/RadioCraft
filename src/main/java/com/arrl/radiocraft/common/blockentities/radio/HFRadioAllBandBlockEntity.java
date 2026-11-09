package com.arrl.radiocraft.common.blockentities.radio;

import com.arrl.radiocraft.CommonConfig;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.menus.HFRadioAllBandMenu;
import com.arrl.radiocraft.common.radio.Band;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The all-band transceiver: one radio for 80, 40, 20 and 10m plus 2m, picked with the band knob, in SSB, CW or FM.
 * FM is a voice mode like SSB (the voice path is the same), but it is squelched: no background static between
 * transmissions, as on a real FM rig. On 2m it talks to VHF stations through a VHF antenna.
 */
public class HFRadioAllBandBlockEntity extends HFRadioBlockEntity {

    /** The knob's positions, in order from fully left to fully right: 3.5, 7, 14, 28 and 144 MHz. */
    public static final List<String> BANDS = List.of("80m", "40m", "20m", "10m", "2m");

        protected boolean fmEnabled = false;

    // -------------------- MEMORIES (2026-11-09) --------------------

    /** Memory channels, numbered 1 to 50 on the panel (0 to 49 here). */
    public static final int MEMORIES = 50;
    /** Each memory's frequency (0 for empty) and mode (0 SSB, 1 CW, 2 AM, 3 FM). */
    private final float[] memoryHz = new float[MEMORIES];
    private final byte[] memoryMode = new byte[MEMORIES];
    /** On the memories (MEM) rather than the VFO, and which one. */
    private boolean memoryMode_ = false;
    private int memoryChannel = 0;
    /** The VFO's frequency, kept while on the memories. */
    private float vfoHz = 0.0F;

    public boolean isMemoryMode() {
        return memoryMode_;
    }

    public int getMemoryChannel() {
        return memoryChannel;
    }

    /** Channel {@code channel}'s frequency, 0 if it is empty. */
    public float getMemoryHz(int channel) {
        return channel >= 0 && channel < MEMORIES ? memoryHz[channel] : 0.0F;
    }

    public String getMemoryModeName(int channel) {
        return channel >= 0 && channel < MEMORIES ? MODE_NAMES[memoryMode[channel]] : "SSB";
    }

    private static final String[] MODE_NAMES = { "SSB", "CW", "AM", "FM" };

    private byte currentModeIndex() {
        return (byte)(getCWEnabled() ? 1 : isAM() ? 2 : isFM() ? 3 : 0);
    }

    private void applyMode(byte mode) {
        switch(mode) {
            case 1 -> setCWEnabled(true);
            case 2 -> setAMEnabled(true);
            case 3 -> setFMEnabled(true);
            default -> setSSBEnabled(true);
        }
    }

    /** MEMORY held: the VFO's frequency and mode into channel {@code channel}. */
    public void storeMemory(int channel) {
        if(channel < 0 || channel >= MEMORIES || memoryMode_)
            return;
        memoryHz[channel] = frequency;
        memoryMode[channel] = currentModeIndex();
        memoryChannel = channel;
        setChanged();
        updateBlock();
    }

    /** VFO/MEM: onto the memories (the last one used, or the first stored) or back to the VFO where it was. */
    public void toggleMemoryMode() {
        if(memoryMode_) {
            memoryMode_ = false;
            if(vfoHz > 0.0F)
                recallFrequency(vfoHz);
        }
        else {
            int channel = memoryHz[memoryChannel] > 0.0F ? memoryChannel : nextStored(memoryChannel, 1);
            if(channel < 0)
                return; // No memories yet.
            vfoHz = frequency;
            memoryMode_ = true;
            recall(channel);
        }
        setChanged();
        updateBlock();
    }

    /** On the memories: to channel {@code channel}, if it holds a frequency. */
    public void selectMemory(int channel) {
        if(!memoryMode_ || channel < 0 || channel >= MEMORIES || memoryHz[channel] <= 0.0F)
            return;
        recall(channel);
        setChanged();
        updateBlock();
    }

    private void recall(int channel) {
        memoryChannel = channel;
        applyMode(memoryMode[channel]);
        recallFrequency(memoryHz[channel]);
    }

    /** VFO/MEM held on a memory: the VFO takes the memory's frequency, and the radio goes back to the VFO. */
    public void memoryToVfo() {
        if(!memoryMode_)
            return;
        memoryMode_ = false;
        vfoHz = frequency;
        setChanged();
        updateBlock();
    }

    /** The next channel after {@code from} in {@code direction} that holds a frequency (wrapping), -1 if none. */
    public int nextStored(int from, int direction) {
        for(int i = 1; i <= MEMORIES; i++) {
            int channel = Math.floorMod(from + i * direction, MEMORIES);
            if(memoryHz[channel] > 0.0F)
                return channel;
        }
        return -1;
    }

    /** QUICK and the memories cross bands: the band knob turns to wherever {@code hz} is. */
    @Override
    public boolean recallFrequency(float hz) {
        for(int i = 0; i < BANDS.size(); i++) {
            Band candidate = Band.getBand(BANDS.get(i));
            if(candidate != null && com.arrl.radiocraft.common.radio.BandEdges.canTune(candidate, hz)) {
                if(i != getBandIndex())
                    setBandIndex(i);
                return tuneTo(hz);
            }
        }
        return tuneTo(hz);
    }

    public HFRadioAllBandBlockEntity(BlockPos pos, BlockState state) {
        super(RadiocraftBlockEntities.HF_RADIO_ALL_BAND.get(), pos, state, Band.getBand("10m"));
    }

    /** Position of the current band on the knob (see {@link #BANDS}), or -1 if the band is not one of them. */
    public int getBandIndex() {
        return band == null ? -1 : BANDS.indexOf(band.name());
    }

    /** Turns the band knob to position {@code index}; the frequency goes to the bottom of the new band. */
    public void setBandIndex(int index) {
        if(index < 0 || index >= BANDS.size() || index == getBandIndex())
            return;
        Band newBand = Band.getBand(BANDS.get(index));
        if(newBand != null)
            changeBand(newBand);
    }

    public boolean getFMEnabled() {
        return fmEnabled;
    }

    /** AM clears FM first: the two are one voice-mode choice here. */
    @Override
    public void setAMEnabled(boolean value) {
        if(value && fmEnabled) {
            fmEnabled = false;
            updateBlock();
            setChanged();
        }
        super.setAMEnabled(value);
    }

    /** Selects FM: voice like SSB, without the static. */
    public void setFMEnabled(boolean value) {
        if(fmEnabled == value)
            return;
        fmEnabled = value;
        if(value) {
            amEnabled = false;
            super.setSSBEnabled(true); // Voice on (and CW off).
        }
        updateBlock();
        setChanged();
    }

    @Override
    public void setSSBEnabled(boolean value) {
        if(value && (fmEnabled || amEnabled)) {
            fmEnabled = false;
            amEnabled = false;
            updateBlock();
            setChanged();
        }
        super.setSSBEnabled(value);
    }

    @Override
    public void setCWEnabled(boolean value) {
        if(value) {
            fmEnabled = false;
            amEnabled = false;
        }
        super.setCWEnabled(value);
    }

    @Override
    public boolean isFM() {
        return fmEnabled;
    }

    @Override
    public boolean shouldPlayStatic() {
        return !fmEnabled && !amEnabled && super.shouldPlayStatic(); // FM is squelched; AM hisses only under a weak signal.
    }

    @Override
    protected void setupSaveTag(CompoundTag nbt) {
        super.setupSaveTag(nbt);
                nbt.putBoolean("fmEnabled", fmEnabled);
        int[] hz = new int[MEMORIES];
        for(int i = 0; i < MEMORIES; i++)
            hz[i] = Float.floatToIntBits(memoryHz[i]);
        nbt.putIntArray("memoryHz", hz);
        nbt.putByteArray("memoryMode", memoryMode.clone());
        nbt.putBoolean("onMemory", memoryMode_);
        nbt.putInt("memoryChannel", memoryChannel);
        nbt.putFloat("vfoHz", vfoHz);
    }

    @Override
    protected void readSaveTag(CompoundTag nbt) {
        super.readSaveTag(nbt);
                fmEnabled = nbt.getBoolean("fmEnabled");
        int[] hz = nbt.getIntArray("memoryHz");
        byte[] modes = nbt.getByteArray("memoryMode");
        for(int i = 0; i < MEMORIES; i++) {
            memoryHz[i] = i < hz.length ? Float.intBitsToFloat(hz[i]) : 0.0F;
            memoryMode[i] = i < modes.length ? modes[i] : 0;
        }
        memoryMode_ = nbt.getBoolean("onMemory");
        memoryChannel = Math.max(0, Math.min(MEMORIES - 1, nbt.getInt("memoryChannel")));
        vfoHz = nbt.getFloat("vfoHz");
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("container.hf_radio_all_band");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory playerInventory, @NotNull Player player) {
        return new HFRadioAllBandMenu(id, this);
    }

    @Override
    public BENetworkObject createNetworkObject() {
        return new RadioNetworkObject(level, worldPosition, CommonConfig.HF_RADIO_ALL_BAND_TRANSMIT_TICK.get(), CommonConfig.HF_RADIO_ALL_BAND_RECEIVE_TICK.get());
    }

}
