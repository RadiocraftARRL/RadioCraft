package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.be_networks.network_objects.BatteryNetworkObject;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.ScannerBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerFileStore;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.ComputerUploads;
import com.arrl.radiocraft.common.datacomponents.ComputerData;
import com.arrl.radiocraft.common.datacomponents.PhotoInfo;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.photo.PhotoImage;
import com.arrl.radiocraft.common.photo.PhotoPalette;
import com.arrl.radiocraft.common.photo.PhotoPrints;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The radio computer platform (1.12 step 5), server side: installing from a floppy, the disk's files going up in parts,
 * the computer keeping its id and programs when broken, and the scanner scanning a map into a file, printing it back
 * on photo paper, printing a book a sheet a page, jamming on a full output tray. The screen, the text mode and the
 * monitor are in NeedsTesting. No radio traffic, so no batch, except the power test (batch {@code computer_power}, with
 * {@code radios_need_power} on: the GameTest server runs with it off, {@link GameTestDefaults}).
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class RadioComputerGameTests {

	private static final BlockPos COMPUTER = new BlockPos(4, 2, 4);
	private static final BlockPos SCANNER = COMPUTER.east();
	private static final String POWER_BATCH = "computer_power";

	@BeforeBatch(batch = POWER_BATCH)
	public static void powerRequired(ServerLevel level) {
		RadiocraftServerConfig.RADIO_POWER_REQUIRED.set(true);
	}

	@AfterBatch(batch = POWER_BATCH)
	public static void powerFree(ServerLevel level) {
		RadiocraftServerConfig.RADIO_POWER_REQUIRED.set(false);
	}

	/**
	 * With power draw on, a computer running a background program (APRS) stays dark with no power wire, comes on with a
	 * charged battery on its wire, and goes dark again when the battery is flat.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = POWER_BATCH)
	public static void computerRunsOffThePowerWire(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper);
		computer.install(ComputerApps.APRS);
		BlockPos battery = COMPUTER.west(2);
		helper.startSequence()
				.thenIdle(10)
				.thenExecute(() -> {
					helper.assertFalse(computer.isPowered(), "the computer runs with no power wire");
					GameTestUtil.placeWire(helper, COMPUTER.west(), RadiocraftBlocks.WIRE.get());
					helper.setBlock(battery, RadiocraftBlocks.LARGE_BATTERY.get());
				})
				.thenIdle(2)
				.thenExecute(() -> GameTestUtil.networkObject(helper, battery, BatteryNetworkObject.class).getStorage().setEnergy(100_000))
				.thenWaitUntil(() -> helper.assertTrue(computer.isPowered(), "the computer stays dark with a charged battery on its wire"))
				.thenExecute(() -> GameTestUtil.networkObject(helper, battery, BatteryNetworkObject.class).getStorage().setEnergy(0))
				.thenWaitUntil(() -> helper.assertFalse(computer.isPowered(), "the computer runs on a flat battery"))
				.thenSucceed();
	}

	/**
	 * MUSIC playing with the screen closed (1.12 playtest): a listener keeps the computer in use (on, so drawing power
	 * when that is on) until they stop, and one who is not online any more (a fake player is never in the player list)
	 * is forgotten at the next viewer check.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 60)
	public static void musicListenerKeepsTheComputerOn(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper);
		FakePlayer player = player(helper, "music");
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertFalse(computer.isPowered(), "an unused computer is on");
					computer.listen(player, true);
					helper.assertTrue(computer.isPowered(), "a listener did not keep the computer on");
					computer.listen(player, false);
					helper.assertFalse(computer.isPowered(), "the computer stayed on after the music stopped");
					computer.listen(player, true);
				})
				.thenWaitUntil(() -> helper.assertFalse(computer.isPowered(), "a listener who is not online was not forgotten"))
				.thenSucceed();
	}

	private static FakePlayer player(GameTestHelper helper, String name) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("computer-" + name).getBytes()), name));
		player.getInventory().clearContent();
		BlockPos at = helper.absolutePos(COMPUTER.south(2));
		player.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
		return player;
	}

	private static RadioComputerBlockEntity computer(GameTestHelper helper) {
		helper.setBlock(COMPUTER, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(COMPUTER.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		return GameTestUtil.blockEntity(helper, COMPUTER, RadioComputerBlockEntity.class);
	}

	private static ScannerBlockEntity scanner(GameTestHelper helper) {
		helper.setBlock(SCANNER, RadiocraftBlocks.SCANNER.get());
		return GameTestUtil.blockEntity(helper, SCANNER, ScannerBlockEntity.class);
	}

	/** Runs one request as the screen would, and gives back the reply's tag ({@code rid}, {@code data}, maybe {@code error}). */
	private static CompoundTag call(RadioComputerBlockEntity computer, FakePlayer player, String action, CompoundTag data) {
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, action, data, new ComputerReply(player, 7, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	private static CompoundTag with(String key, String value) {
		CompoundTag tag = new CompoundTag();
		tag.putString(key, value);
		return tag;
	}

	private static String error(CompoundTag reply) {
		return reply.getString("error");
	}

	/** Every map colour in turn: a picture with plenty in it. */
	private static byte[] picture() {
		PhotoPalette palette = MapPalettes.vanilla();
		byte[] pixels = new byte[PhotoImage.PIXELS];
		for(int i = 0; i < pixels.length; i++)
			pixels[i] = palette.id((i / 5) % palette.size());
		return pixels;
	}

	/** A floppy goes in the drive, INSTALL copies its program on (the disk stays), and it can be removed again. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void installsFromAFloppy(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper);
		FakePlayer player = player(helper, "install");
		helper.assertTrue(error(call(computer, player, "sys.install", with("app", "logbook"))).equals("drive_empty"), "installed with no disk");
		helper.assertTrue(error(call(computer, player, "sys.install", with("app", "edit"))).equals("built_in"), "installed a built-in program");

		player.getInventory().setItem(3, new ItemStack(RadiocraftItems.floppy(ComputerApps.SSTV)));
		player.getInventory().setItem(4, new ItemStack(RadiocraftItems.floppy(ComputerApps.LOGBOOK)));
		CompoundTag inserted = call(computer, player, "sys.insert", inSlot(4));
		helper.assertTrue(!inserted.contains("error") && inserted.getCompound("data").getString("floppy").equals("logbook"), "insert: " + inserted);
		helper.assertTrue(player.getInventory().getItem(4).isEmpty(), "the disk stayed in the inventory");
		helper.assertTrue(error(call(computer, player, "sys.insert", inSlot(3))).equals("drive_full"), "two disks in one drive");
		helper.assertTrue(error(call(computer, player, "sys.install", with("app", "sstv"))).equals("wrong_floppy"), "installed SSTV from the logbook disk");

		CompoundTag installed = call(computer, player, "sys.install", with("app", "logbook"));
		helper.assertFalse(installed.contains("error"), "install failed: " + installed);
		helper.assertTrue(computer.isInstalled(ComputerApps.LOGBOOK), "the logbook is not installed");
		helper.assertTrue(computer.availableApps().contains(ComputerApps.LOGBOOK), "the logbook is not in the shell");
		helper.assertTrue(error(call(computer, player, "sys.install", with("app", "logbook"))).equals("already_installed"), "installed twice");
		helper.assertFalse(computer.getFloppy().isEmpty(), "installing used the disk up");

		helper.assertFalse(call(computer, player, "sys.eject", new CompoundTag()).contains("error"), "eject failed");
		helper.assertTrue(computer.getFloppy().isEmpty() && player.getInventory().countItem(RadiocraftItems.floppy(ComputerApps.LOGBOOK)) == 1, "the disk did not come back");
		helper.assertFalse(call(computer, player, "sys.uninstall", with("app", "logbook")).contains("error"), "uninstall failed");
		helper.assertFalse(computer.isInstalled(ComputerApps.LOGBOOK), "the logbook is still installed");
		helper.assertTrue(error(call(computer, player, "app", with("app", "logbook"))).equals("not_installed"), "an uninstalled program answered");
		helper.succeed();
	}

	/**
	 * The owner (who placed it) sets a password; anyone else is then refused everything until they type it, and asked
	 * again after closing the screen. Only the owner can change it; "" removes it.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void passwordLocksTheComputer(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper);
		FakePlayer owner = player(helper, "owner"), guest = player(helper, "guest");
		helper.assertFalse(guest.hasPermissions(2), "the guest is an operator: the test cannot check the lock");
		computer.claim(owner);
		helper.assertTrue(error(call(computer, guest, "sys.password", with("password", "x"))).equals("not_owner"), "a guest set the password");
		helper.assertTrue(error(call(computer, owner, "sys.password", with("password", "x".repeat(17)))).equals("bad_password"), "a 17-character password");
		helper.assertFalse(call(computer, owner, "sys.password", with("password", "hunter2")).contains("error"), "the owner could not set it");

		helper.assertTrue(computer.isLockedFor(guest) && computer.isLockedFor(owner), "a password asks everyone, owner too");
		helper.assertTrue(error(call(computer, guest, "open", new CompoundTag())).equals("locked"), "opened without the password");
		helper.assertTrue(error(call(computer, guest, "fs.list", with("ext", ""))).equals("locked"), "listed files without the password");
		helper.assertTrue(error(call(computer, guest, "sys.unlock", with("password", "hunter3"))).equals("wrong_password"), "a wrong password opened it");
		helper.assertFalse(call(computer, guest, "sys.unlock", with("password", "hunter2")).contains("error"), "the right password did not open it");
		helper.assertFalse(call(computer, guest, "open", new CompoundTag()).contains("error"), "still locked after the password");
		ComputerServer.closed(computer, guest);
		helper.assertTrue(computer.isLockedFor(guest), "closing the screen did not lock it again");

		helper.assertFalse(call(computer, owner, "sys.unlock", with("password", "hunter2")).contains("error"), "the owner's password");
		CompoundTag cleared = call(computer, owner, "sys.password", with("password", ""));
		helper.assertTrue(!cleared.contains("error") && !cleared.getCompound("data").getBoolean("password"), "cleared: " + cleared);
		helper.assertFalse(computer.isLockedFor(guest), "no password, still locked");
		helper.succeed();
	}

	private static CompoundTag inSlot(int slot) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("slot", slot);
		return tag;
	}

	/** A file bigger than one packet goes up in parts, reads back the same, lists, renames and deletes. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void filesGoUpInPartsAndReadBack(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper);
		FakePlayer player = player(helper, "files");
		byte[] contents = new byte[ComputerUploads.PART_BYTES * 2 + 77];
		for(int i = 0; i < contents.length; i++)
			contents[i] = (byte)(i % 13);
		int parts = ComputerUploads.parts(contents.length);
		CompoundTag last = null;
		for(int part = 0; part < parts; part++) {
			CompoundTag data = with("name", "big.dat");
			data.putInt("part", part);
			data.putInt("parts", parts);
			data.putByteArray("data", ComputerUploads.part(contents, part));
			last = call(computer, player, "fs.write", data);
			helper.assertFalse(last.contains("error"), "part " + part + ": " + last);
		}
		helper.assertTrue(last != null && last.getCompound("data").getString("name").equals("BIG.DAT"), "the file was not written: " + last);
		CompoundTag read = call(computer, player, "fs.read", with("name", "BIG.DAT"));
		helper.assertTrue(Arrays.equals(contents, read.getCompound("data").getByteArray("data")), "the file read back differently");
		CompoundTag list = call(computer, player, "fs.list", with("ext", "DAT"));
		helper.assertTrue(list.getCompound("data").getList("files", 10).size() == 1, "fs.list: " + list);
		helper.assertFalse(call(computer, player, "fs.rename", renaming("BIG.DAT", "OTHER.DAT")).contains("error"), "rename failed");
		helper.assertTrue(error(call(computer, player, "fs.read", with("name", "BIG.DAT"))).equals("not_found"), "the old name still reads");
		helper.assertFalse(call(computer, player, "fs.delete", with("name", "OTHER.DAT")).contains("error"), "delete failed");
		helper.assertTrue(computer.files().storage().count() == 0, "files left: " + computer.files().storage().count());
		helper.assertTrue(error(call(computer, player, "fs.read", with("name", "no good name"))).equals("bad_name"), "a bad name read");
		helper.succeed();
	}

	private static CompoundTag renaming(String from, String to) {
		CompoundTag tag = with("from", from);
		tag.putString("to", to);
		return tag;
	}

	/** Broken and placed again, a computer keeps its id (so its files) and its programs; a floppy in its drive drops. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void keepsItsDiskWhenBroken(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper);
		computer.install(ComputerApps.APRS);
		UUID id = computer.getComputerId();
		helper.assertTrue(computer.files().writeText("NOTE.TXT", "73") == null, "could not write a file");

		DataComponentMap components = computer.collectComponents();
		ComputerData data = components.get(RadiocraftDataComponent.COMPUTER.get());
		helper.assertTrue(data != null && data.id().equals(id) && data.installed().contains("aprs"), "the item would carry " + data);
		RadioComputerBlockEntity placed = new RadioComputerBlockEntity(computer.getBlockPos(), computer.getBlockState());
		placed.applyComponents(components, DataComponentPatch.EMPTY);
		helper.assertTrue(placed.getComputerId().equals(id) && placed.isInstalled(ComputerApps.APRS), "placed again it lost its id or programs");
		helper.assertTrue("73".equals(ComputerFileStore.of(helper.getLevel().getServer(), id).readText("NOTE.TXT")), "the files did not follow the id");

		computer.insertFloppy(new ItemStack(RadiocraftItems.floppy(ComputerApps.SSTV)));
		helper.getLevel().destroyBlock(helper.absolutePos(COMPUTER), true);
		helper.runAfterDelay(2, () -> {
			List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(COMPUTER)).inflate(2.0D));
			helper.assertTrue(drops.stream().anyMatch(item -> item.getItem().is(RadiocraftItems.floppy(ComputerApps.SSTV))), "the floppy did not drop");
			helper.succeed();
		});
	}

	/** A map on the glass scans into a picture file; printed on color photo paper it comes back the same; a full tray jams. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void scansAndPrintsPictures(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper);
		ScannerBlockEntity scanner = scanner(helper);
		FakePlayer player = player(helper, "scan");
		helper.assertTrue(computer.findScanner() == scanner, "the computer does not see the scanner beside it");
		byte[] pixels = picture();
		ItemStack map = PhotoPrints.print(helper.getLevel(), pixels, Component.literal("Test"), List.of(), 0xFFFFFF, helper.absolutePos(SCANNER));
		scanner.getInventory().setStackInSlot(ScannerBlockEntity.BED, map);

		CompoundTag saved = call(computer, player, "scan.save", with("name", "pic"));
		helper.assertTrue(!saved.contains("error") && saved.getCompound("data").getString("name").equals("PIC.IMG"), "scan.save: " + saved);
		helper.assertTrue(Arrays.equals(pixels, computer.files().read("PIC.IMG")), "the scanned picture is not the map's");

		helper.assertTrue(error(call(computer, player, "print.image", with("name", "PIC.IMG"))).equals("no_paper"), "printed with no paper");
		scanner.getInventory().setStackInSlot(ScannerBlockEntity.TRAY, new ItemStack(RadiocraftItems.COLOR_PHOTO_PAPER.get(), 2));
		CompoundTag printed = call(computer, player, "print.image", with("name", "PIC.IMG"));
		helper.assertFalse(printed.contains("error"), "print.image: " + printed);
		ItemStack photo = scanner.output();
		helper.assertTrue(photo.is(Items.FILLED_MAP) && Arrays.equals(pixels, PhotoPrints.pixels(helper.getLevel(), photo)), "the print is not the picture");
		PhotoInfo info = PhotoPrints.info(photo);
		helper.assertTrue(info != null && info.colour() && info.source().equals("scanner"), "photo info " + info);
		helper.assertTrue(scanner.tray().getCount() == 1, "used " + (2 - scanner.tray().getCount()) + " sheets");
		helper.assertTrue(error(call(computer, player, "print.image", with("name", "PIC.IMG"))).equals("output_full"), "printed onto a waiting print");
		helper.succeed();
	}

	/** A text prints as a written book, a sheet of paper a page, and not at all without enough paper. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void printsBooksASheetAPage(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper);
		ScannerBlockEntity scanner = scanner(helper);
		FakePlayer player = player(helper, "book");
		String text = "First page\fSecond page\fThird page";
		computer.files().writeText("LOG.TXT", text);
		scanner.getInventory().setStackInSlot(ScannerBlockEntity.TRAY, new ItemStack(Items.PAPER, 2));
		helper.assertTrue(error(call(computer, player, "print.text", with("name", "LOG.TXT"))).equals("no_paper"), "printed three pages on two sheets");
		scanner.getInventory().setStackInSlot(ScannerBlockEntity.TRAY, new ItemStack(Items.PAPER, 5));
		CompoundTag printed = call(computer, player, "print.text", with("name", "LOG.TXT"));
		helper.assertFalse(printed.contains("error"), "print.text: " + printed);
		WrittenBookContent book = scanner.output().get(DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(scanner.output().is(Items.WRITTEN_BOOK) && book != null && book.pages().size() == 3, "the book is " + scanner.output());
		helper.assertTrue(book.title().raw().equals("LOG") && book.author().equals("book"), "title " + book.title().raw() + " by " + book.author());
		helper.assertTrue(scanner.tray().getCount() == 2, "paper left " + scanner.tray().getCount());

		// The book scans back in as the same text.
		scanner.getInventory().setStackInSlot(ScannerBlockEntity.BED, scanner.output().copy());
		scanner.getInventory().setStackInSlot(ScannerBlockEntity.OUTPUT, ItemStack.EMPTY);
		CompoundTag saved = call(computer, player, "scan.save", with("name", "copy"));
		helper.assertTrue(text.equals(computer.files().readText("COPY.TXT")), "scanned back as " + computer.files().readText("COPY.TXT") + " (" + saved + ")");
		helper.succeed();
	}

	/** Without a scanner everything but scanning and printing works. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void noScannerNoPrinting(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper);
		FakePlayer player = player(helper, "noscanner");
		computer.files().write("PIC.IMG", picture());
		helper.assertTrue(error(call(computer, player, "print.image", with("name", "PIC.IMG"))).equals("no_scanner"), "printed without a scanner");
		helper.assertTrue(error(call(computer, player, "scan.read", new CompoundTag())).equals("no_scanner"), "scanned without a scanner");
		CompoundTag info = call(computer, player, "sys.info", new CompoundTag());
		helper.assertTrue(!info.contains("error") && !info.getCompound("data").getBoolean("scanner"), "sys.info: " + info);
		helper.assertTrue(info.getCompound("data").getLong("used") == PhotoImage.PIXELS, "sys.info used " + info.getCompound("data").getLong("used"));
		helper.assertTrue(error(call(computer, player, "fs.write", writing("BAD.IMG", new byte[10]))).equals(ComputerStorage.Problem.BAD_CONTENTS.key()), "a picture of the wrong size was kept");
		helper.succeed();
	}

	private static CompoundTag writing(String name, byte[] bytes) {
		CompoundTag data = with("name", name);
		data.putInt("part", 0);
		data.putInt("parts", 1);
		data.putByteArray("data", bytes);
		return data;
	}
}
