package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.adsb.AdsbBeaconBlockEntity;
import com.arrl.radiocraft.common.blockentities.adsb.AdsbReceiverBlockEntity;
import com.arrl.radiocraft.common.blocks.adsb.AdsbBeaconBlock;
import com.arrl.radiocraft.common.blocks.adsb.AdsbReceiverBlock;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.adsb.AdsbAirspace;
import com.arrl.radiocraft.common.radio.adsb.AdsbMotion;
import com.arrl.radiocraft.common.radio.adsb.AdsbPicture;
import com.arrl.radiocraft.common.radio.adsb.AdsbTarget;
import com.arrl.radiocraft.common.radio.adsb.AdsbTracks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * ADS-B (1.12 step 9): a receiver hears a beacon through an antenna on top of it or on its coax (and nothing without
 * one), with the flight ID and squawk the beacon was set to; a beacon switched off or broken leaves the airspace; the
 * range cuts off a far beacon; the radio computer's ADS-B program reads a receiver cabled to it. GameTests run
 * side by side, so every check picks its own beacon by its ICAO address (receivers here hear the other plots' beacons
 * too: low down the range is still adsb_min_range). Power draw is off on the GameTest server.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class AdsbGameTests {

	private static final String RANGE_BATCH = "adsb_range";
	private static int savedMinRange = 200, savedMaxRange = 0;

	@BeforeBatch(batch = RANGE_BATCH)
	public static void shortRange(ServerLevel level) {
		savedMinRange = RadiocraftServerConfig.ADSB_MIN_RANGE.get();
		savedMaxRange = RadiocraftServerConfig.ADSB_MAX_RANGE.get();
		RadiocraftServerConfig.ADSB_MIN_RANGE.set(2); // A 2 block range at any height.
		RadiocraftServerConfig.ADSB_MAX_RANGE.set(2);
	}

	@AfterBatch(batch = RANGE_BATCH)
	public static void normalRange(ServerLevel level) {
		RadiocraftServerConfig.ADSB_MIN_RANGE.set(savedMinRange);
		RadiocraftServerConfig.ADSB_MAX_RANGE.set(savedMaxRange);
	}

	private static AdsbBeaconBlockEntity beacon(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, RadiocraftBlocks.ADSB_BEACON.get());
		return GameTestUtil.blockEntity(helper, pos, AdsbBeaconBlockEntity.class);
	}

	private static AdsbReceiverBlockEntity receiver(GameTestHelper helper, BlockPos pos, boolean antennaOnTop) {
		helper.setBlock(pos, RadiocraftBlocks.ADSB_RECEIVER.get());
		if(antennaOnTop)
			helper.setBlock(pos.above(), RadiocraftBlocks.ADSB_ANTENNA.get());
		return GameTestUtil.blockEntity(helper, pos, AdsbReceiverBlockEntity.class);
	}

	private static void switchOn(AdsbBeaconBlockEntity beacon) {
		if(!beacon.isSwitchedOn())
			beacon.togglePower();
	}

	private static void switchOn(AdsbReceiverBlockEntity receiver) {
		if(!receiver.isSwitchedOn())
			receiver.togglePower();
	}

	private static boolean hears(AdsbReceiverBlockEntity receiver, AdsbBeaconBlockEntity beacon) {
		return receiver.getTracks().get(beacon.getIcao()) != null;
	}

	/** The beacon's report in the airspace, or null. */
	private static AdsbTarget onTheAir(GameTestHelper helper, AdsbBeaconBlockEntity beacon) {
		long key = beacon.getBlockPos().asLong();
		for(AdsbTarget report : AdsbAirspace.reports(helper.getLevel()))
			if(report.beacon() == key && AdsbAirspace.isFresh(report, helper.getLevel().getGameTime()))
				return report;
		return null;
	}

	/** A beacon six blocks from a receiver with the antenna on top: heard, with its flight ID and emergency squawk. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void receiverHearsABeacon(GameTestHelper helper) {
		BlockPos beaconPos = new BlockPos(3, 2, 3), receiverPos = new BlockPos(9, 2, 3);
		AdsbBeaconBlockEntity beacon = beacon(helper, beaconPos);
		AdsbReceiverBlockEntity receiver = receiver(helper, receiverPos, true);
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					beacon.setCallsign("gt 1");
					helper.assertTrue(beacon.setSquawk(7700), "7700 refused");
					helper.assertFalse(beacon.setSquawk(1280), "1280 taken: 8 is not a squawk digit");
					switchOn(beacon);
					switchOn(receiver);
				})
				.thenWaitUntil(() -> helper.assertTrue(hears(receiver, beacon), "The receiver did not hear the beacon (antenna " + receiver.hasAntenna() + ")"))
				.thenExecute(() -> {
					AdsbTracks.Track track = receiver.getTracks().get(beacon.getIcao());
					helper.assertTrue(track.last().callsign().equals("GT1"), "flight ID " + track.last().callsign());
					helper.assertTrue(track.last().squawk() == 7700, "squawk " + track.last().squawk());
					helper.assertTrue(track.last().groundSpeed() < AdsbMotion.TELEPORT, "speed " + track.last().groundSpeed());
					helper.assertBlockProperty(beaconPos, AdsbBeaconBlock.LIT, true);
					helper.assertBlockProperty(receiverPos, AdsbReceiverBlock.LIT, true);
					List<AdsbPicture.Contact> contacts = AdsbPicture.contacts(receiver.picture());
					AdsbPicture.Contact contact = contacts.stream().filter(c -> c.icao() == beacon.getIcao()).findFirst().orElse(null);
					helper.assertTrue(contact != null, "not in the picture sent to the screen");
					helper.assertTrue(Math.abs(contact.dx() + 6.0F) < 0.01F && Math.abs(contact.dz()) < 0.01F, "placed at " + contact.dx() + ", " + contact.dz());
					helper.assertTrue(contact.emergency(), "7700 is not shown as an emergency");
				})
				.thenSucceed();
	}

	/** No antenna, no traffic; an antenna set on top brings the beacon in. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 260)
	public static void noAntennaHearsNothing(GameTestHelper helper) {
		BlockPos receiverPos = new BlockPos(9, 2, 9);
		AdsbBeaconBlockEntity beacon = beacon(helper, new BlockPos(3, 2, 9));
		AdsbReceiverBlockEntity receiver = receiver(helper, receiverPos, false);
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					switchOn(beacon);
					switchOn(receiver);
				})
				.thenIdle(60)
				.thenExecute(() -> {
					helper.assertFalse(receiver.hasAntenna(), "found an antenna that is not there");
					helper.assertTrue(receiver.getTracks().isEmpty(), "heard traffic without an antenna");
					helper.assertTrue(onTheAir(helper, beacon) != null, "the beacon is not on the air");
					helper.setBlock(receiverPos.above(), RadiocraftBlocks.ADSB_ANTENNA.get());
				})
				.thenWaitUntil(() -> helper.assertTrue(hears(receiver, beacon), "still deaf with the antenna on top"))
				.thenSucceed();
	}

	/** The antenna three blocks of coax away works as well as one on top. Needs the coax tags (runData). */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void antennaOnCoax(GameTestHelper helper) {
		BlockPos receiverPos = new BlockPos(3, 2, 12);
		AdsbReceiverBlockEntity receiver = receiver(helper, receiverPos, false);
		for(int x = 4; x <= 6; x++)
			GameTestUtil.placeWire(helper, new BlockPos(x, 2, 12), RadiocraftBlocks.COAX_WIRE.get());
		helper.setBlock(new BlockPos(7, 2, 12), RadiocraftBlocks.ADSB_ANTENNA.get());
		AdsbBeaconBlockEntity beacon = beacon(helper, new BlockPos(12, 2, 6));
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					switchOn(beacon);
					switchOn(receiver);
				})
				.thenWaitUntil(() -> helper.assertTrue(receiver.hasAntenna() && hears(receiver, beacon), "antenna on coax: " + receiver.hasAntenna()))
				.thenSucceed();
	}

	/** Switched off, a beacon leaves the airspace at once; switched on again and broken, too. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void silentBeaconsLeaveTheAir(GameTestHelper helper) {
		BlockPos pos = new BlockPos(13, 2, 13);
		AdsbBeaconBlockEntity beacon = beacon(helper, pos);
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> switchOn(beacon))
				.thenWaitUntil(() -> helper.assertTrue(onTheAir(helper, beacon) != null, "never on the air"))
				.thenExecute(beacon::togglePower)
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertTrue(onTheAir(helper, beacon) == null, "still on the air switched off");
					helper.assertBlockProperty(pos, AdsbBeaconBlock.LIT, false);
					beacon.togglePower();
				})
				.thenWaitUntil(() -> helper.assertTrue(onTheAir(helper, beacon) != null, "not back on the air"))
				.thenExecute(() -> {
					long icao = beacon.getIcao();
					helper.setBlock(pos, Blocks.AIR);
					for(AdsbTarget report : AdsbAirspace.reports(helper.getLevel()))
						helper.assertFalse(report.icao() == icao, "a broken beacon is still on the air");
				})
				.thenSucceed();
	}

	/** With the range cut to two blocks, the beacon beside the antenna is heard and the one seven blocks off is not. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = RANGE_BATCH)
	public static void outOfRangeIsNotHeard(GameTestHelper helper) {
		AdsbReceiverBlockEntity receiver = receiver(helper, new BlockPos(5, 2, 5), true);
		AdsbBeaconBlockEntity near = beacon(helper, new BlockPos(5, 3, 6)); // Beside the antenna.
		AdsbBeaconBlockEntity far = beacon(helper, new BlockPos(5, 3, 12));
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					switchOn(near);
					switchOn(far);
					switchOn(receiver);
				})
				.thenWaitUntil(() -> helper.assertTrue(hears(receiver, near), "the beacon beside the antenna was not heard"))
				.thenIdle(45) // Both have reported at least twice by now.
				.thenExecute(() -> {
					helper.assertTrue(onTheAir(helper, far) != null, "the far beacon is not on the air");
					helper.assertFalse(hears(receiver, far), "heard a beacon out of range");
				})
				.thenSucceed();
	}

	/** The computer's ADS-B program sees a receiver touching it, switches it on and lists the beacon. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void computerReadsTheReceiver(GameTestHelper helper) {
		BlockPos computerPos = new BlockPos(3, 2, 6);
		helper.setBlock(computerPos, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(computerPos.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, computerPos, RadioComputerBlockEntity.class);
		computer.install(ComputerApps.ADSB);
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("adsb-computer".getBytes()), "adsb"));
		AdsbBeaconBlockEntity beacon = beacon(helper, new BlockPos(10, 2, 6));
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					helper.assertTrue(app(computer, player, "status").getCompound("data").getBoolean("receiver") == false, "a receiver with none cabled");
					helper.assertTrue(app(computer, player, "power").getString("error").equals("adsb_no_receiver"), "switched on a receiver that is not there");
					receiver(helper, computerPos.east(), true);
					switchOn(beacon);
				})
				.thenIdle(3)
				.thenExecute(() -> {
					CompoundTag status = app(computer, player, "status").getCompound("data");
					helper.assertTrue(status.getBoolean("receiver") && !status.getBoolean("on"), "status: " + status);
					helper.assertFalse(app(computer, player, "power").contains("error"), "could not switch the receiver on");
				})
				.thenWaitUntil(() -> {
					CompoundTag status = app(computer, player, "status").getCompound("data");
					helper.assertTrue(AdsbPicture.contacts(status).stream().anyMatch(c -> c.icao() == beacon.getIcao()), "the beacon is not listed: " + status);
				})
				.thenSucceed();
	}

	/** One program request, as the screen sends it; the reply ({@code data}, maybe {@code error}). */
	private static CompoundTag app(RadioComputerBlockEntity computer, FakePlayer player, String action) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", ComputerApps.ADSB.id());
		wrapped.putString("action", action);
		wrapped.put("data", new CompoundTag());
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, "app", wrapped, new ComputerReply(player, 9, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}
}
