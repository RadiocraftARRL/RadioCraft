package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.AprsTerminalBlockEntity;
import com.arrl.radiocraft.common.blockentities.HeadlessTncBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.StationBus;
import com.arrl.radiocraft.common.blockentities.WeatherAlertGeneratorBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.data.AprsIcon;
import com.arrl.radiocraft.common.radio.data.AprsWeather;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Headless APRS TNC (2026-11-18): it beacons with a Local Weather Station's weather, a weather alert generator on its
 * bus counts the weather beacons it hears, it stands in for the radio interface for an H19 but not for a radio
 * computer, and switched off it neither sends nor hears. Bare connectors, close together, as in StationGameTests.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeadlessTncGameTests {

	private static final BlockPos RADIO_A = new BlockPos(3, 2, 6), TNC_A = RADIO_A.north();
	private static final BlockPos RADIO_B = new BlockPos(7, 2, 6), TNC_B = RADIO_B.north(), GENERATOR_B = TNC_B.north();

	private static void radio(GameTestHelper helper, BlockPos pos, int kHz) {
		helper.setBlock(pos, RadiocraftBlocks.VHF_BASE_STATION.get());
		GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).setFrequency(kHz * 1000.0F);
	}

	private static void tnc(GameTestHelper helper, BlockPos pos, String callsign) {
		helper.setBlock(pos, RadiocraftBlocks.HEADLESS_TNC.get());
		HeadlessTncBlockEntity tnc = GameTestUtil.blockEntity(helper, pos, HeadlessTncBlockEntity.class);
		tnc.setCallsign(callsign, "gametest");
		tnc.setBeaconEnabled(false); // The tests beacon by hand.
	}

	private static void powerOn(GameTestHelper helper, BlockPos pos) {
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, pos, RadioNetworkObject.class);
		if(!networkObject.isPowered)
			GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).toggle();
	}

	private static HeadlessTncBlockEntity tnc(GameTestHelper helper, BlockPos pos) {
		return GameTestUtil.blockEntity(helper, pos, HeadlessTncBlockEntity.class);
	}

	/** A TNC beacons its Local Weather Station's weather; a generator behind another TNC counts it as a station. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = "headless_tnc_wx")
	public static void tncBeaconsWeatherToAGenerator(GameTestHelper helper) {
		radio(helper, RADIO_A, 144_390);
		radio(helper, RADIO_B, 144_390);
		tnc(helper, TNC_A, "W1TNC-3");
		tnc(helper, TNC_B, "W1TNC-4");
		helper.setBlock(GENERATOR_B, RadiocraftBlocks.WEATHER_ALERT_GENERATOR.get());
		helper.setBlock(new BlockPos(3, 2, 12), RadiocraftBlocks.WEATHER_STATION.get());
		helper.startSequence()
				.thenExecute(() -> {
					powerOn(helper, RADIO_A);
					powerOn(helper, RADIO_B);
				})
				.thenIdle(25) // The station reads the weather once a second.
				.thenExecute(() -> helper.assertTrue(tnc(helper, TNC_A).beacon(), "A did not beacon"))
				.thenWaitUntil(() -> helper.assertTrue(tnc(helper, TNC_B).getBeacons().stream().anyMatch(h -> h.packet().from().equals("W1TNC-3")),
						"B never heard A's beacon"))
				.thenExecute(() -> {
					TncPacket packet = tnc(helper, TNC_B).getBeacons().getFirst().packet();
					helper.assertTrue(AprsIcon.byName(packet.icon()) == AprsIcon.WX && AprsWeather.parse(packet.text()) != null,
							"the beacon carries no weather: " + packet.icon() + " \"" + packet.text() + "\"");
					WeatherAlertGeneratorBlockEntity generator = GameTestUtil.blockEntity(helper, GENERATOR_B, WeatherAlertGeneratorBlockEntity.class);
					generator.refreshNow(helper.getLevel());
					helper.assertTrue(generator.getTier() == 1, "the generator on the TNC did not count it: " + generator.getHeardStations());
				})
				.thenSucceed();
	}

	/** An H19 sends through a TNC as through a radio interface; a radio computer beside the TNC does not see one. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = "headless_tnc")
	public static void tncServesAnH19ButNotAComputer(GameTestHelper helper) {
		radio(helper, RADIO_A, 144_510);
		radio(helper, RADIO_B, 144_510);
		tnc(helper, TNC_A, "W1HTA");
		tnc(helper, TNC_B, "W1HTB");
		BlockPos h19 = TNC_A.north(), computer = TNC_A.west();
		helper.setBlock(h19, RadiocraftBlocks.APRS_TERMINAL.get());
		helper.setBlock(computer, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.startSequence()
				.thenExecute(() -> {
					powerOn(helper, RADIO_A);
					powerOn(helper, RADIO_B);
					helper.assertTrue(StationBus.find(helper.getLevel(), helper.absolutePos(h19)).hasInterface(), "the H19 does not see the TNC");
					helper.assertFalse(StationBus.find(helper.getLevel(), helper.absolutePos(computer)).hasInterface(), "the computer uses the TNC");
					helper.assertTrue(GameTestUtil.blockEntity(helper, computer, RadioComputerBlockEntity.class) != null, "no computer");
				})
				.thenIdle(1)
				.thenExecute(() -> helper.assertTrue(GameTestUtil.blockEntity(helper, h19, AprsTerminalBlockEntity.class).beacon(), "the H19 could not beacon through the TNC"))
				.thenWaitUntil(() -> helper.assertTrue(tnc(helper, TNC_B).getBeacons().stream().anyMatch(h -> h.packet().from().equals("W1HTA")),
						"the H19's beacon never arrived"))
				.thenSucceed();
	}

	/** Switched off it sends nothing and hears nothing. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = "headless_tnc_off")
	public static void tncSwitchedOffIsDeaf(GameTestHelper helper) {
		radio(helper, RADIO_A, 144_530);
		radio(helper, RADIO_B, 144_530);
		tnc(helper, TNC_A, "W1OFA");
		tnc(helper, TNC_B, "W1OFB");
		helper.startSequence()
				.thenExecute(() -> {
					powerOn(helper, RADIO_A);
					powerOn(helper, RADIO_B);
					tnc(helper, TNC_A).setOn(false);
					helper.assertFalse(tnc(helper, TNC_A).beacon(), "an off TNC beaconed");
					tnc(helper, TNC_A).setOn(true);
					tnc(helper, TNC_B).setOn(false);
					helper.assertTrue(tnc(helper, TNC_A).beacon(), "A did not beacon");
				})
				.thenWaitUntil(() -> helper.assertFalse(GameTestUtil.blockEntity(helper, RADIO_A, RadioBlockEntity.class).isSendingData(), "A is still sending"))
				.thenIdle(5)
				.thenExecute(() -> helper.assertTrue(tnc(helper, TNC_B).getBeacons().isEmpty(), "an off TNC heard a beacon"))
				.thenSucceed();
	}
}
