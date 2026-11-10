package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioInterfaceBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioAllBandBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.Waterfall;
import com.arrl.radiocraft.common.computer.apps.RigControlHandler;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.BandScope;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Rig control (computer batch 3B): the computer tunes, switches band and mode and keys a radio on its radio interface;
 * a range scan stops on a station that is transmitting, and the waterfall shows it. Each test on its own frequency.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class RigControlGameTests {

	private static final BlockPos RADIO_A = new BlockPos(3, 2, 6);
	private static final BlockPos RADIO_B = RADIO_A.east(2);
	private static final BlockPos AT_A = RADIO_A.north().west();

	private static FakePlayer player(GameTestHelper helper, String name) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("rigctl-" + name).getBytes()), name));
		BlockPos at = helper.absolutePos(AT_A.south(3));
		player.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
		return player;
	}

	private static void powerOn(GameTestHelper helper, BlockPos pos) {
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, pos, RadioNetworkObject.class);
		if(!networkObject.isPowered)
			GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).toggle();
		helper.assertTrue(networkObject.isPowered, "Radio at " + pos.toShortString() + " did not power on");
	}

	private static RadioComputerBlockEntity computer(GameTestHelper helper, BlockPos pos, ComputerApp... apps) {
		helper.setBlock(pos, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(pos.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, pos, RadioComputerBlockEntity.class);
		for(ComputerApp app : apps)
			computer.install(app);
		return computer;
	}

	private static CompoundTag app(RadioComputerBlockEntity computer, FakePlayer player, String action, CompoundTag data) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", ComputerApps.RIG_CONTROL.id());
		wrapped.putString("action", action);
		wrapped.put("data", data);
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, "app", wrapped, new ComputerReply(player, 7, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	private static CompoundTag tag(String key, double value) {
		CompoundTag tag = new CompoundTag();
		tag.putDouble(key, value);
		return tag;
	}

	private static CompoundTag intTag(String key, int value) {
		CompoundTag tag = new CompoundTag();
		tag.putInt(key, value);
		return tag;
	}

	/** What an answer carries (under "data", as the screen gets it). */
	private static CompoundTag data(CompoundTag reply) {
		return reply.getCompound("data");
	}

	private static void ok(GameTestHelper helper, CompoundTag reply, String what) {
		helper.assertFalse(reply.contains("error"), what + ": " + reply);
	}

	/** The keyboard tunes the all-band radio: steps, a typed frequency on another band, the band knob, the mode, the PTT. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void rigControlTunesTheRadio(GameTestHelper helper) {
		helper.setBlock(RADIO_A, RadiocraftBlocks.ALL_BAND_RADIO.get());
		helper.setBlock(RADIO_A.north(), RadiocraftBlocks.RADIO_INTERFACE.get());
		GameTestUtil.blockEntity(helper, RADIO_A.north(), RadioInterfaceBlockEntity.class).setCallsign("W1RIG", "gametest");
		RadioComputerBlockEntity computer = computer(helper, AT_A, ComputerApps.RIG_CONTROL);
		RadioComputerBlockEntity alone = computer(helper, new BlockPos(11, 2, 12), ComputerApps.RIG_CONTROL);
		FakePlayer player = player(helper, "tune");
		HFRadioAllBandBlockEntity radio = GameTestUtil.blockEntity(helper, RADIO_A, HFRadioAllBandBlockEntity.class);
		helper.startSequence()
				.thenExecute(() -> powerOn(helper, RADIO_A))
				.thenIdle(1)
				.thenExecute(() -> {
					helper.assertTrue(app(alone, player, "step", intTag("steps", 1)).getString("error").equals("station_no_interface"),
							"a computer with no radio interface tuned a radio");
					float start = radio.getFrequency();
					ok(helper, app(computer, player, "step", intTag("steps", 3)), "step");
					helper.assertTrue(radio.getFrequency() == start + 3 * radio.getFrequencyStep(), "three steps up: " + start + " -> " + radio.getFrequency());
					ok(helper, app(computer, player, "tune", tag("hz", 14_074_000.0D)), "tune 14.074");
					helper.assertTrue(radio.getBand().name().equals("20m") && radio.getFrequency() == 14_074_000.0F,
							"typed 14.074 on the 10m knob: " + radio.getBand().name() + " " + radio.getFrequency());
					helper.assertTrue(app(computer, player, "tune", tag("hz", 21_074_000.0D)).getString("error").equals("rig_out_of_range"), "tuned to 15m, which it has not");
					ok(helper, app(computer, player, "band", intTag("dir", -1)), "band down");
					helper.assertTrue(radio.getBand().name().equals("40m"), "band down from 20m: " + radio.getBand().name());
					String before = RigControlHandler.mode(radio);
					CompoundTag moded = app(computer, player, "mode", new CompoundTag());
					ok(helper, moded, "mode");
					helper.assertFalse(RigControlHandler.mode(radio).equals(before), "the mode did not change from " + before);
					ok(helper, app(computer, player, "tune", tag("hz", 7_074_000.0D)), "tune 7.074");
					radio.setSSBEnabled(true);
					CompoundTag ptt = new CompoundTag();
					ptt.putBoolean("on", true);
					ok(helper, app(computer, player, "ptt", ptt), "PTT");
					helper.assertTrue(radio.isPTTDown(), "the PTT did not key");
					ok(helper, app(computer, player, "leave", new CompoundTag()), "leave");
					helper.assertFalse(radio.isPTTDown(), "leaving the program did not let go of the PTT");
					CompoundTag status = data(app(computer, player, "status", new CompoundTag()));
					helper.assertTrue(status.getCompound("rig").getDouble("hz") == 7_074_000.0D && status.getCompound("rig").getString("band").equals("40m"),
							"status: " + status);
				})
				.thenSucceed();
	}

	/** A range scan steps until it finds a station transmitting, and stops there; the waterfall shows that station. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 600)
	public static void scanStopsOnActivity(GameTestHelper helper) {
		helper.setBlock(RADIO_A, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(RADIO_A.north(), RadiocraftBlocks.RADIO_INTERFACE.get());
		helper.setBlock(RADIO_B, RadiocraftBlocks.VHF_BASE_STATION.get());
		RadioComputerBlockEntity computer = computer(helper, AT_A, ComputerApps.RIG_CONTROL);
		FakePlayer player = player(helper, "scan");
		RadioBlockEntity a = GameTestUtil.blockEntity(helper, RADIO_A, RadioBlockEntity.class);
		RadioBlockEntity b = GameTestUtil.blockEntity(helper, RADIO_B, RadioBlockEntity.class);
		a.setFrequency(145_480_000.0F);
		b.setFrequency(145_600_000.0F);
		helper.onEachTick(() -> { // B keeps sending packets: its tones are on the air most of the time.
			if(b.isPowered() && !b.isSendingData())
				b.sendData(TncPacket.beacon(helper.getLevel().getRandom().nextLong(), "W1SCN", helper.absolutePos(RADIO_B), "SCAN TEST", "HOUSE"));
		});
		helper.startSequence()
				.thenExecute(() -> {
					powerOn(helper, RADIO_A);
					powerOn(helper, RADIO_B);
				})
				.thenIdle(1)
				.thenExecute(() -> {
					CompoundTag scan = new CompoundTag();
					scan.putString("mode", "range");
					scan.putDouble("low", 145_500_000.0D);
					scan.putDouble("high", 145_700_000.0D);
					ok(helper, app(computer, player, "scan", scan), "scan");
					helper.assertTrue(RigControlHandler.state(computer).scan().isScanning(), "not scanning");
				})
				.thenWaitUntil(() -> helper.assertTrue(RigControlHandler.state(computer).scan().isStopped(),
						"the scan never stopped; it is on " + a.getFrequency()))
				.thenExecute(() -> {
					helper.assertTrue(a.getFrequency() == 145_600_000.0F, "stopped on " + a.getFrequency() + ", not B's 145.600");
					ok(helper, app(computer, player, "zoom", intTag("zoom", 0)), "zoom");
					app(computer, player, "scope", new CompoundTag()); // Attaches the band scope.
					helper.assertTrue(a.hasScope(), "no band scope on the radio");
				})
				.thenIdle(RigControlHandler.SHARED_ROW_TICKS + 2)
				.thenExecute(() -> {
					CompoundTag scope = data(app(computer, player, "scope", new CompoundTag()));
					byte[] row = scope.getByteArray("row");
					int bin = Waterfall.bin(145_600_000.0D, scope.getDouble("lo"), scope.getDouble("hi"), BandScope.BINS);
					helper.assertTrue(row.length == BandScope.BINS && bin >= 0 && row[bin] > 0, "the waterfall does not show B at bin " + bin + ": "
							+ java.util.Arrays.toString(row));
					int quiet = Waterfall.bin(147_000_000.0D, scope.getDouble("lo"), scope.getDouble("hi"), BandScope.BINS);
					helper.assertTrue(row[quiet] == 0, "a quiet frequency shows a signal");
					CompoundTag off = new CompoundTag();
					off.putString("mode", "off");
					ok(helper, app(computer, player, "scan", off), "scan off");
				})
				.thenSucceed();
	}

	/**
	 * Two radios on the radio interface (2026-11-09): the status says none is picked, so the screen asks; picking one
	 * makes it the rig, is kept with the computer, and a radio no longer on the bus cannot be picked.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void pickingTheRigIsKept(GameTestHelper helper) {
		helper.setBlock(RADIO_A, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(RADIO_A.north(), RadiocraftBlocks.RADIO_INTERFACE.get());
		helper.setBlock(RADIO_A.north(2), RadiocraftBlocks.VHF_BASE_STATION.get()); // A second radio beside the interface.
		RadioComputerBlockEntity computer = computer(helper, AT_A, ComputerApps.RIG_CONTROL);
		FakePlayer player = player(helper, "pick");
		helper.startSequence()
				.thenExecute(() -> {
					powerOn(helper, RADIO_A);
					powerOn(helper, RADIO_A.north(2));
				})
				.thenIdle(1)
				.thenExecute(() -> {
					CompoundTag status = data(app(computer, player, "status", new CompoundTag()));
					helper.assertTrue(status.getList("radios", net.minecraft.nbt.Tag.TAG_COMPOUND).size() == 2, "two radios on the bus: " + status);
					helper.assertFalse(status.getBoolean("picked"), "nothing picked yet, so the screen asks");
					long second = helper.absolutePos(RADIO_A.north(2)).asLong();
					CompoundTag select = new CompoundTag();
					select.putLong("pos", second);
					ok(helper, app(computer, player, "select", select), "select");
					status = data(app(computer, player, "status", new CompoundTag()));
					helper.assertTrue(status.getBoolean("picked"), "the pick is kept: " + status);
					helper.assertTrue(RigControlHandler.rig(computer, RigControlHandler.state(computer)).getBlockPos().asLong() == second, "the rig is the one picked");
					CompoundTag saved = new CompoundTag();
					RigControlHandler.state(computer).save(saved, helper.getLevel().registryAccess());
					helper.assertTrue(saved.getLong("selected") == second, "the pick is saved with the computer: " + saved);
					select.putLong("pos", helper.absolutePos(RADIO_B).asLong());
					helper.assertTrue(app(computer, player, "select", select).getString("error").equals("rig_no_radio"), "a radio off the bus was picked");
				})
				.thenSucceed();
	}
}
