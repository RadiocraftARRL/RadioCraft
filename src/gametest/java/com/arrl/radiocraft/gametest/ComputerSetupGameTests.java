package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerFileStore;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.ComputerSettings;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * SETUP's settings on the server (1.12, launcher and SETUP): only the owner (or an operator, or anyone while the computer
 * has no owner) changes them, everyone sees them in {@code open}, they are cleaned on the way in, and they follow the
 * computer's disk when it is broken and placed again. License Study and ABOUT are built in. No radio traffic, no batch.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class ComputerSetupGameTests {

	private static final BlockPos COMPUTER = new BlockPos(4, 2, 4);

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void setupIsTheOwnersAndFollowsTheDisk(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper);
		FakePlayer owner = player(helper, "setup-owner"), guest = player(helper, "setup-guest");
		helper.assertFalse(guest.hasPermissions(2), "the guest is an operator: the test cannot check SETUP");

		// Unowned: anyone may change it (computers placed before owners existed).
		ComputerSettings first = ComputerSettings.DEFAULT.withView(ComputerSettings.View.ICONS);
		helper.assertFalse(call(computer, guest, "sys.settings", settings(first)).contains("error"), "an unowned computer refused SETUP");

		computer.claim(owner);
		ComputerSettings wanted = ComputerSettings.DEFAULT.withView(ComputerSettings.View.COMMAND).withScheme("amber")
				.withName("Shack PC").withClock(ComputerSettings.Clock.REAL).withHour24(false);
		CompoundTag refused = call(computer, guest, "sys.settings", settings(wanted));
		helper.assertTrue("not_owner_settings".equals(refused.getString("error")), "a guest changed SETUP: " + refused);
		helper.assertTrue(computer.files().settings().equals(first), "a refused change was kept");

		CompoundTag kept = call(computer, owner, "sys.settings", settings(wanted));
		helper.assertFalse(kept.contains("error"), "the owner could not change SETUP: " + kept);
		helper.assertTrue(ComputerSettings.fromTag(kept.getCompound("data").getCompound("settings")).equals(wanted), "the answer: " + kept);

		// Everyone sees them, and whether they may change them.
		CompoundTag hello = call(computer, guest, "open", new CompoundTag()).getCompound("data");
		helper.assertTrue(ComputerSettings.fromTag(hello.getCompound("settings")).equals(wanted), "open did not carry the settings: " + hello);
		helper.assertFalse(hello.getBoolean("owner"), "the guest was told they may change SETUP");
		helper.assertTrue(call(computer, owner, "open", new CompoundTag()).getCompound("data").getBoolean("owner"), "the owner was told they may not");

		// Cleaned on the way in.
		CompoundTag dirty = new ComputerSettings(ComputerSettings.View.LIST, "classic", "", ComputerSettings.Clock.OFF, true).toTag();
		dirty.putString("name", "x".repeat(60));
		dirty.putString("scheme", "NOT A SCHEME");
		CompoundTag cleaned = call(computer, owner, "sys.settings", wrap(dirty));
		ComputerSettings clean = ComputerSettings.fromTag(cleaned.getCompound("data").getCompound("settings"));
		helper.assertTrue(clean.name().length() == ComputerSettings.MAX_NAME && clean.scheme().equals("classic"), "not cleaned: " + clean);
		call(computer, owner, "sys.settings", settings(wanted));

		// Broken and placed again: the disk, so the settings, follow the computer's id.
		UUID id = computer.getComputerId();
		DataComponentMap components = computer.collectComponents();
		RadioComputerBlockEntity placed = new RadioComputerBlockEntity(computer.getBlockPos(), computer.getBlockState());
		placed.applyComponents(components, DataComponentPatch.EMPTY);
		helper.assertTrue(placed.getComputerId().equals(id), "placed again it lost its id");
		helper.assertTrue(ComputerFileStore.of(helper.getLevel().getServer(), id).settings().equals(wanted), "the settings did not follow the disk");

		// The disk is saved with them, and read back the same.
		CompoundTag saved = computer.files().save(new CompoundTag(), helper.getLevel().registryAccess());
		helper.assertTrue(ComputerFileStore.load(saved, helper.getLevel().registryAccess()).settings().equals(wanted), "the settings did not survive a save");
		helper.succeed();
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void studyAndAboutAreBuiltIn(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper);
		helper.assertTrue(computer.availableApps().contains(ComputerApps.LICENSE), "License Study is not on a new computer");
		helper.assertTrue(computer.availableApps().contains(ComputerApps.SETUP) && computer.availableApps().contains(ComputerApps.ABOUT), "SETUP or ABOUT missing");
		helper.assertFalse(computer.install(ComputerApps.LICENSE), "a built-in program installed");
		helper.assertTrue(com.arrl.radiocraft.common.init.RadiocraftItems.floppy(ComputerApps.LICENSE) == null, "License Study still has a floppy");
		helper.succeed();
	}

	private static CompoundTag settings(ComputerSettings settings) {
		return wrap(settings.toTag());
	}

	private static CompoundTag wrap(CompoundTag settings) {
		CompoundTag data = new CompoundTag();
		data.put("settings", settings);
		return data;
	}

	private static FakePlayer player(GameTestHelper helper, String name) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("computer-" + name).getBytes()), name));
		BlockPos at = helper.absolutePos(COMPUTER.south(2));
		player.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
		return player;
	}

	private static RadioComputerBlockEntity computer(GameTestHelper helper) {
		helper.setBlock(COMPUTER, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(COMPUTER.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		return GameTestUtil.blockEntity(helper, COMPUTER, RadioComputerBlockEntity.class);
	}

	/** Runs one request as the screen would, and gives back the reply's tag ({@code rid}, {@code data}, maybe {@code error}). */
	private static CompoundTag call(RadioComputerBlockEntity computer, FakePlayer player, String action, CompoundTag data) {
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, action, data, new ComputerReply(player, 7, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}
}
