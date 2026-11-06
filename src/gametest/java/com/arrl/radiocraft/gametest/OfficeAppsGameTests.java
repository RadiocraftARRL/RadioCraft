package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.api.capabilities.LicenseClass;
import com.arrl.radiocraft.api.capabilities.PlayerCallsignData;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioInterfaceBlockEntity;
import com.arrl.radiocraft.common.blockentities.ScannerBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.BookDocument;
import com.arrl.radiocraft.common.computer.Codeplug;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.apps.HandProgramming;
import com.arrl.radiocraft.common.radio.commercial.CommercialContact;
import com.arrl.radiocraft.common.data.PlayerCallsignSavedData;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.CommercialHandheldItem;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgramming;
import com.arrl.radiocraft.common.computer.FrequencyList;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The radio computer's office programs (1.12 step 6), server side, driven as their screens would drive them: the book
 * editor printing a long book in volumes, the radio programmer reading and writing a radio held in the hand and a
 * wired mobile without ever giving out a hidden key, the frequency manager sending channels into a radio around its
 * keys, the logbook hearing who a wired radio heard, and the solar program needing a receiver. The screens are in
 * NeedsTesting. Only the logbook test puts a signal into a radio, injected straight into its receiver on 147.180 MHz.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class OfficeAppsGameTests {

	private static final BlockPos COMPUTER = new BlockPos(4, 2, 4);
	private static final BlockPos SCANNER = COMPUTER.east();
	private static final BlockPos MOBILE = COMPUTER.west();
	private static final BlockPos RADIO = COMPUTER.north();

	private static FakePlayer player(GameTestHelper helper, String name) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("office-" + name).getBytes()), name));
		player.getInventory().clearContent();
		BlockPos at = helper.absolutePos(COMPUTER.south(2));
		player.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
		return player;
	}

	private static RadioComputerBlockEntity computer(GameTestHelper helper, ComputerApp... apps) {
		helper.setBlock(COMPUTER, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(COMPUTER.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, COMPUTER, RadioComputerBlockEntity.class);
		for(ComputerApp app : apps)
			computer.install(app);
		return computer;
	}

	private static ScannerBlockEntity scanner(GameTestHelper helper) {
		helper.setBlock(SCANNER, RadiocraftBlocks.SCANNER.get());
		return GameTestUtil.blockEntity(helper, SCANNER, ScannerBlockEntity.class);
	}

	/** One program request as its screen sends it; the reply's tag ({@code data}, maybe {@code error}). */
	private static CompoundTag app(RadioComputerBlockEntity computer, FakePlayer player, ComputerApp app, String action, CompoundTag data) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", app.id());
		wrapped.putString("action", action);
		wrapped.put("data", data);
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, "app", wrapped, new ComputerReply(player, 3, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	private static CompoundTag radio(String id) {
		CompoundTag tag = new CompoundTag();
		tag.putString("radio", id);
		return tag;
	}

	private static String error(CompoundTag reply) {
		return reply.getString("error");
	}

	/** A book longer than a paper tray prints in volumes, each a written book a sheet a page. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void bookPrintsInVolumes(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.BOOK_EDITOR);
		ScannerBlockEntity scanner = scanner(helper);
		FakePlayer player = player(helper, "book");
		List<String> pages = new ArrayList<>();
		for(int i = 0; i < BookDocument.PAGES_PER_VOLUME + 6; i++)
			pages.add("Page " + (i + 1));
		computer.files().writeNbt("NOVEL.BOK", new BookDocument("Novel", "", pages).toTag());
		scanner.getInventory().setStackInSlot(ScannerBlockEntity.TRAY, new ItemStack(Items.PAPER, 64));

		CompoundTag print = new CompoundTag();
		print.putString("name", "NOVEL.BOK");
		print.putInt("volume", 0);
		CompoundTag first = app(computer, player, ComputerApps.BOOK_EDITOR, "print", print);
		helper.assertFalse(first.contains("error"), "volume 1: " + first);
		helper.assertTrue(first.getCompound("data").getInt("volumes") == 2, "volumes: " + first);
		WrittenBookContent book = scanner.output().get(DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(book != null && book.pages().size() == BookDocument.PAGES_PER_VOLUME, "volume 1 is " + scanner.output());
		helper.assertTrue(book.title().raw().equals("Novel Vol. 1") && book.author().equals("book"), "title " + book.title().raw() + " by " + book.author());
		helper.assertTrue(scanner.tray().isEmpty(), "a full tray for a full volume");

		print.putInt("volume", 1);
		helper.assertTrue(error(app(computer, player, ComputerApps.BOOK_EDITOR, "print", print)).equals("output_full"), "printed onto a waiting book");
		scanner.getInventory().setStackInSlot(ScannerBlockEntity.OUTPUT, ItemStack.EMPTY);
		scanner.getInventory().setStackInSlot(ScannerBlockEntity.TRAY, new ItemStack(Items.PAPER, 10));
		helper.assertFalse(app(computer, player, ComputerApps.BOOK_EDITOR, "print", print).contains("error"), "volume 2 did not print");
		WrittenBookContent second = scanner.output().get(DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(second != null && second.pages().size() == 6 && second.title().raw().equals("Novel Vol. 2"), "volume 2 is " + scanner.output());
		helper.assertTrue(scanner.tray().getCount() == 4, "paper left " + scanner.tray().getCount());

		print.putInt("volume", 2);
		helper.assertTrue(error(app(computer, player, ComputerApps.BOOK_EDITOR, "print", print)).equals("no_such_volume"), "a third volume printed");
		helper.assertTrue(error(app(computer, player, ComputerApps.LOGBOOK, "heard", new CompoundTag())).equals("not_installed"), "a program not installed answered");
		helper.succeed();
	}

	/** The "Program radios" mode's request, as the CPS sends it: on with {@code plug} loaded, or off (null). */
	private static CompoundTag hand(RadioComputerBlockEntity computer, FakePlayer player, Codeplug plug) {
		CompoundTag data = new CompoundTag();
		data.putBoolean("on", plug != null);
		if(plug != null)
			data.put("plug", plug.toTag());
		return app(computer, player, ComputerApps.PROGRAMMER, "hand", data);
	}

	/**
	 * The CPS's "Program radios" mode (1.12 playtest fixes 2): a handheld held by the player is read with its hidden
	 * key as ****, written back with a new name and the key kept, a radio without that key refuses the hidden key, the
	 * unit ID advances from radio to radio, a mobile still in item form takes the codeplug too, and nothing happens
	 * with the mode off. The scanner's glass no longer takes radios.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void programmerProgramsRadiosHeldInTheHand(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.PROGRAMMER);
		FakePlayer player = player(helper, "cps");
		ItemStack handheld = new ItemStack(RadiocraftItems.UPGRADED_COMMERCIAL_HANDHELD.get());
		CommercialProgramming.Target target = CommercialProgramming.of(handheld);
		helper.assertTrue(target != null, "the upgraded handheld is not programmable");
		helper.assertFalse(ScannerBlockEntity.isScannable(handheld), "the glass still takes a commercial radio");
		helper.assertTrue(CommercialProgramming.writeChannel(target, 2, CommercialChannel.parse("TAC|155.5|=|DIG|0|321|H")).ok(), "could not set up the key");

		helper.assertFalse(HandProgramming.use(computer, player, handheld, true), "read a radio with the mode off");
		helper.assertTrue(hand(computer, player, Codeplug.blank("")).getCompound("data").getBoolean("on"), "the mode did not turn on");
		helper.assertTrue(computer.hasHandSessions(), "the computer does not know the mode is on");

		// Sneak: read the radio into the CPS.
		helper.assertTrue(HandProgramming.use(computer, player, handheld, true), "the read was not taken");
		Codeplug read = computer.handSession(player.getUUID()).plug;
		helper.assertTrue(read.model().equals("upgraded_handheld") && read.capacity() == 40, "read " + read.model());
		helper.assertTrue(read.channel(2).keyId() == CommercialChannel.KEY_HIDDEN, "the hidden key read out as " + read.channel(2).keyId());
		helper.assertFalse(read.toTag().toString().contains(":321"), "the key is in the codeplug: " + read.toTag());

		// Edit and write it back by hand: the hidden key stays the radio's.
		CommercialChannel tac = read.channel(2);
		Codeplug edited = read.with(2, new CommercialChannel("TAC RENM", tac.rxHz(), tac.txHz(), true, 0.0F, CommercialChannel.KEY_HIDDEN, true))
				.with(5, CommercialChannel.parse("FIRE|154.28|=|FM|100.0")).with(39, CommercialChannel.parse("LAST|155.94"))
				.withUnitId(1200).withContacts(List.of(new CommercialContact(1201, "ENG 5")));
		hand(computer, player, edited);
		helper.assertTrue(HandProgramming.use(computer, player, handheld, false), "the write was not taken");
		CommercialChannel stored = CommercialHandheldItem.program(handheld).channel(2);
		helper.assertTrue(stored.keyId() == 321 && !stored.keyReadable() && stored.name().equals("TAC RENM"), "stored " + stored);
		helper.assertTrue(CommercialHandheldItem.program(handheld).channel(39).name().equals("LAST"), "channel 40 was not written");
		helper.assertTrue(CommercialHandheldItem.program(handheld).unitId() == 1200, "unit " + CommercialHandheldItem.program(handheld).unitId());
		helper.assertTrue("ENG 5".equals(CommercialHandheldItem.program(handheld).alias(1201)), "the contacts were not written");
		helper.assertTrue(computer.handSession(player.getUUID()).plug.unitId() == 1201, "the next radio should get unit 1201");

		// A radio without that key: the hidden key is refused, nothing written.
		ItemStack other = new ItemStack(RadiocraftItems.UPGRADED_COMMERCIAL_HANDHELD.get());
		helper.assertTrue(HandProgramming.use(computer, player, other, false), "the write was not taken");
		helper.assertTrue(!CommercialHandheldItem.program(other).channel(5).name().equals("FIRE") && CommercialHandheldItem.program(other).unitId() == 0,
				"a refused write changed the radio: " + CommercialHandheldItem.program(other));

		// A mobile in item form, from a plain codeplug: unit 1201 comes next.
		Codeplug plain = Codeplug.blank("").with(0, CommercialChannel.parse("DISP|155.0125")).withUnitId(1201);
		hand(computer, player, plain);
		ItemStack mobileItem = new ItemStack(RadiocraftItems.COMMERCIAL_MOBILE.get());
		helper.assertTrue(HandProgramming.use(computer, player, mobileItem, false), "the mobile item was not written");
		CommercialProgramming.Target mobile = CommercialProgramming.of(mobileItem);
		helper.assertTrue(mobile != null && mobile.program().channel(0).name().equals("DISP") && mobile.program().unitId() == 1201,
				"the mobile item has " + (mobile == null ? null : mobile.program()));

		// Through the click on the monitor, as a player does it: the click is taken, no screen opens.
		ItemStack brick = new ItemStack(RadiocraftItems.BASIC_COMMERCIAL_HANDHELD.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, brick);
		BlockPos monitor = helper.absolutePos(COMPUTER.above());
		PlayerInteractEvent.RightClickBlock click = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, monitor,
				new BlockHitResult(Vec3.atCenterOf(monitor), Direction.NORTH, monitor, false));
		HandProgramming.onRightClickBlock(click);
		helper.assertTrue(click.isCanceled(), "the click on the monitor was not taken");
		helper.assertTrue(CommercialHandheldItem.program(player.getMainHandItem()).channel(0).name().equals("DISP"), "the brick was not written by the click");

		hand(computer, player, null);
		helper.assertFalse(computer.hasHandSessions(), "the mode did not turn off");
		helper.assertFalse(HandProgramming.use(computer, player, handheld, false), "wrote a radio with the mode off");
		helper.succeed();
	}

	/** The CPS's Continue on Read or Write by hand: the mode armed for one radio ({@code action} "read" or "write"). */
	private static CompoundTag arm(RadioComputerBlockEntity computer, FakePlayer player, Codeplug plug, String action) {
		CompoundTag data = new CompoundTag();
		data.putBoolean("on", true);
		data.put("plug", plug.toTag());
		data.putString("arm", action);
		return app(computer, player, ComputerApps.PROGRAMMER, "hand", data);
	}

	/** A right click on the radio computer itself, as a player does it, holding {@code stack}; true if the CPS took it. */
	private static boolean clickComputer(GameTestHelper helper, FakePlayer player, ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPos pos = helper.absolutePos(COMPUTER);
		PlayerInteractEvent.RightClickBlock click = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos,
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
		HandProgramming.onRightClickBlock(click);
		return click.isCanceled();
	}

	/**
	 * Read and Write by hand (2026-11-06): Continue arms the CPS for one radio; the next right click on the computer
	 * holding a handheld or a mobile item does the armed action whether the player sneaks or not (a sneak would read
	 * in the "Program radios" mode), and the mode ends after it, so a second click is the computer's own again.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void programmerReadsAndWritesOneRadioByHand(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.PROGRAMMER);
		FakePlayer player = player(helper, "armed");
		ItemStack handheld = new ItemStack(RadiocraftItems.BASIC_COMMERCIAL_HANDHELD.get());
		CommercialProgramming.Target target = CommercialProgramming.of(handheld);
		helper.assertTrue(target != null && CommercialProgramming.writeChannel(target, 0, CommercialChannel.parse("OPS|151.625")).ok(), "could not set up the handheld");

		// Armed read, a plain click: read, not written with the blank codeplug.
		CompoundTag answer = arm(computer, player, Codeplug.blank(""), "read").getCompound("data");
		helper.assertTrue(answer.getBoolean("on") && answer.getString("arm").equals("read"), "not armed: " + answer);
		helper.assertTrue(computer.handSession(player.getUUID()).armed.equals("read"), "the computer is not waiting for a read");
		player.setShiftKeyDown(false);
		helper.assertTrue(clickComputer(helper, player, handheld), "the armed read's click was not taken");
		helper.assertTrue(CommercialHandheldItem.program(player.getMainHandItem()).channel(0).name().equals("OPS"), "the read wrote the radio");
		helper.assertFalse(computer.hasHandSessions(), "the mode is still on after the armed read");
		helper.assertFalse(clickComputer(helper, player, player.getMainHandItem()), "a click after the read was taken too");

		// Armed write, sneaking: written all the same.
		Codeplug plug = Codeplug.blank("").with(0, CommercialChannel.parse("DISP|155.0125")).with(1, CommercialChannel.parse("TAC|155.4"));
		arm(computer, player, plug, "write");
		player.setShiftKeyDown(true);
		helper.assertTrue(clickComputer(helper, player, player.getMainHandItem()), "the armed write's click was not taken");
		player.setShiftKeyDown(false);
		CommercialProgram written = CommercialHandheldItem.program(player.getMainHandItem());
		helper.assertTrue(written.channel(0).name().equals("DISP") && written.channel(1).name().equals("TAC"), "the handheld has " + written);
		helper.assertFalse(computer.hasHandSessions(), "the mode is still on after the armed write");

		// A mobile in item form: written (the sneak flag ignored), then read without being changed.
		ItemStack mobileItem = new ItemStack(RadiocraftItems.COMMERCIAL_MOBILE.get());
		arm(computer, player, Codeplug.blank("").with(0, CommercialChannel.parse("YARD|154.57")), "write");
		helper.assertTrue(HandProgramming.use(computer, player, mobileItem, true), "the mobile item's armed write was not taken");
		CommercialProgramming.Target mobile = CommercialProgramming.of(mobileItem);
		helper.assertTrue(mobile != null && mobile.program().channel(0).name().equals("YARD"), "the mobile item has " + (mobile == null ? null : mobile.program()));
		arm(computer, player, Codeplug.blank(""), "read");
		helper.assertTrue(HandProgramming.use(computer, player, mobileItem, false), "the mobile item's armed read was not taken");
		helper.assertTrue(CommercialProgramming.of(mobileItem).program().channel(0).name().equals("YARD"), "the read changed the mobile item");
		helper.assertFalse(computer.hasHandSessions(), "the mode is still on after the mobile's read");
		helper.assertFalse(HandProgramming.use(computer, player, mobileItem, false), "the mobile was programmed with nothing armed");

		// Armed and then turned off (the CPS left): nothing is waiting any more.
		arm(computer, player, plug, "write");
		hand(computer, player, null);
		helper.assertFalse(clickComputer(helper, player, handheld.copy()), "a click was taken after the CPS stopped waiting");
		helper.succeed();
	}

	/** A player who is gone (a fake player is never on the player list) has the mode ended at the next check. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void handModeEndsWhenThePlayerIsGone(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.PROGRAMMER);
		FakePlayer player = player(helper, "gone");
		hand(computer, player, Codeplug.blank(""));
		helper.assertTrue(computer.hasHandSessions(), "the mode did not turn on");
		helper.succeedWhen(() -> helper.assertFalse(computer.hasHandSessions(), "the mode is still on"));
	}

	/** A mobile touching the computer is found, programmed and reset to factory. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void programmerReachesAWiredMobile(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.PROGRAMMER);
		helper.setBlock(MOBILE, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		CommercialMobileBlockEntity mobile = GameTestUtil.blockEntity(helper, MOBILE, CommercialMobileBlockEntity.class);
		FakePlayer player = player(helper, "mobile");
		String id = "pos:" + helper.absolutePos(MOBILE).asLong();
		ListTag radios = app(computer, player, ComputerApps.PROGRAMMER, "radios", new CompoundTag()).getCompound("data").getList("radios", Tag.TAG_COMPOUND);
		helper.assertTrue(radios.size() == 1 && radios.getCompound(0).getString("id").equals(id), "radios: " + radios);

		List<CommercialChannel> channels = new ArrayList<>();
		channels.add(CommercialChannel.parse("DISP|155.0125|=|DIG|0|77|R"));
		CompoundTag write = radio(id);
		write.put("channels", Codeplug.channelsTag(channels));
		CompoundTag written = app(computer, player, ComputerApps.PROGRAMMER, "write", write).getCompound("data");
		helper.assertTrue(written.getList("problems", Tag.TAG_COMPOUND).isEmpty(), "refused: " + written);
		helper.assertTrue(mobile.program().channel(0).name().equals("DISP") && mobile.program().channel(0).keyId() == 77, "the mobile has " + mobile.program().channel(0));
		helper.assertTrue(mobile.program().channel(1).isBlank(), "the rest was not cleared");

		app(computer, player, ComputerApps.PROGRAMMER, "factory", radio(id));
		helper.assertTrue(mobile.program().channel(0).isBlank(), "factory reset left " + mobile.program().channel(0));
		helper.assertTrue(error(app(computer, player, ComputerApps.PROGRAMMER, "read", radio("pos:12345"))).equals("radio_gone"), "read a radio the computer does not reach");
		helper.succeed();
	}

	/** The frequency manager sends two channels into a mobile from knob position 4, around a channel with a hidden key. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void frequencyManagerSendsAroundKeys(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.FREQUENCY_MANAGER);
		helper.setBlock(MOBILE, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		CommercialMobileBlockEntity mobile = GameTestUtil.blockEntity(helper, MOBILE, CommercialMobileBlockEntity.class);
		helper.assertTrue(CommercialProgramming.writeChannel(mobile, 0, CommercialChannel.parse("SECRET|155.5|=|DIG|0|4242|H")).ok(), "could not set up the key");
		FakePlayer player = player(helper, "freqman");
		String id = "pos:" + helper.absolutePos(MOBILE).asLong();

		List<CommercialChannel> sent = new ArrayList<>();
		sent.add(new FrequencyList.Entry("CLUB", 151.82D, 151.82D, FrequencyList.Mode.FM, 0.0F, "").toChannel());
		sent.add(new FrequencyList.Entry("WX1", 162.55D, 0.0D, FrequencyList.Mode.FM, 0.0F, "").toChannel());
		CompoundTag export = radio(id);
		export.putInt("start", 3);
		export.put("channels", Codeplug.channelsTag(sent));
		CompoundTag answer = app(computer, player, ComputerApps.FREQUENCY_MANAGER, "export", export).getCompound("data");
		helper.assertTrue(answer.getList("problems", Tag.TAG_COMPOUND).isEmpty() && answer.getInt("written") == 2, "export: " + answer);
		helper.assertTrue(mobile.program().channel(3).name().equals("CLUB") && mobile.program().channel(4).isReceiveOnly(), "channels 4 and 5: "
				+ mobile.program().channel(3) + ", " + mobile.program().channel(4));
		helper.assertTrue(mobile.program().channel(0).keyId() == 4242, "the hidden key on channel 1 was lost: " + mobile.program().channel(0));

		export.putInt("start", 15);
		CompoundTag over = app(computer, player, ComputerApps.FREQUENCY_MANAGER, "export", export).getCompound("data");
		helper.assertTrue(over.getInt("written") == 0 && !over.getList("problems", Tag.TAG_COMPOUND).isEmpty(), "sent past channel 16: " + over);
		helper.succeed();
	}

	/** A station heard on a radio touching the computer comes back with its callsign, frequency, mode and a report. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100)
	public static void logbookHearsWhoTheRadioHeard(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.LOGBOOK);
		helper.setBlock(RADIO, RadiocraftBlocks.VHF_BASE_STATION.get());
		RadioBlockEntity radio = GameTestUtil.blockEntity(helper, RADIO, RadioBlockEntity.class);
		radio.setFrequency(147_180_000.0F);
		FakePlayer player = player(helper, "logbook");
		UUID talker = UUID.nameUUIDFromBytes("gametest-logbook-talker".getBytes());
		PlayerCallsignSavedData.get(helper.getLevel().getServer().overworld())
				.setCallsignData(talker, new PlayerCallsignData(talker.toString(), "Talker", "W1LOG", LicenseClass.TECHNICIAN));
		helper.startSequence()
				.thenExecute(() -> {
					helper.assertTrue(app(computer, player, ComputerApps.LOGBOOK, "heard", new CompoundTag()).getCompound("data").getList("stations", Tag.TAG_COMPOUND).isEmpty(),
							"heard someone before anyone spoke");
					if(!GameTestUtil.networkObject(helper, RADIO, RadioNetworkObject.class).isPowered)
						radio.toggle();
				})
				.thenWaitUntil(() -> helper.assertTrue(radio.getBareConnector() != null, "No bare connector yet"))
				.thenExecute(() -> {
					AntennaVoicePacket packet = new AntennaVoicePacket(() -> helper.getLevel(), new short[960], Band.getBand("2m"), 147_180_000.0F, 1.0D, radio.getBareConnector(), talker);
					helper.assertTrue(radio.receiveVoice(packet), "The radio did not hear the station");
					CompoundTag heard = app(computer, player, ComputerApps.LOGBOOK, "heard", new CompoundTag()).getCompound("data");
					ListTag stations = heard.getList("stations", Tag.TAG_COMPOUND);
					helper.assertTrue(heard.getInt("radios") >= 1 && stations.size() == 1, "heard: " + heard);
					CompoundTag station = stations.getCompound(0);
					helper.assertTrue(station.getString("call").equals("W1LOG"), "call " + station.getString("call"));
					helper.assertTrue(Math.abs(station.getDouble("mhz") - 147.18D) < 0.001D, "mhz " + station.getDouble("mhz"));
					helper.assertTrue(station.getString("mode").equals("FM"), "mode " + station.getString("mode"));
					helper.assertTrue(station.getString("rst").length() == 2 && station.getString("rst").endsWith("9"), "report " + station.getString("rst"));
				})
				.thenSucceed();
	}

	/**
	 * Without a receiver the solar program hears nothing; with the server's radio requirement off it reads the sky
	 * directly. Its own batch: it changes a server config value for the time of the test.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20, batch = "computer_solar")
	public static void solarNeedsAReceiver(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.SOLAR_WEATHER);
		FakePlayer player = player(helper, "solar");
		boolean needed = RadiocraftServerConfig.TELETYPE_NEEDS_RADIO.get();
		try {
			RadiocraftServerConfig.TELETYPE_NEEDS_RADIO.set(true);
			helper.assertTrue(error(app(computer, player, ComputerApps.SOLAR_WEATHER, "solar", new CompoundTag())).equals("solar_no_radio"), "heard the sky with no receiver: " + error(app(computer, player, ComputerApps.SOLAR_WEATHER, "solar", new CompoundTag())));
			RadiocraftServerConfig.TELETYPE_NEEDS_RADIO.set(false);
			CompoundTag solar = app(computer, player, ComputerApps.SOLAR_WEATHER, "solar", new CompoundTag());
			helper.assertFalse(solar.contains("error"), "solar: " + solar);
			CompoundTag data = solar.getCompound("data");
			helper.assertTrue(data.getString("source").equals("direct"), "source " + data.getString("source"));
			if(!data.getString("event").isEmpty()) // An event is picked on the level's first tick.
				helper.assertTrue(data.getList("bands", Tag.TAG_COMPOUND).size() == 5, "bands: " + data.getList("bands", Tag.TAG_COMPOUND));
			CompoundTag forecast = app(computer, player, ComputerApps.SOLAR_WEATHER, "forecast", new CompoundTag());
			helper.assertFalse(forecast.contains("error"), "forecast: " + forecast);
			helper.assertFalse(forecast.getCompound("data").getList("lines", Tag.TAG_STRING).isEmpty(), "an empty forecast");
		}
		finally {
			RadiocraftServerConfig.TELETYPE_NEEDS_RADIO.set(needed);
		}
		helper.succeed();
	}

	private static final String SOLAR_RADIO_BATCH = "computer_solar_radio";
	private static boolean solarNeededRadio = true;

	@BeforeBatch(batch = SOLAR_RADIO_BATCH)
	public static void solarNeedsRadio(ServerLevel level) {
		solarNeededRadio = RadiocraftServerConfig.TELETYPE_NEEDS_RADIO.get();
		RadiocraftServerConfig.TELETYPE_NEEDS_RADIO.set(true);
	}

	@AfterBatch(batch = SOLAR_RADIO_BATCH)
	public static void restoreSolarNeedsRadio(ServerLevel level) {
		RadiocraftServerConfig.TELETYPE_NEEDS_RADIO.set(solarNeededRadio);
	}

	/**
	 * An Ionospheric Processor touching the computer is enough, with no radio at all (maintainer, 2026-11-05): offline
	 * (no Ionosonde yet) it says so; once its Ionosonde is up the program reads the sky through it.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = SOLAR_RADIO_BATCH)
	public static void solarReadsAProcessorWithNoRadio(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.SOLAR_WEATHER);
		FakePlayer player = player(helper, "solarproc");
		BlockPos lower = COMPUTER.west();
		helper.setBlock(lower, RadiocraftBlocks.IONOSPHERIC_PROCESSOR.get().defaultBlockState()
				.setValue(com.arrl.radiocraft.common.blocks.IonosphericProcessorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
		helper.setBlock(lower.above(), RadiocraftBlocks.IONOSPHERIC_PROCESSOR.get().defaultBlockState()
				.setValue(com.arrl.radiocraft.common.blocks.IonosphericProcessorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		String offline = error(app(computer, player, ComputerApps.SOLAR_WEATHER, "solar", new CompoundTag()));
		helper.assertTrue(offline.equals("solar_processor_offline"), "a processor with no Ionosonde should say so, got '" + offline + "'");
		GameTestUtil.placeWire(helper, lower.west(), RadiocraftBlocks.COAX_WIRE.get());
		BlockPos mast = lower.west(2);
		helper.setBlock(mast, RadiocraftBlocks.IONOSONDE.get().defaultBlockState()
				.setValue(com.arrl.radiocraft.common.blocks.antennas.DoubleVHFAntennaBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
		helper.setBlock(mast.above(), RadiocraftBlocks.IONOSONDE.get().defaultBlockState()
				.setValue(com.arrl.radiocraft.common.blocks.antennas.DoubleVHFAntennaBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		helper.succeedWhen(() -> {
			CompoundTag solar = app(computer, player, ComputerApps.SOLAR_WEATHER, "solar", new CompoundTag());
			helper.assertFalse(solar.contains("error"), "the processor was not used: " + solar);
			helper.assertTrue(solar.getCompound("data").getString("source").equals("processor"), "source " + solar.getCompound("data").getString("source"));
		});
	}

	/**
	 * Every radio the computer reaches is tried, not just the nearest: a radio touching the computer with no antenna
	 * does not hide the station's VHF radio with a J-pole behind the radio interface.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 60, batch = SOLAR_RADIO_BATCH)
	public static void solarTriesEveryRadio(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.SOLAR_WEATHER);
		FakePlayer player = player(helper, "solarall");
		BlockPos bare = COMPUTER.west();
		BlockPos radioInterface = COMPUTER.east();
		BlockPos radio = radioInterface.east();
		helper.setBlock(bare, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(radioInterface, RadiocraftBlocks.RADIO_INTERFACE.get());
		GameTestUtil.radioWithJPole(helper, radio);
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					GameTestUtil.blockEntity(helper, radioInterface, RadioInterfaceBlockEntity.class).setCallsign("W1ALL", "gametest");
					for(BlockPos at : List.of(bare, radio))
						if(!GameTestUtil.networkObject(helper, at, RadioNetworkObject.class).isPowered)
							GameTestUtil.blockEntity(helper, at, RadioBlockEntity.class).toggle();
				})
				.thenIdle(4)
				.thenExecute(() -> {
					CompoundTag solar = app(computer, player, ComputerApps.SOLAR_WEATHER, "solar", new CompoundTag());
					helper.assertFalse(solar.contains("error"), "only the first radio was tried: " + solar);
					helper.assertTrue(solar.getCompound("data").getString("source").equals("radio"), "source " + solar.getCompound("data").getString("source"));
				})
				.thenSucceed();
	}

	/**
	 * Playtest bug (2026-11-05): the solar program said no radio was connected while APRS used the station's VHF radio.
	 * It looked only at radios wired to the computer, not those on the radio interface of its station. A VHF base station
	 * with a J-pole, reached only through a radio interface touching the computer, must count once it is on; on the way
	 * the errors say what is missing (no radio, radio off).
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 60, batch = SOLAR_RADIO_BATCH)
	public static void solarHearsThroughTheStationsVhfRadio(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.SOLAR_WEATHER);
		FakePlayer player = player(helper, "solarvhf");
		BlockPos radioInterface = COMPUTER.east();
		BlockPos radio = radioInterface.east(); // Two blocks from the computer: only the interface reaches it.
		helper.assertTrue(error(app(computer, player, ComputerApps.SOLAR_WEATHER, "solar", new CompoundTag())).equals("solar_no_radio"), "no radio at all should say so");
		helper.setBlock(radioInterface, RadiocraftBlocks.RADIO_INTERFACE.get());
		GameTestUtil.radioWithJPole(helper, radio);
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					GameTestUtil.blockEntity(helper, radioInterface, RadioInterfaceBlockEntity.class).setCallsign("W1SUN", "gametest");
					String problem = error(app(computer, player, ComputerApps.SOLAR_WEATHER, "solar", new CompoundTag()));
					helper.assertTrue(problem.equals("solar_radio_off"), "a radio switched off should say so, got '" + problem + "'");
					RadioNetworkObject network = GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class);
					if(!network.isPowered)
						GameTestUtil.blockEntity(helper, radio, RadioBlockEntity.class).toggle();
					helper.assertTrue(network.isPowered, "the VHF radio did not power on");
				})
				.thenIdle(4)
				.thenExecute(() -> {
					CompoundTag solar = app(computer, player, ComputerApps.SOLAR_WEATHER, "solar", new CompoundTag());
					helper.assertFalse(solar.contains("error"), "the station's VHF radio was not used: " + solar);
					helper.assertTrue(solar.getCompound("data").getString("source").equals("radio"), "source " + solar.getCompound("data").getString("source"));
				})
				.thenSucceed();
	}
}
