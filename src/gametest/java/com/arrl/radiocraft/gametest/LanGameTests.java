package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.ComputerRedstoneBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.mesh.ScadaMasterBlockEntity;
import com.arrl.radiocraft.common.blockentities.mesh.ScadaRtuBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.ComputerSettings;
import com.arrl.radiocraft.common.computer.apps.RelayHandler;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicense;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenses;
import com.arrl.radiocraft.common.radio.mesh.MeshNode;
import com.arrl.radiocraft.common.radio.mesh.MeshRole;
import com.arrl.radiocraft.common.radio.mesh.RadioMesh;
import com.arrl.radiocraft.common.radio.scada.Scada;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * The 2026-11-13 batch on the computers and the mesh: LAN REDSTONE (named levels, and who may set a block from another
 * computer), LAN RELAY (a server keeps the room; it needs background services), and SCADA trends and an unpowered RTU.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class LanGameTests {

	private static final BlockPos A = new BlockPos(2, 2, 2), B = new BlockPos(3, 2, 2);

	private static FakePlayer player(GameTestHelper helper, String name, BlockPos near) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("lan-" + name).getBytes()), name));
		BlockPos at = helper.absolutePos(near.south(2));
		player.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
		return player;
	}

	private static RadioComputerBlockEntity computer(GameTestHelper helper, BlockPos at, String name) {
		helper.setBlock(at, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(at.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, at, RadioComputerBlockEntity.class);
		ComputerSettings settings = computer.files().settings();
		computer.files().setSettings(settings.withName(name));
		computer.settingsChanged();
		return computer;
	}

	private static void services(RadioComputerBlockEntity computer, boolean on) {
		computer.files().setSettings(computer.files().settings().withBackgroundServices(on));
		computer.settingsChanged();
	}

	/** Runs one program request as its screen would; the reply's tag ({@code data}, maybe {@code error}). */
	private static CompoundTag app(RadioComputerBlockEntity computer, FakePlayer player, ComputerApp app, String action, CompoundTag data) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", app.id());
		wrapped.putString("action", action);
		wrapped.put("data", data);
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, "app", wrapped, new ComputerReply(player, 7, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	private static CompoundTag at(ComputerRedstoneBlockEntity block, String computer) {
		CompoundTag data = new CompoundTag();
		data.putString("computer", computer);
		data.putLong("pos", block.getBlockPos().asLong());
		return data;
	}

	/**
	 * A redstone block touching ALPHA: ALPHA names level 4 "Door Open" and sets it; BRAVO, beside it on the LAN, may
	 * not set it (and does not see it) until ALPHA lets BRAVO in; then BRAVO's pulse turns it on and off again.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void redstoneLevelsAndLanAccess(GameTestHelper helper) {
		RadioComputerBlockEntity alpha = computer(helper, A, "ALPHA");
		RadioComputerBlockEntity bravo = computer(helper, B, "BRAVO");
		BlockPos redstoneAt = A.west();
		helper.setBlock(redstoneAt, RadiocraftBlocks.COMPUTER_REDSTONE.get());
		ComputerRedstoneBlockEntity block = GameTestUtil.blockEntity(helper, redstoneAt, ComputerRedstoneBlockEntity.class);
		FakePlayer ann = player(helper, "Ann", A), bob = player(helper, "Bob", B);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					CompoundTag entry = at(block, "");
					entry.putString("label", "Door Open");
					entry.putInt("level", 4);
					helper.assertFalse(app(alpha, ann, ComputerApps.REDSTONE, "entry", entry).contains("error"), "naming a level failed");
					helper.assertTrue(block.describe(4).equals("Door Open (4)"), "level 4 reads " + block.describe(4));
					CompoundTag set = at(block, "");
					set.putInt("level", 4);
					helper.assertFalse(app(alpha, ann, ComputerApps.REDSTONE, "set", set).contains("error"), "setting it at its own computer failed");
					helper.assertTrue(block.getOutput() == 4, "it gives out " + block.getOutput());

					CompoundTag status = app(bravo, bob, ComputerApps.REDSTONE, "status", new CompoundTag()).getCompound("data");
					helper.assertTrue(status.getList("remote", Tag.TAG_COMPOUND).isEmpty(), "BRAVO sees a block it may not set");
					CompoundTag remote = at(block, "ALPHA");
					remote.putInt("level", 0);
					helper.assertTrue(app(bravo, bob, ComputerApps.REDSTONE, "set", remote).getString("error").equals("redstone_not_allowed"), "BRAVO set it unasked");
					helper.assertTrue(block.getOutput() == 4, "BRAVO changed it");

					CompoundTag access = at(block, "");
					access.putString("players", "");
					access.putString("computers", "bravo");
					app(alpha, ann, ComputerApps.REDSTONE, "access", access);
					ListTag seen = app(bravo, bob, ComputerApps.REDSTONE, "status", new CompoundTag()).getCompound("data").getList("remote", Tag.TAG_COMPOUND);
					helper.assertTrue(seen.size() == 1 && seen.getCompound(0).getString("outText").equals("Door Open (4)"), "BRAVO does not see it: " + seen);
					CompoundTag pulse = at(block, "ALPHA");
					pulse.putInt("level", 15);
					pulse.putBoolean("pulse", true);
					helper.assertFalse(app(bravo, bob, ComputerApps.REDSTONE, "set", pulse).contains("error"), "BRAVO's pulse was refused");
					helper.assertTrue(block.getOutput() == 15, "the pulse gives out " + block.getOutput());
				})
				.thenIdle(ComputerRedstoneBlockEntity.PULSE_TICKS + 2)
				.thenExecute(() -> helper.assertTrue(block.getOutput() == 0, "the pulse did not end: " + block.getOutput()))
				.thenSucceed();
	}

	/** It reads the redstone coming in while it is off. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 60)
	public static void redstoneBlockReadsItsInput(GameTestHelper helper) {
		BlockPos at = new BlockPos(2, 2, 2);
		helper.setBlock(at, RadiocraftBlocks.COMPUTER_REDSTONE.get());
		helper.setBlock(at.east(), Blocks.REDSTONE_BLOCK);
		ComputerRedstoneBlockEntity block = GameTestUtil.blockEntity(helper, at, ComputerRedstoneBlockEntity.class);
		helper.succeedWhen(() -> helper.assertTrue(block.getInput() == 15, "it reads " + block.getInput()));
	}

	/**
	 * ALPHA serves a room (background services on); Bob at BRAVO names himself, finds it, says something, and is in the
	 * room; ALPHA keeps the line. With ALPHA's background services off the room is gone.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void relayServerKeepsTheRoom(GameTestHelper helper) {
		RadioComputerBlockEntity alpha = computer(helper, A, "ALPHA");
		RadioComputerBlockEntity bravo = computer(helper, B, "BRAVO");
		FakePlayer ann = player(helper, "Ann", A), bob = player(helper, "Bob", B);
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					services(alpha, true);
					CompoundTag serve = new CompoundTag();
					serve.putBoolean("on", true);
					helper.assertFalse(app(alpha, ann, ComputerApps.RELAY, "serve", serve).contains("error"), "ALPHA could not serve");
				})
				.thenWaitUntil(() -> helper.assertTrue(RelayHandler.serves(alpha), "ALPHA is not serving (powered " + alpha.isPowered() + ")"))
				.thenExecute(() -> {
					CompoundTag say = new CompoundTag();
					say.putString("server", "");
					say.putString("text", "hello");
					helper.assertTrue(app(bravo, bob, ComputerApps.RELAY, "say", say).getString("error").equals("relay_no_name"), "Bob spoke with no name");
					CompoundTag name = new CompoundTag();
					name.putString("name", "bob");
					helper.assertFalse(app(bravo, bob, ComputerApps.RELAY, "name", name).contains("error"), "naming Bob failed");
					helper.assertFalse(app(bravo, bob, ComputerApps.RELAY, "say", say).contains("error"), "Bob could not speak");
					CompoundTag status = app(bravo, bob, ComputerApps.RELAY, "status", new CompoundTag()).getCompound("data");
					helper.assertTrue(status.getString("room").equals("ALPHA"), "Bob is not in ALPHA's room: '" + status.getString("room") + "'");
					ListTag lines = status.getList("lines", Tag.TAG_COMPOUND);
					helper.assertTrue(lines.size() == 1 && lines.getCompound(0).getString("text").equals("hello")
							&& lines.getCompound(0).getString("computer").equals("BRAVO"), "the room has " + lines);
					ListTag online = status.getList("online", Tag.TAG_STRING);
					helper.assertTrue(online.size() == 1 && online.getString(0).equals("bob@BRAVO"), "online: " + online);
					services(alpha, false);
					helper.assertTrue(app(bravo, bob, ComputerApps.RELAY, "status", new CompoundTag()).getCompound("data").getList("servers", Tag.TAG_STRING).isEmpty(),
							"the room is still open with ALPHA's services off");
				})
				.thenSucceed();
	}

	private static CompoundTag mail(String to, String subject, String body) {
		CompoundTag t = new CompoundTag();
		t.putString("to", to);
		t.putString("subject", subject);
		t.putString("body", body);
		return t;
	}

	private static void name(RadioComputerBlockEntity computer, FakePlayer player, String name, GameTestHelper helper) {
		CompoundTag t = new CompoundTag();
		t.putString("name", name);
		helper.assertFalse(app(computer, player, ComputerApps.RELAY, "name", t).contains("error"), "naming " + name + " failed");
	}

	/**
	 * LAN EMAIL: Ann at ALPHA mails bob@BRAVO and it is there at once, read and replied to; mail for CHARLIE, not yet
	 * on the LAN, waits in ALPHA's outbox and is delivered when CHARLIE joins.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 300)
	public static void emailDeliversAndWaits(GameTestHelper helper) {
		RadioComputerBlockEntity alpha = computer(helper, A, "ALPHA");
		RadioComputerBlockEntity bravo = computer(helper, B, "BRAVO");
		FakePlayer ann = player(helper, "Ann", A), bob = player(helper, "Bob", B), carol = player(helper, "Carol", A.west());
		BlockPos c = A.west();
		RadioComputerBlockEntity[] charlie = new RadioComputerBlockEntity[1];
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertTrue(app(alpha, ann, ComputerApps.EMAIL, "send", mail("bob@BRAVO", "hi", "x")).getString("error").equals("relay_no_name"),
							"Ann sent mail with no username");
					name(alpha, ann, "ann", helper);
					name(bravo, bob, "bob", helper);
					helper.assertTrue(app(alpha, ann, ComputerApps.EMAIL, "send", mail("bob BRAVO", "hi", "x")).getString("error").equals("email_bad_address"),
							"a bad address was taken");
				})
				.thenExecute(() -> services(bravo, true)) // Nobody at BRAVO: it takes mail with its background services on.
				.thenWaitUntil(() -> helper.assertTrue(bravo.takesMail() && com.arrl.radiocraft.common.computer.ComputerNetwork.peers(alpha).stream()
						.anyMatch(peer -> peer.computer() == bravo && peer.link().up()), "BRAVO is not on ALPHA's LAN yet (takes mail " + bravo.takesMail() + ")"))
				.thenExecute(() -> {
					CompoundTag sent = app(alpha, ann, ComputerApps.EMAIL, "send", mail("bob@bravo", "QSO tonight?", "40m at 8."));
					helper.assertFalse(sent.contains("error") || sent.getCompound("data").getBoolean("waiting"), "mail to BRAVO did not go at once: " + sent);
				})
				.thenExecute(() -> {
					ListTag inbox = app(bravo, bob, ComputerApps.EMAIL, "status", new CompoundTag()).getCompound("data").getList("inbox", Tag.TAG_COMPOUND);
					helper.assertTrue(inbox.size() == 1 && inbox.getCompound(0).getString("from").equals("ann@ALPHA") && !inbox.getCompound(0).getBoolean("read"),
							"Bob's inbox: " + inbox);
					CompoundTag id = new CompoundTag();
					id.putLong("id", inbox.getCompound(0).getLong("id"));
					CompoundTag read = app(bravo, bob, ComputerApps.EMAIL, "read", id).getCompound("data");
					helper.assertTrue(read.getString("body").equals("40m at 8.") && read.getString("to").equals("bob@bravo"), "read " + read);
					helper.assertTrue(app(alpha, ann, ComputerApps.EMAIL, "status", new CompoundTag()).getCompound("data").getList("inbox", Tag.TAG_COMPOUND).isEmpty(),
							"Bob's mail landed at ALPHA too");

					CompoundTag waiting = app(alpha, ann, ComputerApps.EMAIL, "send", mail("carol@CHARLIE", "welcome", "glad you are here"));
					helper.assertTrue(waiting.getCompound("data").getBoolean("waiting"), "mail to a computer not on the LAN went: " + waiting);
					helper.assertTrue(app(alpha, ann, ComputerApps.EMAIL, "status", new CompoundTag()).getCompound("data").getList("outbox", Tag.TAG_COMPOUND).size() == 1,
							"the mail is not waiting in ALPHA's outbox");
					charlie[0] = computer(helper, c, "CHARLIE");
				})
				.thenIdle(2)
				.thenExecute(() -> {
					name(charlie[0], carol, "carol", helper);
					services(charlie[0], true); // CHARLIE takes mail; ALPHA, its services on, keeps trying.
					services(alpha, true);
				})
				.thenWaitUntil(() -> {
					ListTag inbox = app(charlie[0], carol, ComputerApps.EMAIL, "status", new CompoundTag()).getCompound("data").getList("inbox", Tag.TAG_COMPOUND);
					helper.assertTrue(inbox.size() == 1 && inbox.getCompound(0).getString("subject").equals("welcome"), "Carol's inbox: " + inbox);
				})
				.thenExecute(() -> helper.assertTrue(app(alpha, ann, ComputerApps.EMAIL, "status", new CompoundTag()).getCompound("data")
						.getList("outbox", Tag.TAG_COMPOUND).isEmpty(), "the delivered mail is still in ALPHA's outbox"))
				.thenSucceed();
	}

	private static CommercialLicense license(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		return CommercialLicenses.of(server).issue(server, Band.VHF_COMMERCIAL, "tester", "", 1, new Random(helper.getLevel().random.nextLong()));
	}

	/** The master keeps each RTU's readings for trends, and its own inputs too. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 400)
	public static void scadaKeepsTrends(GameTestHelper helper) {
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
		master.setRules(server, List.of("POLL 1"));
		helper.succeedWhen(() -> {
			Scada.Seen seen = scada.master(master.globalPos()).rtus.get(rtu.getAddress());
			helper.assertTrue(seen != null && seen.trend.size() >= 2, "fewer than two readings kept");
			String north = seen.trend.face(Direction.NORTH.get3DDataValue());
			helper.assertTrue(north.endsWith("F"), "the north face's trend is " + north);
			helper.assertTrue(seen.trend.face(Direction.SOUTH.get3DDataValue()).chars().allMatch(c -> c == '-'), "an OFF face has readings");
			helper.assertTrue(scada.master(master.globalPos()).trend.size() >= 1, "the master's own trend is empty");
			helper.assertTrue(scada.master(master.globalPos()).powered && scada.rtu(rtu.globalPos()).powered, "a device shows no power with power not required");
		});
	}

	/** An RTU with no power says nothing: polled, it goes COMM FAIL. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 300)
	public static void unpoweredRtuGoesQuiet(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommercialLicense license = license(helper);
		BlockPos masterAt = new BlockPos(4, 1, 4);
		helper.setBlock(masterAt, RadiocraftBlocks.SCADA_MASTER.get());
		ScadaMasterBlockEntity master = GameTestUtil.blockEntity(helper, masterAt, ScadaMasterBlockEntity.class);
		master.setLicense(server, license);
		master.setRules(server, List.of("POLL 1"));
		GlobalPos near = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(masterAt.east(20))); // In reach, but no block: only in the mesh.
		RadioMesh.of(server).put(new MeshNode(near, MeshRole.RTU, license.outputHz(), 5, false, "", license.callsign()));
		Scada.of(server).rtu(near).powered = false;
		helper.succeedWhen(() -> {
			Scada.Seen seen = Scada.of(server).master(master.globalPos()).rtus.get(5);
			helper.assertTrue(seen != null && seen.polls >= 3 && seen.replies == 0 && seen.commFail, "an unpowered RTU answered or was not polled");
			RadioMesh.of(server).remove(near);
		});
	}

	/** FIRE ALARM's box map (2026-11-13): the program gets a map picture and each box it knows, where the mesh has it. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void fireAlarmMapPlacesTheBox(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommercialLicense license = license(helper);
		BlockPos controllerAt = new BlockPos(2, 2, 2), boxAt = new BlockPos(8, 2, 2), stationAt = new BlockPos(9, 2, 2);
		helper.setBlock(controllerAt, RadiocraftBlocks.FIRE_ALARM_CONTROLLER.get());
		helper.setBlock(boxAt, RadiocraftBlocks.FIRE_ALARM_BOX.get());
		helper.setBlock(stationAt, RadiocraftBlocks.PULL_STATION.get().defaultBlockState().setValue(com.arrl.radiocraft.common.blocks.mesh.PullStationBlock.PULLED, true));
		var controller = GameTestUtil.blockEntity(helper, controllerAt, com.arrl.radiocraft.common.blockentities.mesh.FireAlarmControllerBlockEntity.class);
		var box = GameTestUtil.blockEntity(helper, boxAt, com.arrl.radiocraft.common.blockentities.mesh.FireAlarmBoxBlockEntity.class);
		controller.setLicense(server, license);
		controller.addPaper(10);
		box.setLicense(server, license);
		RadioComputerBlockEntity computer = computer(helper, controllerAt.south(), "FIREDESK"); // Touching the controller.
		computer.install(ComputerApps.FIRE_ALARM);
		FakePlayer ann = player(helper, "Ann", controllerAt.south());
		helper.succeedWhen(() -> {
			CompoundTag map = app(computer, ann, ComputerApps.FIRE_ALARM, "map", new CompoundTag()).getCompound("data");
			helper.assertTrue(map.getByteArray("pixels").length == com.arrl.radiocraft.common.radio.data.AprsMapPainter.SIZE * com.arrl.radiocraft.common.radio.data.AprsMapPainter.SIZE,
					"no map picture");
			ListTag boxes = map.getList("boxes", Tag.TAG_COMPOUND);
			BlockPos abs = helper.absolutePos(boxAt);
			helper.assertTrue(boxes.size() == 1 && boxes.getCompound(0).getInt("box") == box.getAddress()
					&& boxes.getCompound(0).getInt("x") == abs.getX() && boxes.getCompound(0).getInt("z") == abs.getZ(), "boxes on the map: " + boxes);
		});
	}
}
