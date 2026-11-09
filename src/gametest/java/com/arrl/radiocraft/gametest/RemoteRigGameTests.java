package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioInterfaceBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioAllBandBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerNetwork;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.apps.RigControlHandler;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * The computer network and RIGCTL remote control (2026-11-09): two computers on a data wire reach each other; a name
 * clash on the network is caught; a remote session tunes and keys the host's rig and locks out the host's own keyboard
 * until it takes over; OWNER-only and access codes refuse the wrong player; breaking the wire ends the session and lets
 * the PTT go.
 * <p>
 * Layout: the all-band rig at {@link #RIG}, its radio interface north of it, the host computer west of that; data wire
 * runs north from the host to the client computer at {@link #CLIENT}.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class RemoteRigGameTests {

	private static final BlockPos RIG = new BlockPos(3, 2, 6);
	private static final BlockPos INTERFACE = RIG.north();
	private static final BlockPos HOST = INTERFACE.west();
	private static final BlockPos CLIENT = new BlockPos(2, 2, 0);
	/** The wire from the host to the client; the one broken in the last test. */
	private static final BlockPos BREAK = new BlockPos(2, 2, 2);

	private static FakePlayer player(GameTestHelper helper, String name) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("remoterig-" + name).getBytes()), name));
		BlockPos at = helper.absolutePos(CLIENT.east(2));
		player.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
		return player;
	}

	private static RadioComputerBlockEntity computer(GameTestHelper helper, BlockPos pos, ComputerApp... apps) {
		helper.setBlock(pos, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(pos.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, pos, RadioComputerBlockEntity.class);
		for(ComputerApp app : apps)
			computer.install(app);
		return computer;
	}

	/** Two computers with data wire between them (the computers first, so the wire joins them). */
	private static RadioComputerBlockEntity[] wiredPair(GameTestHelper helper, ComputerApp... apps) {
		RadioComputerBlockEntity host = computer(helper, HOST, apps);
		RadioComputerBlockEntity client = computer(helper, CLIENT, apps);
		for(int z = HOST.getZ() - 1; z > CLIENT.getZ(); z--)
			GameTestUtil.placeWire(helper, new BlockPos(HOST.getX(), HOST.getY(), z), RadiocraftBlocks.DATA_WIRE.get());
		return new RadioComputerBlockEntity[] { host, client };
	}

	/** The rig and its radio interface beside the host. */
	private static HFRadioAllBandBlockEntity rig(GameTestHelper helper) {
		helper.setBlock(RIG, RadiocraftBlocks.ALL_BAND_RADIO.get());
		helper.setBlock(INTERFACE, RadiocraftBlocks.RADIO_INTERFACE.get());
		GameTestUtil.blockEntity(helper, INTERFACE, RadioInterfaceBlockEntity.class).setCallsign("W1REM", "gametest");
		return GameTestUtil.blockEntity(helper, RIG, HFRadioAllBandBlockEntity.class);
	}

	private static void powerOn(GameTestHelper helper) {
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, RIG, RadioNetworkObject.class);
		if(!networkObject.isPowered)
			GameTestUtil.blockEntity(helper, RIG, RadioBlockEntity.class).toggle();
		helper.assertTrue(networkObject.isPowered, "The rig did not power on");
	}

	private static CompoundTag call(RadioComputerBlockEntity computer, FakePlayer player, String action, CompoundTag data) {
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, action, data, new ComputerReply(player, 9, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	private static CompoundTag app(RadioComputerBlockEntity computer, FakePlayer player, String action, CompoundTag data) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", ComputerApps.RIG_CONTROL.id());
		wrapped.putString("action", action);
		wrapped.put("data", data);
		return call(computer, player, "app", wrapped);
	}

	private static void ok(GameTestHelper helper, CompoundTag reply, String what) {
		helper.assertFalse(reply.contains("error"), what + ": " + reply);
	}

	private static void refused(GameTestHelper helper, CompoundTag reply, String key, String what) {
		helper.assertTrue(key.equals(reply.getString("error")), what + ": expected " + key + ", got " + reply);
	}

	private static CompoundTag hostSettings(boolean access, boolean anyone) {
		CompoundTag data = new CompoundTag();
		data.putBoolean("access", access);
		data.putBoolean("anyone", anyone);
		return data;
	}

	private static CompoundTag connectTo(RadioComputerBlockEntity host, String code) {
		CompoundTag data = new CompoundTag();
		data.putString("id", host.getComputerId().toString());
		data.putString("code", code);
		return data;
	}

	private static CompoundTag ptt(boolean on) {
		CompoundTag data = new CompoundTag();
		data.putBoolean("on", on);
		return data;
	}

	/** Two computers on a data wire reach each other over WIRE; a third, wired to nothing, is on no network. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100)
	public static void twoComputersOnADataWireSeeEachOther(GameTestHelper helper) {
		RadioComputerBlockEntity[] pair = wiredPair(helper);
		RadioComputerBlockEntity alone = computer(helper, new BlockPos(12, 2, 12));
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					List<ComputerNetwork.Peer> fromHost = ComputerNetwork.peersNow(pair[0]);
					helper.assertTrue(fromHost.size() == 1 && fromHost.getFirst().computer() == pair[1] && fromHost.getFirst().link().wire(),
							"the host does not reach the client over wire: " + fromHost);
					List<ComputerNetwork.Peer> fromClient = ComputerNetwork.peersNow(pair[1]);
					helper.assertTrue(fromClient.size() == 1 && fromClient.getFirst().computer() == pair[0], "the client does not reach the host: " + fromClient);
					helper.assertTrue(ComputerNetwork.link(pair[1], pair[0]) != null && ComputerNetwork.link(pair[1], pair[0]).label().equals("WIRE"),
							"the link is not WIRE");
					helper.assertFalse(ComputerNetwork.onNetwork(alone), "a computer wired to nothing is on a network");
				})
				.thenSucceed();
	}

	/**
	 * A name clash on the network: the computer that boots later asks (the other keeps its name), a taken name is
	 * refused case aside both at the prompt and in SETUP, and a free one is taken.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100)
	public static void aNameClashIsDetected(GameTestHelper helper) {
		RadioComputerBlockEntity[] pair = wiredPair(helper);
		RadioComputerBlockEntity host = pair[0], client = pair[1];
		FakePlayer player = player(helper, "names");
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					host.files().setSettings(host.files().settings().withName("SHACK"));
					client.files().setSettings(client.files().settings().withName("Base"));
					ComputerNetwork.boot(host); // First on the network, with a name of its own.
					helper.assertFalse(host.needsNetworkName(), "a computer with a name nobody else has asks for one");
					client.files().setSettings(client.files().settings().withName("shack"));
					ComputerNetwork.boot(client); // Boots later onto the clash: it asks.
					helper.assertTrue(client.needsNetworkName(), "the computer booting onto a clash does not ask for a name");
					helper.assertFalse(host.needsNetworkName(), "the computer already there asks too");
					CompoundTag taken = new CompoundTag();
					taken.putString("name", "Shack");
					refused(helper, call(client, player, "sys.netname", taken), "netname_in_use", "a taken name at the prompt");
					CompoundTag empty = new CompoundTag();
					empty.putString("name", "");
					refused(helper, call(client, player, "sys.netname", empty), "netname_needed", "an empty name at the prompt");
					CompoundTag free = new CompoundTag();
					free.putString("name", "Outpost");
					ok(helper, call(client, player, "sys.netname", free), "a free name");
					helper.assertTrue(client.files().settings().name().equals("Outpost") && !client.needsNetworkName(), "the free name was not taken");
					CompoundTag setup = new CompoundTag();
					setup.put("settings", host.files().settings().withName("OUTPOST").toTag());
					refused(helper, call(host, player, "sys.settings", setup), "netname_in_use", "SETUP took a name in use on the network");
					helper.assertTrue(ComputerNetwork.name(host).equals("SHACK"), "SETUP changed the name anyway");
				})
				.thenSucceed();
	}

	/**
	 * A remote session drives the host's rig: the client tunes it and keys its PTT; the host's own keyboard is refused
	 * while it lasts, and taking over ends it and lets the PTT go.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void aRemoteSessionDrivesTheHostsRig(GameTestHelper helper) {
		HFRadioAllBandBlockEntity radio = rig(helper);
		RadioComputerBlockEntity[] pair = wiredPair(helper, ComputerApps.RIG_CONTROL);
		RadioComputerBlockEntity host = pair[0], client = pair[1];
		FakePlayer remote = player(helper, "remote"), local = player(helper, "local");
		helper.startSequence()
				.thenExecute(() -> powerOn(helper))
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertTrue(host.isPowered() && client.isPowered(), "the computers did not start");
					ok(helper, app(host, local, "host", hostSettings(true, true)), "remote access on");
					ListTag hosts = app(client, remote, "hosts", new CompoundTag()).getCompound("data").getList("hosts", Tag.TAG_COMPOUND);
					helper.assertTrue(hosts.size() == 1 && hosts.getCompound(0).getString("id").equals(host.getComputerId().toString())
							&& hosts.getCompound(0).getString("link").equals("WIRE"), "the host is not listed: " + hosts);
					ok(helper, app(client, remote, "connect", connectTo(host, "")), "connect");
					helper.assertTrue(RigControlHandler.state(host).session() != null && RigControlHandler.state(client).remote() != null, "no session");
					ok(helper, app(client, remote, "tune", tagHz(14_074_000.0D)), "remote tune");
					helper.assertTrue(radio.getBand().name().equals("20m") && radio.getFrequency() == 14_074_000.0F,
							"the host's rig was not tuned: " + radio.getBand().name() + " " + radio.getFrequency());
					radio.setSSBEnabled(true);
					ok(helper, app(client, remote, "ptt", ptt(true)), "remote PTT");
					helper.assertTrue(radio.isPTTDown(), "the remote PTT did not key the host's rig");
					refused(helper, app(host, local, "tune", tagHz(14_200_000.0D)), "rig_remote_in_use", "the host's keyboard tuned during a remote session");
					helper.assertTrue(radio.getFrequency() == 14_074_000.0F, "the host's keyboard moved the dial");
					CompoundTag status = app(client, remote, "status", new CompoundTag()).getCompound("data");
					helper.assertTrue(status.getCompound("remote").contains("to"), "the client's status does not say it drives a remote rig: " + status);
					ok(helper, app(host, local, "takeover", new CompoundTag()), "take over");
					helper.assertTrue(RigControlHandler.state(host).session() == null && RigControlHandler.state(client).remote() == null, "taking over did not end the session");
					helper.assertFalse(radio.isPTTDown(), "taking over did not let go of the PTT");
					ok(helper, app(host, local, "tune", tagHz(14_200_000.0D)), "the host's keyboard after taking over");
				})
				.thenSucceed();
	}

	/** OWNER-only refuses another player; a code keeps out who does not know it. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void ownerOnlyRefusesAnotherPlayer(GameTestHelper helper) {
		rig(helper);
		RadioComputerBlockEntity[] pair = wiredPair(helper, ComputerApps.RIG_CONTROL);
		RadioComputerBlockEntity host = pair[0], client = pair[1];
		FakePlayer owner = player(helper, "owner"), guest = player(helper, "guest");
		helper.startSequence()
				.thenExecute(() -> powerOn(helper))
				.thenIdle(2)
				.thenExecute(() -> {
					host.files().setOwner(owner.getUUID());
					refused(helper, app(host, guest, "host", hostSettings(true, true)), "remote_owner_settings", "a guest turned remote access on");
					ok(helper, app(host, owner, "host", hostSettings(true, false)), "OWNER only");
					refused(helper, app(client, guest, "connect", connectTo(host, "")), "remote_owner_only", "a guest connected to an OWNER-only host");
					helper.assertTrue(RigControlHandler.state(host).session() == null, "a refused connection left a session");
					ok(helper, app(client, owner, "connect", connectTo(host, "")), "the owner connects");
					ok(helper, app(client, owner, "disconnect", new CompoundTag()), "disconnect");
					helper.assertTrue(RigControlHandler.state(host).session() == null, "disconnecting left the session");
					// Anyone with the code.
					ok(helper, app(host, owner, "host", hostSettings(true, true)), "ANYONE WITH CODE");
					CompoundTag code = new CompoundTag();
					code.putString("code", "73DE");
					ok(helper, app(host, owner, "code", code), "set the code");
					refused(helper, app(client, guest, "connect", connectTo(host, "")), "remote_need_code", "no code given");
					refused(helper, app(client, guest, "connect", connectTo(host, "88")), "remote_bad_code", "a wrong code");
					ok(helper, app(client, guest, "connect", connectTo(host, "73DE")), "the right code");
					helper.assertTrue(RigControlHandler.state(host).session() != null
							&& RigControlHandler.state(host).session().player().equals(guest.getUUID()), "the guest's session is not there");
				})
				.thenSucceed();
	}

	/** Breaking the data wire ends the remote session and lets the host's PTT go. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 300)
	public static void breakingTheWireEndsTheSession(GameTestHelper helper) {
		HFRadioAllBandBlockEntity radio = rig(helper);
		RadioComputerBlockEntity[] pair = wiredPair(helper, ComputerApps.RIG_CONTROL);
		RadioComputerBlockEntity host = pair[0], client = pair[1];
		FakePlayer remote = player(helper, "wirecut");
		helper.startSequence()
				.thenExecute(() -> powerOn(helper))
				.thenIdle(2)
				.thenExecute(() -> {
					ok(helper, app(host, remote, "host", hostSettings(true, true)), "remote access on");
					ok(helper, app(client, remote, "connect", connectTo(host, "")), "connect");
					ok(helper, app(client, remote, "tune", tagHz(14_074_000.0D)), "remote tune");
					radio.setSSBEnabled(true);
					ok(helper, app(client, remote, "ptt", ptt(true)), "remote PTT");
					helper.assertTrue(radio.isPTTDown(), "the remote PTT did not key");
					helper.setBlock(BREAK, Blocks.AIR); // The wire is cut.
				})
				.thenWaitUntil(() -> {
					helper.assertTrue(RigControlHandler.state(host).session() == null, "the session outlived the wire");
					helper.assertTrue(RigControlHandler.state(client).remote() == null, "the client still thinks it drives the host");
					helper.assertFalse(radio.isPTTDown(), "the PTT is still keyed after the link dropped");
				})
				.thenExecute(() -> refused(helper, app(client, remote, "connect", connectTo(host, "")), "remote_not_reached",
						"connected again with no wire"))
				.thenSucceed();
	}

	private static CompoundTag tagHz(double hz) {
		CompoundTag tag = new CompoundTag();
		tag.putDouble("hz", hz);
		return tag;
	}
}
