package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.capabilities.IVHFHandheldCapability;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.VHFRadioBlockEntity;
import com.arrl.radiocraft.common.capabilities.RadiocraftCapabilities;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.CommercialHandheldItem;
import com.arrl.radiocraft.common.items.MarsModItem;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.BandEdges;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgramming;
import com.arrl.radiocraft.common.radio.voice.DigitalVoice;
import com.arrl.radiocraft.common.radio.voice.handheld.PlayerRadio;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 1.12 step 2: the commercial radios. Mobiles talk over their bare connectors (no antenna, a few blocks), each test on
 * its own commercial VHF channel so the plots side by side do not hear each other: analog, digital, encryption, blank
 * channels, the band their signals carry, programming through the API, and the handhelds' channel and dye.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class CommercialRadioGameTests {

	private static final String BATCH = "commercial_radios";
	private static final UUID OPERATOR = UUID.nameUUIDFromBytes("gametest-commercial".getBytes());
	private static final BlockPos A = new BlockPos(4, 2, 4), B = new BlockPos(6, 2, 4), C = new BlockPos(4, 2, 6), D = new BlockPos(6, 2, 6);

	private static short[] tone() {
		short[] audio = new short[960];
		for(int i = 0; i < audio.length; i++)
			audio[i] = (short)(Math.sin(2 * Math.PI * 440 * i / 48000.0D) * 8000);
		return audio;
	}

	private static CommercialMobileBlockEntity mobile(GameTestHelper helper, BlockPos pos) {
		return GameTestUtil.blockEntity(helper, pos, CommercialMobileBlockEntity.class);
	}

	private static void powerOn(GameTestHelper helper, BlockPos pos) {
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, pos, RadioNetworkObject.class);
		if(!networkObject.isPowered)
			GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).toggle();
		helper.assertTrue(networkObject.isPowered, "Radio at " + pos.toShortString() + " did not power on");
	}

	/** Programs the mobile at {@code pos} with {@code spec} on channel 1 and turns its knob there. */
	private static void program(GameTestHelper helper, BlockPos pos, String spec) {
		CommercialMobileBlockEntity radio = mobile(helper, pos);
		CommercialProgramming.Result result = CommercialProgramming.writeChannel(radio, 0, CommercialChannel.parse(spec));
		helper.assertTrue(result.ok(), "Programming " + spec + " failed: " + result.problems());
		CommercialProgramming.select(radio, 0);
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

	/** Two analog mobiles on one channel hear each other, in band: no out-of-band splatter from a commercial radio. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void analogMobilesTalkInBand(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		helper.setBlock(B, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					program(helper, A, "OPS 1|155.0125");
					program(helper, B, "OPS 1|155.0125");
					powerOn(helper, A);
					powerOn(helper, B);
				})
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, A, B), "No bare connectors yet"))
				.thenExecute(() -> {
					CommercialMobileBlockEntity a = mobile(helper, A);
					helper.assertTrue(a.getBand() != null && a.getBand().name().equals(Band.VHF_COMMERCIAL), "The mobile is on commercial VHF, was " + a.getBand());
					helper.assertFalse(a.isOutOfBand() || a.isTransmitInhibited(), "155.0125 is in its band");
					helper.assertTrue(BandEdges.splatterReach(a.getBand(), a.getTransmitFrequency()) == 1.0D, "In band: no extra splatter");
					speak(helper, A);
					helper.assertTrue(heardNow(helper, B), "B did not hear A");
					helper.assertTrue(mobile(helper, B).getLastReception() == DigitalVoice.Reception.ANALOG, "Analog, was " + mobile(helper, B).getLastReception());
				})
				.thenSucceed();
	}

	/** Encrypted digital: the radio with the key decodes it, one with another key scrambles it, a ham radio buzzes. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void encryptionNeedsTheKey(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		helper.setBlock(B, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		helper.setBlock(C, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		helper.setBlock(D, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					program(helper, A, "SWAT|155.0375|=|DIG|0|100|H");
					program(helper, B, "SWAT|155.0375|=|DIG|0|100|H");
					program(helper, C, "OTHER|155.0375|=|DIG|0|200");
					GameTestUtil.blockEntity(helper, D, VHFRadioBlockEntity.class).setFrequency(155_037_500.0F); // A ham rig listening in (no MARS mod needed to listen).
					powerOn(helper, A);
					powerOn(helper, B);
					powerOn(helper, C);
					powerOn(helper, D);
				})
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, A, B, C, D), "No bare connectors yet"))
				.thenExecute(() -> {
					speak(helper, A);
					helper.assertTrue(heardNow(helper, B) && heardNow(helper, C) && heardNow(helper, D), "Everyone should hear the signal");
					helper.assertTrue(mobile(helper, B).getLastReception() == DigitalVoice.Reception.CLEAR, "B has the key: " + mobile(helper, B).getLastReception());
					helper.assertTrue(mobile(helper, C).getLastReception() == DigitalVoice.Reception.SCRAMBLED, "C has another key: " + mobile(helper, C).getLastReception());
					DigitalVoice.Reception ham = GameTestUtil.blockEntity(helper, D, RadioBlockEntity.class).getLastReception();
					helper.assertTrue(ham == DigitalVoice.Reception.BUZZ, "The ham radio hears a buzz: " + ham);
					helper.assertTrue(CommercialProgramming.read(mobile(helper, A)).getFirst().keyId() == CommercialChannel.KEY_HIDDEN, "The key is not readable");
				})
				.thenSucceed();
	}

	/** A blank knob position hears nothing and will not key; a receive-only channel hears but will not key. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void blankAndReceiveOnlyChannels(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		helper.setBlock(B, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					program(helper, A, "TX|155.0625");
					program(helper, B, "RX ONLY|155.0625|RX");
					powerOn(helper, A);
					powerOn(helper, B);
				})
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, A, B), "No bare connectors yet"))
				.thenExecute(() -> {
					CommercialMobileBlockEntity b = mobile(helper, B);
					helper.assertTrue(b.isTransmitInhibited(), "A receive-only channel does not key");
					speak(helper, A);
					helper.assertTrue(heardNow(helper, B), "Receive only still receives");
					CommercialProgramming.select(b, 9); // Unprogrammed.
					helper.assertTrue(b.getChannel() == null && b.isTransmitInhibited(), "A blank position does not key");
				})
				.thenIdle(1)
				.thenExecute(() -> {
					speak(helper, A);
					helper.assertFalse(heardNow(helper, B), "A blank position hears nothing");
				})
				.thenSucceed();
	}

	/** The API refuses what a model cannot do, and the knob steps channels where a ham radio would step frequency. */
	@GameTest(template = GameTestUtil.EMPTY, batch = BATCH)
	public static void programmingFollowsTheModel(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.LOW_BAND_MOBILE.get());
		CommercialMobileBlockEntity low = mobile(helper, A);
		helper.assertTrue(low.getBand().name().equals(Band.LOW_BAND), "The low band mobile is on low band, was " + low.getBand());
		helper.assertFalse(CommercialProgramming.writeChannel(low, 0, CommercialChannel.parse("VHF|155.0")).ok(), "155 MHz on a low band radio");
		helper.assertFalse(CommercialProgramming.writeChannel(low, 0, CommercialChannel.parse("DIG|39.0|=|DIG")).ok(), "Digital on the low band radio");
		helper.assertTrue(CommercialProgramming.writeChannel(low, 0, CommercialChannel.parse("FOREST|39.46")).ok(), "39.46 MHz analog");
		CommercialProgramming.select(low, 0);
		helper.assertTrue(Math.abs(low.getFrequency() - 39_460_000.0F) < 1.0F, "On the channel's frequency, was " + low.getFrequency());
		low.updateFrequency(1);
		helper.assertTrue(low.getChannelPosition() == 1, "Stepping turns the knob, was " + low.getChannelPosition());
		helper.assertFalse(MarsModItem.fits(new ItemStack(RadiocraftItems.LOW_BAND_MOBILE.get())), "No MARS mod in a commercial radio");
		helper.succeed();
	}

	/** Placed from an item, a mobile takes its channels and colour, and gives them back broken. */
	@GameTest(template = GameTestUtil.EMPTY, batch = BATCH)
	public static void mobileKeepsItsProgramAndColour(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		CommercialMobileBlockEntity radio = mobile(helper, A);
		ItemStack item = new ItemStack(RadiocraftItems.COMMERCIAL_MOBILE.get());
		item.set(RadiocraftDataComponent.COMMERCIAL_PROGRAM.value(), new CommercialProgram(List.of(CommercialChannel.parse("DISPATCH|155.1")), 0));
		item.set(DataComponents.DYED_COLOR, new DyedItemColor(0xAA2222, true));
		radio.applyComponentsFromItemStack(item);
		helper.assertTrue(radio.getChannel() != null && radio.getChannel().name().equals("DISPATCH"), "Placed with its channels");
		helper.assertTrue(radio.getCaseColour() == 0xAA2222, "Placed with its colour");
		DataComponentMap components = radio.collectComponents();
		CommercialProgram dropped = components.get(RadiocraftDataComponent.COMMERCIAL_PROGRAM.value());
		helper.assertTrue(dropped != null && dropped.channel(0).name().equals("DISPATCH"), "Broken, it keeps its channels");
		DyedItemColor colour = components.get(DataComponents.DYED_COLOR);
		helper.assertTrue(colour != null && colour.rgb() == 0xAA2222, "Broken, it keeps its colour");
		helper.succeed();
	}

	/** A commercial handheld: ships blank, transmits only on its channel, no MARS mod, and it dyes like leather. */
	@GameTest(template = GameTestUtil.EMPTY, batch = BATCH)
	public static void commercialHandheldIsChannelized(GameTestHelper helper) {
		ItemStack handheld = new ItemStack(RadiocraftItems.BASIC_COMMERCIAL_HANDHELD.get());
		IVHFHandheldCapability cap = handheld.getCapability(RadiocraftCapabilities.VHF_HANDHELDS);
		helper.assertTrue(cap != null, "A commercial handheld is a handheld underneath");
		helper.assertTrue(CommercialHandheldItem.activeChannel(handheld) == null, "A new radio ships blank");
		helper.assertFalse(PlayerRadio.handheldMayTransmit(handheld, 0.0F), "A blank radio does not transmit");
		helper.assertTrue(CommercialProgramming.writeChannel(CommercialProgramming.of(handheld), 0, CommercialChannel.simplex("MURS 1", 151_820_000.0F)).ok(), "programmed");
		helper.assertTrue(CommercialProgramming.writeChannel(CommercialProgramming.of(handheld), 1, CommercialChannel.simplex("MURS 2", 151_880_000.0F)).ok(), "programmed");
		CommercialChannel murs = CommercialHandheldItem.activeChannel(handheld);
		helper.assertTrue(murs != null && murs.name().equals("MURS 1"), "Channel 1 is MURS 1, was " + murs);
		helper.assertTrue(PlayerRadio.handheldMayTransmit(handheld, 0.0F), "It transmits on its channel");
		helper.assertTrue(CommercialHandheldItem.transmitBand(handheld).name().equals(Band.VHF_COMMERCIAL), "In band on commercial VHF");
		CommercialHandheldItem.selectChannel(handheld, 12);
		helper.assertFalse(PlayerRadio.handheldMayTransmit(handheld, 151_820_000.0F), "A blank position does not transmit");
		CommercialHandheldItem.selectChannel(handheld, 1);
		helper.assertTrue(Math.abs(cap.getFrequencyHertz() - 151_880_000.0F) < 1.0F, "The radio state follows the channel, was " + cap.getFrequencyHertz());
		helper.assertFalse(MarsModItem.fits(handheld), "No MARS mod in a commercial radio");
		helper.assertFalse(CommercialProgramming.writeChannel(CommercialProgramming.of(handheld), 0, CommercialChannel.parse("HAM|146.52")).ok(),
				"The basic handheld has no 2m");
		helper.assertTrue(CommercialProgramming.writeChannel(CommercialProgramming.of(new ItemStack(RadiocraftItems.UPGRADED_COMMERCIAL_HANDHELD.get())), 0,
				CommercialChannel.parse("HAM|146.52")).ok(), "The upgraded handheld covers 2m");

		helper.assertTrue(CommercialProgramming.read(CommercialProgramming.of(handheld)).size() == 16, "The basic handheld holds 16 channels");
		helper.assertFalse(CommercialProgramming.writeChannel(CommercialProgramming.of(handheld), 16, CommercialChannel.parse("SEVTEEN|155.1")).ok(),
				"The basic handheld has no channel 17");

		CraftingInput input = CraftingInput.of(2, 1, List.of(handheld, new ItemStack(Items.RED_DYE)));
		ItemStack dyed = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.map(recipe -> recipe.value().assemble(input, helper.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
		helper.assertTrue(dyed.has(DataComponents.DYED_COLOR), "Red dye should colour the case (#minecraft:dyeable)");
		helper.assertTrue(CommercialHandheldItem.activeChannel(dyed) != null, "Dyeing keeps the channels");
		helper.succeed();
	}

	/**
	 * The upgraded handheld holds 40 channels behind its 16-position knob and a zone switch (A 1-16, B 17-32, C 33-40):
	 * the zone moves the radio to the same knob position in that zone, the knob stops at the zone's ends, and the radio
	 * state (what transmits and receives) follows the channel.
	 */
	@GameTest(template = GameTestUtil.EMPTY, batch = BATCH)
	public static void upgradedHandheldHasZones(GameTestHelper helper) {
		ItemStack handheld = new ItemStack(RadiocraftItems.UPGRADED_COMMERCIAL_HANDHELD.get());
		IVHFHandheldCapability cap = handheld.getCapability(RadiocraftCapabilities.VHF_HANDHELDS);
		CommercialProgramming.Target target = CommercialProgramming.of(handheld);
		helper.assertTrue(target != null && target.model().channelCount() == 40 && target.model().zones() == 3, "The upgraded handheld holds 40 channels in 3 zones");
		List<CommercialChannel> channels = new ArrayList<>();
		for(int i = 0; i < 40; i++)
			channels.add(CommercialChannel.simplex("CH" + (i + 1), 155_000_000.0F + i * 12_500.0F));
		helper.assertTrue(CommercialProgramming.write(target, channels).ok(), "40 channels did not fit");
		CommercialHandheldItem.selectChannel(handheld, 3);
		CommercialHandheldItem.setProgram(handheld, CommercialHandheldItem.program(handheld).zone(1, 3));
		CommercialChannel on = CommercialHandheldItem.activeChannel(handheld);
		helper.assertTrue(on != null && on.name().equals("CH20"), "Zone B, knob 4 should be channel 20, was " + on);
		helper.assertTrue(Math.abs(cap.getFrequencyHertz() - (155_000_000.0F + 19 * 12_500.0F)) < 1.0F, "The radio state follows the zone, was " + cap.getFrequencyHertz());
		CommercialHandheldItem.setProgram(handheld, CommercialHandheldItem.program(handheld).zone(2, 3).turn(16));
		helper.assertTrue(CommercialHandheldItem.program(handheld).selected() == 47 && CommercialHandheldItem.activeChannel(handheld) == null,
				"Zone C, knob 16 is past channel 40: blank");
		helper.assertFalse(PlayerRadio.handheldMayTransmit(handheld, 0.0F), "A blank zone C position does not transmit");
		CommercialHandheldItem.selectChannel(handheld, 39);
		helper.assertTrue(CommercialHandheldItem.activeChannel(handheld).name().equals("CH40"), "Channel 40 selectable");
		helper.assertTrue(PlayerRadio.handheldMayTransmit(handheld, 0.0F), "It transmits on channel 40");

		// The basic handheld has no zone switch: a zone C position from a packet lands in zone A, knob kept.
		ItemStack basic = new ItemStack(RadiocraftItems.BASIC_COMMERCIAL_HANDHELD.get());
		CommercialHandheldItem.selectChannel(basic, 33);
		helper.assertTrue(CommercialHandheldItem.program(basic).selected() == 1, "The basic handheld should be on channel 2, was " + (CommercialHandheldItem.program(basic).selected() + 1));
		helper.succeed();
	}
}
