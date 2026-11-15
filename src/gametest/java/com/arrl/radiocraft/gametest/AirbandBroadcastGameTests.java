package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.AntennaNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.radio.AirbandRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.FmBroadcastReceiverBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.FmBroadcastTransmitterBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.blocks.radios.FmBroadcastTransmitterBlock;
import com.arrl.radiocraft.common.init.RadiocraftAntennaTypes;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.types.vhf.AirbandAntennaType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** The airband radio and antenna, and the FM broadcast transmitter and receiver (2026-11-13). */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class AirbandBroadcastGameTests {

	private static final String BATCH = "airband_broadcast";
	private static final UUID OPERATOR = UUID.nameUUIDFromBytes("gametest-airband".getBytes());
	private static final BlockPos A = new BlockPos(3, 2, 3), B = new BlockPos(6, 2, 3);

	private static short[] tone() {
		short[] audio = new short[960];
		for(int i = 0; i < audio.length; i++)
			audio[i] = (short)(Math.sin(2 * Math.PI * 440 * i / 48000.0D) * 8000);
		return audio;
	}

	private static void powerOn(GameTestHelper helper, BlockPos pos) {
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, pos, RadioNetworkObject.class);
		if(!networkObject.isPowered)
			GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).toggle();
		helper.assertTrue(networkObject.isPowered, "Radio at " + pos.toShortString() + " did not power on");
	}

	private static void speak(GameTestHelper helper, BlockPos pos) {
		RadioBlockEntity radio = GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class);
		radio.setPTTDown(true);
		helper.assertTrue(radio.canTransmitVoice(), "Radio at " + pos.toShortString() + " cannot transmit");
		radio.acceptVoicePacket(() -> helper.getLevel(), tone(), OPERATOR);
		radio.setPTTDown(false);
	}

	/** Tuning moves STANDBY in 25 kHz steps (1 MHz on the big knob), inside the band; the flip-flop swaps them. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void airbandTunesStandbyAndFlips(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.AIRBAND_RADIO.get());
		AirbandRadioBlockEntity radio = GameTestUtil.blockEntity(helper, A, AirbandRadioBlockEntity.class);
		helper.assertTrue(radio.getBand() != null && Band.AIRBAND.equals(radio.getBand().name()) && radio.isAM(), "Not an AM airband radio: " + radio.getBand());
		helper.assertTrue(radio.getFrequency() == AirbandRadioBlockEntity.DEFAULT_USE_HZ, "Not on 121.5: " + radio.getFrequency());
		radio.updateFrequency(1);
		helper.assertTrue(radio.getStandbyFrequency() == 122_775_000.0F, "A click is not 25 kHz: " + radio.getStandbyFrequency());
		radio.updateFrequency(AirbandRadioBlockEntity.COARSE_STEPS);
		helper.assertTrue(radio.getStandbyFrequency() == 123_775_000.0F, "The big knob is not 1 MHz: " + radio.getStandbyFrequency());
		helper.assertTrue(radio.getFrequency() == AirbandRadioBlockEntity.DEFAULT_USE_HZ, "Tuning moved USE");
		radio.flip();
		helper.assertTrue(radio.getFrequency() == 123_775_000.0F && radio.getStandbyFrequency() == AirbandRadioBlockEntity.DEFAULT_USE_HZ, "The flip-flop did not swap");
		radio.updateFrequency(-10_000);
		helper.assertTrue(radio.getStandbyFrequency() == AirbandRadioBlockEntity.LOW_HZ, "Tuned below the band: " + radio.getStandbyFrequency());
		helper.succeed();
	}

	/**
	 * The aircraft antenna is matched on the airband and is worse on 2m; a J-pole, cut for 2m, is the other way round.
	 * Hanging under a block it is still an aircraft antenna.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void aircraftAntennaSuitsTheAirband(GameTestHelper helper) {
		helper.assertTrue(AirbandAntennaType.swr(121_500_000.0D) < 1.7D && AirbandAntennaType.swr(118_000_000.0D) < 2.0D
				&& AirbandAntennaType.swr(136_975_000.0D) < 2.0D, "Not matched across the airband");
		helper.assertTrue(AirbandAntennaType.swr(146_520_000.0D) > 2.0D, "As good on 2m as on the airband");
		helper.assertTrue(RadiocraftAntennaTypes.J_POLE.getSWR(null, 121_500_000.0F) > 2.0D && RadiocraftAntennaTypes.J_POLE.getSWR(null, 146_520_000.0F) == 1.0D,
				"A J-pole is not cut for 2m");
		BlockPos hung = new BlockPos(3, 4, 3);
		helper.setBlock(hung.above(), net.minecraft.world.level.block.Blocks.STONE);
		helper.setBlock(hung, RadiocraftBlocks.AIRBAND_ANTENNA.get().defaultBlockState()
				.setValue(com.arrl.radiocraft.common.blocks.antennas.AirbandAntennaBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.CEILING));
		helper.startSequence()
				.thenWaitUntil(() -> {
					AntennaNetworkObject antenna = GameTestUtil.networkObject(helper, hung, AntennaNetworkObject.class);
					helper.assertTrue(antenna.getAntenna() != null && antenna.getAntenna().type == RadiocraftAntennaTypes.AIRBAND, "Not an aircraft antenna yet");
				})
				.thenSucceed();
	}

	/** The airband ground plane stands two blocks tall and is an airband antenna at its foot, reaching further than the aircraft one. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void airbandGroundPlaneStands(GameTestHelper helper) {
		BlockPos foot = new BlockPos(3, 1, 3); // On the ground: it needs something solid under it.
		helper.setBlock(foot, RadiocraftBlocks.AIRBAND_GROUND_PLANE.get().defaultBlockState()
				.setValue(com.arrl.radiocraft.common.blocks.antennas.DoubleVHFAntennaBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
		RadiocraftBlocks.AIRBAND_GROUND_PLANE.get().setPlacedBy(helper.getLevel(), helper.absolutePos(foot), helper.getBlockState(foot), null, ItemStack.EMPTY);
		helper.assertTrue(helper.getBlockState(foot.above()).is(RadiocraftBlocks.AIRBAND_GROUND_PLANE.get()), "No upper half");
		helper.startSequence()
				.thenWaitUntil(() -> {
					AntennaNetworkObject antenna = GameTestUtil.networkObject(helper, foot, AntennaNetworkObject.class);
					helper.assertTrue(antenna.getAntenna() != null && antenna.getAntenna().type == RadiocraftAntennaTypes.AIRBAND_GROUND_PLANE,
							"Not an airband ground plane yet");
				})
				.thenExecute(() -> helper.assertTrue(RadiocraftAntennaTypes.AIRBAND_GROUND_PLANE.getSWR(null, 121_500_000.0F) < 1.7D, "Not matched on the airband"))
				.thenSucceed();
	}

	/** Two airband radios on the same frequency hear each other in AM; one 25 kHz off does not. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void airbandRadiosTalk(GameTestHelper helper) {
		BlockPos c = new BlockPos(3, 2, 6);
		helper.setBlock(A, RadiocraftBlocks.AIRBAND_RADIO.get());
		helper.setBlock(B, RadiocraftBlocks.AIRBAND_RADIO.get());
		helper.setBlock(c, RadiocraftBlocks.AIRBAND_RADIO.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					AirbandRadioBlockEntity off = GameTestUtil.blockEntity(helper, c, AirbandRadioBlockEntity.class);
					off.updateFrequency(1);
					off.flip(); // 121.525.
					powerOn(helper, A);
					powerOn(helper, B);
					powerOn(helper, c);
				})
				.thenWaitUntil(() -> helper.assertTrue(GameTestUtil.blockEntity(helper, A, RadioBlockEntity.class).getBareConnector() != null
						&& GameTestUtil.blockEntity(helper, B, RadioBlockEntity.class).getBareConnector() != null
						&& GameTestUtil.blockEntity(helper, c, RadioBlockEntity.class).getBareConnector() != null, "No bare connectors yet"))
				.thenExecute(() -> {
					speak(helper, A);
					long now = helper.getLevel().getGameTime();
					helper.assertTrue(GameTestUtil.blockEntity(helper, B, RadioBlockEntity.class).getLastSignalGameTime() == now, "B did not hear A on 121.5");
					helper.assertFalse(GameTestUtil.blockEntity(helper, c, RadioBlockEntity.class).getLastSignalGameTime() == now, "A radio on 121.525 heard 121.5");
				})
				.thenSucceed();
	}

	/**
	 * The transmitter stands two blocks tall and lights up on the air; a receiver on its channel hears it (on its own
	 * wire antenna) and one a channel away does not. Breaking the top takes the transmitter down.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void broadcastTransmitterIsHeardOnItsChannel(GameTestHelper helper) {
		BlockPos tx = A, rx = B, other = new BlockPos(6, 2, 6);
		helper.setBlock(tx, RadiocraftBlocks.FM_BROADCAST_TRANSMITTER.get());
		RadiocraftBlocks.FM_BROADCAST_TRANSMITTER.get().setPlacedBy(helper.getLevel(), helper.absolutePos(tx), helper.getBlockState(tx), null, ItemStack.EMPTY);
		helper.setBlock(rx, RadiocraftBlocks.FM_BROADCAST_RECEIVER.get());
		helper.setBlock(other, RadiocraftBlocks.FM_BROADCAST_RECEIVER.get());
		helper.assertTrue(helper.getBlockState(tx.above()).is(RadiocraftBlocks.FM_BROADCAST_TRANSMITTER_TOP.get()), "No cabinet top");
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					GameTestUtil.blockEntity(helper, other, FmBroadcastReceiverBlockEntity.class).updateFrequency(1); // 100.1.
					powerOn(helper, tx);
					powerOn(helper, rx);
					powerOn(helper, other);
				})
				.thenWaitUntil(() -> helper.assertTrue(GameTestUtil.blockEntity(helper, tx, RadioBlockEntity.class).getBareConnector() != null
						&& GameTestUtil.blockEntity(helper, rx, FmBroadcastReceiverBlockEntity.class).getWhip() != null
						&& GameTestUtil.blockEntity(helper, other, FmBroadcastReceiverBlockEntity.class).getWhip() != null, "Not on the air yet"))
				.thenExecute(() -> {
					FmBroadcastTransmitterBlockEntity station = GameTestUtil.blockEntity(helper, tx, FmBroadcastTransmitterBlockEntity.class);
					helper.assertTrue(station.isFM() && station.getFrequency() == 99_900_000.0F && !station.isTransmitInhibited(), "Not on 99.9 FM: " + station.getFrequency());
					helper.assertFalse(GameTestUtil.blockEntity(helper, rx, RadioBlockEntity.class).canTransmitVoice(), "A receiver transmits");
					speak(helper, tx); // Into the station's mixer: it goes out with the next frames.
				})
				// On, the station is on the air all the time (its carrier, 2026-11-14): no PTT needed, heard on 99.9 only.
				.thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getGameTime() - GameTestUtil.blockEntity(helper, rx, RadioBlockEntity.class).getLastSignalGameTime() <= 1,
						"The receiver on 99.9 did not hear the station"))
				.thenExecute(() -> helper.assertTrue(helper.getLevel().getGameTime() - GameTestUtil.blockEntity(helper, other, RadioBlockEntity.class).getLastSignalGameTime() > 5,
						"A receiver on 100.1 heard 99.9"))
				.thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(tx).getValue(FmBroadcastTransmitterBlock.ON_AIR)
						&& helper.getBlockState(tx.above()).getValue(FmBroadcastTransmitterBlock.ON_AIR), "The cabinet did not light on the air"))
				.thenExecute(() -> {
					GameTestUtil.blockEntity(helper, tx, RadioBlockEntity.class).setPTTDown(false);
					helper.destroyBlock(tx.above());
				})
				.thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(tx).isAir(), "The transmitter stayed with its top broken"))
				.thenSucceed();
	}

	/**
	 * The airband radio's squelch knob reaches the server and shuts the squelch on band noise (2026-11-13: the server
	 * ignored it, so the static played whatever the knob said); the FM table radio, receive only, lends no hand mic.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void airbandSquelchAndReceiverMic(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.AIRBAND_RADIO.get());
		AirbandRadioBlockEntity radio = GameTestUtil.blockEntity(helper, A, AirbandRadioBlockEntity.class);
		helper.assertTrue(radio.hasSquelchKnob(), "The airband radio's squelch knob is not taken by the server");
		radio.setSquelch(1.0F);
		helper.assertFalse(radio.squelchOpenNow(), "Squelch all the way up and still open on noise");
		radio.setSquelch(0.0F);
		helper.assertTrue(radio.squelchOpenNow(), "Squelch at 0 and shut");
		BlockPos rx = A.east(2);
		helper.setBlock(rx, RadiocraftBlocks.FM_BROADCAST_RECEIVER.get());
		FmBroadcastReceiverBlockEntity receiver = GameTestUtil.blockEntity(helper, rx, FmBroadcastReceiverBlockEntity.class);
		helper.assertFalse(receiver.canLendHandMic(), "A receive-only table radio lends a hand mic");
		helper.succeed();
	}
}
