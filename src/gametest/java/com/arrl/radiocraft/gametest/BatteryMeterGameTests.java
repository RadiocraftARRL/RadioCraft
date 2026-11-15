package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.BatteryMeterReading;
import com.arrl.radiocraft.common.be_networks.network_objects.BatteryMeterNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.BatteryNetworkObject;
import com.arrl.radiocraft.common.blockentities.BatteryMeterBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The battery meter (maintainer, 2026-10-28): it reads the batteries on its power wire and gives a comparator signal. */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class BatteryMeterGameTests {

    /**
     * A meter on a wire with three batteries, one on its far side (power passes through it): one full and two half
     * full read as two thirds charged, comparator 10.
     */
    @GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
    public static void meterReadsTheBatteriesOnItsWire(GameTestHelper helper) {
        BlockPos full = new BlockPos(1, 2, 1), empty = new BlockPos(1, 2, 3), meter = new BlockPos(3, 2, 2), beyond = new BlockPos(5, 2, 2);
        helper.setBlock(full, RadiocraftBlocks.LARGE_BATTERY.get());
        helper.setBlock(empty, RadiocraftBlocks.LARGE_BATTERY.get());
        GameTestUtil.placeWire(helper, new BlockPos(1, 2, 2), RadiocraftBlocks.WIRE.get());
        GameTestUtil.placeWire(helper, new BlockPos(2, 2, 2), RadiocraftBlocks.WIRE.get());
        helper.setBlock(meter, RadiocraftBlocks.BATTERY_METER.get());
        GameTestUtil.placeWire(helper, new BlockPos(4, 2, 2), RadiocraftBlocks.WIRE.get());
        helper.setBlock(beyond, RadiocraftBlocks.LARGE_BATTERY.get());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(GameTestUtil.networkObject(helper, meter, BatteryMeterNetworkObject.class).getReading().batteries() == 3,
                        "The meter does not see all three batteries yet: " + GameTestUtil.networkObject(helper, meter, BatteryMeterNetworkObject.class).getReading()))
                .thenExecute(() -> {
                    BatteryNetworkObject a = GameTestUtil.networkObject(helper, full, BatteryNetworkObject.class);
                    BatteryNetworkObject b = GameTestUtil.networkObject(helper, beyond, BatteryNetworkObject.class);
                    a.getStorage().setEnergy(a.getStorage().getMaxEnergyStored());
                    b.getStorage().setEnergy(b.getStorage().getMaxEnergyStored() / 2);
                    GameTestUtil.networkObject(helper, empty, BatteryNetworkObject.class).getStorage().setEnergy(b.getStorage().getMaxEnergyStored() / 2);
                })
                .thenWaitUntil(() -> {
                    BatteryMeterReading reading = GameTestUtil.networkObject(helper, meter, BatteryMeterNetworkObject.class).getReading();
                    helper.assertTrue(reading.percent() >= 66 && reading.percent() <= 67, "Expected two thirds charged, read " + reading);
                    helper.assertTrue(GameTestUtil.blockEntity(helper, meter, BatteryMeterBlockEntity.class).getComparator() == reading.comparator()
                            && reading.comparator() == 10, "Expected comparator 10, got " + GameTestUtil.blockEntity(helper, meter, BatteryMeterBlockEntity.class).getComparator());
                })
                .thenSucceed();
    }

    /** With nothing on its wire it reads no batteries and gives no comparator signal. */
    @GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100)
    public static void meterWithNoBatteryReadsNothing(GameTestHelper helper) {
        BlockPos meter = new BlockPos(2, 2, 2);
        helper.setBlock(meter, RadiocraftBlocks.BATTERY_METER.get());
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertFalse(GameTestUtil.networkObject(helper, meter, BatteryMeterNetworkObject.class).getReading().hasBatteries(), "A lone meter found a battery");
                    helper.assertTrue(GameTestUtil.blockEntity(helper, meter, BatteryMeterBlockEntity.class).getComparator() == 0, "A lone meter gives a signal");
                })
                .thenSucceed();
    }

    /** A sealed Stable Chemical Battery reads nothing on the meter until its seal is broken (2026-11-13). */
    @GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
    public static void sealedChemicalBatteryReadsNothing(GameTestHelper helper) {
        BlockPos battery = new BlockPos(1, 2, 2), meter = new BlockPos(3, 2, 2), redstone = new BlockPos(1, 2, 1);
        helper.setBlock(battery, RadiocraftBlocks.STABLE_CHEMICAL_BATTERY.get());
        GameTestUtil.placeWire(helper, new BlockPos(2, 2, 2), RadiocraftBlocks.WIRE.get());
        helper.setBlock(meter, RadiocraftBlocks.BATTERY_METER.get());
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    BatteryMeterReading reading = GameTestUtil.networkObject(helper, meter, BatteryMeterNetworkObject.class).getReading();
                    helper.assertFalse(reading.hasBatteries(), "The meter reads a sealed battery: " + reading);
                    helper.setBlock(redstone, net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(GameTestUtil.networkObject(helper, meter, BatteryMeterNetworkObject.class).getReading().batteries() == 1,
                        "Once activated the meter still does not see it: " + GameTestUtil.networkObject(helper, meter, BatteryMeterNetworkObject.class).getReading()))
                .thenSucceed();
    }
}
