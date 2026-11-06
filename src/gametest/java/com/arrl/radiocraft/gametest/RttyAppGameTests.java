package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioInterfaceBlockEntity;
import com.arrl.radiocraft.common.blockentities.TeletypeBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.RttyCopy;
import com.arrl.radiocraft.common.computer.apps.RttyHandler;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The RTTY program (Computer apps 2) over the air: two computers, each on a VHF base station's radio interface (bare
 * connectors two blocks apart; RTTY goes through any radio, the mod's own stations are on HF but the path is the same),
 * type to each other; a computer's RTTY prints on a Teletype Machine; macros expand; the receive window saves to disk.
 * Each test on its own frequency.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class RttyAppGameTests {

	private static final BlockPos RADIO_A = new BlockPos(3, 2, 6);
	private static final BlockPos RADIO_B = RADIO_A.east(2);
	private static final BlockPos AT_A = RADIO_A.north().west(), AT_B = RADIO_B.north().east();

	private static FakePlayer player(GameTestHelper helper, String name) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("rttyapp-" + name).getBytes()), name));
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

	private static CompoundTag app(RadioComputerBlockEntity computer, FakePlayer player, String action, CompoundTag data) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", ComputerApps.RTTY.id());
		wrapped.putString("action", action);
		wrapped.put("data", data);
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, "app", wrapped, new ComputerReply(player, 7, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	private static CompoundTag tag(String key, String value) {
		CompoundTag tag = new CompoundTag();
		tag.putString(key, value);
		return tag;
	}

	private static boolean printed(RadioComputerBlockEntity computer, String from, String text) {
		return RttyHandler.state(computer).copy().lines().stream().anyMatch(line -> line.from().equals(from) && line.text().equals(text));
	}

	/** A line typed at one computer prints at the other under its callsign, and in its own window as our copy. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 400)
	public static void rttyGoesComputerToComputer(GameTestHelper helper) {
		station(helper, RADIO_A, 145_150, "W1RTA");
		station(helper, RADIO_B, 145_150, "W1RTB");
		RadioComputerBlockEntity a = computer(helper, AT_A, ComputerApps.RTTY);
		RadioComputerBlockEntity b = computer(helper, AT_B, ComputerApps.RTTY);
		FakePlayer player = player(helper, "rtty1");
		helper.startSequence()
				.thenExecute(() -> {
					powerOn(helper, RADIO_A);
					powerOn(helper, RADIO_B);
				})
				.thenIdle(1)
				.thenExecute(() -> {
					CompoundTag reply = app(a, player, "type", tag("text", "cq cq de w1rta\n"));
					helper.assertFalse(reply.contains("error"), "A could not type: " + reply);
					helper.assertTrue(RttyHandler.state(a).pending().equals("CQ CQ DE W1RTA\n"), "A's buffer: " + RttyHandler.state(a).pending());
				})
				.thenWaitUntil(() -> helper.assertTrue(printed(b, "W1RTA", "CQ CQ DE W1RTA"), "B printed " + RttyHandler.state(b).copy().lines()))
				.thenExecute(() -> {
					helper.assertTrue(printed(a, "W1RTA", "CQ CQ DE W1RTA") && RttyHandler.state(a).copy().lines().getFirst().own(), "A's local copy: " + RttyHandler.state(a).copy().lines());
					helper.assertTrue(RttyHandler.state(a).pending().isEmpty(), "A's buffer did not drain");
				})
				.thenSucceed();
	}

	/** The computer's RTTY is the same as the Teletype Machine's: a teletype on another station prints it. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 400)
	public static void aTeletypePrintsTheComputer(GameTestHelper helper) {
		station(helper, RADIO_A, 145_170, "W1RTC");
		helper.setBlock(RADIO_B, RadiocraftBlocks.VHF_BASE_STATION.get());
		GameTestUtil.blockEntity(helper, RADIO_B, RadioBlockEntity.class).setFrequency(145_170_000.0F);
		helper.setBlock(RADIO_B.north(), RadiocraftBlocks.TELETYPE.get());
		TeletypeBlockEntity teletype = GameTestUtil.blockEntity(helper, RADIO_B.north(), TeletypeBlockEntity.class);
		teletype.insertPaper(new ItemStack(Items.PAPER));
		RadioComputerBlockEntity a = computer(helper, AT_A, ComputerApps.RTTY);
		helper.startSequence()
				.thenExecute(() -> {
					powerOn(helper, RADIO_A);
					powerOn(helper, RADIO_B);
				})
				.thenIdle(1)
				.thenExecute(() -> helper.assertTrue(RttyHandler.type(a, "RYRY 599\n") == null, "A could not type"))
				.thenWaitUntil(() -> helper.assertTrue(teletype.getRttyLines().contains("RYRY 599"), "The teletype printed " + teletype.getRttyLines()))
				.thenSucceed();
	}

	/** F3's CQ macro goes out with the station's callsign; the window saves to RTTY0001.TXT; no interface, no sending. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 400)
	public static void macrosAndSaving(GameTestHelper helper) {
		station(helper, RADIO_A, 145_190, "W1RTD");
		RadioComputerBlockEntity a = computer(helper, AT_A, ComputerApps.RTTY);
		RadioComputerBlockEntity alone = computer(helper, new BlockPos(9, 2, 1), ComputerApps.RTTY);
		FakePlayer player = player(helper, "rtty3");
		helper.startSequence()
				.thenExecute(() -> powerOn(helper, RADIO_A))
				.thenIdle(1)
				.thenExecute(() -> {
					CompoundTag n = new CompoundTag();
					n.putInt("n", 0);
					helper.assertFalse(app(a, player, "macro", n).contains("error"), "the CQ macro did not go");
					helper.assertTrue(RttyHandler.state(a).pending().equals(RttyCopy.expand(RttyCopy.DEFAULT_MACROS.getFirst(), "W1RTD", "")),
							"the CQ macro: " + RttyHandler.state(a).pending());
					CompoundTag refused = app(alone, player, "type", tag("text", "TEST"));
					helper.assertTrue(refused.getString("error").equals("station_no_interface"), "a computer with no interface typed: " + refused);
				})
				.thenWaitUntil(() -> helper.assertTrue(RttyHandler.state(a).pending().isEmpty(), "the macro is still going out"))
				.thenExecute(() -> {
					CompoundTag saved = app(a, player, "save", new CompoundTag());
					helper.assertFalse(saved.contains("error"), "save: " + saved);
					String text = a.files().readText("RTTY0001.TXT");
					helper.assertTrue(text != null && text.contains("> W1RTD: CQ CQ CQ DE W1RTD W1RTD W1RTD K"), "the saved file: " + text);
				})
				.thenSucceed();
	}
}
