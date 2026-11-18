package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.api.capabilities.IBENetworks;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.INetworkObjectProvider;
import com.arrl.radiocraft.common.blocks.WireBlock;
import com.arrl.radiocraft.common.blocks.antennas.DoubleVHFAntennaBlock;
import com.arrl.radiocraft.common.entities.AntennaWire;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Helpers shared by RadioCraft GameTests. All positions are relative to the test structure unless stated otherwise.
 */
public final class GameTestUtil {

    /** Name of the empty 16x8x16 template in src/gametest/resources/data/radiocraft/structure. */
    public static final String EMPTY = "empty16";

    private GameTestUtil() {}

    /**
     * Strings an antenna wire between two relative positions, the same way a player using the wire item would. Wires
     * only survive between wire-holder blocks (baluns and Antenna Wire Isolators; not poles since 2026-11-18), so if
     * {@code to} is air an isolator is placed there first.
     */
    public static AntennaWire wire(GameTestHelper helper, BlockPos from, BlockPos to) {
        if (helper.getBlockState(to).isAir())
            helper.setBlock(to, RadiocraftBlocks.ANTENNA_CONNECTOR.get());
        AntennaWire wire = new AntennaWire(helper.getLevel(), helper.absolutePos(from));
        helper.getLevel().addFreshEntity(wire);
        wire.setEndPos(helper.absolutePos(to));
        wire.strung();
        return wire;
    }

    /**
     * Places a wire/coax block with the state a player would get (connections to its neighbors computed), rather than
     * the default state with no connections that {@link GameTestHelper#setBlock} would use.
     */
    public static void placeWire(GameTestHelper helper, BlockPos pos, WireBlock wire) {
        helper.setBlock(pos, wire.getBlockState(helper.getLevel(), helper.absolutePos(pos)));
    }

    /** A VHF base station at {@code radio} with a J-pole on its coax: coax one block south, the J-pole two south. */
    public static void radioWithJPole(GameTestHelper helper, BlockPos radio) {
        radioWithDoubleAntenna(helper, radio, RadiocraftBlocks.J_POLE_ANTENNA.get());
    }

    /** A VHF base station with an EB-VHF eggbeater, the satellite antenna (2026-11-12), on coax to its south. */
    public static void radioWithEggbeater(GameTestHelper helper, BlockPos radio) {
        radioWithDoubleAntenna(helper, radio, RadiocraftBlocks.EGGBEATER_ANTENNA.get());
    }

    private static void radioWithDoubleAntenna(GameTestHelper helper, BlockPos radio, net.minecraft.world.level.block.Block antenna) {
        helper.setBlock(radio, RadiocraftBlocks.VHF_BASE_STATION.get());
        placeWire(helper, radio.south(), RadiocraftBlocks.COAX_WIRE.get());
        BlockPos lower = radio.south(2);
        helper.setBlock(lower, antenna.defaultBlockState().setValue(DoubleVHFAntennaBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(lower.above(), antenna.defaultBlockState().setValue(DoubleVHFAntennaBlock.HALF, DoubleBlockHalf.UPPER));
    }

    public static <T extends BlockEntity> T blockEntity(GameTestHelper helper, BlockPos pos, Class<T> type) {
        BlockEntity be = helper.getBlockEntity(pos);
        if (!type.isInstance(be))
            helper.fail("Expected " + type.getSimpleName() + " but found " + (be == null ? "nothing" : be.getClass().getSimpleName()), pos);
        return type.cast(be);
    }

    /**
     * Gets the network object at pos. Network objects are created lazily (normally in BlockEntity#onLoad), so this
     * asks the block entity for it the same way the game does instead of reading the cap directly.
     */
    public static <T extends BENetworkObject> T networkObject(GameTestHelper helper, BlockPos pos, Class<T> type) {
        BlockPos abs = helper.absolutePos(pos);
        BENetworkObject obj = helper.getLevel().getBlockEntity(abs) instanceof INetworkObjectProvider provider
                ? provider.getNetworkObject(helper.getLevel(), abs)
                : IBENetworks.getObject(helper.getLevel(), abs);
        if (!type.isInstance(obj))
            helper.fail("Expected " + type.getSimpleName() + " but found " + (obj == null ? "nothing" : obj.getClass().getSimpleName()), pos);
        return type.cast(obj);
    }
}
