package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioInterfaceBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.PacketBbs;
import com.arrl.radiocraft.common.computer.apps.BbsHandler;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
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

import java.util.UUID;

/**
 * The Packet BBS program (computer apps 2), server side: computer B runs a mailbox (W1BBB-1), computer A calls it from
 * its terminal page over the air (APRS messages through VHF base stations and radio interfaces on 145.070 MHz), writes
 * a message, lists it and reads it back; the message is on B's disk in BBS.DAT. Then only the sysop may delete.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class PacketBbsGameTests {

	private static final String BATCH = "packet_bbs";
	private static final BlockPos RADIO_A = new BlockPos(3, 2, 6);
	private static final BlockPos RADIO_B = RADIO_A.east(2);
	private static final BlockPos INTERFACE_A = RADIO_A.north(), INTERFACE_B = RADIO_B.north();
	private static final BlockPos AT_A = INTERFACE_A.west(), AT_B = INTERFACE_B.east();
	private static final String BBS = "W1BBB-1";

	private static FakePlayer player(GameTestHelper helper, String name) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("packetbbs-" + name).getBytes()), name));
		BlockPos at = helper.absolutePos(AT_A.south(3));
		player.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
		return player;
	}

	/**
	 * A VHF base station on {@code kHz} with a radio interface north of it set to {@code callsign}. Digipeating off: the
	 * two stations hear each other direct, and a digipeat would keep a radio busy (deaf) when the next line comes.
	 */
	private static void station(GameTestHelper helper, BlockPos radioPos, int kHz, String callsign) {
		helper.setBlock(radioPos, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(radioPos.north(), RadiocraftBlocks.RADIO_INTERFACE.get());
		GameTestUtil.blockEntity(helper, radioPos, RadioBlockEntity.class).setFrequency(kHz * 1000.0F);
		RadioInterfaceBlockEntity radioInterface = GameTestUtil.blockEntity(helper, radioPos.north(), RadioInterfaceBlockEntity.class);
		radioInterface.setCallsign(callsign, "gametest");
		radioInterface.setDigipeatEnabled(false);
	}

	private static void powerOn(GameTestHelper helper, BlockPos pos) {
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, pos, RadioNetworkObject.class);
		if(!networkObject.isPowered)
			GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).toggle();
		helper.assertTrue(networkObject.isPowered, "Radio at " + pos.toShortString() + " did not power on");
	}

	private static RadioComputerBlockEntity computer(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(pos.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, pos, RadioComputerBlockEntity.class);
		computer.install(ComputerApps.BBS);
		return computer;
	}

	private static CompoundTag app(RadioComputerBlockEntity computer, FakePlayer player, String action, CompoundTag data) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", ComputerApps.BBS.id());
		wrapped.putString("action", action);
		wrapped.put("data", data);
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, "app", wrapped, new ComputerReply(player, 7, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	/** A from A's terminal to the BBS. */
	private static void type(GameTestHelper helper, RadioComputerBlockEntity a, FakePlayer player, String text) {
		CompoundTag data = new CompoundTag();
		data.putString("to", BBS);
		data.putString("text", text);
		CompoundTag reply = app(a, player, "term", data);
		helper.assertFalse(reply.contains("error"), "A could not send '" + text + "': " + reply);
	}

	private static boolean heard(RadioComputerBlockEntity a, String part) {
		return BbsHandler.state(a).termLog().stream().anyMatch(line -> !line.startsWith("> ") && line.contains(part));
	}

	/** A writes, lists and reads a message on B's mailbox over the air; B keeps it on its disk. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 900, batch = BATCH)
	public static void callerWritesAndReadsAMessage(GameTestHelper helper) {
		station(helper, RADIO_A, 145_070, "W1AAA");
		station(helper, RADIO_B, 145_070, "W1BBB");
		RadioComputerBlockEntity a = computer(helper, AT_A);
		RadioComputerBlockEntity b = computer(helper, AT_B);
		FakePlayer player = player(helper, "caller");
		helper.startSequence()
				.thenExecute(() -> {
					powerOn(helper, RADIO_A);
					powerOn(helper, RADIO_B);
				})
				.thenWaitUntil(() -> helper.assertTrue(a.isPowered() && b.isPowered(), "the computers did not come on"))
				.thenExecute(() -> {
					helper.assertTrue(BbsHandler.bbsCall(b).equals(BBS), "B's mailbox is " + BbsHandler.bbsCall(b));
					helper.assertFalse(BbsHandler.state(b).online(), "a new mailbox should start switched off");
					CompoundTag on = new CompoundTag();
					on.putBoolean("online", true);
					helper.assertFalse(app(b, player, "settings", on).contains("error"), "could not switch B's mailbox on");
					type(helper, a, player, "SP W1XYZ Field day");
				})
				.thenWaitUntil(() -> helper.assertTrue(heard(a, "ENTER MESSAGE"), "no prompt for the text: " + BbsHandler.state(a).termLog()))
				.thenExecute(() -> type(helper, a, player, "Meet at the park"))
				.thenIdle(30)
				.thenExecute(() -> type(helper, a, player, "/EX"))
				.thenWaitUntil(() -> helper.assertTrue(heard(a, "MSG 1 SAVED"), "not saved: " + BbsHandler.state(a).termLog()))
				.thenExecute(() -> {
					CompoundTag file = b.files().readNbt(BbsHandler.FILE);
					helper.assertTrue(file != null, "no " + BbsHandler.FILE + " on B's disk");
					PacketBbs.Message message = PacketBbs.fromTag(file).find(1);
					helper.assertTrue(message != null && message.from().equals("W1AAA") && message.to().equals("W1XYZ")
							&& message.subject().equals("Field day") && message.body().equals("Meet at the park"), "stored: " + message);
					type(helper, a, player, "L");
				})
				.thenWaitUntil(() -> helper.assertTrue(heard(a, "W1AAA>W1XYZ Field day"), "not listed: " + BbsHandler.state(a).termLog()))
				.thenIdle(20)
				.thenExecute(() -> type(helper, a, player, "R 1"))
				.thenWaitUntil(() -> helper.assertTrue(heard(a, "END MSG 1"), "not read back: " + BbsHandler.state(a).termLog()))
				.thenExecute(() -> {
					helper.assertTrue(heard(a, "Meet at the park"), "the text did not come back");
					helper.assertTrue(BbsHandler.state(b).log().stream().anyMatch(line -> line.startsWith("W1AAA: SP W1XYZ")), "B's traffic log: " + BbsHandler.state(b).log());
				})
				.thenSucceed();
	}

	/** The sysop console: posting works for whoever is at it; deleting and setup only for the owner. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 60)
	public static void onlyTheSysopDeletes(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, AT_B);
		FakePlayer owner = player(helper, "sysop");
		FakePlayer visitor = player(helper, "visitor");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(computer.isPowered(), "the computer did not come on"))
				.thenExecute(() -> {
					computer.files().setOwner(owner.getUUID());
					CompoundTag post = new CompoundTag();
					post.putString("to", "ALL");
					post.putString("subject", "Net");
					post.putString("body", "Tonight");
					post.putBoolean("bulletin", true);
					helper.assertFalse(app(computer, visitor, "post", post).contains("error"), "posting failed");
					CompoundTag status = app(computer, visitor, "status", new CompoundTag()).getCompound("data");
					helper.assertTrue(status.getList("messages", Tag.TAG_COMPOUND).size() == 1 && !status.getBoolean("sysop"), "status: " + status);
					CompoundTag delete = new CompoundTag();
					delete.putInt("n", 1);
					CompoundTag refused = app(computer, visitor, "delete", delete);
					helper.assertTrue("bbs_not_sysop".equals(refused.getString("error")), "a visitor deleted: " + refused);
					CompoundTag settings = new CompoundTag();
					settings.putInt("ssid", 4);
					helper.assertTrue("bbs_not_sysop".equals(app(computer, visitor, "settings", settings).getString("error")), "a visitor changed the setup");
					helper.assertFalse(app(computer, owner, "delete", delete).contains("error"), "the owner could not delete");
					helper.assertTrue(BbsHandler.mailbox(computer).messages().isEmpty(), "still there");
					CompoundTag file = computer.files().readNbt(BbsHandler.FILE);
					helper.assertTrue(file != null && PacketBbs.fromTag(file).messages().isEmpty(), "the disk still has it");
					helper.assertFalse(app(computer, owner, "settings", settings).contains("error"), "the owner could not set the SSID");
					helper.assertTrue(BbsHandler.state(computer).log().stream().anyMatch(line -> line.contains("KILLED MSG 1")), "not logged");
				})
				.thenSucceed();
	}
}
