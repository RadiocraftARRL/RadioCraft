package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.WireUtils;
import com.arrl.radiocraft.common.be_networks.network_objects.AntennaNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.BatteryNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.WireBundleBlockEntity;
import com.arrl.radiocraft.common.blocks.Strand;
import com.arrl.radiocraft.common.blocks.WireBlock;
import com.arrl.radiocraft.common.blocks.WireBundleBlock;
import com.arrl.radiocraft.common.blocks.antennas.DoubleVHFAntennaBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Wire bundles: several cables in one block ({@link WireBundleBlock}). Each strand keeps its own kind's links, so
 * power and coax share a block without meeting, two colours of coax stay apart, and taking a strand out splits only
 * its network.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class WireBundleGameTests {

	private static final WireBlock WIRE = RadiocraftBlocks.WIRE.get();
	private static final WireBlock COAX = RadiocraftBlocks.COAX_WIRE.get();
	private static final WireBlock RED = RadiocraftBlocks.COLOURED_COAX.get(DyeColor.RED).get();
	private static final WireBlock BLUE = RadiocraftBlocks.COLOURED_COAX.get(DyeColor.BLUE).get();
	private static final WireBlock BLACK = RadiocraftBlocks.COLOURED_COAX.get(DyeColor.BLACK).get();

	private static void jPole(GameTestHelper helper, BlockPos lower) {
		helper.setBlock(lower, RadiocraftBlocks.J_POLE_ANTENNA.get().defaultBlockState().setValue(DoubleVHFAntennaBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(lower.above(), RadiocraftBlocks.J_POLE_ANTENNA.get().defaultBlockState().setValue(DoubleVHFAntennaBlock.HALF, DoubleBlockHalf.UPPER));
	}

	/** Lays {@code first} at {@code pos} as a player would, then the other kinds in with it. */
	private static void bundle(GameTestHelper helper, BlockPos pos, WireBlock first, WireBlock... more) {
		GameTestUtil.placeWire(helper, pos, first);
		for(WireBlock kind : more) {
			helper.assertTrue(WireBundleBlock.canAdd(helper.getLevel(), helper.absolutePos(pos), kind), kind + " cannot be added at " + pos.toShortString());
			WireBundleBlock.add(helper.getLevel(), helper.absolutePos(pos), kind);
		}
	}

	private static List<Strand> strands(GameTestHelper helper, BlockPos pos) {
		return Strand.at(helper.getLevel(), helper.absolutePos(pos));
	}

	private static int antennas(GameTestHelper helper, BlockPos radio) {
		return GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class).getAntennas().size();
	}

	/**
	 * A radio's coax and its power wire leave by the same two blocks: the coax reaches the J-pole, the wire the battery,
	 * and neither sees the other's network.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 80)
	public static void coaxAndPowerShareABlock(GameTestHelper helper) {
		BlockPos radio = new BlockPos(3, 2, 2);
		BlockPos battery = new BlockPos(5, 2, 4);
		helper.setBlock(radio, RadiocraftBlocks.VHF_BASE_STATION.get());
		jPole(helper, radio.south(3));
		helper.setBlock(battery, RadiocraftBlocks.LARGE_BATTERY.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					bundle(helper, radio.south(), COAX, WIRE);
					bundle(helper, radio.south(2), COAX, WIRE);
					GameTestUtil.placeWire(helper, radio.south(2).east(), WIRE);
					helper.assertTrue(helper.getBlockState(radio.south()).is(RadiocraftBlocks.WIRE_BUNDLE.get()), "the first block is not a bundle");
					helper.assertTrue(strands(helper, radio.south(2)).size() == 2, "the second bundle has " + strands(helper, radio.south(2)).size() + " strands");
					helper.assertFalse(WireBundleBlock.canAdd(helper.getLevel(), helper.absolutePos(radio.south()), RadiocraftBlocks.WIRE.get()), "a second power wire went in");
					helper.assertFalse(WireBundleBlock.canAdd(helper.getLevel(), helper.absolutePos(radio.south()), (WireBlock)RadiocraftBlocks.WATERPROOF_WIRE.get()), "two power wires in one block");
					helper.assertTrue(WireBundleBlock.canAdd(helper.getLevel(), helper.absolutePos(radio.south()), RED), "a second coax colour cannot go in");
				})
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertTrue(antennas(helper, radio) == 1, "the coax strand reaches " + antennas(helper, radio) + " antennas");
					RadioNetworkObject network = GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class);
					helper.assertTrue(network.hasBattery(), "the power strand does not reach the battery");
					GameTestUtil.networkObject(helper, battery, BatteryNetworkObject.class).getStorage().setEnergy(100_000);
					GameTestUtil.blockEntity(helper, radio, com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity.class).toggle();
				})
				.thenIdle(3)
				.thenExecute(() -> helper.assertTrue(GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class).isPowered, "the radio did not power up over the bundle's wire"))
				.thenSucceed();
	}

	/**
	 * Two radios' runs, each fed by a block of its own colour (a radio touching the bundle itself would take every
	 * coax in it, as it would two coax blocks), share two bundle blocks and reach their own antennas only. Taking the
	 * blue strand out of the first block cuts the blue radio off; putting it back joins it up again; the red run never
	 * notices.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100)
	public static void twoColoursInOneBundleStayApart(GameTestHelper helper) {
		BlockPos radioRed = new BlockPos(3, 2, 1), radioBlue = new BlockPos(5, 2, 3);
		BlockPos first = new BlockPos(3, 2, 3), second = new BlockPos(3, 2, 4);
		helper.setBlock(radioRed, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(radioBlue, RadiocraftBlocks.VHF_BASE_STATION.get());
		jPole(helper, new BlockPos(1, 2, 4)); // Red: west out of the second block.
		jPole(helper, new BlockPos(3, 2, 6)); // Blue: on south.
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					GameTestUtil.placeWire(helper, radioRed.south(), RED); // Into the first block from the north...
					GameTestUtil.placeWire(helper, radioBlue.west(), BLUE); // ...and from the east.
					bundle(helper, first, RED, BLUE);
					bundle(helper, second, RED, BLUE);
					GameTestUtil.placeWire(helper, second.west(), RED);
					GameTestUtil.placeWire(helper, second.south(), BLUE);
				})
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertTrue(antennas(helper, radioRed) == 1, "the red radio sees " + antennas(helper, radioRed) + " antennas");
					helper.assertTrue(antennas(helper, radioBlue) == 1, "the blue radio sees " + antennas(helper, radioBlue) + " antennas");
					WireBundleBlockEntity entity = GameTestUtil.blockEntity(helper, first, WireBundleBlockEntity.class);
					helper.assertTrue(entity.strand(RED) != null && entity.strand(RED).getValue(WireBlock.NORTH), "the red strand is not drawn to the red radio");
					helper.assertFalse(entity.strand(RED).getValue(WireBlock.EAST), "the red strand is drawn to the blue radio");
					helper.assertTrue(entity.strand(BLUE).getValue(WireBlock.EAST), "the blue strand is not drawn to the blue radio");
					helper.assertFalse(entity.strand(BLUE).getValue(WireBlock.NORTH), "the blue strand is drawn to the red radio");
					helper.assertTrue(WireBundleBlock.remove(helper.getLevel(), helper.absolutePos(first), BLUE) != null, "the blue strand did not come out");
					helper.assertTrue(helper.getBlockState(first).is(RED), "down to one strand it should be a red coax block again");
				})
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertTrue(antennas(helper, radioBlue) == 0, "the blue radio still sees " + antennas(helper, radioBlue) + " antennas with its strand gone");
					helper.assertTrue(antennas(helper, radioRed) == 1, "the red radio lost its antenna");
					WireBundleBlock.add(helper.getLevel(), helper.absolutePos(first), BLUE);
				})
				.thenIdle(2)
				.thenExecute(() -> helper.assertTrue(antennas(helper, radioBlue) == 1, "the blue radio does not see its antenna again, " + antennas(helper, radioBlue)))
				.thenSucceed();
	}

	/** A bundle's coax strand climbs a step to the J-pole on it while its power strand runs on along the floor to the battery. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 80)
	public static void bundleStrandsClimbOnTheirOwn(GameTestHelper helper) {
		BlockPos radio = new BlockPos(3, 2, 2);
		BlockPos foot = radio.south(), step = radio.south(2), top = step.above();
		BlockPos battery = new BlockPos(5, 2, 3);
		helper.setBlock(radio, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(step, Blocks.STONE);
		jPole(helper, top);
		helper.setBlock(battery, RadiocraftBlocks.LARGE_BATTERY.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					GameTestUtil.placeWire(helper, foot.east(), WIRE);
					bundle(helper, foot, COAX, WIRE);
					WireBundleBlockEntity entity = GameTestUtil.blockEntity(helper, foot, WireBundleBlockEntity.class);
					helper.assertTrue(entity.strand(COAX).getValue(WireBlock.CLIMB) != WireBlock.Climb.NONE, "the coax strand does not climb the step");
					helper.assertTrue(entity.strand(WIRE).getValue(WireBlock.CLIMB) == WireBlock.Climb.NONE, "the power strand climbs to a J-pole");
					helper.assertTrue(entity.strand(WIRE).getValue(WireBlock.EAST), "the power strand does not run east to its wire");
				})
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertTrue(antennas(helper, radio) == 1, "the climbing coax strand reaches " + antennas(helper, radio) + " antennas");
					helper.assertTrue(GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class).hasBattery(), "the power strand does not reach the battery");
				})
				.thenSucceed();
	}

	/**
	 * The maintainer's playtest (2026-11-11): red coax from one radio and black coax from another run together through
	 * two bundle blocks, and the first of them sits against the black radio, where the black run ends. The red strand
	 * only runs past that radio, so it must not take it: each radio has its own one antenna, neither antenna is shared,
	 * and it stays so when the radios connect again from scratch (a reload). Before the fix both radios saw both
	 * J-poles and showed COAX SHARED. {@code passingFirst} lays the passing red cable first (it takes the radio as a
	 * lone coax, then must let go when the second colour joins it); otherwise the ending one goes first.
	 */
	private static void runPastTheOtherRadio(GameTestHelper helper, WireBlock passing, WireBlock ending, boolean passingFirst) {
		BlockPos radioPassing = new BlockPos(1, 2, 2), radioEnding = new BlockPos(3, 2, 1);
		BlockPos first = new BlockPos(3, 2, 2), second = new BlockPos(3, 2, 3); // The first bundle is against the ending run's radio.
		BlockPos antennaPassing = new BlockPos(3, 2, 5), antennaEnding = new BlockPos(5, 2, 3);
		helper.setBlock(radioPassing, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(radioEnding, RadiocraftBlocks.VHF_BASE_STATION.get());
		jPole(helper, antennaPassing);
		jPole(helper, antennaEnding);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					GameTestUtil.placeWire(helper, radioPassing.east(), passing); // Into the first bundle from the west...
					GameTestUtil.placeWire(helper, second.south(), passing); // ...out of the second south to its J-pole.
					GameTestUtil.placeWire(helper, second.east(), ending); // The ending run leaves the second east to its J-pole.
					bundle(helper, second, passing, ending);
					if(passingFirst)
						bundle(helper, first, passing, ending);
					else
						bundle(helper, first, ending, passing);
				})
				.thenIdle(2)
				.thenExecute(() -> assertOwnAntennas(helper, radioPassing, radioEnding, antennaPassing, antennaEnding))
				.thenExecute(() -> {
					WireUtils.connectToWires(helper.getLevel(), helper.absolutePos(radioEnding)); // As after a reload.
					WireUtils.connectToWires(helper.getLevel(), helper.absolutePos(radioPassing));
				})
				.thenIdle(2)
				.thenExecute(() -> assertOwnAntennas(helper, radioPassing, radioEnding, antennaPassing, antennaEnding))
				.thenSucceed();
	}

	private static void assertOwnAntennas(GameTestHelper helper, BlockPos radioPassing, BlockPos radioEnding, BlockPos antennaPassing, BlockPos antennaEnding) {
		helper.assertTrue(antennas(helper, radioPassing) == 1, "the passing run's radio sees " + antennas(helper, radioPassing) + " antennas");
		helper.assertTrue(antennas(helper, radioEnding) == 1, "the radio the bundle runs past sees " + antennas(helper, radioEnding) + " antennas");
		AntennaNetworkObject passing = GameTestUtil.networkObject(helper, antennaPassing, AntennaNetworkObject.class);
		AntennaNetworkObject ending = GameTestUtil.networkObject(helper, antennaEnding, AntennaNetworkObject.class);
		helper.assertTrue(GameTestUtil.networkObject(helper, radioPassing, RadioNetworkObject.class).getAntennas().contains(passing), "the passing run's radio lost its own J-pole");
		helper.assertTrue(GameTestUtil.networkObject(helper, radioEnding, RadioNetworkObject.class).getAntennas().contains(ending), "the radio the bundle runs past lost its own J-pole");
		helper.assertFalse(passing.isOverdrawn(), "the passing run's J-pole is shared by two radios");
		helper.assertFalse(ending.isOverdrawn(), "the ending run's J-pole is shared by two radios");
	}

	/** Red laid first past the black radio, black laid in with it: the red strand lets go of the radio. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100)
	public static void redCoaxBundledPastTheBlackRadioStaysApart(GameTestHelper helper) {
		runPastTheOtherRadio(helper, RED, BLACK, true);
	}

	/** The plain (undyed) run ends at its radio first, red laid in after it: red never takes that radio. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100)
	public static void redCoaxBundledPastThePlainRadioStaysApart(GameTestHelper helper) {
		runPastTheOtherRadio(helper, RED, COAX, false);
	}

	/**
	 * Red and black coax crossing through one passthrough (2026-11-11, the maintainer's COAX SHARED): each colour goes
	 * on through it to its own colour only, so each radio keeps its own one J-pole, placed in either order and after a
	 * reload.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100)
	public static void twoColoursThroughOnePassthroughStayApart(GameTestHelper helper) {
		BlockPos wall = new BlockPos(3, 2, 3);
		BlockPos radioRed = new BlockPos(1, 2, 3), antennaRed = new BlockPos(5, 2, 3);
		BlockPos radioBlack = new BlockPos(3, 2, 1), antennaBlack = new BlockPos(3, 2, 5);
		helper.setBlock(radioRed, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(radioBlack, RadiocraftBlocks.VHF_BASE_STATION.get());
		jPole(helper, antennaRed);
		jPole(helper, antennaBlack);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					GameTestUtil.placeWire(helper, radioRed.east(), RED);
					GameTestUtil.placeWire(helper, radioBlack.south(), BLACK);
					helper.setBlock(wall, RadiocraftBlocks.PASSTHROUGH.get().getBlockState(helper.getLevel(), helper.absolutePos(wall)));
					GameTestUtil.placeWire(helper, wall.east(), RED);
					GameTestUtil.placeWire(helper, wall.south(), BLACK);
				})
				.thenIdle(2)
				.thenExecute(() -> assertOwnAntennas(helper, radioRed, radioBlack, antennaRed, antennaBlack))
				.thenExecute(() -> { // The passthrough placed last, with both colours already round it.
					helper.setBlock(wall, net.minecraft.world.level.block.Blocks.AIR);
				})
				.thenIdle(2)
				.thenExecute(() -> helper.setBlock(wall, RadiocraftBlocks.PASSTHROUGH.get().getBlockState(helper.getLevel(), helper.absolutePos(wall))))
				.thenIdle(2)
				.thenExecute(() -> assertOwnAntennas(helper, radioRed, radioBlack, antennaRed, antennaBlack))
				.thenExecute(() -> {
					WireUtils.connectToWires(helper.getLevel(), helper.absolutePos(radioBlack)); // As after a reload.
					WireUtils.connectToWires(helper.getLevel(), helper.absolutePos(radioRed));
				})
				.thenIdle(2)
				.thenExecute(() -> assertOwnAntennas(helper, radioRed, radioBlack, antennaRed, antennaBlack))
				.thenSucceed();
	}

	/**
	 * Shift-right click takes the strand under the crosshair (2026-11-13: after the cable running rework it took the
	 * wrong one): a hit on any point of a strand's drawn line picks that strand, straight runs and a corner alike.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 60)
	public static void crosshairPicksTheStrandItIsOn(GameTestHelper helper) {
		BlockPos[] run = {new BlockPos(1, 2, 2), new BlockPos(2, 2, 2), new BlockPos(3, 2, 2), new BlockPos(3, 2, 3)};
		for(BlockPos at : run)
			bundle(helper, at, WIRE, RadiocraftBlocks.DATA_WIRE.get(), COAX, RED);
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					for(BlockPos at : run) {
						BlockPos abs = helper.absolutePos(at);
						for(com.arrl.radiocraft.common.blocks.WireGeometry.Segment segment : com.arrl.radiocraft.common.blocks.WireGeometry.at(helper.getLevel(), abs)) {
							for(double t : new double[] {0.2D, 0.5D, 0.8D}) {
								net.minecraft.world.phys.Vec3 point = segment.from().add(segment.to().subtract(segment.from()).scale(t));
								net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(point, net.minecraft.core.Direction.UP, abs, false);
								WireBlock picked = WireBundleBlock.strandAt(helper.getLevel(), abs, hit);
								helper.assertTrue(picked == segment.kind(), "At " + at.toShortString() + " a hit on the " + segment.kind() + " line picked " + picked);
							}
						}
					}
				})
				.thenSucceed();
	}
}
