package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.weather.StormReport;
import com.arrl.radiocraft.api.weather.WeatherReport;
import com.arrl.radiocraft.api.weather.WeatherSource;
import com.arrl.radiocraft.common.blockentities.AprsTerminalBlockEntity;
import com.arrl.radiocraft.common.blockentities.WeatherAlertGeneratorBlockEntity;
import com.arrl.radiocraft.common.blockentities.WeatherAlertReceiverBlockEntity;
import com.arrl.radiocraft.common.blocks.WeatherAlertReceiverBlock;
import com.arrl.radiocraft.common.blocks.antennas.DoubleVHFAntennaBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.common.radio.wxalert.ReceiverSignals;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The weather alert blocks (1.12): the generator will not go on the air without an APRS station that hears three
 * weather beacons each 150 blocks from the others; a receiver near a generator with a tornado over it flashes and
 * gives one redstone pulse however often the alert is sent; a low battery blinks the lamp every 10 s.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class WeatherAlertGameTests {

	/** The weather alert channel is shared by every plot: the test that transmits runs on its own. */
	private static final String AIR_BATCH = "weather_alert_air";
	private static final String WEATHER = "_11061200c270s005g008t060r000p000h50b10130";

	/** A tornado (down) over {@code at}, radius 40, and nothing else. */
	private record Tornado(BlockPos at) implements WeatherSource {
		@Override public String name() { return "test"; }
		@Override public boolean handles(Level level) { return true; }
		@Override public boolean isLocal() { return true; }
		@Override public WeatherReport reportAt(ServerLevel level, BlockPos pos, long gameTime) {
			return new WeatherReport(WeatherReport.Precipitation.RAIN, 1.0F, 1.0F, 30.0D, 270.0D, 20.0D, 990.0D, 90.0D, Set.of(WeatherReport.Hazard.TORNADO));
		}
		@Override public double rainAt(ServerLevel level, double x, double z, long gameTime) { return 1.0D; }
		@Override public double thunderAt(ServerLevel level, double x, double z, long gameTime) { return 1.0D; }
		@Override public Optional<Arrival> nextArrival(ServerLevel level, BlockPos pos, long horizon) { return Optional.empty(); }
		@Override public long forecastHorizon(ServerLevel level) { return 0L; }
		@Override public List<StormReport> storms(ServerLevel level, BlockPos center, double radius, boolean forecast) {
			return List.of(new StormReport(77L, StormReport.Kind.TORNADO, false, 0L,
					List.of(new StormReport.TrackPoint(0L, at.getX(), at.getZ(), 40.0D, 1.0F)), 2));
		}
	}

	private static void beacon(GameTestHelper helper, AprsTerminalBlockEntity h19, String call, int dx, int dz) {
		BlockPos at = helper.absolutePos(BlockPos.ZERO).offset(dx, 0, dz);
		h19.receiveDirect(TncPacket.weatherBeacon(helper.getLevel().getRandom().nextLong(), call, at, WEATHER, ""));
	}

	private static WeatherAlertGeneratorBlockEntity.State state(GameTestHelper helper, BlockPos generator) {
		WeatherAlertGeneratorBlockEntity be = GameTestUtil.blockEntity(helper, generator, WeatherAlertGeneratorBlockEntity.class);
		be.refreshNow(helper.getLevel());
		return be.getState();
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 60)
	public static void generatorNeedsSpreadOutWeatherStations(GameTestHelper helper) {
		BlockPos lone = new BlockPos(12, 1, 12), generator = new BlockPos(2, 1, 2), h19 = new BlockPos(3, 1, 2);
		helper.setBlock(lone, RadiocraftBlocks.WEATHER_ALERT_GENERATOR.get());
		helper.setBlock(generator, RadiocraftBlocks.WEATHER_ALERT_GENERATOR.get());
		helper.setBlock(h19, RadiocraftBlocks.APRS_TERMINAL.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertTrue(state(helper, lone) == WeatherAlertGeneratorBlockEntity.State.NO_APRS,
							"a generator with no APRS station is " + state(helper, lone));
					helper.assertTrue(state(helper, generator) == WeatherAlertGeneratorBlockEntity.State.TOO_FEW,
							"a station that heard nothing is " + state(helper, generator));
					AprsTerminalBlockEntity terminal = GameTestUtil.blockEntity(helper, h19, AprsTerminalBlockEntity.class);
					beacon(helper, terminal, "WX1", -200, 0);
					beacon(helper, terminal, "WX2", 200, 0);
					terminal.receiveDirect(TncPacket.beacon(helper.getLevel().getRandom().nextLong(), "NOWX", helper.absolutePos(BlockPos.ZERO).offset(0, 0, 300), "", "HOUSE"));
					helper.assertTrue(state(helper, generator) == WeatherAlertGeneratorBlockEntity.State.TOO_FEW,
							"two weather beacons and a plain one are " + state(helper, generator));
					beacon(helper, terminal, "WX3", -150, 60); // 78 from WX1: too close.
					helper.assertTrue(state(helper, generator) == WeatherAlertGeneratorBlockEntity.State.TOO_CLOSE,
							"three beacons, two of them close, are " + state(helper, generator));
					WeatherAlertGeneratorBlockEntity be = GameTestUtil.blockEntity(helper, generator, WeatherAlertGeneratorBlockEntity.class);
					helper.assertTrue(be.getHeard() == 3 && be.getSpread() == 2, "heard " + be.getHeard() + ", spread " + be.getSpread());
					beacon(helper, terminal, "WX4", 0, 300);
					helper.assertTrue(state(helper, generator) == WeatherAlertGeneratorBlockEntity.State.NO_ANTENNA,
							"three spread-out beacons and no antenna are " + state(helper, generator));
				})
				.thenSucceed();
	}

	private static ItemStack battery(int fraction100) {
		ItemStack stack = new ItemStack(RadiocraftItems.SMALL_BATTERY.get());
		IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
		if(energy != null) {
			energy.extractEnergy(energy.getMaxEnergyStored(), false);
			energy.receiveEnergy(energy.getMaxEnergyStored() * fraction100 / 100, false);
		}
		return stack;
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 400, batch = AIR_BATCH)
	public static void receiverSoundsATornadoWarningAndPulsesOnce(GameTestHelper helper) {
		BlockPos generator = new BlockPos(2, 1, 2), h19 = new BlockPos(1, 1, 2), receiver = new BlockPos(12, 1, 12);
		helper.setBlock(generator, RadiocraftBlocks.WEATHER_ALERT_GENERATOR.get());
		helper.setBlock(h19, RadiocraftBlocks.APRS_TERMINAL.get());
		GameTestUtil.placeWire(helper, generator.south(), RadiocraftBlocks.COAX_WIRE.get());
		BlockPos lower = generator.south(2);
		helper.setBlock(lower, RadiocraftBlocks.J_POLE_ANTENNA.get().defaultBlockState().setValue(DoubleVHFAntennaBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(lower.above(), RadiocraftBlocks.J_POLE_ANTENNA.get().defaultBlockState().setValue(DoubleVHFAntennaBlock.HALF, DoubleBlockHalf.UPPER));
		helper.setBlock(receiver, RadiocraftBlocks.WEATHER_ALERT_RECEIVER.get());
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					GameTestUtil.blockEntity(helper, receiver, WeatherAlertReceiverBlockEntity.class).swapBattery(battery(100));
					AprsTerminalBlockEntity terminal = GameTestUtil.blockEntity(helper, h19, AprsTerminalBlockEntity.class);
					beacon(helper, terminal, "WX1", -250, -250);
					beacon(helper, terminal, "WX2", 250, -250);
					beacon(helper, terminal, "WX3", 0, 300);
					GameTestUtil.blockEntity(helper, generator, WeatherAlertGeneratorBlockEntity.class).setWeatherOverride(new Tornado(helper.absolutePos(receiver)));
				})
				.thenWaitUntil(() -> helper.assertTrue(state(helper, generator) == WeatherAlertGeneratorBlockEntity.State.READY,
						"the generator never got ready: " + state(helper, generator)))
				.thenWaitUntil(() -> helper.assertTrue(GameTestUtil.blockEntity(helper, receiver, WeatherAlertReceiverBlockEntity.class).getPulses() == 1,
						"the receiver never sounded the tornado warning"))
				.thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(receiver).getValue(WeatherAlertReceiverBlock.POWERED), "no redstone pulse"))
				.thenExecute(() -> {
					WeatherAlertReceiverBlockEntity be = GameTestUtil.blockEntity(helper, receiver, WeatherAlertReceiverBlockEntity.class);
					helper.assertTrue(be.getLastAlert().startsWith("TORNADO WARNING"), "the alert was " + be.getLastAlert());
					helper.assertTrue(be.isAlerting(helper.getLevel().getGameTime()), "the receiver is not alerting");
				})
				.thenWaitUntil(() -> helper.assertFalse(GameTestUtil.blockEntity(helper, generator, WeatherAlertGeneratorBlockEntity.class).isTransmitting(),
						"the generator is still sending its tone"))
				.thenExecute(() -> helper.assertTrue(GameTestUtil.blockEntity(helper, generator, WeatherAlertGeneratorBlockEntity.class).broadcastNow(),
						"the generator would not send again"))
				.thenIdle(ReceiverSignals.PULSE_TICKS + 60) // The pulse ends; the alert sent again has arrived.
				.thenExecute(() -> {
					helper.assertFalse(helper.getBlockState(receiver).getValue(WeatherAlertReceiverBlock.POWERED), "the redstone signal is held");
					int pulses = GameTestUtil.blockEntity(helper, receiver, WeatherAlertReceiverBlockEntity.class).getPulses();
					helper.assertTrue(pulses == 1, "the same alert sent again pulsed again (" + pulses + " pulses)");
				})
				.thenSucceed();
	}

	/**
	 * Storm Spotter and Take Shelter Now (the advancements batch): the generator's placer gets Storm Spotter when its
	 * first warning goes out; a player near the receiver when it sounds the tornado warning gets Take Shelter Now once
	 * the tornado (the receiver's own weather, a tornado over it) is within 100 blocks. A batch of its own: the air.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 400, batch = "weather_alert_air_advancements")
	public static void warningsGiveStormSpotterAndTakeShelter(GameTestHelper helper) {
		BlockPos generator = new BlockPos(2, 1, 2), h19 = new BlockPos(1, 1, 2), receiver = new BlockPos(12, 1, 12);
		helper.setBlock(generator, RadiocraftBlocks.WEATHER_ALERT_GENERATOR.get());
		helper.setBlock(h19, RadiocraftBlocks.APRS_TERMINAL.get());
		GameTestUtil.placeWire(helper, generator.south(), RadiocraftBlocks.COAX_WIRE.get());
		BlockPos lower = generator.south(2);
		helper.setBlock(lower, RadiocraftBlocks.J_POLE_ANTENNA.get().defaultBlockState().setValue(DoubleVHFAntennaBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(lower.above(), RadiocraftBlocks.J_POLE_ANTENNA.get().defaultBlockState().setValue(DoubleVHFAntennaBlock.HALF, DoubleBlockHalf.UPPER));
		helper.setBlock(receiver, RadiocraftBlocks.WEATHER_ALERT_RECEIVER.get());
		net.neoforged.neoforge.common.util.FakePlayer spotter = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
				new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("adv-spotter".getBytes()), "wxspotter"));
		net.neoforged.neoforge.common.util.FakePlayer sheltering = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
				new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("adv-shelter".getBytes()), "wxshelter"));
		BlockPos far = helper.absolutePos(generator).offset(0, 0, -300), near = helper.absolutePos(receiver.north(2));
		spotter.moveTo(far.getX(), far.getY(), far.getZ());
		sheltering.moveTo(near.getX() + 0.5D, near.getY(), near.getZ() + 0.5D);
		com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.registerTestPlayer(spotter);
		com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.registerTestPlayer(sheltering);
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					WeatherAlertReceiverBlockEntity rx = GameTestUtil.blockEntity(helper, receiver, WeatherAlertReceiverBlockEntity.class);
					rx.swapBattery(battery(100));
					rx.setWeatherOverride(new Tornado(helper.absolutePos(receiver)));
					AprsTerminalBlockEntity terminal = GameTestUtil.blockEntity(helper, h19, AprsTerminalBlockEntity.class);
					beacon(helper, terminal, "WX1", -250, -250);
					beacon(helper, terminal, "WX2", 250, -250);
					beacon(helper, terminal, "WX3", 0, 300);
					WeatherAlertGeneratorBlockEntity gen = GameTestUtil.blockEntity(helper, generator, WeatherAlertGeneratorBlockEntity.class);
					gen.setPlacer(spotter.getUUID());
					gen.setWeatherOverride(new Tornado(helper.absolutePos(receiver)));
				})
				.thenWaitUntil(() -> helper.assertTrue(com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.has(spotter,
						com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.STORM_SPOTTER), "the placer did not get Storm Spotter"))
				.thenWaitUntil(() -> helper.assertTrue(com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.has(sheltering,
						com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.TAKE_SHELTER_NOW), "the player by the receiver did not get Take Shelter Now"))
				.thenExecute(() -> {
					helper.assertFalse(com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.has(spotter,
							com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.TAKE_SHELTER_NOW), "a player far from the receiver got Take Shelter Now");
					helper.assertFalse(GameTestUtil.blockEntity(helper, receiver, WeatherAlertReceiverBlockEntity.class).watchingForTornado(), "still watching after the tornado came");
				})
				.thenWaitUntil(() -> helper.assertFalse(GameTestUtil.blockEntity(helper, generator, WeatherAlertGeneratorBlockEntity.class).isTransmitting(),
						"the generator is still sending its tone"))
				.thenExecute(() -> {
					com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.unregisterTestPlayer(spotter);
					com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.unregisterTestPlayer(sheltering);
				})
				.thenSucceed();
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 260)
	public static void lowBatteryBlinksEveryTenSeconds(GameTestHelper helper) {
		BlockPos low = new BlockPos(2, 1, 2), full = new BlockPos(6, 1, 2), none = new BlockPos(10, 1, 2);
		helper.setBlock(low, RadiocraftBlocks.WEATHER_ALERT_RECEIVER.get());
		helper.setBlock(full, RadiocraftBlocks.WEATHER_ALERT_RECEIVER.get());
		helper.setBlock(none, RadiocraftBlocks.WEATHER_ALERT_RECEIVER.get());
		helper.startSequence()
				.thenIdle(1)
				.thenExecute(() -> {
					GameTestUtil.blockEntity(helper, low, WeatherAlertReceiverBlockEntity.class).swapBattery(battery(5));
					GameTestUtil.blockEntity(helper, full, WeatherAlertReceiverBlockEntity.class).swapBattery(battery(100));
					helper.assertTrue(GameTestUtil.blockEntity(helper, low, WeatherAlertReceiverBlockEntity.class).isLowBattery(), "5 % is not low");
				})
				.thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(low).getValue(WeatherAlertReceiverBlock.LIT), "a low battery never blinked"))
				.thenExecute(() -> {
					helper.assertFalse(helper.getBlockState(full).getValue(WeatherAlertReceiverBlock.LIT), "a full battery blinks");
					helper.assertFalse(helper.getBlockState(none).getValue(WeatherAlertReceiverBlock.LIT), "no battery blinks");
				})
				.thenIdle(ReceiverSignals.LOW_BLINK_TICKS + 1)
				.thenExecute(() -> helper.assertFalse(helper.getBlockState(low).getValue(WeatherAlertReceiverBlock.LIT), "the blink is held on"))
				.thenSucceed();
	}
}
