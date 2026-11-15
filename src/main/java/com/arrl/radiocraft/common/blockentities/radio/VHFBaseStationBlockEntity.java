package com.arrl.radiocraft.common.blockentities.radio;

import com.arrl.radiocraft.CommonConfig;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.datacomponents.HandheldMemory;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.menus.VHFBaseStationMenu;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.TuningStep;
import com.arrl.radiocraft.common.radio.voice.Ctcss;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class VHFBaseStationBlockEntity extends VHFRadioBlockEntity implements MemoryRadio {

    // -------------------- MEMORIES (2026-11-14) --------------------

    /**
     * Memory channels, numbered 1 to 50 on the panel (0 to 49 here), as the all-band radio's (2026-11-14, the maintainer:
     * "the vhf base stations are missing the MEMORY button and MEM/VFO toggle and the ability to save and navigate those
     * channels"). Each holds what a 2m channel needs: the frequency, the repeater offset, the CTCSS tone and the STEP.
     */
    public static final int MEMORIES = 50;
    private final float[] memoryHz = new float[MEMORIES];
    private final float[] memoryTone = new float[MEMORIES];
    private final int[] memoryOffset = new int[MEMORIES];
    private final int[] memoryStep = new int[MEMORIES];
    /** On the memories (MEM) rather than the VFO, and which one. */
    private boolean onMemory = false;
    private int memoryChannel = 0;
    /** The VFO's settings, kept while on the memories. */
    private float vfoHz = 0.0F, vfoTone = Ctcss.NONE;
    private int vfoOffset = 0, vfoStep = 0;

    public VHFBaseStationBlockEntity(BlockPos pos, BlockState state) {
        super(RadiocraftBlockEntities.VHF_BASE_STATION.get(), pos, state, Band.getBand("2m"));
        squelch = defaultSquelch(); // A new one; a saved one loads its own.
    }

    @Override
    public int memoryCount() {
        return MEMORIES;
    }

    @Override
    public boolean isMemoryMode() {
        return onMemory;
    }

    @Override
    public int getMemoryChannel() {
        return memoryChannel;
    }

    @Override
    public float getMemoryHz(int channel) {
        return channel >= 0 && channel < MEMORIES ? memoryHz[channel] : 0.0F;
    }

    public float getMemoryTone(int channel) {
        return channel >= 0 && channel < MEMORIES ? memoryTone[channel] : Ctcss.NONE;
    }

    public int getMemoryOffset(int channel) {
        return channel >= 0 && channel < MEMORIES ? memoryOffset[channel] : 0;
    }

    public int getMemoryStep(int channel) {
        return channel >= 0 && channel < MEMORIES ? memoryStep[channel] : 0;
    }

    /** The offset and tone, as the display's top line shows them: "-600 T88.5". */
    @Override
    public String memoryLabel(int channel) {
        return Ctcss.formatOffset(getMemoryOffset(channel)) + " T" + Ctcss.format(getMemoryTone(channel));
    }

    @Override
    public void storeMemory(int channel) {
        if(channel < 0 || channel >= MEMORIES || onMemory)
            return;
        memoryHz[channel] = frequency;
        memoryTone[channel] = ctcssTone;
        memoryOffset[channel] = offsetHz;
        memoryStep[channel] = tuneStepHz;
        memoryChannel = channel;
        setChanged();
        updateBlock();
    }

    @Override
    public void toggleMemoryMode() {
        if(onMemory) {
            onMemory = false;
            apply(vfoTone, vfoOffset, vfoStep);
            if(vfoHz > 0.0F)
                recallFrequency(vfoHz);
        }
        else {
            int channel = memoryHz[memoryChannel] > 0.0F ? memoryChannel : nextStored(memoryChannel, 1);
            if(channel < 0)
                return; // No memories yet.
            vfoHz = frequency;
            vfoTone = ctcssTone;
            vfoOffset = offsetHz;
            vfoStep = tuneStepHz;
            onMemory = true;
            recall(channel);
        }
        setChanged();
        updateBlock();
    }

    @Override
    public void selectMemory(int channel) {
        if(!onMemory || channel < 0 || channel >= MEMORIES || memoryHz[channel] <= 0.0F)
            return;
        recall(channel);
        setChanged();
        updateBlock();
    }

    private void recall(int channel) {
        memoryChannel = channel;
        int step = memoryStep[channel] != 0 ? memoryStep[channel] : TuningStep.fitting(memoryHz[channel]); // A cloned channel kept no step: one it sits on.
        apply(memoryTone[channel], memoryOffset[channel], step);
        recallFrequency(memoryHz[channel]); // Tuned as any other: no MARS mod, no out of band.
    }

    /** The settings a channel carries, put straight on (the frequency, tuned after, lands on the step's grid). */
    private void apply(float tone, int offset, int step) {
        ctcssTone = Ctcss.sanitize(tone);
        offsetHz = Ctcss.sanitizeOffset(offset);
        tuneStepHz = TuningStep.sanitize(step);
    }

    @Override
    public void memoryToVfo() {
        if(!onMemory)
            return;
        onMemory = false;
        vfoHz = frequency;
        vfoTone = ctcssTone;
        vfoOffset = offsetHz;
        vfoStep = tuneStepHz;
        setChanged();
        updateBlock();
    }

    @Override
    public int nextStored(int from, int direction) {
        for(int i = 1; i <= MEMORIES; i++) {
            int channel = Math.floorMod(from + i * direction, MEMORIES);
            if(memoryHz[channel] > 0.0F)
                return channel;
        }
        return -1;
    }

    /**
     * Its first {@link HandheldMemory#SLOTS} channels as a handheld's memories, for the Radio Cloner (a handheld has ten).
     * Names stay empty: the base station has none.
     */
    public HandheldMemory toHandheldMemory() {
        List<HandheldMemory.Channel> channels = new ArrayList<>();
        for(int i = 0; i < HandheldMemory.SLOTS && i < MEMORIES; i++)
            if(memoryHz[i] > 0.0F)
                channels.add(new HandheldMemory.Channel(i, memoryHz[i], memoryTone[i], memoryTone[i], memoryOffset[i]));
        return new HandheldMemory(channels, false, 0, null, HandheldMemory.Display.FREQUENCY).sanitized();
    }

    /**
     * A handheld's memories from the Radio Cloner into the first {@link HandheldMemory#SLOTS} channels (cleared first; the
     * rest stay). Each keeps this radio's STEP for that channel (none: the server's). Back to the VFO.
     */
    public void loadHandheldMemory(HandheldMemory memory) {
        for(int i = 0; i < HandheldMemory.SLOTS && i < MEMORIES; i++) {
            memoryHz[i] = 0.0F;
            memoryTone[i] = Ctcss.NONE;
            memoryOffset[i] = 0;
            memoryStep[i] = 0;
        }
        for(HandheldMemory.Channel channel : memory.channels()) {
            int i = channel.slot();
            if(i < 0 || i >= MEMORIES || !Float.isFinite(channel.frequency()) || channel.frequency() <= 0.0F)
                continue;
            memoryHz[i] = channel.frequency();
            memoryTone[i] = Ctcss.sanitize(channel.ctcssTone());
            memoryOffset[i] = Ctcss.sanitizeOffset(channel.offsetHz());
        }
        if(onMemory)
            toggleMemoryMode(); // Off the memories: the one in use may be gone.
        setChanged();
        updateBlock();
    }

    @Override
    protected void setupSaveTag(CompoundTag nbt) {
        super.setupSaveTag(nbt);
        int[] hz = new int[MEMORIES], tone = new int[MEMORIES];
        for(int i = 0; i < MEMORIES; i++) {
            hz[i] = Float.floatToIntBits(memoryHz[i]);
            tone[i] = Float.floatToIntBits(memoryTone[i]);
        }
        nbt.putIntArray("memoryHz", hz);
        nbt.putIntArray("memoryTone", tone);
        nbt.putIntArray("memoryOffset", memoryOffset.clone());
        nbt.putIntArray("memoryStep", memoryStep.clone());
        nbt.putBoolean("onMemory", onMemory);
        nbt.putInt("memoryChannel", memoryChannel);
        nbt.putFloat("vfoHz", vfoHz);
        nbt.putFloat("vfoTone", vfoTone);
        nbt.putInt("vfoOffset", vfoOffset);
        nbt.putInt("vfoStep", vfoStep);
    }

    @Override
    protected void readSaveTag(CompoundTag nbt) {
        super.readSaveTag(nbt);
        int[] hz = nbt.getIntArray("memoryHz"), tone = nbt.getIntArray("memoryTone"), offset = nbt.getIntArray("memoryOffset"), step = nbt.getIntArray("memoryStep");
        for(int i = 0; i < MEMORIES; i++) {
            float f = i < hz.length ? Float.intBitsToFloat(hz[i]) : 0.0F;
            memoryHz[i] = Float.isFinite(f) && f > 0.0F ? f : 0.0F;
            memoryTone[i] = i < tone.length ? Ctcss.sanitize(Float.intBitsToFloat(tone[i])) : Ctcss.NONE;
            memoryOffset[i] = i < offset.length ? Ctcss.sanitizeOffset(offset[i]) : 0;
            memoryStep[i] = i < step.length ? TuningStep.sanitize(step[i]) : 0;
        }
        onMemory = nbt.getBoolean("onMemory");
        memoryChannel = Math.max(0, Math.min(MEMORIES - 1, nbt.getInt("memoryChannel")));
        vfoHz = nbt.getFloat("vfoHz");
        vfoTone = Ctcss.sanitize(nbt.getFloat("vfoTone"));
        vfoOffset = Ctcss.sanitizeOffset(nbt.getInt("vfoOffset"));
        vfoStep = TuningStep.sanitize(nbt.getInt("vfoStep"));
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("container.vhf_base_station");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return new VHFBaseStationMenu(id, this);
    }

    @Override
    public BENetworkObject createNetworkObject() {
        return new RadioNetworkObject(level, worldPosition, CommonConfig.VHF_BASE_STATION_TRANSMIT_TICK.get(), CommonConfig.VHF_BASE_STATION_RECEIVE_TICK.get());
    }

    /** It comes with the squelch on 1 (2026-11-11): just shut on the receiver's hiss. */
    @Override
    protected float defaultSquelch() {
        return 0.025F;
    }

    /** Its new panel has a squelch knob (2026-11-09). */
    @Override
    public boolean hasSquelchKnob() {
        return true;
    }
}
