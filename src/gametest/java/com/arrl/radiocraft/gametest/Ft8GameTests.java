package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioInterfaceBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.Logbook;
import com.arrl.radiocraft.common.computer.Schedule;
import com.arrl.radiocraft.common.computer.apps.Ft8Handler;
import com.arrl.radiocraft.common.computer.apps.RttyHandler;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.ft8.Ft8;
import com.arrl.radiocraft.common.radio.ft8.Ft8Decode;
import com.arrl.radiocraft.common.radio.ft8.Ft8Sequencer;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * FT8 (computer batch 3B) over the air: two computers, each on a VHF base station's radio interface (bare connectors;
 * the path is the same as on HF), work each other: A calls CQ, B answers it, the auto-sequencers run the contact and
 * both log it to LOGBOOK.LOG. Seven blocks apart the signal is far too weak for RTTY, and FT8 still decodes. Each test
 * on its own frequency. A contact takes five or six 15 s slots, so the timeouts are long.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class Ft8GameTests {

	private static final BlockPos RADIO_A = new BlockPos(3, 2, 6);
	private static final BlockPos AT_A = RADIO_A.north().west();

	private static FakePlayer player(GameTestHelper helper, String name) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("ft8-" + name).getBytes()), name));
		BlockPos at = helper.absolutePos(AT_A.south(3));
		player.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
		return player;
	}

	/** A VHF base station on {@code kHz} with a radio interface north of it set to {@code callsign}. */
	private static void station(GameTestHelper helper, BlockPos radioPos, int kHz, String callsign) {
		helper.setBlock(radioPos, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(radioPos.north(), RadiocraftBlocks.RADIO_INTERFACE.get());
		GameTestUtil.blockEntity(helper, radioPos, RadioBlockEntity.class).setFrequency(kHz * 1000.0F);
		GameTestUtil.blockEntity(helper, radioPos.north(), RadioInterfaceBlockEntity.class).setCallsign(callsign, "gametest");
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

	private static CompoundTag app(RadioComputerBlockEntity computer, FakePlayer player, ComputerApp program, String action, CompoundTag data) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", program.id());
		wrapped.putString("action", action);
		wrapped.put("data", data);
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, "app", wrapped, new ComputerReply(player, 7, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	/** The decode {@code computer} heard from {@code text} (exactly), or null. */
	@Nullable
	private static Ft8Decode decoded(RadioComputerBlockEntity computer, String text) {
		for(Ft8Decode decode : Ft8Handler.state(computer).decodes())
			if(decode.text().equals(text))
				return decode;
		return null;
	}

	/** {@code computer}'s logbook entry with {@code call}, or null. */
	@Nullable
	private static Logbook.Entry logged(RadioComputerBlockEntity computer, String call) {
		CompoundTag tag = computer.files().readNbt(Ft8Handler.LOG_FILE);
		if(tag == null)
			return null;
		for(Logbook.Entry entry : Logbook.fromTag(tag).entries())
			if(entry.call().equals(call))
				return entry;
		return null;
	}

	/** A calls CQ, B answers; the sequencers run the whole contact and both stations log it. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 3600)
	public static void twoComputersCompleteAnFt8Qso(GameTestHelper helper) {
		BlockPos radioB = RADIO_A.east(2);
		station(helper, RADIO_A, 145_270, "W1FTA");
		station(helper, radioB, 145_270, "K2FTB");
		RadioComputerBlockEntity a = computer(helper, AT_A, ComputerApps.FT8);
		RadioComputerBlockEntity b = computer(helper, radioB.north().east(), ComputerApps.FT8);
		FakePlayer player = player(helper, "qso");
		String gridA = Ft8.grid(a.getBlockPos());
		String cq = "CQ W1FTA " + gridA;
		helper.startSequence()
				.thenExecute(() -> {
					powerOn(helper, RADIO_A);
					powerOn(helper, radioB);
				})
				.thenIdle(1)
				.thenExecute(() -> {
					CompoundTag reply = app(a, player, ComputerApps.FT8, "cq", new CompoundTag());
					helper.assertFalse(reply.contains("error"), "A could not call CQ: " + reply);
					app(b, player, ComputerApps.FT8, "status", new CompoundTag()); // B is listening.
				})
				.thenWaitUntil(() -> helper.assertTrue(decoded(b, cq) != null, "B has not decoded A's CQ: " + Ft8Handler.state(b).decodes()))
				.thenExecute(() -> {
					Ft8Decode heard = decoded(b, cq);
					helper.assertTrue(heard.snr() > Ft8.COPY_THRESHOLD_SNR, "two blocks apart the CQ should be strong: " + heard.snr());
					CompoundTag reply = app(b, player, ComputerApps.FT8, "answer", heard.toTag());
					helper.assertFalse(reply.contains("error"), "B could not answer: " + reply);
					helper.assertTrue(Ft8Handler.state(b).sequencer().step() == Ft8Sequencer.Step.REPLIED, "B is " + Ft8Handler.state(b).sequencer().step());
				})
				.thenWaitUntil(() -> helper.assertTrue(logged(a, "K2FTB") != null && logged(b, "W1FTA") != null,
						"not logged yet: A " + Ft8Handler.state(a).sequencer().step() + " " + Ft8Handler.state(a).lastTx()
								+ " / B " + Ft8Handler.state(b).sequencer().step() + " " + Ft8Handler.state(b).lastTx()))
				.thenExecute(() -> {
					Logbook.Entry atA = logged(a, "K2FTB"), atB = logged(b, "W1FTA");
					helper.assertTrue(atA.mode().equals("FT8") && atB.mode().equals("FT8"), "mode " + atA.mode() + " / " + atB.mode());
					helper.assertTrue(atA.rstSent().equals(atB.rstReceived()) && atB.rstSent().equals(atA.rstReceived()),
							"the reports do not match: A sent " + atA.rstSent() + " got " + atA.rstReceived() + ", B sent " + atB.rstSent() + " got " + atB.rstReceived());
					helper.assertTrue(atA.rstSent().startsWith("+") || atA.rstSent().startsWith("-"), "an FT8 report is in dB: " + atA.rstSent());
					helper.assertTrue(atB.notes().contains(gridA), "B's notes do not have A's grid: " + atB.notes());
					helper.assertTrue(Math.abs(atA.frequencyMhz() - 145.27D) < 0.01D, "logged on " + atA.frequencyMhz());
					helper.assertTrue(Ft8Handler.state(a).qsoCount() == 1 && Ft8Handler.state(b).qsoCount() == 1, "counted twice or not at all");
					app(a, player, ComputerApps.FT8, "halt", new CompoundTag());
					helper.assertFalse(Ft8Handler.state(a).txEnabled(), "halt left TX on");
				})
				.thenSucceed();
	}

	/**
	 * Seven blocks apart over bare connectors the signal is (1/8)^2 = 0.016 against a copy threshold of 0.15: RTTY is
	 * lost entirely (it garbles down to half the threshold), FT8 still decodes it, about -18 dB.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 1500)
	public static void ft8DecodesWhereRttyIsLost(GameTestHelper helper) {
		BlockPos far = RADIO_A.east(7);
		station(helper, RADIO_A, 145_290, "W1FTC");
		station(helper, far, 145_290, "K2FTD");
		RadioComputerBlockEntity a = computer(helper, AT_A, ComputerApps.FT8, ComputerApps.RTTY);
		RadioComputerBlockEntity b = computer(helper, far.north().east(), ComputerApps.FT8, ComputerApps.RTTY);
		FakePlayer player = player(helper, "weak");
		String cq = "CQ W1FTC " + Ft8.grid(a.getBlockPos());
		helper.startSequence()
				.thenExecute(() -> {
					powerOn(helper, RADIO_A);
					powerOn(helper, far);
				})
				.thenIdle(1)
				.thenExecute(() -> helper.assertTrue(RttyHandler.type(a, "RYRY DE W1FTC\n") == null, "A could not type RTTY"))
				.thenWaitUntil(() -> helper.assertTrue(RttyHandler.state(a).pending().isEmpty() && !GameTestUtil.blockEntity(helper, RADIO_A, RadioBlockEntity.class).isSendingData(),
						"A's RTTY is still going out"))
				.thenIdle(10)
				.thenExecute(() -> {
					helper.assertTrue(RttyHandler.state(b).copy().lines().isEmpty(), "B copied RTTY that weak: " + RttyHandler.state(b).copy().lines());
					CompoundTag reply = app(a, player, ComputerApps.FT8, "cq", new CompoundTag());
					helper.assertFalse(reply.contains("error"), "A could not call CQ: " + reply);
					app(b, player, ComputerApps.FT8, "status", new CompoundTag());
				})
				.thenWaitUntil(() -> helper.assertTrue(decoded(b, cq) != null, "B has not decoded the weak CQ: " + Ft8Handler.state(b).decodes()))
				.thenExecute(() -> {
					int snr = decoded(b, cq).snr();
					helper.assertTrue(snr < -12 && snr >= -20, "the weak CQ's SNR: " + snr);
				})
				.thenSucceed();
	}

	/** The FT8 floppy takes any pressure plate (maintainer's recipe): wooden ones by their tag, and the stone and metal ones. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void theFloppyTakesAnyPressurePlate(GameTestHelper helper) {
		var recipe = helper.getLevel().getRecipeManager().byKey(Radiocraft.id("floppy_ft8"));
		helper.assertTrue(recipe.isPresent(), "no floppy_ft8 recipe");
		var ingredients = recipe.get().value().getIngredients();
		for(net.minecraft.world.item.Item plate : new net.minecraft.world.item.Item[] { net.minecraft.world.item.Items.OAK_PRESSURE_PLATE,
				net.minecraft.world.item.Items.CRIMSON_PRESSURE_PLATE, net.minecraft.world.item.Items.STONE_PRESSURE_PLATE,
				net.minecraft.world.item.Items.POLISHED_BLACKSTONE_PRESSURE_PLATE, net.minecraft.world.item.Items.LIGHT_WEIGHTED_PRESSURE_PLATE,
				net.minecraft.world.item.Items.HEAVY_WEIGHTED_PRESSURE_PLATE })
			helper.assertTrue(ingredients.stream().anyMatch(ingredient -> ingredient.test(new net.minecraft.world.item.ItemStack(plate))), plate + " is not taken");
		helper.succeed();
	}

	/** SCHEDULE's FT8 CQ job starts calling CQ (and needs FT8 installed). */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100)
	public static void theSchedulerCallsCq(GameTestHelper helper) {
		station(helper, RADIO_A, 145_310, "W1FTE");
		RadioComputerBlockEntity a = computer(helper, AT_A, ComputerApps.FT8, ComputerApps.SCHEDULER);
		RadioComputerBlockEntity without = computer(helper, new BlockPos(11, 2, 12), ComputerApps.SCHEDULER);
		helper.startSequence()
				.thenExecute(() -> powerOn(helper, RADIO_A))
				.thenIdle(1)
				.thenExecute(() -> {
					helper.assertTrue("sched_need_ft8".equals(Ft8Handler.startCq(without, 10)), "FT8 CQ ran without FT8");
					Schedule.Job job = new Schedule.Job(1, Schedule.Kind.FT8_CQ, Schedule.When.EVERY, 60, true, "", "", "", 0L);
					String result = com.arrl.radiocraft.common.computer.apps.SchedulerHandler.runAndLog(a, com.arrl.radiocraft.common.computer.apps.SchedulerHandler.state(a), job);
					helper.assertTrue(result == null || result.isEmpty(), "the job failed: " + result);
					Ft8Handler.State state = Ft8Handler.state(a);
					helper.assertTrue(state.sequencer().step() == Ft8Sequencer.Step.CALLING_CQ && state.txEnabled(), "not calling CQ: " + state.sequencer().step());
					helper.assertTrue(state.sequencer().next().encode().startsWith("CQ W1FTE "), "the CQ: " + state.sequencer().next().encode());
				})
				.thenSucceed();
	}
}
