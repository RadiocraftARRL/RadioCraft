package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.blockentities.WeatherAlertReceiverBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.VHFReceiverBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.api.antenna.IAntenna;
import com.arrl.radiocraft.api.antenna.IAntennaType;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.AntennaCWPacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket;
import com.arrl.radiocraft.common.radio.antenna.ReceiverWhipAntenna;
import com.arrl.radiocraft.common.radio.antenna.data.AntennaData;
import com.arrl.radiocraft.common.radio.antenna.data.RubberDuckyAntennaData;
import com.arrl.radiocraft.common.radio.antenna.types.RubberDuckyAntennaType;
import com.arrl.radiocraft.common.radio.morse.CWBuffer;
import com.arrl.radiocraft.common.radio.voice.handheld.HandheldRadiation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The VHF receiver (2026-11-09): it runs from a small cell with no power wire, draining about twice as fast as the
 * weather alert receiver while it listens and faster still while it plays a signal; the cell comes out as it went in,
 * a flat alkaline as a dead one. Since it became an eight-channel scanner (2026-11-09): the channels switched on are
 * scanned and the scan stops on a signal; SET retunes a channel only while it is the one switched on; a channel stays
 * inside 144 to 174 MHz; and a save from before the scanner loads. The cell tests switch power draw on for their batch
 * ({@link GameTestDefaults} runs with it off).
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class VHFReceiverGameTests {

	private static final String BATCH = "power_vhf_receiver";
	private static final BlockPos RX = new BlockPos(2, 1, 2), BARE = new BlockPos(6, 1, 2);
	/** Ticks each drain is measured over. */
	private static final int SPAN = 200;

	@BeforeBatch(batch = BATCH)
	public static void powerRequired(ServerLevel level) {
		RadiocraftServerConfig.RADIO_POWER_REQUIRED.set(true);
	}

	@AfterBatch(batch = BATCH)
	public static void powerFree(ServerLevel level) {
		RadiocraftServerConfig.RADIO_POWER_REQUIRED.set(false);
	}

	private static ItemStack cell(ItemStack stack, int fraction100) {
		IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
		if(energy != null) {
			energy.extractEnergy(energy.getMaxEnergyStored(), false);
			energy.receiveEnergy(energy.getMaxEnergyStored() * fraction100 / 100, false);
		}
		return stack;
	}

	private static VHFReceiverBlockEntity rx(GameTestHelper helper, BlockPos pos) {
		return GameTestUtil.blockEntity(helper, pos, VHFReceiverBlockEntity.class);
	}

	/** With a cell and no wire it switches on; one without either stays dark. Hearing a signal drains the cell faster. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 2 * SPAN + 60, batch = BATCH)
	public static void receiverRunsOnACellAndDrainsFasterWhileHearing(GameTestHelper helper) {
		helper.setBlock(RX, RadiocraftBlocks.VHF_RECEIVER.get());
		helper.setBlock(BARE, RadiocraftBlocks.VHF_RECEIVER.get());
		int[] energy = new int[3];
		short[] frame = new short[960];
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					double ratio = VHFReceiverBlockEntity.idleDrainPerTick() / WeatherAlertReceiverBlockEntity.drainPerTick();
					helper.assertTrue(Math.abs(ratio - 2.0D) < 0.01D, "a cell lasts " + (1.0D / ratio) + " of the weather radio's time, not half");
					rx(helper, RX).swapCell(cell(new ItemStack(RadiocraftItems.SMALL_BATTERY.get()), 100));
					rx(helper, RX).toggle();
					rx(helper, BARE).toggle();
				})
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertTrue(rx(helper, RX).isPowered(), "a receiver with a full cell and no wire does not run");
					helper.assertTrue(rx(helper, RX).isOnCell(), "it runs, but not from its cell");
					helper.assertFalse(rx(helper, BARE).isPowered(), "a receiver with no cell and no wire runs");
					energy[0] = rx(helper, RX).getCellEnergy();
				})
				.thenIdle(SPAN)
				.thenExecute(() -> energy[1] = rx(helper, RX).getCellEnergy())
				.thenExecuteFor(SPAN, () -> rx(helper, RX).playBroadcast(helper, frame, 0.8D)) // A station on the air the whole time.
				.thenExecute(() -> {
					energy[2] = rx(helper, RX).getCellEnergy();
					int idle = energy[0] - energy[1], hearing = energy[1] - energy[2];
					helper.assertTrue(idle > 0, "listening drew nothing from the cell");
					helper.assertTrue(hearing >= 2 * idle, "hearing a signal drew " + hearing + " FE, listening " + idle + ": not faster");
					helper.assertTrue(rx(helper, RX).isOnCell(), "it stopped running from the cell");
				})
				.thenSucceed();
	}

	/** A NiCd comes back with its charge; a flat alkaline comes back dead, and the receiver goes dark. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = BATCH)
	public static void cellComesOutAsItIsAndAFlatAlkalineDead(GameTestHelper helper) {
		helper.setBlock(RX, RadiocraftBlocks.VHF_RECEIVER.get());
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					VHFReceiverBlockEntity rx = rx(helper, RX);
					rx.swapCell(cell(new ItemStack(RadiocraftItems.SMALL_BATTERY.get()), 50));
					int before = rx.getCellEnergy();
					ItemStack out = rx.swapCell(ItemStack.EMPTY);
					helper.assertTrue(out.is(RadiocraftItems.SMALL_BATTERY.get()), "a NiCd came out as " + out);
					IEnergyStorage storage = out.getCapability(Capabilities.EnergyStorage.ITEM);
					helper.assertTrue(storage != null && storage.getEnergyStored() == before, "the NiCd lost or gained charge in the receiver");
					helper.assertFalse(rx.hasCell(), "the cell is still in");
					rx.swapCell(cell(new ItemStack(RadiocraftItems.SMALL_ALKALINE_BATTERY.get()), 0));
					rx.toggle();
				})
				.thenIdle(3)
				.thenExecute(() -> {
					helper.assertFalse(rx(helper, RX).isPowered(), "it runs from a flat alkaline");
					ItemStack out = rx(helper, RX).swapCell(ItemStack.EMPTY);
					helper.assertTrue(out.is(RadiocraftItems.DEAD_ALKALINE_BATTERY.get()), "a flat alkaline came out as " + out);
				})
				.thenSucceed();
	}

	/** Two channels on scan back and forth; a signal stops the scan on the channel it is on until it goes quiet. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 400)
	public static void scanStepsChannelsAndStopsOnActivity(GameTestHelper helper) {
		helper.setBlock(RX, RadiocraftBlocks.VHF_RECEIVER.get());
		float first = 146_520_000.0F, second = 156_800_000.0F;
		Set<Float> seen = new HashSet<>();
		float[] held = new float[1];
		boolean[] left = new boolean[1];
		short[] frame = new short[960];
		int scanSpan = 6 * VHFReceiverBlockEntity.SCAN_DWELL_TICKS;
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					VHFReceiverBlockEntity rx = rx(helper, RX);
					rx.toggle();
					rx.setChannelHz(0, first);
					rx.setChannelHz(1, second);
					rx.setChannelOn(1, true);
					helper.assertTrue(rx.isChannelOn(0) && rx.isChannelOn(1) && rx.isScanning(), "channels 1 and 2 on do not scan");
				})
				.thenExecuteFor(scanSpan, () -> seen.add(rx(helper, RX).getFrequency()))
				.thenExecute(() -> helper.assertTrue(seen.contains(first) && seen.contains(second), "the scan did not visit both channels: " + seen))
				.thenExecuteFor(4, () -> rx(helper, RX).playBroadcast(helper, frame, 0.8D)) // A station comes up: the scan stops on it.
				.thenExecute(() -> {
					held[0] = rx(helper, RX).getFrequency();
					helper.assertTrue(rx(helper, RX).isScanStopped(), "a signal did not stop the scan");
				})
				.thenExecuteFor(scanSpan, () -> {
					rx(helper, RX).playBroadcast(helper, frame, 0.8D);
					helper.assertTrue(rx(helper, RX).getFrequency() == held[0], "the scan moved off a channel with a signal on it");
				})
				// With two channels the scan comes back to this one every other dwell, so look for it leaving at any time.
				.thenExecuteFor(VHFReceiverBlockEntity.SCAN_HANG_TICKS + 2 * VHFReceiverBlockEntity.SCAN_DWELL_TICKS + 4, () -> {
					if(rx(helper, RX).getFrequency() != held[0])
						left[0] = true;
				})
				.thenExecute(() -> {
					VHFReceiverBlockEntity rx = rx(helper, RX);
					helper.assertTrue(left[0], "the scan stayed after the signal went quiet");
					rx.setChannelOn(0, false);
				})
				.thenIdle(2)
				.thenExecute(() -> {
					VHFReceiverBlockEntity rx = rx(helper, RX);
					helper.assertTrue(rx.getFrequency() == second && rx.getScanChannel() == 1 && !rx.isScanning(), "one channel on does not sit on it");
					rx.setChannelOn(1, false);
					helper.assertTrue(rx.channelsOnCount() == 0, "switching both off left one on");
				})
				.thenSucceed();
	}

	/** SET retunes a channel only while it is the one switched on; TUNE does nothing otherwise. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 60)
	public static void setRetunesOnlyWithOneChannelOn(GameTestHelper helper) {
		helper.setBlock(RX, RadiocraftBlocks.VHF_RECEIVER.get());
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					VHFReceiverBlockEntity rx = rx(helper, RX);
					rx.toggle();
					rx.setChannelHz(0, 146_520_000.0F);
					rx.setChannelHz(1, 156_800_000.0F);
					rx.setChannelOn(1, true);
					helper.assertFalse(rx.tuneSetChannel(VHFReceiverBlockEntity.STEP_HZ), "TUNE moved a channel with SET out");
					rx.setSetMode(true);
					helper.assertFalse(rx.isSetLit(), "the SET lamp lit with two channels on");
					helper.assertFalse(rx.tuneSetChannel(VHFReceiverBlockEntity.STEP_HZ), "TUNE moved a channel with two on");
					helper.assertTrue(rx.getChannelHz(0) == 146_520_000.0F && rx.getChannelHz(1) == 156_800_000.0F, "a channel moved with two on");
					rx.setChannelOn(0, false);
					helper.assertTrue(rx.isSetLit(), "the SET lamp is dark with SET in and one channel on");
					helper.assertTrue(rx.tuneSetChannel(VHFReceiverBlockEntity.COARSE_STEP_HZ), "TUNE did not move the one channel on");
					helper.assertTrue(rx.getChannelHz(1) == VHFReceiverBlockEntity.snapHz(156_825_000.0D), "channel 2 is on " + rx.getChannelHz(1) + ", not 156.825");
					helper.assertTrue(rx.getChannelHz(0) == 146_520_000.0F, "TUNE moved a channel that is off");
				})
				.thenIdle(2)
				.thenExecute(() -> {
					VHFReceiverBlockEntity rx = rx(helper, RX);
					helper.assertTrue(rx.getFrequency() == rx.getChannelHz(1), "the receiver did not follow the channel being set");
					rx.setSetMode(false);
					helper.assertFalse(rx.tuneSetChannel(VHFReceiverBlockEntity.STEP_HZ), "TUNE moved a channel after SET came out");
				})
				.thenSucceed();
	}

	/** A channel's frequency stays inside 144 to 174 MHz, on the 2.5 kHz grid, however far TUNE turns. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void channelFrequencyClampsTo144To174(GameTestHelper helper) {
		helper.setBlock(RX, RadiocraftBlocks.VHF_RECEIVER.get());
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					VHFReceiverBlockEntity rx = rx(helper, RX);
					rx.toggle();
					rx.setSetMode(true);
					helper.assertTrue(rx.onlyChannel() == 0, "a new receiver does not have just channel 1 on");
					rx.setChannelHz(0, 140_000_000.0F);
					helper.assertTrue(rx.getChannelHz(0) == VHFReceiverBlockEntity.MIN_HZ, "140 MHz did not clamp to 144");
					rx.tuneSetChannel(-VHFReceiverBlockEntity.FAST_STEP_HZ);
					helper.assertTrue(rx.getChannelHz(0) == VHFReceiverBlockEntity.MIN_HZ, "TUNE went under 144 MHz");
					rx.setChannelHz(0, 173_900_000.0F);
					rx.tuneSetChannel(VHFReceiverBlockEntity.FAST_STEP_HZ);
					helper.assertTrue(rx.getChannelHz(0) == VHFReceiverBlockEntity.MAX_HZ, "TUNE went over 174 MHz: " + rx.getChannelHz(0));
					rx.setChannelHz(0, 146_521_200.0F);
					helper.assertTrue(rx.getChannelHz(0) == VHFReceiverBlockEntity.snapHz(146_520_000.0D), "146.5212 did not land on 146.520");
					rx.setChannelHz(0, 154_437_500.0F);
					helper.assertTrue(Math.abs(rx.getChannelHz(0) - 154_437_500.0D) < 20.0D, "a 12.5 kHz commercial channel is off the grid");
				})
				.thenSucceed();
	}

	/**
	 * A receiver saved before the scanner (a dial and six memories) loads: its memories become channels, the selected
	 * ones switched on; one that listened on its dial gets the dial's frequency in its first empty channel, switched on.
	 * Memories past eight (no old receiver had them) are kept.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void oldMemoriesLoadAsChannels(GameTestHelper helper) {
		helper.setBlock(RX, RadiocraftBlocks.VHF_RECEIVER.get());
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					VHFReceiverBlockEntity rx = rx(helper, RX);
					HolderLookup.Provider registries = helper.getLevel().registryAccess();
					CompoundTag tag = rx.saveWithoutMetadata(registries);
					for(String key : new String[] { "channels", "channelsOn", "setMode", "scanHold", "scanChannel", "scanStopped" })
						tag.remove(key);
					int[] old = new int[10];
					old[0] = Float.floatToIntBits(146_520_000.0F);
					old[2] = Float.floatToIntBits(156_800_000.0F);
					old[9] = Float.floatToIntBits(147_330_000.0F);
					tag.putIntArray("memories", old);
					tag.putInt("memorySelected", 0b100);
					tag.putBoolean("muted", true);
					rx.loadWithComponents(tag, registries);
					helper.assertTrue(rx.getChannelHz(0) == 146_520_000.0F && rx.getChannelHz(2) == 156_800_000.0F, "the memories did not become channels");
					helper.assertTrue(rx.isChannelOn(2) && rx.channelsOnCount() == 1, "the selected memory is not the channel switched on");
					helper.assertTrue(rx.getChannelHz(1) == VHFReceiverBlockEntity.FACTORY_HZ[1], "an empty memory did not keep its factory crystal");
					helper.assertTrue(rx.getExtraMemories().length == 2 && rx.getExtraMemories()[1] == old[9], "the memories past eight were lost");

					tag.putInt("memorySelected", 0); // Listening on the dial.
					tag.putFloat("frequency", 162_400_000.0F);
					rx.loadWithComponents(tag, registries);
					helper.assertTrue(rx.isChannelOn(1) && rx.channelsOnCount() == 1 && rx.getChannelHz(1) == 162_400_000.0F,
							"the dial's frequency did not go into the first empty channel, switched on");
				})
				.thenSucceed();
	}

	/** A stand-in handheld for the whip test: an antenna at {@code at} that only has a place. */
	private static IAntenna handheldAt(ServerLevel level, BlockPos at) {
		IAntenna.AntennaPos pos = new IAntenna.AntennaPos(at.immutable(), level);
		return new IAntenna() {
			@Override public void transmitAudioPacket(de.maxhenkel.voicechat.api.ServerLevel l, short[] rawAudio, Band band, float hz, UUID player) {}
			@Override public void receiveAudioPacket(AntennaVoicePacket packet) {}
			@Override public void transmitCWPacket(ServerLevel l, Collection<CWBuffer> buffers, Band band, float hz) {}
			@Override public void receiveCWPacket(AntennaCWPacket packet) {}
			@Override public AntennaPos getAntennaPos() { return pos; }
			@Override public IAntennaType<? extends AntennaData> getType() { return new RubberDuckyAntennaType(); }
			@Override public AntennaData getData() { return new RubberDuckyAntennaData(0.155); }
			@Override public Player getPlayer() { return null; }
		};
	}

	/**
	 * With nothing on its coax the receiver's own whip is on the air (not the bare connector), and it hears a handheld as
	 * a handheld would: what a handheld puts into it ({@link HandheldRadiation#strength}) arrives with no gain added,
	 * well inside 2 m line of sight; past it a handheld reaches nothing, so neither does the whip.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 60)
	public static void whipHearsAHandheldOutToHandheldRange(GameTestHelper helper) {
		helper.setBlock(RX, RadiocraftBlocks.VHF_RECEIVER.get());
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> rx(helper, RX).toggle())
				.thenIdle(3)
				.thenExecute(() -> {
					VHFReceiverBlockEntity rx = rx(helper, RX);
					ReceiverWhipAntenna whip = rx.getWhip();
					helper.assertTrue(whip != null, "the whip is not on the air with nothing on the coax");
					helper.assertTrue(rx.getBareConnector() == null, "the bare connector is on the air beside the whip");
					ServerLevel level = helper.getLevel();
					Band band = Band.getBand("2m");
					float hz = rx.getFrequency();
					BlockPos here = helper.absolutePos(RX);
					int near = Math.max(4, band.losRange() / 2), far = band.losRange() * 2 + 16;

					IAntenna close = handheldAt(level, here.east(near));
					AntennaVoicePacket packet = new AntennaVoicePacket(() -> level, new short[960], band, hz, 1.0D, close, UUID.randomUUID());
					double strength = HandheldRadiation.strength(packet, level, band, close.getAntennaPos(), whip.getAntennaPos());
					helper.assertTrue(strength > 0.0D, "a handheld " + near + " blocks away reaches nothing");
					packet.setStrength(strength);
					packet.setDestination(whip);
					whip.receiveAudioPacket(packet);
					helper.assertTrue(rx.getCarrierGameTime() == level.getGameTime(), "the whip did not hear a handheld " + near + " blocks away");
					helper.assertTrue(rx.getCarrierStrength() <= strength + 1.0E-9D, "the whip heard " + rx.getCarrierStrength() + ", more than a handheld's " + strength);

					IAntenna distant = handheldAt(level, here.east(far));
					AntennaVoicePacket away = new AntennaVoicePacket(() -> level, new short[960], band, hz, 1.0D, distant, UUID.randomUUID());
					double farStrength = HandheldRadiation.strength(away, level, band, distant.getAntennaPos(), whip.getAntennaPos());
					helper.assertTrue(farStrength <= 0.0D, "a handheld " + far + " blocks away still reaches " + farStrength);
				})
				.thenSucceed();
	}
}
