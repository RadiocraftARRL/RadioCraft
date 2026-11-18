package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.WeatherAlertGeneratorBlockEntity;
import com.arrl.radiocraft.common.blockentities.WeatherObserverBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Weather Observer (2026-11-18): it sends its Local Weather Station's readings through its radio, and a weather alert
 * generator with a radio on that frequency counts it as a station; one on another frequency does not hear it. Its own
 * batch: the station and the air reach the plots beside it.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class WeatherObserverGameTests {

	private static final BlockPos RADIO_A = new BlockPos(3, 2, 6), OBSERVER = RADIO_A.east();
	private static final BlockPos RADIO_B = new BlockPos(6, 2, 6), GENERATOR_B = RADIO_B.east(); // Close: bare connectors.
	private static final BlockPos RADIO_C = new BlockPos(6, 2, 8), GENERATOR_C = RADIO_C.east();

	private static void radio(GameTestHelper helper, BlockPos pos, int kHz) {
		helper.setBlock(pos, RadiocraftBlocks.VHF_BASE_STATION.get());
		GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).setFrequency(kHz * 1000.0F);
	}

	private static void powerOn(GameTestHelper helper, BlockPos pos) {
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, pos, RadioNetworkObject.class);
		if(!networkObject.isPowered)
			GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).toggle();
	}

	private static WeatherAlertGeneratorBlockEntity generator(GameTestHelper helper, BlockPos pos) {
		WeatherAlertGeneratorBlockEntity be = GameTestUtil.blockEntity(helper, pos, WeatherAlertGeneratorBlockEntity.class);
		be.refreshNow(helper.getLevel());
		return be;
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 300, batch = "weather_observer")
	public static void generatorHearsAnObserverOnItsFrequency(GameTestHelper helper) {
		radio(helper, RADIO_A, 145_710);
		radio(helper, RADIO_B, 145_710);
		radio(helper, RADIO_C, 145_730);
		helper.setBlock(OBSERVER, RadiocraftBlocks.WEATHER_OBSERVER.get());
		helper.setBlock(GENERATOR_B, RadiocraftBlocks.WEATHER_ALERT_GENERATOR.get());
		helper.setBlock(GENERATOR_C, RadiocraftBlocks.WEATHER_ALERT_GENERATOR.get());
		helper.setBlock(new BlockPos(3, 2, 12), RadiocraftBlocks.WEATHER_STATION.get());
		helper.startSequence()
				.thenExecute(() -> {
					powerOn(helper, RADIO_A);
					powerOn(helper, RADIO_B);
					powerOn(helper, RADIO_C);
					GameTestUtil.blockEntity(helper, OBSERVER, WeatherObserverBlockEntity.class).setCallsign("W1OBS");
					helper.assertTrue(generator(helper, GENERATOR_B).getState() != WeatherAlertGeneratorBlockEntity.State.NO_SOURCE,
							"a generator touching a radio has nothing to hear with");
				})
				.thenIdle(25) // The station reads the weather once a second.
				.thenExecute(() -> {
					WeatherObserverBlockEntity observer = GameTestUtil.blockEntity(helper, OBSERVER, WeatherObserverBlockEntity.class);
					helper.assertTrue(observer.send(helper.getLevel()), "the observer did not send: " + observer.statusLines());
					helper.assertTrue(GameTestUtil.blockEntity(helper, RADIO_A, RadioBlockEntity.class).isSendingData(), "its radio is not sending");
				})
				.thenWaitUntil(() -> helper.assertTrue(generator(helper, GENERATOR_B).getTier() == 1,
						"the generator on the frequency never counted the observer: " + generator(helper, GENERATOR_B).getHeardStations()))
				.thenExecute(() -> {
					WeatherAlertGeneratorBlockEntity.HeardStation heard = generator(helper, GENERATOR_B).getHeardStations().getFirst();
					helper.assertTrue(heard.observer() && heard.call().equals("W1OBS") && heard.standing() == WeatherAlertGeneratorBlockEntity.Standing.COUNTS,
							"the observer is listed as " + heard);
					helper.assertTrue(generator(helper, GENERATOR_C).getTier() == 0, "a generator on another frequency heard the observer");
				})
				.thenSucceed();
	}

	/** Without a station, a radio or a callsign it sends nothing and says why. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void observerSaysWhatIsMissing(GameTestHelper helper) {
		BlockPos lone = new BlockPos(2, 2, 2);
		helper.setBlock(lone, RadiocraftBlocks.WEATHER_OBSERVER.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					WeatherObserverBlockEntity observer = GameTestUtil.blockEntity(helper, lone, WeatherObserverBlockEntity.class);
					helper.assertFalse(observer.send(helper.getLevel()), "a bare observer sent a report");
					helper.assertTrue(observer.statusLines().size() >= 2, "it does not say what is missing: " + observer.statusLines());
				})
				.thenSucceed();
	}
}
