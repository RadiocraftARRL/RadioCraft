package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.AntennaNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.LinearAmplifierNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blocks.LinearAmplifierBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.RadiocraftServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The linear amplifier (2026-11-13) on the coax between a 20m radio and a dipole. Power draw is off on the GameTest
 * server, so OPERATE is enough for it to work. Its own batch, with coax loss switched on (it is off for the rest of the
 * run, see {@link GameTestDefaults}).
 * <pre>
 *   z=1  radio
 *   z=2  coax
 *   z=3  linear amplifier
 *   z=4  coax
 *   z=5  ---balun---
 * </pre>
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class LinearAmplifierGameTests {

	private static final BlockPos RADIO = new BlockPos(8, 2, 1);
	private static final BlockPos AMP = new BlockPos(8, 2, 3);
	private static final BlockPos BALUN = new BlockPos(8, 2, 5);
	private static final float TWENTY = 14_200_000.0F, TWO_METRES = 146_000_000.0F;
	private static final String BATCH = "amplifier";

	@BeforeBatch(batch = BATCH)
	public static void coaxLossOn(ServerLevel level) {
		RadiocraftServerConfig.COAX_LOSS.set(1.0D);
	}

	@AfterBatch(batch = BATCH)
	public static void coaxLossOff(ServerLevel level) {
		RadiocraftServerConfig.COAX_LOSS.set(0.0D);
	}

	private static void build(GameTestHelper helper) {
		helper.setBlock(RADIO, RadiocraftBlocks.HF_RADIO_20M.get());
		helper.setBlock(BALUN, RadiocraftBlocks.BALUN_ONE_TO_ONE.get());
		GameTestUtil.wire(helper, BALUN, BALUN.west(5));
		GameTestUtil.wire(helper, BALUN, BALUN.east(5));
		GameTestUtil.placeWire(helper, RADIO.south(), RadiocraftBlocks.COAX_WIRE.get());
		helper.setBlock(AMP, RadiocraftBlocks.LINEAR_AMPLIFIER.get());
		GameTestUtil.placeWire(helper, AMP.south(), RadiocraftBlocks.COAX_WIRE.get());
	}

	private static AntennaNetworkObject antenna(GameTestHelper helper) {
		return GameTestUtil.networkObject(helper, BALUN, AntennaNetworkObject.class);
	}

	private static LinearAmplifierNetworkObject amp(GameTestHelper helper) {
		return GameTestUtil.networkObject(helper, AMP, LinearAmplifierNetworkObject.class);
	}

	private static void set(GameTestHelper helper, boolean operate, boolean blown) {
		BlockPos at = helper.absolutePos(AMP);
		BlockState state = helper.getLevel().getBlockState(at);
		helper.getLevel().setBlockAndUpdate(at, state.setValue(LinearAmplifierBlock.OPERATE, operate).setValue(LinearAmplifierBlock.BLOWN, blown));
	}

	/**
	 * STANDBY passes the signal straight through; OPERATE makes HF 1.5 times stronger (and leaves VHF alone); a blown
	 * amplifier passes it through again. The coax run is the 4 blocks from radio to balun, a hair of loss.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void operateAmplifiesStandbyAndBlownPassThrough(GameTestHelper helper) {
		build(helper);
		double[] standby = new double[1];
		helper.startSequence()
				.thenWaitUntil(() -> {
					var antennas = GameTestUtil.networkObject(helper, RADIO, RadioNetworkObject.class).getAntennas();
					helper.assertTrue(antennas.size() == 1 && antennas.getFirst().getAntenna() != null, "Antenna not recognized yet");
					helper.assertTrue(antenna(helper).getAmplifier() != null, "the antenna does not see the amplifier on its coax");
				})
				.thenExecute(() -> {
					helper.assertTrue(antenna(helper).coaxLength() == 4, "coax run " + antenna(helper).coaxLength());
					double coax = antenna(helper).coaxFactor(TWENTY);
					helper.assertTrue(coax < 1.0D && coax > 0.99D, "4 blocks of coax on 20m should lose a hair: " + coax);
					standby[0] = antenna(helper).getTransmitFactor(TWENTY);
					helper.assertTrue(!amp(helper).isWorking() && standby[0] > 0.0D, "standby: working " + amp(helper).isWorking() + ", factor " + standby[0]);
					helper.assertTrue(Math.abs(standby[0] - antenna(helper).getMatchEfficiency(TWENTY) * coax) < 1e-9, "standby is not a straight pass-through");
					set(helper, true, false);
				})
				.thenWaitUntil(() -> helper.assertTrue(amp(helper).isWorking(), "not working in OPERATE"))
				.thenExecute(() -> {
					double operate = antenna(helper).getTransmitFactor(TWENTY);
					helper.assertTrue(Math.abs(operate - standby[0] * 1.5D) < 1e-6, "OPERATE gives " + operate + " against " + standby[0]);
					helper.assertTrue(amp(helper).gain(TWO_METRES) == 1.0D, "VHF was amplified");
					set(helper, true, true);
				})
				.thenWaitUntil(() -> helper.assertFalse(amp(helper).isWorking(), "still working when blown"))
				.thenExecute(() -> helper.assertTrue(Math.abs(antenna(helper).getTransmitFactor(TWENTY) - standby[0]) < 1e-9, "a blown amplifier still amplifies"))
				.thenSucceed();
	}

	/**
	 * CB through an amplifier is illegal (2026-11-13): a CB mobile on channel 21, keyed through the amplifier in OPERATE,
	 * marks its antenna for the FCC program; in STANDBY the mark runs out.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 300, batch = BATCH)
	public static void cbThroughTheAmplifierIsFlaggedForTheFcc(GameTestHelper helper) {
		helper.setBlock(RADIO, RadiocraftBlocks.CB_MOBILE_PC68.get());
		helper.setBlock(BALUN, RadiocraftBlocks.BALUN_ONE_TO_ONE.get());
		GameTestUtil.wire(helper, BALUN, BALUN.west(3));
		GameTestUtil.wire(helper, BALUN, BALUN.east(3));
		GameTestUtil.placeWire(helper, RADIO.south(), RadiocraftBlocks.COAX_WIRE.get());
		helper.setBlock(AMP, RadiocraftBlocks.LINEAR_AMPLIFIER.get());
		GameTestUtil.placeWire(helper, AMP.south(), RadiocraftBlocks.COAX_WIRE.get());
		BlockPos antennaAt = helper.absolutePos(BALUN);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(antenna(helper).getAntenna() != null && antenna(helper).getAmplifier() != null, "Antenna or amplifier not on the run yet"))
				.thenExecute(() -> {
					var radio = GameTestUtil.blockEntity(helper, RADIO, com.arrl.radiocraft.common.blockentities.radio.CbRadioBlockEntity.class);
					radio.setRadioState(radio.getRadioState().withChannel(21).withVolume(5)); // The volume knob is its power switch.
					set(helper, true, false);
				})
				.thenWaitUntil(() -> helper.assertTrue(GameTestUtil.networkObject(helper, RADIO, RadioNetworkObject.class).isPowered, "the CB radio is off"))
				.thenExecute(() -> {
					var radio = GameTestUtil.blockEntity(helper, RADIO, com.arrl.radiocraft.common.blockentities.radio.CbRadioBlockEntity.class);
					radio.setPTTDown(true);
					helper.assertTrue(radio.canTransmitVoice(), "the CB radio cannot transmit on " + radio.getFrequency());
				})
				.thenWaitUntil(() -> helper.assertTrue(LinearAmplifierNetworkObject.amplifiedCb(helper.getLevel(), antennaAt),
						"CB through the amplifier is not flagged (keyed " + amp(helper).isKeyed() + ")"))
				.thenExecute(() -> set(helper, false, false))
				.thenIdle((int)LinearAmplifierNetworkObject.CB_FLAG_TICKS + 5)
				.thenExecute(() -> {
					helper.assertFalse(LinearAmplifierNetworkObject.amplifiedCb(helper.getLevel(), antennaAt), "still flagged in STANDBY");
					GameTestUtil.blockEntity(helper, RADIO, com.arrl.radiocraft.common.blockentities.radio.CbRadioBlockEntity.class).setPTTDown(false);
				})
				.thenSucceed();
	}
}
