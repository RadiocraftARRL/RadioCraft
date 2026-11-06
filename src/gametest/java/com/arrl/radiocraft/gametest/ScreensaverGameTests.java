package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioComputerNetworkObject;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.ComputerSettings;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The screensaver on the server (computer batch 3A): a computer running a background program comes to its screensaver
 * after SETUP's idle minutes with nobody using it, draws a quarter of its power then, and any request from its screen
 * wakes it. Without the program installed, or with the wait set to never, it never comes on. What it looks like is the
 * client's (NeedsTesting).
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class ScreensaverGameTests {

	private static final BlockPos COMPUTER = new BlockPos(2, 2, 2);

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 120)
	public static void screensaverCutsThePowerDraw(GameTestHelper helper) {
		helper.setBlock(COMPUTER, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(COMPUTER.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, COMPUTER, RadioComputerBlockEntity.class);
		RadioComputerNetworkObject power = GameTestUtil.networkObject(helper, COMPUTER, RadioComputerNetworkObject.class);
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("saver-tester".getBytes()), "saver"));
		int full = power.drawPerTick();
		helper.assertTrue(full >= 4, "the computer's draw is too small to see a quarter of it: " + full);

		computer.install(ComputerApps.APRS); // A background program: the computer stays in use with nobody at it.
		computer.files().setSettings(ComputerSettings.DEFAULT.withSaverMinutes(1));
		long now = helper.getLevel().getGameTime();
		computer.idleSince(now - 1300L); // A minute and a bit ago.
		helper.runAfterDelay(15, () -> {
			helper.assertFalse(computer.isSaving(), "the screensaver came on without its program installed");
			helper.assertTrue(power.drawPerTick() == full, "the draw dropped with no screensaver: " + power.drawPerTick());
			computer.install(ComputerApps.SCREENSAVER);
			helper.runAfterDelay(15, () -> {
				helper.assertTrue(computer.isSaving(), "idle past the wait, the screensaver is not on");
				helper.assertTrue(power.saving && power.drawPerTick() == Math.max(1, full / RadioComputerNetworkObject.SAVER_SHARE_DIVISOR),
						"the screensaver's draw is " + power.drawPerTick() + " of " + full);
				// Someone uses it: awake, full draw.
				CompoundTag[] reply = new CompoundTag[1];
				ComputerServer.handle(computer, player, "sys.info", new CompoundTag(), new ComputerReply(player, 7, tag -> reply[0] = tag));
				helper.runAfterDelay(15, () -> {
					helper.assertFalse(computer.isSaving(), "a request did not wake it");
					helper.assertTrue(power.drawPerTick() == full, "awake, the draw is " + power.drawPerTick());
					// The wait set to never: idle for ever, no screensaver.
					computer.files().setSettings(ComputerSettings.DEFAULT.withSaverMinutes(0));
					computer.idleSince(helper.getLevel().getGameTime() - 100_000L);
					helper.runAfterDelay(15, () -> {
						helper.assertFalse(computer.isSaving(), "the screensaver came on with the wait set to never");
						helper.succeed();
					});
				});
			});
		});
	}
}
