package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.ComputerSettings;
import com.arrl.radiocraft.common.computer.apps.DistressHandler;
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
 * SETUP's background services switch (2026-11-12): on (the default), a computer with DISTRESS or WXSAT installed stays
 * on with nobody at it; off, it goes dark, the services stop (DISTRESS forgets it was watching), and a service runs
 * again only while its program is open (it asked for something in the last few seconds). Turned back on, the computer
 * comes back on by itself.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class BackgroundServicesGameTests {

	private static final UUID USER = UUID.nameUUIDFromBytes("gametest-background-services".getBytes());

	private static void setServices(RadioComputerBlockEntity computer, boolean on) {
		ComputerSettings settings = computer.files().settings();
		computer.files().setSettings(settings.withBackgroundServices(on));
		computer.settingsChanged();
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 300)
	public static void servicesOffStopKeepingTheComputerOn(GameTestHelper helper) {
		BlockPos at = new BlockPos(4, 2, 4);
		helper.setBlock(at, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(at.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, at, RadioComputerBlockEntity.class);
		computer.install(ComputerApps.WXSAT);
		computer.install(ComputerApps.DISTRESS);
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(USER, "gametest"));
		BlockPos stand = helper.absolutePos(at.south(2));
		player.moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
		helper.startSequence()
				.thenWaitUntil(() -> {
					helper.assertTrue(computer.isPowered(), "with services on, the computer is not kept on");
					helper.assertTrue(DistressHandler.state(computer).primed(), "DISTRESS did not start watching");
				})
				.thenExecute(() -> {
					helper.assertTrue(computer.backgroundServicesOn(), "the switch is off on a new computer");
					setServices(computer, false);
					helper.assertFalse(computer.backgroundServicesOn(), "the switch did not take");
				})
				.thenWaitUntil(() -> {
					helper.assertFalse(computer.isPowered(), "with services off and nobody at it, the computer stays on");
					helper.assertFalse(computer.serviceRunning(ComputerApps.WXSAT), "WXSAT runs while shut");
					helper.assertFalse(DistressHandler.state(computer).primed(), "DISTRESS was not told it stopped");
				})
				.thenExecute(() -> {
					CompoundTag wrapped = new CompoundTag();
					wrapped.putString("app", ComputerApps.WXSAT.id());
					wrapped.putString("action", "status");
					wrapped.put("data", new CompoundTag());
					ComputerServer.handle(computer, player, "app", wrapped, new ComputerReply(player, 7, tag -> {}));
					helper.assertTrue(computer.serviceRunning(ComputerApps.WXSAT), "WXSAT open on the screen does not run");
					helper.assertFalse(computer.serviceRunning(ComputerApps.DISTRESS), "DISTRESS runs though only WXSAT is open");
					helper.assertTrue(computer.serviceRunning(ComputerApps.RIG_CONTROL), "a program that is no service is held back");
				})
				.thenIdle(RadioComputerBlockEntity.PROGRAM_OPEN_TICKS + 2)
				.thenExecute(() -> {
					helper.assertFalse(computer.serviceRunning(ComputerApps.WXSAT), "WXSAT still runs after it was closed");
					helper.assertFalse(ComputerSettings.fromTag(computer.files().settings().toTag()).backgroundServices(), "the setting is not on the disk");
					setServices(computer, true);
				})
				.thenWaitUntil(() -> helper.assertTrue(computer.isPowered() && computer.serviceRunning(ComputerApps.DISTRESS), "services back on: the computer is not on again"))
				.thenSucceed();
	}
}
