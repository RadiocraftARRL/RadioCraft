package com.arrl.radiocraft.common.be_networks.network_objects;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.VHFReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The VHF receiver on its power wire and coax (2026-11-09). It pays its draw from the wire first, like any radio; with
 * nothing on the wire it runs from the small cell in it ({@link VHFReceiverBlockEntity#getCellEnergy}), which the block
 * entity drains at its own, much lower, rate while {@link #isOnCell} says so. A flat cell browns it out as a flat
 * battery does a wired radio.
 */
public class VHFReceiverNetworkObject extends RadioNetworkObject {

    public static final ResourceLocation TYPE = Radiocraft.id("vhf_receiver");

    /** True while the receiver's last tick was paid for by its cell, not the wire. */
    private boolean onCell = false;

    public VHFReceiverNetworkObject(Level level, BlockPos pos, int transmitUse, int receiveUse) {
        super(level, pos, transmitUse, receiveUse);
    }

    /** Wire first; with nothing there, the cell, if it has any charge. The cell itself is drained by the block entity. */
    @Override
    protected boolean tryConsumeEnergy(int amount, boolean simulate) {
        if(super.tryConsumeEnergy(amount, simulate)) {
            if(!simulate)
                onCell = false;
            return true;
        }
        boolean cell = cellEnergy() > 0;
        if(!simulate)
            onCell = cell;
        return cell;
    }

    /** FE left in the receiver's cell (0 with none, or if the block is not loaded). */
    private int cellEnergy() {
        if(level == null || !level.isLoaded(pos))
            return 0;
        return level.getBlockEntity(pos) instanceof VHFReceiverBlockEntity receiver ? receiver.getCellEnergy() : 0;
    }

    /** True while it runs (switched on, powered) from its cell, with nothing coming in over the wire. Never with power draw off. */
    public boolean isOnCell() {
        return isPowered && onCell && com.arrl.radiocraft.RadiocraftServerConfig.RADIO_POWER_REQUIRED.get();
    }

    /** A cell in it counts as a battery, so a flat one makes the panel show PWR as a flat battery on the wire does. */
    @Override
    public boolean hasBattery() {
        if(level != null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof VHFReceiverBlockEntity receiver && receiver.hasCell())
            return true;
        return super.hasBattery();
    }

    @Override
    public ResourceLocation getType() {
        return TYPE;
    }
}
