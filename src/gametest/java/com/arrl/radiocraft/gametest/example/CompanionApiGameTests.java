package com.arrl.radiocraft.gametest.example;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.RadiocraftApi;
import com.arrl.radiocraft.api.antenna.AntennaNetworks;
import com.arrl.radiocraft.api.antenna.AntennaTypes;
import com.arrl.radiocraft.api.blockentities.radio.IRadio;
import com.arrl.radiocraft.api.radio.CompanionRadioBlockEntity;
import com.arrl.radiocraft.api.radio.RadioBands;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgramming;
import com.arrl.radiocraft.gametest.GameTestUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * 1.12 step 12: the example companion mod ({@link ExampleCompanionMod}), built only from the API, works with
 * RadioCraft's own radios. Its low band mobile talks both ways with the stock low band mobile over the bare
 * connectors, and through its own ground plane antenna on coax; the band rules read through {@link RadioBands} hold for
 * the stock bands and the example's registered 6m band. Each test is on its own low band channel so the plots side by
 * side do not hear each other. (The tests themselves use internal classes to set things up; the example does not.)
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class CompanionApiGameTests {

	private static final String BATCH = "companion_api";
	private static final UUID OPERATOR = UUID.nameUUIDFromBytes("gametest-companion".getBytes());

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

	/** Programs the stock low band mobile at {@code pos} to a simplex channel on {@code mhz} and selects it. */
	private static void program(GameTestHelper helper, BlockPos pos, String mhz) {
		CommercialMobileBlockEntity radio = GameTestUtil.blockEntity(helper, pos, CommercialMobileBlockEntity.class);
		CommercialProgramming.Result result = CommercialProgramming.writeChannel(radio, 0, CommercialChannel.parse("TEST|" + mhz));
		helper.assertTrue(result.ok(), "Programming " + mhz + " failed: " + result.problems());
		CommercialProgramming.select(radio, 0);
	}

	private static IRadio radio(GameTestHelper helper, BlockPos pos) {
		return GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class); // Every radio is an IRadio.
	}

	private static void speak(GameTestHelper helper, BlockPos pos) {
		RadioBlockEntity radio = GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class);
		radio.setPTTDown(true);
		helper.assertTrue(radio.canTransmitVoice(), "Radio at " + pos.toShortString() + " cannot transmit");
		radio.acceptVoicePacket(() -> helper.getLevel(), tone(), OPERATOR);
		radio.setPTTDown(false);
	}

	private static boolean heardNow(GameTestHelper helper, BlockPos pos) {
		return radio(helper, pos).getLastSignalGameTime() == helper.getLevel().getGameTime();
	}

	private static boolean bare(GameTestHelper helper, BlockPos pos) {
		return GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).getBareConnector() != null;
	}

	/** The example mobile and the stock low band mobile on 33.120 MHz hear each other both ways, in band. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void exampleRadioTalksToStockLowBandMobile(GameTestHelper helper) {
		BlockPos a = new BlockPos(4, 2, 4), b = new BlockPos(6, 2, 4);
		helper.setBlock(a, ExampleCompanionMod.LOW_BAND_RADIO);
		helper.setBlock(b, RadiocraftBlocks.LOW_BAND_MOBILE.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					GameTestUtil.blockEntity(helper, a, CompanionRadioBlockEntity.class).setFrequency(33_120_000.0F);
					program(helper, b, "33.120");
					powerOn(helper, a);
					powerOn(helper, b);
				})
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, a) && bare(helper, b), "No bare connectors yet"))
				.thenExecute(() -> {
					IRadio example = radio(helper, a);
					helper.assertTrue(example.getBand() != null && RadioBands.LOW_BAND.equals(example.getBand().name()), "The example is on low band, was " + example.getBand());
					helper.assertTrue(example.isFM(), "The example is FM");
					helper.assertFalse(example.isTransmitInhibited() || example.isOutOfBand(), "33.120 MHz is in low band");
					speak(helper, a);
					helper.assertTrue(heardNow(helper, b), "The stock mobile did not hear the example");
					speak(helper, b);
					helper.assertTrue(heardNow(helper, a), "The example did not hear the stock mobile");
				})
				.thenSucceed();
	}

	/**
	 * The example mobile with the example ground plane on its coax: the antenna is matched (SWR 1.2 on low band, as the
	 * type says), the radio leaves its bare connector, and signals go both ways through the antenna to a stock mobile
	 * standing next to it on 33.140 MHz.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 300, batch = BATCH)
	public static void exampleAntennaCarriesTheSignal(GameTestHelper helper) {
		BlockPos radio = new BlockPos(4, 2, 8), coax = new BlockPos(4, 2, 7), antenna = new BlockPos(4, 2, 6), stock = new BlockPos(6, 2, 5);
		helper.setBlock(radio, ExampleCompanionMod.LOW_BAND_RADIO);
		helper.setBlock(antenna, ExampleCompanionMod.GROUND_PLANE);
		GameTestUtil.placeWire(helper, coax, RadiocraftBlocks.COAX_WIRE.get()); // After both ends, so it joins them.
		helper.setBlock(stock, RadiocraftBlocks.LOW_BAND_MOBILE.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertTrue(AntennaTypes.match(helper.getLevel(), helper.absolutePos(antenna)) != null
							&& AntennaTypes.match(helper.getLevel(), helper.absolutePos(antenna)).getType() == ExampleCompanionMod.GROUND_PLANE_ANTENNA,
							"The ground plane block is not matched as the example antenna type");
					helper.assertTrue(AntennaTypes.getType(ExampleCompanionMod.id("ground_plane")) == ExampleCompanionMod.GROUND_PLANE_ANTENNA, "The type is not registered");
					GameTestUtil.blockEntity(helper, radio, CompanionRadioBlockEntity.class).setFrequency(33_140_000.0F);
					program(helper, stock, "33.140");
					powerOn(helper, radio);
					powerOn(helper, stock);
				})
				.thenWaitUntil(() -> helper.assertTrue(Math.abs(radio(helper, radio).getAntennaSWR() - 1.2D) < 0.01D,
						"The radio should see the ground plane's SWR 1.2, sees " + radio(helper, radio).getAntennaSWR()))
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, stock), "No bare connector on the stock mobile yet"))
				.thenExecute(() -> {
					helper.assertFalse(bare(helper, radio), "With an antenna on its coax the example radio has no bare connector");
					speak(helper, radio);
					helper.assertTrue(heardNow(helper, stock), "The stock mobile did not hear the example through its antenna");
					speak(helper, stock);
					helper.assertTrue(heardNow(helper, radio), "The example did not hear the stock mobile through its antenna");
					// 6m is not low band: the same antenna reads its 6m SWR there, and 10 far off.
					helper.assertTrue(ExampleCompanionMod.GROUND_PLANE_ANTENNA.getSWR(null, 50_500_000.0F) == 1.5D, "6m SWR");
					helper.assertTrue(ExampleCompanionMod.GROUND_PLANE_ANTENNA.getSWR(null, 146_000_000.0F) == 10.0D, "2m is not covered");
				})
				.thenSucceed();
	}

	/** The band rules through the API, for the stock 1.12 bands and the example's registered 6m. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = BATCH)
	public static void bandRulesThroughTheApi(GameTestHelper helper) {
		BlockPos a = new BlockPos(4, 2, 12);
		helper.setBlock(a, ExampleCompanionMod.LOW_BAND_RADIO);
		helper.runAfterDelay(2, () -> {
			Band low = RadioBands.get(RadioBands.LOW_BAND);
			Band commercial = RadioBands.get(RadioBands.VHF_COMMERCIAL);
			Band six = RadioBands.get("6m");
			helper.assertTrue(low != null && low.minFrequency() == 30_000_000.0F && low.maxFrequency() == 50_000_000.0F, "Low band is 30-50 MHz: " + low);
			helper.assertTrue(commercial != null && commercial.isVhf(), "Commercial VHF is VHF: " + commercial);
			helper.assertTrue(six != null && RadioBands.isAmateur(six) && !RadioBands.isAmateur(low), "6m is amateur, low band is not");
			helper.assertTrue(RadioBands.registered().contains(six), "6m is listed as registered");
			helper.assertTrue(RadioBands.all().stream().anyMatch(band -> band.name().equals("6m")), "6m is in all()");
			Band at = RadioBands.at(52_000_000.0D);
			helper.assertTrue(at != null && at.name().equals("6m"), "52 MHz is 6m: " + at);
			at = RadioBands.at(40_000_000.0D);
			helper.assertTrue(at != null && at.name().equals(RadioBands.LOW_BAND), "40 MHz is low band: " + at);
			helper.assertTrue(six.conditionName().equals(RadioBands.M10) && six.displayName().equals("6m"), "6m follows 10m's solar weather");
			// Commercial bands tune exactly the band; amateur ones a quarter past each edge, transmitting there with a MARS mod.
			helper.assertFalse(RadioBands.canTransmit(low, 51_000_000.0D, true), "No MARS extension on low band");
			helper.assertFalse(RadioBands.canTransmit(six, 49_500_000.0D, false), "49.5 MHz is out of 6m");
			helper.assertTrue(RadioBands.canTransmit(six, 49_500_000.0D, true), "A MARS mod reaches 49.5 MHz from 6m");
			helper.assertTrue(RadioBands.splatterReach(low, 40_000_000.0D) == 1.0D, "In band: no extra splatter");
			helper.assertTrue(RadioBands.antennaNetwork(low).equals(AntennaNetworks.HF) && RadioBands.antennaNetwork(commercial).equals(AntennaNetworks.VHF), "Networks by band");
			RadioBands.MarineChannel sixteen = RadioBands.marineChannel("16");
			helper.assertTrue(sixteen != null && Math.abs(sixteen.transmitHz() - 156_800_000.0D) < 1.0D, "Channel 16 is 156.800 MHz: " + sixteen);
			// The example radio's transmitter follows the rules: 51 MHz is outside low band.
			IRadio example = radio(helper, a);
			helper.assertTrue(example.mayTransmitOn(40_000_000.0D) && !example.mayTransmitOn(51_000_000.0D), "The example may transmit in low band only");
			helper.assertTrue(RadiocraftApi.API_VERSION_MAJOR == 1, "API version 1");
			helper.succeed();
		});
	}
}
