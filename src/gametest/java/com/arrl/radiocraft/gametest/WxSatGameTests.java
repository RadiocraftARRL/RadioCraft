package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.CrtMonitorBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.blocks.CrtMonitorBlock;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.apps.WxSatHandler;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.satellite.Satellite;
import com.arrl.radiocraft.common.radio.satellite.SatelliteMode;
import com.arrl.radiocraft.common.radio.satellite.SatelliteRadio;
import com.arrl.radiocraft.common.radio.satellite.Satellites;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

/**
 * Weather satellite imaging (2026-11-12, {@code doc/SatellitePlan.md}, "Weather imaging"): a commercial satellite in
 * imaging mode, pinned over its plot, sends scan lines that a radio computer with WXSAT records through a radio linked
 * to it on the downlink; with no radio, or the radio on another frequency, nothing is recorded. Amateur satellites
 * refuse imaging. External CRT Monitors join into screens and split, and link to a computer with WXSAT beside them.
 * <p>
 * The satellite tests have their own batch: a satellite covers 5,000 blocks.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class WxSatGameTests {

	private static final String BATCH = "wxsat";
	private static final UUID OPERATOR = UUID.nameUUIDFromBytes("gametest-wxsat-operator".getBytes());
	/** The radio (J-pole two blocks south), the computer touching it to the east, a computer far from any radio. */
	private static final BlockPos RADIO = new BlockPos(4, 2, 4), COMPUTER = RADIO.east(), LONE = new BlockPos(11, 2, 11);

	@AfterBatch(batch = BATCH)
	public static void bringThemDown(ServerLevel level) {
		MinecraftServer server = level.getServer();
		Satellites satellites = Satellites.of(server);
		for(Satellite s : new ArrayList<>(satellites.all()))
			satellites.remove(server, s.name);
		SatelliteRadio.forgetSky();
	}

	/** A new commercial satellite in imaging mode, hanging over the plot. */
	private static Satellite imager(GameTestHelper helper, long seed) {
		MinecraftServer server = helper.getLevel().getServer();
		Satellite satellite = Satellites.of(server).launch(server, false, OPERATOR, "gametest", new Random(seed));
		helper.assertTrue(satellite != null, "no commercial frequencies left");
		helper.assertTrue(Satellites.of(server).setMode(server, satellite, SatelliteMode.IMAGING), "a commercial satellite would not image");
		BlockPos centre = helper.absolutePos(new BlockPos(8, 2, 8));
		Satellites.of(server).pin(server, satellite, centre.getX(), centre.getZ(), 2400L);
		return satellite;
	}

	private static RadioComputerBlockEntity computer(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(at.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, at, RadioComputerBlockEntity.class);
		computer.install(ComputerApps.WXSAT);
		return computer;
	}

	private static void radioOn(GameTestHelper helper, float hz) {
		RadioBlockEntity radio = GameTestUtil.blockEntity(helper, RADIO, RadioBlockEntity.class);
		radio.setFrequency(hz);
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, RADIO, RadioNetworkObject.class);
		if(!networkObject.isPowered)
			radio.toggle();
		helper.assertTrue(networkObject.isPowered, "the radio did not power on");
	}

	private static void antennaUp(GameTestHelper helper) {
		int found = GameTestUtil.networkObject(helper, RADIO, RadioNetworkObject.class).getAntennas().size();
		helper.assertTrue(found == 1, "the radio sees " + found + " antennas");
	}

	/**
	 * A pass in imaging mode, received by a station with WXSAT and a radio touching the computer on the downlink:
	 * lines come in clean (it is overhead), and the mosaic gets the pass and its tiles round the station.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 300, batch = BATCH)
	public static void imagingPassIsRecorded(GameTestHelper helper) {
		GameTestUtil.radioWithJPole(helper, RADIO);
		RadioComputerBlockEntity computer = computer(helper, COMPUTER);
		Satellite satellite = imager(helper, 4401L);
		helper.startSequence()
				.thenWaitUntil(() -> antennaUp(helper))
				.thenExecute(() -> radioOn(helper, satellite.downlinkHz))
				.thenWaitUntil(() -> helper.assertTrue(WxSatHandler.state(computer).received() >= 20,
						"lines recorded: " + WxSatHandler.state(computer).lineCount() + " (computer on " + computer.isPowered() + ", imaging " + SatelliteRadio.imaging(satellite) + ")"))
				.thenExecute(() -> {
					WxSatHandler.State state = WxSatHandler.state(computer);
					helper.assertTrue(state.satellite().equals(satellite.name), "from " + state.satellite());
					helper.assertTrue(state.quality() >= 1.0D, "overhead, the signal is strong: " + state.quality());
					helper.assertTrue(WxSatHandler.store(computer).mosaic().passCount() == 1, "one pass in the mosaic");
					helper.assertTrue(WxSatHandler.store(computer).mosaic().tileCount() > 0, "the pass is stitched into tiles");
					// SATTRACK-style status for the program's screen.
					FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(OPERATOR, "gametest"));
					BlockPos stand = helper.absolutePos(COMPUTER.south(2));
					player.moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
					CompoundTag status = app(computer, player, "status", new CompoundTag()).getCompound("data");
					helper.assertTrue(status.getBoolean("radio") && status.getInt("lines") >= 20 && status.getList("imagers", 10).size() >= 1, "status: " + status);
					// Held still over the plot's middle, it scans the same line over and over: the mosaic has that line,
					// a whole swath (AptSwath.SWATH_BLOCKS) long, through the point under it.
					CompoundTag view = new CompoundTag();
					BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
					view.putInt("x", at.getX());
					view.putInt("z", at.getZ());
					view.putInt("zoom", 8);
					view.putInt("frame", -1);
					view.putInt("w", 64);
					view.putInt("h", 64);
					byte[] pixels = app(computer, player, "view", view).getCompound("data").getByteArray("pixels");
					int filled = 0;
					for(byte b : pixels)
						if(b != 0)
							filled++;
					helper.assertTrue(filled >= 60, "the scan line is in the mosaic: " + filled + " pixels of " + pixels.length);
					boolean middle = false;
					for(int u = 31; u <= 32; u++)
						for(int v = 31; v <= 32; v++)
							middle |= pixels[u + v * 64] != 0;
					helper.assertTrue(middle, "the line runs through the point under the satellite");
				})
				.thenSucceed();
	}

	/**
	 * The maintainer's station (2026-11-14: "an egg-beater antenna connected during a pass of a commercial satellite set
	 * to weather satellite, with the vhf ham radio tuned out of band to its down frequency, but it still showed nothing"):
	 * an EB-VHF eggbeater, the ham VHF radio on the commercial downlink, and the computer on data wire a few blocks off.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 300, batch = BATCH)
	public static void eggbeaterAndDataWireRecord(GameTestHelper helper) {
		GameTestUtil.radioWithEggbeater(helper, RADIO);
		BlockPos far = RADIO.east(4);
		for(int x = 1; x < 4; x++)
			GameTestUtil.placeWire(helper, RADIO.east(x), RadiocraftBlocks.DATA_WIRE.get());
		RadioComputerBlockEntity computer = computer(helper, far);
		Satellite satellite = imager(helper, 4403L);
		helper.startSequence()
				.thenWaitUntil(() -> antennaUp(helper))
				.thenExecute(() -> radioOn(helper, satellite.downlinkHz))
				.thenWaitUntil(() -> helper.assertTrue(WxSatHandler.state(computer).received() >= 20,
						"lines recorded: " + WxSatHandler.state(computer).lineCount() + " (computer on " + computer.isPowered() + ", imaging " + SatelliteRadio.imaging(satellite)
								+ ", downlink " + satellite.downlinkHz + ")"))
				.thenSucceed();
	}

	/**
	 * A geostationary imager's frames (2026-11-14): WXSAT records the frame's rows as they come ("frame 1, row r of R"),
	 * marks it complete at the end-of-frame, and the next frame is a new layer over it, replacing it row by row.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 800, batch = BATCH)
	public static void geostationaryFramesAreRecorded(GameTestHelper helper) {
		GameTestUtil.radioWithEggbeater(helper, RADIO); // High in the sky: an eggbeater, as a station for it would have.
		RadioComputerBlockEntity computer = computer(helper, COMPUTER);
		MinecraftServer server = helper.getLevel().getServer();
		BlockPos centre = helper.absolutePos(new BlockPos(8, 2, 8));
		Satellite satellite = Satellites.of(server).launchGeostationary(server, false, OPERATOR, "gametest", new Random(4405L), centre.getX(), centre.getZ());
		helper.assertTrue(satellite != null, "no frequency pair left");
		helper.assertTrue(Satellites.of(server).setMode(server, satellite, SatelliteMode.IMAGING), "a geostationary commercial satellite would not image");
		helper.startSequence()
				.thenWaitUntil(() -> antennaUp(helper))
				.thenExecute(() -> radioOn(helper, satellite.downlinkHz))
				.thenWaitUntil(() -> helper.assertTrue(WxSatHandler.state(computer).received() >= 5, "rows recorded: " + WxSatHandler.state(computer).lineCount()))
				.thenExecute(() -> {
					WxSatHandler.State state = WxSatHandler.state(computer);
					helper.assertTrue(state.frame() == 0 && !state.frameDone(), "frame " + state.frame() + " done " + state.frameDone());
					helper.assertTrue(WxSatHandler.store(computer).mosaic().passCount() == 1, "one frame in the mosaic");
					SatelliteRadio.geoSkipTo(satellite, com.arrl.radiocraft.common.radio.satellite.imaging.AptSwath.PIXELS - 2);
				})
				.thenWaitUntil(() -> helper.assertTrue(WxSatHandler.state(computer).frameDone(), "the end of the frame did not come"))
				.thenWaitUntil(() -> helper.assertTrue(WxSatHandler.state(computer).frame() == 1, "the next frame did not start"))
				.thenExecute(() -> helper.assertTrue(WxSatHandler.store(computer).mosaic().passCount() == 2, "the next frame is not a new layer: "
						+ WxSatHandler.store(computer).mosaic().passCount()))
				.thenSucceed();
	}

	/** A radio on another frequency records nothing; nor does a computer with no radio, though the satellite is sending. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 300, batch = BATCH)
	public static void noRadioOrWrongFrequencyRecordsNothing(GameTestHelper helper) {
		GameTestUtil.radioWithJPole(helper, RADIO);
		RadioComputerBlockEntity computer = computer(helper, COMPUTER);
		RadioComputerBlockEntity lone = computer(helper, LONE);
		Satellite satellite = imager(helper, 4402L);
		helper.startSequence()
				.thenWaitUntil(() -> antennaUp(helper))
				.thenExecute(() -> radioOn(helper, satellite.downlinkHz + 1_000_000.0F))
				.thenWaitUntil(() -> helper.assertTrue(SatelliteRadio.imaging(satellite), "the satellite is not scanning for the station under it"))
				.thenIdle(100)
				.thenExecute(() -> {
					helper.assertTrue(computer.isPowered() && lone.isPowered(), "the computers are on (WXSAT runs in the background)");
					helper.assertTrue(WxSatHandler.state(computer).lineCount() == 0, "recorded on the wrong frequency: " + WxSatHandler.state(computer).lineCount());
					helper.assertTrue(WxSatHandler.state(lone).lineCount() == 0, "recorded with no radio: " + WxSatHandler.state(lone).lineCount());
					helper.assertTrue(WxSatHandler.store(computer).mosaic().passCount() == 0, "a pass in the mosaic");
				})
				.thenSucceed();
	}

	/** An amateur satellite refuses imaging, from SATTRACK and directly; a commercial one switches to it and back. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 60, batch = BATCH)
	public static void amateurSatellitesRefuseImaging(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Satellite amateur = Satellites.of(server).launch(server, true, OPERATOR, "gametest", new Random(4403L));
		helper.assertTrue(amateur != null, "no amateur frequencies left");
		BlockPos centre = helper.absolutePos(new BlockPos(8, 2, 8));
		Satellites.of(server).pin(server, amateur, centre.getX(), centre.getZ(), 2400L);
		RadioComputerBlockEntity computer = computer(helper, COMPUTER);
		computer.install(ComputerApps.SATTRACK);
		FakePlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(OPERATOR, "gametest"));
		BlockPos stand = helper.absolutePos(COMPUTER.south(2));
		owner.moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
		helper.startSequence()
				.thenExecute(() -> {
					helper.assertFalse(Satellites.of(server).setMode(server, amateur, SatelliteMode.IMAGING), "an amateur satellite took IMAGING");
					CompoundTag refused = sattrack(computer, owner, amateur, SatelliteMode.IMAGING);
					helper.assertTrue("sat_no_imaging".equals(refused.getString("error")), "SATTRACK let an amateur satellite image: " + refused);
					helper.assertTrue(amateur.mode() == SatelliteMode.REPEATER, "it changed anyway");
					Satellite commercial = Satellites.of(server).launch(server, false, OPERATOR, "gametest", new Random(4404L));
					helper.assertTrue(commercial != null, "no commercial frequencies left");
					Satellites.of(server).pin(server, commercial, centre.getX(), centre.getZ(), 2400L);
					CompoundTag done = sattrack(computer, owner, commercial, SatelliteMode.IMAGING);
					helper.assertFalse(done.contains("error"), "the owner could not switch it to imaging: " + done);
					helper.assertTrue(commercial.mode() == SatelliteMode.IMAGING, "the mode is " + commercial.mode());
					helper.assertTrue("sat_no_modes".equals(sattrack(computer, owner, commercial, SatelliteMode.SSTV).getString("error")), "a commercial one took SSTV");
					helper.assertFalse(sattrack(computer, owner, commercial, SatelliteMode.REPEATER).contains("error"), "could not switch back to repeater");
				})
				.thenSucceed();
	}

	private static CompoundTag sattrack(RadioComputerBlockEntity computer, FakePlayer player, Satellite satellite, SatelliteMode mode) {
		CompoundTag data = new CompoundTag();
		data.putString("name", satellite.name);
		data.putString("field", "mode");
		data.putString("mode", mode.name());
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", ComputerApps.SATTRACK.id());
		wrapped.putString("action", "set");
		wrapped.put("data", data);
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, "app", wrapped, new ComputerReply(player, 7, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	private static CompoundTag app(RadioComputerBlockEntity computer, FakePlayer player, String action, CompoundTag data) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", ComputerApps.WXSAT.id());
		wrapped.putString("action", action);
		wrapped.put("data", data);
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, "app", wrapped, new ComputerReply(player, 7, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	// -------------------- THE EXTERNAL CRT MONITOR --------------------

	private static void display(GameTestHelper helper, BlockPos at, Direction facing) {
		helper.setBlock(at, RadiocraftBlocks.EXTERNAL_CRT_MONITOR.get().defaultBlockState().setValue(CrtMonitorBlock.FACING, facing));
	}

	private static CrtMonitorBlockEntity displayAt(GameTestHelper helper, BlockPos at) {
		return GameTestUtil.blockEntity(helper, at, CrtMonitorBlockEntity.class);
	}

	/**
	 * A wall of north-facing displays three wide and two high is one screen (its master the bottom left as you look at
	 * it: the east end); one of another facing beside it is not in it. Breaking the top middle splits it into the bottom
	 * row and two single displays.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100)
	public static void displayGroupsFormAndSplit(GameTestHelper helper) {
		for(int x = 4; x <= 6; x++)
			for(int y = 2; y <= 3; y++)
				display(helper, new BlockPos(x, y, 5), Direction.NORTH);
		display(helper, new BlockPos(7, 2, 5), Direction.EAST);
		helper.startSequence()
				.thenWaitUntil(() -> {
					for(int x = 4; x <= 6; x++)
						for(int y = 2; y <= 3; y++) {
							CrtMonitorBlockEntity d = displayAt(helper, new BlockPos(x, y, 5));
							helper.assertTrue(d.groupW() == 3 && d.groupH() == 2, "at " + x + "," + y + ": " + d.groupW() + "x" + d.groupH());
							helper.assertTrue(d.master().equals(helper.absolutePos(new BlockPos(6, 2, 5))), "master " + d.master().toShortString());
							helper.assertTrue(d.cellA() == 6 - x && d.cellB() == y - 2, "cell " + d.cellA() + "," + d.cellB() + " at " + x + "," + y);
						}
					CrtMonitorBlockEntity other = displayAt(helper, new BlockPos(7, 2, 5));
					helper.assertTrue(other.groupW() == 1 && other.groupH() == 1, "the east-facing one joined");
				})
				.thenExecute(() -> helper.setBlock(new BlockPos(5, 3, 5), Blocks.AIR))
				.thenWaitUntil(() -> {
					CrtMonitorBlockEntity bottom = displayAt(helper, new BlockPos(5, 2, 5));
					helper.assertTrue(bottom.groupW() == 3 && bottom.groupH() == 1, "the bottom row: " + bottom.groupW() + "x" + bottom.groupH());
					CrtMonitorBlockEntity corner = displayAt(helper, new BlockPos(4, 3, 5));
					helper.assertTrue(corner.groupW() == 1 && corner.groupH() == 1 && corner.isMaster(), "the top corner alone: " + corner.groupW() + "x" + corner.groupH());
				})
				.thenSucceed();
	}

	/**
	 * A display beside a computer with WXSAT links to it, and so does one beside that display; with no WXSAT on the
	 * computer neither links. Right-click cycles the whole screen's mode.
	 */
	/**
	 * A display beside the computer's screen (the monitor block on top of the computer), not the computer itself: what a
	 * player sees as the PC (2026-11-14, the maintainer's display next to the PC stayed blank). It links.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 60)
	public static void displayBesideThePcsScreenLinks(GameTestHelper helper) {
		helper.setBlock(COMPUTER, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(COMPUTER.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		BlockPos beside = COMPUTER.above().west();
		display(helper, beside, Direction.SOUTH);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, COMPUTER, RadioComputerBlockEntity.class);
					computer.install(ComputerApps.WXSAT);
					helper.assertTrue(displayAt(helper, beside).findComputer() == computer, "a display beside the PC's screen did not link");
				})
				.thenSucceed();
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 60)
	public static void displayBesideTheComputerLinks(GameTestHelper helper) {
		helper.setBlock(COMPUTER, RadiocraftBlocks.RADIO_COMPUTER.get());
		BlockPos first = COMPUTER.west(), second = COMPUTER.west(2);
		display(helper, first, Direction.SOUTH);
		display(helper, second, Direction.SOUTH);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, COMPUTER, RadioComputerBlockEntity.class);
					helper.assertTrue(displayAt(helper, first).findComputer() == null, "linked to a computer without WXSAT");
					computer.install(ComputerApps.WXSAT);
					helper.assertTrue(displayAt(helper, first).findComputer() == computer, "the display beside the computer did not link");
					helper.assertTrue(displayAt(helper, second).findComputer() == computer, "the display beside a linked one did not link");
				})
				.thenWaitUntil(() -> helper.assertTrue(displayAt(helper, first).groupW() == 2, "the two did not join"))
				.thenExecute(() -> {
					CrtMonitorBlockEntity.Mode next = displayAt(helper, second).cycleMode();
					helper.assertTrue(next == CrtMonitorBlockEntity.Mode.LOOP, "LIVE MOSAIC goes to LOOP: " + next);
					helper.assertTrue(displayAt(helper, first).mode() == next, "the whole screen changes mode");
				})
				.thenSucceed();
	}
}
