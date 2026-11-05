package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.capabilities.IVHFHandheldCapability;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.radio.MarineRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.VHFRadioBlockEntity;
import com.arrl.radiocraft.common.capabilities.RadiocraftCapabilities;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.MarineHandheldItem;
import com.arrl.radiocraft.common.items.MarsModItem;
import com.arrl.radiocraft.common.items.VHFHandheldItem;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.BandEdges;
import com.arrl.radiocraft.common.radio.MarineChannels;
import com.arrl.radiocraft.common.radio.marine.MarineRadioState;
import com.arrl.radiocraft.common.radio.marine.MarineRadios;
import com.arrl.radiocraft.common.radio.voice.handheld.PlayerRadio;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * 1.12 step 3: the marine radios. Fixed-mount radios talk over their bare connectors (no antenna, a few blocks), each
 * test on its own marine channel so the plots side by side do not hear each other (only {@link #dualWatchGivesSixteenPriority}
 * uses channel 16): in band on commercial VHF, duplex channels, weather and DSC channels, dual watch, HI/LO and the
 * item components, and the handheld.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class MarineRadioGameTests {

	private static final String BATCH = "marine_radios";
	private static final UUID OPERATOR = UUID.nameUUIDFromBytes("gametest-marine".getBytes());
	private static final BlockPos A = new BlockPos(4, 2, 4), B = new BlockPos(6, 2, 4), C = new BlockPos(4, 2, 6), D = new BlockPos(6, 2, 6);
	private static final MarineChannels.Region US = MarineChannels.Region.US;

	private static short[] tone() {
		short[] audio = new short[960];
		for(int i = 0; i < audio.length; i++)
			audio[i] = (short)(Math.sin(2 * Math.PI * 440 * i / 48000.0D) * 8000);
		return audio;
	}

	private static MarineRadioBlockEntity marine(GameTestHelper helper, BlockPos pos) {
		return GameTestUtil.blockEntity(helper, pos, MarineRadioBlockEntity.class);
	}

	private static void powerOn(GameTestHelper helper, BlockPos pos) {
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, pos, RadioNetworkObject.class);
		if(!networkObject.isPowered)
			GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).toggle();
		helper.assertTrue(networkObject.isPowered, "Radio at " + pos.toShortString() + " did not power on");
	}

	/** Puts the marine radio at {@code pos} on channel {@code channel}. */
	private static void channel(GameTestHelper helper, BlockPos pos, String channel) {
		MarineRadioBlockEntity radio = marine(helper, pos);
		radio.setRadioState(radio.getRadioState().select(US, channel));
		helper.assertTrue(radio.getChannel().name().equals(channel), "Not on channel " + channel + ", on " + radio.getChannel().name());
	}

	private static void speak(GameTestHelper helper, BlockPos pos) {
		RadioBlockEntity radio = GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class);
		radio.setPTTDown(true);
		helper.assertTrue(radio.canTransmitVoice(), "Radio at " + pos.toShortString() + " cannot transmit");
		radio.acceptVoicePacket(() -> helper.getLevel(), tone(), OPERATOR);
		radio.setPTTDown(false);
	}

	private static boolean heardNow(GameTestHelper helper, BlockPos pos) {
		return GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).getLastSignalGameTime() == helper.getLevel().getGameTime();
	}

	private static boolean bare(GameTestHelper helper, BlockPos... radios) {
		for(BlockPos pos : radios)
			if(GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).getBareConnector() == null)
				return false;
		return true;
	}

	/** Two marine radios on one simplex channel hear each other, in band on commercial VHF (no out-of-band splatter). */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void marineRadiosTalkInBand(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.MARINE_RADIO.get());
		helper.setBlock(B, RadiocraftBlocks.MARINE_RADIO.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					channel(helper, A, "68");
					channel(helper, B, "68");
					powerOn(helper, A);
					powerOn(helper, B);
				})
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, A, B), "No bare connectors yet"))
				.thenExecute(() -> {
					MarineRadioBlockEntity a = marine(helper, A);
					helper.assertTrue(a.getBand() != null && a.getBand().name().equals(Band.VHF_COMMERCIAL), "The marine radio is on commercial VHF, was " + a.getBand());
					helper.assertTrue(Math.abs(a.getTransmitFrequency() - 156_425_000.0F) < 2.0F, "Channel 68 is 156.425 MHz, was " + a.getTransmitFrequency());
					helper.assertFalse(a.isOutOfBand() || a.isTransmitInhibited(), "Channel 68 is in its band");
					helper.assertTrue(BandEdges.splatterReach(a.getBand(), a.getTransmitFrequency()) == 1.0D, "In band: no extra splatter");
					speak(helper, A);
					helper.assertTrue(heardNow(helper, B), "B did not hear A on channel 68");
				})
				.thenSucceed();
	}

	/**
	 * A duplex channel transmits on the ship frequency and listens 4.6 MHz up (the coast station): two ships on 26 do
	 * not hear each other, but a radio tuned to the ship frequency does.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void duplexChannelListensToTheCoast(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.MARINE_RADIO.get());
		helper.setBlock(B, RadiocraftBlocks.MARINE_RADIO.get());
		helper.setBlock(C, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					channel(helper, A, "26");
					channel(helper, B, "26");
					GameTestUtil.blockEntity(helper, C, VHFRadioBlockEntity.class).setFrequency(157_300_000.0F); // A ham rig listening on the ship frequency.
					powerOn(helper, A);
					powerOn(helper, B);
					powerOn(helper, C);
				})
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, A, B, C), "No bare connectors yet"))
				.thenExecute(() -> {
					MarineRadioBlockEntity a = marine(helper, A);
					helper.assertTrue(Math.abs(a.getTransmitFrequency() - 157_300_000.0F) < 2.0F, "Ship side of 26 is 157.300, was " + a.getTransmitFrequency());
					helper.assertTrue(Math.abs(a.getFrequency() - 161_900_000.0F) < 2.0F, "It listens to the coast on 161.900, was " + a.getFrequency());
					speak(helper, A);
					helper.assertTrue(heardNow(helper, C), "The ship frequency carries the call");
					helper.assertFalse(heardNow(helper, B), "Two ships on a duplex channel do not hear each other");
				})
				.thenSucceed();
	}

	/** The weather channels only receive, and channel 70 is DSC (no voice): the radio will not key on either. */
	@GameTest(template = GameTestUtil.EMPTY, batch = BATCH)
	public static void weatherAndDscChannelsDoNotKey(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.MARINE_RADIO.get());
		MarineRadioBlockEntity radio = marine(helper, A);
		channel(helper, A, "WX1");
		helper.assertTrue(radio.isTransmitInhibited(), "A weather channel does not key");
		helper.assertTrue(Math.abs(radio.getFrequency() - 162_550_000.0F) < 2.0F, "WX1 is 162.550, was " + radio.getFrequency());
		channel(helper, A, "70");
		helper.assertTrue(radio.isTransmitInhibited(), "DSC channel 70 carries no voice");
		radio.updateFrequency(1); // The knob: 70 to 71.
		helper.assertTrue(radio.getChannel().name().equals("71") && !radio.isTransmitInhibited(), "Stepping turns the channel knob, was " + radio.getChannel().name());
		helper.succeed();
	}

	/** Dual watch hears channel 16 as well, and while 16 is busy the working channel stays muted. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void dualWatchGivesSixteenPriority(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.MARINE_RADIO.get());
		helper.setBlock(B, RadiocraftBlocks.MARINE_RADIO.get());
		helper.setBlock(C, RadiocraftBlocks.MARINE_RADIO.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					channel(helper, A, "16");
					channel(helper, B, "72");
					channel(helper, C, "72");
					MarineRadioBlockEntity b = marine(helper, B);
					b.toggleDualWatch();
					helper.assertTrue(b.getRadioState().dualWatch(), "Dual watch on");
					powerOn(helper, A);
					powerOn(helper, B);
					powerOn(helper, C);
				})
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, A, B, C), "No bare connectors yet"))
				.thenExecute(() -> {
					speak(helper, A);
					helper.assertTrue(heardNow(helper, B), "Dual watch hears channel 16");
					helper.assertTrue(marine(helper, B).isPriorityBusy(), "16 holds the receiver");
				})
				.thenIdle(1)
				.thenExecute(() -> {
					speak(helper, C);
					helper.assertFalse(heardNow(helper, B), "The working channel stays muted while 16 is busy");
				})
				.thenSucceed();
	}

	/** Placed from an item, the radio takes its channel and switches, and gives them back when broken; no MARS mod. */
	@GameTest(template = GameTestUtil.EMPTY, batch = BATCH)
	public static void marineRadioKeepsItsSettings(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.MARINE_RADIO.get());
		MarineRadioBlockEntity radio = marine(helper, A);
		ItemStack item = new ItemStack(RadiocraftItems.MARINE_RADIO.get());
		item.set(RadiocraftDataComponent.MARINE_RADIO.value(), MarineRadioState.DEFAULT.select(US, "22A").togglePower());
		radio.applyComponentsFromItemStack(item);
		helper.assertTrue(radio.getChannel().name().equals("22A"), "Placed on its channel, was " + radio.getChannel().name());
		helper.assertFalse(radio.getRadioState().transmitsHigh(MarineRadios.region()), "Placed on low power");
		helper.assertTrue(radio.displayStatus().startsWith("LO"), "The display says LO: " + radio.displayStatus());
		DataComponentMap components = radio.collectComponents();
		MarineRadioState dropped = components.get(RadiocraftDataComponent.MARINE_RADIO.value());
		helper.assertTrue(dropped != null && dropped.channel().equals("22A") && !dropped.highPower(), "Broken, it keeps its settings");
		helper.assertFalse(MarsModItem.fits(item), "No MARS mod in a marine radio");
		radio.pressSixteenNine();
		helper.assertTrue(radio.getChannel().name().equals("16"), "16/9 goes to 16");
		radio.pressSixteenNine();
		radio.pressSixteenNine();
		helper.assertTrue(radio.getChannel().name().equals("22A"), "and back by way of 9, was " + radio.getChannel().name());
		helper.succeed();
	}

	/** The marine handheld: channel-only, transmits in band, no weather-channel transmit, no MARS mod or keypad. */
	@GameTest(template = GameTestUtil.EMPTY, batch = BATCH)
	public static void marineHandheldIsChannelOnly(GameTestHelper helper) {
		ItemStack handheld = new ItemStack(RadiocraftItems.MARINE_HANDHELD.get());
		IVHFHandheldCapability cap = handheld.getCapability(RadiocraftCapabilities.VHF_HANDHELDS);
		helper.assertTrue(cap != null, "A marine handheld is a handheld underneath");
		helper.assertTrue(MarineHandheldItem.channel(handheld).name().equals(MarineChannels.DISTRESS), "A new handheld is on 16");
		helper.assertTrue(PlayerRadio.handheldMayTransmit(handheld, 0.0F), "It transmits on its channel");
		helper.assertTrue(VHFHandheldItem.isChannelized(handheld), "Channelized: no VFO, keypad, memories or APRS");
		helper.assertFalse(MarsModItem.fits(handheld), "No MARS mod in a marine radio");
		MarineHandheldItem.setState(handheld, MarineHandheldItem.state(handheld).select(US, "68"));
		helper.assertTrue(Math.abs(cap.getFrequencyHertz() - 156_425_000.0F) < 2.0F, "The radio state follows the channel, was " + cap.getFrequencyHertz());
		MarineHandheldItem.setState(handheld, MarineHandheldItem.state(handheld).select(US, "WX2"));
		helper.assertFalse(PlayerRadio.handheldMayTransmit(handheld, 162_400_000.0F), "No transmit on a weather channel");
		helper.assertTrue(PlayerRadio.handheldInhibited(handheld, cap), "Inhibited on a weather channel");
		MarineHandheldItem.setState(handheld, MarineHandheldItem.state(handheld).select(US, "16").togglePower());
		helper.assertTrue(MarineHandheldItem.transmitPower(handheld) < 1.0F, "1 W is weaker than 5 W");
		helper.succeed();
	}
}
