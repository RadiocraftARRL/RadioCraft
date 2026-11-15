package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.benetworks.BENetwork;
import com.arrl.radiocraft.api.benetworks.PowerBENetwork;
import com.arrl.radiocraft.common.be_networks.network_objects.AntennaNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.BatteryNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.AntennaSwitchBlockEntity;
import com.arrl.radiocraft.common.blocks.WallMount;
import com.arrl.radiocraft.common.blocks.antennas.AntennaSwitchBlock;
import com.arrl.radiocraft.common.blocks.antennas.DoubleVHFAntennaBlock;
import com.arrl.radiocraft.common.blocks.power.LeftRightPowerSwitchBlock;
import com.arrl.radiocraft.common.blocks.power.OnOffPowerSwitchBlock;
import com.arrl.radiocraft.common.blocks.power.PowerSwitchBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The antenna switch and the power switches on a wall (2026-11-13, {@link WallMount}): tipped up against it, the
 * terminal side at the bottom, where a cable comes up the wall; the others left, top and right as you face it.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class WallSwitchGameTests {

	private static final BlockPos SWITCH = new BlockPos(5, 3, 5);

	private static BlockState onWall(BlockState state, Direction out) {
		return state.setValue(WallMount.FACE, AttachFace.WALL).setValue(WallMount.FACING, out);
	}

	private static boolean wiredTo(GameTestHelper helper, BlockPos radio, BlockPos battery) {
		BatteryNetworkObject cell = GameTestUtil.networkObject(helper, battery, BatteryNetworkObject.class);
		for(BENetwork network : GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class).getNetworks().values())
			if(network instanceof PowerBENetwork && network.getNetworkObjects().contains(cell))
				return true;
		return false;
	}

	private static void wire(GameTestHelper helper, BlockPos... positions) {
		for(BlockPos pos : positions)
			GameTestUtil.placeWire(helper, pos, RadiocraftBlocks.WIRE.get());
	}

	/** The side mapping, without a world: on a north-facing wall (wall to the south) and an east-facing one. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void wallSidesMapAsTheModelTurns(GameTestHelper helper) {
		BlockState north = onWall(RadiocraftBlocks.POWER_SWITCH_LEFT_RIGHT.get().defaultBlockState(), Direction.NORTH);
		helper.assertTrue(WallMount.toWorld(north, Direction.NORTH) == Direction.DOWN && WallMount.toWorld(north, Direction.SOUTH) == Direction.UP,
				"The box's north and south are not down and up");
		helper.assertTrue(LeftRightPowerSwitchBlock.left(north) == Direction.EAST && LeftRightPowerSwitchBlock.right(north) == Direction.WEST,
				"Facing the wall (south), left is not east");
		BlockState east = onWall(north, Direction.EAST);
		helper.assertTrue(LeftRightPowerSwitchBlock.left(east) == Direction.SOUTH && LeftRightPowerSwitchBlock.right(east) == Direction.NORTH,
				"Facing the wall (west), left is not south: " + LeftRightPowerSwitchBlock.left(east));
		for(Direction out : Direction.Plane.HORIZONTAL) {
			BlockState state = onWall(north, out);
			for(Direction side : Direction.values())
				helper.assertTrue(WallMount.toFrame(state, WallMount.toWorld(state, side)) == side, "toFrame does not undo toWorld for " + side + " on " + out);
		}
		BlockState antenna = onWall(RadiocraftBlocks.ANTENNA_SWITCH.get().defaultBlockState(), Direction.NORTH);
		helper.assertTrue(AntennaSwitchBlock.input(antenna) == Direction.DOWN, "The antenna switch's input is not at the bottom");
		helper.assertTrue(AntennaSwitchBlock.output(antenna.setValue(AntennaSwitchBlock.OUTPUT, 1)) == Direction.EAST
				&& AntennaSwitchBlock.output(antenna.setValue(AntennaSwitchBlock.OUTPUT, 2)) == Direction.UP
				&& AntennaSwitchBlock.output(antenna.setValue(AntennaSwitchBlock.OUTPUT, 3)) == Direction.WEST, "Outputs are not left, top, right");
		helper.succeed();
	}

	/** An On/Off switch on a wall: the radio's wire comes up to its bottom, the battery's down to its top. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100)
	public static void onOffSwitchOnAWall(GameTestHelper helper) {
		BlockPos radio = new BlockPos(5, 2, 3), battery = new BlockPos(5, 4, 4);
		helper.setBlock(SWITCH.south(), Blocks.STONE);
		helper.setBlock(SWITCH, onWall(RadiocraftBlocks.POWER_SWITCH_ON_OFF.get().defaultBlockState(), Direction.NORTH).setValue(OnOffPowerSwitchBlock.ON, true));
		helper.setBlock(radio, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(battery, RadiocraftBlocks.LARGE_BATTERY.get());
		wire(helper, new BlockPos(5, 2, 4), SWITCH.below(), SWITCH.above());
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(wiredTo(helper, radio, battery), "On, the radio is not on the battery's circuit through the wall switch"))
				.thenExecute(() -> {
					PowerSwitchBlock.toggle(helper.getLevel(), helper.absolutePos(SWITCH));
					helper.assertFalse(wiredTo(helper, radio, battery), "Off, the radio is still on the battery's circuit");
					PowerSwitchBlock.toggle(helper.getLevel(), helper.absolutePos(SWITCH));
					helper.assertTrue(wiredTo(helper, radio, battery), "On again, the radio is not back on the battery's circuit");
				})
				.thenSucceed();
	}

	/** A Left/Right switch on an east-facing wall: input at the bottom, left output south, right north. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100)
	public static void leftRightSwitchOnAWall(GameTestHelper helper) {
		BlockPos radio = new BlockPos(7, 2, 5), left = new BlockPos(5, 3, 7), right = new BlockPos(5, 3, 3);
		helper.setBlock(SWITCH.west(), Blocks.STONE);
		helper.setBlock(SWITCH, onWall(RadiocraftBlocks.POWER_SWITCH_LEFT_RIGHT.get().defaultBlockState(), Direction.EAST));
		helper.setBlock(radio, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(left, RadiocraftBlocks.LARGE_BATTERY.get());
		helper.setBlock(right, RadiocraftBlocks.LARGE_BATTERY.get());
		wire(helper, SWITCH.below(), new BlockPos(6, 2, 5), SWITCH.south(), SWITCH.north());
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(wiredTo(helper, radio, left), "On the left, the radio is not on the left (south) battery's circuit"))
				.thenExecute(() -> {
					helper.assertFalse(wiredTo(helper, radio, right), "On the left, the radio is on the right battery's circuit too");
					PowerSwitchBlock.toggle(helper.getLevel(), helper.absolutePos(SWITCH));
					helper.assertTrue(wiredTo(helper, radio, right), "On the right, the radio is not on the right (north) battery's circuit");
					helper.assertFalse(wiredTo(helper, radio, left), "On the right, the radio is still on the left battery's circuit");
				})
				.thenSucceed();
	}

	private static void jPole(GameTestHelper helper, BlockPos lower) {
		helper.setBlock(lower, RadiocraftBlocks.J_POLE_ANTENNA.get().defaultBlockState().setValue(DoubleVHFAntennaBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(lower.above(), RadiocraftBlocks.J_POLE_ANTENNA.get().defaultBlockState().setValue(DoubleVHFAntennaBlock.HALF, DoubleBlockHalf.UPPER));
	}

	private static void assertOnlyAntenna(GameTestHelper helper, BlockPos radio, BlockPos pole) {
		List<AntennaNetworkObject> antennas = GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class).getAntennas();
		helper.assertTrue(antennas.size() == 1 && antennas.getFirst().getPos().equals(helper.absolutePos(pole)),
				"The radio should have only the antenna at " + pole.toShortString() + ", has " + antennas.stream().map(a -> helper.relativePos(a.getPos()).toShortString()).toList());
	}

	/**
	 * An antenna switch on a wall (wall to the south): the radio's coax up the wall to its bottom, a J-pole on output 1
	 * (east, left as you face the wall) and one on output 2 (the top). It stays put: no turning toward the radio.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 160)
	public static void antennaSwitchOnAWall(GameTestHelper helper) {
		BlockPos radio = new BlockPos(5, 2, 3), eastPole = new BlockPos(7, 3, 5), topPole = new BlockPos(5, 4, 4); // Beside the coax above the switch.
		helper.setBlock(SWITCH.south(), Blocks.STONE);
		helper.setBlock(SWITCH, onWall(RadiocraftBlocks.ANTENNA_SWITCH.get().defaultBlockState(), Direction.NORTH));
		jPole(helper, eastPole);
		jPole(helper, topPole);
		GameTestUtil.placeWire(helper, SWITCH.east(), RadiocraftBlocks.COAX_WIRE.get());
		GameTestUtil.placeWire(helper, SWITCH.above(), RadiocraftBlocks.COAX_WIRE.get());
		helper.setBlock(radio, RadiocraftBlocks.VHF_BASE_STATION.get());
		GameTestUtil.placeWire(helper, new BlockPos(5, 2, 4), RadiocraftBlocks.COAX_WIRE.get());
		GameTestUtil.placeWire(helper, SWITCH.below(), RadiocraftBlocks.COAX_WIRE.get());
		helper.startSequence()
				.thenWaitUntil(() -> assertOnlyAntenna(helper, radio, eastPole))
				.thenIdle(AntennaSwitchBlockEntity.CHECK_TICKS + 2)
				.thenExecute(() -> {
					BlockState state = helper.getBlockState(SWITCH);
					helper.assertTrue(WallMount.onWall(state) && state.getValue(WallMount.FACING) == Direction.NORTH, "The wall switch turned: " + state);
					helper.setBlock(SWITCH, state.setValue(AntennaSwitchBlock.OUTPUT, 2));
					AntennaSwitchBlockEntity.rewire(helper.getLevel(), helper.absolutePos(SWITCH));
					assertOnlyAntenna(helper, radio, topPole);
					helper.setBlock(SWITCH, helper.getBlockState(SWITCH).setValue(AntennaSwitchBlock.OUTPUT, 3)); // West: nothing there.
					AntennaSwitchBlockEntity.rewire(helper.getLevel(), helper.absolutePos(SWITCH));
					helper.assertTrue(GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class).getAntennas().isEmpty(), "Output 3 has no antenna");
				})
				.thenSucceed();
	}

	/**
	 * The maintainer's build (2026-11-13), laid as a player lays it: an antenna switch on a wall {@code height} blocks
	 * up, a VHF radio's coax along the floor and up the wall into its bottom, a J-pole each side fed from its left and
	 * right outputs (coax out sideways, then down the wall to the J-pole's foot). Turning the knob gives the radio the left
	 * antenna, then nothing on the top, then the right; the coax up the wall comes in at the bottom connector, against
	 * the wall in the middle, not out in the block with a stub to its middle.
	 */
	private static void playerWiredWallSwitch(GameTestHelper helper, int height) {
		int floor = 2, top = floor + height;
		BlockPos sw = new BlockPos(5, top, 5), radio = new BlockPos(5, floor, 2), east = new BlockPos(8, floor, 5), west = new BlockPos(2, floor, 5);
		// (Coax beside the J-pole's foot at x 7 and 3 feeds it.)
		for(int x = 1; x <= 9; x++)
			for(int y = floor; y <= top + 2; y++)
				helper.setBlock(new BlockPos(x, y, 6), Blocks.STONE); // The wall, to the south.
		helper.setBlock(radio, RadiocraftBlocks.VHF_BASE_STATION.get());
		jPole(helper, east);
		jPole(helper, west);
		helper.setBlock(sw, onWall(RadiocraftBlocks.ANTENNA_SWITCH.get().defaultBlockState(), Direction.NORTH));
		// The radio's coax: along the floor to under the switch, then up the wall.
		GameTestUtil.placeWire(helper, new BlockPos(5, floor, 3), RadiocraftBlocks.COAX_WIRE.get());
		GameTestUtil.placeWire(helper, new BlockPos(5, floor, 4), RadiocraftBlocks.COAX_WIRE.get());
		for(int y = floor; y < top; y++)
			GameTestUtil.placeWire(helper, new BlockPos(5, y, 5), RadiocraftBlocks.COAX_WIRE.get());
		// Each output: out sideways two blocks (coax beside the run up would join it, past the switch), down the wall
		// there to the J-pole beside it.
		for(int side : new int[] {1, -1}) {
			GameTestUtil.placeWire(helper, new BlockPos(5 + side, top, 5), RadiocraftBlocks.COAX_WIRE.get());
			for(int y = top; y >= floor; y--)
				GameTestUtil.placeWire(helper, new BlockPos(5 + 2 * side, y, 5), RadiocraftBlocks.COAX_WIRE.get());
		}
		helper.startSequence()
				.thenWaitUntil(() -> assertOnlyAntenna(helper, radio, east)) // Output 1: east, left as you face the wall.
				.thenExecute(() -> {
					// The coax up the wall meets the bottom connector: against the wall (z 14..16 px), in the middle.
					BlockPos under = helper.absolutePos(sw.below());
					double bestY = -Double.MAX_VALUE, atX = 0, atZ = 0; // Test worlds sit below y 0.
					for(com.arrl.radiocraft.common.blocks.WireGeometry.Segment segment : com.arrl.radiocraft.common.blocks.WireGeometry.at(helper.getLevel(), under))
						for(net.minecraft.world.phys.Vec3 end : new net.minecraft.world.phys.Vec3[] {segment.from(), segment.to()})
							if(end.y > bestY) {
								bestY = end.y;
								atX = end.x - under.getX();
								atZ = end.z - under.getZ();
							}
					helper.assertTrue(bestY >= under.getY() + 0.98D && bestY <= under.getY() + 1.15D, "The run up the wall does not reach the switch's bottom: top at " + (bestY - under.getY()));
					helper.assertTrue(Math.abs(atX - 0.5D) <= 0.1D && atZ >= 13.0D / 16.0D, "The run up the wall meets the switch at x " + atX + ", z " + atZ + ", not its bottom connector (0.5, 0.94)");
					AntennaSwitchBlock.turnTo(helper.getLevel(), helper.absolutePos(sw), helper.getBlockState(sw), 2);
					helper.assertTrue(GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class).getAntennas().isEmpty(), "Output 2 (the top) has no antenna");
					AntennaSwitchBlock.turnTo(helper.getLevel(), helper.absolutePos(sw), helper.getBlockState(sw), 3);
					assertOnlyAntenna(helper, radio, west);
				})
				.thenSucceed();
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void playerWiredWallSwitchOneUp(GameTestHelper helper) {
		playerWiredWallSwitch(helper, 1);
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void playerWiredWallSwitchTwoUp(GameTestHelper helper) {
		playerWiredWallSwitch(helper, 2);
	}
}
