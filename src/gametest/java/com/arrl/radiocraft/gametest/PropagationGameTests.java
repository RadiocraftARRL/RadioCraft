package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.solar.SolarEventManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The Propagation Predictor (computer apps 2, 2026-11-06), server side, as its screen asks: with the server's radio
 * requirement off (it reads the sky directly) and an average sky, 20m is open by skip in the middle of its day skip
 * range and by line of sight close by; with the requirement on and no receiver it says what is missing. Both batches
 * change a server config value, so each has its own.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class PropagationGameTests {

	private static final String DIRECT_BATCH = "propagation_direct";
	private static final String RADIO_BATCH = "propagation_needs_radio";
	private static final BlockPos AT = new BlockPos(2, 2, 2);
	private static boolean neededRadio = true;

	@BeforeBatch(batch = DIRECT_BATCH)
	public static void readTheSkyDirectly(ServerLevel level) {
		neededRadio = RadiocraftServerConfig.TELETYPE_NEEDS_RADIO.get();
		RadiocraftServerConfig.TELETYPE_NEEDS_RADIO.set(false);
		GameTestDefaults.calmSky(level); // Average: 20m good by day and night, noise 0.3; no aurora.
		SolarEventManager.clearForecast(level);
	}

	@AfterBatch(batch = DIRECT_BATCH)
	public static void restoreDirect(ServerLevel level) {
		RadiocraftServerConfig.TELETYPE_NEEDS_RADIO.set(neededRadio);
	}

	@BeforeBatch(batch = RADIO_BATCH)
	public static void needARadio(ServerLevel level) {
		neededRadio = RadiocraftServerConfig.TELETYPE_NEEDS_RADIO.get();
		RadiocraftServerConfig.TELETYPE_NEEDS_RADIO.set(true);
	}

	@AfterBatch(batch = RADIO_BATCH)
	public static void restoreRadio(ServerLevel level) {
		RadiocraftServerConfig.TELETYPE_NEEDS_RADIO.set(neededRadio);
	}

	private static FakePlayer player(GameTestHelper helper) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("propagation".getBytes()), "propagation"));
		BlockPos at = helper.absolutePos(AT.south(2));
		player.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
		return player;
	}

	private static RadioComputerBlockEntity computer(GameTestHelper helper) {
		helper.setBlock(AT, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(AT.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, AT, RadioComputerBlockEntity.class);
		computer.install(ComputerApps.PROPAGATION);
		return computer;
	}

	private static CompoundTag app(RadioComputerBlockEntity computer, FakePlayer player, String action, CompoundTag data) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", ComputerApps.PROPAGATION.id());
		wrapped.putString("action", action);
		wrapped.put("data", data);
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, "app", wrapped, new ComputerReply(player, 7, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	private static CompoundTag bearing(int distance, int bearing) {
		CompoundTag data = new CompoundTag();
		data.putString("mode", "bearing");
		data.putInt("distance", distance);
		data.putDouble("bearing", bearing);
		return data;
	}

	/** The row for {@code band} in a prediction. */
	private static CompoundTag row(GameTestHelper helper, CompoundTag prediction, String band) {
		ListTag bands = prediction.getList("bands", Tag.TAG_COMPOUND);
		for(int i = 0; i < bands.size(); i++)
			if(bands.getCompound(i).getString("band").equals(band))
				return bands.getCompound(i);
		helper.fail("no " + band + " row: " + bands);
		return new CompoundTag();
	}

	/** 4000 blocks east is the middle of 20m's day skip: good by skip in every day hour; 100 blocks is line of sight. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40, batch = DIRECT_BATCH)
	public static void twentyMetersOpensBySkipAndLineOfSight(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper);
		FakePlayer player = player(helper);
		helper.startSequence()
				.thenIdle(1)
				.thenExecute(() -> {
					CompoundTag reply = app(computer, player, "predict", bearing(4000, 90));
					helper.assertFalse(reply.contains("error"), "predict: " + reply);
					CompoundTag data = reply.getCompound("data");
					helper.assertTrue(data.getString("source").equals("direct"), "source " + data.getString("source"));
					helper.assertTrue(Math.abs(data.getInt("distance") - 4000) <= 1, "distance " + data.getInt("distance"));
					helper.assertTrue(data.getInt("bearing") == 90, "bearing " + data.getInt("bearing"));
					ListTag hours = data.getList("hours", Tag.TAG_COMPOUND);
					helper.assertTrue(hours.size() == 24, "hours: " + hours.size());
					CompoundTag twenty = row(helper, data, "20m");
					String grades = twenty.getString("grades"), paths = twenty.getString("paths");
					int dayHours = 0;
					for(int h = 0; h < hours.size(); h++) {
						CompoundTag hour = hours.getCompound(h);
						helper.assertTrue(hour.getBoolean("known"), "hour " + h + " unknown under a fresh average event");
						if(hour.getBoolean("day")) {
							dayHours++;
							helper.assertTrue(grades.charAt(h) == 'G' && paths.charAt(h) == 'S', "20m by day at hour " + h + ": " + grades + " / " + paths);
						}
						else
							helper.assertTrue(grades.charAt(h) == '-', "20m by night reached 4000 blocks at hour " + h + ": " + grades);
					}
					helper.assertTrue(dayHours > 0 && dayHours < 24, "day hours: " + dayHours);
					helper.assertTrue(twenty.getInt("best") >= 0, "20m has no best hour");

					CompoundTag near = app(computer, player, "predict", bearing(100, 0)).getCompound("data");
					CompoundTag nearRow = row(helper, near, "20m");
					helper.assertTrue(nearRow.getString("paths").charAt(0) == 'L' && nearRow.getString("grades").charAt(0) == 'G',
							"20m at 100 blocks: " + nearRow);
					helper.assertTrue(!near.getString("bestNow").isEmpty(), "no best band at 100 blocks");

					CompoundTag coords = new CompoundTag();
					coords.putString("mode", "coords");
					coords.putInt("x", helper.absolutePos(AT).getX());
					coords.putInt("z", helper.absolutePos(AT).getZ() + 1000);
					CompoundTag south = app(computer, player, "predict", coords).getCompound("data");
					helper.assertTrue(south.getInt("bearing") == 180 && Math.abs(south.getInt("distance") - 1000) <= 1, "coordinates: " + south);

					CompoundTag call = new CompoundTag();
					call.putString("mode", "call");
					call.putString("call", "NOSUCH1");
					CompoundTag unknown = app(computer, player, "predict", call);
					helper.assertTrue(unknown.getString("error").equals("prop_unknown_call"), "an unknown call: " + unknown);
				})
				.thenSucceed();
	}

	/** With the server's radio requirement on and nothing to hear the sky with, the program says so. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40, batch = RADIO_BATCH)
	public static void predictionNeedsAReceiver(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper);
		FakePlayer player = player(helper);
		helper.startSequence()
				.thenIdle(1)
				.thenExecute(() -> {
					CompoundTag reply = app(computer, player, "predict", bearing(4000, 90));
					helper.assertTrue(reply.getString("error").equals("solar_no_radio"), "predicted with no receiver: " + reply);
				})
				.thenSucceed();
	}
}
