package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.AprsTerminalBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioFaxBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioInterfaceBlockEntity;
import com.arrl.radiocraft.common.blockentities.WefaxReceiverBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.DfSearches;
import com.arrl.radiocraft.common.computer.apps.AprsHandler;
import com.arrl.radiocraft.common.computer.apps.FaxHandler;
import com.arrl.radiocraft.common.computer.apps.FaxModem;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.photo.PhotoImage;
import com.arrl.radiocraft.common.photo.PhotoPalette;
import com.arrl.radiocraft.common.photo.PhotoPrints;
import com.arrl.radiocraft.common.radio.data.FileTransfer;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * The radio computer's radio programs (1.12 step 7), server side, driven as their screens drive them: APRS messages
 * acked between two computers, a computer faxing a fax machine and a fax machine faxing a computer, an SSTV picture from
 * one computer to another, a WEFAX chart a computer sends printing on a WEFAX receiver, and the direction finding
 * program reading a cabled direction finder and saving searches. Each station is a VHF base station on its bare
 * connector with a radio interface north of it and the computer (or machine) beside that, two blocks from the other,
 * on a frequency of its own. Also the computer's packet dedupe (a packet from two interfaces counts once).
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class RadioAppsGameTests {

	private static final String BATCH = "radio_apps";
	private static final BlockPos RADIO_A = new BlockPos(3, 2, 6);
	private static final BlockPos RADIO_B = RADIO_A.east(2);
	private static final BlockPos INTERFACE_A = RADIO_A.north(), INTERFACE_B = RADIO_B.north();
	private static final BlockPos AT_A = INTERFACE_A.west(), AT_B = INTERFACE_B.east(); // Away from each other: separate buses.

	private static FakePlayer player(GameTestHelper helper, String name) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("radioapps-" + name).getBytes()), name));
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

	/** One program request as its screen sends it; the reply's tag ({@code data}, maybe {@code error}). */
	private static CompoundTag app(RadioComputerBlockEntity computer, FakePlayer player, ComputerApp app, String action, CompoundTag data) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", app.id());
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

	/** A picture of a few colours in bands, as map colour ids the palette has. */
	private static byte[] picture() {
		PhotoPalette palette = MapPalettes.vanilla();
		byte[] pixels = new byte[PhotoImage.PIXELS];
		int[] colours = { 0xC03020, 0x2050C0, 0x30A040, 0xF0F0F0 };
		for(int i = 0; i < pixels.length; i++)
			pixels[i] = palette.id(palette.nearest(colours[(i / 128 / 32) % colours.length]));
		return pixels;
	}

	// -------------------- APRS --------------------

	/** A message from one computer to another arrives once and is acked: the sender shows it SENT. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 400, batch = BATCH)
	public static void aprsMessageIsAcked(GameTestHelper helper) {
		station(helper, RADIO_A, 144_930, "W1APA");
		station(helper, RADIO_B, 144_930, "W1APB");
		RadioComputerBlockEntity a = computer(helper, AT_A, ComputerApps.APRS);
		RadioComputerBlockEntity b = computer(helper, AT_B, ComputerApps.APRS);
		FakePlayer player = player(helper, "aprs");
		helper.startSequence()
				.thenExecute(() -> {
					powerOn(helper, RADIO_A);
					powerOn(helper, RADIO_B);
				})
				.thenIdle(1)
				.thenExecute(() -> {
					CompoundTag send = new CompoundTag();
					send.putString("to", "w1apb");
					send.putString("text", "Hello from the computer");
					CompoundTag reply = app(a, player, ComputerApps.APRS, "send", send);
					helper.assertFalse(reply.contains("error"), "A could not send: " + reply);
				})
				.thenWaitUntil(() -> helper.assertTrue(AprsHandler.state(b).messages().stream().anyMatch(m -> m.packet().messageBody().equals("Hello from the computer")),
						"B never got the message: " + AprsHandler.state(b).messages()))
				.thenWaitUntil(() -> helper.assertTrue(AprsHandler.state(a).messages().getFirst().delivery() == AprsTerminalBlockEntity.Delivery.SENT,
						"A's message never acked: " + AprsHandler.state(a).messages().getFirst().delivery()))
				// Beacon only after the ack: a beacon queued behind the message is still going out (it is longer than
				// the ack) when B's ack arrives, and a transceiver is deaf while it sends, so the ack would be lost
				// until the 20 s retry, past this test's timeout.
				.thenExecute(() -> helper.assertFalse(app(a, player, ComputerApps.APRS, "beacon", new CompoundTag()).contains("error"), "A could not beacon"))
				.thenWaitUntil(() -> helper.assertTrue(AprsHandler.state(b).stations().stream().anyMatch(s -> s.packet().from().equals("W1APA")), "B never heard A's beacon"))
				.thenExecute(() -> {
					CompoundTag status = app(b, player, ComputerApps.APRS, "status", new CompoundTag()).getCompound("data");
					helper.assertTrue(status.getList("messages", Tag.TAG_COMPOUND).size() == 1, "B logged the message more than once: " + status);
					helper.assertTrue(status.getString("callsign").equals("W1APB"), "B's status: " + status);
					helper.assertTrue(status.getList("stations", Tag.TAG_COMPOUND).getCompound(0).getInt("distance") == 4, "distance: " + status);
				})
				.thenSucceed();
	}

	/** A packet the computer hears through two radio interfaces reaches its programs once (1.12 step 7 fix). */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void aPacketCountsOnce(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, AT_A, ComputerApps.APRS);
		TncPacket bulletin = TncPacket.message(12345L, "W1BUL", TncPacket.EVERYONE, "Net tonight");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(computer.isPowered(), "the computer with APRS installed did not come on")) // A tick or two.
				.thenExecute(() -> {
					computer.receive(bulletin);
					computer.receive(bulletin);
					helper.assertTrue(AprsHandler.state(computer).messages().size() == 1, "the bulletin came in " + AprsHandler.state(computer).messages().size() + " times");
					computer.receive(bulletin.withId(54321L));
					helper.assertTrue(AprsHandler.state(computer).messages().size() == 2, "a new packet was not taken");
				})
				.thenSucceed();
	}

	// -------------------- RADIO FAX --------------------

	/** The computer faxes a picture to a fax machine, which prints it on its blank map: a real map of the same picture. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 1400, batch = BATCH)
	public static void computerFaxesAMachine(GameTestHelper helper) {
		station(helper, RADIO_A, 144_950, "W1FXA");
		station(helper, RADIO_B, 144_950, "W1FXB");
		RadioComputerBlockEntity a = computer(helper, AT_A, ComputerApps.RADIO_FAX);
		helper.setBlock(AT_B, RadiocraftBlocks.RADIO_FAXES.get("oak").get());
		FakePlayer player = player(helper, "fax1");
		byte[] pixels = picture();
		helper.startSequence()
				.thenExecute(() -> {
					powerOn(helper, RADIO_A);
					powerOn(helper, RADIO_B);
					a.files().write("PIC.IMG", pixels);
					GameTestUtil.blockEntity(helper, AT_B, RadioFaxBlockEntity.class).insertSupply(new ItemStack(Items.MAP, 2));
				})
				.thenIdle(1)
				.thenExecute(() -> {
					CompoundTag send = new CompoundTag();
					send.putString("name", "PIC.IMG");
					send.putString("to", "W1FXB");
					CompoundTag reply = app(a, player, ComputerApps.RADIO_FAX, "send", send);
					helper.assertFalse(reply.contains("error"), "A could not fax: " + reply);
				})
				.thenWaitUntil(() -> helper.assertFalse(GameTestUtil.blockEntity(helper, AT_B, RadioFaxBlockEntity.class).getReceived().getStackInSlot(0).isEmpty(),
						"B never printed: " + FaxHandler.state(a).modem().status()))
				.thenWaitUntil(() -> helper.assertTrue(FaxHandler.state(a).modem().phase() == FaxModem.Phase.IDLE && FaxHandler.state(a).modem().status().startsWith("SENT"),
						"A never heard the last ack: " + FaxHandler.state(a).modem().status()))
				.thenExecute(() -> {
					ItemStack printed = GameTestUtil.blockEntity(helper, AT_B, RadioFaxBlockEntity.class).getReceived().getStackInSlot(0);
					helper.assertTrue(printed.is(Items.FILLED_MAP) && printed.has(DataComponents.MAP_ID) && !FileTransfer.isVirtualImage(printed), "B printed " + printed);
					helper.assertTrue(java.util.Arrays.equals(PhotoPrints.pixels(helper.getLevel(), printed), MapPalettes.vanilla().sanitised(pixels)), "the picture changed on the way");
					helper.assertTrue(GameTestUtil.blockEntity(helper, AT_B, RadioFaxBlockEntity.class).getSupplies().getStackInSlot(RadioFaxBlockEntity.SLOT_MAP).getCount() == 1,
							"B did not use one blank map");
				})
				.thenSucceed();
	}

	/** A fax machine faxes a book to a computer, which keeps it as a text file in its inbox. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 1400, batch = BATCH)
	public static void machineFaxesAComputer(GameTestHelper helper) {
		station(helper, RADIO_A, 144_970, "W1FXC");
		station(helper, RADIO_B, 144_970, "W1FXD");
		helper.setBlock(AT_A, RadiocraftBlocks.RADIO_FAXES.get("oak").get());
		RadioComputerBlockEntity b = computer(helper, AT_B, ComputerApps.RADIO_FAX);
		FakePlayer player = player(helper, "fax2");
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Station Log"), "W1AW", 0,
				List.of(Filterable.passThrough(Component.literal("Page one")), Filterable.passThrough(Component.literal("Page two"))), true));
		helper.startSequence()
				.thenExecute(() -> {
					powerOn(helper, RADIO_A);
					powerOn(helper, RADIO_B);
					GameTestUtil.blockEntity(helper, AT_A, RadioFaxBlockEntity.class).getOutbox().setStackInSlot(0, book);
				})
				.thenIdle(1)
				.thenExecute(() -> helper.assertTrue(GameTestUtil.blockEntity(helper, AT_A, RadioFaxBlockEntity.class).send("W1FXD") == null, "A could not send"))
				.thenWaitUntil(() -> helper.assertTrue(b.files().storage().exists("FAX00001.TXT"),
						"B never kept it: " + FaxHandler.state(b).modem().status() + " / A " + GameTestUtil.blockEntity(helper, AT_A, RadioFaxBlockEntity.class).getStatus()))
				.thenWaitUntil(() -> helper.assertFalse(GameTestUtil.blockEntity(helper, AT_A, RadioFaxBlockEntity.class).isBusy(), "A never heard the last ack"))
				.thenExecute(() -> {
					String text = b.files().readText("FAX00001.TXT");
					helper.assertTrue(text != null && text.contains("Page one") && text.contains("Page two"), "B kept: " + text);
					CompoundTag status = app(b, player, ComputerApps.RADIO_FAX, "status", new CompoundTag()).getCompound("data");
					helper.assertTrue(status.getList("inbox", Tag.TAG_COMPOUND).getCompound(0).getString("from").equals("W1FXC"), "inbox: " + status);
					helper.assertTrue(GameTestUtil.blockEntity(helper, AT_A, RadioFaxBlockEntity.class).getStatus().startsWith("SENT"), "A shows "
							+ GameTestUtil.blockEntity(helper, AT_A, RadioFaxBlockEntity.class).getStatus());
				})
				.thenSucceed();
	}

	// -------------------- SSTV AND WEFAX --------------------

	/** An SSTV picture sent to all arrives at the other computer, line by line, and keeps as SSTV0001.IMG. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 1000, batch = BATCH)
	public static void sstvPictureArrives(GameTestHelper helper) {
		station(helper, RADIO_A, 144_990, "W1SSA");
		station(helper, RADIO_B, 144_990, "W1SSB");
		RadioComputerBlockEntity a = computer(helper, AT_A, ComputerApps.SSTV);
		RadioComputerBlockEntity b = computer(helper, AT_B, ComputerApps.SSTV);
		FakePlayer player = player(helper, "sstv");
		byte[] pixels = picture();
		helper.startSequence()
				.thenExecute(() -> {
					powerOn(helper, RADIO_A);
					powerOn(helper, RADIO_B);
					a.files().write("PIC.IMG", pixels);
				})
				.thenIdle(1)
				.thenExecute(() -> {
					CompoundTag send = new CompoundTag();
					send.putString("name", "PIC.IMG");
					CompoundTag reply = app(a, player, ComputerApps.SSTV, "send", send);
					helper.assertFalse(reply.contains("error"), "A could not send: " + reply);
				})
				.thenWaitUntil(() -> helper.assertTrue(app(b, player, ComputerApps.SSTV, "status", new CompoundTag()).getCompound("data").getBoolean("receiving"),
						"B never heard the picture start"))
				.thenWaitUntil(() -> helper.assertTrue(b.files().storage().exists("SSTV0001.IMG"), "B never kept the picture"))
				.thenExecute(() -> {
					byte[] got = b.files().read("SSTV0001.IMG");
					byte[] sent = MapPalettes.vanilla().sanitised(pixels);
					int same = 0;
					for(int i = 0; i < got.length; i++)
						if(got[i] == sent[i])
							same++;
					helper.assertTrue(same > got.length * 3 / 4, "the picture came mostly as noise: " + same + " of " + got.length);
					CompoundTag status = app(b, player, ComputerApps.SSTV, "status", new CompoundTag()).getCompound("data");
					helper.assertTrue(status.getList("received", Tag.TAG_COMPOUND).getCompound(0).getString("from").equals("W1SSA"), "received: " + status);
				})
				.thenSucceed();
	}

	/** A computer's WEFAX chart prints on a WEFAX receiver with AUTO on, beside the far station's radio. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 900, batch = BATCH)
	public static void wefaxChartPrintsOnAReceiver(GameTestHelper helper) {
		station(helper, RADIO_A, 145_030, "W1WXA");
		helper.setBlock(RADIO_B, RadiocraftBlocks.VHF_BASE_STATION.get());
		BlockPos receiverPos = RADIO_B.east();
		helper.setBlock(receiverPos, RadiocraftBlocks.WEFAX_RECEIVER.get().defaultBlockState()
				.setValue(com.arrl.radiocraft.common.blocks.WefaxReceiverBlock.FACING, Direction.SOUTH));
		RadioComputerBlockEntity a = computer(helper, AT_A, ComputerApps.WEFAX);
		FakePlayer player = player(helper, "wefax");
		helper.startSequence()
				.thenExecute(() -> {
					GameTestUtil.blockEntity(helper, RADIO_B, RadioBlockEntity.class).setFrequency(145_030_000.0F);
					powerOn(helper, RADIO_A);
					powerOn(helper, RADIO_B);
					a.files().write("CHART.IMG", picture());
					WefaxReceiverBlockEntity receiver = GameTestUtil.blockEntity(helper, receiverPos, WefaxReceiverBlockEntity.class);
					receiver.insertPaper(new ItemStack(Items.PAPER, 2));
					receiver.setAuto(true);
				})
				.thenIdle(1)
				.thenExecute(() -> {
					CompoundTag reply = app(a, player, ComputerApps.WEFAX, "send", tag("name", "CHART.IMG"));
					helper.assertFalse(reply.contains("error"), "A could not send: " + reply);
				})
				.thenWaitUntil(() -> helper.assertTrue(GameTestUtil.blockEntity(helper, receiverPos, WefaxReceiverBlockEntity.class).isPrinting(), "the receiver never started"))
				.thenWaitUntil(() -> helper.assertTrue(GameTestUtil.blockEntity(helper, receiverPos, WefaxReceiverBlockEntity.class).hasReadySheet(), "no sheet printed"))
				.thenExecute(() -> helper.assertTrue(GameTestUtil.blockEntity(helper, receiverPos, WefaxReceiverBlockEntity.class).getPaper() == 1, "a sheet should be used"))
				.thenSucceed();
	}

	/** WEFAX draws a chart of the weather here into a file, for the station to send as its own. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void wefaxDrawsAChart(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, AT_A, ComputerApps.WEFAX);
		FakePlayer player = player(helper, "chart");
		CompoundTag reply = app(computer, player, ComputerApps.WEFAX, "chart", tag("kind", "radar"));
		helper.assertFalse(reply.contains("error"), "no chart: " + reply);
		byte[] chart = computer.files().read("WXRADAR.IMG");
		helper.assertTrue(chart != null && chart.length == PhotoImage.PIXELS, "WXRADAR.IMG is " + (chart == null ? "missing" : chart.length + " bytes"));
		helper.assertTrue(app(computer, player, ComputerApps.WEFAX, "wxrept", new CompoundTag()).getString("error").equals("wefax_no_antenna")
				|| com.arrl.radiocraft.RadiocraftServerConfig.WEFAX_WEATHER_ALWAYS.get(), "WX REPT heard the coast station with no radio");
		helper.succeed();
	}

	// -------------------- DIRECTION FINDING --------------------

	/** The program reads a direction finder beside the computer, types bearings into it, fixes and saves the search. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void directionFindingSavesSearches(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, AT_A, ComputerApps.DIRECTION_FINDING);
		FakePlayer player = player(helper, "df");
		helper.assertTrue(app(computer, player, ComputerApps.DIRECTION_FINDING, "power", new CompoundTag()).getString("error").equals("df_no_finder"),
				"a set answered with none cabled");
		helper.setBlock(AT_A.east(), RadiocraftBlocks.DIRECTION_FINDER.get());
		for(double[] entry : new double[][] { { 0, 0, 45 }, { 200, 0, 315 } }) {
			CompoundTag add = new CompoundTag();
			add.putInt("x", (int)entry[0]);
			add.putInt("z", (int)entry[1]);
			add.putDouble("bearing", entry[2]);
			add.putDouble("sigma", 3.0D);
			helper.assertFalse(app(computer, player, ComputerApps.DIRECTION_FINDING, "add", add).contains("error"), "could not type a bearing in");
		}
		CompoundTag status = app(computer, player, ComputerApps.DIRECTION_FINDING, "status", new CompoundTag()).getCompound("data");
		helper.assertTrue(status.getBoolean("finder"), "the set is not seen: " + status);
		CompoundTag fix = status.getCompound("fix");
		helper.assertTrue(fix.getBoolean("found") && Math.abs(fix.getDouble("x") - 100.5D) < 2 && Math.abs(fix.getDouble("z") + 99.5D) < 2, "fix: " + fix);
		helper.assertFalse(app(computer, player, ComputerApps.DIRECTION_FINDING, "save", tag("note", "fox hunt")).contains("error"), "could not save");
		CompoundTag searches = app(computer, player, ComputerApps.DIRECTION_FINDING, "searches", new CompoundTag()).getCompound("data");
		helper.assertTrue(searches.getList("searches", Tag.TAG_COMPOUND).size() == 1, "searches: " + searches);
		helper.assertTrue(computer.files().storage().exists(DfSearches.FILE), "no " + DfSearches.FILE);
		CompoundTag map = new CompoundTag();
		map.putInt("search", 0);
		CompoundTag painted = app(computer, player, ComputerApps.DIRECTION_FINDING, "map", map).getCompound("data");
		helper.assertTrue(painted.getByteArray("pixels").length == PhotoImage.PIXELS && painted.getList("bearings", Tag.TAG_COMPOUND).size() == 2, "map: " + painted.getAllKeys());
		helper.succeed();
	}
}
