package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioInterfaceBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.Schedule;
import com.arrl.radiocraft.common.computer.apps.SchedulerHandler;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * The scheduler (computer batch 3A), server side: a station ID job fires on its own when its time comes round, through
 * the radio interface's VHF radio (the CW ID goes out on the radio's frequency); jobs follow the disk's
 * {@code SCHEDULE.DAT}; the rules hold (owner-only editing, programs needed installed, the interface's transmit switch,
 * a reminder with nobody near). Its own batch: the station keys up on 146.430 MHz.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class SchedulerGameTests {

	private static final String BATCH = "scheduler";
	private static final BlockPos RADIO = new BlockPos(3, 2, 4), INTERFACE = RADIO.north(), COMPUTER = INTERFACE.west();

	private static FakePlayer player(GameTestHelper helper, String name) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("scheduler-" + name).getBytes()), name));
		BlockPos at = helper.absolutePos(COMPUTER.south(3));
		player.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
		return player;
	}

	/** A VHF base station on 146.430 MHz with a radio interface (W1SKD) north of it and the computer beside that. */
	private static RadioComputerBlockEntity station(GameTestHelper helper) {
		helper.setBlock(RADIO, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(INTERFACE, RadiocraftBlocks.RADIO_INTERFACE.get());
		GameTestUtil.blockEntity(helper, RADIO, RadioBlockEntity.class).setFrequency(146_430_000.0F);
		RadioInterfaceBlockEntity radioInterface = GameTestUtil.blockEntity(helper, INTERFACE, RadioInterfaceBlockEntity.class);
		radioInterface.setCallsign("W1SKD", "gametest");
		radioInterface.setDigipeatEnabled(false);
		helper.setBlock(COMPUTER, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(COMPUTER.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, COMPUTER, RadioComputerBlockEntity.class);
		computer.install(ComputerApps.SCHEDULER);
		return computer;
	}

	private static void powerOn(GameTestHelper helper) {
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, RADIO, RadioNetworkObject.class);
		if(!networkObject.isPowered)
			GameTestUtil.blockEntity(helper, RADIO, RadioBlockEntity.class).toggle();
		helper.assertTrue(networkObject.isPowered, "the radio did not power on");
	}

	private static CompoundTag app(RadioComputerBlockEntity computer, FakePlayer player, String action, CompoundTag data) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", ComputerApps.SCHEDULER.id());
		wrapped.putString("action", action);
		wrapped.put("data", data);
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, "app", wrapped, new ComputerReply(player, 7, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	private static CompoundTag job(Schedule.Job job) {
		CompoundTag data = new CompoundTag();
		data.put("job", job.toTag());
		return data;
	}

	private static CompoundTag id(int id) {
		CompoundTag data = new CompoundTag();
		data.putInt("id", id);
		return data;
	}

	/** A station ID every minute, saved 58.5 seconds ago: within a few seconds it sends the callsign in Morse. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 400, batch = BATCH)
	public static void stationIdRunsOnItsOwn(GameTestHelper helper) {
		RadioComputerBlockEntity computer = station(helper);
		RadioBlockEntity radio = GameTestUtil.blockEntity(helper, RADIO, RadioBlockEntity.class);
		helper.startSequence()
				.thenExecute(() -> powerOn(helper))
				.thenWaitUntil(() -> helper.assertTrue(computer.isPowered(), "the computer did not come on"))
				.thenExecute(() -> {
					long now = helper.getLevel().getGameTime();
					// Written straight to the disk, as a copy from a floppy would be: due in 30 ticks. The scheduler's
					// first look (within 20 ticks, maybe earlier this tick) only notes the count; the next one runs it.
					Schedule.Job id = new Schedule.Job(1, Schedule.Kind.STATION_ID, Schedule.When.EVERY, 1, true, "", "", "", now - 1170L);
					helper.assertTrue(computer.files().writeNbt(Schedule.FILE, Schedule.toTag(List.of(id))) == null, "could not write the schedule");
					helper.assertTrue(SchedulerHandler.jobs(computer, SchedulerHandler.state(computer)).size() == 1, "the job was not read from the disk");
				})
				.thenWaitUntil(() -> helper.assertTrue(!SchedulerHandler.state(computer).log().isEmpty(), "the station ID never ran"))
				.thenExecute(() -> {
					SchedulerHandler.Ran ran = SchedulerHandler.state(computer).log().getFirst();
					helper.assertTrue(ran.job() == 1 && "station_id".equals(ran.kind()) && ran.result().isEmpty(), "the run: " + ran);
					helper.assertTrue(radio.isSendingDtmf(), "the radio is not sending the Morse ID");
					helper.assertTrue(SchedulerHandler.state(computer).log().size() == 1, "it ran more than once: " + SchedulerHandler.state(computer).log());
				})
				.thenIdle(40)
				.thenExecute(() -> helper.assertTrue(SchedulerHandler.state(computer).log().size() == 1, "it ran again within the minute"))
				.thenSucceed();
	}

	/** Editing is the owner's; WEFAX and beacons need their programs; a switched-off interface sends no ID; reminders. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void schedulerKeepsTheRules(GameTestHelper helper) {
		RadioComputerBlockEntity computer = station(helper);
		FakePlayer owner = player(helper, "owner"), guest = player(helper, "guest");
		helper.startSequence()
				.thenExecute(() -> powerOn(helper))
				.thenWaitUntil(() -> helper.assertTrue(computer.isPowered(), "the computer did not come on"))
				.thenExecute(() -> {
					computer.claim(owner);
					Schedule.Job id = new Schedule.Job(0, Schedule.Kind.STATION_ID, Schedule.When.EVERY, 10, true, "", "", "", 0L);
					helper.assertTrue("not_owner_sched".equals(app(computer, guest, "save", job(id)).getString("error")), "a guest changed the schedule");
					helper.assertFalse(app(computer, guest, "status", new CompoundTag()).contains("error"), "a guest could not look");
					CompoundTag saved = app(computer, owner, "save", job(id));
					helper.assertFalse(saved.contains("error"), "the owner could not add a job: " + saved);
					helper.assertTrue(computer.files().storage().exists(Schedule.FILE), "no " + Schedule.FILE + " on the disk");

					// The interface's transmit switch off: no ID goes out.
					GameTestUtil.blockEntity(helper, INTERFACE, RadioInterfaceBlockEntity.class).setTransmitEnabled(false);
					helper.assertTrue("sched_no_transmit".equals(app(computer, owner, "run", id(1)).getCompound("data").getString("result")), "an ID went out with transmit off");
					GameTestUtil.blockEntity(helper, INTERFACE, RadioInterfaceBlockEntity.class).setTransmitEnabled(true);
					helper.assertTrue(app(computer, owner, "run", id(1)).getCompound("data").getString("result").isEmpty(), "the ID did not go out");

					// WEFAX and beacons need their programs.
					Schedule.Job chart = new Schedule.Job(0, Schedule.Kind.WEFAX, Schedule.When.DAILY_GAME, 6 * 60, true, "WX.TXT", "", "", 0L);
					helper.assertTrue("sched_need_image".equals(app(computer, owner, "save", job(chart)).getString("error")), "a WEFAX job took a text file");
					chart = new Schedule.Job(0, Schedule.Kind.WEFAX, Schedule.When.DAILY_GAME, 6 * 60, true, "WX.IMG", "", "", 0L);
					helper.assertFalse(app(computer, owner, "save", job(chart)).contains("error"), "the WEFAX job was refused");
					helper.assertTrue("sched_need_wefax".equals(app(computer, owner, "run", id(2)).getCompound("data").getString("result")), "WEFAX ran without its program");
					Schedule.Job beacon = new Schedule.Job(0, Schedule.Kind.BEACON, Schedule.When.EVERY, 30, true, "", "", "", 0L);
					app(computer, owner, "save", job(beacon));
					helper.assertTrue("sched_need_aprs".equals(app(computer, owner, "run", id(3)).getCompound("data").getString("result")), "a beacon ran without APRS");
					computer.install(ComputerApps.WEFAX);
					helper.assertTrue("picture_not_image".equals(app(computer, owner, "run", id(2)).getCompound("data").getString("result")), "a missing chart was sent");

					// A reminder with nobody near the computer (fake players are not in the world's list).
					Schedule.Job net = new Schedule.Job(0, Schedule.Kind.REMINDER, Schedule.When.DAILY_GAME, 20 * 60, true, "", "Net on 146.52 at 8", "", 0L);
					helper.assertTrue(app(computer, owner, "save", job(net)).getCompound("data").getList("jobs", 10).size() == 4, "four jobs");
					helper.assertTrue("sched_nobody".equals(app(computer, owner, "run", id(4)).getCompound("data").getString("result")), "a reminder found someone");

					// Off and on, and deleted.
					app(computer, owner, "toggle", id(1));
					helper.assertFalse(SchedulerHandler.jobs(computer, SchedulerHandler.state(computer)).getFirst().enabled(), "job 1 is still on");
					app(computer, owner, "delete", id(1));
					helper.assertTrue(SchedulerHandler.jobs(computer, SchedulerHandler.state(computer)).size() == 3, "job 1 was not deleted");
					// The disk is the schedule: rewritten from elsewhere (a floppy, the shell), it is read again.
					computer.files().writeNbt(Schedule.FILE, Schedule.toTag(List.of()));
					helper.assertTrue(SchedulerHandler.jobs(computer, SchedulerHandler.state(computer)).isEmpty(), "the rewritten schedule was not read");
				})
				.thenSucceed();
	}
}
