package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.radio.CbRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.cb.CbRadioState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The CB radios (2026-11-08): the mobiles talk AM over their bare connectors on the 40 CB channels, each test on its
 * own channels so plots side by side do not hear each other.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class CbRadioGameTests {

	private static final String BATCH = "cb_radios";
	private static final UUID OPERATOR = UUID.nameUUIDFromBytes("gametest-cb".getBytes());
	private static final BlockPos A = new BlockPos(4, 2, 4), B = new BlockPos(6, 2, 4), C = new BlockPos(4, 2, 6);

	private static short[] tone() {
		short[] audio = new short[960];
		for(int i = 0; i < audio.length; i++)
			audio[i] = (short)(Math.sin(2 * Math.PI * 440 * i / 48000.0D) * 8000);
		return audio;
	}

	private static CbRadioBlockEntity cb(GameTestHelper helper, BlockPos pos) {
		return GameTestUtil.blockEntity(helper, pos, CbRadioBlockEntity.class);
	}

	/** Turns the volume knob up from OFF: that switches a CB radio on (the knob is its power switch). */
	private static void powerOn(GameTestHelper helper, BlockPos pos) {
		CbRadioBlockEntity radio = cb(helper, pos);
		CbRadioState s = radio.getRadioState();
		radio.setRadioState(s.withVolume(5));
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, pos, RadioNetworkObject.class);
		helper.assertTrue(networkObject.isPowered, "Radio at " + pos.toShortString() + " did not power on");
	}

	private static void channel(GameTestHelper helper, BlockPos pos, int channel) {
		CbRadioBlockEntity radio = cb(helper, pos);
		CbRadioState s = radio.getRadioState();
		radio.setRadioState(s.withChannel(channel));
	}

	private static void speak(GameTestHelper helper, BlockPos pos) {
		RadioBlockEntity radio = GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class);
		radio.setPTTDown(true);
		helper.assertTrue(radio.canTransmitVoice(), "Radio at " + pos.toShortString() + " cannot transmit");
		radio.acceptVoicePacket(() -> helper.getLevel(), tone(), OPERATOR);
		radio.setPTTDown(false);
	}

	private static boolean bare(GameTestHelper helper, BlockPos... radios) {
		for(BlockPos pos : radios)
			if(GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).getBareConnector() == null)
				return false;
		return true;
	}

	/**
	 * A PC68 and a 5875 on channel 21 (27.215 MHz) hear each other in AM on the CB band; a third radio on channel 22,
	 * 10 kHz up, does not hear it but gets its bleedover.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void cbMobilesTalkOnTheirChannel(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.CB_MOBILE_PC68.get());
		helper.setBlock(B, RadiocraftBlocks.CB_MOBILE_5875.get());
		helper.setBlock(C, RadiocraftBlocks.CB_MOBILE_PC68.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					channel(helper, A, 21);
					channel(helper, B, 21);
					channel(helper, C, 22);
					powerOn(helper, A);
					powerOn(helper, B);
					powerOn(helper, C);
				})
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, A, B, C), "No bare connectors yet"))
				.thenExecute(() -> {
					CbRadioBlockEntity a = cb(helper, A);
					helper.assertTrue(a.getBand() != null && a.getBand().name().equals(Band.CB), "A CB radio is on the CB band, was " + a.getBand());
					helper.assertTrue(a.isAM() && !a.isFM(), "A CB radio hears AM");
					helper.assertTrue(Math.abs(a.getTransmitFrequency() - 27_215_000.0F) < 2.0F, "Channel 21 is 27.215 MHz, was " + a.getTransmitFrequency());
					helper.assertFalse(a.isTransmitInhibited(), "Channel 21 is in the band");
					speak(helper, A);
					long now = helper.getLevel().getGameTime();
					helper.assertTrue(cb(helper, B).getLastSignalGameTime() == now, "B did not hear A on channel 21");
					helper.assertFalse(cb(helper, C).getLastSignalGameTime() == now, "C on channel 22 heard channel 21");
					helper.assertTrue(cb(helper, C).getAdjacentQrmGameTime() == now, "C on channel 22 got no bleedover from channel 21");
				})
				.thenSucceed();
	}

	/**
	 * The squelch knob (2026-11-09): open, a signal plays; turned up past what the RX meter reads, the meter still moves
	 * but the speaker stays shut (nothing reaches the logbook's heard list).
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = BATCH)
	public static void squelchShutsUnderTheMeter(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.CB_MOBILE_PC68.get());
		helper.setBlock(B, RadiocraftBlocks.CB_MOBILE_5875.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					channel(helper, A, 21);
					channel(helper, B, 21);
					powerOn(helper, A);
					powerOn(helper, B);
				})
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, A, B), "No bare connectors yet"))
				.thenExecute(() -> {
					CbRadioBlockEntity b = cb(helper, B);
					speak(helper, A);
					long now = helper.getLevel().getGameTime();
					helper.assertTrue(b.getLastSignalGameTime() == now, "B did not hear A with the squelch open");
					helper.assertFalse(b.getStationsHeard().recent(now, 5).isEmpty(), "An open squelch played nothing");
					double strength = b.getLastSignalStrength();
					helper.assertTrue(strength < 1.0D, "Test needs a signal under full scale, was " + strength);
					b.getStationsHeard().clear();
					b.setRadioState(b.getRadioState().press(CbRadioState.Key.SQUELCH, CbRadioState.MAX_KNOB));
					helper.assertTrue(b.getSquelchLevel() == 1.0F, "Squelch fully up should be the meter's full scale, was " + b.getSquelchLevel());
					speak(helper, A);
					helper.assertTrue(b.getLastSignalGameTime() == now, "The meter should still see a squelched signal");
					helper.assertTrue(b.getStationsHeard().recent(now, 5).isEmpty(), "The squelch let a signal under it through");
				})
				.thenSucceed();
	}

	/** The volume knob is the power switch: OFF switches the radio off, up from OFF on, and the speaker follows it. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = BATCH)
	public static void volumeKnobSwitchesTheRadio(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.CB_MOBILE_5875.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					CbRadioBlockEntity radio = cb(helper, A);
					RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, A, RadioNetworkObject.class);
					helper.assertFalse(networkObject.wantsOn, "A new CB radio is off");
					radio.setRadioState(radio.getRadioState().press(CbRadioState.Key.CHANNEL, 2));
					helper.assertFalse(networkObject.wantsOn, "Turning the channel knob switched it on");
					radio.setRadioState(radio.getRadioState().press(CbRadioState.Key.VOLUME, 3));
					helper.assertTrue(networkObject.wantsOn, "Volume up from OFF did not switch it on");
					helper.assertTrue(Math.abs(radio.getSpeakerGain() - 0.6F) < 1e-4F, "Volume 3 should be gain 0.6, was " + radio.getSpeakerGain());
					radio.setRadioState(radio.getRadioState().press(CbRadioState.Key.VOLUME, 1));
					helper.assertTrue(networkObject.wantsOn, "Turning the volume further switched it off");
					radio.setRadioState(radio.getRadioState().press(CbRadioState.Key.VOLUME, -10));
					helper.assertFalse(networkObject.wantsOn, "Volume to OFF did not switch it off");
				})
				.thenSucceed();
	}

	/**
	 * On a charged battery with power required (as in a real world): the volume knob switches the radio on within a
	 * second, CH9 and the channel knob never switch it off, and a radio switched on some other way shows its knob on.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = "cb_power")
	public static void cbTurnsOnAtOnceOnABattery(GameTestHelper helper) {
		com.arrl.radiocraft.RadiocraftServerConfig.RADIO_POWER_REQUIRED.set(true);
		BlockPos radio = new BlockPos(4, 2, 4), wire = radio.south(), battery = radio.south(2);
		helper.setBlock(radio, RadiocraftBlocks.CB_MOBILE_PC68.get());
		GameTestUtil.placeWire(helper, wire, RadiocraftBlocks.WIRE.get());
		helper.setBlock(battery, RadiocraftBlocks.LARGE_BATTERY.get());
		long[] switchedAt = {0L};
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class).hasBattery(), "No battery on the wire yet"))
				.thenExecute(() -> {
					com.arrl.radiocraft.common.be_networks.network_objects.BatteryNetworkObject cell =
							GameTestUtil.networkObject(helper, battery, com.arrl.radiocraft.common.be_networks.network_objects.BatteryNetworkObject.class);
					cell.getStorage().setEnergy(cell.getStorage().getMaxEnergyStored());
				})
				.thenIdle(5)
				.thenExecute(() -> {
					CbRadioBlockEntity be = cb(helper, radio);
					be.setRadioState(be.getRadioState().press(CbRadioState.Key.VOLUME, 1));
					switchedAt[0] = helper.getLevel().getGameTime();
				})
				.thenWaitUntil(() -> helper.assertTrue(GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class).isPowered,
						"Not running " + (helper.getLevel().getGameTime() - switchedAt[0]) + " ticks after the volume came up"))
				.thenExecute(() -> {
					long took = helper.getLevel().getGameTime() - switchedAt[0];
					helper.assertTrue(took <= 20, "Took " + took + " ticks to switch on");
					CbRadioBlockEntity be = cb(helper, radio);
					for(int i = 0; i < 4; i++)
						be.setRadioState(be.getRadioState().press(CbRadioState.Key.CHANNEL_9, 0));
					be.setRadioState(be.getRadioState().press(CbRadioState.Key.CHANNEL, 3));
					helper.assertTrue(GameTestUtil.networkObject(helper, radio, RadioNetworkObject.class).wantsOn, "CH9 or the channel knob switched it off");
					be.toggle(); // Off some other way: the knob goes to OFF...
				})
				.thenIdle(3)
				.thenExecute(() -> {
					helper.assertTrue(cb(helper, radio).getRadioState().volume() == 0, "The knob should follow the switch to OFF");
					cb(helper, radio).toggle(); // ...and on again: it comes up off OFF.
				})
				.thenIdle(3)
				.thenExecute(() -> {
					helper.assertTrue(cb(helper, radio).getRadioState().volume() > 0, "The knob should follow the switch on");
					com.arrl.radiocraft.RadiocraftServerConfig.RADIO_POWER_REQUIRED.set(false);
				})
				.thenSucceed();
	}

	/**
	 * Modes meet (2026-11-08): an all-band radio on the 10m freeband, tuned to CB channel 21, hears a CB mobile in SSB
	 * (its carrier zero-beat) and in AM; the CB mobile does not hear the all-band radio's SSB (no carrier), but does hear
	 * its AM.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void amAndSsbMeetOnTheFreeband(GameTestHelper helper) {
		BlockPos cbPos = new BlockPos(2, 2, 2), hamPos = new BlockPos(4, 2, 2);
		helper.setBlock(cbPos, RadiocraftBlocks.CB_MOBILE_PC68.get());
		helper.setBlock(hamPos, RadiocraftBlocks.ALL_BAND_RADIO.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					channel(helper, cbPos, 21);
					powerOn(helper, cbPos);
					com.arrl.radiocraft.common.blockentities.radio.HFRadioAllBandBlockEntity ham = GameTestUtil.blockEntity(helper, hamPos, com.arrl.radiocraft.common.blockentities.radio.HFRadioAllBandBlockEntity.class);
					ham.setFrequency(27_215_000.0F);
					RadioNetworkObject net = GameTestUtil.networkObject(helper, hamPos, RadioNetworkObject.class);
					if(!net.isPowered)
						ham.toggle();
					ham.setSSBEnabled(true);
				})
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, cbPos, hamPos), "No bare connectors yet"))
				.thenExecute(() -> {
					com.arrl.radiocraft.common.blockentities.radio.HFRadioAllBandBlockEntity ham = GameTestUtil.blockEntity(helper, hamPos, com.arrl.radiocraft.common.blockentities.radio.HFRadioAllBandBlockEntity.class);
					helper.assertTrue(Math.abs(ham.getFrequency() - 27_215_000.0F) < 2.0F, "The all-band radio did not stay on 27.215 (freeband), was " + ham.getFrequency());
					speak(helper, cbPos);
					long now = helper.getLevel().getGameTime();
					helper.assertTrue(ham.getLastSignalGameTime() == now, "SSB did not hear the CB's AM on its carrier");
					// SSB back to the CB: no carrier, nothing heard (a MARS mod lets the all-band radio transmit there).
					ham.setMarsMod(true);
					long before = cb(helper, cbPos).getLastSignalGameTime();
					speak(helper, hamPos);
					helper.assertTrue(cb(helper, cbPos).getLastSignalGameTime() == before, "The CB heard SSB, which has no carrier");
					ham.setAMEnabled(true);
					speak(helper, hamPos);
					helper.assertTrue(cb(helper, cbPos).getLastSignalGameTime() == helper.getLevel().getGameTime(), "The CB did not hear the all-band radio's AM");
				})
				.thenSucceed();
	}

	/** Only the 5875 has the speech compressor and the MOD/SWR switch; the radio keeps its settings on its item. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = BATCH)
	public static void onlyThe5875HasACompressor(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.CB_MOBILE_PC68.get());
		helper.setBlock(B, RadiocraftBlocks.CB_MOBILE_5875.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					CbRadioState wanted = new CbRadioState(33, true, true, 4, 5);
					cb(helper, A).setRadioState(wanted);
					cb(helper, B).setRadioState(wanted);
					helper.assertFalse(cb(helper, A).getRadioState().compressor() || cb(helper, A).getRadioState().swrMeter(), "The PC68 has no compressor or SWR meter");
					helper.assertTrue(cb(helper, A).getRadioState().channel() == 33, "The PC68 kept its channel");
					helper.assertTrue(cb(helper, B).getRadioState().equals(wanted), "The 5875 kept every setting, was " + cb(helper, B).getRadioState());
					DataComponentMap components = cb(helper, B).collectComponents();
					helper.assertTrue(wanted.equals(components.get(RadiocraftDataComponent.CB_RADIO.value())), "The item does not carry the CB settings");
				})
				.thenSucceed();
	}

	/**
	 * The 5875's FUNCTION knob (2026-11-09): two 5875s on USB hear each other; one on LSB does not hear USB, only some
	 * QRM; a PC68 on AM does not hear USB on its own (no carrier); the PC68's FM switch and the 5875's lack of one.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = BATCH)
	public static void sidebandsOnThe5875(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.CB_MOBILE_5875.get());
		helper.setBlock(B, RadiocraftBlocks.CB_MOBILE_5875.get());
		helper.setBlock(C, RadiocraftBlocks.CB_MOBILE_PC68.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					for(BlockPos pos : new BlockPos[] { A, B, C }) {
						channel(helper, pos, 30);
						powerOn(helper, pos);
					}
					cb(helper, A).setRadioState(cb(helper, A).getRadioState().press(CbRadioState.Key.FUNCTION, 1));
					cb(helper, B).setRadioState(cb(helper, B).getRadioState().press(CbRadioState.Key.FUNCTION, 1));
				})
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, A, B, C), "No bare connectors yet"))
				.thenExecute(() -> {
					helper.assertTrue(cb(helper, A).getRadioState().mode() == CbRadioState.Mode.USB, "FUNCTION one click is USB, was " + cb(helper, A).getRadioState().mode());
					helper.assertTrue(cb(helper, A).getModulation() == com.arrl.radiocraft.common.radio.voice.Modulation.SSB && !cb(helper, A).isLowerSideband(), "USB is SSB, upper");
					speak(helper, A);
					long now = helper.getLevel().getGameTime();
					helper.assertTrue(cb(helper, B).getLastSignalGameTime() == now, "A 5875 on USB did not hear another on USB");
					helper.assertFalse(cb(helper, C).getLastSignalGameTime() == now, "An AM set heard SSB alone");
					cb(helper, B).setRadioState(cb(helper, B).getRadioState().press(CbRadioState.Key.FUNCTION, 1));
					helper.assertTrue(cb(helper, B).isLowerSideband(), "FUNCTION two clicks is LSB");
					helper.getLevel().getGameTime();
				})
				.thenIdle(1)
				.thenExecute(() -> {
					speak(helper, A);
					long now = helper.getLevel().getGameTime();
					helper.assertFalse(cb(helper, B).getLastSignalGameTime() == now, "LSB heard USB");
					helper.assertTrue(cb(helper, B).getAdjacentQrmGameTime() == now, "LSB got no QRM from USB on its channel");
					cb(helper, C).setRadioState(cb(helper, C).getRadioState().press(CbRadioState.Key.AM_FM, 0));
					helper.assertTrue(cb(helper, C).isFM(), "The PC68's AM/FM switch puts it on FM");
					cb(helper, A).setRadioState(cb(helper, A).getRadioState().withMode(CbRadioState.Mode.FM));
					helper.assertFalse(cb(helper, A).isFM(), "The 5875 has no FM");
				})
				.thenSucceed();
	}
}
