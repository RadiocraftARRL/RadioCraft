package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioFaxBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioInterfaceBlockEntity;
import com.arrl.radiocraft.common.blockentities.ScannerBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.Logbook;
import com.arrl.radiocraft.common.computer.NetLog;
import com.arrl.radiocraft.common.computer.apps.LogbookHandler;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.QslAlbumItem;
import com.arrl.radiocraft.common.network.serverbound.SQslCardPacket;
import com.arrl.radiocraft.common.qsl.QslArt;
import com.arrl.radiocraft.common.qsl.QslCard;
import com.arrl.radiocraft.common.qsl.QslCards;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Computer batch 3C in the world: QSL cards printed in runs, filled in and signed, stacked, faxed and kept in an album;
 * the Net Control logger's report printed and its check-ins logged; the Awards' certificate; and the logbook's
 * "heard" stations carrying no coordinates of other players.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class QslAwardsNetGameTests {

	private static final BlockPos COMPUTER = new BlockPos(4, 2, 4);
	private static final BlockPos SCANNER = COMPUTER.east();
	private static final BlockPos RADIO = COMPUTER.west();

	private static FakePlayer player(GameTestHelper helper, String name) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("qsl-" + name).getBytes()), name));
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

	private static QslCard template(String call) {
		return new QslCard(QslCard.Design.DEFAULT.withCall(call), QslCard.Fill.EMPTY, false);
	}

	private static QslCard signed(String from, String to) {
		QslCard.Fill fill = new QslCard.Fill("", to, "2M", "147.180", "FM", "59", "Thanks", 0L, "", "", false);
		QslCard card = template(from).filled(fill, 1_000L, "D1 1200", "Ana", true);
		if(card == null)
			throw new IllegalStateException("could not sign");
		return card;
	}

	// -------------------- QSL CARDS --------------------

	/** A template prints a run of identical blank cards, a sheet each, that stack; a full output tray jams the next run. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void printARunOfBlankCards(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.QSL);
		ScannerBlockEntity scanner = scanner(helper);
		FakePlayer player = player(helper, "print");
		computer.files().write("W1BTR.QSL", QslCards.file(template("W1BTR")));
		scanner.getInventory().setStackInSlot(ScannerBlockEntity.TRAY, new ItemStack(Items.PAPER, 8));
		CompoundTag data = new CompoundTag();
		data.putString("name", "W1BTR.QSL");
		data.putInt("count", 5);
		CompoundTag reply = app(computer, player, ComputerApps.QSL, "print", data);
		helper.assertTrue(reply.getString("error").isEmpty() && reply.getCompound("data").getInt("printed") == 5, "print: " + reply);
		ItemStack out = scanner.output();
		helper.assertTrue(QslCards.is(out) && out.getCount() == 5, "the output tray holds " + out);
		QslCard card = QslCards.card(out);
		helper.assertTrue(card != null && card.blank() && card.from().equals("W1BTR"), "the cards are " + card);
		helper.assertTrue(scanner.tray().getCount() == 3, "used " + (8 - scanner.tray().getCount()) + " sheets for five cards");
		ItemStack again = QslCards.stack(template("W1BTR"), 1);
		helper.assertTrue(ItemStack.isSameItemSameComponents(out, again), "two prints of one template do not stack");
		helper.assertTrue(app(computer, player, ComputerApps.QSL, "print", data).getString("error").equals("output_full"), "a full output tray did not jam");
		scanner.getInventory().setStackInSlot(ScannerBlockEntity.OUTPUT, ItemStack.EMPTY);
		scanner.getInventory().setStackInSlot(ScannerBlockEntity.TRAY, new ItemStack(RadiocraftItems.PHOTO_PAPER.get(), 2));
		app(computer, player, ComputerApps.QSL, "print", data);
		helper.assertTrue(scanner.output().getCount() == 2 && scanner.tray().isEmpty(), "photo paper printed " + scanner.output().getCount() + " of the five asked");
		helper.succeed();
	}

	/** Filling a card from a stack takes one off; signing locks it; a signed card cannot be written on again. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void fillingAndSigningLocksTheCard(GameTestHelper helper) {
		FakePlayer player = player(helper, "sign");
		player.setItemInHand(InteractionHand.MAIN_HAND, QslCards.stack(template(""), 3)); // Made for a player with no computer.
		QslCard.Fill written = new QslCard.Fill("K2XYZ", "W1BTR", "20M", "14.225", "USB", "57", "Hello", 0L, "", "", false);
		helper.assertTrue(SQslCardPacket.apply(player, InteractionHand.MAIN_HAND, written, false) == null, "could not write on a blank card");
		helper.assertTrue(player.getMainHandItem().getCount() == 2, "the stack in the hand is " + player.getMainHandItem().getCount());
		ItemStack draft = ItemStack.EMPTY;
		for(ItemStack stack : player.getInventory().items)
			if(QslCards.is(stack) && stack != player.getMainHandItem() && !QslCards.card(stack).blank())
				draft = stack;
		helper.assertTrue(!draft.isEmpty(), "the written card did not reach the inventory");
		QslCard card = QslCards.card(draft);
		helper.assertTrue(!card.signed() && card.from().equals("K2XYZ") && card.fill().utc() > 0L, "the draft is " + card);
		player.setItemInHand(InteractionHand.MAIN_HAND, draft.copy());
		helper.assertTrue(SQslCardPacket.apply(player, InteractionHand.MAIN_HAND, new QslCard.Fill("K2XYZ", "", "", "", "", "", "", 0L, "", "", false), true)
				.equals("need_to"), "signed with nobody to send it to");
		helper.assertTrue(SQslCardPacket.apply(player, InteractionHand.MAIN_HAND, written, true) == null, "could not sign");
		QslCard signed = QslCards.card(player.getMainHandItem());
		helper.assertTrue(signed.signed() && signed.fill().signer().equals("sign"), "signed card " + signed);
		helper.assertTrue("signed".equals(SQslCardPacket.apply(player, InteractionHand.MAIN_HAND, written, false)), "a signed card was changed");
		helper.assertTrue(QslCards.card(player.getMainHandItem()).equals(signed), "the signed card changed");
		helper.succeed();
	}

	/** An album takes cards (identical ones in one stack), gives them back one at a time, and holds no more than its cap. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void albumHoldsCards(GameTestHelper helper) {
		ItemStack album = new ItemStack(RadiocraftItems.QSL_ALBUM.get());
		ItemStack blanks = QslCards.stack(template("W1BTR"), 40);
		ItemStack one = QslCards.stack(signed("W1BTR", "K2XYZ"), 1);
		QslCards.flip(one);
		helper.assertTrue(QslAlbumItem.add(album, blanks) == 40 && blanks.isEmpty(), "the album took " + QslAlbumItem.count(album));
		helper.assertTrue(QslAlbumItem.add(album, one) == 1, "the signed card did not go in");
		helper.assertTrue(QslAlbumItem.cards(album).size() == 2, "the album has " + QslAlbumItem.cards(album).size() + " stacks");
		helper.assertTrue(!QslCards.flipped(QslAlbumItem.cards(album).get(1)), "a card goes in front up");
		ItemStack more = QslCards.stack(template("N3ZZ"), 20);
		helper.assertTrue(QslAlbumItem.add(album, more) == QslAlbumItem.MAX_CARDS - 41 && more.getCount() == 20 - (QslAlbumItem.MAX_CARDS - 41),
				"over the cap: " + QslAlbumItem.count(album));
		ItemStack taken = QslAlbumItem.take(album, 1);
		helper.assertTrue(QslCards.card(taken).signed(), "took " + taken);
		helper.assertTrue(QslAlbumItem.count(album) == QslAlbumItem.MAX_CARDS - 1, "count " + QslAlbumItem.count(album));
		helper.assertTrue(QslAlbumItem.add(album, new ItemStack(Items.PAPER)) == 0, "the album took paper");
		helper.succeed();
	}

	private static void faxStation(GameTestHelper helper, BlockPos radioPos, int kHz, String callsign, Direction faxSide) {
		BlockPos interfacePos = radioPos.north();
		helper.setBlock(radioPos, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(interfacePos, RadiocraftBlocks.RADIO_INTERFACE.get());
		helper.setBlock(interfacePos.relative(faxSide), RadiocraftBlocks.RADIO_FAXES.get("oak").get());
		GameTestUtil.blockEntity(helper, radioPos, RadioBlockEntity.class).setFrequency(kHz * 1000.0F);
		GameTestUtil.blockEntity(helper, interfacePos, RadioInterfaceBlockEntity.class).setCallsign(callsign, "gametest");
	}

	private static void powerOn(GameTestHelper helper, BlockPos pos) {
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, pos, RadioNetworkObject.class);
		if(!networkObject.isPowered)
			GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).toggle();
		helper.assertTrue(networkObject.isPowered, "Radio at " + pos.toShortString() + " did not power on");
	}

	/** A signed card faxed to another station prints there as a QSL card, a copy, on its paper; the sender keeps its own. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 1200, batch = "fax")
	public static void faxedCardPrintsACopy(GameTestHelper helper) {
		BlockPos radioA = new BlockPos(3, 2, 6), radioB = radioA.east(2);
		BlockPos faxA = radioA.north().west(), faxB = radioB.north().east();
		faxStation(helper, radioA, 145_410, "W1QAA", Direction.WEST);
		faxStation(helper, radioB, 145_410, "W1QBB", Direction.EAST);
		QslCard card = signed("W1QAA", "W1QBB");
		helper.startSequence()
				.thenExecute(() -> {
					powerOn(helper, radioA);
					powerOn(helper, radioB);
					GameTestUtil.blockEntity(helper, faxA, RadioFaxBlockEntity.class).getOutbox().setStackInSlot(0, QslCards.stack(card, 1));
					GameTestUtil.blockEntity(helper, faxB, RadioFaxBlockEntity.class).insertSupply(new ItemStack(Items.PAPER, 2));
				})
				.thenIdle(1)
				.thenExecute(() -> helper.assertTrue(GameTestUtil.blockEntity(helper, faxA, RadioFaxBlockEntity.class).send("W1QBB") == null, "A could not send the card"))
				.thenWaitUntil(() -> helper.assertFalse(GameTestUtil.blockEntity(helper, faxB, RadioFaxBlockEntity.class).getReceived().getStackInSlot(0).isEmpty(),
						"B never printed the card"))
				.thenExecute(() -> {
					ItemStack copy = GameTestUtil.blockEntity(helper, faxB, RadioFaxBlockEntity.class).getReceived().getStackInSlot(0);
					QslCard got = QslCards.card(copy);
					helper.assertTrue(got != null && got.copy() && got.signed() && got.fill().equals(card.fill()) && got.design().equals(card.design()),
							"B printed " + copy + " = " + got);
					helper.assertTrue(GameTestUtil.blockEntity(helper, faxB, RadioFaxBlockEntity.class).getSupplies().getStackInSlot(RadioFaxBlockEntity.SLOT_PAPER).getCount() == 1,
							"B did not use one sheet");
					helper.assertTrue(QslCards.card(GameTestUtil.blockEntity(helper, faxA, RadioFaxBlockEntity.class).getOutbox().getStackInSlot(0)).equals(card), "A lost its card");
				})
				.thenSucceed();
	}

	// -------------------- NET CONTROL AND AWARDS --------------------

	/** The net report prints on the scanner as a book; closing with "log" puts every check-in in LOGBOOK.LOG. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void netReportPrintsAndCheckInsAreLogged(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.NET_CONTROL);
		ScannerBlockEntity scanner = scanner(helper);
		FakePlayer player = player(helper, "net");
		NetLog net = new NetLog();
		net.setup("Tuesday Net", "147.180", "FM", "W1BTR");
		net.open(System.currentTimeMillis());
		net.checkIn("K2XYZ", "Bob", "JJ00", true, false, System.currentTimeMillis());
		net.checkIn("N3ZZ", "Zed", "", false, true, System.currentTimeMillis());
		scanner.getInventory().setStackInSlot(ScannerBlockEntity.TRAY, new ItemStack(Items.PAPER, 4));
		CompoundTag print = new CompoundTag();
		print.putString("title", "Tuesday Net");
		print.putString("text", String.join("\n", net.summary()));
		CompoundTag printed = call(computer, player, "print.text", print);
		helper.assertTrue(printed.getString("error").isEmpty() && scanner.output().is(Items.WRITTEN_BOOK), "the report printed " + printed + " " + scanner.output());
		CompoundTag data = new CompoundTag();
		data.put("net", net.toTag());
		CompoundTag logged = app(computer, player, ComputerApps.NET_CONTROL, "log", data);
		helper.assertTrue(logged.getCompound("data").getInt("logged") == 2, "logged " + logged);
		Logbook log = Logbook.fromTag(computer.files().readNbt(LogbookHandler.LOG_FILE));
		helper.assertTrue(log.size() == 2 && log.countWith("K2XYZ") == 1 && log.get(0).mode().equals("FM"), "the log has " + log.entries());
		helper.succeed();
	}

	/** Contacts on three bands earn Worked All Bands' first level, and its certificate prints on plain paper. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void awardsCountTheLogAndPrintACertificate(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.AWARDS);
		ScannerBlockEntity scanner = scanner(helper);
		FakePlayer player = player(helper, "awards");
		Logbook log = new Logbook();
		log.add(new Logbook.Entry(1L, "D1 2200", "A1A", 14.2D, "USB", "59", "59", "", Logbook.Where.grid("JJ00")));
		log.add(new Logbook.Entry(2L, "D1 2300", "B2B", 7.1D, "CW", "599", "599", ""));
		log.add(new Logbook.Entry(3L, "D1 1200", "C3C", 3.6D, "FT8", "-10", "-12", "", new Logbook.Where("", "minecraft:the_nether", "", -1)));
		computer.files().writeNbt(LogbookHandler.LOG_FILE, log.toTag());
		CompoundTag status = app(computer, player, ComputerApps.AWARDS, "status", new CompoundTag()).getCompound("data");
		ListTag awards = status.getList("awards", Tag.TAG_COMPOUND);
		CompoundTag bands = null, dims = null, biomes = null;
		for(int i = 0; i < awards.size(); i++) {
			CompoundTag a = awards.getCompound(i);
			switch(a.getString("id")) {
				case "bands" -> bands = a;
				case "dimensions" -> dims = a;
				case "biomes" -> biomes = a;
				default -> {}
			}
		}
		helper.assertTrue(status.getInt("contacts") == 3, "status " + status);
		helper.assertTrue(bands != null && bands.getLong("count") == 3 && bands.getInt("level") == 1, "bands " + bands);
		helper.assertTrue(dims != null && dims.getLong("count") == 2, "dimensions " + dims);
		helper.assertTrue(biomes != null && biomes.getLong("count") == 1, "the grid's centre gives a biome: " + biomes);
		scanner.getInventory().setStackInSlot(ScannerBlockEntity.TRAY, new ItemStack(Items.PAPER, 1));
		CompoundTag data = new CompoundTag();
		data.putString("award", "bands");
		CompoundTag reply = app(computer, player, ComputerApps.AWARDS, "certificate", data);
		helper.assertTrue(reply.getString("error").isEmpty() && scanner.output().is(Items.FILLED_MAP) && scanner.tray().isEmpty(),
				"certificate " + reply + " " + scanner.output());
		data.putString("award", "contacts");
		scanner.getInventory().setStackInSlot(ScannerBlockEntity.OUTPUT, ItemStack.EMPTY);
		helper.assertTrue(app(computer, player, ComputerApps.AWARDS, "certificate", data).getString("error").equals("awards_not_earned"), "three contacts earned the ladder");
		helper.succeed();
	}

	/**
	 * The logbook's "heard" answer says where a heard station was only coarsely (grid square, dimension, biome, a
	 * distance to 100 blocks), from the snapshot taken when it was heard: never block coordinates.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100)
	public static void heardStationsCarryNoCoordinates(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.LOGBOOK);
		helper.setBlock(RADIO, RadiocraftBlocks.VHF_BASE_STATION.get());
		RadioBlockEntity radio = GameTestUtil.blockEntity(helper, RADIO, RadioBlockEntity.class);
		radio.setFrequency(146_470_000.0F);
		FakePlayer player = player(helper, "heard");
		UUID talker = UUID.nameUUIDFromBytes("gametest-qsl-talker".getBytes());
		helper.startSequence()
				.thenExecute(() -> {
					if(!GameTestUtil.networkObject(helper, RADIO, RadioNetworkObject.class).isPowered)
						radio.toggle();
				})
				.thenWaitUntil(() -> helper.assertTrue(radio.getBareConnector() != null, "No bare connector yet"))
				.thenExecute(() -> {
					AntennaVoicePacket packet = new AntennaVoicePacket(() -> helper.getLevel(), new short[960], Band.getBand("2m"), 146_470_000.0F, 1.0D, radio.getBareConnector(), talker);
					helper.assertTrue(radio.receiveVoice(packet), "The radio did not hear the station");
					CompoundTag heard = app(computer, player, ComputerApps.LOGBOOK, "heard", new CompoundTag()).getCompound("data");
					ListTag stations = heard.getList("stations", Tag.TAG_COMPOUND);
					helper.assertTrue(stations.size() == 1, "heard " + heard);
					CompoundTag station = stations.getCompound(0);
					CompoundTag where = station.getCompound("where");
					helper.assertTrue(Set.of("grid", "dim", "biome", "dist").containsAll(where.getAllKeys()), "where carries " + where.getAllKeys());
					helper.assertTrue(!where.contains("x") && !where.contains("z") && !station.contains("x") && !station.contains("z") && !station.contains("pos"),
							"coordinates went to the client: " + station);
					BlockPos at = helper.absolutePos(RADIO);
					helper.assertTrue(where.getString("grid").equals(com.arrl.radiocraft.common.radio.ft8.Ft8.grid(at.getX(), at.getZ())), "grid " + where);
					helper.assertTrue(where.getInt("dist") % Logbook.Where.DISTANCE_STEP == 0, "distance not rounded: " + where);
					helper.assertTrue(where.getString("dim").equals(helper.getLevel().dimension().location().toString()), "dimension " + where);
				})
				.thenSucceed();
	}

	/** A card's art fits the map picture a faxed card goes out as. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 5)
	public static void cardPictureIsAMapPicture(GameTestHelper helper) {
		byte[] picture = QslCards.frontPicture(signed("W1BTR", "K2XYZ"));
		helper.assertTrue(picture.length == 128 * 128 && QslArt.WIDTH == 128, "the card's picture is " + picture.length);
		helper.assertTrue(com.arrl.radiocraft.common.radio.data.FileTransfer.canSend(QslCards.stack(signed("W1BTR", "K2XYZ"), 1)), "a fax cannot send a card");
		helper.assertTrue(List.of(com.arrl.radiocraft.common.radio.data.FileTransfer.describe(QslCards.stack(signed("W1BTR", "K2XYZ"), 1))).getFirst().startsWith("QSL W1BTR"),
				"the fax calls it " + com.arrl.radiocraft.common.radio.data.FileTransfer.describe(QslCards.stack(signed("W1BTR", "K2XYZ"), 1)));
		helper.succeed();
	}
}
