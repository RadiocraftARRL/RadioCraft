package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.mesh.FireAlarmBoxBlockEntity;
import com.arrl.radiocraft.common.blockentities.mesh.PullStationBlockEntity;
import com.arrl.radiocraft.common.blockentities.mesh.FireAlarmControllerBlockEntity;
import com.arrl.radiocraft.common.blockentities.mesh.ScadaMasterBlockEntity;
import com.arrl.radiocraft.common.blockentities.mesh.ScadaRtuBlockEntity;
import com.arrl.radiocraft.common.blocks.mesh.PullStationBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicense;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenses;
import com.arrl.radiocraft.common.radio.firealarm.AlarmEvent;
import com.arrl.radiocraft.common.radio.firealarm.ControllerState;
import com.arrl.radiocraft.common.radio.firealarm.FireAlarmMessage;
import com.arrl.radiocraft.common.radio.firealarm.FireAlarms;
import com.arrl.radiocraft.common.radio.mesh.MeshNode;
import com.arrl.radiocraft.common.radio.mesh.MeshRole;
import com.arrl.radiocraft.common.radio.mesh.RadioMesh;
import com.arrl.radiocraft.common.radio.scada.Scada;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The radio mesh (2026-11-10): a pulled station reaching the controller and its ack coming back, relaying through boxes
 * whose chunks are not loaded (devices that are only in the mesh's memory), TAMPER from a broken box, and SCADA: an RTU's
 * input driving the master's output and the RTU's own, and COMM FAIL for an RTU out of reach. Each test has its own
 * license, so its own frequency.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class RadioMeshGameTests {

	private static CommercialLicense license(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		return CommercialLicenses.of(server).issue(server, Band.VHF_COMMERCIAL, "tester", "", 1, new Random(helper.getLevel().random.nextLong()));
	}

	private static GlobalPos global(GameTestHelper helper, BlockPos relative) {
		return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relative));
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void pulledStationReachesTheController(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommercialLicense license = license(helper);
		BlockPos controllerAt = new BlockPos(2, 1, 2), boxAt = new BlockPos(8, 1, 2), stationAt = new BlockPos(9, 1, 2);
		helper.setBlock(controllerAt, RadiocraftBlocks.FIRE_ALARM_CONTROLLER.get());
		helper.setBlock(boxAt, RadiocraftBlocks.FIRE_ALARM_BOX.get());
		helper.setBlock(stationAt, RadiocraftBlocks.PULL_STATION.get().defaultBlockState().setValue(PullStationBlock.PULLED, true));
		FireAlarmControllerBlockEntity controller = GameTestUtil.blockEntity(helper, controllerAt, FireAlarmControllerBlockEntity.class);
		FireAlarmBoxBlockEntity box = GameTestUtil.blockEntity(helper, boxAt, FireAlarmBoxBlockEntity.class);
		controller.setLicense(server, license);
		controller.addPaper(10);
		box.setLicense(server, license);
		helper.succeedWhen(() -> {
			ControllerState state = FireAlarms.of(server).controllerIfAny(controller.globalPos());
			helper.assertTrue(state != null, "The controller has no state");
			AlarmEvent latest = state.latest();
			helper.assertTrue(latest != null && latest.type().equals(FireAlarmMessage.FIRE) && latest.zone() == 1 && latest.box() == box.getAddress(),
					"No FIRE for zone 1 from box " + box.getAddress() + ": " + latest);
			helper.assertTrue(state.alarm() && state.needsAttention(), "The controller is not in alarm");
			helper.assertTrue(!FireAlarms.of(server).waiting(box.globalPos()), "The box is still waiting for its ack");
			helper.assertTrue(controller.getPaper() < 10 || controller.isJammed(), "Nothing printed");
			helper.assertTrue(helper.getBlockState(controllerAt).getValue(com.arrl.radiocraft.common.blocks.mesh.FireAlarmControllerBlock.POWERED), "No bell output");
		});
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void relaysThroughUnloadedBoxes(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommercialLicense license = license(helper);
		BlockPos controllerAt = new BlockPos(2, 1, 2);
		helper.setBlock(controllerAt, RadiocraftBlocks.FIRE_ALARM_CONTROLLER.get());
		FireAlarmControllerBlockEntity controller = GameTestUtil.blockEntity(helper, controllerAt, FireAlarmControllerBlockEntity.class);
		controller.setLicense(server, license);
		// Boxes 90 blocks apart, out where nothing is loaded: built-in antennas reach about 112, so only neighbours hear each other.
		RadioMesh mesh = RadioMesh.of(server);
		List<GlobalPos> virtual = new ArrayList<>();
		for(int i = 1; i <= 3; i++) {
			GlobalPos pos = global(helper, controllerAt.east(90 * i));
			virtual.add(pos);
			mesh.put(new MeshNode(pos, MeshRole.BOX, license.outputHz(), 900 + i, false, "FAR BOX " + i, license.callsign()));
		}
		GlobalPos far = virtual.get(2);
		FireAlarms.of(server).report(server, far, FireAlarmMessage.FIRE, 5);
		helper.succeedWhen(() -> {
			ControllerState state = FireAlarms.of(server).controller(controller.globalPos());
			AlarmEvent latest = state.latest();
			helper.assertTrue(latest != null && latest.box() == 903 && latest.zone() == 5, "The far box's FIRE did not arrive: " + latest);
			helper.assertTrue(latest.hops() == 2, "It came through " + latest.hops() + " relays, not 2");
			helper.assertTrue(!FireAlarms.of(server).waiting(far), "The ack did not get back to the far box");
			for(GlobalPos pos : virtual)
				mesh.remove(pos);
		});
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void brokenBoxSendsTamper(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommercialLicense license = license(helper);
		BlockPos controllerAt = new BlockPos(2, 1, 2), boxAt = new BlockPos(8, 1, 8);
		helper.setBlock(controllerAt, RadiocraftBlocks.FIRE_ALARM_CONTROLLER.get());
		helper.setBlock(boxAt, RadiocraftBlocks.FIRE_ALARM_BOX.get());
		FireAlarmControllerBlockEntity controller = GameTestUtil.blockEntity(helper, controllerAt, FireAlarmControllerBlockEntity.class);
		FireAlarmBoxBlockEntity box = GameTestUtil.blockEntity(helper, boxAt, FireAlarmBoxBlockEntity.class);
		controller.setLicense(server, license);
		box.setLicense(server, license);
		int address = box.getAddress();
		GlobalPos boxPos = box.globalPos();
		helper.runAfterDelay(FireAlarms.FIRST_CHECK_IN_TICKS + 20, () -> {
			ControllerState.Box known = FireAlarms.of(server).controller(controller.globalPos()).box(address);
			helper.assertTrue(known != null && known.status() == ControllerState.Status.NORMAL, "The box's first check-in did not list it");
			helper.setBlock(boxAt, Blocks.AIR);
			helper.assertTrue(RadioMesh.of(server).get(boxPos) == null, "The broken box is still on the mesh");
		});
		helper.runAfterDelay(FireAlarms.FIRST_CHECK_IN_TICKS + 40, () -> helper.succeedWhen(() -> {
			ControllerState.Box known = FireAlarms.of(server).controller(controller.globalPos()).box(address);
			helper.assertTrue(known.status() == ControllerState.Status.TROUBLE, "No TAMPER: " + known.status());
		}));
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 400)
	public static void scadaInputDrivesOutputs(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommercialLicense license = license(helper);
		BlockPos rtuAt = new BlockPos(2, 1, 4), masterAt = new BlockPos(10, 1, 4);
		helper.setBlock(rtuAt.north(), Blocks.REDSTONE_BLOCK);
		helper.setBlock(rtuAt, RadiocraftBlocks.SCADA_RTU.get());
		helper.setBlock(masterAt, RadiocraftBlocks.SCADA_MASTER.get());
		ScadaRtuBlockEntity rtu = GameTestUtil.blockEntity(helper, rtuAt, ScadaRtuBlockEntity.class);
		ScadaMasterBlockEntity master = GameTestUtil.blockEntity(helper, masterAt, ScadaMasterBlockEntity.class);
		rtu.setLicense(server, license);
		master.setLicense(server, license);
		Scada scada = Scada.of(server);
		scada.rtu(rtu.globalPos()).modes[Direction.NORTH.get3DDataValue()] = Scada.FaceMode.IN;
		scada.rtu(rtu.globalPos()).modes[Direction.SOUTH.get3DDataValue()] = Scada.FaceMode.OUT;
		scada.master(master.globalPos()).modes[Direction.UP.get3DDataValue()] = Scada.FaceMode.OUT;
		int id = rtu.getAddress();
		master.setRules(server, List.of("COPY " + id + ".N TO 0.U", "IF " + id + ".N >= 15 THEN " + id + ".S = 9", "POLL 1"));
		helper.succeedWhen(() -> {
			helper.assertTrue(master.emitted(Direction.UP) == 15, "The master's top gives off " + master.emitted(Direction.UP) + ", not the RTU's 15");
			helper.assertTrue(rtu.emitted(Direction.SOUTH) == 9, "The RTU's south face gives off " + rtu.emitted(Direction.SOUTH) + ", not 9");
			helper.assertTrue(rtu.emitted(Direction.NORTH) == 0, "An IN face gives off redstone");
			helper.assertTrue(scada.master(master.globalPos()).pollSeconds == 1, "POLL 1 was not taken");
		});
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 300)
	public static void scadaCommFailOutOfReach(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommercialLicense license = license(helper);
		BlockPos masterAt = new BlockPos(4, 1, 4);
		helper.setBlock(masterAt, RadiocraftBlocks.SCADA_MASTER.get());
		ScadaMasterBlockEntity master = GameTestUtil.blockEntity(helper, masterAt, ScadaMasterBlockEntity.class);
		master.setLicense(server, license);
		GlobalPos far = global(helper, masterAt.east(3000));
		RadioMesh.of(server).put(new MeshNode(far, MeshRole.RTU, license.outputHz(), 4, false, "", license.callsign()));
		helper.succeedWhen(() -> {
			Scada.Seen seen = Scada.of(server).master(master.globalPos()).rtus.get(4);
			helper.assertTrue(seen != null && seen.commFail, "The RTU out of reach is not COMM FAIL");
			RadioMesh.of(server).remove(far);
		});
	}

	/** The box rings its bells out of its back (2026-11-11): in alarm, not when SILENCE is thrown, and for a DRILL. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void boxRingsBellsOutOfItsBack(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommercialLicense license = license(helper);
		BlockPos boxAt = new BlockPos(4, 2, 4), wall = boxAt.south(), stationAt = boxAt.east();
		helper.setBlock(wall, Blocks.STONE);
		helper.setBlock(boxAt, RadiocraftBlocks.FIRE_ALARM_BOX.get().defaultBlockState().setValue(com.arrl.radiocraft.common.blocks.mesh.FireAlarmBoxBlock.FACING, Direction.NORTH));
		helper.setBlock(stationAt, RadiocraftBlocks.PULL_STATION.get().defaultBlockState().setValue(PullStationBlock.FACING, Direction.WEST).setValue(PullStationBlock.PULLED, true));
		FireAlarmBoxBlockEntity box = GameTestUtil.blockEntity(helper, boxAt, FireAlarmBoxBlockEntity.class);
		box.setLicense(server, license);
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		BlockPos wallAbs = helper.absolutePos(wall);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(box.bellsRinging(), "Not ringing with zone 1 in alarm");
			helper.assertTrue(level.getDirectSignalTo(wallAbs) == 15, "The wall behind is not powered: " + level.getDirectSignalTo(wallAbs));
			helper.assertTrue(level.getSignal(helper.absolutePos(boxAt.north()), Direction.NORTH) == 0, "Signal out of the front");
			box.panelButton(level, com.arrl.radiocraft.common.menus.FireAlarmBoxMenu.BUTTON_SILENCE);
			helper.assertTrue(!box.bellsRinging() && level.getDirectSignalTo(wallAbs) == 0, "SILENCE did not stop the bells");
			helper.assertTrue(!box.alarmZones().isEmpty(), "SILENCE ended the alarm; it should only quiet the bells");
			helper.assertTrue(box.comparatorSignal() == 15, "A comparator stopped reading the alarm when the bells were silenced");
			box.panelButton(level, com.arrl.radiocraft.common.menus.FireAlarmBoxMenu.BUTTON_SILENCE);
			helper.assertTrue(!box.bellsRinging(), "A second SILENCE brought the bells back (it is a button, not a toggle)");
			box.panelButton(level, com.arrl.radiocraft.common.menus.FireAlarmBoxMenu.BUTTON_RESET);
		});
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(!helper.getBlockState(stationAt).getValue(PullStationBlock.PULLED), "RESET left the station pulled");
			helper.assertTrue(box.alarmZones().isEmpty(), "Still in alarm after RESET: " + box.alarmZones());
			helper.assertTrue(box.comparatorSignal() == 0, "A comparator still reads the alarm after RESET");
			box.panelButton(level, com.arrl.radiocraft.common.menus.FireAlarmBoxMenu.BUTTON_DRILL);
			helper.assertTrue(box.bellsRinging() && level.getDirectSignalTo(wallAbs) == 15, "DRILL did not ring");
			helper.assertTrue(box.alarmZones().isEmpty(), "The box's own bell signal set off its detector input");
		});
		helper.runAfterDelay(80, () -> helper.succeedIf(() -> helper.assertTrue(box.alarmZones().isEmpty(), "The drill's bells came back in as zone 0")));
	}

	/** A pressed panic button is a pull station (2026-11-11): its box sends FIRE for its zone. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void panicButtonRaisesTheAlarm(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommercialLicense license = license(helper);
		BlockPos controllerAt = new BlockPos(2, 1, 2), boxAt = new BlockPos(8, 1, 2), buttonAt = new BlockPos(8, 1, 3);
		helper.setBlock(controllerAt, RadiocraftBlocks.FIRE_ALARM_CONTROLLER.get());
		helper.setBlock(boxAt, RadiocraftBlocks.FIRE_ALARM_BOX.get());
		helper.setBlock(buttonAt, RadiocraftBlocks.PANIC_BUTTON.get().defaultBlockState()
				.setValue(com.arrl.radiocraft.common.blocks.mesh.PanicButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
				.setValue(PullStationBlock.PULLED, true));
		FireAlarmControllerBlockEntity controller = GameTestUtil.blockEntity(helper, controllerAt, FireAlarmControllerBlockEntity.class);
		FireAlarmBoxBlockEntity box = GameTestUtil.blockEntity(helper, boxAt, FireAlarmBoxBlockEntity.class);
		controller.setLicense(server, license);
		box.setLicense(server, license);
		helper.succeedWhen(() -> {
			AlarmEvent latest = FireAlarms.of(server).controller(controller.globalPos()).latest();
			helper.assertTrue(latest != null && latest.type().equals(FireAlarmMessage.FIRE) && latest.zone() == 1, "No FIRE from the panic button: " + latest);
		});
	}

	/**
	 * A station named on the box's programming panel (2026-11-11): the box numbers it, the name sticks to it, and its
	 * alarm reaches the controller with the name.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 240)
	public static void namedStationReachesTheController(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommercialLicense license = license(helper);
		BlockPos controllerAt = new BlockPos(2, 1, 2), boxAt = new BlockPos(8, 1, 2), stationAt = new BlockPos(8, 1, 3);
		helper.setBlock(controllerAt, RadiocraftBlocks.FIRE_ALARM_CONTROLLER.get());
		helper.setBlock(boxAt, RadiocraftBlocks.FIRE_ALARM_BOX.get());
		helper.setBlock(stationAt, RadiocraftBlocks.PANIC_BUTTON.get().defaultBlockState()
				.setValue(com.arrl.radiocraft.common.blocks.mesh.PanicButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
		FireAlarmControllerBlockEntity controller = GameTestUtil.blockEntity(helper, controllerAt, FireAlarmControllerBlockEntity.class);
		FireAlarmBoxBlockEntity box = GameTestUtil.blockEntity(helper, boxAt, FireAlarmBoxBlockEntity.class);
		controller.setLicense(server, license);
		box.setLicense(server, license);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(box.stationList(helper.getLevel()).size() == 1 && box.stationList(helper.getLevel()).get(0).zone() == 1,
					"The box did not number its station: " + box.stationList(helper.getLevel()));
			helper.assertTrue(box.setStation(helper.getLevel(), 1, "Front desk", com.arrl.radiocraft.common.radio.firealarm.AlarmKind.MEDICAL), "Could not set location 01");
			helper.assertTrue(!box.nameStation(helper.getLevel(), 7, "Nowhere"), "Named a station that is not there");
			helper.assertTrue(GameTestUtil.blockEntity(helper, stationAt, PullStationBlockEntity.class).getName().equals("FRONT DESK"), "The name did not stick");
			helper.setBlock(stationAt, helper.getBlockState(stationAt).setValue(PullStationBlock.PULLED, true));
		});
		helper.runAfterDelay(40, () -> helper.succeedWhen(() -> {
			AlarmEvent latest = FireAlarms.of(server).controller(controller.globalPos()).latest();
			helper.assertTrue(latest != null && latest.type().equals(FireAlarmMessage.FIRE) && latest.zoneName().equals("FRONT DESK")
					&& latest.kind() == com.arrl.radiocraft.common.radio.firealarm.AlarmKind.MEDICAL && latest.what().equals("ZONE 01 MEDICAL"),
					"No MEDICAL alarm from FRONT DESK at the controller: " + latest);
		}));
	}

	/**
	 * Data wire up a wall into a fire alarm box on that wall (2026-11-11): the box shows the cable coming up into it, and
	 * the wire keeps to the box's wall and goes straight on, with no stub turning out to the block's middle.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void dataWireRunsStraightIntoAWallBox(GameTestHelper helper) {
		BlockPos wireAt = new BlockPos(4, 1, 4), boxAt = wireAt.above();
		helper.setBlock(wireAt.south(), net.minecraft.world.level.block.Blocks.STONE);
		helper.setBlock(boxAt.south(), net.minecraft.world.level.block.Blocks.STONE);
		helper.setBlock(boxAt, RadiocraftBlocks.FIRE_ALARM_BOX.get().defaultBlockState()
				.setValue(com.arrl.radiocraft.common.blocks.mesh.FireAlarmBoxBlock.FACING, Direction.NORTH));
		GameTestUtil.placeWire(helper, wireAt, RadiocraftBlocks.DATA_WIRE.get());
		helper.runAfterDelay(2, () -> helper.succeedIf(() -> {
			net.minecraft.world.level.block.state.BlockState box = helper.getBlockState(boxAt);
			helper.assertTrue(box.getValue(com.arrl.radiocraft.common.blocks.mesh.WallWired.WIRE_DOWN), "The box does not show the wire from below: " + box);
			net.minecraft.world.level.block.state.BlockState wire = helper.getBlockState(wireAt);
			com.arrl.radiocraft.common.blocks.WireLook.Look look = ((com.arrl.radiocraft.common.blocks.WireBlock)wire.getBlock())
					.look(helper.getLevel(), helper.absolutePos(wireAt), wire);
			helper.assertTrue(look.hug() == Direction.SOUTH && look.end() == com.arrl.radiocraft.common.blocks.WireLook.End.NONE,
					"The wire does not run straight up the box's wall: " + look);
		}));
	}

	/** A fire bell rings while it has redstone power (2026-11-11). */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void fireBellRingsOnRedstone(GameTestHelper helper) {
		BlockPos bellAt = new BlockPos(4, 2, 4);
		helper.setBlock(bellAt.south(), net.minecraft.world.level.block.Blocks.STONE);
		helper.setBlock(bellAt, RadiocraftBlocks.FIRE_BELL.get().defaultBlockState());
		helper.assertTrue(!helper.getBlockState(bellAt).getValue(com.arrl.radiocraft.common.blocks.mesh.FireBellBlock.POWERED), "Rings with no power");
		helper.setBlock(bellAt.east(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(helper.getBlockState(bellAt).getValue(com.arrl.radiocraft.common.blocks.mesh.FireBellBlock.POWERED), "Not ringing with power");
			helper.setBlock(bellAt.east(), net.minecraft.world.level.block.Blocks.AIR);
		});
		helper.runAfterDelay(4, () -> helper.succeedIf(() -> helper.assertTrue(
				!helper.getBlockState(bellAt).getValue(com.arrl.radiocraft.common.blocks.mesh.FireBellBlock.POWERED), "Still ringing with the power gone")));
	}

	/** The SCADA master is two blocks (2026-11-11): the CRT on the viewer's left, gone with the panel and the other way round. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 60)
	public static void scadaMasterIsTwoBlocks(GameTestHelper helper) {
		BlockPos panelAt = new BlockPos(4, 1, 4);
		net.minecraft.world.level.block.state.BlockState panel = RadiocraftBlocks.SCADA_MASTER.get().defaultBlockState()
				.setValue(com.arrl.radiocraft.common.blocks.mesh.ScadaMasterBlock.FACING, Direction.NORTH);
		helper.setBlock(panelAt, panel);
		panel.getBlock().setPlacedBy(helper.getLevel(), helper.absolutePos(panelAt), panel, null, net.minecraft.world.item.ItemStack.EMPTY);
		BlockPos screenAt = panelAt.east(); // Facing north the viewer looks south: their left is east.
		helper.assertTrue(helper.getBlockState(screenAt).is(RadiocraftBlocks.SCADA_MASTER.get())
				&& helper.getBlockState(screenAt).getValue(com.arrl.radiocraft.common.blocks.mesh.ScadaMasterBlock.PART) == com.arrl.radiocraft.common.blocks.mesh.ScadaMasterBlock.Part.SCREEN,
				"No CRT on the left: " + helper.getBlockState(screenAt));
		helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(screenAt)) == null, "The CRT has a block entity");
		helper.destroyBlock(screenAt);
		helper.succeedWhen(() -> helper.assertBlockNotPresent(RadiocraftBlocks.SCADA_MASTER.get(), panelAt));
	}

	/** No license needed (2026-11-11): a frequency and a typed callsign set up a box and a controller that work. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void devicesSetUpWithoutALicense(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		helper.assertTrue(FireAlarmBoxBlockEntity.setupProblem(157.0E6F, "KAB1234") != null, "A marine frequency was taken");
		helper.assertTrue(FireAlarmBoxBlockEntity.setupProblem(28.4E6F, "KAB1234") != null, "A ham frequency was taken");
		helper.assertTrue(FireAlarmBoxBlockEntity.setupProblem(151.94E6F, "K!") != null, "A bad callsign was taken");
		float hz = 151.0E6F + (helper.getLevel().random.nextInt(800) * 12_500.0F) % 4.0E6F; // A frequency of its own.
		helper.assertTrue(FireAlarmBoxBlockEntity.setupProblem(hz, "KAB1234") == null, "A good setup was refused");
		BlockPos controllerAt = new BlockPos(2, 1, 2), boxAt = new BlockPos(8, 1, 2), stationAt = new BlockPos(9, 1, 2);
		helper.setBlock(controllerAt, RadiocraftBlocks.FIRE_ALARM_CONTROLLER.get());
		helper.setBlock(boxAt, RadiocraftBlocks.FIRE_ALARM_BOX.get());
		helper.setBlock(stationAt, RadiocraftBlocks.PULL_STATION.get().defaultBlockState().setValue(PullStationBlock.PULLED, true));
		FireAlarmControllerBlockEntity controller = GameTestUtil.blockEntity(helper, controllerAt, FireAlarmControllerBlockEntity.class);
		FireAlarmBoxBlockEntity box = GameTestUtil.blockEntity(helper, boxAt, FireAlarmBoxBlockEntity.class);
		controller.setFrequency(server, hz, "KAB1234");
		box.setFrequency(server, hz, "KAB1234");
		box.setLabel(server, "12 Elm St");
		helper.succeedWhen(() -> {
			AlarmEvent latest = FireAlarms.of(server).controller(controller.globalPos()).latest();
			helper.assertTrue(latest != null && latest.type().equals(FireAlarmMessage.FIRE) && latest.label().equals("12 ELM ST"), "No FIRE from the box: " + latest);
			helper.assertTrue(box.getLicense().equals("KAB1234"), "The callsign was not kept");
		});
	}
}
