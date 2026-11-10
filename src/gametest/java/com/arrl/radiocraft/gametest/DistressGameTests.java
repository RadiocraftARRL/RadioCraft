package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.MarineRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.apps.DistressHandler;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.network.serverbound.SBlockRadioControlPacket;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgramming;
import com.arrl.radiocraft.common.radio.MarineChannels;
import com.arrl.radiocraft.common.radio.distress.DistressAlert;
import com.arrl.radiocraft.common.radio.distress.DistressBeacons;
import com.arrl.radiocraft.common.radio.distress.DistressLog;
import com.arrl.radiocraft.common.radio.distress.DistressNature;
import com.arrl.radiocraft.common.radio.distress.DscCall;
import com.arrl.radiocraft.common.radio.distress.PersonalSos;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Distress alerting (2026-11-09): a marine radio's DSC distress call goes out on channel 70 as a data packet and every
 * marine radio in range hears it whatever channel it is on; an acknowledgement from one of them stops the caller's
 * repeats; a cancel marks the call cancelled. A distress beacon's alert on 161.975 MHz is heard by a commercial VHF
 * mobile tuned there, not by a marine radio nor a commercial radio on another channel (2026-11-09). The DISTRESS
 * program hears through every cabled radio at once, acknowledges through a marine radio, and sends a marine SOS (DSC)
 * or a personal SOS (161.975 through a commercial radio). The radios talk over their bare connectors (no antenna, a
 * few blocks), as in {@link MarineRadioGameTests}.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class DistressGameTests {

	private static final String BATCH = "distress";
	private static final BlockPos A = new BlockPos(4, 2, 4), B = new BlockPos(6, 2, 4);
	private static final String CALLER = "366000001", RESCUER = "338000002";

	private static MarineRadioBlockEntity marine(GameTestHelper helper, BlockPos pos) {
		return GameTestUtil.blockEntity(helper, pos, MarineRadioBlockEntity.class);
	}

	private static void powerOn(GameTestHelper helper, BlockPos pos) {
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, pos, RadioNetworkObject.class);
		if(!networkObject.isPowered)
			GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).toggle();
		helper.assertTrue(networkObject.isPowered, "Radio at " + pos.toShortString() + " did not power on");
	}

	private static boolean bare(GameTestHelper helper, BlockPos... radios) {
		for(BlockPos pos : radios)
			if(GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).getBareConnector() == null)
				return false;
		return true;
	}

	/** Two marine radios, B on a working channel (72), both on. */
	private static void twoRadios(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.MARINE_RADIO.get());
		helper.setBlock(B, RadiocraftBlocks.MARINE_RADIO.get());
	}

	private static void setUp(GameTestHelper helper) {
		MarineRadioBlockEntity b = marine(helper, B);
		b.setRadioState(b.getRadioState().select(MarineChannels.Region.US, "72"));
		powerOn(helper, A);
		powerOn(helper, B);
	}

	/**
	 * A sends a distress call (fire): A goes to 16 and its call is on; B, on channel 72, hears it on its channel 70
	 * receiver, logs it with A's MMSI, nature and position, shows it and sounds the alarm. B acknowledges: A's call is
	 * acknowledged (no more repeats) by B's MMSI, and B has it marked acknowledged.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void dscCallIsHeardAndAcknowledged(GameTestHelper helper) {
		twoRadios(helper);
		long[] id = new long[1];
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> setUp(helper))
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, A, B), "No bare connectors yet"))
				.thenExecute(() -> {
					MarineRadioBlockEntity a = marine(helper, A), b = marine(helper, B);
					a.dscKey(com.arrl.radiocraft.common.radio.marine.MarineRadioState.Key.NATURE, 0, java.util.UUID.randomUUID()); // UNDESIGNATED to FIRE.
					helper.assertTrue(a.getNature() == DistressNature.FIRE, "NAT steps to FIRE, was " + a.getNature());
					helper.assertTrue(a.sendDistress(CALLER), "A refused to send");
					DscCall call = a.getDscCall();
					id[0] = call.alertId();
					helper.assertTrue(call.state() == DscCall.State.ACTIVE, "A's call is on");
					helper.assertTrue(a.getChannel().name().equals(MarineChannels.DISTRESS), "A went to 16 for the MAYDAY");
					helper.assertFalse(a.sendDistress(CALLER), "A second call while one is on is refused");

					DistressLog.Entry heard = b.getDistressLog().get(id[0]);
					helper.assertTrue(heard != null, "B (on 72) did not hear the call on 70");
					DistressAlert alert = heard.alert();
					helper.assertTrue(alert.isDsc() && alert.state() == DistressAlert.State.ACTIVE, "B has it as an active DSC call");
					helper.assertTrue(alert.owner().equals(CALLER) && alert.nature() == DistressNature.FIRE, "MMSI and nature carried: " + alert.encode());
					BlockPos at = a.worldPos();
					helper.assertTrue(alert.x() == at.getX() && alert.z() == at.getZ(), "A's position carried: " + alert.coordinates());
					long now = helper.getLevel().getGameTime();
					helper.assertTrue(b.getDistressLog().shown(now) != null, "B shows it on its display");
					helper.assertTrue(b.isAlarming(now), "B sounds the alarm");

					helper.assertTrue(b.acknowledge(id[0], RESCUER) == MarineRadioBlockEntity.AckResult.OK, "B could not acknowledge");
					helper.assertFalse(b.isAlarming(now), "Acknowledging stops B's alarm");
					helper.assertTrue(b.getDistressLog().get(id[0]).alert().state() == DistressAlert.State.ACKNOWLEDGED, "B marks it acknowledged");
					DscCall acked = a.getDscCall();
					helper.assertTrue(acked.state() == DscCall.State.ACKNOWLEDGED, "The ack reached A, its call is " + acked.state());
					helper.assertTrue(acked.ackBy().equals(RESCUER), "A knows who acknowledged: " + acked.ackBy());
					helper.assertFalse(acked.repeatDue(Long.MAX_VALUE / 2), "No more repeats");
					helper.assertTrue(b.acknowledge(id[0], RESCUER) == MarineRadioBlockEntity.AckResult.NOT_ACTIVE, "A second ack is refused");
				})
				.thenSucceed();
	}

	/** A cancel goes out like the call: B, which heard the call, marks it cancelled (and keeps it listed). */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void dscCancelReachesListeners(GameTestHelper helper) {
		twoRadios(helper);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> setUp(helper))
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, A, B), "No bare connectors yet"))
				.thenExecute(() -> {
					MarineRadioBlockEntity a = marine(helper, A), b = marine(helper, B);
					helper.assertTrue(a.sendDistress(CALLER), "A refused to send");
					long id = a.getDscCall().alertId();
					helper.assertTrue(a.cancelDistress(), "A could not cancel");
					helper.assertTrue(a.getDscCall().state() == DscCall.State.CANCELLED, "A's call is cancelled");
					helper.assertTrue(b.getDistressLog().get(id).alert().state() == DistressAlert.State.CANCELLED, "B did not hear the cancel");
					helper.assertTrue(a.sendDistress(CALLER) && a.getDscCall().alertId() != id, "A new call may follow, with a new number");
				})
				.thenSucceed();
	}

	/** A radio that is off hears nothing, and sends nothing. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void radioOffNeitherSendsNorHears(GameTestHelper helper) {
		twoRadios(helper);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> powerOn(helper, A))
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, A), "No bare connector yet"))
				.thenExecute(() -> {
					helper.assertFalse(marine(helper, B).sendDistress(CALLER), "B is off: no call");
					helper.assertTrue(marine(helper, A).sendDistress(CALLER), "A refused to send");
					helper.assertTrue(marine(helper, B).getDistressLog().entries().isEmpty(), "B is off and heard it anyway");
				})
				.thenSucceed();
	}

	// -------------------- BEACONS ON 161.975 (2026-11-09) --------------------

	private static final BlockPos C = new BlockPos(8, 2, 4), D = new BlockPos(10, 2, 4), K = new BlockPos(7, 2, 4);

	private static CommercialMobileBlockEntity commercial(GameTestHelper helper, BlockPos pos) {
		return GameTestUtil.blockEntity(helper, pos, CommercialMobileBlockEntity.class);
	}

	/** Programs the commercial mobile at {@code pos} with {@code spec} on channel 1 and turns its knob there. */
	private static void program(GameTestHelper helper, BlockPos pos, String spec) {
		CommercialMobileBlockEntity radio = commercial(helper, pos);
		CommercialProgramming.Result result = CommercialProgramming.writeChannel(radio, 0, CommercialChannel.parse(spec));
		helper.assertTrue(result.ok(), "Programming " + spec + " failed: " + result.problems());
		CommercialProgramming.select(radio, 0);
	}

	private static DistressAlert beacon(GameTestHelper helper, long id, DistressAlert.State state) {
		BlockPos at = helper.absolutePos(A.above());
		return new DistressAlert(id, DistressAlert.Type.BEACON, state, "Castaway", null, at.getX(), at.getY(), at.getZ(), helper.getLevel().getGameTime(), "");
	}

	/**
	 * A beacon's alert is heard by the commercial VHF mobile on 161.975 (logged, shown with the alarm until dismissed),
	 * not by a marine radio (only DSC), nor by a commercial radio on another channel. Dismissed, a repeat of the same
	 * alert does not bring it back; the packet path's DISMISS_DISTRESS key is last in the enum.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void beaconIsHeardOnlyByACommercialRadioOn161975(GameTestHelper helper) {
		helper.setBlock(B, RadiocraftBlocks.MARINE_RADIO.get());
		helper.setBlock(C, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		helper.setBlock(D, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					program(helper, C, "AIS 1|161.975");
					program(helper, D, "OPS 1|155.0125");
					powerOn(helper, B);
					powerOn(helper, C);
					powerOn(helper, D);
				})
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, B, C, D), "No bare connectors yet"))
				.thenExecute(() -> {
					CommercialMobileBlockEntity c = commercial(helper, C), d = commercial(helper, D);
					helper.assertTrue(c.isOnBeaconFrequency(), "C is on 161.975");
					helper.assertTrue(c.distressLines() == null, "C shows nothing yet");
					DistressBeacons.alert(helper.getLevel(), helper.absolutePos(A.above()), null, beacon(helper, 99L, DistressAlert.State.ACTIVE));
					DistressLog.Entry heard = c.getDistressLog().get(99L);
					helper.assertTrue(heard != null, "The commercial radio on 161.975 did not hear the beacon");
					helper.assertTrue(!heard.alert().isDsc() && heard.alert().owner().equals("Castaway"), "Logged as Castaway's beacon: " + heard.alert().encode());
					List<String> lines = c.distressLines();
					helper.assertTrue(lines != null && lines.getFirst().equals("DISTRESS DETECTED 161.975"), "C's display: " + lines);
					helper.assertTrue(lines.get(1).equals(heard.alert().coordinates()), "C shows where the beacon is: " + lines);
					helper.assertTrue(marine(helper, B).getDistressLog().entries().isEmpty(), "A marine radio decoded a beacon alert");
					helper.assertTrue(d.getDistressLog().entries().isEmpty() && d.distressLines() == null, "A commercial radio on 155.0125 heard the beacon");

					helper.assertTrue(c.dismissDistress(), "nothing to dismiss");
					helper.assertTrue(c.distressLines() == null, "Dismissed, it is still shown");
					helper.assertFalse(c.dismissDistress(), "dismissed twice");
					DistressBeacons.alert(helper.getLevel(), helper.absolutePos(A.above()), null, beacon(helper, 99L, DistressAlert.State.ACTIVE));
					helper.assertTrue(c.distressLines() == null, "A repeat brought a dismissed alert back");
					helper.assertTrue(c.getDistressLog().get(99L) != null, "The repeat is still logged");
					SBlockRadioControlPacket.Action[] actions = SBlockRadioControlPacket.Action.values();
					helper.assertTrue(actions[actions.length - 1] == SBlockRadioControlPacket.Action.DISMISS_DISTRESS, "DISMISS_DISTRESS is not the last key");
				})
				.thenSucceed();
	}

	// -------------------- THE DISTRESS PROGRAM (2026-11-09) --------------------

	private static FakePlayer player(GameTestHelper helper) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("distress-app".getBytes()), "Rescuer"));
		BlockPos at = helper.absolutePos(K.south(3));
		player.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
		return player;
	}

	/** The computer at {@code K}, between the marine radio at {@code B} and the commercial radio at {@code C}, touching both. */
	private static RadioComputerBlockEntity computer(GameTestHelper helper) {
		helper.setBlock(K, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(K.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, K, RadioComputerBlockEntity.class);
		computer.install(ComputerApps.DISTRESS);
		return computer;
	}

	private static CompoundTag app(RadioComputerBlockEntity computer, FakePlayer player, String action, CompoundTag data) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", ComputerApps.DISTRESS.id());
		wrapped.putString("action", action);
		wrapped.put("data", data);
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, "app", wrapped, new ComputerReply(player, 7, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	private static CompoundTag kind(String kind, DistressNature nature) {
		CompoundTag tag = new CompoundTag();
		tag.putString("kind", kind);
		if(nature != null)
			tag.putString("nature", nature.name());
		return tag;
	}

	/**
	 * The program hears through both cabled radios with no picker: a DSC call from A (via the marine radio B) and a
	 * beacon (via the commercial radio C on 161.975) in one list, the status saying what it hears; F5's ack goes out
	 * through the marine radio and reaches A.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void programMergesEveryRadioAndAcksThroughTheMarineRadio(GameTestHelper helper) {
		twoRadios(helper);
		helper.setBlock(C, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		RadioComputerBlockEntity computer = computer(helper);
		FakePlayer player = player(helper);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					setUp(helper);
					program(helper, C, "AIS 1|161.975");
					powerOn(helper, C);
				})
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, A, B, C), "No bare connectors yet"))
				.thenExecute(() -> {
					helper.assertTrue(marine(helper, A).sendDistress(CALLER), "A refused to send");
					long call = marine(helper, A).getDscCall().alertId();
					// From beyond C, so the computer and its monitor are not in the way.
					DistressBeacons.alert(helper.getLevel(), helper.absolutePos(D.above()), null, beacon(helper, 77L, DistressAlert.State.ACTIVE));
					CompoundTag status = app(computer, player, "status", new CompoundTag()).getCompound("data");
					helper.assertTrue(status.getInt("marinesOn") == 1 && status.getInt("beaconRadios") == 1, "hears through both: " + status);
					helper.assertTrue(status.getList("alerts", Tag.TAG_COMPOUND).size() == 2, "both alerts in one list: " + status);
					CompoundTag ack = new CompoundTag();
					ack.putLong("id", call);
					CompoundTag reply = app(computer, player, "ack", ack);
					helper.assertFalse(reply.contains("error"), "ack: " + reply);
					helper.assertTrue(marine(helper, A).getDscCall().state() == DscCall.State.ACKNOWLEDGED, "The ack did not reach A");
					ack.putLong("id", 77L);
					helper.assertTrue(app(computer, player, "ack", ack).getString("error").equals("distress_ack_not_dsc"), "a beacon was acknowledged");
				})
				.thenSucceed();
	}

	/** MARINE SOS from the program: the cabled marine radio sends a DSC call with the nature picked; A hears it; F8 cancels it. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void programSendsAndCancelsAMarineSos(GameTestHelper helper) {
		twoRadios(helper);
		RadioComputerBlockEntity computer = computer(helper);
		FakePlayer player = player(helper);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					MarineRadioBlockEntity a = marine(helper, A);
					a.setRadioState(a.getRadioState().select(MarineChannels.Region.US, "72"));
					powerOn(helper, A);
				})
				.thenExecute(() -> {
					CompoundTag status = app(computer, player, "status", new CompoundTag()).getCompound("data");
					helper.assertTrue(status.getString("marineWhy").equals("distress_marine_off"), "B is off: " + status);
					helper.assertTrue(status.getString("personalWhy").equals("distress_no_commercial"), "no commercial radio: " + status);
					powerOn(helper, B);
				})
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, A, B), "No bare connectors yet"))
				.thenExecute(() -> {
					CompoundTag reply = app(computer, player, "send", kind("marine", DistressNature.SINKING));
					helper.assertFalse(reply.contains("error"), "send: " + reply);
					DscCall call = marine(helper, B).getDscCall();
					helper.assertTrue(call.state() == DscCall.State.ACTIVE && call.nature() == DistressNature.SINKING, "B's call: " + call);
					DistressLog.Entry heard = marine(helper, A).getDistressLog().get(call.alertId());
					helper.assertTrue(heard != null && heard.alert().nature() == DistressNature.SINKING, "A did not hear the SOS");
					CompoundTag status = reply.getCompound("data");
					helper.assertTrue(status.getCompound("outDsc").getString("state").equals("ACTIVE"), "the outgoing call is shown: " + status);
					helper.assertTrue(app(computer, player, "send", kind("marine", null)).getString("error").equals("distress_marine_busy"), "a second call went out");
					reply = app(computer, player, "cancel", kind("marine", null));
					helper.assertFalse(reply.contains("error"), "cancel: " + reply);
					helper.assertTrue(marine(helper, B).getDscCall().state() == DscCall.State.CANCELLED, "B's call is not cancelled");
					helper.assertTrue(marine(helper, A).getDistressLog().get(call.alertId()).alert().state() == DistressAlert.State.CANCELLED, "A did not hear the cancel");
				})
				.thenSucceed();
	}

	/**
	 * PERSONAL SOS from the program: a beacon alert with the player's name and the nature picked goes out on 161.975
	 * through the cabled commercial radio C, and D (on 161.975, not cabled) hears it; cancelled, D marks it so; sent
	 * again, it stops once C loses power.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void programSendsAPersonalSosUntilCancelledOrPowerLost(GameTestHelper helper) {
		helper.setBlock(C, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		helper.setBlock(D, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		RadioComputerBlockEntity computer = computer(helper);
		FakePlayer player = player(helper);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					program(helper, C, "OPS 1|155.0125"); // It sends on 161.975 whatever channel it is on.
					program(helper, D, "AIS 1|161.975");
					powerOn(helper, C);
					powerOn(helper, D);
				})
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, C, D), "No bare connectors yet"))
				.thenExecute(() -> {
					CompoundTag reply = app(computer, player, "send", kind("personal", DistressNature.MAN_OVERBOARD));
					helper.assertFalse(reply.contains("error"), "send: " + reply);
					PersonalSos sos = DistressHandler.state(computer).personal();
					helper.assertTrue(sos.active(), "the SOS is not on");
					DistressLog.Entry heard = commercial(helper, D).getDistressLog().get(sos.alertId());
					helper.assertTrue(heard != null, "D on 161.975 did not hear the personal SOS");
					helper.assertTrue(heard.alert().type() == DistressAlert.Type.BEACON && heard.alert().owner().equals("Rescuer")
							&& heard.alert().nature() == DistressNature.MAN_OVERBOARD, "D logged: " + heard.alert().encode());
					helper.assertTrue(commercial(helper, D).distressLines() != null, "D does not show it");
					helper.assertTrue(app(computer, player, "send", kind("personal", null)).getString("error").equals("distress_personal_busy"), "a second SOS went out");
					helper.assertFalse(app(computer, player, "cancel", kind("personal", null)).contains("error"), "cancel");
					helper.assertTrue(commercial(helper, D).getDistressLog().get(sos.alertId()).alert().state() == DistressAlert.State.CANCELLED, "D did not hear the cancel");

					helper.assertFalse(app(computer, player, "send", kind("personal", null)).contains("error"), "send again");
					helper.assertTrue(DistressHandler.state(computer).personal().nature() == DistressNature.UNDESIGNATED, "no nature given: undesignated");
					commercial(helper, C).toggle(); // C loses power.
					DistressHandler handler = new DistressHandler();
					long now = helper.getLevel().getGameTime();
					for(long t = now; t < now + 40L; t++) // Its checks run once a second.
						handler.tick(computer, t);
					PersonalSos stopped = DistressHandler.state(computer).personal();
					helper.assertTrue(stopped.state() == PersonalSos.State.STOPPED && stopped.reason().equals("no_power"), "the SOS went on without power: " + stopped);
				})
				.thenSucceed();
	}
}
