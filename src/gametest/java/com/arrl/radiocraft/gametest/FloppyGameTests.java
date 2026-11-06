package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.FloppyDisk;
import com.arrl.radiocraft.common.computer.FloppyDrive;
import com.arrl.radiocraft.common.computer.FloppyFileStore;
import com.arrl.radiocraft.common.datacomponents.FloppyData;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Data floppies on the server (computer batch 3A): a blank floppy in the drive is drive A:, files copy onto it and off
 * it through the platform's requests, a blank written to becomes a Data Floppy whose files follow the item to another
 * computer, and programs never go onto one. No radio traffic, no batch.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class FloppyGameTests {

	private static final BlockPos COMPUTER = new BlockPos(2, 2, 2), OTHER = new BlockPos(6, 2, 2);

	/** C: to A: and back, a blank turning into a Data Floppy, its files going to another computer with the item. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void filesCopyToAndFromAFloppy(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, COMPUTER), other = computer(helper, OTHER);
		FakePlayer player = player(helper, "floppy-copier");
		computer.files().writeText("NOTES.TXT", "73 de W1BTR");

		// No disk: A: is not ready.
		CompoundTag none = call(computer, player, "fs.list", drive());
		helper.assertTrue("no_disk".equals(none.getString("error")), "an empty drive listed: " + none);
		helper.assertTrue("no_disk".equals(copy(computer, player, "NOTES.TXT", "A:", false).getString("error")), "copied to an empty drive");

		// A blank floppy goes in; copying a file onto it makes it a Data Floppy.
		helper.assertTrue(computer.insertFloppy(new ItemStack(RadiocraftItems.FLOPPY_DISK.get())), "the blank floppy did not go in");
		helper.assertTrue(FloppyDrive.kind(computer.getFloppy()) == FloppyDrive.Kind.BLANK, "not a blank disk");
		CompoundTag empty = call(computer, player, "fs.list", drive());
		helper.assertFalse(empty.contains("error"), "a blank floppy did not list: " + empty);
		helper.assertTrue(empty.getCompound("data").getLong("capacity") == FloppyDisk.CAPACITY, "A: is not 1.44 MB: " + empty);
		CompoundTag copied = copy(computer, player, "NOTES.TXT", "A:", false);
		helper.assertFalse(copied.contains("error"), "C: to A: failed: " + copied);
		helper.assertTrue("data".equals(copied.getCompound("data").getString("floppy")), "the drive does not say data: " + copied);
		FloppyData data = computer.getFloppy().get(RadiocraftDataComponent.FLOPPY_DATA.value());
		helper.assertTrue(data != null && data.files() == 1 && data.used() == "73 de W1BTR".length(), "the item's summary: " + data);

		// Again without /Y: it is there already. With it: replaced.
		helper.assertTrue("exists".equals(copy(computer, player, "NOTES.TXT", "A:", false).getString("error")), "copied over a file without asking");
		helper.assertFalse(copy(computer, player, "NOTES.TXT", "A:NOTES2.TXT", false).contains("error"), "copy to a new name on A:");
		helper.assertTrue("copy_onto_itself".equals(copy(computer, player, "A:NOTES.TXT", "A:", true).getString("error")), "a file copied onto itself");

		// A label, shown in the item.
		CompoundTag label = new CompoundTag();
		label.putString("label", "field day!");
		helper.assertFalse(call(computer, player, "fs.label", label).contains("error"), "label refused");
		helper.assertTrue("FIELD DAY!".equals(FloppyDrive.label(computer.getFloppy())), "the label: " + FloppyDrive.label(computer.getFloppy()));

		// Out of this computer and into the other: the files went with the disk.
		ItemStack disk = computer.ejectFloppy();
		helper.assertTrue(other.insertFloppy(disk), "the data floppy did not go in the other computer");
		CompoundTag listed = call(other, player, "fs.list", drive());
		helper.assertTrue(listed.getCompound("data").getList("files", 10).size() == 2, "the other computer's A: " + listed);
		helper.assertTrue("FIELD DAY!".equals(listed.getCompound("data").getString("label")), "the label did not come along: " + listed);
		CompoundTag back = copy(other, player, "A:NOTES.TXT", "C:", false);
		helper.assertFalse(back.contains("error"), "A: to C: failed: " + back);
		helper.assertTrue("73 de W1BTR".equals(other.files().readText("NOTES.TXT")), "the copy on C: " + other.files().readText("NOTES.TXT"));

		// Rename and delete on A:; emptied and unlabelled it is a blank floppy again.
		CompoundTag rename = drive();
		rename.putString("from", "NOTES2.TXT");
		rename.putString("to", "QSL.TXT");
		helper.assertFalse(call(other, player, "fs.rename", rename).contains("error"), "rename on A:");
		for(String name : new String[] { "NOTES.TXT", "QSL.TXT" }) {
			CompoundTag delete = drive();
			delete.putString("name", name);
			helper.assertFalse(call(other, player, "fs.delete", delete).contains("error"), "delete " + name + " on A:");
		}
		helper.assertTrue(FloppyDrive.kind(other.getFloppy()) == FloppyDrive.Kind.DATA, "still labelled, it should stay a data floppy");
		label.putString("label", "");
		call(other, player, "fs.label", label);
		helper.assertTrue(FloppyDrive.kind(other.getFloppy()) == FloppyDrive.Kind.BLANK, "an empty unlabelled floppy is not blank again");
		helper.assertTrue(ItemStack.isSameItemSameComponents(other.getFloppy(), new ItemStack(RadiocraftItems.FLOPPY_DISK.get())), "it would not stack with blanks");
		helper.succeed();
	}

	/** Programs never go on a floppy, a program's disk takes no files, and the caps hold. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void programsAreNeverCopiedToAFloppy(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper, COMPUTER);
		FakePlayer player = player(helper, "floppy-pirate");
		computer.files().write("CRACKED.EXE", new byte[] { 1, 2, 3 }); // A file named like a program, on C:.
		computer.files().writeText("README.TXT", "hello");

		// A program's own disk is write-protected.
		helper.assertTrue(computer.insertFloppy(new ItemStack(RadiocraftItems.floppy(ComputerApps.LOGBOOK))), "the logbook disk did not go in");
		helper.assertTrue("write_protected".equals(copy(computer, player, "README.TXT", "A:", false).getString("error")), "a program disk took a file");
		CompoundTag label = new CompoundTag();
		label.putString("label", "MINE");
		helper.assertTrue("write_protected".equals(call(computer, player, "fs.label", label).getString("error")), "a program disk took a label");
		helper.assertTrue(computer.getFloppy().get(RadiocraftDataComponent.FLOPPY_DATA.value()) == null, "the program disk got data");
		computer.ejectFloppy();

		// On a blank disk: no program, by copy or by writing it there directly; the disk stays blank.
		computer.insertFloppy(new ItemStack(RadiocraftItems.FLOPPY_DISK.get()));
		helper.assertTrue("program".equals(copy(computer, player, "CRACKED.EXE", "A:", false).getString("error")), "a program went to A:");
		helper.assertTrue("program".equals(copy(computer, player, "README.TXT", "A:README.COM", false).getString("error")), "a .COM went to A:");
		CompoundTag write = drive();
		write.putString("name", "LOGBOOK.EXE");
		write.putInt("part", 0);
		write.putInt("parts", 1);
		write.putByteArray("data", new byte[] { 9 });
		helper.assertTrue("program".equals(call(computer, player, "fs.write", write).getString("error")), "a program written straight to A:");
		helper.assertTrue(FloppyDrive.kind(computer.getFloppy()) == FloppyDrive.Kind.BLANK, "a refused program made it a data floppy");

		// Data goes on; renaming it to a program's name does not.
		helper.assertFalse(copy(computer, player, "README.TXT", "A:", false).contains("error"), "README.TXT refused");
		CompoundTag rename = drive();
		rename.putString("from", "README.TXT");
		rename.putString("to", "README.EXE");
		helper.assertTrue("program".equals(call(computer, player, "fs.rename", rename).getString("error")), "renamed into a program on A:");

		// The file cap.
		FloppyData data = computer.getFloppy().get(RadiocraftDataComponent.FLOPPY_DATA.value());
		helper.assertTrue(data != null, "no data after a copy");
		FloppyFileStore store = FloppyFileStore.of(helper.getLevel().getServer(), data.id());
		for(int i = store.storage().count(); i < FloppyDisk.MAX_FILES; i++)
			helper.assertTrue(store.write("F" + i + ".TXT", new byte[] { 1 }) == null, "file " + i);
		helper.assertTrue(store.write("ONEMORE.TXT", new byte[] { 1 }) == ComputerStorage.Problem.TOO_MANY_FILES, "past the file cap");
		helper.succeed();
	}

	private static CompoundTag drive() {
		CompoundTag data = new CompoundTag();
		data.putString("drive", "A");
		return data;
	}

	private static CompoundTag copy(RadioComputerBlockEntity computer, FakePlayer player, String from, String to, boolean overwrite) {
		CompoundTag data = new CompoundTag();
		data.putString("from", from);
		data.putString("to", to);
		data.putBoolean("overwrite", overwrite);
		return call(computer, player, "fs.copy", data);
	}

	private static FakePlayer player(GameTestHelper helper, String name) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("computer-" + name).getBytes()), name));
		BlockPos at = helper.absolutePos(COMPUTER.south(2));
		player.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
		return player;
	}

	private static RadioComputerBlockEntity computer(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(at.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		return GameTestUtil.blockEntity(helper, at, RadioComputerBlockEntity.class);
	}

	/** Runs one request as the screen would; the reply's tag ({@code rid}, {@code data}, maybe {@code error}). */
	private static CompoundTag call(RadioComputerBlockEntity computer, FakePlayer player, String action, CompoundTag data) {
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, action, data, new ComputerReply(player, 7, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}
}
