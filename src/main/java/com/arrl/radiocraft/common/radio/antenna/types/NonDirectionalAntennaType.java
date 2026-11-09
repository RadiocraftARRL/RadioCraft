package com.arrl.radiocraft.common.radio.antenna.types;

import com.arrl.radiocraft.api.antenna.IAntenna;
import com.arrl.radiocraft.api.antenna.IAntennaPacket;
import com.arrl.radiocraft.api.antenna.IAntennaType;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.BandUtils;
import com.arrl.radiocraft.common.radio.antenna.data.AntennaData;
import com.arrl.radiocraft.common.radio.solar.Aurora;
import com.arrl.radiocraft.common.radio.solar.SporadicE;
import com.arrl.radiocraft.common.radio.solar.SolarEventManager;
import com.arrl.radiocraft.common.radio.terrain.TerrainPropagation;
import com.arrl.radiocraft.compat.sable.Sublevels;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public abstract class NonDirectionalAntennaType<T extends AntennaData> implements IAntennaType<T> {

    private static final double CW_RANGE_MULTIPLIER = 1.5D;

    private final ResourceLocation id;
    private final double los;
    private final double skip;
    private final double receiveGainDbi;
    private final double transmitGainDbi;
    private final double receiveGainLinear;
    private final double transmitGainLinear;

    protected NonDirectionalAntennaType(ResourceLocation id, double receiveGainDbi, double transmitGainDbi, double los, double skip) {
        this.id = id;
        this.los = los;
        this.skip = skip;
        this.receiveGainDbi = receiveGainDbi;
        this.transmitGainDbi = transmitGainDbi;
        this.receiveGainLinear = dbiToLinear(receiveGainDbi);
        this.transmitGainLinear = dbiToLinear(transmitGainDbi);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public double getTransmitEfficiency(IAntennaPacket packet, T data, BlockPos destination, boolean isCW) {
        double distance = computeDistance(packet, destination);
        double adjustedDistance = modifyDistanceForTransmit(packet, data, destination, distance);
        double propagation = getPropagationStrength(packet, destination, adjustedDistance, isCW);
        return propagation * getTransmitGainLinear();
    }

    @Override
    public double getReceiveEfficiency(IAntennaPacket packet, T data, BlockPos pos) {
        return getReceiveGainLinear() * packet.getStrength();
    }

    protected double computeDistance(IAntennaPacket packet, BlockPos destination) {
        IAntenna.AntennaPos from = sourcePos(packet);
        if(from == null)
            return Double.POSITIVE_INFINITY; // The sender went away mid-broadcast: out of reach.
        return Math.sqrt(Sublevels.distSqr(packet.getLevel(), from.position(), destination));
    }

    /**
     * The sending antenna's position, read once; null when there is no sender or it has none any more. A mobile
     * radio's position is cleared on the server thread when it is put away, dropped or its player logs out, which can
     * happen while Simple Voice Chat's thread is still delivering its audio (2026-11-09).
     */
    protected static IAntenna.AntennaPos sourcePos(IAntennaPacket packet) {
        IAntenna source = packet.getSource();
        return source == null ? null : source.getAntennaPos();
    }

    protected double modifyDistanceForTransmit(IAntennaPacket packet, T data, BlockPos destination, double distance) {
        return distance;
    }

    protected double getPropagationStrength(IAntennaPacket packet, BlockPos destination, double distance, boolean isCW) {
        double effectiveDistance = isCW ? distance / CW_RANGE_MULTIPLIER : distance;
        Level level = packet.getLevel();
        IAntenna.AntennaPos from = sourcePos(packet);
        if(from == null || Double.isInfinite(distance))
            return 0.0D; // The sender went away mid-broadcast.
        // Where both ends really are (either may be on a Sable ship), for solar weather, aurora and terrain.
        BlockPos source = from.world();
        destination = Sublevels.world(level, destination);
        // Solar weather only touches the sky wave: a poor or closed band weakens skip, line of sight is unchanged. An
        // aurora over either end costs HF a condition step, and lets VHF skip between two stations under it.
        double skip = getSkipEfficiency() * SolarEventManager.getCondition(level, packet.getBand(), source, destination).getSkipMultiplier();
        double strength = BandUtils.getBaseStrength(packet.getBand(), effectiveDistance, getLosEfficiency(), skip, level.isDay(),
                getLosRangeScale(packet.getBand()), getMaxSkipScale(packet.getBand()));
        // Terrain: hills in the way of line of sight, or the horizon around either station for skip.
        if(strength > 0.0D && packet.getBand() != null)
            strength *= TerrainPropagation.factor(packet, destination, effectiveDistance >= packet.getBand().losRange() * getLosRangeScale(packet.getBand()));
        return Math.max(strength, Math.max(Aurora.vhfStrength(level, packet.getBand(), source, destination, effectiveDistance),
                SporadicE.vhfStrength(level, packet.getBand(), effectiveDistance)));
    }

    /** How far line of sight reaches on {@code band} with this antenna, as a share of the band's range. */
    protected double getLosRangeScale(Band band) {
        return 1.0D;
    }

    /** How far skip reaches on {@code band} with this antenna, as a share of the band's furthest skip. */
    protected double getMaxSkipScale(Band band) {
        return 1.0D;
    }

        protected double getLosEfficiency() {
        return los;
    }

    protected double getSkipEfficiency() {
        return skip;
    }

    public double getReceiveGainDbi() {
        return receiveGainDbi;
    }

    public double getTransmitGainDbi() {
        return transmitGainDbi;
    }

    public double getReceiveGainLinear() {
        return receiveGainLinear;
    }

    public double getTransmitGainLinear() {
        return transmitGainLinear;
    }

    private static double dbiToLinear(double dbi) {
        return Math.pow(10.0D, dbi / 10.0D);
    }

}
