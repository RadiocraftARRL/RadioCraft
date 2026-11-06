package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.VHFRepeaterBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.VHFRadioBlockEntity;
import com.arrl.radiocraft.common.blocks.antennas.DoubleVHFAntennaBlock;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.FrequencyList;
import com.arrl.radiocraft.common.computer.RepeaterDirectory;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.RepeaterRegistry;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The Repeater Directory program (computer apps 2, 2026-11-06): a repeater with a callsign and an access tone is used
 * once; a base station tuned to its output, on a radio interface beside the computer, hears its hang carrier and
 * courtesy beep under the repeater's own voice id, and the program's {@code list} finds it with its callsign, output,
 * offset and tone. A typed-in entry and the heard one export to a frequency manager list. The repeater and stations
 * talk over bare connectors and a J-pole, a few blocks, on 147.240 in / 147.840 out (no other test uses them).
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class RepeaterDirectoryGameTests {

	private static final String BATCH = "repeater_directory";
	private static final UUID OPERATOR = UUID.nameUUIDFromBytes("gametest-rptdir-operator".getBytes(StandardCharsets.UTF_8));
	private static final BlockPos REPEATER = new BlockPos(8, 2, 6);
	private static final BlockPos USER = new BlockPos(5, 2, 6); // On the input.
	private static final BlockPos LISTENER = new BlockPos(10, 2, 6); // On the output.
	private static final BlockPos INTERFACE = LISTENER.north();
	private static final BlockPos COMPUTER = INTERFACE.east();
	private static final float INPUT_HZ = 147_240_000.0F, OUTPUT_HZ = 147_840_000.0F, TONE = 100.0F;

	private static void powerOn(GameTestHelper helper, BlockPos pos) {
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, pos, RadioNetworkObject.class);
		if(!networkObject.isPowered)
			GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).toggle();
		helper.assertTrue(networkObject.isPowered, "Radio at " + pos.toShortString() + " did not power on");
	}

	private static short[] toneFrame() {
		short[] audio = new short[960];
		for(int i = 0; i < audio.length; i++)
			audio[i] = (short)(Math.sin(2 * Math.PI * 440 * i / 48000.0D) * 8000);
		return audio;
	}

	private static CompoundTag app(RadioComputerBlockEntity computer, FakePlayer player, String action, CompoundTag data) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", ComputerApps.REPEATERS.id());
		wrapped.putString("action", action);
		wrapped.put("data", data);
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, "app", wrapped, new ComputerReply(player, 7, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	private static CompoundTag find(ListTag entries, String call) {
		for(int i = 0; i < entries.size(); i++)
			if(entries.getCompound(i).getString("call").equals(call))
				return entries.getCompound(i);
		return null;
	}

	/** Heard through the radio interface, listed with its details, then exported with a typed-in one. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 400, batch = BATCH)
	public static void heardRepeaterIsListedAndExported(GameTestHelper helper) {
		helper.setBlock(REPEATER, RadiocraftBlocks.VHF_REPEATER.get());
		helper.setBlock(REPEATER.south(), RadiocraftBlocks.DUPLEXER.get());
		GameTestUtil.placeWire(helper, REPEATER.south(2), RadiocraftBlocks.COAX_WIRE.get());
		GameTestUtil.placeWire(helper, REPEATER.south(3), RadiocraftBlocks.COAX_WIRE.get());
		helper.setBlock(REPEATER.south(4), RadiocraftBlocks.J_POLE_ANTENNA.get().defaultBlockState().setValue(DoubleVHFAntennaBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(REPEATER.south(4).above(), RadiocraftBlocks.J_POLE_ANTENNA.get().defaultBlockState().setValue(DoubleVHFAntennaBlock.HALF, DoubleBlockHalf.UPPER));
		helper.setBlock(USER, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(LISTENER, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(INTERFACE, RadiocraftBlocks.RADIO_INTERFACE.get());
		helper.setBlock(COMPUTER, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(COMPUTER.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, COMPUTER, RadioComputerBlockEntity.class);
		computer.install(ComputerApps.REPEATERS);
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("rptdir-player".getBytes(StandardCharsets.UTF_8)), "rptdir"));
		BlockPos stand = helper.absolutePos(COMPUTER.south(3));
		player.moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
		VHFRepeaterBlockEntity repeater = GameTestUtil.blockEntity(helper, REPEATER, VHFRepeaterBlockEntity.class);
		long[] spokeAt = new long[1];
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(repeater.hasDuplexer(), "Repeater does not see its duplexer"))
				.thenExecute(() -> {
					CompoundTag settings = new CompoundTag();
					settings.putFloat("rxFrequency", INPUT_HZ);
					settings.putFloat("txFrequency", OUTPUT_HZ);
					settings.putFloat("rxTone", TONE);
					settings.putString("callsign", "W1DIR");
					settings.putInt("hang", 20);
					repeater.applySettings(settings);
					VHFRadioBlockEntity user = GameTestUtil.blockEntity(helper, USER, VHFRadioBlockEntity.class);
					user.setFrequency(INPUT_HZ);
					user.setCtcssTone(TONE);
					GameTestUtil.blockEntity(helper, LISTENER, RadioBlockEntity.class).setFrequency(OUTPUT_HZ);
					powerOn(helper, REPEATER);
					powerOn(helper, USER);
					powerOn(helper, LISTENER);
					RepeaterRegistry.Info info = RepeaterRegistry.get(UUID.nameUUIDFromBytes(("radiocraft-repeater-" + helper.absolutePos(REPEATER).asLong()).getBytes(StandardCharsets.UTF_8)));
					helper.assertTrue(info != null && info.callsign().equals("W1DIR") && info.accessTone() == TONE, "The registry does not know the repeater: " + info);
				})
				.thenIdle(1) // Bare connectors register on the next tick.
				.thenExecute(() -> {
					CompoundTag before = app(computer, player, "list", new CompoundTag());
					helper.assertTrue(before.getCompound("data").getList("entries", Tag.TAG_COMPOUND).isEmpty(), "Something listed before the repeater was used: " + before);
					spokeAt[0] = helper.getLevel().getGameTime();
					VHFRadioBlockEntity user = GameTestUtil.blockEntity(helper, USER, VHFRadioBlockEntity.class);
					user.setPTTDown(true);
					helper.assertTrue(user.canTransmitVoice(), "The user's station cannot transmit");
					user.acceptVoicePacket(() -> helper.getLevel(), toneFrame(), OPERATOR);
					user.setPTTDown(false);
				})
				.thenWaitUntil(() -> helper.assertTrue(GameTestUtil.blockEntity(helper, LISTENER, RadioBlockEntity.class).getRepeatersHeard()
						.recent(helper.getLevel().getGameTime(), 1000L).size() == 1, "The listener never heard the repeater's own carrier or beep"))
				.thenWaitUntil(() -> helper.assertFalse(repeater.isTransmitting(), "Repeater is still transmitting"))
				.thenExecute(() -> {
					CompoundTag reply = app(computer, player, "list", new CompoundTag());
					helper.assertFalse(reply.contains("error"), "list failed: " + reply);
					CompoundTag data = reply.getCompound("data");
					helper.assertTrue(data.getInt("radios") >= 1, "No radio was asked: " + data);
					CompoundTag entry = find(data.getList("entries", Tag.TAG_COMPOUND), "W1DIR");
					helper.assertTrue(entry != null, "The repeater is not listed: " + data);
					helper.assertTrue(Math.abs(entry.getDouble("out") - 147.84D) < 0.0005D, "output " + entry);
					helper.assertTrue(entry.getInt("offset") == -600, "offset " + entry);
					helper.assertTrue(entry.getFloat("tone") == TONE, "tone " + entry);
					helper.assertTrue(entry.getString("source").equals("HEARD") && entry.getLong("ago") >= 0L, "not heard: " + entry);
					helper.assertTrue(entry.getInt("distance") >= 0 && entry.getInt("distance") < 10, "distance " + entry);
					helper.assertTrue(computer.files().readNbt(RepeaterDirectory.FILE) != null, "The directory was not saved on the disk");

					CompoundTag typed = new CompoundTag();
					typed.putString("call", "K1HIL");
					typed.putDouble("out", 444.1D);
					typed.putInt("offset", 5000);
					typed.putString("notes", "Hilltop");
					helper.assertFalse(app(computer, player, "add", typed).contains("error"), "add failed");
					ListTag entries = app(computer, player, "list", new CompoundTag()).getCompound("data").getList("entries", Tag.TAG_COMPOUND);
					helper.assertTrue(entries.size() == 2, "Not two entries: " + entries);
					CompoundTag export = new CompoundTag();
					export.putString("file", "rpts.frq");
					export.put("indices", new IntArrayTag(new int[] { find(entries, "W1DIR").getInt("i"), find(entries, "K1HIL").getInt("i") }));
					CompoundTag exported = app(computer, player, "export", export);
					helper.assertTrue(exported.getCompound("data").getInt("added") == 2, "export: " + exported);
					CompoundTag file = computer.files().readNbt("RPTS.FRQ");
					helper.assertTrue(file != null, "No RPTS.FRQ written");
					FrequencyList list = FrequencyList.fromTag(file);
					helper.assertTrue(list.entries().stream().anyMatch(e -> e.name().equals("W1DIR") && Math.abs(e.rxMhz() - 147.84D) < 0.0005D
							&& Math.abs(e.txMhz() - 147.24D) < 0.0005D && e.tone() == TONE && e.mode() == FrequencyList.Mode.FM), "W1DIR not in the list: " + list.entries());
					helper.assertTrue(list.entries().stream().anyMatch(e -> e.name().equals("K1HIL") && Math.abs(e.txMhz() - 449.1D) < 0.0005D), "K1HIL not in the list");
					helper.assertTrue(app(computer, player, "export", export).getCompound("data").getInt("duplicates") == 2, "A second export added them again");
					helper.assertTrue(ComputerFileFormats.nbt(computer.files().read("RPTS.FRQ")) != null, "RPTS.FRQ is not a frequency list");
				})
				.thenSucceed();
	}

	/** Breaking a repeater takes it out of the registry (a radio's memory of it then finds nothing). */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40, batch = BATCH)
	public static void brokenRepeaterLeavesTheRegistry(GameTestHelper helper) {
		BlockPos at = new BlockPos(3, 2, 3);
		helper.setBlock(at, RadiocraftBlocks.VHF_REPEATER.get());
		UUID voiceId = UUID.nameUUIDFromBytes(("radiocraft-repeater-" + helper.absolutePos(at).asLong()).getBytes(StandardCharsets.UTF_8));
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(RepeaterRegistry.isRepeater(voiceId), "A placed repeater is not registered"))
				.thenExecute(() -> helper.destroyBlock(at))
				.thenWaitUntil(() -> helper.assertFalse(RepeaterRegistry.isRepeater(voiceId), "A broken repeater is still registered"))
				.thenSucceed();
	}
}
