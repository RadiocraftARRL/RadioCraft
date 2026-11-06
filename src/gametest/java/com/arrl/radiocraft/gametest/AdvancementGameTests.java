package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.capabilities.LicenseClass;
import com.arrl.radiocraft.api.capabilities.PlayerCallsignData;
import com.arrl.radiocraft.common.advancements.AdvancementData;
import com.arrl.radiocraft.common.advancements.AdvancementEvents;
import com.arrl.radiocraft.common.advancements.ContactTracker;
import com.arrl.radiocraft.common.advancements.RadiocraftAdvancements;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioInterfaceBlockEntity;
import com.arrl.radiocraft.common.blockentities.TeletypeBlockEntity;
import com.arrl.radiocraft.common.computer.Awards;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerAppHandlers;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.NetLog;
import com.arrl.radiocraft.common.computer.apps.AwardsHandler;
import com.arrl.radiocraft.common.computer.apps.BbsHandler;
import com.arrl.radiocraft.common.computer.apps.Ft8Handler;
import com.arrl.radiocraft.common.data.PlayerCallsignSavedData;
import com.arrl.radiocraft.common.fcc.FccGear;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.QslAlbumItem;
import com.arrl.radiocraft.common.qsl.QslCard;
import com.arrl.radiocraft.common.qsl.QslCards;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.common.radio.ft8.Ft8Sequencer;
import com.mojang.authlib.GameProfile;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The advancements (the advancements batch, 2026-11-06): the event trigger and the advancement files, the verified
 * two-way contact and what it earns, and each server-side hook that can be driven from a test. The rules of the
 * contact book itself are unit tested ({@code ContactBookTest}); Repeater Owner, Storm Spotter / Take Shelter Now,
 * Chart Topper and Fleet Manager are checked in the repeater, weather alert, WEFAX and office apps tests.
 * <p>
 * No test here changes the world's time, solar weather or aurora (shared by every test running at once): Grey Line,
 * Into the Static and Northern Lights are left to the unit tests and a playtest.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class AdvancementGameTests {

	private static final float HF = 14_230_000.0F;
	private static final float VHF = 146_550_000.0F;

	/** A fake player the advancement code finds as if online, at {@code pos} (relative), in this plot. */
	private static FakePlayer player(GameTestHelper helper, String name, BlockPos pos) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("adv-" + name).getBytes()), name));
		player.getInventory().clearContent();
		BlockPos at = helper.absolutePos(pos);
		player.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
		RadiocraftAdvancements.registerTestPlayer(player);
		return player;
	}

	private static void done(GameTestHelper helper, FakePlayer... players) {
		for(FakePlayer player : players)
			RadiocraftAdvancements.unregisterTestPlayer(player);
		helper.succeed();
	}

	private static void assertHas(GameTestHelper helper, FakePlayer player, String event) {
		helper.assertTrue(RadiocraftAdvancements.has(player, event), player.getGameProfile().getName() + " does not have " + event);
	}

	private static void assertHasNot(GameTestHelper helper, FakePlayer player, String event) {
		helper.assertFalse(RadiocraftAdvancements.has(player, event), player.getGameProfile().getName() + " has " + event);
	}

	private static CompoundTag call(RadioComputerBlockEntity computer, FakePlayer player, String action, CompoundTag data) {
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, action, data, new ComputerReply(player, 5, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	private static CompoundTag app(RadioComputerBlockEntity computer, FakePlayer player, ComputerApp app, String action, CompoundTag data) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", app.id());
		wrapped.putString("action", action);
		wrapped.put("data", data);
		return call(computer, player, "app", wrapped);
	}

	private static RadioComputerBlockEntity computer(GameTestHelper helper, BlockPos pos, ComputerApp... apps) {
		helper.setBlock(pos, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(pos.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, pos, RadioComputerBlockEntity.class);
		for(ComputerApp app : apps)
			computer.install(app);
		return computer;
	}

	// -------------------- THE TRIGGER AND THE FILES --------------------

	/**
	 * Every advancement of the tree is loaded with the right parent, and those granted from code have the event of their
	 * own id. With a real ServerPlayer (fake players never make advancement progress in NeoForge), granting the event
	 * completes the advancement; an offline player's grant waits for their login.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void theEventGrantsAndWaitsForLogin(GameTestHelper helper) {
		var manager = helper.getLevel().getServer().getAdvancements();
		for(com.arrl.radiocraft.datagen.RadiocraftAdvancementProvider.Def def : com.arrl.radiocraft.datagen.RadiocraftAdvancementProvider.TREE) {
			net.minecraft.advancements.AdvancementHolder holder = manager.get(Radiocraft.id(def.id()));
			helper.assertTrue(holder != null, "radiocraft:" + def.id() + " is not loaded");
			helper.assertTrue(holder.value().display().isPresent() && holder.value().display().get().isHidden() == def.hidden(), def.id() + ": display or hidden flag wrong");
			helper.assertTrue(def.parent() == null ? holder.value().parent().isEmpty() : holder.value().parent().equals(java.util.Optional.of(Radiocraft.id(def.parent()))),
					def.id() + " has parent " + holder.value().parent());
			boolean byEvent = holder.value().criteria().values().stream().anyMatch(c -> c.triggerInstance() instanceof com.arrl.radiocraft.common.advancements.RadioEventTrigger.TriggerInstance t
					&& t.event().equals(def.id()));
			boolean vanilla = List.of("going_ham", RadiocraftAdvancements.SHACK_UPGRADE, RadiocraftAdvancements.OUT_OF_BAND).contains(def.id());
			helper.assertTrue(byEvent != vanilla, def.id() + (vanilla ? " should use a vanilla criterion" : " has no event criterion of its id"));
		}
		helper.assertTrue(manager.get(Radiocraft.id("found_secret_floppy")) != null, "found_secret_floppy (the Handbook's lock) is gone");

		net.minecraft.server.level.ServerPlayer real = realPlayer(helper, "advreal");
		helper.assertFalse(RadiocraftAdvancements.has(real, RadiocraftAdvancements.FIRST_CONTACT), "a new player has First Contact");
		RadiocraftAdvancements.grant(real, RadiocraftAdvancements.FIRST_CONTACT);
		helper.assertTrue(RadiocraftAdvancements.has(real, RadiocraftAdvancements.FIRST_CONTACT), "the event did not complete First Contact");
		helper.assertFalse(RadiocraftAdvancements.has(real, RadiocraftAdvancements.SKIP_DAY), "granting one gave another");

		UUID away = UUID.nameUUIDFromBytes("adv-offline-player".getBytes());
		RadiocraftAdvancements.grant(helper.getLevel().getServer(), away, RadiocraftAdvancements.PAPER_TRAIL);
		helper.assertTrue(AdvancementData.of(helper.getLevel().getServer()).isPending(away, RadiocraftAdvancements.PAPER_TRAIL), "an offline player's grant is not kept");
		AdvancementData.of(helper.getLevel().getServer()).takePending(away);
		helper.succeed();
	}

	/** A ServerPlayer with real advancements (not on the player list, no connection: only for advancement checks). */
	private static net.minecraft.server.level.ServerPlayer realPlayer(GameTestHelper helper, String name) {
		net.minecraft.server.level.ServerPlayer player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
				new GameProfile(UUID.nameUUIDFromBytes(("adv-real-" + name).getBytes()), name), net.minecraft.server.level.ClientInformation.createDefault());
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		player.setPos(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D); // Not moveTo: that resets the (missing) connection.
		return player;
	}

	/** Going Ham, Shack Upgrade and Out of Band are vanilla criteria: placing a radio, holding a computer, the MARS recipe. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void vanillaCriteriaWork(GameTestHelper helper) {
		net.minecraft.server.level.ServerPlayer player = realPlayer(helper, "advvanilla");
		BlockPos radio = new BlockPos(4, 2, 4);
		helper.setBlock(radio, RadiocraftBlocks.HF_RADIO_20M.get());
		CriteriaTriggers.PLACED_BLOCK.trigger(player, helper.absolutePos(radio), new ItemStack(RadiocraftItems.HF_RADIO_20M.get()));
		helper.assertTrue(RadiocraftAdvancements.has(player, "going_ham"), "placing a radio is not Going Ham");
		ItemStack computer = new ItemStack(RadiocraftItems.RADIO_COMPUTER.get());
		player.getInventory().setItem(0, computer);
		CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), computer);
		helper.assertTrue(RadiocraftAdvancements.has(player, RadiocraftAdvancements.SHACK_UPGRADE), "holding a radio computer is not Shack Upgrade");
		CriteriaTriggers.RECIPE_CRAFTED.trigger(player, Radiocraft.id("mars_mod_install"), List.of());
		helper.assertTrue(RadiocraftAdvancements.has(player, RadiocraftAdvancements.OUT_OF_BAND), "fitting a MARS mod is not Out of Band");
		helper.succeed();
	}

	// -------------------- CONTACTS --------------------

	/**
	 * A heard B on their handheld, then B heard A: Is This Thing On? for each talker, then a verified contact. 1,500
	 * blocks apart on HF: First Contact and Skip Day, but not DX Hunter.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void bothWaysOnHfIsAContact(GameTestHelper helper) {
		FakePlayer a = player(helper, "advhfa", new BlockPos(2, 2, 2));
		FakePlayer b = player(helper, "advhfb", new BlockPos(2, 2, 2));
		BlockPos far = b.blockPosition().offset(1500, 0, 0);
		b.moveTo(far.getX() + 0.5D, far.getY(), far.getZ() + 0.5D);
		ContactTracker.hear(helper.getLevel(), a.getUUID(), b.blockPosition(), b, HF); // B heard A.
		assertHas(helper, a, RadiocraftAdvancements.IS_THIS_THING_ON);
		assertHasNot(helper, b, RadiocraftAdvancements.IS_THIS_THING_ON);
		assertHasNot(helper, a, RadiocraftAdvancements.FIRST_CONTACT);
		ContactTracker.hear(helper.getLevel(), b.getUUID(), a.blockPosition(), a, HF); // And A heard B.
		for(FakePlayer p : new FakePlayer[] { a, b }) {
			assertHas(helper, p, RadiocraftAdvancements.IS_THIS_THING_ON);
			assertHas(helper, p, RadiocraftAdvancements.FIRST_CONTACT);
			assertHas(helper, p, RadiocraftAdvancements.SKIP_DAY);
			assertHasNot(helper, p, RadiocraftAdvancements.DX_HUNTER);
			assertHasNot(helper, p, RadiocraftAdvancements.MOONBOUNCE);
		}
		done(helper, a, b);
	}

	/** Two VHF stations above Y 200 and 6,000 blocks apart: DX Hunter and Moonbounce, and no Skip Day (not HF). */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void highAndFarIsDxAndMoonbounce(GameTestHelper helper) {
		FakePlayer a = player(helper, "advdxa", new BlockPos(2, 2, 2));
		FakePlayer b = player(helper, "advdxb", new BlockPos(2, 2, 2));
		BlockPos highA = new BlockPos(a.getBlockX(), 230, a.getBlockZ());
		BlockPos highB = highA.offset(0, 10, 6000);
		ContactTracker.heard(helper.getLevel(), b.getUUID(), a.getUUID(), highB, VHF);
		ContactTracker.heard(helper.getLevel(), a.getUUID(), b.getUUID(), highA, VHF);
		for(FakePlayer p : new FakePlayer[] { a, b }) {
			assertHas(helper, p, RadiocraftAdvancements.FIRST_CONTACT);
			assertHas(helper, p, RadiocraftAdvancements.DX_HUNTER);
			assertHas(helper, p, RadiocraftAdvancements.MOONBOUNCE);
			assertHasNot(helper, p, RadiocraftAdvancements.SKIP_DAY);
		}
		done(helper, a, b);
	}

	/** A block radio's speaker: players within 16 blocks of it hear it, players further away do not. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void playersNearABlockRadioHearIt(GameTestHelper helper) {
		BlockPos radio = new BlockPos(4, 2, 4);
		FakePlayer talker = player(helper, "advblockt", new BlockPos(2, 2, 2));
		BlockPos away = talker.blockPosition().offset(0, 0, 500);
		talker.moveTo(away.getX(), away.getY(), away.getZ());
		FakePlayer near = player(helper, "advblockn", radio.east(3));
		FakePlayer far = player(helper, "advblockf", radio.east(3));
		BlockPos farAway = far.blockPosition().offset(40, 0, 0);
		far.moveTo(farAway.getX(), farAway.getY(), farAway.getZ());
		ContactTracker.hear(helper.getLevel(), talker.getUUID(), helper.absolutePos(radio), null, VHF);
		assertHas(helper, talker, RadiocraftAdvancements.IS_THIS_THING_ON);
		ContactTracker.hear(helper.getLevel(), near.getUUID(), talker.blockPosition(), talker, VHF);
		assertHas(helper, near, RadiocraftAdvancements.FIRST_CONTACT);
		ContactTracker.hear(helper.getLevel(), far.getUUID(), talker.blockPosition(), talker, VHF);
		assertHasNot(helper, far, RadiocraftAdvancements.FIRST_CONTACT);
		done(helper, talker, near, far);
	}

	/** A hundred stations worked (each once): Contest Station, at the hundredth and not before. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void aHundredContactsIsAContestStation(GameTestHelper helper) {
		FakePlayer op = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "advcontest")); // New each run: the count is saved.
		op.moveTo(helper.absolutePos(new BlockPos(2, 2, 2)).getX(), helper.absolutePos(new BlockPos(2, 2, 2)).getY(), helper.absolutePos(new BlockPos(2, 2, 2)).getZ());
		RadiocraftAdvancements.registerTestPlayer(op);
		BlockPos here = op.blockPosition();
		int start = AdvancementData.of(helper.getLevel().getServer()).contacts(op.getUUID());
		List<UUID> others = new ArrayList<>();
		for(int i = 0; i < RadiocraftAdvancements.CONTEST_CONTACTS - start; i++) {
			UUID other = UUID.nameUUIDFromBytes(("adv-contest-" + i).getBytes());
			others.add(other);
			helper.assertFalse(RadiocraftAdvancements.has(op, RadiocraftAdvancements.CONTEST_STATION), "Contest Station after " + (start + i) + " contacts");
			ContactTracker.heard(helper.getLevel(), other, op.getUUID(), here.offset(30, 0, 0), VHF);
			ContactTracker.heard(helper.getLevel(), op.getUUID(), other, here, VHF);
		}
		assertHas(helper, op, RadiocraftAdvancements.CONTEST_STATION);
		for(UUID other : others) // They were "offline": their grants waited for a login that will not come.
			AdvancementData.of(helper.getLevel().getServer()).takePending(other);
		done(helper, op);
	}

	/** Hearing a numbers station's voice id: Numbers Station. Another station's voice is nothing of the kind. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void hearingANumbersStation(GameTestHelper helper) {
		FakePlayer listener = player(helper, "advnumbers", new BlockPos(2, 2, 2));
		ContactTracker.hear(helper.getLevel(), UUID.nameUUIDFromBytes("adv-not-numbers".getBytes()), listener.blockPosition(), listener, HF);
		assertHasNot(helper, listener, RadiocraftAdvancements.NUMBERS_STATION);
		UUID numbers = UUID.nameUUIDFromBytes("adv-numbers-station".getBytes());
		ContactTracker.numbersStation(numbers);
		ContactTracker.hear(helper.getLevel(), numbers, listener.blockPosition(), listener, HF);
		assertHas(helper, listener, RadiocraftAdvancements.NUMBERS_STATION);
		done(helper, listener);
	}

	/**
	 * FT8: each computer logging the other's callsign is one way of the contact. The owners get Weak Signal Wizard,
	 * and when both have logged, First Contact. Setting their callsigns gave them Licensed to Chill.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void ft8ContactsAreVerifiedContacts(GameTestHelper helper) {
		FakePlayer a = player(helper, "advft8a", new BlockPos(2, 2, 8));
		FakePlayer b = player(helper, "advft8b", new BlockPos(8, 2, 8));
		PlayerCallsignSavedData calls = PlayerCallsignSavedData.get(helper.getLevel().getServer().overworld());
		calls.setCallsignData(a.getUUID(), new PlayerCallsignData(a.getUUID().toString(), "advft8a", "ZZ1FTA", LicenseClass.TECHNICIAN));
		calls.setCallsignData(b.getUUID(), new PlayerCallsignData(b.getUUID().toString(), "advft8b", "ZZ1FTB", LicenseClass.TECHNICIAN));
		assertHas(helper, a, RadiocraftAdvancements.LICENSED_TO_CHILL);
		RadioComputerBlockEntity ca = computer(helper, new BlockPos(2, 2, 4), ComputerApps.FT8);
		RadioComputerBlockEntity cb = computer(helper, new BlockPos(8, 2, 4), ComputerApps.FT8);
		ca.files().setOwner(a.getUUID());
		cb.files().setOwner(b.getUUID());
		Ft8Handler.contactMade(helper.getLevel(), ca, new Ft8Sequencer.Qso("ZZ1FTB", "JJ00", -10, -12), 14_075_200.0F);
		assertHas(helper, a, RadiocraftAdvancements.WEAK_SIGNAL_WIZARD);
		assertHasNot(helper, a, RadiocraftAdvancements.FIRST_CONTACT);
		Ft8Handler.contactMade(helper.getLevel(), cb, new Ft8Sequencer.Qso("ZZ1FTA", "JJ00", -12, -10), 14_075_400.0F);
		assertHas(helper, b, RadiocraftAdvancements.WEAK_SIGNAL_WIZARD);
		assertHas(helper, a, RadiocraftAdvancements.FIRST_CONTACT);
		assertHas(helper, b, RadiocraftAdvancements.FIRST_CONTACT);
		calls.resetCallsign(a.getUUID());
		calls.resetCallsign(b.getUUID());
		done(helper, a, b);
	}

	// -------------------- THE COMPUTER --------------------

	/** Net Control: a net logged with five check-ins; four is not enough. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void aNetOfFiveIsNetControl(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, new BlockPos(4, 2, 4), ComputerApps.NET_CONTROL);
		FakePlayer ncs = player(helper, "advnet", new BlockPos(4, 2, 6));
		NetLog net = new NetLog();
		net.setup("Advancement Net", "147.180", "FM", "ZZ1NCS");
		net.open(System.currentTimeMillis());
		for(int i = 0; i < 4; i++)
			net.checkIn("ZZ" + i + "AB", "Op" + i, "", false, false, System.currentTimeMillis());
		CompoundTag data = new CompoundTag();
		data.put("net", net.toTag());
		app(computer, ncs, ComputerApps.NET_CONTROL, "log", data);
		assertHasNot(helper, ncs, RadiocraftAdvancements.NET_CONTROL);
		net.checkIn("ZZ9AB", "Op9", "", false, false, System.currentTimeMillis());
		data.put("net", net.toTag());
		CompoundTag logged = app(computer, ncs, ComputerApps.NET_CONTROL, "log", data);
		helper.assertTrue(logged.getCompound("data").getInt("logged") == 5, "logged " + logged);
		assertHas(helper, ncs, RadiocraftAdvancements.NET_CONTROL);
		done(helper, ncs);
	}

	/** The awards program's own result drives Worked All Biomes: complete gives it, a level short does not. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void workedAllBiomesFollowsTheAward(GameTestHelper helper) {
		FakePlayer player = player(helper, "advbiomes", new BlockPos(2, 2, 2));
		AwardsHandler.grantEarned(player, List.of(new Awards.Progress("biomes", 30, List.of(5, 10, 25, 40), 3, List.of(), List.of("minecraft:desert"))));
		assertHasNot(helper, player, RadiocraftAdvancements.WORKED_ALL_BIOMES);
		AwardsHandler.grantEarned(player, List.of(new Awards.Progress("bands", 5, List.of(1, 3, 5), 3, List.of(), List.of())));
		assertHasNot(helper, player, RadiocraftAdvancements.WORKED_ALL_BIOMES);
		AwardsHandler.grantEarned(player, List.of(new Awards.Progress("biomes", 40, List.of(5, 10, 25, 40), 4, List.of(), List.of())));
		assertHas(helper, player, RadiocraftAdvancements.WORKED_ALL_BIOMES);
		done(helper, player);
	}

	/** Bible Study, Spin the Disk and High Score: opening the Bible, playing MUSIC and PILEUP saving its table. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void secretProgramsHaveTheirAdvancements(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, new BlockPos(4, 2, 4));
		FakePlayer player = player(helper, "advsecret", new BlockPos(4, 2, 6));
		CompoundTag bible = new CompoundTag();
		bible.putString("app", "bible");
		call(computer, player, "sys.secret", bible);
		assertHasNot(helper, player, RadiocraftAdvancements.BIBLE_STUDY); // Not installed.
		computer.install(ComputerApps.BIBLE);
		computer.install(ComputerApps.MUSIC);
		computer.install(ComputerApps.FALLING_BLOCKS);
		call(computer, player, "sys.secret", bible);
		assertHas(helper, player, RadiocraftAdvancements.BIBLE_STUDY);
		CompoundTag music = new CompoundTag();
		music.putBoolean("on", true);
		call(computer, player, "music", music);
		assertHas(helper, player, RadiocraftAdvancements.SPIN_THE_DISK);
		CompoundTag write = new CompoundTag();
		write.putString("name", "PILEUP.DAT");
		write.putInt("part", 0);
		write.putInt("parts", 1);
		write.putByteArray("data", new byte[] { 1, 2, 3 });
		CompoundTag wrote = call(computer, player, "fs.write", write);
		helper.assertTrue(wrote.getString("error").isEmpty(), "PILEUP.DAT was not written: " + wrote);
		assertHas(helper, player, RadiocraftAdvancements.HIGH_SCORE);
		CompoundTag off = new CompoundTag();
		off.putBoolean("on", false);
		call(computer, player, "music", off);
		done(helper, player);
	}

	/** Digital Native: the tenth program installed from its floppy. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void tenProgramsIsDigitalNative(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, new BlockPos(4, 2, 4));
		FakePlayer player = player(helper, "advinstall", new BlockPos(4, 2, 6));
		List<ComputerApp> apps = ComputerApps.onFloppies();
		helper.assertTrue(apps.size() >= RadiocraftAdvancements.DIGITAL_NATIVE_PROGRAMS, "only " + apps.size() + " programs on floppies");
		for(int i = 0; i < RadiocraftAdvancements.DIGITAL_NATIVE_PROGRAMS - 1; i++)
			computer.install(apps.get(i));
		ComputerApp tenth = apps.get(RadiocraftAdvancements.DIGITAL_NATIVE_PROGRAMS - 1);
		helper.assertTrue(computer.insertFloppy(new ItemStack(RadiocraftItems.floppy(tenth))), "the floppy would not go in");
		CompoundTag install = new CompoundTag();
		install.putString("app", tenth.id());
		CompoundTag reply = call(computer, player, "sys.install", install);
		helper.assertTrue(reply.getString("error").isEmpty() && computer.installedCount() == RadiocraftAdvancements.DIGITAL_NATIVE_PROGRAMS, "not installed: " + reply);
		assertHas(helper, player, RadiocraftAdvancements.DIGITAL_NATIVE);
		done(helper, player);
	}

	/** Mission Control: opening a computer with a monitor, a scanner, a radio interface and a speaker. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 80)
	public static void theFullStationIsMissionControl(GameTestHelper helper) {
		BlockPos at = new BlockPos(4, 2, 4);
		RadioComputerBlockEntity computer = computer(helper, at);
		FakePlayer player = player(helper, "advmission", at.south(2));
		helper.setBlock(at.east(), RadiocraftBlocks.SCANNER.get());
		helper.setBlock(at.west(), RadiocraftBlocks.RADIO_INTERFACE.get());
		computer.open(player, computer.monitorPos());
		assertHasNot(helper, player, RadiocraftAdvancements.MISSION_CONTROL); // No speaker yet.
		helper.setBlock(at.north(), RadiocraftBlocks.LARGE_SPEAKER.get());
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertFalse(computer.getSpeakers().isEmpty(), "the computer does not see its speaker"))
				.thenExecute(() -> {
					computer.open(player, computer.monitorPos());
					assertHas(helper, player, RadiocraftAdvancements.MISSION_CONTROL);
				})
				.thenExecute(() -> done(helper, player));
	}

	/** Typewriter Thunder: RTTY printed on a teletype, for the players by it. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void copyingRttyIsTypewriterThunder(GameTestHelper helper) {
		BlockPos teletype = new BlockPos(4, 2, 4);
		helper.setBlock(teletype, RadiocraftBlocks.TELETYPE.get());
		FakePlayer player = player(helper, "advrtty", teletype.south(2));
		GameTestUtil.blockEntity(helper, teletype, TeletypeBlockEntity.class).receive(TncPacket.rtty(helper.getLevel().getRandom().nextLong(), "ZZ1RT", "RYRYRY DE ZZ1RT\n"));
		assertHas(helper, player, RadiocraftAdvancements.TYPEWRITER_THUNDER);
		done(helper, player);
	}

	/**
	 * Packet Rat: a message posted over the air (SP, the text, /EX) on another station's BBS gives the poster the
	 * advancement; the BBS's own owner posting does not.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void postingOnAnotherBbsIsPacketRat(GameTestHelper helper) {
		BlockPos at = new BlockPos(4, 2, 4);
		RadioComputerBlockEntity computer = computer(helper, at, ComputerApps.BBS);
		helper.setBlock(at.west(), RadiocraftBlocks.RADIO_INTERFACE.get());
		GameTestUtil.blockEntity(helper, at.west(), RadioInterfaceBlockEntity.class).setCallsign("ZZ1BBS", "gametest");
		FakePlayer sysop = player(helper, "advsysop", at.south(2));
		FakePlayer poster = player(helper, "advposter", at.south(3));
		computer.files().setOwner(sysop.getUUID());
		PlayerCallsignSavedData calls = PlayerCallsignSavedData.get(helper.getLevel().getServer().overworld());
		calls.setCallsignData(poster.getUUID(), new PlayerCallsignData(poster.getUUID().toString(), "advposter", "ZZ1PST", LicenseClass.TECHNICIAN));
		CompoundTag on = new CompoundTag();
		on.putBoolean("online", true);
		app(computer, sysop, ComputerApps.BBS, "settings", on);
		String bbs = BbsHandler.bbsCall(computer);
		BbsHandler handler = (BbsHandler)ComputerAppHandlers.get(ComputerApps.BBS);
		for(String line : new String[] { "SP ZZ1XYZ Field day", "Meet at the park", "/EX" })
			handler.receive(computer, TncPacket.message(helper.getLevel().getRandom().nextLong(), "ZZ1PST-7", bbs, line));
		helper.assertTrue(BbsHandler.mailbox(computer).find(1) != null, "nothing was posted: " + BbsHandler.state(computer).log());
		assertHas(helper, poster, RadiocraftAdvancements.PACKET_RAT);
		assertHasNot(helper, sysop, RadiocraftAdvancements.PACKET_RAT);
		calls.resetCallsign(poster.getUUID());
		done(helper, sysop, poster);
	}

	// -------------------- QSL CARDS --------------------

	/**
	 * Paper Trail: another player holding a card you signed gives it to you (the signer), not to them; your own card
	 * in your own hands is nothing. Card Collector: an album of 20.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void signedCardsMakeAPaperTrail(GameTestHelper helper) {
		FakePlayer signer = player(helper, "advsigner", new BlockPos(2, 2, 2));
		FakePlayer holder = player(helper, "advholder", new BlockPos(4, 2, 2));
		QslCard blank = new QslCard(QslCard.Design.DEFAULT.withCall("ZZ1QSL"), QslCard.Fill.EMPTY, false);
		QslCard signed = blank.filled(new QslCard.Fill("", "ZZ2QSL", "2M", "147.180", "FM", "59", "Tnx", 0L, "", "", false), 1_000L, "D1 1200", "advsigner", true);
		helper.assertTrue(signed != null && signed.signed(), "the card could not be signed");
		ItemStack card = QslCards.stack(signed, 1);
		AdvancementEvents.holding(signer, card);
		assertHasNot(helper, signer, RadiocraftAdvancements.PAPER_TRAIL);
		holder.getInventory().add(card.copy());
		AdvancementEvents.checkInventory(holder);
		assertHas(helper, signer, RadiocraftAdvancements.PAPER_TRAIL);
		assertHasNot(helper, holder, RadiocraftAdvancements.PAPER_TRAIL);

		ItemStack album = new ItemStack(RadiocraftItems.QSL_ALBUM.get());
		QslAlbumItem.add(album, QslCards.stack(blank, RadiocraftAdvancements.ALBUM_CARDS - 1));
		AdvancementEvents.holding(holder, album);
		assertHasNot(helper, holder, RadiocraftAdvancements.CARD_COLLECTOR);
		QslAlbumItem.add(album, QslCards.stack(blank, 1));
		AdvancementEvents.holding(holder, album);
		assertHas(helper, holder, RadiocraftAdvancements.CARD_COLLECTOR);
		done(helper, signer, holder);
	}

	// -------------------- EVERYTHING ELSE --------------------

	/** Ground Plane Theory: a dipole strung by a player standing by; The FCC Is Watching: a badge issued. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 400)
	public static void buildingAnAntennaAndTheBadge(GameTestHelper helper) {
		BlockPos centre = new BlockPos(8, 2, 8);
		FakePlayer builder = player(helper, "advantenna", centre.south(3));
		helper.setBlock(centre, RadiocraftBlocks.BALUN_ONE_TO_ONE.get());
		FccGear.issueBadge(builder);
		assertHas(helper, builder, RadiocraftAdvancements.FCC_IS_WATCHING);
		helper.startSequence()
				.thenIdle(5) // The balun is in place (and has looked at its empty surroundings) before the wires go up, as in play.
				.thenExecute(() -> {
					assertHasNot(helper, builder, RadiocraftAdvancements.GROUND_PLANE_THEORY);
					GameTestUtil.wire(helper, centre, centre.west(5));
					GameTestUtil.wire(helper, centre, centre.east(5));
				})
				.thenWaitUntil(() -> assertHas(helper, builder, RadiocraftAdvancements.GROUND_PLANE_THEORY))
				.thenExecute(() -> done(helper, builder));
	}
}
