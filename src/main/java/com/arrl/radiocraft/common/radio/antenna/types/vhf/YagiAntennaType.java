package com.arrl.radiocraft.common.radio.antenna.types.vhf;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.antenna.IAntennaPacket;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.StaticAntenna;
import com.arrl.radiocraft.common.radio.antenna.types.DirectionalAntennaType;
import com.arrl.radiocraft.common.radio.antenna.data.YagiAntennaData;
import net.minecraft.core.BlockPos;
import com.arrl.radiocraft.common.blocks.antennas.YagiAntennaBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class YagiAntennaType extends DirectionalAntennaType<YagiAntennaData> {

    public YagiAntennaType() {
        super(Radiocraft.id("yagi"), 0.0D, 0.0D, 1.0D, 0.0D);
    }

    @Override
    public StaticAntenna<YagiAntennaData> match(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if(!state.is(RadiocraftBlocks.YAGI_ANTENNA.get()))
            return null;
        return new StaticAntenna<>(this, pos, level, new YagiAntennaData(YagiAntennaBlock.bearing(state))); // Aimed as placed (2026-11-12).
    }

    @Override
    public double getDirectionalEfficiency(YagiAntennaData data, BlockPos from, BlockPos to) {
        return com.arrl.radiocraft.common.radio.antenna.YagiAim.efficiency(data.getBearing(), to.getX() - from.getX(), to.getZ() - from.getZ());
    }

    @Override
    protected double modifyDistanceForTransmit(IAntennaPacket packet, YagiAntennaData data, BlockPos destination, double distance) {
        return distance / 2.0D;
    }

    @Override
    public double getSWR(YagiAntennaData data, float frequencyHertz) {
        Band band = Band.getBand(frequencyHertz); // A fresh Band from the config each call: compare by band, not identity.
        return com.arrl.radiocraft.common.radio.Band.twoMetreAntennaSwr(band);
    }

    @Override
    public YagiAntennaData getDefaultData() {
        return new YagiAntennaData(0.0D);
    }

}
